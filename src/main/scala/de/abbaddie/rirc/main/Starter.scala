package de.abbaddie.rirc.main

import akka.actor.Props
import de.abbaddie.rirc.connector.IrcSocketConnector

object Starter {
	def main(args: Array[String]) {
		// setup irc connector
		val ircc = new IrcSocketConnector
		ircc start

		// setup server object
		Server.actor = Server.actorSystem.actorOf(Props[ServerActor])
	}
}
