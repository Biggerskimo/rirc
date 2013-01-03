package de.abbaddie.rirc.service

import akka.actor.{Props, Actor, ActorRef}
import java.net.InetSocketAddress
import de.abbaddie.rirc.service._
import grizzled.slf4j.Logging
import de.abbaddie.rirc.main.{Server, User}

class SystemUser extends User {
	def initActor(): ActorRef = Server.actorSystem.actorOf(Props[SystemUserActor], name = "system")

	nickname = "Eierkopf"
	username = "eierkopf"
	realname = "Annie Mann"
	address = new InetSocketAddress("localhost", 0)
}

class SystemUserActor extends Actor with Logging {
	def receive = {
		case AuthSuccess(user, _) =>
		case message =>
			error("Dropped message in SystemUserActor: " + message + ", sent by " + context.sender)
	}
}