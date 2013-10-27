package de.abbaddie.rirc.connector

import akka.actor._
import akka.pattern.ask
import de.abbaddie.rirc.main._
import org.jboss.netty.channel.{Channel => NettyChannel}
import grizzled.slf4j.Logging
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import concurrent.Await
import collection.mutable
import java.net.InetSocketAddress
import org.joda.time.DateTime
import org.scala_tools.time.Imports._
import de.abbaddie.rirc.Munin
import scala.Some
import de.abbaddie.rirc.main._
import akka.actor.SupervisorStrategy.Resume

class IrcUser(val channel : NettyChannel, val address : InetSocketAddress) extends User {
	def initActor() = Server.actorSystem.actorOf(Props(new IrcUserSystemActor(IrcUser.this)), name = uid.toString)

	var ds : ActorRef = null
	var us : ActorRef = null
	var pinger : ActorRef = null
	var dying = false
	var dies : DateTime = DateTime.now + 10.years
	var isDead = false

	override def isSystemUser = false

	var nickname = IrcConstants.UNASSIGNED_NICK
	var username = IrcConstants.UNASSIGNED_USERNAME
	var realname = IrcConstants.UNASSIGNED_REALNAME
	val hostname = address.getHostName

	// ensure ds + us
	implicit val timeout = Timeout(1, TimeUnit.SECONDS)
	Await.result(actor ? InitDummy, timeout.duration)
}

case object InitDummy
case object Tick

class IrcUserSystemActor(val user : IrcUser) extends Actor with Logging {
	override val supervisorStrategy = OneForOneStrategy() {
		case ex: Exception => Resume
	}

	def receive = {
		case ConnectMessage(_) => // it's me!
			user.ds ! RPL_WELCOME()
			user.ds ! RPL_YOURHOST()
			user.ds ! RPL_CREATED()
			user.ds ! RPL_MYINFO()
			user.ds ! RPL_ISUPPORT()
			user.ds ! RPL_LUSERCLIENT()
			user.ds ! RPL_LUSEROP()
			user.ds ! RPL_LUSERCHANNELS()
			user.ds ! RPL_LUSERME()
			user.ds ! ERR_NOMOTD()

		case JoinMessage(channel, joiner) =>
			Server.eventBus.subscribe(self, ChannelClassifier(channel))
			user.ds ! MSG_JOIN(channel, joiner)
			if (user == joiner) {
				channel.topic match {
					case Some(topic) =>
						user.ds ! RPL_TOPIC(channel)
					case None =>
						// no message here
				}
				user.ds ! RPL_NAMEREPLY(channel)
				user.ds ! RPL_ENDOFNAMES(channel)
			}

		case PublicTextMessage(channel, sender, message) =>
			if(sender != user)
				user.ds ! new MSG_PRIVMSG(channel, sender, message)

		case PublicNoticeMessage(channel, sender, message) =>
			if(sender != user)
				user.ds ! new MSG_NOTICE(channel, sender, message)

		case PrivateTextMessage(sender, _, message) =>
			user.ds ! new MSG_PRIVMSG(user, sender, message)

		case PrivateNoticeMessage(sender, _, message) =>
			user.ds ! new MSG_NOTICE(user, sender, message)

		case PartMessage(channel, sender, message) =>
			if(user == sender)
				Server.eventBus.unsubscribe(self, ChannelClassifier(channel))
			user.ds ! MSG_PART(channel, sender, message)

		case QuitMessage(sender, _) if sender == user =>
			user.isDead = true
			user.ds ! PoisonPill
			user.us ! PoisonPill
			user.pinger ! PoisonPill
			Server.eventBus.unsubscribe(self)

		case QuitMessage(sender, message) =>
			user.ds ! MSG_QUIT(sender, message)

		case NickchangeMessage(sender, oldNick, newNick) =>
			if(sender == user)
				user.nickname = newNick
			user.ds ! MSG_NICK(user, oldNick, newNick)

		case TopicChangeMessage(channel, sender, oldTopic, newTopic) =>
			user.ds ! MSG_TOPIC(channel, sender, newTopic)

		case PrivilegeChangeMessage(channel, sender, target, priv, op) =>
			val flag = if(op == SET) "+" else "-"
			val char = if(priv == OP) "o" else "v"
			user.ds ! MSG_MODE(channel, sender, flag + char, target.nickname)

		case AuthSuccess(_, account) =>
			user.ds ! SVC_AUTHSUCCESS()
			user.authacc = Some(account)
			user.isOper = account.isOper

		case AuthFailure(_, _, message) =>
			user.ds ! SVC_AUTHFAILURE(message)

		case RegistrationSuccess(_, account) =>
			user.ds ! SVC_REGISTRATIONSUCCESS()
			user.authacc = Some(account)
			user.isOper = account.isOper

		case RegistrationFailure(_, _, message) =>
			user.ds ! SVC_REGISTRATIONFAILURE(message)

		case ChannelModeChangeMessage(channel, sender, INVITE_ONLY(yes)) =>
			val flag = if(yes) "+" else "-"
			user.ds ! MSG_MODE(channel, sender, flag + "i")

		case ChannelModeChangeMessage(channel, sender, PROTECTION(Some(passwd))) =>
			user.ds ! MSG_MODE(channel, sender, "+k", passwd)

		case ChannelModeChangeMessage(channel, sender, PROTECTION(None)) =>
			user.ds ! MSG_MODE(channel, sender, "-k")

		case InvitationMessage(channel, inviter, _) =>
			user.ds ! MSG_INVITE(channel, inviter)

		case KickMessage(channel, kicker, kicked) =>
			if(user == sender)
				Server.eventBus.unsubscribe(self, ChannelClassifier(channel))
			user.ds ! MSG_KICK(channel, kicker, kicked)

		case BanMessage(channel, sender, mask) =>
			user.ds ! MSG_MODE(channel, sender, "+b", mask)

		case UnbanMessage(channel, sender, mask) =>
			user.ds ! MSG_MODE(channel, sender, "-b", mask)

		case ServiceCommandMessage(_, _, _, ignore @ _*) =>

		case ChannelCloseMessage(_) =>

		case InitDummy =>
			user.ds = context.actorOf(Props(new IrcUserDownstreamActor(user, user.channel)), name = "ds")
			user.us = context.actorOf(Props(new IrcUserUpstreamActor(user)), name = "us")
			user.pinger = context.actorOf(Props(new IrcUserPingActor(user)), name = "ping")
			implicit val dispatcher = context.system.dispatcher
			context.system.scheduler.schedule(IrcConstants.TIMEOUT_TICK, IrcConstants.TIMEOUT_TICK, user.pinger, Tick)
			sender ! InitDummy

		case message : Any =>
			error("Dropped message in IrcUserSystemActor for " + user.nickname + ": " + message + ", sent by " + context.sender)
	}
}

