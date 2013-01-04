name := "rirc"

version := "0.0.1"

scalaVersion := "2.10.0-RC5"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.3"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.1.0"

libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.1.0"

libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.1.0"

libraryDependencies += "org.clapper" % "grizzled-slf4j_2.10" % "1.0.1"

libraryDependencies += "io.netty" % "netty" % "3.6.0.Final"

libraryDependencies += "org.scalaj" % "scalaj-time_2.9.2" % "0.6"

libraryDependencies += "org.mindrot" % "jbcrypt" % "0.3m"

libraryDependencies += "org.yaml" % "snakeyaml" % "1.11"