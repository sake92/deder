package ba.sake.deder.zinc

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.Optional
import scala.concurrent.duration.*
import com.github.blemale.scaffeine.*
import sbt.internal.inc.{MappedFileConverter, ZincUtil}
import sbt.internal.inc.Locate
import sbt.internal.inc.consistent.ConsistentFileAnalysisStore
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
import ba.sake.deder.{DederGlobals, OTEL, RequestContext, ServerNotification, ServerNotificationsLogger}

object ZincCompiler {
  def apply(compilerBridgeJar: os.Path): ZincCompiler =
    new ZincCompiler(compilerBridgeJar)
}

class ZincCompiler(compilerBridgeJar: os.Path) extends StrictLogging {

  private val incrementalCompiler = ZincUtil.defaultIncrementalCompiler

  // In-memory cache for Zinc analysis, avoids re-reading inc_compile.zip from disk
  private val analysisCache: Cache[os.Path, AnalysisContents] =
    Scaffeine()
      .expireAfterAccess(5.minute)
      .maximumSize(50)
      .build()

  // Cached compiler setup: classloaders, ScalaInstance, and Compilers
  // are expensive to create (JAR scanning, reflection) and can be reused
  // across compilations when the same compiler/library JARs are used.
  // Uses a proper cache (not a single Option) because cross-compilation
  // produces different compilerJars for the same scalaVersion (JVM vs JS vs Native).
  private case class CachedCompilerSetup(
      zincClassloader: URLClassLoader,
      libraryClassloader: URLClassLoader,
      compilerClassloader: URLClassLoader,
      allJarsClassloader: URLClassLoader,
      compilers: xsbti.compile.Compilers
  ) {
    def closeClassloaders(): Unit = {
      try allJarsClassloader.close()
      catch { case _: Exception => }
      try compilerClassloader.close()
      catch { case _: Exception => }
      try libraryClassloader.close()
      catch { case _: Exception => }
      try zincClassloader.close()
      catch { case _: Exception => }
    }
  }

  private val setupCache: Cache[String, CachedCompilerSetup] =
    Scaffeine()
      .expireAfterAccess(5.minute)
      .maximumSize(20)
      .removalListener[String, CachedCompilerSetup] { (_, setup, _) =>
        if setup != null then setup.closeClassloaders()
      }
      .build()

  def close(): Unit = {
    setupCache.invalidateAll()
    analysisCache.invalidateAll()
  }

  private def compilerSetupKey(
      compilerJars: Seq[os.Path],
      scalaLibraryJars: Seq[os.Path],
      javaHome: Option[Path]
  ): String =
    s"${compilerJars.map(_.toString).sorted.hashCode};${scalaLibraryJars.map(_.toString).sorted.hashCode};${javaHome.map(_.toString).getOrElse("")}"

