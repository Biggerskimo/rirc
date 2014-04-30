package de.abbaddie.rirc.service

import concurrent.Future
import de.abbaddie.rirc.main.RircModule

trait AuthProvider extends RircModule {
	def init()

	def register(name : String, password : String, mail : String) : Future[_]
	
	def setPassword(name : String, password : String) : Future[String]

	def isValid(name : String, password : String) : Future[_]

	def lookup(name : String) : Future[_]
}
