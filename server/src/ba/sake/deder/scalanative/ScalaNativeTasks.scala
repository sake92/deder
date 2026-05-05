package ba.sake.deder.scalanative

import java.nio.file.Files
import scala.util.Properties
import scala.concurrent.ExecutionContext.Implicits.global
import ba.sake.deder.config.DederProject.{DederModule, ModuleType, ScalaNativeModule, ScalaNativeTestModule}
import ba.sake.deder.testing.{DederTestOptions, DederTestResults, JUnitXmlReportWriter, TestResultsSummary}
import ba.sake.deder.*

class ScalaNativeTasks(coreTasks: CoreTasks) {

  val fastNativeLinkTask = CachedTaskBuilder
    .make[String](
      name = "fastNativeLink",
      supportedModuleTypes = Set(ModuleType.SCALA_NATIVE, ModuleType.SCALA_NATIVE_TEST)
    )
    .dependsOn(coreTasks.runClasspathTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .build { ctx =>
      val (classpath, mainClass) = ctx.depResults
      os.makeDir.all(ctx.out)
      buildNativeLink(ctx.module, ctx.notifications, classpath, mainClass, ctx.out) { (linker, nativeModule, mc) =>
        linker.linkFast(nirPaths = classpath, outputDir = ctx.out, mainClass = mc, nativeModule = nativeModule)
      }.toString
    }

  val fullNativeLinkTask = CachedTaskBuilder
    .make[String](
      name = "fullNativeLink",
      supportedModuleTypes = Set(ModuleType.SCALA_NATIVE, ModuleType.SCALA_NATIVE_TEST)
    )
    .dependsOn(coreTasks.runClasspathTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .build { ctx =>
      val (classpath, mainClass) = ctx.depResults
      os.makeDir.all(ctx.out)
      buildNativeLink(ctx.module, ctx.notifications, classpath, mainClass, ctx.out) { (linker, nativeModule, mc) =>
        linker.linkFull(nirPaths = classpath, outputDir = ctx.out, mainClass = mc, nativeModule = nativeModule)
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
    .build { ctx =>
      val (classpath, mainClass) = ctx.depResults
      os.makeDir.all(ctx.out)
      buildNativeLink(ctx.module, ctx.notifications, classpath, mainClass, ctx.out) { (linker, nativeModule, mc) =>
        linker.link(nirPaths = classpath, outputDir = ctx.out, mainClass = mc, nativeModule = nativeModule)
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
    .buildWithSummary(
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
      isResultSuccessful = _.success,
      summarize = (results, notifs) => TestResultsSummary.summarize(results.map((m, r) => m.id -> r), notifs)
    )

  val all: Seq[Task[?, ?]] = Seq(
    fastNativeLinkTask,
    fullNativeLinkTask,
    nativeLinkTask,
    runNativeTask,
    testNativeTask
  )

  private def buildNativeLink(
      module: DederModule,
      notifications: ServerNotificationsLogger,
      classpath: Seq[os.Path],
      mainClass: Option[String],
      outputDir: os.Path
  )(linkFn: (ScalaNativeLinker, ScalaNativeModule, Option[String]) => os.Path): os.Path = {
    val effectiveMainClass = module match {
      case _: ScalaNativeTestModule => Some("scala.scalanative.testinterface.TestMain")
      case _                        => mainClass
    }
    val linker = new ScalaNativeLinker(notifications, module.id)
    val nativeModule = module.asInstanceOf[ScalaNativeModule]
    linkFn(linker, nativeModule, effectiveMainClass)
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
