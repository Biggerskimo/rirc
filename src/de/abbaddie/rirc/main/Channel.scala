package de.abbaddie.rirc.main

import akka.actor.{Props, Actor}
import de.abbaddie.rirc.message._
import collection.immutable.HashMap
import org.joda.time.DateTime
import de.abbaddie.rirc.message.PartMessage
import de.abbaddie.rirc.message.JoinMessage
import de.abbaddie.rirc.message.ChannelClassifier
import grizzled.slf4j.Logging

class ChannelUserInformation(val user : User) {
	var isOp = false
	var isVoice = false
	val joined = DateTime.now
}

case class Channel(name : String) {
	var users : Map[User, ChannelUserInformation] = HashMap()
	val creation = DateTime.now
	var topic : String = null

	val actor = Server.actorSystem.actorOf(Props(new ChannelActor(Channel.this)))
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
			sender ! null
		case PartMessage(_, user, _) =>
			rmUser(user)
			sender ! null
		case QuitMessage(user, _) =>
			rmUser(user)
			sender ! null
		case TopicChangeMessage(_, _, _, topic) =>
			channel.topic = topic
			sender ! null
		case ChannelCloseMessage(_) |
			 ChannelCreationMessage(_, _) |
			 PublicTextMessage(_, _, _) |
			 PublicNoticeMessage(_, _, _) =>
			// ignore
			sender ! null
		case message: Any =>
			error("Dropped message in ChannelActor for " + channel.name + ": " + message)
			sender ! null
	}

	def rmUser(user : User) {
		channel.users -= user

		if(channel.users.isEmpty) {
			Server.events ! ChannelCloseMessage(channel)
		}
	}
}
