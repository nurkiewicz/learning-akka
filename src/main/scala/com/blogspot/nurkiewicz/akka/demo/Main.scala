package com.blogspot.nurkiewicz.akka.demo

import com.weiglewilczek.slf4s.Logging
import akka.actor.{ActorSystem, Props}
import util.concurrent.TimeUnit

/**
 * @author Tomasz Nurkiewicz
 * @since 15.04.12, 21:12
 */

object Main extends App with Logging {
	val system = ActorSystem("RandomOrgSystem")
	system.log.info("Started")
	val randomOrgBuffer = system.actorOf(Props[RandomOrgBuffer], "randomOrg")


	val random = new RandomOrgRandom(randomOrgBuffer)

	val scalaRandom = new scala.util.Random(random)

	for(_ <- 1 to 1000) {
		TimeUnit.MILLISECONDS.sleep(50 + (math.random * 50).toInt)
		val start = System.nanoTime()
		val rnd = random.nextInt(1000)
		val end = System.nanoTime()
		logger.info(((end - start) / 1000000.0).toString + ": " + rnd)
	}

	system.shutdown()

}
