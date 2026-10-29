package ba.sake.deder.scalanative

import java.nio.file.Files
import scala.util.Properties
import scala.jdk.CollectionConverters.*
import ba.sake.tupson.JsonRW
import scala.concurrent.ExecutionContext.Implicits.global
import ba.sake.deder.config.DederProject.{DederModule, ModuleType, ScalaNativeModule, ScalaNativeTestModule}
import ba.sake.deder.testing.{DederTestOptions, DederTestResults, JUnitXmlReportWriter, TestResultsSummary}
import ba.sake.deder.*

class ScalaNativeTasks(coreTasks: CoreTasks) {

  private val gcTask = configValue[String]("gc")(_.gc.toString)
  private val modeTask = configValue[String]("mode")(_.mode.toString)
  private val multithreadingTask = configValue[Boolean]("multithreading")(_.multithreading)
  private val ltoTask = configValue[String]("lto")(_.lto.toString)
  private val embedResourcesTask = configValue[Boolean]("embedResources")(_.embedResources)
  private val linkStubsTask = configValue[Boolean]("linkStubs")(_.linkStubs)
  private val checkTask = configValue[Boolean]("check")(_.check)
  private val checkFatalWarningsTask = configValue[Boolean]("checkFatalWarnings")(_.checkFatalWarnings)
  private val optimizeTask = configValue[Boolean]("optimize")(_.optimize)
  private val targetTripleTask = configValue[Option[String]]("targetTriple")(m => Option(m.targetTriple))
  private val nativeLinkingOptionsTask =
    configValue[Seq[String]]("nativeLinkingOptions")(_.nativeLinkingOptions.asScala.toSeq)
  private val nativeCompileOptionsTask =
    configValue[Seq[String]]("nativeCompileOptions")(_.nativeCompileOptions.asScala.toSeq)
  private val nativeCOptionsTask = configValue[Seq[String]]("nativeCOptions")(_.nativeCOptions.asScala.toSeq)
  private val nativeCppOptionsTask = configValue[Seq[String]]("nativeCppOptions")(_.nativeCppOptions.asScala.toSeq)
  private val resourceIncludePatternsTask =
    configValue[Seq[String]]("resourceIncludePatterns")(_.resourceIncludePatterns.asScala.toSeq)
  private val resourceExcludePatternsTask =
    configValue[Seq[String]]("resourceExcludePatterns")(_.resourceExcludePatterns.asScala.toSeq)

  private val linkerConfigTask = TaskBuilder
    .make[ScalaNativeLinkerSettings](
      name = "nativeLinkerConfig",
      supportedModuleTypes = Set(ModuleType.SCALA_NATIVE, ModuleType.SCALA_NATIVE_TEST),
      internal = true
    )
    .dependsOn(gcTask)
    .dependsOn(modeTask)
    .dependsOn(multithreadingTask)
    .dependsOn(ltoTask)
    .dependsOn(embedResourcesTask)
    .dependsOn(linkStubsTask)
    .dependsOn(checkTask)
    .dependsOn(checkFatalWarningsTask)
    .dependsOn(optimizeTask)
    .dependsOn(targetTripleTask)
    .dependsOn(nativeLinkingOptionsTask)
    .dependsOn(nativeCompileOptionsTask)
    .dependsOn(nativeCOptionsTask)
    .dependsOn(nativeCppOptionsTask)
    .dependsOn(resourceIncludePatternsTask)
    .dependsOn(resourceExcludePatternsTask)
    .build { ctx =>
      val (
        gc,
        mode,
        multithreading,
        lto,
        embedResources,
        linkStubs,
        check,
        checkFatalWarnings,
        optimize,
        targetTriple,
        nativeLinkingOptions,
        nativeCompileOptions,
        nativeCOptions,
        nativeCppOptions,
        resourceIncludePatterns,
        resourceExcludePatterns
      ) = ctx.depResults
      ScalaNativeLinkerSettings(
        gc,
        mode,
        multithreading,
        lto,
        embedResources,
        linkStubs,
        check,
        checkFatalWarnings,
        optimize,
        targetTriple,
        nativeLinkingOptions,
        nativeCompileOptions,
        nativeCOptions,
        nativeCppOptions,
        resourceIncludePatterns,
        resourceExcludePatterns
      )
    }

