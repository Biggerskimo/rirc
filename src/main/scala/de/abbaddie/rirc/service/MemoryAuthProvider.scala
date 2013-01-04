package de.abbaddie.rirc.service

import collection.immutable.HashMap
import concurrent.Future
import de.abbaddie.rirc.main.Server

class MemoryAuthProvider extends AuthProvider {
	val key = "memory"

	var memory : Map[String, String] = HashMap()

	implicit val dispatcher = Server.actorSystem.dispatcher

	def register(user : String, password : String, mail : String) : Future[Option[String]] = {
		memory get user match {
			case Some(_) =>
				Future(Some("There is already a user named '" + user + "'"))
			case None =>
				memory += (user -> password)
				Future(None)
		}
	}

	def isValid(user : String, password : String) : Future[Option[AuthAccount]] = {
		if(memory.contains(user) && memory(user) == password) Future(Some(new AuthAccount(user, false)))
		else Future(None)
	}

	def lookup(user : String) = {
		if(memory.contains(user)) Future(Some(new AuthAccount(user, false)))
		else Future(None)
	}
}