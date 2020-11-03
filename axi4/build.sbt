scalaVersion := "2.12.12"

scalacOptions := Seq("-Xsource:2.12")

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

libraryDependencies += "edu.berkeley.cs" %% "chisel-iotesters" % "1.4.2"
libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "0.2.2"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"
libraryDependencies += "edu.berkeley.cs" %% "chisel3" % "3.1.6"