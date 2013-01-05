package de.abbaddie.rirc.main

object UserUtil {
	def checkOp(channel : Channel, user : User) = {
		if(user.isOper) true
		else channel.users get user match {
			case Some(info) if info.isOp => true
			case Some(info) if channel.isRegistered && user.authacc.isDefined =>
				val desc = Server.channelProvider.registeredChannels.get(channel.name).get
				val accId = user.authacc.get.id
				(desc.owner == accId) || (desc.ops contains accId)
			case _ => false
		}
	}

	def checkInviteOnly(channel : Channel, user : User) =
		!channel.isInviteOnly ||
		checkOp(channel, user) ||
		(channel.invited contains user)

	def checkPasswordProtection(channel : Channel, user : User, password : Option[String]) =
		channel.protectionPassword.isEmpty ||
		checkOp(channel, user) ||
		(channel.invited contains user) ||
		password.isDefined && channel.protectionPassword.get == password.get

	def checkBanned(channel : Channel, user : User) = !(channel.bans exists(matchesMask(user, _)))

	val regex = """^([^!@]*)!?([^!@]*)@?([^!@]*)$""".r
	def matchesMask(user : User, mask : String) = { mask match {
			case regex(nickmask, usermask, hostmask) =>
				matchConcrete(user.nickname, nickmask) &&
				matchConcrete(user.username, usermask) &&
				matchConcrete(user.hostname, hostmask)
			case _ =>
				false
		}
	}
	def cleanMask(mask : String) = { mask match {
			case regex(nickmask, "", "") => nickmask + "!*@*"
			case regex("", usermask, "") => "*!" + usermask + "@*"
			case regex("", "", hostmask) => "*!*@" + hostmask
			case regex(nickmask, usermask, "") => nickmask + "!" + usermask + "@*"
			case regex(nickmask, "", hostmask) => nickmask + "!*@" + hostmask
			case regex("", nickmask, hostmask) => "*!" + nickmask + "@" + hostmask
			case regex(nickmask, usermask, hostmask) => nickmask + "!" + usermask + "@" + hostmask
			case _ => "I!AM@INVALID"
		}
	}

	protected def matchConcrete(string : String, mask : String) = string.matches("\\Q" + mask.replace("*", "\\E.*\\Q") + "\\E")
}
