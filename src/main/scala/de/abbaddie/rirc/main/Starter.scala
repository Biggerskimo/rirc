package de.abbaddie.rirc.main

import akka.actor.{ActorSystem, Props}
import de.abbaddie.rirc.connector.Connector
import de.abbaddie.rirc.service._
import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logger
import de.abbaddie.jmunin.Munin
import scala.collection.JavaConverters._

trait RircModule {
	var config : Config
}

class DefaultRircModule extends RircModule {
	var config : Config = null
}

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
		logger.info("Server: Basic setup done.")

		// setup auth provider
		config.getConfigList("modules").asScala.toList.filter(_.getStringList("used-as").contains("auth")) match {
			case first :: Nil =>
				val module = Class.forName(first.getString("class")).newInstance().asInstanceOf[AuthProvider]
				module.config = first

				Server.authProvider = module
				Server.authSys = Server.actorSystem.actorOf(Props[AuthSystem])
				logger.info("Auth: Using " + first.getString("class") + " as AuthProvider")
			case Nil =>
				logger.warn("Auth: No AuthProvider configured")
			case _ =>
				logger.error("Auth: Multiple AuthProviders configured")
				return
		}

		// setup channel provider
		config.getConfigList("modules").asScala.toList.filter(_.getStringList("used-as").contains("channels")) match {
			case first :: Nil =>
				val module = Class.forName(first.getString("class")).newInstance().asInstanceOf[ChannelProvider]
				module.config = first

				Server.channelProvider = module
				logger.info("Channel: Using " + first.getString("class") + " as ChannelProvider")
			case Nil =>
				logger.error("Channel: No ChannelProviders configured")
				return
			case _ =>
				logger.error("Channel: Multiple ChannelProviders configured")
				return
		}

		// setup channels
		ChannelHelper.startup()
		logger.info("Channel: All channels loaded.")

		// setup connectors
		config.getConfigList("modules").asScala.toList.filter(_.getStringList("used-as").contains("connector")).foreach { moduleConfig =>
				val module = Class.forName(moduleConfig.getString("class")).newInstance().asInstanceOf[Connector]
				module.config = moduleConfig
				module.start()
				logger.info("Connector: " + moduleConfig.getString("class") + " started")
		}

		logger.info("Running.")
	}
}
