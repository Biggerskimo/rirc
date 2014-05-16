package de.abbaddie.rirc.main

import akka.actor.ActorRef
import de.abbaddie.rirc.service.AuthAccount
import org.joda.time.DateTime
import de.abbaddie.rirc.main.Message._

abstract class User extends GenericTarget {
	def nickname : String
	def username : String
	def realname : String
	def hostname : String
	def initActor() : ActorRef
	def isSystemUser = true
	def name = nickname
	def fullString = nickname + "!~" + username + "@" + hostname
	def extendedString = fullString + "#" + uid
	
	def channels = Server.channels.values.filter(_.users.contains(this))
	def channelNames = channels.map(_.name).mkString(",")

	override def hashCode = uid
	override def equals(other : Any) = other match {
		case user2 : User => this.uid == user2.uid
		case _ => false
	}
	override def toString = "User#" + uid + "(" + nickname + ")"

	var authacc : Option[AuthAccount] = None
	var isOper = false
	val uid = IdGenerator("user")
	val actor = initActor()
	val signOn : DateTime = DateTime.now
	var lastActivity : DateTime = DateTime.now

	Server.events.subscribe(actor, UserClassifier(this))
}
