package de.abbaddie.rirc.auth

import collection.immutable.HashMap

class MemoryAuthSystem extends AuthSystem {
	var memory : Map[String, String] = HashMap()

	def register(user : String, password : String, mail : String) : Option[String] = {
		memory get user match {
			case Some(_) =>
				Some("There is already a user named '" + user + "'")
			case None =>
				memory += (user -> password)
				None
		}
	}

	def isValid(user : String, password : String) : Boolean = memory.contains(user) && memory(user) == password
}
