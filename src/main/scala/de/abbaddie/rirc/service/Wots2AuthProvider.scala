package de.abbaddie.rirc.service

import collection.immutable.HashMap
import akka.actor.{ActorSystem, Actor}
import com.typesafe.config.ConfigFactory
import de.abbaddie.wot.rirc._
import akka.pattern._
import de.abbaddie.rirc.main.{User, Server}
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import concurrent.{Promise, Future}
import grizzled.slf4j.Logging
import org.mindrot.jbcrypt.BCrypt

class Wots2AuthProvider extends AuthProvider with Logging {
	val key = "wots2"
	implicit val dispatcher = Server.actorSystem.dispatcher
	implicit val timeout = Timeout(1, TimeUnit.SECONDS)

	protected def comInternal(user : String, password : String, checkPassword : Boolean) = {
		remoteActor ? new AuthRequest(user) map {
			case resp : AuthResponse if resp.found && (!checkPassword || BCrypt.checkpw(password, resp.hash)) =>
				Some(new AuthAccount(user, resp.isOper))
			case resp : AuthResponse =>
				None
			case resp =>
				error("Dropped illegal response from wots2-connector: " + resp)
				None
		}
	}

	def register(user : String, password : String, mail : String) : Future[Option[String]] = Future(Some("please register via the website."))

	def isValid(user : String, password : String) = comInternal(user, password, checkPassword = true)

	def lookup(user : String) = comInternal(user, "", checkPassword = false)

	val config = ConfigFactory.parseString("akka { actor { provider = \"akka.remote.RemoteActorRefProvider\" } }")
	val actorSystem = ActorSystem("wots2-auth-actors", config)
	val remoteActor = actorSystem.actorFor("akka://rirc-connector@127.0.0.1:2552/user/connector")
}