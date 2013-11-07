package de.abbaddie.rirc.service

import de.abbaddie.rirc.main._
import scala.collection.JavaConverters._
import akka.actor.{Props, ActorRef, Actor}
import de.abbaddie.rirc.main.Message._

class SilentUsersAddon extends DefaultRircModule with RircAddon {
	def init() {
		config.getConfigList("users").asScala.toList.foreach { userConfig =>
			val user = new SilentUser(
				userConfig.getString("nickname"),
				userConfig.getString("username"),
				userConfig.getString("realname"),
				userConfig.getString("hostname"))
			Server.events ! ConnectMessage(user)
		}
	}
}

class SilentUser(val nickname : String, val username : String, val realname : String, val hostname : String) extends User {
	def initActor(): ActorRef = Server.actorSystem.actorOf(Props[SilentUserActor], name = uid.toString)

}

class SilentUserActor extends Actor {
	def receive = {
		case _ =>
	}
}
