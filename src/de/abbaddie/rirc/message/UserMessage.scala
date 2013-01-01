package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.{User, RircEventClassifier, Channel}

trait UserMessage {
	this: Message =>
	def user : User
}

case class UserClassifier(user : User) extends RircEventClassifier
