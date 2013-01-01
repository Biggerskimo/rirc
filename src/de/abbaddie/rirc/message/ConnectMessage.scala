package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.User

case class ConnectMessage(user : User) extends Message with UserMessage with ServerMessage
