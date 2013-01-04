package de.abbaddie.rirc.service

import concurrent.Future


trait AuthProvider {
	def key : String

	def register(name : String, password : String, mail : String) : Future[Option[String]]

	def isValid(name : String, password : String) : Future[Option[AuthAccount]]

	def lookup(name : String) : Future[Option[AuthAccount]]
}