class IrcUserUpstreamActor(val user : IrcUser) extends Actor with Logging {
	// registration part
	var nickSet = false
	var loggedIn = false

	def receive = receiveStart orElse receiveAux

	def receiveAux : Receive = {
		case IrcChannelError(msg) if !user.isDead =>
			Server.events ! QuitMessage(user, Some("Verbindungsfehler: " + msg))
		case IrcChannelError(msg) =>
			// ignore
		case line : IrcIncomingLine =>
			info("Dropped incoming line from " + user.nickname + ": " + line)
		case _ =>
			// ignore
	}

	def receiveStart : Receive = {
		case IrcIncomingLine("NICK", nickname) =>
			if(Server.userNicks.contains(Server.nickToLowerCase(nickname)))
				user.ds ! ERR_NICKNAMEINUSE(nickname)
			else if(!Server.isValidNick(nickname))
				user.ds ! ERR_ERRONEUSNICKNAME(nickname)
			else {
				user.nickname = nickname
				nickSet = true
				checkRegistration()
			}
		case IrcIncomingLine("USER", username, _, _, realname) =>
			user.username = username
			user.realname = realname
			loggedIn = true
			checkRegistration()
		case _ =>
			user.ds ! ERR_NOTREGISTERED()
	}

	def checkRegistration() {
		if(nickSet && loggedIn) {
			Server.events ! ConnectMessage(user)
			context.become(receiveUsual orElse receiveAux)
		}
	}


