name := "MovieRecommendationSystem"

version := "1.0"

scalaVersion := "2.13.10"

// Add dependencies
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.8.0",
  "com.typesafe.akka" %% "akka-stream" % "2.8.0",
  "com.typesafe.akka" %% "akka-http" % "10.5.0",
  "com.softwaremill.sttp.client3" %% "akka-http-backend" % "3.8.13",
  "com.softwaremill.sttp.client3" %% "core" % "3.8.13", // replace with the latest version
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.0",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.0",
  "com.typesafe.akka" %% "akka-http-xml" % "10.5.0",
  "com.typesafe.akka" %% "akka-slf4j" % "2.8.0",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.5.0" % Test,
  "com.typesafe.akka" %% "akka-testkit" % "2.8.0" % Test,
  "ch.qos.logback" % "logback-classic" % "1.4.6",
  "org.scalatest" %% "scalatest" % "3.2.15" % Test,
  "com.typesafe.play" %% "play-json" % "2.9.4"
)

scalacOptions ++= Seq("-Ybackend-parallelism", "4")

// Add ScalaTest library dependency
//libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.15" % Test
