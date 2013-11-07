package de.abbaddie.rirc.connector

import scala.io.Source
import de.abbaddie.rirc.connector.IrcResponse._
import grizzled.slf4j.Logging

object IrcMotdHandler extends Logging {
	var commands : List[IrcServerResponse] = List()

	def read(filename : String) {
		try {
			val source = Source.fromFile(filename)
			try {
				commands = RPL_MOTDSTART() :: source.getLines().map(RPL_MOTD).toList ::: RPL_ENDOFMOTD() :: Nil
				info("loaded motd file " + filename)
			} finally {
				source.close()
			}
		} catch {
			case e : Exception =>
				commands = List(ERR_NOMOTD())
				error("error loading motd file " + filename, e)
		}
	}

	def send(user : IrcUser) {
		commands.foreach(user.ds !)
	}
}
