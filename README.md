# WatchService combined with Akka actors

[`WatchService`](http://docs.oracle.com/javase/7/docs/api/java/nio/file/WatchService.html) is a handy class that can notify you about any file system changes (create/update/delete of file) in a given set of directories. It is described nicely in the [official documentation](http://docs.oracle.com/javase/tutorial/essential/io/notification.html) so I won't write another introduction tutorial. Instead we will try to combine it with Akka to provide fully asynchronous, non-blocking file system changes notification mechanism. And we will scale it both to multiple directories and multiple... servers!

Just for starters here is a simple, self-descriptive example:

	val watchService = FileSystems.getDefault.newWatchService()
	Paths.get("/foo/bar").register(watchService, ENTRY_CREATE, ENTRY_DELETE)

	while(true) {
		val key = watchService.take()
		key.pollEvents() foreach { event =>
			event.kind() match {
				case ENTRY_CREATE =>    //...
				case ENTRY_DELETE =>    //...
				case x =>
					logger.warn(s"Unknown event $x")
			}
		}
		key.reset()
	}

I know `java.nio` stands for "*New I/O*" and not for "*Non-blocking I/O*" but one might expect such a class to work asynchronously. Instead we have to sacrifice one thread, use awkward `while(true)` loop and block on `watchService.take()`. Maybe that's how the underlying operating systems works (luckily `WatchService` uses native OS API when available)? Doesn't matter, we have to live with that. Fortunately one `WatchService` can monitor arbitrary number of paths, thus we need only one thread per whole application, not per directory. So, let's wrap it up in a `Runnable`:

	class WatchServiceTask2(notifyActor: ActorRef) extends Runnable with Logging {
		private val watchService = FileSystems.getDefault.newWatchService()
	
		def run() {
			try {
				while (!Thread.currentThread().isInterrupted) {
					val key = watchService.take()
					//coming soon...
					key.reset()
				}
			} catch {
				case e: InterruptedException =>
					logger.info("Interrupting, bye!")
			} finally {
				watchService.close()
			}
		}
	}

This is the skeletal implementation of any `Runnable` that waits/blocks I want you to follow. Check [`Thread.isInterrupted()`](http://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#isInterrupted()) and escape main loop when `InterruptedException` occurs. This way you can later safely shut down your thread by calling [`Thread.interrupt()`](http://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#interrupt()) without any delay. Two things to notice: we require `notifyActor` reference in a constructor (will be needed later, I hope you know why) and we don't monitor any directories, yet. Luckily we can add monitored directories at any time (but we can never remove them afterwards, API limitation?!) There is one issue, however: `WatchService` only monitors given directory, but not subdirectories (it is not recursive). Fortunately another new kid on the JDK block, [`Files.walkFileTree()`](http://docs.oracle.com/javase/7/docs/api/java/nio/file/Files.html#walkFileTree(java.nio.file.Path, java.nio.file.FileVisitor)), releases us from tedious recursive algorithm:

	def watchRecursively(root: Path) {
		watch(root)
		Files.walkFileTree(root, new SimpleFileVisitor[Path] {
			override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = {
				watch(dir)
				FileVisitResult.CONTINUE
			}
		})
	}

	private def watch(path: Path) =
		path.register(watchService, ENTRY_CREATE, ENTRY_DELETE)

See how nicely we can traverse the whole directory tree using flat [`FileVisitor`](http://docs.oracle.com/javase/7/docs/api/java/nio/file/FileVisitor.html)? Now the last piece of the puzzle is the body of loop above (you will find full source code on GitHub):

	key.pollEvents() foreach {
		event =>
			val relativePath = event.context().asInstanceOf[Path]
			val path = key.watchable().asInstanceOf[Path].resolve(relativePath)
			event.kind() match {
				case ENTRY_CREATE =>
					if (path.toFile.isDirectory) {
						watchRecursively(path)
					}
					notifyActor ! Created(path.toFile)
				case ENTRY_DELETE =>
					notifyActor ! Deleted(path.toFile)
				case x =>
					logger.warn(s"Unknown event $x")
			}
	}

When a new file system entry is created and it happens to be a directory, we start monitoring that directory as well. This way if, for example, we start monitoring `/tmp`, every single subdirectory is monitored as well, both existing during startup and newly created one.

Message classes are pretty straightforward. You might argue that `CreatedFile` and `CreatedDirectory` separate classes might have been a better idea - depends on your use case, this was simpler from this article's perspective:

	sealed trait FileSystemChange
	case class Created(fileOrDir: File) extends FileSystemChange
	case class Deleted(fileOrDir: File) extends FileSystemChange

	case class MonitorDir(path: Path)

The last `MonitorDir` message will be used in just a second. Let's wrap our `Runnable` task and encapsulate it inside an actor. I know how bad it looks to start a thread inside Akka actor, but Java API forces us to do so and it will be our secret that never escapes that particular actor:

	class FileSystemActor extends Actor {
		val log = Logging(context.system, this)
		val watchServiceTask = new WatchServiceTask(self)
		val watchThread = new Thread(watchServiceTask, "WatchService")

		override def preStart() {
			watchThread.setDaemon(true)
			watchThread.start()
		}

		override def postStop() {
			watchThread.interrupt()
		}

		def receive = LoggingReceive {
			case MonitorDir(path) =>
				watchServiceTask watchRecursively path
			case Created(file) =>  
				//e.g. forward or broadcast to other actors
			case Deleted(fileOrDir) =>
		}
	}

Few things to keep in mind: actor takes full responsibility of the `"WatchService"` thread lifecycle. Also see how it handles the `MonitorDir` message. However we don't monitor any directory from the beginning. This is done outside:

	val system = ActorSystem("WatchFsSystem")
	val fsActor = system.actorOf(Props[FileSystemActor], "fileSystem")
	fsActor ! MonitorDir(Paths get "/home/john")
	//...
	system.shutdown()

Obviously you can send any number of `MonitorDir` messages with different directories and all of them are monitored simultaneously - but you don't have to monitor subdirectories, this is done for you. Creating and deleting new file to smoke test our solution and apparently it works:

	received handled message MonitorDir(/home/john/tmp)
	received handled message Created(/home/john/tmp/test.txt)
	received handled message Deleted(/home/john/tmp/test.txt)

There is one interested piece of functionality we get for free. If we run this application in a cluster and configure one actor to only be created on one of the instances (see: [*Remote actors - discovering Akka*](http://nurkiewicz.blogspot.no/2012/11/remote-actors-discovering-akka.html) for thorough example how to configure remote actors), we can easily aggregate file system changes from multiple servers! Simply lookup remote ("singleton" across cluster) aggregate actor in `FileSystemActor` and forward events to it. Aforementioned article explains very similar architecutre so I won't go into too much detail. Enough to say, with this topology one can easily monitor multiple servers and collect change events on all of them.

---

So... we have a cool solution, let's look for a problem. In a single-node setup `FileSystemActor` provides nice abstraction over blocking `WatchService`. Other actors interested in file system changes can register in `FileSystemActor` and respond quickly to changes. In multi-node, cluster setup it works pretty much the same, but now we can easily control several nodes. One idea would be to replicate files over nodes.