  val fastNativeLinkTask = CachedTaskBuilder
    .make[String](
      name = "fastNativeLink",
      supportedModuleTypes = Set(ModuleType.SCALA_NATIVE, ModuleType.SCALA_NATIVE_TEST)
    )
    .dependsOn(coreTasks.runClasspathTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .dependsOn(linkerConfigTask)
    .build { ctx =>
      val (classpath, mainClass, linkerConfig) = ctx.depResults
      os.makeDir.all(ctx.out)
      buildNativeLink(ctx.module, ctx.notifications, classpath, mainClass, linkerConfig, ctx.out) { (linker, settings, mc) =>
        linker.linkFast(nirPaths = classpath, outputDir = ctx.out, mainClass = mc, settings = settings)
      }.toString
    }

  val fullNativeLinkTask = CachedTaskBuilder
    .make[String](
      name = "fullNativeLink",
      supportedModuleTypes = Set(ModuleType.SCALA_NATIVE, ModuleType.SCALA_NATIVE_TEST)
    )
    .dependsOn(coreTasks.runClasspathTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .dependsOn(linkerConfigTask)
    .build { ctx =>
      val (classpath, mainClass, linkerConfig) = ctx.depResults
      os.makeDir.all(ctx.out)
      buildNativeLink(ctx.module, ctx.notifications, classpath, mainClass, linkerConfig, ctx.out) { (linker, settings, mc) =>
        linker.linkFull(nirPaths = classpath, outputDir = ctx.out, mainClass = mc, settings = settings)
      }.toString
    }

  val nativeLinkTask = CachedTaskBuilder
    .make[String](
      name = "nativeLink",
      supportedModuleTypes = Set(ModuleType.SCALA_NATIVE, ModuleType.SCALA_NATIVE_TEST),
      category = "Scala Native"
    )
    .dependsOn(coreTasks.runClasspathTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .dependsOn(linkerConfigTask)
    .build { ctx =>
      val (classpath, mainClass, linkerConfig) = ctx.depResults
      os.makeDir.all(ctx.out)
      buildNativeLink(ctx.module, ctx.notifications, classpath, mainClass, linkerConfig, ctx.out) { (linker, settings, mc) =>
        linker.link(nirPaths = classpath, outputDir = ctx.out, mainClass = mc, settings = settings)
      }.toString
    }

  val runNativeTask = TaskBuilder
    .make[Seq[String]](
      name = "runNative",
      singleton = true,
      supportedModuleTypes = Set(ModuleType.SCALA_NATIVE)
    )
    .dependsOn(fastNativeLinkTask)
    .build { ctx =>
      val linkedBinaryPath = ctx.depResults._1
      val cmd = Seq(linkedBinaryPath) ++ ctx.args
      ctx.notifications.add(ServerNotification.RunSubprocess(cmd, Map.empty, ctx.watch))
      cmd
    }

  val testNativeTask = TaskBuilder
    .make[DederTestResults](
      name = "test",
      supportedModuleTypes = Set(ModuleType.SCALA_NATIVE_TEST),
      category = "Scala Native"
    )
    .dependsOn(fastNativeLinkTask)
    .dependsOn(coreTasks.testClassesTask)
    .buildSummarized[TestResultsSummary](
      execute = { ctx =>
        val (linkedBinaryPath, discoveredTests) = ctx.depResults
        OutputCaptureContext.withCapture(ctx.notifications, ctx.module.id) {
          val testOptions = DederTestOptions(ctx.args)
          val nativeModule = ctx.module.asInstanceOf[ScalaNativeTestModule]
          val runner = new ScalaNativeTestRunner(ctx.notifications, ctx.module.id)
          val results = runner.run(
            discoveredTests = discoveredTests,
            nativeBinaryPath = os.Path(linkedBinaryPath),
            testOptions = testOptions,
            testParallelism = { val n = nativeModule.testParallelism.toInt; if n == 0 then Runtime.getRuntime.availableProcessors() else n }
          )
          JUnitXmlReportWriter.outputDir(ctx.module, ctx.out).foreach(JUnitXmlReportWriter.writeReports(results, _))
          results
        }
      },
      isResultSuccessful = _.success
    )

  val all: Seq[Task[?, ?, ?]] = Seq(
    gcTask,
    modeTask,
    multithreadingTask,
    ltoTask,
    embedResourcesTask,
    linkStubsTask,
    checkTask,
    checkFatalWarningsTask,
    optimizeTask,
    targetTripleTask,
    nativeLinkingOptionsTask,
    nativeCompileOptionsTask,
    nativeCOptionsTask,
    nativeCppOptionsTask,
    resourceIncludePatternsTask,
    resourceExcludePatternsTask,
    linkerConfigTask,
    fastNativeLinkTask,
    fullNativeLinkTask,
    nativeLinkTask,
    runNativeTask,
    testNativeTask
  )

  private def configValue[T: JsonRW: Hashable](name: String)(read: ScalaNativeModule => T) =
    ConfigValueTask[T](
      name = name,
      supportedModuleTypes = Set(ModuleType.SCALA_NATIVE, ModuleType.SCALA_NATIVE_TEST),
      internal = true,
      execute = ctx => read(ctx.module.asInstanceOf[ScalaNativeModule])
    )

  private def buildNativeLink(
      module: DederModule,
      notifications: ServerNotificationsLogger,
      classpath: Seq[os.Path],
      mainClass: Option[String],
      settings: ScalaNativeLinkerSettings,
      outputDir: os.Path
  )(linkFn: (ScalaNativeLinker, ScalaNativeLinkerSettings, Option[String]) => os.Path): os.Path = {
    val effectiveMainClass = module match {
      case _: ScalaNativeTestModule => Some("scala.scalanative.testinterface.TestMain")
      case _                        => mainClass
    }
    val linker = new ScalaNativeLinker(notifications, module.id)
    linkFn(linker, settings, effectiveMainClass)
  }
}

object ScalaNativeTasks {

  private val ignoredFileSuffixes = Seq(".ll", ".c", ".o", ".s", ".json")

  private def isExecutableBinaryCandidate(path: os.Path): Boolean =
    if Properties.isWin then path.ext == "exe"
    else Files.isExecutable(path.toNIO)

  private[scalanative] def findNativeBinary(nativeLinkDir: os.Path): os.Path = {
    val files = os.list(nativeLinkDir).filter(os.isFile).sortBy(_.last)
    val candidates = files.filterNot(path => ignoredFileSuffixes.exists(path.last.endsWith))
    val executableCandidates = candidates.filter(isExecutableBinaryCandidate)

    executableCandidates.headOption.getOrElse(
      throw DederException(
        s"No executable native binary found in $nativeLinkDir. Files: ${files.map(_.last).mkString(", ")}"
      )
    )
  }
}
