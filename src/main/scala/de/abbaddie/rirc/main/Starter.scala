package de.abbaddie.rirc.main

import akka.actor.{ActorSystem, Props}
import de.abbaddie.rirc.connector.Connector
import de.abbaddie.rirc.service._
import com.typesafe.config.{ConfigObject, Config, ConfigFactory}
import grizzled.slf4j.Logger
import de.abbaddie.jmunin.Munin
import scala.collection.JavaConverters._
import collection.immutable.HashSet

trait RircModule {
	var config : Config
}

class DefaultRircModule extends RircModule {
	var config : Config = null
}

trait RircAddon extends RircModule {
	def init()
}

object Starter {
	val logger = Logger[Starter.type]

	def main(args: Array[String]) {
		logger.info("Starting...")

		// setup munin
		Munin("connected", Server.users.size)("title" -> "Verbundene Nutzer", "vlabel" -> "Nutzer")()
		Munin("events")("title" -> "Globale Events", "vlabel" -> "Events")("type" -> "DERIVE", "min" -> 0)

		// load config
		val config = ConfigFactory.load()
		val moduleObjs = config.getObject("modules")
		val moduleList = moduleObjs.keySet().asScala.toList.map(x => config.getConfig("modules." + x))

		// setup server object
		Server.config = config
		Server.actorSystem = ActorSystem("rirc-actors", config)
		Server.actor = Server.actorSystem.actorOf(Props[ServerActor])
		//Server.systemUser = new SystemUser
		Server.reservedNicks = HashSet(moduleList.filter(_.hasPath("reserved-nicks")).flatMap(_.getStringList("reserved-nicks").asScala) :_*)
		logger.info("Server: Reserved nicks are " + Server.reservedNicks.mkString("['", "', '", "']"))

		logger.info("Server: Basic setup done.")

		// setup auth provider
		moduleList.filter(_.getStringList("used-as").contains("auth")) match {
			case first :: Nil =>
				val module = Class.forName(first.getString("class")).newInstance().asInstanceOf[AuthProvider]
				module.config = first
				module.init()

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
		moduleList.filter(_.getStringList("used-as").contains("channels")) match {
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

		// setup addons
		moduleList.filter(_.getStringList("used-as").contains("addon")).foreach { moduleConfig =>
			val module = Class.forName(moduleConfig.getString("class")).newInstance().asInstanceOf[RircAddon]
			module.config = moduleConfig
			module.init()
		}

		// setup connectors
		moduleList.filter(_.getStringList("used-as").contains("connector")).foreach { moduleConfig =>
			val module = Class.forName(moduleConfig.getString("class")).newInstance().asInstanceOf[Connector]
			module.config = moduleConfig
			module.start()
			logger.info("Connector: " + moduleConfig.getString("class") + " started")
		}

		logger.info("Running.")
	}
}
