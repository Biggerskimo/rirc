package de.abbaddie.rirc.connector

import akka.util.ByteString

object IrcConstants {
	var COLON = ByteString(":")
	val WHITESPACE = ByteString(" \t\n\r")
	val CRLF = ByteString("\r\n")
	val CR = ByteString("\r")
	val LF = ByteString("\n")

	val UNASSIGNED_NICK = "DUMMY"
	val UNASSIGNED_USERNAME = "johndoe"
	val UNASSIGNED_REALNAME = "JohnDoe"
	val DEFAULT_PORT = 6667
	val MAX_LINE_LEN = 512 // http://tools.ietf.org/html/rfc1459.html#section-2.3
	val OWNER = "Biggerskimo"

	val OUR_VERSION = "1.0.0-SUPERBETA"
	val OUR_NAME = "SUPERNET"
	val OUR_HOST = "dyn.abbaddie.de"

	def isRegularWhitespace(b : Byte) = (b == ' ' || b == '\t')
	def isLineBreak(b : Byte) = (b == '\n' || b == '\r')

	def isWhitespace(b : Byte) = (isRegularWhitespace(b) || isLineBreak(b))
}
