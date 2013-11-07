package de.abbaddie.rirc.main

import akka.actor.ActorRef
import de.abbaddie.rirc.connector.IrcConstants
import java.net.InetSocketAddress
import de.abbaddie.rirc.service.AuthAccount
import org.joda.time.DateTime
import de.abbaddie.rirc.main.Message._

abstract class User extends GenericTarget {
	def nickname : String
	def username : String
	def realname : String
	def hostname : String


	var authacc : Option[AuthAccount] = None
	var isOper = false

	val uid = IdGenerator("user")

	val actor = initActor()

	def initActor() : ActorRef

	override def hashCode = uid
	override def equals(other : Any) = other match {
		case user2 : User => this.uid == user2.uid
		case _ => false
	}
	override def toString = "User#" + uid + "(" + nickname + ")"

	def isSystemUser = true
	def name = nickname

	Server.eventBus.subscribe(actor, UserClassifier(this))

	def fullString = nickname + "!~" + username + "@" + hostname
	def extendedString = fullString + "#" + uid

	var lastActivity : DateTime = DateTime.now
}
