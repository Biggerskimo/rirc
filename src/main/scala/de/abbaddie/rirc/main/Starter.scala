package de.abbaddie.rirc.main

import akka.actor.Props
import de.abbaddie.rirc.connector.IrcSocketConnector
import de.abbaddie.rirc.auth.MemoryAuthSystem

object Starter {
	def main(args: Array[String]) {
		// setup server object
		Server.actor = Server.actorSystem.actorOf(Props[ServerActor])

		// setup auth system
		Server.authSys = new MemoryAuthSystem

		// setup irc connector
		val ircc = new IrcSocketConnector
		ircc.start
	}
}
