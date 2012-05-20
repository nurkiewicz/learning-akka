package com.blogspot.nurkiewicz.akka.demo

import akka.event.LoggingReceive
import com.ning.http.client.{RequestBuilder, AsyncCompletionHandler, Response, AsyncHttpClient}
import akka.actor.Actor

/**
 * @author Tomasz Nurkiewicz
 * @since 20.05.12, 14:12
 */
case class FetchFromRandomOrg(batchSize: Int)

case class RandomOrgServerResponse(randomNumbers: List[Int])

class RandomOrgClient extends Actor {

	val client = new AsyncHttpClient();

	override def postStop() {
		client.close()
	}

	implicit def block2completionHandler[T](block: Response => T) = new AsyncCompletionHandler[T]() {
		def onCompleted(response: Response) = block(response)
	}

	def receive = LoggingReceive {
		case FetchFromRandomOrg(batchSize) =>
			val curSender = sender
			val request = new RequestBuilder().
					setUrl("https://www.random.org/integers/?num=" + batchSize + "&min=0&max=65535&col=1&base=10&format=plain&rnd=new").
					build();
			client.executeRequest(request, {
				response: Response =>
					val numbers = response.getResponseBody.lines.map(_.toInt).toList
					curSender ! RandomOrgServerResponse(numbers)
			})
	}
}