package de.abbaddie.rirc.service

import de.abbaddie.rirc.main.Channel
import org.joda.time.DateTime

trait ChannelProvider {
	def register(channel : Channel, owner : AuthAccount, oper : AuthAccount)

	def registeredChannels : Map[String, ChannelDescriptor]
}

trait ChannelDescriptor {
	def owner : String
	def oper : String
	def ops : Seq[String]
	def voices : Seq[String]
	def registration : DateTime

	def setOwner(owner : String)
	def addOp(op : String)
	def addVoice(voice : String)
	def rmOp(op : String)
	def rmVoice(voice : String)
}