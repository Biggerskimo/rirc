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
import scala.collection.JavaConverters._
import scala.util.matching.Regex

class ChanServ extends DefaultRircModule with RircAddon {
	def init() {
		val user = new ChanServUser(config)
		Server.events ! ConnectMessage(user)
	}
}

class ChanServUser(val config : Config) extends User {
	def initActor(): ActorRef = Server.actorSystem.actorOf(Props(classOf[ChanServGeneralActor], this), name = "ChanServ")

	val nickname = config.getString("nickname")
	val username = config.getString("username")
	val realname = config.getString("realname")
	val hostname = config.getString("hostname")
	
	var replaces = List[(Regex, String)]()
	
	config.getConfigList("replace").asScala.foreach { tuple =>
		val find = tuple.getString("find")
		val replacement = tuple.getString("replacement")
		val regex = find.r
		replaces ::= (regex -> replacement)
	}
}

class ChanServGeneralActor(val suser : User) extends Actor with Logging {
	override def preStart() {
		startup()
		Server.eventBus.subscribeServer(self)
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
					case Array(channelName, rest @ _*) if channelName.startsWith("#") =>
						Server.channels get channelName match {
							case Some(channel) =>
								Server.events ! ServiceRequest(channel, user, rest.mkString(" "))
							case None =>
								Server.events ! PrivateNoticeMessage(suser, user, "Der Channel existiert nicht!")
						}
					case _ =>
						Server.events ! PrivateNoticeMessage(suser, user, "Geh bügeln!")
				}
			}
			
		case AuthSuccess(user, account) =>
			Server.channelProvider.registeredChannels.values.foreach { case desc =>
				val setting = desc.getUserSetting(account, "autoinvite").getOrElse("0")
				if(setting == "1") {
					Server.events ! InvitationMessage(Server.channels(desc.name), suser, user)
				}
			}

		case ConnectMessage(_) |
			 JoinMessage(_, _) |
			 NickchangeMessage(_, _, _) |
			 QuitMessage(_, _) |
			 RegistrationSuccess(_, _) |
			 ChannelCreationMessage(_, _) |
			 ChannelCloseMessage(_) =>
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

class ChanServChannelActor(val suser : ChanServUser, val channel : Channel) extends Actor with Logging {
	implicit def desc = Server.channelProvider.registeredChannels(channel.name)

	def receive = {
		case AuthSuccess(user, acc) if channel.users contains user =>
			checkUser(user, Some(acc), channel.users(user), join = false, force = false)

		case JoinMessage(_, user) if user == suser =>
			resync()

		case JoinMessage(_, user) =>
			// delay check, some irc clients wouldnt show @/+ without this delay
			context.system.scheduler.scheduleOnce(1 second) {
				checkUser(user, join = true, force = false)
			}

		case PartMessage(_, user, _) if user == suser =>
			Server.events.unsubscribe(self, ChannelClassifier(channel))
			context.stop(self)

		case KickMessage(_, _, user) if user == suser =>
			Server.events ! JoinMessage(channel, user)

		case PublicTextMessage(_, user, message) if message.startsWith("!") =>
			processServiceRequest(user, message.tail)
			
		case ServiceRequest(_, user, message) =>
			processServiceRequest(user, message)

		case ConnectMessage(_) |
			 QuitMessage(_, _) |
			 KickMessage(_, _, _) |
			 BanMessage(_, _, _) |
			 PartMessage(_, _, _) |
			 NickchangeMessage(_, _, _) |
			 PrivilegeChangeMessage(_, _, _, _, _) |
			 PublicTextMessage(_, _, _) |
			 TopicChangeMessage(_, _, _, _) | 
			 RegistrationSuccess(_, _) |
			 ChannelModeChangeMessage(_, _, _) =>

		case message =>
			error("Dropped message in ChanServChannelActor: " + message + ", sent by " + context.sender)
	}
	
	def isManaged = Server.channelProvider.registeredChannels.contains(channel.name)
	
