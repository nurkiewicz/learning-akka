package com.blogspot.nurkiewicz.akka.demo

import akka.event.LoggingReceive
import com.ning.http.client.{AsyncCompletionHandler, Response, AsyncHttpClient}
import akka.actor.{ActorRef, Actor}
import collection.mutable

/**
 * @author Tomasz Nurkiewicz
 * @since 20.05.12, 14:12
 */
case class FetchFromRandomOrg(batchSize: Int)

case class RandomOrgServerResponse(randomNumbers: List[Int])

class RandomOrgClient extends Actor {

	val client = new AsyncHttpClient()
	val waitingForReply = new mutable.Queue[(ActorRef, Int)]

	override def postStop() {
		client.close()
	}

	implicit def block2completionHandler[T](block: Response => T) = new AsyncCompletionHandler[T]() {
		def onCompleted(response: Response) = block(response)
	}

	def receive = LoggingReceive {
		case FetchFromRandomOrg(batchSize) =>
			waitingForReply += (sender -> batchSize)
			if (waitingForReply.tail.isEmpty) {
				sendHttpRequest(batchSize)
			}
		case response: RandomOrgServerResponse =>
			waitingForReply.dequeue()._1 ! response
			if (!waitingForReply.isEmpty) {
				sendHttpRequest(waitingForReply.front._2)
			}
	}

	private def sendHttpRequest(batchSize: Int) {
		val url = "https://www.random.org/integers/?num=" + batchSize + "&min=0&max=65535&col=1&base=10&format=plain&rnd=new"
		client.prepareGet(url).execute {
			response: Response =>
				val numbers = response.getResponseBody.lines.map(_.toInt).toList
				self ! RandomOrgServerResponse(numbers)
		}
	}
}
