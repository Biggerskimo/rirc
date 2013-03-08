package de.abbaddie.rirc.service

import de.abbaddie.rirc.main.{Server, DefaultRircModule}
import scala.concurrent.duration._
import concurrent.Future
import beans.BeanProperty
import org.yaml.snakeyaml.{DumperOptions, Yaml}
import org.yaml.snakeyaml.constructor.Constructor
import java.io.{FileWriter, FileReader}
import scala.collection.JavaConverters._
import collection.immutable.HashMap
import org.mindrot.jbcrypt.BCrypt

class YamlFileAuthProvider extends DefaultRircModule with AuthProvider {
	val yaml = new Yaml(new Constructor(classOf[YamlAuthAccount]))
	var accounts : Map[String, YamlAuthAccount] = HashMap()
	val dumperOptions = new DumperOptions
	dumperOptions.setPrettyFlow(true)

	def init() {}

	def register(name: String, password: String, mail: String): Future[_] = {
		accounts get name match {
			case Some(_) =>
				Future("Es ist bereits jemand unter dem Namen '" + name + "' registriert.")
			case None =>
				val acc = new YamlAuthAccount
				acc.name = name
				acc.hash = BCrypt.hashpw(password, BCrypt.gensalt(12))
				acc.isOper = false
				acc.emailAddress = mail
				accounts += (name -> acc)
				Future(new AuthAccount(name, false))
		}

	}

	def isValid(name: String, password: String): Future[_] = {
		accounts get name match {
			case Some(account) if BCrypt.checkpw(password, account.hash) =>
				Future(new AuthAccount(name, account.isOper))
			case Some(account) =>
				Future("Das Passwort ist falsch.")
			case None =>
				Future("Der Account " + name + " wurde nicht gefunden.")
		}
	}

	def lookup(name: String): Future[_] = {
		accounts get name match {
			case Some(account) => Future(account)
			case None => Future("Es wurde kein Nutzer mit diesem Namen gefunden.")
		}
	}

	protected def load() {
		yaml.loadAll(new FileReader("accounts.yml")).asScala foreach {
			case acc : YamlAuthAccount =>
				accounts += (acc.name -> acc)
			case _ =>
		}
	}

	protected def save() {
		val writer = new FileWriter("accounts.yml")
		val result = accounts.values map(yaml.dumpAsMap(_)) mkString("---\n")
		writer.write(result)
		writer.close()
	}

	load()

	implicit val dispatcher = Server.actorSystem.dispatcher
	Server.actorSystem.scheduler.schedule(0 seconds, 5 seconds)(save())
}

class YamlAuthAccount {
	@BeanProperty
	var name : String = null

	@BeanProperty
	var hash : String = null

	@BeanProperty
	var isOper = false

	@BeanProperty
	var emailAddress : String = null
}
