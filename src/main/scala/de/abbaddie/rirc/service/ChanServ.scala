package de.abbaddie.rirc.service

import de.abbaddie.rirc.main._
import akka.actor.{Props, Actor, ActorRef}
import grizzled.slf4j.Logging
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import concurrent.Await
import de.abbaddie.rirc.main.Channel
import scala.Some
import com.typesafe.config.Config
import concurrent.duration._
import de.abbaddie.rirc.main.Message._

class ChanServ extends DefaultRircModule with RircAddon {
	def init() {
		val user = new ChanServUser(config)
		Server.events ! ConnectMessage(user)
	}
}

class ChanServUser(config : Config) extends User {
	def initActor(): ActorRef = Server.actorSystem.actorOf(Props(classOf[ChanServGeneralActor], this), name = "ChanServ")

	val nickname = config.getString("nickname")
	val username = config.getString("username")
	val realname = config.getString("realname")
	val hostname = config.getString("hostname")
}

class ChanServGeneralActor(val suser : User) extends Actor with Logging {
	override def preStart() {
		startup()
	}

	def receive = {
		case PrivateTextMessage(user, _, message) =>
			if(user.authacc.isEmpty || !user.isOper) {
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Oper-Rechte benötigt.")
			}
			else {
				message.split(" ") match {
					case Array("join", name) if !name.startsWith("#") =>
						Server.events ! PrivateNoticeMessage(suser, user, "Das ist kein gültiger Channel-Name!")
					case Array("join", name) if !Server.channels.contains(name) =>
						Server.events ! PrivateNoticeMessage(suser, user, "Der Channel existiert nicht!")
					case Array("join", name) if Server.channels(name).users.contains(suser) =>
						Server.events ! PrivateNoticeMessage(suser, user, "Schau doch mal auf die Userliste!")
					case Array("join", name) =>
						val channel = Server.channels(name)
						join(channel)
					case _ =>
						Server.events ! PrivateNoticeMessage(suser, user, "Geh bügeln!")
				}
			}

		case ConnectMessage(_) |
			 JoinMessage(_, _) =>
			// ignore

		case message =>
			error("Dropped message in ChanServGeneralActor: " + message + ", sent by " + context.sender)
	}

	def startup() {
		var channelCount = 0

		Server.channelProvider.registeredChannels.keys foreach { name =>
			Server.channels get name match {
				case Some(channel : Channel) if !channel.users.contains(suser) =>
					join(channel)
				case None =>
					val channel = new Channel(name)
					Server.events ! ChannelCreationMessage(channel, suser)
					join(channel)
			}
			channelCount += 1
		}

		info(channelCount + " channels loaded.")
	}

	def join(channel : Channel) {
		val actor = Server.actorSystem.actorOf(Props(classOf[ChanServChannelActor], suser, channel), name = "ChanServ" + channel.name.tail)

		Server.events.subscribe(actor, ChannelClassifier(channel))

		Server.events ! JoinMessage(channel, suser)
	}
}

