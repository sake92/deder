scalaVersion := "3.3.4"
// sbt-tpolecat adds -Xfatal-warnings and -Ykind-projector which
// causes issues with Scala 3.3.4; override to keep only safe options.
scalacOptions --= Seq("-Xfatal-warnings", "-Ykind-projector")
libraryDependencies ++= Seq(
  "org.scalameta" %% "munit" % "1.0.2" % Test
)
