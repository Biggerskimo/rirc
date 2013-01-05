package de.abbaddie.rirc.connector

import org.jboss.netty.channel._
import java.net.InetSocketAddress
import org.joda.time.DateTime
import de.abbaddie.rirc.main.{QuitMessage, Server}
import grizzled.slf4j.Logging

class IrcUpstreamHandler extends SimpleChannelUpstreamHandler with Logging {
	var user : IrcUser = null

	override def channelDisconnected(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
		Server.events ! QuitMessage(user, Some("Client disconnected"))
	}

	override def channelClosed(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
		info("channel to " + user.extendedString + " closed")
	}

	override def channelConnected(ctx : ChannelHandlerContext, e : ChannelStateEvent) {
		user = new IrcUser(ctx.getChannel, ctx.getChannel.getRemoteAddress.asInstanceOf[InetSocketAddress])
		info("channel to " + user.extendedString + " connected")
	}

	override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
		val readLine = e.getMessage.asInstanceOf[IrcIncomingLine]

		user.us ! readLine
		user.lastActivity = DateTime.now
		user.deathMode = false
	}
}
