package de.abbaddie.rirc.connector

import org.jboss.netty.channel._
import java.net.InetSocketAddress
import org.joda.time.DateTime
import de.abbaddie.rirc.main.{QuitMessage, Server}
import grizzled.slf4j.Logging
import de.abbaddie.rirc.Munin
import java.nio.channels.ClosedChannelException
import java.io.IOException

class IrcUpstreamHandler extends SimpleChannelUpstreamHandler with Logging {
	var user : IrcUser = null

	override def channelDisconnected(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
		user.us ! IrcChannelError("Channel disconnected")
	}

	override def channelClosed(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
		user.us ! IrcChannelError("Channel closed")
		info("channel to " + user.extendedString + " closed")
	}

	override def channelConnected(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
		user = new IrcUser(ctx.getChannel, ctx.getChannel.getRemoteAddress.asInstanceOf[InetSocketAddress])
		info("channel to " + user.extendedString + " connected")
	}

	override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
		Munin.inc("irc-in")
		val readLine = e.getMessage.asInstanceOf[IrcIncomingLine]

		user.us ! readLine
		user.lastActivity = DateTime.now
		user.dying = false
	}

	override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
		e.getCause match {
			case ex : Exception =>
				user.us ! IrcChannelError(ex.getLocalizedMessage)
			case ex =>
				super.exceptionCaught(ctx, e)
		}
	}
}
