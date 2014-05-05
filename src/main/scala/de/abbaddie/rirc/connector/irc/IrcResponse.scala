package de.abbaddie.rirc.connector.irc

import de.abbaddie.rirc.main.{Server, Channel, User}
import org.joda.time.DateTime
import org.scala_tools.time.Imports._

abstract class IrcResponse {
	def toIrcOutgoingLine(user : IrcUser) : IrcOutgoingLine
}

object IrcResponse {
	/** SERVER **/
	abstract class IrcServerResponse(val numeric : Int, val message : String, val before : String*) extends IrcResponse {
		def colonPos = 1
		def this(numeric: Int, message : String) = this(numeric, message, Nil: _*)
		def this(numeric: Int) = this(numeric, null)

		def toIrcOutgoingLine(user: IrcUser) = {
			val params = List(user.nickname) ++ before :+ message

			new IrcOutgoingLine(Some(IrcConstants.OUR_HOST), numericToString, colonPos, params: _*)
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
		def colonPos = 1
		def toIrcOutgoingLine(user: IrcUser) = new IrcOutgoingLine(Some(IrcConstants.OUR_HOST), command, colonPos, params : _*)
	}

	// http://tools.ietf.org/html/rfc2812#section-5.1
	case class RPL_WELCOME() extends IrcServerResponse(1, "Welcome to the " + IrcConstants.OUR_NAME)
	case class RPL_YOURHOST() extends IrcServerResponse(2, "Your host is " + IrcConstants.OUR_HOST + ", running version " + IrcConstants.OUR_VERSION)
	case class RPL_CREATED() extends IrcServerResponse(3, "This server was created a not so long time ago.")
	case class RPL_MYINFO() extends IrcServerResponse(4, IrcConstants.OUR_HOST + " " + IrcConstants.OUR_VERSION + " ")
	case class RPL_ISUPPORT() extends IrcServerResponse(5, "are supported by this server",
		"PREFIX=(ov)@+ " +
				"CHANTYPES=# " +
				"CHANMODES=b,k,l,imna " + // TODO
				"MODES=9 " + // TODO
				"MAXCHANNELS=20 " + // TODO
				"MAXBANS=100 " + // TODO
				"NETWORK=" + IrcConstants.OUR_NAME + " " +
				"CASEMAPPING=rfc1459 " +
				"TOPICLEN=200 " + // TODO
				"KICKLEN=200 " + // TODO
				"CHANNELLEN=20 " + // TODO
				"NICKLEN=24 " // TODO
	)

	case class RPL_UMODEIS(modes : String) extends IrcServerResponse(221, "+" + modes)

	case class RPL_LUSERCLIENT() extends IrcServerResponse(251, "There are " + Server.users.size + " users and 0 invisible on 1 servers")
	case class RPL_LUSEROP() extends IrcServerResponse(252, "operator(s) online", "2")
	case class RPL_LUSERCHANNELS() extends IrcServerResponse(254, "channels formed", Server.channels.size.toString)
	case class RPL_LUSERME() extends IrcServerResponse(255, "I have " + Server.users.size + " clients and 0 servers")

	case class RPL_USERHOST(users : User*) extends IrcServerResponse(302, users.map(user => user.nickname + "=+" + user.username + "@" + user.hostname).mkString(" "))

	case class RPL_WHOISUSER(user : User) extends IrcServerResponse(311, user.realname, user.nickname, user.username, user.hostname, "*")
	case class RPL_WHOISSERVER(user : User) extends IrcServerResponse(312, IrcConstants.OUR_NAME, user.nickname, IrcConstants.OUR_HOST)

	case class RPL_WHOISIDLE(user : User) extends IrcServerResponse(317, "seconds idle, signon time", user.nickname, (((DateTime.now.getMillis - user.lastActivity.getMillis) / 1000).round).toString, (user.signOn.getMillis / 1000).toString) // TODO
	case class RPL_ENDOFWHOIS(user : User) extends IrcServerResponse(318, "End of /WHOIS list")
	case class RPL_WHOISCHANNELS(user : User) extends IrcServerResponse(319,
		Server.channels
				.filter(_._2.users.contains(user))
				.map({ e => (if(e._2.users(user).isOp) "@" else if(e._2.users(user).isVoice) "+" else "") + e._2.name })
				.mkString(" "),
		user.nickname)

