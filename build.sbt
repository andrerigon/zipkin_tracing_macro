import java.io.File

import spray.revolver.RevolverPlugin._

name := """zipkin_tracing_macro"""

fork in run := true

resolvers ++= Seq("Twitter Repo" at "http://maven.twttr.com/")

resolvers ++= Seq("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")

resolvers += "Mesosphere Public Repository" at "http://downloads.mesosphere.io/maven"

scalaVersion := "2.11.8"

val finagleVersion = "6.40.0"

organization := "com.rigon"

libraryDependencies ++= Seq(
  "com.twitter" %% "twitter-server" % "1.25.0",
  "com.twitter" % "finagle-core_2.11" % finagleVersion,
  "com.twitter" %% "finagle-http" % finagleVersion,
  "com.twitter" %% "finagle-mysql" % finagleVersion,
  ("com.twitter" %% "finagle-stats" % finagleVersion).exclude("asm", "asm"),
  "com.twitter" %% "finagle-zipkin" % finagleVersion,
  "org.slf4j" % "slf4j-log4j12" % "1.7.10"
)

// test

libraryDependencies ++= Seq(
  "org.mockito" % "mockito-core" % "1.9.5" % "test",
  "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test",
  "org.codehaus.groovy" % "groovy-all" % "2.4.3" % "test"
)

assemblyJarName in assembly := s"${name.value}.jar"

Revolver.settings

test in assembly := {}

assemblyMergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
{
  case "META-INF/spring.tooling" => MergeStrategy.first
  case x => old(x)
}
}

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

releaseSettings

publishTo := Some(Resolver.file("Local repo", Path.userHome / ".m2" / "repository" asFile ))
