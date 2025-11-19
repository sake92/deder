package ba.sake.deder

import ba.sake.deder.zinc.ZincCompiler

import java.time.{Duration, Instant}

@main def blaApp() = {

  // TODO use coursier to resolve these
  val scalaCompilerJar = os.pwd / "scala-compiler-2.13.17.jar"
  val scalaLibraryJars = Seq(os.pwd / "scala-library-2.13.17.jar")
  val scalaReflectJar = os.pwd / "scala-reflect-2.13.17.jar" // only for scala 2
  val compilerBridgeJar = os.pwd / "compiler-bridge_2.13-1.11.0.jar"
  val zincCacheFile = os.pwd / "out_deder/zinc/inc_compile.zip"

  val scalaVersion = "2.13.17"
  val sources = os.walk(os.pwd / "d/src/scala", skip = p => {
    os.isFile(p) && p.ext != "scala"
  })
  val classesDir = os.pwd / "out_deder/zinc/classes"

  val scalacOptions = Seq.empty[String]
  val javacOptions = Seq.empty[String]

  val zincCompiler = ZincCompiler(compilerBridgeJar)
  for i <- 0 to 10 do {
    val start = Instant.now()
    zincCompiler.compile(
      scalaVersion,
      scalaCompilerJar,
      scalaLibraryJars,
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

}
