package de.abbaddie.rirc.main

import akka.actor.{ActorSystem, Props}
import de.abbaddie.rirc.connector.{Connector, IrcSocketConnector}
import de.abbaddie.rirc.service._
import com.typesafe.config.{ConfigValue, ConfigFactory}
import grizzled.slf4j.Logger
import de.abbaddie.jmunin.Munin
import scala.collection.JavaConverters._

object Starter {
	val logger = Logger[Starter.type]

	def main(args: Array[String]) {
		logger.info("Starting...")

		val config = ConfigFactory.load()

		// setup munin
		Munin("connected", Server.users.size)("title" -> "Verbundene Nutzer", "vlabel" -> "Nutzer")()
		Munin("events")("title" -> "Globale Events", "vlabel" -> "Events")("type" -> "DERIVE", "min" -> 0)

		// setup server object
		Server.actorSystem = ActorSystem("rirc-actors", config)
		Server.actor = Server.actorSystem.actorOf(Props[ServerActor])
		Server.systemUser = new SystemUser
		logger.info("Server object setup done.")

		// setup auth provider
		config.getConfigList("modules").asScala.toList.filter(_.getStringList("used-as").contains("auth")) match {
			case first :: Nil =>
				val module = Class.forName(first.getString("class")).newInstance().asInstanceOf[AuthProvider]

				Server.authProvider = module
				Server.authSys = Server.actorSystem.actorOf(Props[AuthSystem])
				logger.info("Auth system setup done.")
			case Nil =>
				logger.warn("Starting without auth module")
			case _ =>
				logger.error("Multiple auth modules found, aborting.")
				return
		}

		// setup channel provider
		config.getConfigList("modules").asScala.toList.filter(_.getStringList("used-as").contains("channels")) match {
			case first :: Nil =>
				val module = Class.forName(first.getString("class")).newInstance().asInstanceOf[ChannelProvider]

				Server.channelProvider = module
				logger.info("Channel provider setup done.")
			case Nil =>
				logger.error("Multiple channel provider found, aborting.")
				return
			case _ =>
				logger.error("Multiple auth modules found, aborting.")
				return
		}

		// setup channels
		ChannelHelper.startup()
		logger.info("Channel setup done.")

		// setup connectors
		config.getConfigList("modules").asScala.toList.filter(_.getStringList("used-as").contains("connector")).foreach { moduleConfig =>
				val module = Class.forName(moduleConfig.getString("class")).newInstance().asInstanceOf[Connector]
				module.start()
				logger.info("Connector " + moduleConfig.getString("class") + " started")
		}

		logger.info("Running.")
	}
}
