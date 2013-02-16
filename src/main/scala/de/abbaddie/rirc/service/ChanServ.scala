package de.abbaddie.rirc.service

import de.abbaddie.rirc.main._
import akka.actor.{Props, Actor, ActorRef}
import grizzled.slf4j.Logging
import de.abbaddie.rirc.connector.IrcConstants
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import concurrent.Await
import de.abbaddie.rirc.main.BanMessage
import de.abbaddie.rirc.main.AuthSuccess
import de.abbaddie.rirc.main.PublicTextMessage
import de.abbaddie.rirc.main.Channel
import scala.Some
import de.abbaddie.rirc.main.JoinMessage
import de.abbaddie.rirc.main.ServiceCommandMessage
import de.abbaddie.rirc.main.KickMessage
import de.abbaddie.rirc.main.PrivateNoticeMessage
import de.abbaddie.rirc.main.QuitMessage
import com.typesafe.config.Config

class ChanServ extends DefaultRircModule with RircAddon {
	def init() {
		val user = new ChanServUser(config)
		Server.events ! ConnectMessage(user)
	}
}

class ChanServUser(config : Config) extends User {
	def initActor(): ActorRef = Server.actorSystem.actorOf(Props(new ChanServActor(this)), name = "ChanServ")

	val nickname = config.getString("nickname")
	val username = config.getString("username")
	val realname = config.getString("realname")
	val hostname = config.getString("hostname")
}

class ChanServActor(val suser : User) extends Actor with Logging {
	override def preStart() {
		startup()
		Server.events.subscribeService(self)
	}

	def receive = {
		case AuthSuccess(user, acc) =>
			Server.channels.values filter(_.users contains user) foreach(checkUser(_, user, Some(acc)))

		case ServiceCommandMessage(channel, user, "register", ownerName) =>
			if(user.authacc.isEmpty || !user.isOper)
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Oper-Rechte benötigt.")
			else if(channel.isRegistered)
				Server.events ! PrivateNoticeMessage(suser, user, "Der Channel ist bereits registriert.")
			else {
				resolveAccount(ownerName) match {
					case Some(account) =>
						Server.channelProvider.register(channel, account, user.authacc.get)
						Server.events ! JoinMessage(channel, suser)
						resync(channel)
						Server.events ! PrivateNoticeMessage(suser, user, "Der Channel wurde registriert.")
					case None =>
						Server.events ! PrivateNoticeMessage(suser, user, "Der Account zu " + ownerName + " wurde nicht gefunden.")
				}
			}

		case ServiceCommandMessage(channel, user, "resync") =>
			if(!checkOp(channel, user))
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
			else {
				resync(channel)
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

		case JoinMessage(channel, user) =>
			checkUser(channel, user)

		case ConnectMessage(_) |
			 QuitMessage(_, _) |
			 KickMessage(_, _, _) |
			 BanMessage(_, _, _) |
			 BanMessage(_, _, _) =>


		case message =>
			error("Dropped message in ChanServUserActor: " + message + ", sent by " + context.sender)
	}

	def userChange(channel : Channel, user : User, name : String, todo : ChannelDescriptor => (String => Unit), message : String) {
		if(!checkOp(channel, user))
			Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
		else {
			(Server.channelProvider.registeredChannels get channel.name, resolveAccount(name)) match {
				case (Some(desc), Some(account)) =>
					todo(desc)(account.id)
					checkUser(channel, user)
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

	def resolveAccount(name : String) = {
		if(name startsWith "*") {
			val accName = name.tail
			Await.result(Server.authProvider.lookup(accName), timeout.duration) match {
				case acc : AuthAccount =>
					Some(acc)
				case _ => None
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

	def startup() {
		var channelCount = 0

		Server.channelProvider.registeredChannels.keys foreach { name =>
			Server.channels get name match {
				case Some(channel : Channel) if !channel.users.contains(suser) =>
					Server.events ! JoinMessage(channel, suser)
					channelCount += 1
				case None =>
					val channel = new Channel(name)
					Server.events ! ChannelCreationMessage(channel, suser)
					Server.events ! JoinMessage(channel, suser)
					channelCount += 1
			}
		}

		info(channelCount + " channels loaded.")
	}

	def resync(channel : Channel) {
		if(channel.isRegistered) {
			implicit val desc = Server.channelProvider.registeredChannels(channel.name)

			// presence of Service
			if(!channel.users.contains(suser)) {
				Server.events ! JoinMessage(channel, suser)
			}

			// iterate users
			channel.users foreach {case (user, info) => checkUserPart(channel, user, info) }
		}
	}

	def setNone(channel : Channel, user : User, info : ChannelUserInformation) {
		if(info.isOp)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, OP, UNSET)
		if(info.isVoice)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, VOICE, UNSET)
	}
	def setVoice(channel : Channel, user : User, info : ChannelUserInformation) {
		if(info.isOp)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, OP, UNSET)
		if(!info.isVoice)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, VOICE, SET)
	}
	def setOp(channel : Channel, user : User, info : ChannelUserInformation) {
		if(!info.isOp)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, OP, SET)
		if(info.isVoice)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, VOICE, UNSET)
	}

	def checkUser(channel : Channel, user : User) {
		Server.channelProvider.registeredChannels get channel.name match {
			case Some(desc) =>
				checkUserPart(channel, user, channel.users(user))(desc)
			case _ =>
		}
	}

	def checkUser(channel : Channel, user : User, acc : Option[AuthAccount]) {
		Server.channelProvider.registeredChannels get channel.name match {
			case Some(desc) =>
				checkUserPart(channel, user, acc, channel.users(user))(desc)
			case _ =>
		}
	}

	def checkUserPart(channel : Channel, user2 : User, info2 : ChannelUserInformation)(implicit desc : ChannelDescriptor) {
		checkUserPart(channel, user2, user2.authacc, info2)(desc)
	}

	def checkUserPart(channel : Channel, user2 : User, acc : Option[AuthAccount], info2 : ChannelUserInformation)(implicit desc : ChannelDescriptor) {
		(user2, info2) match {
			case (user, info) if user == suser =>
				setOp(channel, user, info)
			case (user, info) if acc.isEmpty =>
				setNone(channel, user, info)
			case (user, info) if desc.owner == acc.get.id =>
				setOp(channel, user, info)
			case (user, info) if desc.ops contains acc.get.id =>
				setOp(channel, user, info)
			case (user, info) if desc.voices contains acc.get.id =>
				setVoice(channel, user, info)
			case (user, info) =>
				setNone(channel, user, info)
		}
	}
}