name := "akka-ib"

version       := "1.0-SNAPSHOT"

scalaVersion  := "2.11.7"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

val akkaStreamV = "2.0.1"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.4.0",
    "com.typesafe.akka" %% "akka-testkit" % "2.4.0",
    "com.typesafe.akka" %% "akka-persistence" % "2.4.0",
    "com.typesafe.akka" %% "akka-stream-experimental"             % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental"          % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-experimental"               % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental"    % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-testkit-experimental"       % akkaStreamV,
    "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.1.6" % "test",
    "org.iq80.leveldb" % "leveldb" % "0.7",
    "akka-k2" %% "akka-k2" % "1.0-SNAPSHOT",
    "org.scalaz" %% "scalaz-core" % "7.2.0",
    "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
    "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
)


