package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.{Channel, User}

case class TopicChangeMessage(channel : Channel, user : User, oldTopic : String, newTopic : String) extends Message with ChannelMessage