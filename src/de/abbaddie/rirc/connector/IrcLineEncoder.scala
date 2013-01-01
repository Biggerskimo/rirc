package de.abbaddie.rirc.connector

import org.jboss.netty.handler.codec.oneone.OneToOneEncoder
import org.jboss.netty.channel.{Channel, ChannelHandlerContext}
import org.jboss.netty.buffer.ChannelBuffers
import java.nio.ByteOrder
import java.nio.charset.Charset

class IrcLineEncoder extends OneToOneEncoder{
	def encode(ctx: ChannelHandlerContext, channel: Channel, msg: Any): AnyRef = {
		val message = msg.asInstanceOf[IrcOutgoingLine]

		return ChannelBuffers.copiedBuffer(ByteOrder.BIG_ENDIAN, message.toString, Charset.defaultCharset())
	}
}
