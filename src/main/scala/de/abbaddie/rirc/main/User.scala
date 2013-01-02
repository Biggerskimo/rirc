package de.abbaddie.rirc.main

import akka.actor.{ActorRef, Actor}
import de.abbaddie.rirc.connector.IrcConstants
import java.net.InetSocketAddress
import de.abbaddie.rirc.message.UserClassifier

abstract class User extends GenericTarget {
	var nickname : String = IrcConstants.UNASSIGNED_NICK
	var username : String = IrcConstants.UNASSIGNED_USERNAME
	var realname : String = IrcConstants.UNASSIGNED_REALNAME
	var address : InetSocketAddress = null

	val uid = IdGenerator("user")
	val sid = uid.toString.map(c => ('A' - '1' + c).toChar)

	val actor = initActor()

	def initActor() : ActorRef

	Server.eventBus.subscribe(actor, UserClassifier(this))
}
