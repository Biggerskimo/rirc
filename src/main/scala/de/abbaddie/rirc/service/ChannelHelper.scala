package de.abbaddie.rirc.service

import de.abbaddie.rirc.main.{ChannelUserInformation, User, Channel, Server}
import de.abbaddie.rirc.message._
import de.abbaddie.rirc.main.Channel
import de.abbaddie.rirc.message.ChannelCreationMessage
import scala.Some
import de.abbaddie.rirc.message.JoinMessage
import de.abbaddie.rirc.message.PrivilegeChangeMessage

object ChannelHelper {
	val suser = Server.systemUser

	val startup = () => {
		Server.channelProvider.registeredChannels.keys foreach { name =>
			Server.channels get name match {
				case Some(channel : Channel) if !channel.users.contains(Server.systemUser) =>
					Server.events ! JoinMessage(channel, Server.systemUser)
				case None =>
					val channel = new Channel(name)
					Server.events ! ChannelCreationMessage(channel, Server.systemUser)
					Server.events ! JoinMessage(channel, Server.systemUser)
			}
		}
	}

	val resync = (channel : Channel) => {
		if(channel.isRegistered) {
			implicit val desc = Server.channelProvider.registeredChannels(channel.name)

			// presence of Service
			if(!channel.users.contains(Server.systemUser)) {
				Server.events ! JoinMessage(channel, suser)
			}

			// iterate users
			channel.users foreach {case (user, info) => checkUserPart(channel, user, info) }
		}
	}

	val setNone = (channel : Channel, user : User, info : ChannelUserInformation) => {
		if(info.isOp)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, OP, UNSET)
		if(info.isVoice)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, VOICE, UNSET)
	}
	val setVoice = (channel : Channel, user : User, info : ChannelUserInformation) => {
		if(info.isOp)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, OP, UNSET)
		if(!info.isVoice)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, VOICE, SET)
	}
	val setOp = (channel : Channel, user : User, info : ChannelUserInformation) => {
		if(!info.isOp)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, OP, SET)
		if(info.isVoice)
			Server.events ! PrivilegeChangeMessage(channel, suser, user, VOICE, UNSET)
	}

	def checkUser(channel : Channel, user : User) {
		checkUserPart(channel, user, channel.users(user))(Server.channelProvider.registeredChannels(channel.name))
	}

	def checkUser(channel : Channel, user : User, acc : Option[AuthAccount]) {
		checkUserPart(channel, user, acc, channel.users(user))(Server.channelProvider.registeredChannels(channel.name))
	}

	def checkUserPart(channel : Channel, user2 : User, info2 : ChannelUserInformation)(implicit desc : ChannelDescriptor) {
		checkUserPart(channel, user2, user2.authacc, info2)(desc)
	}

	def checkUserPart(channel : Channel, user2 : User, acc : Option[AuthAccount], info2 : ChannelUserInformation)(implicit desc : ChannelDescriptor) {
		(user2, info2) match {
			case (user, info) if user == suser =>
				setOp(channel, user, info)
			case (user, info) if acc.isEmpty =>
				setNone(channel, user, info)
			case (user, info) if desc.owner == acc.get.id =>
				setOp(channel, user, info)
			case (user, info) if desc.ops contains acc.get.id =>
				setOp(channel, user, info)
			case (user, info) if desc.voices contains acc.get.id =>
				setVoice(channel, user, info)
			case (user, info) =>
				setNone(channel, user, info)
		}
	}
}
