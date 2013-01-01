package de.abbaddie.rirc.connector

import de.abbaddie.rirc.main.{Server, Channel, User}

abstract class IrcResponse {
	def toIrcOutgoingLine(user : IrcUser) : IrcOutgoingLine
}

/** SERVER **/
abstract class IrcServerResponse(val numeric : Int, val message : String, val before : String*) extends IrcResponse {
	def this(numeric: Int, message : String) = this(numeric, message, Nil: _*)
	def this(numeric: Int) = this(numeric, null)

	def toIrcOutgoingLine(user: IrcUser) = {
		val params = List(user.nickname) ++ before :+ message

		new IrcOutgoingLine(IrcConstants.OUR_HOST, numericToString, params: _*)
	}

	def numericToString = {
		if(numeric < 10)
			"00" + numeric
		else if(numeric < 100)
			"0" + numeric
		else
			numeric.toString
	}
}

case class IrcSimpleResponse(command : String, params : String*) extends IrcResponse {
	def toIrcOutgoingLine(user: IrcUser) = new IrcOutgoingLine(IrcConstants.OUR_HOST, command, params : _*)
}

// http://tools.ietf.org/html/rfc2812#section-5.1
case class RPL_WELCOME() extends IrcServerResponse(1, "Welcome to the " + IrcConstants.OUR_NAME)
case class RPL_YOURHOST() extends IrcServerResponse(2, "Your host is " + IrcConstants.OUR_HOST + ", running version" + IrcConstants.OUR_VERSION)
case class RPL_CREATED() extends IrcServerResponse(3, "This server was created a not so long time ago.")
case class RPL_MYINFO() extends IrcServerResponse(4, IrcConstants.OUR_HOST + " " + IrcConstants.OUR_VERSION + "  ")

case class RPL_WHOISUSER(user : User) extends IrcServerResponse(311, user.realname, user.nickname, user.username, user.address.getHostName, "*")
case class RPL_WHOISSERVER(user : User) extends IrcServerResponse(312, IrcConstants.OUR_NAME, user.nickname, IrcConstants.OUR_HOST)

case class RPL_WHOISIDLE(user : User) extends IrcServerResponse(317, "seconds idle", user.nickname, "1337") // TODO
case class RPL_ENDOFWHOIS(user : User) extends IrcServerResponse(318, "End of /WHOIS list")
case class RPL_WHOISCHANNELS(user : User) extends IrcServerResponse(319,
	Server.channels
			.filter(_._2.users.contains(user))
			.map({ e => (if(e._2.users(user).isOp) "@" else if(e._2.users(user).isVoice) "+" else "") + e._2.name })
			.mkString(" "),
	user.nickname)

case class RPL_ENDOFWHO() extends IrcServerResponse(315, "End of /WHO list")

case class RPL_CHANNELMODEIS(channel : Channel) /* sic */extends IrcResponse { // 324
	def toIrcOutgoingLine(user: IrcUser): IrcOutgoingLine = {
		new IrcOutgoingLine(
			IrcConstants.OUR_HOST,
			"324",
			0,
			user.nickname,
			channel.name,
			"+",
			""
		)
	}
}

case class RPL_CREATIONTIME(channel : Channel) extends IrcResponse { // 329
	def toIrcOutgoingLine(user: IrcUser): IrcOutgoingLine = {
		new IrcOutgoingLine(
			IrcConstants.OUR_HOST,
			"329",
			0,
			user.nickname,
			channel.name,
			(channel.creation.getMillis / 1000).toString
		)
	}
}

case class RPL_NOTOPIC(channel : Channel) extends IrcServerResponse(331, "No topic is set", channel.name)
case class RPL_TOPIC(channel : Channel) extends IrcServerResponse(332, channel.topic)

case class RPL_WHOREPLY(user : User) extends IrcResponse { // 352
	def toIrcOutgoingLine(user: IrcUser): IrcOutgoingLine = {
		new IrcOutgoingLine(
			IrcConstants.OUR_HOST,
			"352", // (numeric)
			2, // (colon before hop count)
			"*", // channels
			user.username,
			user.address.getHostName,
			user.nickname,
			"H", // Here/Gone
			"0", // hop count
			user.realname
		)
	}
}
case class RPL_NAMEREPLY(channel : Channel) extends IrcResponse { // 353
	def toIrcOutgoingLine(user: IrcUser): IrcOutgoingLine = {
		val users = channel.users

		var params : Seq[String] = users.map(e => (if(e._2.isOp) "@" else if(e._2.isVoice) "+" else "") + e._1.nickname).toSeq
		params +:= channel.name
		params +:= "="
		params +:= user.nickname

		new IrcOutgoingLine(
			IrcConstants.OUR_HOST,
			"353",
			users.size,
			params: _*
		)
	}
}

case class RPL_ENDOFNAMES(channel : Channel) extends IrcServerResponse(366, "End of /NAMES list", channel.name)

case class ERR_NOSUCHNICK(name : String) extends IrcServerResponse(401, "No such nick/channel")

case class ERR_NOSUCHCHANNEL(name : String) extends IrcServerResponse(403, "No such channel", name)

case class ERR_NICKNAMEINUSE(name : String) extends IrcServerResponse(433, "Nickname is already in use", name)

case class ERR_NOTONCHANNEL(channel : Channel) extends IrcServerResponse(442, "You're not on that channel", channel.name)

case class ERR_NOTREGISTERED() extends IrcServerResponse(451, "You have not registered")

case class ERR_CHANOPRIVSNEEDED(channel : Channel) /* sic */ extends IrcServerResponse(482, "You're not channel operator")

/** CLIENT **/
abstract class IrcClientResponse(val source : User, val command : String, val params : String*) extends IrcResponse {
	def toIrcOutgoingLine(user: IrcUser) = {
		new IrcOutgoingLine(userSourceString(source), command, params : _*)
	}

	def userSourceString(user : User) = user.nickname + "!" + user.username + "@" + user.address.getHostName
}

case class MSG_JOIN(channel : Channel, user : User) extends IrcClientResponse(user, "JOIN", channel.name)

case class MSG_PRIVMSG(target : String, user : User, text : String) extends IrcClientResponse(user, "PRIVMSG", target, text) {
	def this(channel : Channel, user : User, text : String) = this(channel.name, user, text)
	def this(to : User, from : User, text : String) = this(to.nickname, from, text)
}

case class MSG_NOTICE(target : String, user : User, text : String) extends IrcClientResponse(user, "NOTICE", target, text) {
	def this(channel : Channel, user : User, text : String) = this(channel.name, user, text)
	def this(to : User, from : User, text : String) = this(to.nickname, from, text)
}

case class MSG_PART(channel : Channel, user : User, message : Option[String]) extends IrcClientResponse(user, "PART", channel.name +: message.toSeq :_*)

case class MSG_QUIT(user : User, message : Option[String]) extends IrcClientResponse(user, "QUIT", message.toSeq :_*)

case class MSG_NICK(user : User, oldNick : String, newNick : String) extends IrcClientResponse(user, "NICK", newNick) {
	override def userSourceString(user : User) = oldNick + "!" + user.username + "@" + user.address.getHostName
}

case class MSG_TOPIC(channel : Channel, user : User, topic : String) extends IrcClientResponse(user, "TOPIC", channel.name, topic)