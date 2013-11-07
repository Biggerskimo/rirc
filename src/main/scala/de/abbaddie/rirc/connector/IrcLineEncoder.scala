package de.abbaddie.rirc.connector

import java.nio.charset.Charset
import io.netty.handler.codec.MessageToMessageEncoder
import java.util.{List => JList}
import io.netty.channel.ChannelHandlerContext
import io.netty.buffer.Unpooled

class IrcLineEncoder extends MessageToMessageEncoder[IrcOutgoingLine] {
	def encode(ctx: ChannelHandlerContext, in : IrcOutgoingLine, out : JList[Object]) {
		out.add(in.toString)
	}
}
