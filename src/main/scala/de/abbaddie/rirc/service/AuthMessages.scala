package de.abbaddie.rirc.service

import de.abbaddie.rirc.message.{UserMessage, Message}
import de.abbaddie.rirc.main.{User}

sealed trait AuthMessage {
	this: Message =>

	def user : User
}

case class AuthStart(user : User, name : String, password : String) extends Message with AuthMessage

case class AuthSuccess(user : User, account : AuthAccount) extends Message with ServiceMessage with UserMessage

case class AuthFailure(user : User, name : String) extends Message with UserMessage