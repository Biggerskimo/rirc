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
import de.abbaddie.rirc.main.{DefaultRircModule, Server}
import de.abbaddie.jmunin.Munin

class IrcSocketConnector extends DefaultRircModule with Connector with Logging {
	def start() {
		Munin("irc-in")("title" -> "IRC/Eingehende Nachrichten", "vlabel" -> "Nachrichten")("type" -> "DERIVE", "min" -> 0)
		Munin("irc-out")("title" -> "IRC/Ausgehende Nachrichten", "vlabel" -> "Nachrichten")("type" -> "DERIVE", "min" -> 0)

		IrcConstants.config = config

		val bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool, Executors.newCachedThreadPool, Runtime.getRuntime.availableProcessors * 4))
		bootstrap.setPipelineFactory(new ChannelPipelineFactory {
			def getPipeline: ChannelPipeline = {
				Channels.pipeline(
					new DelimiterBasedFrameDecoder(MAX_LINE_LEN, '\n', '\r', "\r\n"),
					new IrcLineDecoder(),
					new IrcLineEncoder(),
					new IrcLogger(),
					new IrcUpstreamHandler()
				)
			}
		})

		bootstrap.bind(new InetSocketAddress(IrcConstants.PORT))
	}

	implicit def toChannelBuffer(c : Char) : ChannelBuffer = ChannelBuffers.wrappedBuffer(Array(c.toByte))

	implicit def toChannelBuffer(s : String) : ChannelBuffer = ChannelBuffers.wrappedBuffer(s.toCharArray.map(_.toByte))
}

