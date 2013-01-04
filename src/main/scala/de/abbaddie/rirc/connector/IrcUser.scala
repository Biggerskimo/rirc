package de.abbaddie.rirc.connector

import akka.actor.{ActorRef, PoisonPill, Props, Actor}
import akka.pattern.ask
import de.abbaddie.rirc.main._
import org.jboss.netty.channel.{Channel => NettyChannel}
import grizzled.slf4j.Logging
import scala.Some
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import concurrent.Await
import concurrent.duration._
import collection.mutable
import java.net.InetSocketAddress

class IrcUser(val channel : NettyChannel, address2 : InetSocketAddress) extends User {
	def initActor() = Server.actorSystem.actorOf(Props(new IrcUserSystemActor(IrcUser.this)), name = uid.toString)

	var ds : ActorRef = null
	var us : ActorRef = null

	address = address2

	// ensure ds + us
	implicit val timeout = Timeout(1, TimeUnit.SECONDS)
	Await.result(actor ? InitDummy(), 1 seconds)
}


case class InitDummy()

class IrcUserSystemActor(val user : IrcUser) extends Actor with Logging {
	def receive = {
		case ConnectMessage(_) => // it's me!
			user.ds ! RPL_WELCOME()
			user.ds ! RPL_YOURHOST()
			user.ds ! RPL_CREATED()
			user.ds ! RPL_MYINFO()

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
			user.ds ! MSG_PART(channel, sender, message)

		case QuitMessage(sender, message) =>
			// TODO check whether user is visible
			if(sender == user) {
				user.ds ! PoisonPill
				Server.eventBus.unsubscribe(self)
			}
			else
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

		case AuthFailure(_, _) =>
			user.ds ! SVC_AUTHFAILURE()

		case InitDummy() =>
			user.ds = context.actorOf(Props(new IrcUserDownstreamActor(user, user.channel)), name = "ds")
			user.us = context.actorOf(Props(new IrcUserUpstreamActor(user)), name = "us")
			sender ! InitDummy()

		case message : Any =>
			error("Dropped message in IrcUserSystemActor for " + user.nickname + ": " + message + ", sent by " + context.sender)
	}
}

class IrcUserUpstreamActor(val user : IrcUser) extends Actor with Logging {
	// registration part
	var nickSet = false
	var loggedIn = false

	def receive = {
		case IrcIncomingLine("NICK", nickname) =>
			if(Server.users contains nickname)
				user.ds ! ERR_NICKNAMEINUSE(nickname)
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
			Server.eventBus.publish(ConnectMessage(user))
			context.become(receiveUsual)
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

		case IrcIncomingLine("JOIN", name) =>
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

				Server.eventBus.publish(JoinMessage(channel, user))
			}

		case IrcIncomingLine("MODE", name) =>
			Server.channels get name match {
				case Some(channel) =>
					user.ds ! RPL_CHANNELMODEIS(channel)
					user.ds ! RPL_CREATIONTIME(channel)
				case None =>
					user.ds ! ERR_NOSUCHCHANNEL(name)
			}

		case IrcIncomingLine("MODE", name, "+b") =>
			Server.channels get name match {
				case Some(channel) if !channel.users.contains(user) =>
					user.ds ! ERR_NOTONCHANNEL(channel)
				case Some(channel) =>
					// TODO ban list
				case None =>
					user.ds ! ERR_NOSUCHCHANNEL(name)
			}

		case IrcIncomingLine("MODE", name, desc, rest @_*) =>
			Server.channels get name match {
				case Some(channel) if !channel.users.contains(user) =>
					user.ds ! ERR_NOTONCHANNEL(channel)
				case Some(channel) if !channel.users(user).isOp =>
					user.ds ! ERR_CHANOPRIVSNEEDED(channel)
				case Some(channel) =>
					handleModeChange(channel, desc, rest)
				case None =>
					user.ds ! ERR_NOSUCHCHANNEL
			}

		case IrcIncomingLine("PRIVMSG", target, message) if message startsWith "!" =>
			Server.channels get target match {
				case Some(channel) =>
					val parts = message split " "
					Server.events ! ServiceCommandMessage(channel, user, parts(0).tail toLowerCase, parts.tail :_*)
				case _ =>
					user.ds ! ERR_NOSUCHCHANNEL(target)
			}

		case IrcIncomingLine("PRIVMSG", target, message) =>
			Server.targets get target match {
				case Some(channel : Channel) =>
					Server.eventBus.publish(PublicTextMessage(channel, user, message))
				case Some(to : User) =>
					Server.eventBus.publish(PrivateTextMessage(user, to, message))
				case _ =>
					user.ds ! ERR_NOSUCHCHANNEL(target)
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

		case IrcIncomingLine("PART", name, rest @_*)  =>
			Server.channels get name match {
				case Some(channel) =>
					if(!channel.users.contains(user))
						user.ds ! ERR_NOTONCHANNEL(channel)
					else
						Server.eventBus.publish(PartMessage(channel, user, rest.headOption))
				case None =>
					user.ds ! ERR_NOSUCHCHANNEL(name)
			}

		case IrcIncomingLine("QUIT", rest @_*)  =>
			Server.events ! QuitMessage(user, rest.headOption)

		case IrcIncomingLine("NICK", newNick) =>
			if(Server.users.contains(newNick))
				user.ds ! ERR_NICKNAMEINUSE(newNick)
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
					else if (!channel.users(user).isOp)
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

		case IrcIncomingLine("LOGIN", name, password) =>
			Server.events ! AuthStart(user, name, password)

		case line : IrcIncomingLine =>
			info("Dropped incoming line from " + user.nickname + ": " + line)
	}

	private def handleModeChange(channel : Channel, desc : String, rest : Seq[String]) {
		val queue = mutable.Queue() ++ rest
		desc.head match {
			case '+' =>
				desc.tail foreach{ char => char match {
					case 'o' | 'v' if queue.isEmpty =>
						user.ds ! ERR_NONICKNAMEGIVEN()
					case 'o' =>
						handlePrivilegeChange(channel, queue.dequeue(), OP, SET)
					case 'v' =>
						handlePrivilegeChange(channel, queue.dequeue(), VOICE, SET)
				}}
			case '-' =>
				desc.tail foreach{ char => char match {
					case 'o' | 'v' if queue.isEmpty =>
						user.ds ! ERR_NONICKNAMEGIVEN()
					case 'o' =>
						handlePrivilegeChange(channel, queue.dequeue(), OP, UNSET)
					case 'v' =>
						handlePrivilegeChange(channel, queue.dequeue(), VOICE, UNSET)
				}
			}
		}
	}

	private def handlePrivilegeChange(channel : Channel, username : String, priv : Privilege, op : PrivilegeOperation) {
		Server.users get username match {
			case Some(user2) if !channel.users.contains(user2) =>
				user.ds ! ERR_USERNOTINCHANNEL(channel, user2)
			case Some(user2) =>
				Server.events ! PrivilegeChangeMessage(channel, user, user2, priv, op)
		}
	}
}

class IrcUserDownstreamActor(val user : IrcUser, val channel : NettyChannel) extends Actor with Logging {
	def receive = {
		case message =>
			if(message.isInstanceOf[IrcResponse])
				channel.write(message.asInstanceOf[IrcResponse].toIrcOutgoingLine(user))
			else if(message.isInstanceOf[IrcOutgoingLine])
				channel.write(message)
			else
				error("Dropped message in IrcUserDownStreamActor for " + user.nickname + ": " + message + ", sent by " + context.sender)
	}

	override def postStop() {
		channel.close
	}
}