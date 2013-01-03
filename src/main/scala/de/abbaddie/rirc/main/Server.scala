package de.abbaddie.rirc.main

import akka.actor.{Props, ActorRef, Actor, ActorSystem}
import de.abbaddie.rirc.message._
import collection.immutable.HashMap
import grizzled.slf4j.Logging
import de.abbaddie.rirc.service.AuthProvider

object Server {
	var actorSystem : ActorSystem = null
	var actor : ActorRef = null
	val eventBus = new RircEventBus
	val events = eventBus

	var authProvider : AuthProvider = null
	var authSys : ActorRef = null
	var systemUser : User = null

	var channels : Map[String, Channel] = HashMap()
	var users : Map[String, User] = HashMap()
	val targets = TargetHelper
}

object TargetHelper {
	def get(name : String) : Option[GenericTarget] = {
		if(Server.channels contains name) Server.channels.get(name) else Server.users.get(name)
	}
}

class ServerActor extends Actor {
	import Server._

	def receive = {
		case ConnectMessage(user) =>
			users += (user.nickname -> user)
		case ChannelCreationMessage(channel, user) =>
			channels += (channel.name -> channel)
		case ChannelCloseMessage(channel) =>
			channels -= channel.name
		case QuitMessage(user, _) =>
			users -= user.nickname
		case NickchangeMessage(user, oldNick, newNick) =>
			users += (newNick -> user)
			users -= oldNick
	}
}