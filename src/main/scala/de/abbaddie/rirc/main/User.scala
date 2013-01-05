package de.abbaddie.rirc.main

import akka.actor.ActorRef
import de.abbaddie.rirc.connector.IrcConstants
import java.net.InetSocketAddress
import de.abbaddie.rirc.service.AuthAccount
import org.joda.time.DateTime

abstract class User extends GenericTarget {
	var nickname : String = IrcConstants.UNASSIGNED_NICK
	var username : String = IrcConstants.UNASSIGNED_USERNAME
	var realname : String = IrcConstants.UNASSIGNED_REALNAME
	protected var address : InetSocketAddress = null
	def hostname = address.getHostName
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

	Server.eventBus.subscribe(actor, UserClassifier(this))

	def fullString = nickname + "!" + username + "@" + hostname
	def extendedString = nickname + "!" + username + "@" + hostname + "#" + uid

	var lastActivity : DateTime = DateTime.now
}
