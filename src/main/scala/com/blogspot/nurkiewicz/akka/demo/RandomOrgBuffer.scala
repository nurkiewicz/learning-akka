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
import java.util.Random

case object RandomRequest

class RandomOrgBuffer extends Actor with ActorLogging {

	val BatchSize = 50

	val buffer = new Queue[Int]
	val backlog = new Queue[ActorRef]

	val randomOrgClient = context.actorOf(Props[RandomOrgClient], name="randomOrgClient")

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
		case RandomOrgServerResponse(randomNumbers) =>
			buffer ++= randomNumbers
			context.unbecome()
			while(!backlog.isEmpty && !buffer.isEmpty) {
				backlog.dequeue() ! buffer.dequeue()
			}
			preFetchIfAlmostEmpty()
	}

	private def preFetchIfAlmostEmpty() {
		if(buffer.size <= BatchSize / 4) {
			randomOrgClient ! FetchFromRandomOrg(BatchSize)
			context become receiveWhenWaiting
		}
	}

}

case class FetchFromRandomOrg(batchSize: Int)

case class RandomOrgServerResponse(randomNumbers: List[Int])

class RandomOrgClient extends Actor {
	protected def receive = LoggingReceive {
		case FetchFromRandomOrg(batchSize) =>
			val url = new URL("https://www.random.org/integers/?num=" + batchSize + "&min=0&max=65535&col=1&base=10&format=plain&rnd=new")
			val connection = url.openConnection()
			val stream = Source.fromInputStream(connection.getInputStream)
			sender ! RandomOrgServerResponse(stream.getLines().map(_.toInt).toList)
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

