package de.abbaddie.rirc.connector

class IrcOutgoingLine(val source : String, val command : String, val colonPos : Int, val params : String*) {
	def this(source : String, command : String, params : String*) = this(source, command, 1, params: _*)

	override def toString = {
		if(params == null || params == None || params.length == 0)
			":" + source + " " + command + "\n"
		else if(params.size == colonPos)
			":" + source + " " + command + " :" + params(0) + "\n"
		else if(params.size > colonPos)
			":" + source + " " + command + params.slice(0, params.length - colonPos).mkString(" ", " ", "") + params.slice(params.length - colonPos, params.length).mkString(" :", " ", "\n")
		else
			":" + source + " " + command + params.mkString(" ", " ", "\n")
	}
}
