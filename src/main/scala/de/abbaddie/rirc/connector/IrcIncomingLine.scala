package de.abbaddie.rirc.connector

case class IrcIncomingLine(command : String, params : String*) {
	override def toString = command + "/" + params.mkString(",")
}