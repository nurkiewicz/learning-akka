package com.blogspot.nurkiewicz.akka.demo.fs

import java.nio.file._
import java.util.concurrent.TimeUnit
import akka.actor.{Props, ActorSystem}
import com.weiglewilczek.slf4s.Logging

/**
 * @author Tomasz Nurkiewicz
 * @since 3/30/13, 1:13 PM
 */
object WatchMain extends App with Logging {
	val system = ActorSystem("WatchFsSystem")
	system.log.info("Started")
	val fsActor = system.actorOf(Props[FileSystemActor], "fileSystem")
	fsActor ! MonitorDir(Paths get ".")
	TimeUnit.SECONDS.sleep(60)
	system.shutdown()
}

