package de.abbaddie.rirc.main

import akka.actor.{Props, ActorRef, Actor}
import grizzled.slf4j.Logging
import org.joda.time.DateTime
import de.abbaddie.rirc.connector.irc.IrcConstants
import org.scala_tools.time.Imports._
import de.abbaddie.rirc.main.Message.QuitMessage
import scala.Some


// TODO decouple from irc
abstract class TimeoutManagedUser extends User {
	var dying = false
	var dies : DateTime = DateTime.now + 10.years
	var isDead = false

	val pinger : ActorRef = Server.actorSystem.actorOf(Props(classOf[TimeoutManagedUserPingActor], this), name = uid + "-pinger")
	implicit val dispatcher = Server.actorSystem.dispatcher
	Server.actorSystem.scheduler.schedule(IrcConstants.TIMEOUT_TICK, IrcConstants.TIMEOUT_TICK, pinger, Tick)

	def sendPing()
}

case object Tick

class TimeoutManagedUserPingActor(val user : TimeoutManagedUser) extends Actor with Logging {
	def receive = {
		case Tick if user.dying && user.dies < DateTime.now =>
			trace("killing " + user.nickname + ", inactive for " + ((DateTime.now.millis - user.lastActivity.millis) / 1000).round + "s")
 			Server.events ! QuitMessage(user, Some("Ping timeout"))
		case Tick if user.dying =>
			// wait ...
		case Tick if user.lastActivity < DateTime.now - IrcConstants.TIMEOUT.toMillis =>
			user.dies = DateTime.now + IrcConstants.TIMEOUT.toMillis
			user.dying = true
			trace("pinging " + user.nickname + ", inactive for " + ((DateTime.now.millis - user.lastActivity.millis) / 1000).round + "s")
			user.sendPing()
	}
}
