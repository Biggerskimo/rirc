package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.{User, Channel}

case class PublicTextMessage(user : User, channel : Channel, text : String) extends Message {
	override def isValid = channel.users.contains(user.nickname)
}
