package de.abbaddie.rirc.connector.irc

import grizzled.slf4j.Logging
import java.util.concurrent.{ThreadFactory, TimeUnit, Executors}
import IrcConstants._
import java.net.InetSocketAddress
import de.abbaddie.rirc.main.{DefaultRircModule, Server}
import de.abbaddie.rirc.Munin
import io.netty.channel.{ChannelOption, ChannelInitializer, EventLoopGroup}
import io.netty.channel.nio.{NioEventLoop, NioEventLoopGroup}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.{LineBasedFrameDecoder, DelimiterBasedFrameDecoder}
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.handler.codec.string.{StringEncoder, StringDecoder}
import io.netty.util.CharsetUtil
import de.abbaddie.rirc.connector.Connector
import akka.util.Timeout
import concurrent.duration._
import io.netty.util.concurrent.EventExecutor
import java.nio.channels.spi.SelectorProvider

class IrcSocketConnector extends DefaultRircModule with Connector with Logging {
	def start() {
		Munin("irc-in")("title" -> "IRC/Eingehende Nachrichten", "vlabel" -> "Nachrichten")("type" -> "DERIVE", "min" -> 0)
		Munin("irc-out")("title" -> "IRC/Ausgehende Nachrichten", "vlabel" -> "Nachrichten")("type" -> "DERIVE", "min" -> 0)

		IrcConstants.config = config

		IrcMotdHandler.read("motd")

		val bossGroup = new NioEventLoopGroup()
		val workerGroup = new NioEventLoopGroup()

		val bootstrap = new ServerBootstrap()
		bootstrap
			.group(bossGroup, workerGroup)
			.channel(classOf[NioServerSocketChannel])
			.childHandler(new ChannelInitializer[SocketChannel] {
				def initChannel(ch : SocketChannel) {
					ch.pipeline().addLast(new LineBasedFrameDecoder(IrcConstants.MAX_LINE_LEN))

					ch.pipeline().addLast(new StringDecoder(CharsetUtil.UTF_8))
					ch.pipeline().addLast(new StringEncoder(CharsetUtil.UTF_8))

					ch.pipeline().addLast(new IrcLineDecoder())
					ch.pipeline().addLast(new IrcLineEncoder())

					ch.pipeline().addLast(new IrcUpstreamHandler())
				}
			})
		bootstrap.childOption[AnyRef](ChannelOption.WRITE_BUFFER_LOW_WATER_MARK.asInstanceOf[ChannelOption[AnyRef]], 1024.asInstanceOf[AnyRef])
		bootstrap.childOption[AnyRef](ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK.asInstanceOf[ChannelOption[AnyRef]], 2048.asInstanceOf[AnyRef])
		bootstrap.bind(IrcConstants.PORT).sync()
	}

	implicit def toChannelBuffer(c : Char) : ByteBuf = Unpooled.wrappedBuffer(Array(c.toByte))
	implicit def toChannelBuffer(s : String) : ByteBuf = Unpooled.wrappedBuffer(s.toCharArray.map(_.toByte))
}

