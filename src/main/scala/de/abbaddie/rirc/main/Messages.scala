package de.abbaddie.rirc.main

import de.abbaddie.rirc.service.AuthAccount

/** TRAITS / BASE CLASSES */
sealed trait Message

sealed trait AuthMessage {
	this: Message =>

	def user : User
}

sealed trait ScopedBroadcastMessage {
	this: Message =>

	def user : User
}

sealed trait ChannelMessage {
	this: Message =>

	def channel : Channel
}

sealed trait ServerMessage {
	this: Message =>
}

sealed trait UserMessage {
	this: Message =>

	def user : User
}

/** CLASSIFIERS */
case class ChannelClassifier(channel : Channel) extends RircEventClassifier

case class UserClassifier(user : User) extends RircEventClassifier

/** CONCRETE */
case class AuthStart(user : User, name : String, password : String) extends Message with AuthMessage

case class AuthSuccess(user : User, account : AuthAccount) extends Message with ScopedBroadcastMessage with UserMessage

case class AuthFailure(user : User, name : String, message : String) extends Message with UserMessage

case class BanMessage(channel : Channel, user : User, mask : String) extends Message with ChannelMessage

case class ChannelCloseMessage(channel : Channel) extends Message with ChannelMessage with ServerMessage

case class ChannelCreationMessage(channel : Channel, user : User) extends Message with ChannelMessage with ServerMessage

case class ChannelModeChangeMessage(channel : Channel, sender : User, mode : ChannelMode) extends Message with ChannelMessage

case class ConnectMessage(user : User) extends Message with UserMessage with ServerMessage

case class InvitationMessage(channel : Channel, invitar : User, invited : User) extends Message with ChannelMessage with UserMessage {
	def user = invited
}

case class JoinMessage(channel : Channel, user : User) extends Message with ChannelMessage with UserMessage

case class KickMessage(channel : Channel, kicker : User, kicked : User) extends Message with ChannelMessage with UserMessage {
	def user = kicked
}

case class NickchangeMessage(user : User, oldNick : String, newNick : String) extends Message with ScopedBroadcastMessage

case class PartMessage(channel : Channel, user : User, text : Option[String]) extends Message with ChannelMessage with UserMessage

case class PrivateNoticeMessage(from : User, to : User, text : String) extends Message with UserMessage {
	def user = to
}

case class PrivateTextMessage(from : User, to : User, text : String) extends Message with UserMessage {
	def user = to
}

case class PrivilegeChangeMessage(channel : Channel, sender : User, privileged : User, priv : Privilege, op : PrivilegeOperation) extends Message with ChannelMessage

case class PublicNoticeMessage(channel : Channel, user : User, text : String) extends Message with ChannelMessage

case class PublicTextMessage(channel : Channel, user : User, text : String) extends Message with ChannelMessage

case class QuitMessage(user : User, message : Option[String]) extends Message with ScopedBroadcastMessage with ServerMessage

case class RegistrationStart(user : User, name : String, password : String, emailAddress : String) extends Message with AuthMessage

case class RegistrationSuccess(user : User, account : AuthAccount) extends Message with ScopedBroadcastMessage with UserMessage

case class RegistrationFailure(user : User, name : String, message : String) extends Message with UserMessage

case class ServiceCommandMessage(channel : Channel, user : User, command : String, params : String*) extends Message with ChannelMessage

case class TopicChangeMessage(channel : Channel, user : User, oldTopic : Option[String], newTopic : String) extends Message with ChannelMessage

case class UnbanMessage(channel : Channel, user : User, mask : String) extends Message with ChannelMessage


sealed class Privilege
case object VOICE extends Privilege
case object OP extends Privilege

sealed class PrivilegeOperation
case object SET extends PrivilegeOperation
case object UNSET extends PrivilegeOperation

sealed class ChannelMode
case class INVITE_ONLY(yes : Boolean) extends ChannelMode
case class PROTECTION(password : Option[String]) extends ChannelMode