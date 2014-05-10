package de.abbaddie.rirc.main

import akka.actor.{ActorRef, Actor, ActorSystem}
import scala.collection.immutable.{HashSet, HashMap}
import de.abbaddie.rirc.service.{ChannelProvider, AuthProvider}
import grizzled.slf4j.Logging
import com.typesafe.config.Config
import de.abbaddie.rirc.main.Message._
import de.abbaddie.rirc.connector.Connector

object Server {
	var actorSystem : ActorSystem = null
	var actor : ActorRef = null
	val eventBus = new RircEventBus
	val events = eventBus
	var config : Config = null
	var connectors = List[Connector]()
	var maxQueueSize = 1000000

	var authProvider : AuthProvider = null
	var authSys : ActorRef = null
	var channelProvider : ChannelProvider = null
	//var systemUser : User = null
	var reservedNicks : Set[String] = null

	var channels : Map[String, Channel] = HashMap()
	var users : Map[String, User] = HashMap()
	var userNicks : Set[String] = HashSet()
	val targets = TargetHelper

	def isValidNick(nick : String) =
		nick.length >= config.getInt("nicklen_min") &&
		nick.length <= config.getInt("nicklen_max") &&
		nick.matches("^[A-Za-z_\\[\\]\\{\\}\\\\`\\|^][A-Za-z0-9_\\[\\]\\{\\}\\\\`\\|^-]*$") &&
		nick != "anonymous" // http://tools.ietf.org/html/rfc2811#section-4.2.1

	def nickToLowerCase(nick : String) =
		nick.map({
			case c if c >= 'A' && c <= 'Z' => c.toLower
			case '[' => '{'
			case ']' => '}'
			case '|' => '\\'
			case '^' => '~'
			case c => c
		})
	
	def isQueueFull = connectors.map(_.queueLength).sum >= maxQueueSize
}

object TargetHelper {
	def get(name : String) : Option[GenericTarget] = {
		if(Server.channels contains name) Server.channels.get(name) else Server.users.get(name)
	}
}

class ServerActor extends Actor with Logging {
	import Server._

	def receive = {
		case ConnectMessage(user) =>
			users += (user.nickname -> user)
			userNicks += nickToLowerCase(user.nickname)
			info(user.extendedString + " connected")
		case ChannelCreationMessage(channel, user) =>
			channels += (channel.name -> channel)
		case ChannelCloseMessage(channel) =>
			channels -= channel.name
		case QuitMessage(user, _) =>
			users -= user.nickname
			userNicks -= nickToLowerCase(user.nickname)
			info(user.extendedString + " disconnected")
		case NickchangeMessage(user, oldNick, newNick) =>
			users += (newNick -> user)
			userNicks += nickToLowerCase(newNick)
			users -= oldNick
			userNicks -= nickToLowerCase(oldNick)

	}
}
