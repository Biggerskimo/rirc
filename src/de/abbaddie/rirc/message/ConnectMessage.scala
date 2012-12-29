package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.{Server, User}

case class ConnectMessage(user : User) extends Message {
	override def isValid = !Server.users.contains(user.nickname)
}
