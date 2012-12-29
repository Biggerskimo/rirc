package de.abbaddie.rirc.connector

import akka.actor._
import java.net.InetSocketAddress
import akka.actor.IO._
import akka.actor.IO.NewClient
import akka.actor.IO.Read
import akka.actor.IO.Closed
import scala.collection.mutable
import akka.util.ByteString
import java.io.EOFException
import grizzled.slf4j.Logging

class IrcSocketConnector extends Actor with Logging {
	val connections : mutable.Map[Handle, IrcConnection] = mutable.Map()
	val state = IO.IterateeRef.Map.async[Handle]()(context.dispatcher)

	override def preStart {
		IOManager(context.system) listen new InetSocketAddress(6667)
		info("setup listening port 6667")
	}

	def receive = {
		case Connected(server, address) =>
			printf("connected")
		case NewClient(server) =>
			printf("...")
			val socket = server.accept()
			val connection = new IrcConnection(socket)
			info("accepted connection from " + socket)
			connections += (socket -> connection)
			state(socket) flatMap(_ => IrcSocketConnector.processConnection(connection))
		case Read(socket, bytes) =>
			state(socket)(IO.Chunk(bytes))
		case Closed(socket, cause) =>
			state(socket)(EOF)
			connections -= socket
			state -= socket
	}
}
object IrcSocketConnector {
	// see http://tools.ietf.org/html/rfc1459.html#section-2.3.1
	val regex = """^(?::[^ ]+ +)?([^ ]+)((?: +[^: ][^ ]* *)*)(?: :(.*)|)$""".r

	def processConnection(connection : IrcConnection) : Iteratee[Unit] = repeat {
		println("start method")

		for(line <- takeLine) yield {
			val lineStr : String = line.utf8String
			println('"' + lineStr + '"')
			if(!line.isEmpty) {
				lineStr match {
					case regex(command, middle, trailing) =>
						var params = if(middle != null) middle.split(' ').filter(!_.isEmpty) else Array.empty[String]
						if(trailing != null) params :+= trailing
					case _ => println("bar")
				}
			}
		}
	}

	// similar to akka.actor.IO.takeUntil
	def takeLine : Iteratee[ByteString] = {
		def step(taken: ByteString)(input: Input): (Iteratee[ByteString], Input) = input match {
			case Chunk(more) ⇒
				val bytes = taken ++ more
				val idxCr = bytes.indexOfSlice("\r", math.max(taken.length - 1, 0))
				val idxLf = bytes.indexOfSlice("\n", math.max(taken.length - 1, 0))
				val idx = if(idxCr >= 0 && idxLf >= 0) math.min(idxCr, idxLf)
				else if (idxCr >= 0) idxCr
				else idxLf
				if (idx >= 0) {
					(Done(bytes take idx), Chunk(bytes drop (idx + 1)))
				} else {
					(Next(step(bytes)), Chunk.empty)
				}
			case EOF              ⇒ (Failure(new EOFException("Unexpected EOF")), EOF)
			case e @ Error(cause) ⇒ (Failure(cause), e)
		}

		Next(step(ByteString.empty))
	}
}