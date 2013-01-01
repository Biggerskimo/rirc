package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.User

case class QuitMessage(user : User, message : Option[String]) extends Message with BroadcastMessage