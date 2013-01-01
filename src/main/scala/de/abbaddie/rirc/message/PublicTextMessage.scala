package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.{User, Channel}

case class PublicTextMessage(channel : Channel, user : User, text : String) extends Message with ChannelMessage