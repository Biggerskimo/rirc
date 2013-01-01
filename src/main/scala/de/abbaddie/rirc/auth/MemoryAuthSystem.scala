package de.abbaddie.rirc.auth

class MemoryAuthSystem extends AuthSystem {
	def register(user : String, password : String, mail : String) {

	}

	def isValid(user : String, password : String) : Boolean = {
		true
	}
}
