package de.abbaddie.rirc.connector

import grizzled.slf4j.Logging
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.util.concurrent.Executors
import org.jboss.netty.channel.{Channels, ChannelPipeline, ChannelPipelineFactory}
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder
import IrcConstants._
import org.jboss.netty.buffer.{ChannelBuffers, ChannelBuffer}
import java.net.InetSocketAddress

class IrcSocketConnector(val port : Int) extends Logging {
	def this() = this(DEFAULT_PORT)

	def start {
		val bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool, Executors.newCachedThreadPool))
		bootstrap.setPipelineFactory(new ChannelPipelineFactory {
			def getPipeline: ChannelPipeline = {
				return Channels.pipeline(
					new DelimiterBasedFrameDecoder(MAX_LINE_LEN, '\n', '\r', "\r\n"),
					new IrcLineDecoder(),
					new IrcLineEncoder(),
					new IrcUpstreamHandler()
				)
			}
		})

		bootstrap.bind(new InetSocketAddress(port))
	}

	implicit def toChannelBuffer(c : Char) : ChannelBuffer = ChannelBuffers.wrappedBuffer(Array(c.toByte))

	implicit def toChannelBuffer(s : String) : ChannelBuffer = ChannelBuffers.wrappedBuffer(s.toCharArray.map(_.toByte))
}