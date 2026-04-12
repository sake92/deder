package ba.sake.deder.scalanative

import java.nio.file.Files
import scala.util.Using
import scala.util.Properties
import ba.sake.deder.config.DederProject.{ModuleType, ScalaNativeModule, ScalaNativeTestModule}
import ba.sake.deder.testing.{DederTestOptions, DederTestResults, OutputCaptureContext}
import ba.sake.deder.*

/*
TODO
- fullNativeLink
- runNative
 */
class ScalaNativeTasks(coreTasks: CoreTasks) {

  val nativeLinkTask = CachedTaskBuilder
    .make[String](
      name = "nativeLink",
      supportedModuleTypes = Set(ModuleType.SCALA_NATIVE, ModuleType.SCALA_NATIVE_TEST)
    )
    .dependsOn(coreTasks.runClasspathTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .build { ctx =>
      val (classpath, mainClass) = ctx.depResults
      val nirPaths = classpath
      os.makeDir.all(ctx.out)
      import scala.concurrent.ExecutionContext.Implicits.global
      val effectiveMainClass = ctx.module match {
        case _: ScalaNativeTestModule =>
          Some("scala.scalanative.testinterface.TestMain")
        case _ => mainClass
      }
      val linker = new ScalaNativeLinker(ctx.notifications, ctx.module.id)
      linker.link(
        nirPaths = nirPaths,
        outputDir = ctx.out,
        mainClass = effectiveMainClass,
        nativeLibs = Seq.empty
      )
      // TODO thread pool..
      ""
    }

  val testNativeTask = TaskBuilder
    .make[DederTestResults](
      name = "test",
      supportedModuleTypes = Set(ModuleType.SCALA_NATIVE_TEST)
    )
    .dependsOn(nativeLinkTask)
    .dependsOn(coreTasks.testClassesTask)
    .buildWithSummary(
      execute = { ctx =>
        val (_, discoveredTests) = ctx.depResults
        OutputCaptureContext.withCapture(ctx.notifications, ctx.module.id) {
          val testOptions = DederTestOptions(ctx.args)
          val nativeBinaryPath = ScalaNativeTasks.findNativeBinary(ctx.out / os.up / "nativeLink")
          Using.resource(java.util.concurrent.Executors.newFixedThreadPool(DederGlobals.testWorkerThreads)) {
            executorService =>
              val runner = new ScalaNativeTestRunner(ctx.notifications, ctx.module.id)
              runner.run(
                discoveredTests = discoveredTests,
                nativeBinaryPath = nativeBinaryPath,
                testOptions = testOptions,
                executorService = executorService
              )
          }
        }
      },
      isResultSuccessful = _.success,
      summarize = DederTestResults.summarize
    )

  val all: Seq[Task[?, ?]] = Seq(
    nativeLinkTask,
    testNativeTask
  )
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
