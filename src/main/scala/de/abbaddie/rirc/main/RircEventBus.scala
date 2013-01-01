package de.abbaddie.rirc.main

import akka.event.ActorEventBus
import akka.actor.{Actor, ActorRef}
import de.abbaddie.rirc.message._
import collection._
import de.abbaddie.rirc.message.ChannelClassifier
import de.abbaddie.rirc.message.UserClassifier
import scala.Some
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import akka.pattern.ask
import scala.concurrent.Future
import grizzled.slf4j.Logging

class RircEventBus extends ActorEventBus with Logging {
	type Event = Message
	type Classifier = RircEventClassifier

	val DUMMY = AnyRef
	// used as a map containing sets
	val subscriptions : concurrent.Map[RircEventClassifier, concurrent.Map[ActorRef, Any]] = concurrent.TrieMap()

	val important : concurrent.Map[ActorRef, Any] = concurrent.TrieMap()

	def subscribe(subscriber: ActorRef, to: RircEventClassifier): Boolean = {
		if(!subscriptions.contains(to)) {
			subscriptions.putIfAbsent(to, concurrent.TrieMap())
		}
		val Some(set) = subscriptions get to

		set.putIfAbsent(subscriber, DUMMY) == None
	}

	def unsubscribe(subscriber: ActorRef, from: RircEventClassifier): Boolean = {
		subscriptions get from match {
			case Some(set) =>
				set.remove(subscriber, DUMMY)
			case None =>
				false
		}
	}

	def makeImportant(subscriber: ActorRef) {
		important += (subscriber -> DUMMY)
	}

	def unsubscribe(subscriber: ActorRef) {
		subscriptions.keysIterator foreach(unsubscribe(subscriber, _))
	}

	def publish(event: Message) {
		var actors: Set[ActorRef] = mutable.HashSet()

		if(event.isInstanceOf[BroadcastMessage]) {
			//actors += Server.actor
			actors ++= subscriptions.values flatMap(map => map.keys)
		}
		else {
			/*if(event.isInstanceOf[ServerMessage]) {
				actors += Server.actor
			}*/
			if(event.isInstanceOf[ChannelMessage]) {
				subscriptions get (ChannelClassifier(event.asInstanceOf[ChannelMessage].channel)) match {
					case Some(actors2) => actors ++= actors2.keys
					case _ =>
				}
			}

			if(event.isInstanceOf[UserMessage]) {
				subscriptions get (UserClassifier(event.asInstanceOf[UserMessage].user)) match {
					case Some(actors2) => actors ++= actors2.keys
					case _ =>
				}
			}
		}

		if(event.isInstanceOf[BroadcastMessage] || event.isInstanceOf[ServerMessage]) {
			Server.actor ! event
		}
		implicit val timeout = Timeout(1, TimeUnit.SECONDS)
		implicit val actorSystem = Server.actorSystem.dispatcher

		val firstBatch = Future.sequence(actors filter(important.contains(_)) map (_ ? event))

		firstBatch.onComplete(_ => actors filter(!important.contains(_)) foreach(_ ! event))
	}

	def !(event: Message) {
		publish(event)
	}
}