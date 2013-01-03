package de.abbaddie.rirc.service

import akka.actor.Actor
import concurrent.Future
import de.abbaddie.rirc.main.Server

class AuthSystem extends Actor {
	import context._

	def receive = {
		case AuthStart(user, name, password) =>
			Server.authProvider.isValid(name, password) onSuccess {
				case Some(acc: AuthAccount) =>
					Server.events ! AuthSuccess(user, acc)
				case None =>
					Server.events ! AuthFailure(user, name)
			}
	}
}
