package de.abbaddie.rirc.service

class AuthAccount(val name : String, protected val provider : AuthProvider) {
	final def id = provider.key + "-" + name
}