	case class RPL_ENDOFWHO(name : String) extends IrcServerResponse(315, "End of /WHO list", name)

	case class RPL_CHANNELMODEIS(channel : Channel) /* sic */extends IrcResponse { // 324
	def toIrcOutgoingLine(user: IrcUser): IrcOutgoingLine = {
		new IrcOutgoingLine(
			Some(IrcConstants.OUR_HOST),
			"324",
			0,
			user.nickname,
			channel.name,
			getChanModes(channel)
		)
	}

		def getChanModes(channel : Channel) = {
			var additional : List[String] = Nil
			var str = new StringBuilder("+tn")

			if(channel.isInviteOnly) str += 'i'
			if(channel.protectionPassword.isDefined) {
				str += 'k'
				additional :+= channel.protectionPassword.get
			}

			str.toString() + additional.mkString(" ", " ", "")
		}
	}

	case class RPL_CREATIONTIME(channel : Channel) extends IrcResponse { // 329
	def toIrcOutgoingLine(user: IrcUser): IrcOutgoingLine = {
		new IrcOutgoingLine(
			Some(IrcConstants.OUR_HOST),
			"329",
			0,
			user.nickname,
			channel.name,
			(channel.creation.getMillis / 1000).toString
		)
	}
	}

	case class RPL_WHOISACCOUNT(user : User) extends IrcServerResponse(330, "is logged in as", user.nickname, user.authacc.get.name)
	case class RPL_NOTOPIC(channel : Channel) extends IrcServerResponse(331, "No topic is set", channel.name)
	case class RPL_TOPIC(channel : Channel) extends IrcServerResponse(332, channel.topic.get, channel.name)

	case class RPL_INVITING(channel : Channel, user : User) extends IrcServerResponse(341, channel.name, user.nickname)

	case class RPL_WHOREPLY(user : User, channame : String, userflags : String) extends IrcResponse { // 352
		def toIrcOutgoingLine(user2: IrcUser): IrcOutgoingLine = {
			new IrcOutgoingLine(
				Some(IrcConstants.OUR_HOST),
				"352", // (numeric)
				2, // (colon before hop count)
				user2.nickname,
				channame, // channels
				"~" + user.username,
				user.hostname,
				IrcConstants.OUR_HOST,
				user.nickname,
				userflags, // Here/Gone
				"0", // hop count
				user.realname
			)
		}
	}
	object RPL_WHOREPLY {
		def apply(user : User) = new RPL_WHOREPLY(user, "*", "H")
		def apply(user : User, channel : Channel) = {
			channel.users.get(user) match {
				case Some(info) if info.isOp =>
					new RPL_WHOREPLY(user, channel.name, "H@")
				case Some(info) if info.isVoice =>
					new RPL_WHOREPLY(user, channel.name, "H+")
				case _ =>
					new RPL_WHOREPLY(user, channel.name, "H")
			}
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
			Some(IrcConstants.OUR_HOST),
			"353",
			users.size,
			params: _*
		)
	}
	}

	case class RPL_ENDOFNAMES(name : String) extends IrcServerResponse(366, "End of /NAMES list", name)
	object RPL_ENDOFNAMES { def apply(channel : Channel) = new RPL_ENDOFNAMES(channel.name)}
	case class RPL_BANLIST(channel : Channel, mask : String) extends IrcServerResponse(367, mask, channel.name)
	case class RPL_ENDOFBANLIST(channel : Channel) extends IrcServerResponse(368, "End of channel ban list", channel.name)

	case class RPL_MOTD(line : String) extends IrcServerResponse(372, "- " + line)

	case class RPL_MOTDSTART() extends IrcServerResponse(375, "- " + IrcConstants.OUR_HOST + " Message of the Day -")
	case class RPL_ENDOFMOTD() extends IrcServerResponse(376, "End of /MOTD command")

	case class ERR_NOSUCHNICK(name : String) extends IrcServerResponse(401, "No such nick/channel", name)

	case class ERR_NOSUCHCHANNEL(name : String) extends IrcServerResponse(403, "No such channel", name)

	case class ERR_UNKNOWNCOMMAND(command : String) extends IrcServerResponse(421, "Unknown command", command)
	case class ERR_NOMOTD() extends IrcServerResponse(422, "MOTD File is missing")

