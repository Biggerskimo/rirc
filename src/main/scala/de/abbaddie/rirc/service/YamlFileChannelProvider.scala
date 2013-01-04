package de.abbaddie.rirc.service

import de.abbaddie.rirc.main.{Server, Channel}
import scala.concurrent.duration._
import beans.BeanProperty
import org.yaml.snakeyaml.{DumperOptions, Yaml}
import org.yaml.snakeyaml.constructor.Constructor
import collection.immutable.HashMap
import scala.collection.JavaConversions._
import java.io.{FileReader, FileWriter}
import org.joda.time.DateTime
import java.util.{List => JavaList}
import java.util.{ArrayList => JavaArrayList}

class YamlFileChannelProvider extends ChannelProvider {
	val yaml = new Yaml(new Constructor(classOf[YamlChannel]))
	var channels : Map[String, YamlChannel] = HashMap()
	val dumperOptions = new DumperOptions
	dumperOptions.setPrettyFlow(true)

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
		yaml.loadAll(new FileReader("channels.yml")) foreach {
			case chan : YamlChannel =>
				channels += (chan.name -> chan)
			case _ =>
		}
	}

	protected def save() {
		val writer = new FileWriter("channels.yml")
		val result = channels.values map(yaml.dumpAsMap(_)) mkString("---\n")
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

	var ops : Seq[String] = opsList
	var voices : Seq[String] = voicesList
}