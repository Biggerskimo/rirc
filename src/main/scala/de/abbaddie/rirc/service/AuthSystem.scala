package de.abbaddie.rirc.service

import akka.actor.Actor
import de.abbaddie.rirc.main._
import grizzled.slf4j.Logging
import de.abbaddie.rirc.main.Message._

class AuthSystem extends Actor with Logging {
	import context._

	def receive = {
		case AuthStart(user, name, password) =>
			Server.authProvider.isValid(name, password) onSuccess {
				case acc: AuthAccount =>
					Server.events ! AuthSuccess(user, acc)
				case message : String =>
					Server.events ! AuthFailure(user, name, message)
				case ex =>
					error("Invalid message from AuthProvider:" + ex)
			}
		case RegistrationStart(user, name, password, emailAddress) =>
			Server.authProvider.register(name, password, emailAddress) onSuccess {
				case acc : AuthAccount =>
					Server.events ! RegistrationSuccess(user, acc)
				case message : String =>
					Server.events ! RegistrationFailure(user, name, message)
				case ex =>
					error("Invalid message from AuthProvider:" + ex)
			}
	}
}
