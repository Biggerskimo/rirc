package de.abbaddie.rirc.connector

import org.jboss.netty.channel._
import java.net.InetSocketAddress

class IrcUpstreamHandler extends SimpleChannelUpstreamHandler {
	var user : IrcUser = null

	override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
		// messageReceived may happen before channelConnected Oo, so dont use channelConnted!
		if (user == null) {
			user = new IrcUser(ctx.getChannel, ctx.getChannel.getRemoteAddress.asInstanceOf[InetSocketAddress])
		}
		val readLine = e.getMessage.asInstanceOf[IrcIncomingLine]

		user.us ! readLine
	}
}
