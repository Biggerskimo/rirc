package de.abbaddie.rirc.connector.irc

import java.net.InetSocketAddress
import org.joda.time.DateTime
import grizzled.slf4j.Logging
import de.abbaddie.rirc.Munin
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}

class IrcUpstreamHandler extends SimpleChannelInboundHandler[IrcIncomingLine] with Logging {
	var user : IrcUser = null

	override def channelInactive(ctx : ChannelHandlerContext) {
		user.us ! IrcChannelError("Channel disconnected")
		info("channel to " + user.extendedString + " closed")
	}

	override def channelActive(ctx : ChannelHandlerContext) {
		user = new IrcUser(ctx.channel, ctx.channel.remoteAddress.asInstanceOf[InetSocketAddress])
		info("channel to " + user.extendedString + " connected")
	}

	override def channelRead0(ctx: ChannelHandlerContext, line : IrcIncomingLine) {
		Munin.inc("irc-in")

		user.us ! line
		user.lastActivity = DateTime.now
		user.dying = false
	}

	override def exceptionCaught(ctx: ChannelHandlerContext, cause : Throwable) {
		cause match {
			case ex : Exception =>
				user.us ! IrcChannelError(ex.getLocalizedMessage)
			case ex =>
				super.exceptionCaught(ctx, cause)
		}
	}
}
