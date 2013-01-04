package de.abbaddie.rirc.service

import akka.actor.{Props, Actor, ActorRef}
import java.net.InetSocketAddress
import de.abbaddie.rirc.service._
import grizzled.slf4j.Logging
import de.abbaddie.rirc.main._
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import concurrent.Await
import de.abbaddie.rirc.main.Channel
import scala.Some
import de.abbaddie.rirc.main.JoinMessage

class SystemUser extends User {
	def initActor(): ActorRef = Server.actorSystem.actorOf(Props[SystemUserActor], name = "system")

	nickname = "Eierkopf"
	username = "eierkopf"
	realname = "Annie Mann"
	address = new InetSocketAddress("localhost", 0)
	override val hostname = "localhost"
}

class SystemUserActor extends Actor with Logging {
	def suser = Server.systemUser

	def receive = {
		case AuthSuccess(user, acc) =>
			Server.channels.values filter(_.users contains user) foreach(ChannelHelper.checkUser(_, user, Some(acc)))

		case ServiceCommandMessage(channel, user, "register", ownerName) =>
			if(user.authacc.isEmpty || !user.isOper)
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Oper-Rechte benötigt.")
			else if(channel.isRegistered)
				Server.events ! PrivateNoticeMessage(suser, user, "Der Channel ist bereits registriert.")
			else {
				resolveAccount(ownerName) match {
					case Some(account) =>
						Server.channelProvider.register(channel, account, user.authacc.get)
						Server.events ! JoinMessage(channel, Server.systemUser)
						ChannelHelper.resync(channel)
						Server.events ! PrivateNoticeMessage(suser, user, "Der Channel wurde registriert.")
					case None =>
						Server.events ! PrivateNoticeMessage(suser, user, "Der Account zu " + ownerName + " wurde nicht gefunden.")
				}
			}

		case ServiceCommandMessage(channel, user, "resync") =>
			if(!checkOp(channel, user))
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
			else {
				ChannelHelper.resync(channel)
				Server.events ! PrivateNoticeMessage(suser, user, "Der Privilegiencheck wurde ausgeführt.")
			}

		case message =>
			error("Dropped message in SystemUserActor: " + message + ", sent by " + context.sender)
	}

	implicit val timeout = Timeout(5, TimeUnit.SECONDS)
	implicit val actorSystem = Server.actorSystem.dispatcher

	val resolveAccount = (name : String) => {
		if(name startsWith "*") {
			val accName = name.tail
			Await.result(Server.authProvider.lookup(accName), timeout.duration) match {
				case Some(acc) =>
					Some(acc)
				case None => None
			}
		}
		else {
			Server.users get name match {
				case Some(user) => user.authacc
				case None => None
			}
		}
	}

	val checkOp = (channel : Channel, user : User) => {
		if(user.isOper) true
		else channel.users get user match {
			case Some(info) => info.isOp
			case None => false
		}
	}
}