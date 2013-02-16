package de.abbaddie.rirc.service

import concurrent.Future
import de.abbaddie.rirc.main.RircModule

trait AuthProvider extends RircModule {
	def key : String

	def register(name : String, password : String, mail : String) : Future[Option[String]]

	def isValid(name : String, password : String) : Future[Option[AuthAccount]]

	def lookup(name : String) : Future[Option[AuthAccount]]
}