	def processServiceRequest(user : User, str: String) {
		val newStr = suser.replaces.foldLeft(str) {
			case (string, (regex, replacement)) =>
				regex.replaceAllIn(string, replacement)
		}
		if(!isManaged && !newStr.startsWith("register")) {
			Server.events ! PrivateNoticeMessage(suser, user, "Der Kanal ist nicht registriert!")
		}
		else {
			processServiceRequest(user)(newStr.split(" "))
		}
	}

	def processServiceRequest(user : User) : PartialFunction[Array[String], Unit] = {
		case Array("register", ownerName) =>
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

		case Array("resync") =>
			if(!checkOp(user))
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
			else {
				resync()
				Server.events ! PrivateNoticeMessage(suser, user, "Der Privilegiencheck wurde ausgeführt.")
			}

		case Array("users") =>
			Server.events ! PrivateNoticeMessage(suser, user, "Owner: " + desc.owner)
			Server.events ! PrivateNoticeMessage(suser, user, "Ops: " + desc.ops.mkString(" "))
			Server.events ! PrivateNoticeMessage(suser, user, "Voices: " + desc.voices.mkString(" "))

		case Array("addop", name) =>
			userChange(user, name, desc => desc.addOp , "Der User wurde zur Op-Liste hinzugefügt.")

		case Array("addvoice", name) =>
			userChange(user, name, desc => desc.addVoice, "Der User wurde zur Voice-Liste hinzugefügt.")

		case Array("rmop", name) =>
			userChange(user, name, desc => desc.rmOp, "Der User wurde aus der Op-Liste entfernt.")

		case Array("rmvoice", name) =>
			userChange(user, name, desc => desc.rmVoice, "Der User wurde aus der Voice-Liste entfernt.")

		case Array("god") =>
			Server.events ! PrivateNoticeMessage(suser, user, "God: Biggerskimo")

		case Array("ping") =>
			Server.events ! PublicTextMessage(channel, suser, user.nickname + ": " + "Pong!")

		case Array("8ball", rest @ _*) =>
			val msg = rest.flatMap(_.map(_.toInt)).sum % 4 match {
				case 0 => "Not a chance."
				case 1 => "In your dreams."
				case 2 => "Absolutely."
				case 3 => "Could be, could be."
			}
			Server.events ! PublicTextMessage(channel, suser, user.nickname + ": " + msg)

		case Array("part") =>
			if(user.authacc.isEmpty || !user.isOper)
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Oper-Rechte benötigt.")
			else {
				Server.events ! PartMessage(channel, suser, None)
			}

		case Array("set", "topicmask", rest @_*) =>
			if(!checkOp(user))
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
			else {
				desc.setAdditional("topicmask", rest.mkString(" "))
			}

		case Array("topic", rest @_*) =>
			if(!checkOp(user))
				Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
			else {
				val topicInner = rest.mkString(" ")
				if(desc.getAdditional("topicmask").isDefined) {
					val topicMask = desc.getAdditional("topicmask").getOrElse("*")
					val topic = topicMask.replace("*", topicInner)
					Server.events ! TopicChangeMessage(channel, user, channel.topic, topic)
				}
			}

		case Array("invite", inviteds @_*) =>
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

		case Array("op", names @ _*) =>
			names.foreach(privilegeChange(user, _, OP, SET))

		case Array("deop", names @ _*) =>
			names.foreach(privilegeChange(user, _, OP, UNSET))

		case Array("voice", names @ _*) =>
			names.foreach(privilegeChange(user, _, VOICE, SET))

		case Array("devoice", names @ _*) =>
			names.foreach(privilegeChange(user, _, VOICE, UNSET))

		case Array("up") =>
			checkUser(user, join = false, force = true)
			
		case Array("kick") =>
			if(checkOp(user)) {
				Server.events ! KickMessage(channel, suser, user)
			}

		case Array("down") =>
			if(checkOp(user)) {
				privilegeChange(user, user.nickname, OP, UNSET)
				privilegeChange(user, user.nickname, VOICE, UNSET)
			}
			
		case Array("uset", setting, rest @ _*) =>
			val settings = List("autoinvite", "info", "noautoop")
			if(!settings.contains(setting)) {
				Server.events ! PrivateNoticeMessage(suser, user, "Das ist keine gültige Einstellung.")
			}
			else {
				user.authacc match {
					case Some(authacc) =>
						setUserSetting(authacc, setting, rest.mkString(" "))
					case None =>
						Server.events ! PrivateNoticeMessage(suser, user, "Du bist nicht angemeldet.")
				}
			}
			
		case Array("help", _*) =>
			Server.events ! PrivateNoticeMessage(suser, user, "Siehe <https://biggerskimo.github.io/rirc/>")

		case arr =>
			error(s"illegal service request from $user: !" + arr.mkString(" "))
	}

