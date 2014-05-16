package de.abbaddie.rirc.connector.irc

import scala.collection.mutable
import grizzled.slf4j.Logging
import de.abbaddie.rirc.main.Server

object IrcQueueHandler extends Logging {
	private var counter = 0
	private val counters = mutable.Map[IrcUser, Int]()
	private val limit = 10000

	def inc(user : IrcUser) = add(user, 1)

	def dec(user : IrcUser) = sub(user, 1)

	def add(user : IrcUser, count : Int) : Unit = synchronized {
		counters(user) += count
		counter += count
		//maybeKillSomeone()
	}

	def sub(user : IrcUser, count : Int) = add(user, -count)
	
	def rm(user : IrcUser) = synchronized { sub(user, counters(user)) }

	def kill(user : IrcUser) = synchronized {
		info("killing " + user + ", worth " + counters(user) + " of " + counter)
		user.killImmidiately("Nachrichtenpuffer voll!")
	}

	def maybeKillSomeone() = synchronized {
		if(!isOk) killSomeone()
	}

	def killSomeone() = synchronized {
		kill(counters.filter(_._1.authacc.fold(true)(!_.isOper)).maxBy(_._2)._1)
	}

	def touch(user : IrcUser) = synchronized { counters += (user -> 0)}

	def get = synchronized { counter }
	
	def getFast = counter

	def isOk = synchronized { counter <= limit }

	import concurrent.duration._
	implicit val dispatcher = Server.actorSystem.dispatcher
	Server.actorSystem.scheduler.schedule(1 second, 1 second) {
		val size = get
		if(size > 0) warn("buffer size: " + size)
	}
}
