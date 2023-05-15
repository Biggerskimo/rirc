package de.abbaddie.rirc.service

import scala.concurrent.Future
import de.abbaddie.rirc.main.DefaultRircModule
import scala.collection.JavaConverters._
import grizzled.slf4j.Logging

class MultiAuthProvider extends DefaultRircModule with AuthProvider with Logging {
	var children = List[(String, AuthProvider)]()
	override def init() {
		config.getConfigList("children").asScala.foreach { subconfig =>
			val module = Class.forName(subconfig.getString("class")).newInstance().asInstanceOf[AuthProvider]
			module.config = subconfig
			val prefix = if(subconfig.hasPath("prefix")) subconfig.getString("prefix") else ""
			children ::= (prefix, module)
		}
		children = children.reverse
		
		children.foreach(_._2.init())
	}

	override def lookup(name: String): Future[_] = {
		def rek : List[(String, AuthProvider)] => Future[_] = {
			case Nil =>
				Future.failed(new Exception("Es wurde kein Nutzer mit diesem Namen gefunden."))
			case (prefix, child) :: rest if !name.startsWith(prefix) =>
				rek(rest)
			case (prefix, child) :: rest =>
				child.lookup(name) fallbackTo rek(rest)
		}
		rek(children)
	}

	override def register(name: String, password: String, mail: String): Future[_] = {
		def rek : List[(String, AuthProvider)] => Future[_] = {
			case Nil =>
				Future.failed(new Exception("Es wurde kein Nutzer mit diesem Namen gefunden."))
			case (prefix, child) :: rest if !name.startsWith(prefix) =>
				rek(rest)
			case (prefix, child) :: rest =>
				child.register(name, password, mail) fallbackTo rek(rest)
		}
		rek(children)
	}

	override def setPassword(name: String, password: String): Future[String] = {
		def rek : List[(String, AuthProvider)] => Future[String] = {
			case Nil =>
				Future.failed(new Exception("Es wurde kein Nutzer mit diesem Namen gefunden."))
			case (prefix, child) :: rest if !name.startsWith(prefix) =>
				rek(rest)
			case (prefix, child) :: rest =>
				child.setPassword(name, password) fallbackTo rek(rest)
		}
		rek(children)
	}

	override def isValid(name: String, password: String): Future[_] = {
		def rek : List[(String, AuthProvider)] => Future[_] = {
			case Nil =>
				Future.failed(new Exception("Es wurde kein Nutzer mit diesem Namen gefunden."))
			case (prefix, child) :: rest if !name.startsWith(prefix) =>
				rek(rest)
			case (prefix, child) :: rest =>
				child.isValid(name, password) fallbackTo rek(rest)
		}
		rek(children)
	}
}
