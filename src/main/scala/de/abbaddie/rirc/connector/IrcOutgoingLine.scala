package de.abbaddie.rirc.connector

class IrcOutgoingLine(val source : Option[String], val command : String, val colonPos : Int, val params : String*) {
	def this(source : Option[String], command : String, params : String*) = this(source, command, 1, params: _*)

	override def toString = {
		val str = new StringBuilder

		source match {
			case Some(prefix) =>
				str += ':'
				str ++= prefix
				str += ' '
			case None =>
		}
		str ++= command

		if(params == null || params == None || params.length == 0) {}
		else if(params.size == colonPos)
			str ++= " :" + params(0)
		else if(params.size > colonPos)
			str ++= params.slice(0, params.length - colonPos).mkString(" ", " ", "") + params.slice(params.length - colonPos, params.length).mkString(" :", " ", "")
		else
			str ++= " " + params.mkString(" ")

		str += '\n'
		str.toString()
	}
}
