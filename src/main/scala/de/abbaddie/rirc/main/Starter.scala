package de.abbaddie.rirc.main

import akka.actor.{ActorSystem, Props}
import de.abbaddie.rirc.connector.IrcSocketConnector
import de.abbaddie.rirc.service._
import com.typesafe.config.ConfigFactory

object Starter {
	def main(args: Array[String]) {
		// setup server object
		val config = ConfigFactory.load()
		Server.actorSystem = ActorSystem("rirc-actors", config)
		Server.actor = Server.actorSystem.actorOf(Props[ServerActor])
		Server.systemUser = new SystemUser

		// setup auth provider
		Server.authProvider = new Wots2AuthProvider
		Server.authSys = Server.actorSystem.actorOf(Props[AuthSystem])

		// setup channel provider
		Server.channelProvider = new YamlFileChannelProvider

		// setup channels
		ChannelHelper.startup()

		// setup irc connector
		val ircc = new IrcSocketConnector
		ircc.start
	}
}
