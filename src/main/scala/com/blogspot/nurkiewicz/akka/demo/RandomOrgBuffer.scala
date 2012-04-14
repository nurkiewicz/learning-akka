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
import java.util.concurrent.TimeUnit
import com.weiglewilczek.slf4s.Logging
import scala.collection.JavaConversions._
import java.util.{ArrayList, Collections, Random}

object Bootstrap extends App with Logging {
	val system = ActorSystem("Akka")
	system.log.info("Started")
	val randomOrgBuffer = system.actorOf(Props[RandomOrgBuffer], "randomOrg")


	val random = new RandomOrgRandom(randomOrgBuffer)

	val scalaRandom = new scala.util.Random(random)

	println(scalaRandom.shuffle((1 to 10).toList))

	system.shutdown()

}

case object RandomRequest

class RandomOrgBuffer extends Actor with ActorLogging {

	val buffer = new Queue[Int]

	val userAgent = Option(System.getProperty("email"))

	def receive = LoggingReceive {
		case RandomRequest =>
			if(buffer.isEmpty) {
				buffer ++= fetchRandomNumbers(50)
			}
			sender ! buffer.dequeue()
	}

	def fetchRandomNumbers(count: Int) = {
		val url = new URL("https://www.random.org/integers/?num=" + count + "&min=0&max=65535&col=1&base=10&format=plain&rnd=new")
		val connection = url.openConnection()
		val stream = Source.fromInputStream(connection.getInputStream)
		val randomNumbers = stream.getLines().map(_.toInt).toList
		stream.close()
		randomNumbers
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

