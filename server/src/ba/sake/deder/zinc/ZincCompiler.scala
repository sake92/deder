package ba.sake.deder.zinc

import java.io.File
import java.nio.file.Path
import java.util.Optional
import java.util.function.Supplier
import sbt.internal.inc.{FileAnalysisStore, PlainVirtualFileConverter, ZincUtil}
import sbt.internal.inc.consistent.ConsistentFileAnalysisStore
import sbt.internal.util.ManagedLogger
import sbt.util.LoggerContext
import xsbti.compile.analysis.ReadWriteMappers
import xsbti.compile.{
  AnalysisContents,
  ClasspathOptionsUtil,
  CompileAnalysis,
  CompileOptions,
  CompileOrder,
  CompileProgress,
  CompilerCache,
  DefinesClass,
  GlobalsCache,
  IncOptions,
  PerClasspathEntryLookup,
  PreviousResult,
  Setup
}
import sbt.internal.inc.ScalaInstance
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.ServerNotification

object ZincCompiler {
  def apply(compilerBridgeJar: os.Path): ZincCompiler =
    new ZincCompiler(compilerBridgeJar)
}

class ZincCompiler(compilerBridgeJar: os.Path) {

  private val incrementalCompiler = ZincUtil.defaultIncrementalCompiler

  def compile(
      scalaVersion: String,
      compilerJars: Seq[os.Path], // compiler + reflect
      compileClasspath: Seq[os.Path],
      zincCacheFile: os.Path,
      sources: Seq[os.Path],
      classesDir: os.Path,
      scalacOptions: Seq[String],
      javacOptions: Seq[String],
      zincLogger: xsbti.Logger,
      moduleId: String,
      notifications: ServerNotificationsLogger
  ): Unit = {

    val scalaLibraryJars = compileClasspath.filter { p =>
      (p.last.startsWith("scala-library-") || p.last.startsWith("scala3-library_3")) && p.last.endsWith(".jar")
    }

    val parentClassloader = this.getClass.getClassLoader
    val libraryClassloader = new java.net.URLClassLoader(
      scalaLibraryJars.map(_.toNIO.toUri.toURL).toArray,
      parentClassloader
    )
    val compilerClassloader = new java.net.URLClassLoader(
      compilerJars.map(_.toNIO.toUri.toURL).toArray,
      libraryClassloader
    )
    val allJarsClassloader = new java.net.URLClassLoader(
      (compilerJars ++ scalaLibraryJars).map(_.toNIO.toUri.toURL).toArray,
      parentClassloader
    )
    /*
    println(
      s"""Zinc compile:
        |compilerBridgeJar = $compilerBridgeJar
        |
        |scalacOptions = $scalacOptions
        |
        |javacOptions = $javacOptions
        |
        |compileClasspath = ${compileClasspath.mkString("\n")}
        |
        |compilerJars = ${compilerJars.mkString("\n")}
        |
        |scalaLibraryJars = ${scalaLibraryJars.mkString("\n")}
        |""".stripMargin
    )*/

    val scalaInstance = new ScalaInstance(
      version = scalaVersion,
      loader = allJarsClassloader,
      loaderCompilerOnly = compilerClassloader,
      loaderLibraryOnly = libraryClassloader,
      libraryJars = scalaLibraryJars.map(_.toIO).toArray,
      compilerJars = compilerJars.map(_.toIO).toArray,
      allJars = (compilerJars ++ scalaLibraryJars).map(_.toIO).toArray,
      explicitActual = Some(scalaVersion)
    )

    val classpathOptions = ClasspathOptionsUtil.auto()
    val scalaCompiler = ZincUtil.scalaCompiler(scalaInstance, compilerBridgeJar.toIO, classpathOptions)
    val javaHome = os.Path(scala.util.Properties.javaHome) // TODO customize?
    val compilers =
      ZincUtil.compilers(scalaInstance, classpathOptions, javaHome = Some(javaHome.toNIO), scalac = scalaCompiler)

    val converter = PlainVirtualFileConverter.converter
    val sourcesVFs = sources.map(s => converter.toVirtualFile(s.toNIO)).toArray
    val classpath = compileClasspath.map(f => converter.toVirtualFile(f.toNIO)).toArray

    val compileOptions = CompileOptions.of(
      /*_classpath =*/ classpath,
      /*_sources =*/ sourcesVFs,
      /*_classesDirectory =*/ classesDir.toNIO,
      /*_scalacOptions =*/ scalacOptions.toArray,
      /*_javacOptions =*/ javacOptions.toArray,
      /*_maxErrors =*/ 100,
      /*_sourcePositionMapper =*/ null,
      /*_order =*/ CompileOrder.Mixed
    )

    val analysisStore = ConsistentFileAnalysisStore.binary(
      file = zincCacheFile.toIO,
      mappers = ReadWriteMappers.getEmptyMappers,
      reproducible = true,
      // No need to utilize more than 8 cores to serialize a small file
      parallelism = math.min(Runtime.getRuntime.availableProcessors(), 8)
    )
    val previousResult = locally {
      val previous = analysisStore.get()
      PreviousResult.of(
        previous.map(_.getAnalysis),
        previous.map(_.getMiniSetup)
      )
    }

    val reporter = new DederZincReporter(moduleId, notifications, zincLogger)
    val setup = getSetup(zincCacheFile.toNIO, reporter, moduleId, notifications)
    val inputs = xsbti.compile.Inputs.of(compilers, compileOptions, setup, previousResult)

    // try {
    notifications.add(ServerNotification.CompileStarted(moduleId, sources)) // so we can reset BSP diagnostics
    val newResult = incrementalCompiler.compile(inputs, zincLogger)
    analysisStore.set(AnalysisContents.create(newResult.analysis(), newResult.setup()))
    /*} catch {
      case e: xsbti.CompileFailed =>
      // println("Noooooooooooooooooooooooooooooooooo")
      // e.printStackTrace()
    }*/
  }

  private def getSetup(
      cacheFile: Path,
      reporter: xsbti.Reporter,
      moduleId: String,
      notifications: ServerNotificationsLogger
  ): Setup = {
    val perClasspathEntryLookup: PerClasspathEntryLookup = new PerClasspathEntryLookup {
      override def analysis(x$0: xsbti.VirtualFile): java.util.Optional[CompileAnalysis] =
        Optional.empty[CompileAnalysis]

      override def definesClass(x$0: xsbti.VirtualFile): DefinesClass = (className: String) => true
    }

    val skip: Boolean = false
    val cache: GlobalsCache = CompilerCache.getDefault
    val incOptions: IncOptions = IncOptions.of()

    val compileProgress = new CompileProgress {
      override def advance(current: Int, total: Int, prevPhase: String, nextPhase: String): Boolean = {
        notifications.add(
          ServerNotification.TaskProgress(moduleId, "compile", current.toLong, total.toLong)
        )
        true
      }
    }

    Setup.of(
      perClasspathEntryLookup,
      skip,
      cacheFile,
      cache,
      incOptions,
      reporter,
      compileProgress,
      Array.empty[xsbti.T2[String, String]] // extra
    )
  }
}
