package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.{RircEventClassifier, Channel}

trait ChannelMessage {
	this: Message =>

	def channel : Channel
}

case class ChannelClassifier(channel : Channel) extends RircEventClassifier