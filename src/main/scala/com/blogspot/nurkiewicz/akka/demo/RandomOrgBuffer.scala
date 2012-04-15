package com.blogspot.nurkiewicz.akka.demo

import akka.actor._
import java.net.URL
import io.Source
import collection.mutable.Queue
import akka.pattern.ask
import akka.util.duration._
import akka.util.Timeout
import akka.dispatch.Await
import akka.event.LoggingReceive
import com.weiglewilczek.slf4s.Logging
import java.util.Random
import java.util.concurrent.TimeUnit

object Bootstrap extends App with Logging {
	val system = ActorSystem("Akka")
	system.log.info("Started")
	val randomOrgBuffer = system.actorOf(Props[RandomOrgBuffer], "randomOrg")


	val random = new RandomOrgRandom(randomOrgBuffer)

	val scalaRandom = new scala.util.Random(random)

	for(_ <- 1 to 100000) {
		TimeUnit.MILLISECONDS.sleep(50);
		val start = System.nanoTime()
		random.nextInt(1000)
		val end = System.nanoTime()
		logger.info(((end - start) / 1000000.0).toString)
	}

	system.shutdown()

}

case object RandomRequest

class RandomOrgBuffer extends Actor with ActorLogging {

	val BatchSize = 50

	val buffer = new Queue[Int]
	val backlog = new Queue[ActorRef]

	val randomOrgPoller = context.actorOf(Props[RandomOrgPoller], name="randomOrgPoller")

	override def preStart() {
		preFetchIfAlmostEmpty()
	}

	def receive = LoggingReceive {
		case RandomRequest =>
			preFetchIfAlmostEmpty()
			handleOrQueueInBacklog()
	}

	def handleOrQueueInBacklog() {
		if (buffer.isEmpty) {
			backlog += sender
		} else {
			sender ! buffer.dequeue()
		}
	}

	def receiveWhenWaiting = LoggingReceive {
		case RandomRequest =>
			handleOrQueueInBacklog()
		case RandomOrgResponse(randomNumbers) =>
			buffer ++= randomNumbers
			context.unbecome()
			while(!backlog.isEmpty && !buffer.isEmpty) {
				backlog.dequeue() ! buffer.dequeue()
			}
			preFetchIfAlmostEmpty()
	}

	private def preFetchIfAlmostEmpty() {
		if(buffer.size <= BatchSize / 4) {
			randomOrgPoller ! RandomOrgRequest(BatchSize)
			context become receiveWhenWaiting
		}
	}

}

case class RandomOrgRequest(batchSize: Int)

case class RandomOrgResponse(randomNumbers: List[Int])

class RandomOrgPoller extends Actor {
	protected def receive = LoggingReceive {
		case RandomOrgRequest(batchSize) =>
			val url = new URL("https://www.random.org/integers/?num=" + batchSize + "&min=0&max=65535&col=1&base=10&format=plain&rnd=new")
			val connection = url.openConnection()
			val stream = Source.fromInputStream(connection.getInputStream)
			sender ! RandomOrgResponse(stream.getLines().map(_.toInt).toList)
			stream.close()
	}
}

class RandomOrgRandom(randomOrgBuffer: ActorRef) extends Random {
	implicit val timeout = Timeout(1 minutes)

	override def next(bits: Int) = {
		if(bits <= 16) {
			random16Bits() & ((1 << bits) - 1)
		} else {
			(next(bits - 16) << 16) + random16Bits()
		}
	}

	private def random16Bits(): Int = {
		val future = randomOrgBuffer ? RandomRequest
		Await.result(future.mapTo[Int], 1 minute)
	}
}

