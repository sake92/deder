ThisBuild / tlBaseVersion    := "0.1"
ThisBuild / organization     := "com.example"
ThisBuild / startYear        := Some(2025)
ThisBuild / scalaVersion     := "3.3.4"
ThisBuild / crossScalaVersions := Seq("2.13.15", "3.3.4")
// Workaround: sbt-build-extract and sbt-typelevel have conflicting upickle versions
ThisBuild / libraryDependencySchemes += "com.lihaoyi" % "upickle" % VersionScheme.Always

lazy val root = tlCrossRootProject.aggregate(core)

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "typelevel-demo",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.0.2" % Test
  )
