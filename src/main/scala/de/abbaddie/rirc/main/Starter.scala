package de.abbaddie.rirc.main

import akka.actor.{ActorSystem, Props}
import de.abbaddie.rirc.connector.IrcSocketConnector
import de.abbaddie.rirc.service._
import com.typesafe.config.ConfigFactory
import grizzled.slf4j.Logger
import de.abbaddie.jmunin.Munin

object Starter {
	val logger = Logger[Starter.type]

	def main(args: Array[String]) {
		logger.info("Starting...")

		// setup munin
		Munin("connected", Server.users.size)("title" -> "Verbundene Nutzer", "vlabel" -> "Nutzer")()
		Munin("events")("title" -> "Globale Events", "vlabel" -> "Events")("type" -> "DERIVE", "min" -> 0)

		// setup server object
		val config = ConfigFactory.load()
		Server.actorSystem = ActorSystem("rirc-actors", config)
		Server.actor = Server.actorSystem.actorOf(Props[ServerActor])
		Server.systemUser = new SystemUser
		logger.info("Server object setup done.")

		// setup auth provider
		Server.authProvider = new Wots2AuthProvider
		Server.authSys = Server.actorSystem.actorOf(Props[AuthSystem])
		logger.info("Auth system setup done.")

		// setup channel provider
		Server.channelProvider = new YamlFileChannelProvider

		// setup channels
		ChannelHelper.startup()
		logger.info("Channel setup done.")

		// setup irc connector
		val ircc = new IrcSocketConnector
		ircc.start()
		logger.info("IRC-Connector started.")

		logger.info("Running.")
	}
}
