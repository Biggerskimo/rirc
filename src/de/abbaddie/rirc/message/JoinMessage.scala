package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.{Channel, User}

case class JoinMessage(channel : Channel, user : User) extends Message with ChannelMessage with UserMessage