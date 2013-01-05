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
import de.abbaddie.rirc.connector.IrcConstants

class SystemUser extends User {
	def initActor(): ActorRef = Server.actorSystem.actorOf(Props[SystemUserActor], name = "system")

	nickname = "Bug"
	username = "buggy"
	realname = "Herr Sumsemann"
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

		case ServiceCommandMessage(channel, user, "users") =>
			Server.channelProvider.registeredChannels get channel.name match {
				case Some(desc) =>
					Server.events ! PrivateNoticeMessage(suser, user, "Owner: " + desc.owner)
					Server.events ! PrivateNoticeMessage(suser, user, "Ops: " + desc.ops.mkString(" "))
					Server.events ! PrivateNoticeMessage(suser, user, "Voices: " + desc.voices.mkString(" "))
				case None =>
					Server.events ! PrivateNoticeMessage(suser, user, "Der Channel ist nicht registriert!")
			}

		case ServiceCommandMessage(channel, user, "addop", name) =>
			userChange(channel, user, name, desc => desc addOp _, "Der User wurde zur Op-Liste hinzugefügt.")

		case ServiceCommandMessage(channel, user, "addvoice", name) =>
			userChange(channel, user, name, desc => desc addVoice _, "Der User wurde zur Voice-Liste hinzugefügt.")

		case ServiceCommandMessage(channel, user, "rmop", name) =>
			userChange(channel, user, name, desc => desc rmOp _, "Der User wurde aus der Op-Liste entfernt.")

		case ServiceCommandMessage(channel, user, "rmvoice", name) =>
			userChange(channel, user, name, desc => desc rmVoice _, "Der User wurde aus der Voice-Liste entfernt.")

		case ServiceCommandMessage(channel, user, "god") =>
			Server.events ! PrivateNoticeMessage(suser, user, "God: " + IrcConstants.OWNER)

		case ServiceCommandMessage(channel, user, "ping") =>
			Server.events ! PublicTextMessage(channel, suser, user.nickname + ": " + "Pong!")

		case ServiceCommandMessage(channel, user, "8ball", rest @ _*) =>
			val msg = rest.flatMap(_.map(_.toInt)).sum % 4 match {
				case 0 => "Not a chance."
				case 1 => "In your dreams."
				case 2 => "Absolutely."
				case 3 => "Could be, could be."
			}
			Server.events ! PublicTextMessage(channel, suser, user.nickname + ": " + msg)

		case JoinMessage(_, _) |
			QuitMessage(_, _) =>


		case message =>
			error("Dropped message in SystemUserActor: " + message + ", sent by " + context.sender)
	}

	def userChange(channel : Channel, user : User, name : String, todo : ChannelDescriptor => (String => Unit), message : String) {
		if(!checkOp(channel, user))
			Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
		else {
			(Server.channelProvider.registeredChannels get channel.name, resolveAccount(name)) match {
				case (Some(desc), Some(account)) =>
					todo(desc)(account.id)
					ChannelHelper.checkUser(channel, user)
					Server.events ! PrivateNoticeMessage(suser, user, message)
				case (Some(desc), None) =>
					Server.events ! PrivateNoticeMessage(suser, user, "Der Account zu " + name + " wurde nicht gefunden.")
				case (None, _) =>
					Server.events ! PrivateNoticeMessage(suser, user, "Der Channel ist nicht registriert!")
			}
		}
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

	def checkOp(channel : Channel, user : User) = UserUtil.checkOp(channel, user)
}