	def privilegeChange(user : User, name : String, priv : Privilege, op : PrivilegeOperation) {
		if(!checkOp(user))
			Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
		else {
			Server.users.get(name) match {
				case Some(user2) if !channel.users.contains(user) =>
					Server.events ! PrivateNoticeMessage(suser, user, s"Ich kann hier keinen $name finden.")
				case Some(user2) =>
					Server.events ! PrivilegeChangeMessage(channel, user, user2, priv, op)
			}
		}
	}

	def userChange(user : User, name : String, todo : ChannelDescriptor => (String => Unit), message : String) {
		if(!checkOp(user))
			Server.events ! PrivateNoticeMessage(suser, user, "Es werden Op-Rechte benötigt.")
		else {
			resolveAccount(name) match {
				case Some(account) =>
					todo(desc)(account.id)
					checkUser(user, join = false, force = false)
					Server.events ! PrivateNoticeMessage(suser, user, message)
				case None =>
					Server.events ! PrivateNoticeMessage(suser, user, "Der Account zu " + name + " wurde nicht gefunden.")
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
			// presence of Service
			if(!channel.users.contains(suser)) {
				Server.events ! JoinMessage(channel, suser)
			}

			// iterate users
			channel.users foreach {case (user, info) => checkUser(user, info, join = false, force = false) }
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

	def checkUser(user : User, join : Boolean, force : Boolean) {
		checkUser(user, channel.users(user), join, force)
	}

	def checkUser(user2 : User, info2 : ChannelUserInformation, join : Boolean, force : Boolean) {
		checkUser(user2, user2.authacc, info2, join, force)
	}

	def checkUser(user2 : User, accOpt : Option[AuthAccount], info2 : ChannelUserInformation, join : Boolean, force : Boolean) {
		if(!isManaged) return
		
		(user2, info2, accOpt) match {
			case (user, info, _) if user == suser =>
				setOp(user, info)
			case (user, info, None) =>
				setNone(user, info)
			case (user, info, Some(acc)) if !force && getUserSetting(acc, "noautoop").getOrElse("0") == "1" =>
				// ignore
			case (user, info, Some(acc)) if desc.owner == acc.id =>
				setOp(user, info)
			case (user, info, Some(acc)) if desc.ops contains acc.id =>
				setOp(user, info)
			case (user, info, Some(acc)) if desc.voices contains acc.id =>
				setVoice(user, info)
			case (user, info, _) =>
				setNone(user, info)
		}
		if(join) {
			accOpt match {
				case Some(acc) =>
					getUserSetting(acc, "info") match {
						case Some(text) =>
							Server.events ! PublicTextMessage(channel, suser, s"[${user2.nickname}] $text")
						case None =>
					}
				case None =>
			}
		}
	}

	def getUserSetting(authacc : AuthAccount, key : String) = {
		desc.getUserSetting(authacc, key)
	}
	
	def setUserSetting(authacc : AuthAccount, key : String, value : String) {
		desc.setUserSetting(authacc, key, value)
	}
}
