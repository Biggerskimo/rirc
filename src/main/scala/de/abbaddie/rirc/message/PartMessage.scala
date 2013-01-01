package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.{Channel, User}

case class PartMessage(channel : Channel, user : User, text : Option[String]) extends Message with ChannelMessage with UserMessage