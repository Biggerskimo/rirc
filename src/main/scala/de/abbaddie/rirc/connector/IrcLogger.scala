package de.abbaddie.rirc.connector

import org.jboss.netty.channel._
import grizzled.slf4j.Logging

class IrcLogger extends ChannelDownstreamHandler with ChannelUpstreamHandler with Logging {
	def handleDownstream(ctx: ChannelHandlerContext, e: ChannelEvent) {
		if(isTraceEnabled && e.isInstanceOf[MessageEvent] && e.asInstanceOf[MessageEvent].getMessage.isInstanceOf[IrcOutgoingLine]) {
			trace(e.asInstanceOf[MessageEvent].getMessage.asInstanceOf[IrcOutgoingLine].toString(withNl = false))
		}
		ctx.sendDownstream(e)
	}

	def handleUpstream(ctx: ChannelHandlerContext, e: ChannelEvent) {
		if(isInfoEnabled && e.isInstanceOf[MessageEvent]) {
			trace(e.asInstanceOf[MessageEvent].getMessage)
		}
		ctx.sendUpstream(e)
	}
}
