package de.abbaddie.rirc.service

case class AuthAccount(name : String, isOper : Boolean) {
	final val id = name.toLowerCase
}