  private def getOrCreateSetup(
      javaHome: Option[Path],
      scalaVersion: String,
      compilerJars: Seq[os.Path],
      scalaLibraryJars: Seq[os.Path]
  ): CachedCompilerSetup = {
    val key = compilerSetupKey(compilerJars, scalaLibraryJars, javaHome)
    setupCache.get(
      key,
      _ => {
        val span = OTEL.TRACER.spanBuilder("ZincCompiler.createSetup")
          .setAttribute("scalaVersion", scalaVersion)
          .startSpan()
        try {
          // Isolated bridge: only xsbti.* and sbt.internal.inc.* delegate to app classloader
          val appClassLoader = getClass.getClassLoader
          val sharedPrefixes = Seq("xsbti.", "sbt.internal.inc.")
          val bridgeClassLoader = new ClassLoader(ClassLoader.getPlatformClassLoader) {
            override def loadClass(name: String, resolve: Boolean): Class[?] =
              if sharedPrefixes.exists(name.startsWith) then
                val c = appClassLoader.loadClass(name)
                if resolve then resolveClass(c)
                c
              else super.loadClass(name, resolve)

            override def getResource(name: String): java.net.URL =
              val pathPrefixes = sharedPrefixes.map(_.replace('.', '/'))
              if pathPrefixes.exists(name.startsWith) then appClassLoader.getResource(name)
              else super.getResource(name)
          }

          val zincClassloader = new URLClassLoader(compilerJars.map(_.toURI.toURL).toArray, bridgeClassLoader)
          val libraryClassloader = new URLClassLoader(scalaLibraryJars.map(_.toURI.toURL).toArray, zincClassloader)
          val compilerClassloader = new URLClassLoader(compilerJars.map(_.toURI.toURL).toArray, libraryClassloader)
          val allJarsClassloader =
            new URLClassLoader((compilerJars ++ scalaLibraryJars).map(_.toURI.toURL).toArray, zincClassloader)

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
          val compilers = ZincUtil.compilers(scalaInstance, classpathOptions, javaHome, scalac = scalaCompiler)

          CachedCompilerSetup(zincClassloader, libraryClassloader, compilerClassloader,
            allJarsClassloader, compilers)
        } finally span.end()
      }
    )
  }

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
      compileOrder: CompileOrder = CompileOrder.JavaThenScala,
      zincLogger: xsbti.Logger,
      moduleId: String,
      notifications: ServerNotificationsLogger
  ): Unit = {
    val scalaLibraryPrefixes = Seq(
      "scala-library",
      "scala3-library",
      "scalajs-library",
      "scala3lib",
      "scalalib",
      "javalib",
      "nativelib",
      "auxlib",
      "clib",
      "posixlib"
    )
    val scalaLibraryJars = compileClasspath.filter { p =>
      scalaLibraryPrefixes.exists(p.last.startsWith) &&
      p.last.endsWith(".jar")
    }

    val setup = getOrCreateSetup(javaHome, scalaVersion, compilerJars, scalaLibraryJars)

    val oldClassloader = Thread.currentThread().getContextClassLoader
    Thread.currentThread().setContextClassLoader(setup.allJarsClassloader)
    try {
      doCompile(
        compileClasspath = compileClasspath,
        compilers = setup.compilers,
        zincCacheFile = zincCacheFile,
        sources = sources,
        classesDir = classesDir,
        scalacOptions = scalacOptions,
        javacOptions = javacOptions,
        compileOrder = compileOrder,
        zincLogger = zincLogger,
        moduleId = moduleId,
        notifications = notifications
      )
    } finally {
      Thread.currentThread().setContextClassLoader(oldClassloader)
    }
  }

  private def doCompile(
      compileClasspath: Seq[os.Path],
      compilers: xsbti.compile.Compilers,
      zincCacheFile: os.Path,
      sources: Seq[os.Path],
      classesDir: os.Path,
      scalacOptions: Seq[String],
      javacOptions: Seq[String],
      compileOrder: CompileOrder,
      zincLogger: xsbti.Logger,
      moduleId: String,
      notifications: ServerNotificationsLogger
  ): Unit = {
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
      /*_order =*/ compileOrder
    )

    val analysisStore = ConsistentFileAnalysisStore.binary(
      file = zincCacheFile.toIO,
      mappers = ReadWriteMappers.getEmptyMappers,
      reproducible = true,
      // No need to utilize more than 8 cores to serialize a small file
      parallelism = math.min(Runtime.getRuntime.availableProcessors(), 8)
    )
    val previousResult = locally {
      val span = OTEL.TRACER.spanBuilder("ZincCompiler.analysisStore.get")
        .setAttribute("moduleId", moduleId)
        .startSpan()
      try {
        val cached = analysisCache.getIfPresent(zincCacheFile)
        val previous = cached match {
          case Some(contents) => Optional.of(contents)
          case None           => analysisStore.get()
        }
        PreviousResult.of(
          previous.map(_.getAnalysis),
          previous.map(_.getMiniSetup)
        )
      } finally span.end()
    }

    val reporter = new DederZincReporter(moduleId, notifications, zincLogger)
    val setup = getSetup(zincCacheFile.toNIO, reporter, moduleId, notifications)
    val inputs = xsbti.compile.Inputs.of(compilers, compileOptions, setup, previousResult)

    logger.debug(
      s"""Starting compilation
         |  module: $moduleId
         |  compileClasspath: ${compileClasspath.mkString(", ")}
         |  sources: ${sources.size}
         |  classesDir: $classesDir
         |  scalacOptions: ${scalacOptions.mkString(" ")}
         |  javacOptions: ${javacOptions.mkString(" ")}
         |""".stripMargin
    )

    try {
      notifications.add(ServerNotification.CompileStarted(moduleId, sources)) // so we can reset BSP diagnostics
      val compileSpan = OTEL.TRACER.spanBuilder("ZincCompiler.incrementalCompile")
        .setAttribute("moduleId", moduleId)
        .setAttribute("sources.count", sources.size.toLong)
        .startSpan()
      val newResult =
        try incrementalCompiler.compile(inputs, zincLogger)
        finally compileSpan.end()
      val storeSpan = OTEL.TRACER.spanBuilder("ZincCompiler.analysisStore.set")
        .setAttribute("moduleId", moduleId)
        .startSpan()
      try {
        val contents = AnalysisContents.create(newResult.analysis(), newResult.setup())
        analysisStore.set(contents)
        analysisCache.put(zincCacheFile, contents)
      } finally storeSpan.end()
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
