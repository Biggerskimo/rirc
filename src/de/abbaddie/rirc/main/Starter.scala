package de.abbaddie.rirc.main

import akka.actor.Props
import de.abbaddie.rirc.connector.IrcSocketConnector

object Starter {
	def main(args: Array[String]) {
		val ircc = Server.actorSystem.actorOf(Props[IrcSocketConnector])
	}
}
