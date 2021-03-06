organization := "Lewuathe"

name := "dllib"

version := "0.0.7"

sparkVersion := "1.5.2"

libraryDependencies  ++= Seq(
  // other dependencies here
  "org.scalanlp" %% "breeze" % "0.12",
  "org.scalanlp" %% "breeze-viz" % "0.12",
  // native libraries are not included by default. add this if you want them (as of 0.7)
  // native libraries greatly improve performance, but increase jar sizes.
//  "org.scalanlp" %% "breeze-natives" % "0.11.2",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "org.scalamock" %% "scalamock-scalatest-support" % "3.2" % "test"
)

resolvers ++= Seq(
  // other resolvers here
  // if you want to use snapshot builds (currently 0.8-SNAPSHOT), use this.
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Spark" at "https://repository.apache.org/content/repositories/"
)

// Scala 2.9.2 is still supported for 0.2.1, but is dropped afterwards.
// Don't use an earlier version of 2.10, you will probably get weird compiler crashes.
scalaVersion := "2.10.5"

crossScalaVersions := Seq("2.10.5", "2.11.7")

//spAppendScalaVersion := true

sparkComponents ++= Seq("mllib", "sql")

spName := "Lewuathe/dllib"

spShortDescription := "Distributed Deep learning module on Apache Spark"

spDescription :=
"""dllib is a distributed deep learning module running on Spark
    |dllib provides configurable interface and scalable performance
    |that fits your deep learning usage.
  """.stripMargin

spHomepage := "https://github.com/Lewuathe/dllib"

//spIncludeMaven := true

credentials += Credentials(Path.userHome / ".ivy2" / ".sbtcredentials")

licenses += "Apache-2.0" -> url("http://opensource.org/licenses/Apache-2.0")

publishMavenStyle := true