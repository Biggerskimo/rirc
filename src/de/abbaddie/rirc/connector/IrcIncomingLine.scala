package de.abbaddie.rirc.connector

case class IrcIncomingLine(command : String, params : Array[String])