package com.blogspot.nurkiewicz.akka.demo

import com.weiglewilczek.slf4s.Logging
import java.util.concurrent.TimeUnit
import akka.actor.{ActorSystem, Props}

/**
 * @author Tomasz Nurkiewicz
 * @since 15.04.12, 21:12
 */

object Main extends App with Logging {
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
