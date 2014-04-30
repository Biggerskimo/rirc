package de.abbaddie.rirc.service

import de.abbaddie.rirc.main.{RircModule, Channel}
import org.joda.time.DateTime
import java.io.OptionalDataException

trait ChannelProvider extends RircModule {
	def register(channel : Channel, owner : AuthAccount, oper : AuthAccount)

	def registeredChannels : Map[String, ChannelDescriptor]
}

trait ChannelDescriptor {
	def name : String
	def owner : String
	def oper : String
	def ops : Seq[String]
	def voices : Seq[String]
	def registration : String
	def bans : Seq[ChannelBan]

	def getAdditional(key : String) : Option[String]
	def setAdditional(key : String, value : String)

	def getUserSetting(account : AuthAccount, key : String) : Option[String]
	def setUserSetting(account : AuthAccount, key : String, value : String)

	def setOwner(owner : String)
	def addOp(op : String)
	def addVoice(voice : String)
	def rmOp(op : String)
	def rmVoice(voice : String)
	def addBan(mask : String, expiration: Option[DateTime], message : String, by : String)
	def rmBan(ban : ChannelBan)
}

trait ChannelBan {
	def mask : String
	def expiration : Option[DateTime]
	def message : String
	def by : String
}
