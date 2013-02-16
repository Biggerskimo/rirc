package de.abbaddie.rirc.connector

import akka.util.ByteString
import concurrent.duration._
import com.typesafe.config.Config
import de.abbaddie.rirc.main.Server

object IrcConstants {
	var config : Config = null

	val COLON = ByteString(":")
	val WHITESPACE = ByteString(" \t\n\r")
	val CRLF = ByteString("\r\n")
	val CR = ByteString("\r")
	val LF = ByteString("\n")

	val UNASSIGNED_NICK = "?"
	val UNASSIGNED_USERNAME = "?"
	val UNASSIGNED_REALNAME = "?"
	lazy val PORT = config.getInt("port")
	val MAX_LINE_LEN = 512 // http://tools.ietf.org/html/rfc1459.html#section-2.3
	lazy val OWNER = config.getString("owner")
	val TIMEOUT = 60.seconds
	val TIMEOUT_TICK = 5.seconds

	lazy val OUR_VERSION = config.getString("version")
	lazy val OUR_NAME = config.getString("name")
	lazy val OUR_HOST = config.getString("host")

	lazy val AUTH_USERSTRING = Server.users(config.getString("auth-nick")).fullString

	def isRegularWhitespace(b : Byte) = (b == ' ' || b == '\t')
	def isLineBreak(b : Byte) = (b == '\n' || b == '\r')

	def isWhitespace(b : Byte) = (isRegularWhitespace(b) || isLineBreak(b))
}
