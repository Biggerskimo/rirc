package de.abbaddie.rirc.connector

import akka.util.ByteString

object IrcConstants {
	var COLON = ByteString(":")
	var WHITESPACE = ByteString(" \t\n\r")
	val CRLF = ByteString("\r\n")
	val CR = ByteString("\r")
	var LF = ByteString("\n")

	def isRegularWhitespace(b : Byte) = (b == ' ' || b == '\t')
	def isLineBreak(b : Byte) = (b == '\n' || b == '\r')

	def isWhitespace(b : Byte) = (isRegularWhitespace(b) || isLineBreak(b))
}
