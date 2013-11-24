package de.abbaddie.rirc.main

import akka.actor.{PoisonPill, Props, Actor}
import collection.immutable.{HashSet, HashMap}
import org.joda.time.DateTime
import grizzled.slf4j.Logging
import de.abbaddie.rirc.main.Message._

class ChannelUserInformation(val user : User) {
	var isOp = false
	var isVoice = false
	val joined = DateTime.now
}

case class Channel(name : String) extends GenericTarget {
	var users : Map[User, ChannelUserInformation] = HashMap()
	val creation = DateTime.now
	var topic : Option[String] = None
	var isInviteOnly = false
	var invited : Set[User] = HashSet()
	var protectionPassword : Option[String] = None
	var bans : List[String] = Nil
	def isRegistered = Server.channelProvider.registeredChannels contains name

	val actor = Server.actorSystem.actorOf(Props(classOf[ChannelActor], Channel.this), name = "channel" + name.tail)
	Server.eventBus.subscribe(actor, new ChannelClassifier(Channel.this))
	Server.eventBus.makeImportant(actor)

	override def equals(that: Any) = that match {
		case Channel(oname) => oname == name
		case _ => false
	}
}
class ChannelActor(val channel : Channel) extends Actor with Logging {
	def receive = {
		case JoinMessage(_, user) =>
			channel.users += (user -> new ChannelUserInformation(user))
			if(channel.users.size == 1) channel.users(user).isOp = true
			sender ! Dummy

		case PartMessage(_, user, _) =>
			rmUser(user)
			sender ! Dummy

		case QuitMessage(user, _) =>
			rmUser(user)
			sender ! Dummy

		case TopicChangeMessage(_, _, _, topic) =>
			channel.topic = Some(topic)
			sender ! Dummy

		case PrivilegeChangeMessage(_, _, user, OP, op) =>
			channel.users(user).isOp = (op == SET)
			sender ! Dummy

		case PrivilegeChangeMessage(_, _, user, VOICE, op) =>
			channel.users(user).isVoice = (op == SET)
			sender ! Dummy

		case ChannelModeChangeMessage(_, _, INVITE_ONLY(yes)) =>
			channel.isInviteOnly = yes
			sender ! Dummy

		case ChannelModeChangeMessage(_, _, PROTECTION(passwd)) =>
			channel.protectionPassword = passwd
			sender ! Dummy

		case InvitationMessage(_, _, invited) =>
			channel.invited += invited
			sender ! Dummy

		case KickMessage(_, _, kicked) =>
			rmUser(kicked)
			sender ! Dummy

		case BanMessage(_, _, mask) =>
			channel.bans ::= mask
			sender ! Dummy

		case UnbanMessage(_, _, mask) =>
			channel.bans = channel.bans filter(_ != mask)
			sender ! Dummy

		case ChannelCloseMessage(_) =>
			Server.events.clear(ChannelClassifier(channel))
			self ! PoisonPill
			sender ! Dummy

		case ChannelCloseMessage(_) |
			 ChannelCreationMessage(_, _) |
			 PublicTextMessage(_, _, _) |
			 PublicNoticeMessage(_, _, _) |
			 NickchangeMessage(_, _, _) |
			 AuthSuccess(_, _) =>
			// ignore
			sender ! Dummy

		case message: Any =>
			error("Dropped message in ChannelActor for " + channel.name + ": " + message)
			sender ! Dummy
	}

	def rmUser(user : User) {
		channel.users -= user

		if(channel.users.isEmpty) {
			Server.events ! ChannelCloseMessage(channel)
		}

		channel.invited -= user
	}
}

case object Dummy
