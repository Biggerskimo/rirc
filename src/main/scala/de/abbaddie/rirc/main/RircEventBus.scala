package de.abbaddie.rirc.main

import akka.event.ActorEventBus
import akka.actor.{Actor, ActorRef}
import collection._
import scala.Some
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import akka.pattern.ask
import scala.concurrent.Future
import grizzled.slf4j.Logging
import de.abbaddie.rirc.Munin
import de.abbaddie.rirc.main.Message._

class RircEventBus extends ActorEventBus with Logging {
	type Event = Message
	type Classifier = RircEventClassifier

	val DUMMY = AnyRef
	// used as a map containing sets
	val subscriptions : concurrent.Map[RircEventClassifier, concurrent.Map[ActorRef, Any]] = concurrent.TrieMap()

	val important : concurrent.Map[ActorRef, Any] = concurrent.TrieMap()
	val serverSubscriptions : concurrent.Map[ActorRef, Any] = concurrent.TrieMap()

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

	def subscribeServer(subscriber: ActorRef) {
		serverSubscriptions += (subscriber -> DUMMY)
	}

	def unsubscribeServer(subscriber: ActorRef) {
		serverSubscriptions -= subscriber
	}

	def clear(from: RircEventClassifier) {
		subscriptions.remove(from)
	}

	def publish(event: Message) {
		Munin.inc("events")

		var actors: Set[ActorRef] = mutable.HashSet()

		if(event.isInstanceOf[ScopedBroadcastMessage]) {
			val user = event.asInstanceOf[ScopedBroadcastMessage].user
			Server.channels.values
			.filter(_.users.contains(user))
			.foreach { channel =>
				subscriptions.get(ChannelClassifier(channel)) match {
					case Some(actors2) => actors ++= actors2.keys
					case _ =>
				}
			}
		}
		if(event.isInstanceOf[ChannelMessage]) {
			subscriptions.get(ChannelClassifier(event.asInstanceOf[ChannelMessage].channel)) match {
				case Some(actors2) => actors ++= actors2.keys
				case _ =>
			}
		}

		if(event.isInstanceOf[UserMessage]) {
			subscriptions.get(UserClassifier(event.asInstanceOf[UserMessage].user)) match {
				case Some(actors2) => actors ++= actors2.keys
				case _ =>
			}
		}

		if(event.isInstanceOf[ScopedBroadcastMessage] || event.isInstanceOf[ServerMessage]) {
			serverSubscriptions.keys.foreach(_ ! event)
		}

		if(event.isInstanceOf[ScopedBroadcastMessage] || event.isInstanceOf[AuthMessage]) {
			Server.authSys ! event
		}

		implicit val timeout = Timeout(1, TimeUnit.SECONDS)
		implicit val actorSystem = Server.actorSystem.dispatcher

		val firstBatch = Future.sequence(actors.filter(important.contains).map(_ ? event))

		firstBatch.onComplete(_ => actors.filterNot(important.contains).foreach(_ ! event))
	}

	def !(event: Message) {
		publish(event)
	}
}