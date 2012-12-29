package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.{Channel, User}

case class JoinMessage(user : User, channel : Channel) extends Message {
	override def isValid = !channel.users.contains(user.nickname)
}