class ChanServChannelActor(val suser : User, val channel : Channel) extends Actor with Logging {
	def receive = {
		case AuthSuccess(user, acc) if channel.users contains user =>
			checkUser(user, Some(acc))

		case JoinMessage(_, user) if user == suser =>
			resync()

		case JoinMessage(_, user) =>
			// delay check, some irc clients wouldnt show @/+ without this delay
			context.system.scheduler.scheduleOnce(1 second) {
				checkUser(user)
			}

		case PartMessage(_, user, _) if user == suser =>
			Server.events.unsubscribe(self, ChannelClassifier(channel))
			context.stop(self)

		case KickMessage(_, _, user) if user == suser =>
			Server.events ! JoinMessage(channel, user)

		case ServiceCommandMessage(_, user, "register", ownerName) =>
			if(user.authacc.isEmpty || !user.isOper)
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Oper-Rechte benötigt.")
			else if(channel.isRegistered)
				Server.events ! PrivateNoticeMessage(suser, user, "Der Channel ist bereits registriert.")
			else {
				resolveAccount(ownerName) match {
					case Some(account) =>
						Server.channelProvider.register(channel, account, user.authacc.get)
						resync()
						Server.events ! PrivateNoticeMessage(suser, user, "Der Channel wurde registriert.")
					case None =>
						Server.events ! PrivateNoticeMessage(suser, user, "Der Account zu " + ownerName + " wurde nicht gefunden.")
				}
			}

		case ServiceCommandMessage(_, user, "resync") =>
			if(!checkOp(user))
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
			else {
				resync()
				Server.events ! PrivateNoticeMessage(suser, user, "Der Privilegiencheck wurde ausgeführt.")
			}

		case ServiceCommandMessage(_, user, "users") =>
			Server.channelProvider.registeredChannels get channel.name match {
				case Some(desc) =>
					Server.events ! PrivateNoticeMessage(suser, user, "Owner: " + desc.owner)
					Server.events ! PrivateNoticeMessage(suser, user, "Ops: " + desc.ops.mkString(" "))
					Server.events ! PrivateNoticeMessage(suser, user, "Voices: " + desc.voices.mkString(" "))
				case None =>
					Server.events ! PrivateNoticeMessage(suser, user, "Der Channel ist nicht registriert!")
			}

		case ServiceCommandMessage(_, user, "addop", name) =>
			userChange(user, name, desc => desc.addOp , "Der User wurde zur Op-Liste hinzugefügt.")

		case ServiceCommandMessage(_, user, "addvoice", name) =>
			userChange(user, name, desc => desc.addVoice, "Der User wurde zur Voice-Liste hinzugefügt.")

		case ServiceCommandMessage(_, user, "rmop", name) =>
			userChange(user, name, desc => desc.rmOp, "Der User wurde aus der Op-Liste entfernt.")

		case ServiceCommandMessage(_, user, "rmvoice", name) =>
			userChange(user, name, desc => desc.rmVoice, "Der User wurde aus der Voice-Liste entfernt.")

		case ServiceCommandMessage(_, user, "god") =>
			Server.events ! PrivateNoticeMessage(suser, user, "God: Biggerskimo")

		case ServiceCommandMessage(_, user, "ping") =>
			Server.events ! PublicTextMessage(channel, suser, user.nickname + ": " + "Pong!")

		case ServiceCommandMessage(_, user, "8ball", rest @ _*) =>
			val msg = rest.flatMap(_.map(_.toInt)).sum % 4 match {
				case 0 => "Not a chance."
				case 1 => "In your dreams."
				case 2 => "Absolutely."
				case 3 => "Could be, could be."
			}
			Server.events ! PublicTextMessage(channel, suser, user.nickname + ": " + msg)

		case ServiceCommandMessage(_, user, "part") =>
			if(user.authacc.isEmpty || !user.isOper)
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Oper-Rechte benötigt.")
			else {
				Server.events ! PartMessage(channel, user, None)
			}

		case ServiceCommandMessage(_, user, "set", "topicmask", rest @_*) =>
			if(!checkOp(user))
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
			else {
				Server.channelProvider.registeredChannels.get(channel.name) match {
					case Some(desc) =>
						desc.setAdditional("topicmask", rest.mkString(" "))
					case None =>
						Server.events ! PrivateNoticeMessage(suser, user, "Der Channel ist nicht registriert!")
				}
			}

		case ServiceCommandMessage(_, user, "topic", rest @_*) =>
			if(!checkOp(user))
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
			else {
				val topicInner = rest.mkString(" ")
				Server.channelProvider.registeredChannels.get(channel.name) match {
					case Some(desc) if desc.getAdditional("topicmask").isDefined =>
						val topicMask = desc.getAdditional("topicmask").get
						val topic = topicMask.replace("*", topicInner)
						Server.events ! TopicChangeMessage(channel, user, channel.topic, topic)
					case Some(desc) =>
						Server.events ! TopicChangeMessage(channel, user, channel.topic, topicInner)
					case None =>
						Server.events ! PrivateNoticeMessage(suser, user, "Der Channel ist nicht registriert!")
				}
			}

		case ServiceCommandMessage(_, user, "invite", inviteds @_*) =>
			inviteds.foreach { invited => Server.users.get(invited) match {
					case Some(user2) if channel.users.contains(user2) =>
						Server.events ! PrivateNoticeMessage(suser, user, "Benutzer " + invited + " ist bereits im Channel.")
					case Some(user2) =>
						Server.events ! InvitationMessage(channel, user, user2)
						Server.events ! PrivateNoticeMessage(suser, user, "Benutzer " + invited + " eingeladen.")
					case None =>
						Server.events ! PrivateNoticeMessage(suser, user, "Benutzer " + invited + " nicht gefunden.")
				}
			}

		case ConnectMessage(_) |
			 QuitMessage(_, _) |
			 KickMessage(_, _, _) |
			 BanMessage(_, _, _) |
			 PartMessage(_, _, _) |
			 NickchangeMessage(_, _, _) |
			 PrivilegeChangeMessage(_, _, _, _, _) |
			 PublicTextMessage(_, _, _) |
			 TopicChangeMessage(_, _, _, _) =>

		case message =>
			error("Dropped message in ChanServChannelActor: " + message + ", sent by " + context.sender)
	}

