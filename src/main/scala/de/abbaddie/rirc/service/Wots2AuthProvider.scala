package de.abbaddie.rirc.service

import akka.actor._
import com.typesafe.config.{Config, ConfigFactory}
import de.abbaddie.wot.rirc._
import akka.pattern._
import de.abbaddie.rirc.main.{ConnectMessage, User, DefaultRircModule, Server}
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import concurrent.Future
import grizzled.slf4j.Logging
import org.mindrot.jbcrypt.BCrypt

class Wots2AuthProvider extends DefaultRircModule with AuthProvider with Logging {
	implicit val dispatcher = Server.actorSystem.dispatcher
	implicit val timeout = Timeout(1, TimeUnit.SECONDS)

	override def init() {}

	protected def comInternal(user : String, password : String, checkPassword : Boolean) = {
		(remoteActor ? new AuthRequest(user))
		.map {
			case resp : AuthResponse if resp.found && (!checkPassword || BCrypt.checkpw(password, resp.hash)) =>
				new AuthAccount(user, resp.isOper)
			case resp : AuthResponse if resp.found =>
				"Der Account " + user + " wurde nicht gefunden."
			case resp : AuthResponse =>
				"Das Passwort ist falsch."
			case resp =>
				error("Dropped illegal response from wots2-connector: " + resp)
				"Unbekannter Fehler."
		}

		.recover {
			case ex =>
				error(ex)
				"Es konnte keine Verbindung aufgebaut werden; probier es später nochmal oder mecker den Admin an."
		}
	}

	def register(user : String, password : String, mail : String) : Future[String] = Future("Du musst dich über die Webseite registrieren.")

	def isValid(user : String, password : String) = comInternal(user, password, checkPassword = true)

	def lookup(user : String) = comInternal(user, "", checkPassword = false)

	val remoteconfig = ConfigFactory.parseString("akka { actor { provider = \"akka.remote.RemoteActorRefProvider\" } }")
	val actorSystem = ActorSystem("wots2-auth-actors", remoteconfig)
	val remoteActor = actorSystem.actorFor("akka://rirc-connector@127.0.0.1:2552/user/connector")
}