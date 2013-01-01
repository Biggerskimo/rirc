package de.abbaddie.rirc.connector

import org.jboss.netty.channel.{Channel, ChannelHandlerContext}
import org.jboss.netty.buffer.ChannelBuffer
import grizzled.slf4j.Logging
import java.nio.charset.Charset
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder

class IrcLineDecoder extends OneToOneDecoder with Logging {
	// see http://tools.ietf.org/html/rfc1459.html#section-2.3.1
	val regex = """^(?::[^ ]+ +)?([^ ]+)((?: +[^: ][^ ]* *)*)(?: :(.*)|)$""".r

	def decode(ctx: ChannelHandlerContext, channel: Channel, message: Object): AnyRef = {
		val lineStr = message.asInstanceOf[ChannelBuffer].toString(Charset.defaultCharset())

		lineStr match {
			case regex(command, middle, trailing) =>
				var params = if(middle != null) middle.split(' ').filter(!_.isEmpty) else Array.empty[String]
				if(trailing != null) params :+= trailing

				IrcIncomingLine(command, params: _*)
			case _ =>
				error("invalid line received")
				null
		}
	}
}
