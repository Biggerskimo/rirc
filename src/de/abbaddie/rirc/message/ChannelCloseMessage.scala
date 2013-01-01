package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.Channel

case class ChannelCloseMessage(channel : Channel) extends Message with ChannelMessage with ServerMessage