	def userChange(user : User, name : String, todo : ChannelDescriptor => (String => Unit), message : String) {
		if(!checkOp(user))
			Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
		else {
			(Server.channelProvider.registeredChannels get channel.name, resolveAccount(name)) match {
				case (Some(desc), Some(account)) =>
					todo(desc)(account.id)
					checkUser(user)
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

	def checkOp(user : User) = UserUtil.checkOp(channel, user)

	def resync() {
		if(channel.isRegistered) {
			implicit val desc = Server.channelProvider.registeredChannels(channel.name)

			// presence of Service
			if(!channel.users.contains(suser)) {
				Server.events ! JoinMessage(channel, suser)
			}

			// iterate users
			channel.users foreach {case (user, info) => checkUserPart(user, info) }
		}
		if(!channel.users.contains(suser) || !channel.users(suser).isOp) {
			Server.events ! PrivilegeChangeMessage(channel, suser, suser, OP, SET)
		}
	}

	def setNone(user : User, info : ChannelUserInformation) {
		if(info.isOp)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, OP, UNSET)
		if(info.isVoice)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, VOICE, UNSET)
	}
	def setVoice(user : User, info : ChannelUserInformation) {
		if(info.isOp)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, OP, UNSET)
		if(!info.isVoice)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, VOICE, SET)
	}
	def setOp(user : User, info : ChannelUserInformation) {
		if(!info.isOp)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, OP, SET)
		if(info.isVoice)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, VOICE, UNSET)
	}

	def checkUser(user : User) {
		Server.channelProvider.registeredChannels get channel.name match {
			case Some(desc) =>
				checkUserPart(user, channel.users(user))(desc)
			case _ =>
		}
	}

	def checkUser(user : User, acc : Option[AuthAccount]) {
		Server.channelProvider.registeredChannels get channel.name match {
			case Some(desc) =>
				checkUserPart(user, acc, channel.users(user))(desc)
			case _ =>
		}
	}

	def checkUserPart(user2 : User, info2 : ChannelUserInformation)(implicit desc : ChannelDescriptor) {
		checkUserPart(user2, user2.authacc, info2)(desc)
	}

	def checkUserPart(user2 : User, acc : Option[AuthAccount], info2 : ChannelUserInformation)(implicit desc : ChannelDescriptor) {
		(user2, info2) match {
			case (user, info) if user == suser =>
				setOp(user, info)
			case (user, info) if acc.isEmpty =>
				setNone(user, info)
			case (user, info) if desc.owner == acc.get.id =>
				setOp(user, info)
			case (user, info) if desc.ops contains acc.get.id =>
				setOp(user, info)
			case (user, info) if desc.voices contains acc.get.id =>
				setVoice(user, info)
			case (user, info) =>
				setNone(user, info)
		}
	}
}