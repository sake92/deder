package ba.sake.deder.scalanative

import scala.util.Using
import ba.sake.deder.config.DederProject.{ModuleType, ScalaNativeModule, ScalaNativeTestModule}
import ba.sake.deder.testing.{DederTestOptions, DederTestResults, OutputCaptureContext}
import ba.sake.deder.{CoreTasks, DederException, DederGlobals, Task, TaskBuilder}

class ScalaNativeTasks(coreTasks: CoreTasks) {

  val nativeLinkTask = TaskBuilder
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
          val nativeBinaryPath = findNativeBinary(ctx.out / os.up / "nativeLink")
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

  private def findNativeBinary(nativeLinkDir: os.Path): os.Path = {
    // ScalaNative linker outputs the binary in the nativeLink output directory
    val candidates = os
      .list(nativeLinkDir)
      .filter(p =>
        os.isFile(p) && !p.last.endsWith(".ll") && !p.last.endsWith(".c") && !p.last.endsWith(".o") && !p.last
          .endsWith(".s")
      )
    candidates.headOption.getOrElse(
      throw DederException(
        s"No native binary found in $nativeLinkDir. Files: ${os.list(nativeLinkDir).map(_.last).mkString(", ")}"
      )
    )
  }

  val all: Seq[Task[?, ?]] = Seq(
    nativeLinkTask,
    testNativeTask
  )
}