	case class ERR_NONICKNAMEGIVEN() extends IrcServerResponse(431, "No nickname given")
	case class ERR_ERRONEUSNICKNAME(name : String) extends IrcServerResponse(432, "Erroneus nickname", name)
	case class ERR_NICKNAMEINUSE(name : String) extends IrcServerResponse(433, "Nickname is already in use", name)

	case class ERR_USERNOTINCHANNEL(channel : Channel, user : User) extends IrcServerResponse(441, "They aren't on that channel", user.nickname, channel.name)
	case class ERR_NOTONCHANNEL(channel : Channel) extends IrcServerResponse(442, "You're not on that channel", channel.name)
	case class ERR_USERONCHANNEL(channel : Channel, user : User) extends IrcServerResponse(443, "is already on channel", user.nickname, channel.name)

	case class ERR_NOTREGISTERED() extends IrcServerResponse(451, "You have not registered")

	case class ERR_NEEDMOREPARAMS() extends IrcServerResponse(461, "Not enough parameters")

	case class ERR_INVITEONLYCHAN(channel : Channel) extends IrcServerResponse(473, "Cannot join channel (+i)", channel.name)
	case class ERR_BANNEDFROMCHAN(channel : Channel) extends IrcServerResponse(474, "Cannot join channel (+b)", channel.name)
	case class ERR_BADCHANNELKEY(channel : Channel) extends IrcServerResponse(475, "Cannot join channel (+k)", channel.name)

	case class ERR_NOPRIVILEGES() extends IrcServerResponse(481, "Permission Denied- You're not an IRC operator")
	case class ERR_CHANOPRIVSNEEDED(channel : Channel) extends IrcServerResponse(482, "You're not channel operator", channel.name)

	case class ERR_UMODEUNKNOWNFLAG() extends IrcServerResponse(501, "Unknown MODE flag")
	case class ERR_USERSDONTMATCH() extends IrcServerResponse(502, "Cant change mode for other users")

	/** CLIENT **/
	abstract class IrcClientResponse(val source : User, val command : String, val params : String*) extends IrcResponse {
		def colonPos = 1
		def toIrcOutgoingLine(user: IrcUser) = {
			new IrcOutgoingLine(Some(userSourceString(source)), command, colonPos, params : _*)
		}

		val userSourceString = (user : User) => user.fullString
	}

	case class MSG_JOIN(channel : Channel, user : User) extends IrcClientResponse(user, "JOIN", channel.name) {
		override def colonPos = 0
	}

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
		override val userSourceString = (user : User) => oldNick + "!~" + user.username + "@" + user.hostname
	}

	case class MSG_TOPIC(channel : Channel, user : User, topic : String) extends IrcClientResponse(user, "TOPIC", channel.name, topic)

	case class MSG_MODE(channel : Channel, user : User, desc : String, additional : String) extends IrcClientResponse(user, "MODE", channel.name :: desc :: additional :: Nil :_*)
	object MSG_MODE { def apply(channel : Channel, user : User, desc : String) = new MSG_MODE(channel, user, desc, "")}

	case class MSG_INVITE(channel : Channel, user : User) extends IrcClientResponse(user, "INVITE", user.nickname, channel.name)

	case class MSG_KICK(channel : Channel, kicker : User, kicked : User, reason : Option[String]) extends IrcClientResponse(kicker, "KICK", channel.name +: kicked.nickname +: reason.toSeq :_*)

	/** SERVICE **/
	abstract class IrcServiceResponse(message : String) extends IrcResponse {
		def toIrcOutgoingLine(user: IrcUser) = {
			new IrcOutgoingLine(Some(IrcConstants.AUTH_USERSTRING), "NOTICE", user.nickname, message)
		}
	}

	case class SVC_AUTHSUCCESS() extends IrcServiceResponse("Du wurdest erfolgreich angemeldet.")

	case class SVC_AUTHFAILURE(message : String) extends IrcServiceResponse(message)

	case class SVC_REGISTRATIONSUCCESS() extends IrcServiceResponse("Du wurdest erfolgreich registriert.")

	case class SVC_REGISTRATIONFAILURE(message : String) extends IrcServiceResponse(message)

	/** EXTRA **/
	case class CMD_PING() extends IrcResponse {
		def toIrcOutgoingLine(user: IrcUser): IrcOutgoingLine = {
			new IrcOutgoingLine(
				None,
				"PING",
				IrcConstants.OUR_HOST
			)
		}
	}
}
