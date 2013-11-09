package de.abbaddie.rirc.connector.irc

import scala.collection.mutable
import grizzled.slf4j.Logging

object IrcQueueHandler extends Logging {
	private var counter = 0
	private val counters = mutable.Map[IrcUser, Int]()
	private var limit = 1000

	def inc(user : IrcUser) = add(user, 1)

	def dec(user : IrcUser) = sub(user, 1)

	def add(user : IrcUser, count : Int) : Unit = synchronized {
		counters(user) += count
		counter += count
		maybeKillSomeone()
	}

	def sub(user : IrcUser, count : Int) = add(user, -count)

	def kill(user : IrcUser) = synchronized {
		info("killing " + user + ", worth " + counters(user) + " of " + counter)
		sub(user, counters(user))
		user.killImmidiately("Nachrichtenpuffer voll!")
	}

	def maybeKillSomeone() = synchronized {
		if(!isOk) killSomeone()
	}

	def killSomeone() = synchronized {
		kill(counters.maxBy(_._2)._1)
	}

	def touch(user : IrcUser) = synchronized { counters += (user -> 0)}

	def get = synchronized { counter }

	def isOk = synchronized { counter <= limit }
}
