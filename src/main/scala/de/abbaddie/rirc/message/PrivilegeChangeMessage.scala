package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.{User, Channel}

case class PrivilegeChangeMessage(channel : Channel, sender : User, privileged : User, priv : Privilege, op : PrivilegeOperation) extends Message with ChannelMessage

sealed class Privilege
case object VOICE extends Privilege
case object OP extends Privilege

sealed class PrivilegeOperation
case object SET extends PrivilegeOperation
case object UNSET extends PrivilegeOperation