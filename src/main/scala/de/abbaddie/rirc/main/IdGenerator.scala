package de.abbaddie.rirc.main

import collection.immutable.HashMap

object IdGenerator {
	var gens : Map[String, Int] = HashMap()

	def apply(gen : String) : Int = {
		synchronized {
			val counter = gens.get(gen).getOrElse(0) + 1
			gens += (gen -> counter)

			counter
		}
	}
}
