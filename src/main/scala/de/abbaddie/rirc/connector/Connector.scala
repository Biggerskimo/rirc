package de.abbaddie.rirc.connector

import de.abbaddie.rirc.main.RircModule

trait Connector extends RircModule {
	def start()
	
	def queueLength : Int
}