	// "usual" part
	def receiveUsual : Receive = {
		case IrcIncomingLine("PING", no : String) =>
			user.ds ! new IrcSimpleResponse("PONG", no)

		case IrcIncomingLine("WHO", mask, _*) =>
			// TODO
			if(mask == user.nickname)
				user.ds ! RPL_WHOREPLY(user)
			user.ds ! RPL_ENDOFWHO()

		case IrcIncomingLine("JOIN", names, extra @ _*) =>
			val passwds = extra.headOption.getOrElse("")

			names.split(",").zipAll(passwds.split(",").map(Some(_)), "", None).foreach { case(name, passwd) =>
				if(!name.startsWith("#"))
					user.ds ! ERR_NOSUCHCHANNEL(name)
				else {
					var channel : Channel = null

					Server.channels get name match {
						case Some(channel2) =>
							channel = channel2
						case None =>
							channel = new Channel(name)
							Server.eventBus.publish(ChannelCreationMessage(channel, user))
					}

					if(!UserUtil.checkInviteOnly(channel, user)) {
						user.ds ! ERR_INVITEONLYCHAN(channel)
					}
					else if(!UserUtil.checkPasswordProtection(channel, user, passwd)) {
						user.ds ! ERR_BADCHANNELKEY(channel)
					}
					else if(!UserUtil.checkBanned(channel, user)) {
						user.ds ! ERR_BANNEDFROMCHAN(channel)
					}
					else {
						Server.events ! JoinMessage(channel, user)
					}
				}
			}

		case IrcIncomingLine("MODE", name) =>
			Server.targets get name match {
				case Some(channel : Channel) =>
					user.ds ! RPL_CHANNELMODEIS(channel)
					user.ds ! RPL_CREATIONTIME(channel)
				case Some(user2 : User) if user != user2 =>
					user.ds ! ERR_USERSDONTMATCH()
				case Some(user2 : User) =>
					user.ds ! RPL_UMODEIS("i")
				case None =>
					user.ds ! ERR_NOSUCHCHANNEL(name)
			}

		case IrcIncomingLine("MODE", name, "+b") =>
			Server.channels get name match {
				case Some(channel) if !channel.users.contains(user) =>
					user.ds ! ERR_NOTONCHANNEL(channel)
				case Some(channel) =>
					channel.bans foreach(user.ds ! RPL_BANLIST(channel, _))
					user.ds ! RPL_ENDOFBANLIST(channel)
				case None =>
					user.ds ! ERR_NOSUCHCHANNEL(name)
			}

		case IrcIncomingLine("MODE", name, desc, rest @_*) =>
			Server.targets get name match {
				case Some(channel : Channel) if !channel.users.contains(user) =>
					user.ds ! ERR_NOTONCHANNEL(channel)
				case Some(channel : Channel) if !channel.users(user).isOp =>
					user.ds ! ERR_CHANOPRIVSNEEDED(channel)
				case Some(channel : Channel) =>
					handleModeChange(channel, desc, rest)
				case Some(user2 : User) if user != user2 =>
					user.ds ! ERR_USERSDONTMATCH()
				case Some(user2 : User) =>
					user.ds ! ERR_UMODEUNKNOWNFLAG()
				case None =>
					user.ds ! ERR_NOSUCHCHANNEL(name)
			}

		case IrcIncomingLine("PRIVMSG", target, message) if message startsWith "!" =>
			Server.channels get target match {
				case Some(channel) =>
					val parts = message split " "
					Server.events ! ServiceCommandMessage(channel, user, parts(0).tail toLowerCase, parts.tail :_*)
					Server.events ! PublicTextMessage(channel, user, message)
				case _ =>
					user.ds ! ERR_NOSUCHCHANNEL(target)
			}

		case IrcIncomingLine("PRIVMSG", target, message) =>
			Server.targets get target match {
				case Some(channel : Channel) if !(channel.users contains user) =>
					user.ds ! ERR_NOTONCHANNEL(channel)
				case Some(channel : Channel) =>
					Server.eventBus.publish(PublicTextMessage(channel, user, message))
				case Some(to : User) =>
					Server.eventBus.publish(PrivateTextMessage(user, to, message))
				case _ if target.startsWith("#") =>
					user.ds ! ERR_NOSUCHCHANNEL(target)
				case _ =>
					user.ds ! ERR_NOSUCHNICK(target)
			}

		case IrcIncomingLine("NOTICE", target, message) =>
			Server.targets get target match {
				case Some(channel : Channel) =>
					Server.eventBus.publish(PublicNoticeMessage(channel, user, message))
				case Some(to : User) =>
					Server.eventBus.publish(PrivateNoticeMessage(user, to, message))
				case _ =>
					// ignore
			}

		case IrcIncomingLine("PART", names, rest @_*)  =>
			names.split(",").foreach { case name =>
				Server.channels get name match {
					case Some(channel) =>
						if(!channel.users.contains(user))
							user.ds ! ERR_NOTONCHANNEL(channel)
						else
							Server.eventBus.publish(PartMessage(channel, user, rest.headOption))
					case None =>
						user.ds ! ERR_NOSUCHCHANNEL(name)
				}
			}

		case IrcIncomingLine("QUIT", rest @_*)  =>
			Server.events ! QuitMessage(user, rest.headOption)

		case IrcIncomingLine("NICK", newNick) =>
			if(Server.userNicks.contains(Server.nickToLowerCase(newNick)))
				user.ds ! ERR_NICKNAMEINUSE(newNick)
			else if(!Server.isValidNick(newNick))
				user.ds ! ERR_ERRONEUSNICKNAME(newNick)
			else {
				val oldNick = user.nickname
				Server.events ! NickchangeMessage(user, oldNick, newNick)
			}

		case IrcIncomingLine("TOPIC", name) =>
			Server.channels get name match {
				case Some(channel) if !channel.users.contains(user) =>
					user.ds ! ERR_NOTONCHANNEL(channel)
				case Some(channel) => channel.topic match {
					case Some(topic) =>
						user.ds ! RPL_TOPIC(channel)
					case None =>
						user.ds ! RPL_NOTOPIC(channel)
				}
				case None =>
					user.ds ! ERR_NOSUCHCHANNEL(name)
			}

		case IrcIncomingLine("TOPIC", name, topic) =>
			Server.channels get name match {
				case Some(channel) =>
					if(!channel.users.contains(user))
						user.ds ! ERR_NOTONCHANNEL(channel)
					else if (!UserUtil.checkOp(channel, user))
						user.ds ! ERR_CHANOPRIVSNEEDED(channel)
					else
						Server.events ! TopicChangeMessage(channel, user, channel.topic, topic)
				case None =>
					user.ds ! ERR_NOSUCHCHANNEL(name)
			}

		case IrcIncomingLine("WHOIS", name, rest @ _*) =>
			Server.users get name match {
				case Some(found) =>
					user.ds ! RPL_WHOISUSER(found)
					user.ds ! RPL_WHOISCHANNELS(found)
					user.ds ! RPL_WHOISSERVER(found)
					if (found.authacc.isDefined) user.ds ! RPL_WHOISACCOUNT(found)
					user.ds ! RPL_WHOISIDLE(found)
					user.ds ! RPL_ENDOFWHOIS(found)
				case None =>
					user.ds ! ERR_NOSUCHNICK(name)
			}

		case IrcIncomingLine("REGISTER", name, password, mail) =>
			if(user.authacc.isDefined) user.ds ! SVC_REGISTRATIONFAILURE("Du bist bereits eingeloggt.")
			else Server.events ! RegistrationStart(user, name, password, mail)

		case IrcIncomingLine("LOGIN", name, password) =>
			if(user.authacc.isDefined) user.ds ! SVC_AUTHFAILURE("Du bist bereits eingeloggt.")
			else Server.events ! AuthStart(user, name, password)

		case IrcIncomingLine("INVITE", uname, cname) =>
			(Server.users get uname, Server.channels get cname) match {
				case (Some(invited), Some(channel)) if channel.users contains invited =>
					user.ds ! ERR_USERONCHANNEL(channel, invited)
				case (Some(invited), Some(channel)) if !(channel.users contains user) =>
					user.ds ! ERR_NOTONCHANNEL(channel)
				case (Some(invited), Some(channel)) if !UserUtil.checkOp(channel, user) =>
					user.ds ! ERR_CHANOPRIVSNEEDED(channel)
				case (Some(invited), Some(channel)) =>
					Server.events ! InvitationMessage(channel, user, invited)
					user.ds ! RPL_INVITING(channel, invited)
				case (Some(_), None) =>
					user.ds ! ERR_NOSUCHCHANNEL(cname)
				case (None, _) =>
					user.ds ! ERR_NOSUCHNICK(uname)
			}

		case IrcIncomingLine("USERHOST", names @ _*) =>
			user.ds ! RPL_USERHOST(names.map(Server.users.get).filter(_.isDefined).map(_.get) :_*)

		case IrcIncomingLine("PONG", sth @ _*) =>
			info("received pong from " + user.nickname)

		case IrcIncomingLine("KICK", cname, uname, rest @ _*) =>
			(Server.channels get cname, Server.users get uname) match {
				case (Some(channel), Some(kicked)) if !(channel.users contains user) =>
					user.ds ! ERR_NOTONCHANNEL(channel)
				case (Some(channel), Some(kicked)) if !(channel.users contains kicked) =>
					user.ds ! ERR_NOSUCHNICK(kicked.nickname)
				case (Some(channel), Some(kicked)) if !UserUtil.checkOp(channel, user) =>
					user.ds ! ERR_CHANOPRIVSNEEDED(channel)
				case (Some(channel), Some(kicked)) =>
					Server.events ! KickMessage(channel, user, kicked)
				case (Some(_), None) =>
					user.ds ! ERR_NOSUCHNICK(uname)
				case (None, _) =>
					user.ds ! ERR_NOSUCHCHANNEL(cname)
			}

		case IrcIncomingLine("NAMES") =>
			user.ds ! RPL_ENDOFNAMES("*")

		case IrcIncomingLine("NAMES", "*") =>
			user.ds ! RPL_ENDOFNAMES("*")

		case IrcIncomingLine("NAMES", channels) if channels.contains(",") =>
			user.ds ! ERR_NOPRIVILEGES()

		case IrcIncomingLine("NAMES", name) =>
			Server.channels get name match {
				case Some(channel) =>
					user.ds ! RPL_NAMEREPLY(channel)
					user.ds ! RPL_ENDOFNAMES(channel)
				case None =>
					user.ds ! RPL_ENDOFNAMES(name)
			}
	}

