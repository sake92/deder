package ba.sake.deder.zinc

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.Optional
import java.util.function.Supplier
import sbt.internal.inc.{FileAnalysisStore, MappedFileConverter, ZincUtil}
import sbt.internal.inc.Locate
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
import com.typesafe.scalalogging.StrictLogging
import sbt.internal.inc.ScalaInstance
import ba.sake.deder.{DederGlobals, RequestContext, ServerNotification, ServerNotificationsLogger}

object ZincCompiler {
  def apply(compilerBridgeJar: os.Path): ZincCompiler =
    new ZincCompiler(compilerBridgeJar)
}

class ZincCompiler(compilerBridgeJar: os.Path) extends StrictLogging {

  private val incrementalCompiler = ZincUtil.defaultIncrementalCompiler

  def compile(
      javaHome: Option[Path],
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

    // TODO try with resources classloader...
    val parentClassloader = this.getClass.getClassLoader
    val libraryClassloader = new URLClassLoader(
      scalaLibraryJars.map(_.toNIO.toUri.toURL).toArray,
      parentClassloader
    )
    val compilerClassloader = new URLClassLoader(
      compilerJars.map(_.toNIO.toUri.toURL).toArray,
      libraryClassloader
    )
    val allJarsClassloader = new URLClassLoader(
      (compilerJars ++ scalaLibraryJars).map(_.toNIO.toUri.toURL).toArray,
      parentClassloader
    )

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
    val compilers =
      ZincUtil.compilers(scalaInstance, classpathOptions, javaHome, scalac = scalaCompiler)

    val converter = MappedFileConverter.empty
    val sourcesVFs = sources.map(s => converter.toVirtualFile(s.toNIO)).toArray
    val classpathVFs = compileClasspath.map(f => converter.toVirtualFile(f.toNIO)).toArray

    val compileOptions = CompileOptions.of(
      /*_classpath =*/ classpathVFs,
      /*_sources =*/ sourcesVFs,
      /*_classesDirectory =*/ classesDir.toNIO,
      /*_scalacOptions =*/ scalacOptions.toArray,
      /*_javacOptions =*/ javacOptions.toArray,
      /*_maxErrors =*/ 100,
      /*_sourcePositionMapper =*/ null,
      /*_order =*/ CompileOrder.JavaThenScala // TODO make configurable
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

    logger.debug(
      s"""Starting compilation
         |  module: $moduleId
         |  scalaVersion: $scalaVersion
         |  compilerJars: ${compilerJars.mkString(", ")}
         |  compileClasspath: ${compileClasspath.mkString(", ")}
         |  sources: ${sources.size}
         |  classesDir: $classesDir
         |  scalacOptions: ${scalacOptions.mkString(" ")}
         |  javacOptions: ${javacOptions.mkString(" ")}
         |""".stripMargin
    )

    try {
      notifications.add(ServerNotification.CompileStarted(moduleId, sources)) // so we can reset BSP diagnostics
      val newResult = incrementalCompiler.compile(inputs, zincLogger)
      analysisStore.set(AnalysisContents.create(newResult.analysis(), newResult.setup()))
      // trigger just in case, for BSP
      if !newResult.hasModified then notifications.add(ServerNotification.CompileFinished(moduleId, 0, 0))
    } catch {
      case e: xsbti.CompileFailed =>
        val problems = reporter.problems()
        if problems.isEmpty then {
          // logger.error(s"Compilation failed but no problems reported by Zinc reporter!")
          notifications.add(
            ServerNotification.logError(
              "Compilation failed but no diagnostic messages were reported by the compiler. This may indicate a compiler crash or configuration issue.",
              Some(moduleId)
            )
          )
        } else {
          val problemsSummary = problems.map(_.message()).mkString("\n")
          // logger.error(s"Compilation failed: ${problemsSummary}", e)
          notifications.add(
            ServerNotification.logError(s"Compilation failed: ${problemsSummary}", Some(moduleId))
          )
        }
        problems.foreach { problem =>
          notifications.add(ServerNotification.CompileDiagnostic(moduleId, problem))
        }
        val errorsCount = problems.count(_.severity == xsbti.Severity.Error)
        val warningsCount = problems.count(_.severity == xsbti.Severity.Warn)
        notifications.add(
          ServerNotification.CompileFailed(moduleId, errorsCount, warningsCount)
        )
        throw e
    }
  }

  private def getSetup(
      cacheFile: Path,
      reporter: xsbti.Reporter,
      moduleId: String,
      notifications: ServerNotificationsLogger
  ): Setup = {
    val perClasspathEntryLookup: PerClasspathEntryLookup = new PerClasspathEntryLookup {
      override def analysis(classpathEntry: xsbti.VirtualFile): Optional[CompileAnalysis] =
        // TODO
        Optional.empty[CompileAnalysis]

      override def definesClass(classpathEntry: xsbti.VirtualFile): DefinesClass = (className: String) =>
        Locate.definesClass(classpathEntry).apply(className)
    }

    val skip: Boolean = false
    val cache: GlobalsCache = CompilerCache.getDefault
    val incOptions: IncOptions = IncOptions.of()

    val compileProgress = new CompileProgress {
      override def advance(current: Int, total: Int, prevPhase: String, nextPhase: String): Boolean = {
        notifications.add(
          ServerNotification.TaskProgress(moduleId, "compile", current.toLong, total.toLong)
        )
        val currentRequestId = RequestContext.id.get()
        if currentRequestId == null then true
        else !DederGlobals.cancellationTokens.get(currentRequestId).get()
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
