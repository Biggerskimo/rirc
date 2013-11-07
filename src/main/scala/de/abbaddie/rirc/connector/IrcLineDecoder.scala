package de.abbaddie.rirc.connector

import grizzled.slf4j.Logging
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.{ByteToMessageDecoder, MessageToMessageDecoder}
import java.util.{List => JList}
import io.netty.buffer.ByteBuf
import java.nio.charset.Charset

class IrcLineDecoder extends MessageToMessageDecoder[String] with Logging {
	// see http://tools.ietf.org/html/rfc1459.html#section-2.3.1
	val regex = """^(?::[^ ]+ +)?([^ ]+)((?: +[^: ][^ ]* *)*)(?: :(.*)|)$""".r

	def decode(ctx: ChannelHandlerContext, in : String, out : JList[Object]) {
		in match {
			case regex(command, middle, trailing) =>
				var params = if(middle != null) middle.split(' ').filter(!_.isEmpty) else Array.empty[String]
				if(trailing != null) params :+= trailing

				out.add(IrcIncomingLine(command.toUpperCase, params: _*))
			case "" =>
				error("emtpy line")
				// empty lines are ok
			case _ =>
				error("invalid line received: '" + in + "'")
		}
	}
}
