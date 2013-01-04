package de.abbaddie.rirc.message

import de.abbaddie.rirc.main.{Channel, User}
import de.abbaddie.rirc.service.ServiceMessage

case class ServiceCommandMessage(channel : Channel, user : User, command : String, params : String*) extends Message with ServiceMessage
