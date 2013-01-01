package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.User

case class PrivateNoticeMessage(from : User, to : User, text : String) extends Message with UserMessage {
	def user = to
}
