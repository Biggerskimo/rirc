package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.User

case class NickchangeMessage(user : User, oldNick : String, newNick : String) extends Message with BroadcastMessage
