package de.abbaddie.rirc.auth

trait AuthSystem {
	def register(name : String, password : String, mail : String)

	def isValid(name : String, password : String) : Boolean
}
