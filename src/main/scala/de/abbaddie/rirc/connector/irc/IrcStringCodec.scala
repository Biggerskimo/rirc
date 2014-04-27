package de.abbaddie.rirc.connector.irc

import io.netty.handler.codec.MessageToMessageCodec
import java.util
import io.netty.buffer.{Unpooled, ByteBuf}
import java.nio.charset._
import io.netty.channel.ChannelHandlerContext
import java.nio.CharBuffer

class IrcStringCodec(val charsets : Seq[String]) extends MessageToMessageCodec[ByteBuf, String] {
	val charsetIter = charsets.iterator

	var charset : Charset = null
	var decoder : CharsetDecoder = null
	var encoder : CharsetEncoder = null

	def setCharset(charset2 : String) : Unit = setCharset(Charset.forName(charset2))
	def setCharset(charset2 : Charset) : Unit = {
		charset = charset2

		decoder = charset.newDecoder()
		decoder.onMalformedInput(CodingErrorAction.REPORT)
		decoder.onUnmappableCharacter(CodingErrorAction.REPORT)

		encoder = charset.newEncoder()
	}
	def tryNextCharset() = setCharset(charsetIter.next())

	def decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: util.List[AnyRef]) {
		try {
			out.add(decoder.decode(msg.nioBuffer()).toString)
		}
		catch {
			case e : CharacterCodingException =>
				tryNextCharset()
				decode(ctx, msg, out)
		}
	}

	def encode(ctx: ChannelHandlerContext, msg: String, out: util.List[AnyRef]) {
		out.add(Unpooled.wrappedBuffer(encoder.encode(CharBuffer.wrap(msg))))
	}

	tryNextCharset()
}
