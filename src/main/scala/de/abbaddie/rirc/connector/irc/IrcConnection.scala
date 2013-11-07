package de.abbaddie.rirc.connector.irc

import akka.actor.IO.SocketHandle
import de.abbaddie.rirc.main.Server

object IrcConnection {
	var counter : Int = 0
}
class IrcConnection(val socket : SocketHandle) {
	IrcConnection.counter += 1
	val id : Int = IrcConnection.counter

}
