package de.abbaddie.rirc.main

import akka.actor.ActorSystem

object Server {
	val actorSystem = ActorSystem("rirc-actors");

	var channels : Map[String, Channel] = Map()
	var users : Map[String, User] = Map()
}
