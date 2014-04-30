package de.abbaddie.rirc.service

import collection.immutable.HashMap
import concurrent.Future
import de.abbaddie.rirc.main.{DefaultRircModule, Server}
import org.mindrot.jbcrypt.BCrypt

class MemoryAuthProvider extends DefaultRircModule with AuthProvider {
	val key = "memory"

	var memory : Map[String, String] = HashMap()
	var oper : String = ""

	implicit val dispatcher = Server.actorSystem.dispatcher

	def init() {}

	def register(user : String, password : String, mail : String) : Future[_] = {
		memory get user match {
			case Some(_) =>
				Future("Es ist bereits jemand unter dem Namen '" + user + "' registriert.")
			case None =>
				memory += (user -> password)
				if(memory.size == 0) oper = user
				Future(new AuthAccount(user, user == oper))
		}
	}

	def setPassword(name: String, password: String): Future[String] = {
		memory get name match {
			case Some(saved) if saved == password =>
				Future("Genau dieses Passwort war bereits gesetzt.")
			case Some(saved) =>
				memory += (name -> password)
				Future("Das Passwort wurde gesetzt.")
			case None =>
				Future("Es wurde kein Nutzer mit diesem Namen gefunden.")
		}
	}

	def isValid(user : String, password : String) : Future[_] = {
		memory get user match {
			case Some(saved) if saved == password =>
				Future(new AuthAccount(user, user == oper))
			case Some(saved) =>
				Future("Das angegebene Passwort stimmt nicht.")
			case None =>
				Future("Es wurde kein Nutzer mit diesem Namen gefunden.")
		}
	}

	def lookup(user : String) = {
		if(memory.contains(user)) Future(new AuthAccount(user, user == oper))
		else Future("Es wurde kein Nutzer mit diesem Namen gefunden.")
	}
}
