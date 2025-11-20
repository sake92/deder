package ba.sake.deder

import ba.sake.deder.config.ConfigParser

import java.time.{Duration, Instant}
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.zinc.ZincCompiler
import coursier.parse.DependencyParser

@main def blaApp() = {

  val configParser = ConfigParser()
  configParser.parse()


  // TODO handle scala 3
  /*
  val scalaVersion = "2.13.17"
  val scalaCompilerJar = DependencyResolver.fetchOne(DependencyParser.dependency(s"org.scala-lang:scala-compiler:${scalaVersion}", scalaVersion).toOption.get)
  val scalaLibraryJar = DependencyResolver.fetchOne(DependencyParser.dependency(s"org.scala-lang:scala-library:${scalaVersion}", scalaVersion).toOption.get)
  val scalaReflectJar = DependencyResolver.fetchOne(DependencyParser.dependency(s"org.scala-lang:scala-reflect:${scalaVersion}", scalaVersion).toOption.get) // only for scala 2
  val compilerBridgeJar = DependencyResolver.fetchOne(DependencyParser.dependency(s"org.scala-sbt:compiler-bridge_2.13:1.11.0", "2.13").toOption.get)

  val zincCacheFile = os.pwd / "out_deder/zinc/inc_compile.zip"

  val sources = os.walk(os.pwd / "d/src/scala", skip = p => {
    if os.isDir(p) then false
    else if os.isFile(p) then p.ext != "scala" && p.ext != "java"
    else true
  })
  println(sources)
  val classesDir = os.pwd / "out_deder/zinc/classes"

  val scalacOptions = Seq.empty[String]
  val javacOptions = Seq.empty[String]

  val zincCompiler = ZincCompiler(compilerBridgeJar)
  for i <- 0 to 10 do {
    println("#" * 50)
    val start = Instant.now()
    zincCompiler.compile(
      scalaVersion,
      scalaCompilerJar,
      Seq(scalaLibraryJar),
      Some(scalaReflectJar),
      zincCacheFile,
      sources,
      classesDir,
      scalacOptions,
      javacOptions
    )
    val end = Instant.now()
    println(s"Compiled in ${Duration.between(start, end)}")
  }
*/
}
