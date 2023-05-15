package de.abbaddie.rirc.connector.irc

import scala.util.matching.Regex
import scala.collection.JavaConverters._

object IrcHostRewriter {
	var replaces = List[(Regex, String)]()
	// todo ugly global variable
	IrcConstants.config.getConfigList("replaceHost").asScala.foreach { tuple =>
		val find = tuple.getString("find")
		val replacement = tuple.getString("replacement")
		val regex = ("(?i)" + find).r
		replaces ::= (regex, replacement)
	}
	
	def rewrite(str : String) = replaces.foldLeft(str) {
		case (string, (regex, replacement)) =>
			regex.replaceAllIn(string, replacement)
	}
}
