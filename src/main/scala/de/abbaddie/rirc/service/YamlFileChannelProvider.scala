package de.abbaddie.rirc.service

import de.abbaddie.rirc.main.{DefaultRircModule, Server, Channel}
import scala.concurrent.duration._
import beans.BeanProperty
import org.yaml.snakeyaml.{DumperOptions, Yaml}
import org.yaml.snakeyaml.constructor.Constructor
import collection.immutable.HashMap
import scala.collection.JavaConverters._
import java.io.{File, FileReader, FileWriter}
import org.joda.time.DateTime
import java.util.{List => JavaList}
import java.util.{ArrayList => JavaArrayList}
import java.util.{Map => JavaMap, HashMap => JavaHashMap}
import de.abbaddie.rirc.BackupFileProvider

class YamlFileChannelProvider extends DefaultRircModule with ChannelProvider with BackupFileProvider {
	val yaml = new Yaml(new Constructor(classOf[YamlChannel]))
	var channels : Map[String, YamlChannel] = HashMap()
	val dumperOptions = new DumperOptions
	dumperOptions.setPrettyFlow(true)

	def filename = "channels.yml"

	def register(channel: Channel, owner: AuthAccount, oper : AuthAccount) {
		synchronized {
			val ychannel = new YamlChannel()
			ychannel.name = channel.name
			ychannel.owner = owner.id
			ychannel.oper = oper.id
			ychannel.registration = DateTime.now
			channels += (channel.name -> ychannel)
		}
	}

	def registeredChannels = channels

	protected def load() {
		val file = getFile()
		if(!file.exists()) file.createNewFile()
		yaml.loadAll(new FileReader(file)).asScala foreach {
			case chan : YamlChannel =>
				channels += (chan.name -> chan)
			case _ =>
		}
	}

	protected def save() {
		val writer = new FileWriter(getFile(write = true))
		val result = channels.values.map(yaml.dumpAsMap(_)).mkString("---\n")
		writer.write(result)
		writer.close()
	}

	load()

	implicit val dispatcher = Server.actorSystem.dispatcher
	Server.actorSystem.scheduler.schedule(0 seconds, 5 seconds)(save())
}

class YamlChannel extends ChannelDescriptor {
	@BeanProperty
	var name : String = null
	@BeanProperty
	var registration : DateTime = null
	@BeanProperty
	var oper : String = null
	@BeanProperty
	var owner : String = null
	@BeanProperty
	var opsList : JavaList[String] = new JavaArrayList()
	@BeanProperty
	var voicesList : JavaList[String] = new JavaArrayList()
	@BeanProperty
	var banList : JavaList[YamlBan] = new JavaArrayList()
	@BeanProperty
	var additional : JavaMap[String, String] = new JavaHashMap()

	def getAdditional(key : String) = if(additional.containsKey(key)) Some(additional.get(key)) else None
	def setAdditional(key : String, value : String) = additional.put(key, value)

	def ops : Seq[String] = opsList.asScala
	def voices : Seq[String] = voicesList.asScala
	def bans : Seq[YamlBan] = banList.asScala

	def addOp(op : String) { opsList add op }
	def addVoice(voice : String) { voicesList add voice }
	def rmOp(op : String) { opsList remove op }
	def rmVoice(voice : String) { voicesList remove voice }
	def addBan(mask : String, expiration : Option[DateTime], message : String, by : String) {
		banList add new YamlBan(mask, expiration, message, by)
	}
	def rmBan(ban : ChannelBan) { banList remove ban }
}

class YamlBan(@BeanProperty var mask : String,
		var expiration : Option[DateTime],
		@BeanProperty var message : String,
		@BeanProperty var by : String) extends ChannelBan {

	def getExpiration = expiration match {
		case Some(time) => time.getMillis
		case None => 0L
	}

	def setExpiration(millis : Long) {
		if(millis == 0L) expiration = None
		else expiration = Some(new DateTime(millis))
	}
}