	private def handleModeChange(channel : Channel, desc : String, rest : Seq[String]) {
		val queue = mutable.Queue() ++ rest
		desc.head match {
			case '+' =>
				desc.tail foreach {
					case 'o' | 'v' | 'b' if queue.isEmpty =>
						user.ds ! ERR_NONICKNAMEGIVEN()
					case 'k' | 'b' if queue.isEmpty =>
						user.ds ! ERR_NEEDMOREPARAMS()
					case 'o' =>
						handlePrivilegeChange(channel, queue.dequeue(), OP, SET)
					case 'v' =>
						handlePrivilegeChange(channel, queue.dequeue(), VOICE, SET)
					case 'i' =>
						Server.events ! ChannelModeChangeMessage(channel, user, INVITE_ONLY(yes = true))
					case 'k' =>
						Server.events ! ChannelModeChangeMessage(channel, user, PROTECTION(Some(queue.dequeue())))
					case 'b' =>
						Server.events ! BanMessage(channel, user, UserUtil.cleanMask(queue.dequeue()))
				}
			case '-' =>
				desc.tail foreach {
					case 'o' | 'v' if queue.isEmpty =>
						user.ds ! ERR_NONICKNAMEGIVEN()
					case 'b' if queue.isEmpty =>
						user.ds ! ERR_NEEDMOREPARAMS()
					case 'o' =>
						handlePrivilegeChange(channel, queue.dequeue(), OP, UNSET)
					case 'v' =>
						handlePrivilegeChange(channel, queue.dequeue(), VOICE, UNSET)
					case 'i' =>
						Server.events ! ChannelModeChangeMessage(channel, user, INVITE_ONLY(yes = false))
					case 'k' =>
						Server.events ! ChannelModeChangeMessage(channel, user, PROTECTION(None))
					case 'b' =>
						Server.events ! UnbanMessage(channel, user, UserUtil.cleanMask(queue.dequeue()))
				}
		}
	}

