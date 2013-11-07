package de.abbaddie.rirc.connector.irc

case class IrcIncomingLine(command : String, params : String*) {
	override def toString = command + "/" + params.mkString(",")
}