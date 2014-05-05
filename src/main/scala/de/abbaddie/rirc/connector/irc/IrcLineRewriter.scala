package de.abbaddie.rirc.connector.irc

import grizzled.slf4j.Logging
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import java.util.{List => JList}
import com.typesafe.config.Config
import scala.collection.JavaConverters._
import scala.util.matching.Regex

class IrcLineRewriter(val config : Config) extends MessageToMessageDecoder[String] with Logging {
	var replaces = List[(Regex, String)]()
	config.getConfigList("replace").asScala.foreach { tuple =>
		val find = tuple.getString("find")
		val replacement = tuple.getString("replacement")
		val regex = ("(?i)" + find).r
		replaces ::= (regex, replacement)
	}
	
	def decode(ctx: ChannelHandlerContext, in : String, out : JList[Object]) {
		val outStr = replaces.foldLeft(in) {
			case (string, (regex, replacement)) =>
				regex.replaceAllIn(string, replacement)
		}
		out.add(outStr)
	}
}