	private def handlePrivilegeChange(channel : Channel, username : String, priv : Privilege, op : PrivilegeOperation) {
		Server.users get username match {
			case Some(user2) if !channel.users.contains(user2) =>
				user.ds ! ERR_USERNOTINCHANNEL(channel, user2)
			case Some(user2) if priv == OP && channel.users(user2).isOp != (op == SET)
					|| priv == VOICE && channel.users(user2).isVoice != (op == SET) =>
				Server.events ! PrivilegeChangeMessage(channel, user, user2, priv, op)
			case _ =>
		}
	}
}

class IrcUserDownstreamActor(val user : IrcUser, val channel : NettyChannel) extends Actor with Logging {
	def receive = {
		case message : IrcResponse =>
			channel.write(message.asInstanceOf[IrcResponse].toIrcOutgoingLine(user))
			Munin.inc("irc-out")
		case message =>
			error("Dropped message in IrcUserDownStreamActor for " + user.nickname + ": " + message + ", sent by " + context.sender)
	}

	override def postStop() {
		channel.close
	}
}

class IrcUserPingActor(val user : IrcUser) extends Actor with Logging {
	def receive = {
		case Tick if user.dying && user.dies < DateTime.now =>
			Server.events ! QuitMessage(user, Some("Ping timeout"))
			info("killed " + user.nickname + ", inactive for " + ((DateTime.now.millis - user.lastActivity.millis) / 1000).round + "s")
		case Tick if user.dying =>
			// wait ...
		case Tick if user.lastActivity < DateTime.now - IrcConstants.TIMEOUT.toMillis =>
			user.dies = DateTime.now + IrcConstants.TIMEOUT.toMillis
			user.dying = true
			user.ds ! CMD_PING()
			info("pinged " + user.nickname + ", inactive for " + ((DateTime.now.millis - user.lastActivity.millis) / 1000).round + "s")
	}
}