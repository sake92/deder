val scala3 = "3.3.4"

ThisBuild / scalaVersion := scala3

lazy val root = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("."))
  .settings(
    name := "cross-full",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.0.2" % Test
  )
