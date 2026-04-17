package ba.sake.deder.scalajs

import ba.sake.deder.config.DederProject.{ModuleType, ScalaJsModule, ScalaJsTestModule}
import ba.sake.deder.testing.{DederTestOptions, DederTestResults, OutputCaptureContext}
import ba.sake.deder.*

/*
TODO
- fullLinkJs
- runJs
 */
class ScalaJsTasks(coreTasks: CoreTasks) {

  val fastLinkJsTask = CachedTaskBuilder
    .make[String](
      name = "fastLinkJs",
      supportedModuleTypes = Set(ModuleType.SCALA_JS, ModuleType.SCALA_JS_TEST)
    )
    .dependsOn(coreTasks.runClasspathTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .build { ctx =>
      val (classpath, mainClass) = ctx.depResults
      val irContainers = classpath
      os.makeDir.all(ctx.out)
      import scala.concurrent.ExecutionContext.Implicits.global
      val moduleInitializers = ctx.module match {
        case _: ScalaJsTestModule =>
          val init = org.scalajs.testing.adapter.TestAdapterInitializer
          Seq(org.scalajs.linker.interface.ModuleInitializer.mainMethod(init.ModuleClassName, init.MainMethodName))
        case _ =>
          mainClass.map { mc =>
            org.scalajs.linker.interface.ModuleInitializer.mainMethodWithArgs(mc, "main")
          }.toSeq
      }
      val linker = new ScalaJsLinker(ctx.notifications, ctx.module.id)
      linker.link(
        irContainers = irContainers,
        outputDir = ctx.out,
        moduleInitializers = moduleInitializers,
        jsModuleKind = ctx.module.asInstanceOf[ScalaJsModule].moduleKind
      )
      // TODO thread pool..
      ctx.out.toString
    }

  val testJsTask = TaskBuilder
    .make[DederTestResults](
      name = "test",
      supportedModuleTypes = Set(ModuleType.SCALA_JS_TEST)
    )
    .dependsOn(fastLinkJsTask)
    .dependsOn(coreTasks.testClassesTask)
    .buildWithSummary(
      execute = { ctx =>
        val (linkedJsDir, discoveredTests) = ctx.depResults
        OutputCaptureContext.withCapture(ctx.notifications, ctx.module.id) {
          val jsModule = ctx.module.asInstanceOf[ScalaJsTestModule]
          val testOptions = DederTestOptions(ctx.args)
          val runner = new ScalaJsTestRunner(ctx.notifications, ctx.module.id)
          runner.run(
            discoveredTests = discoveredTests,
            linkedJsDir = os.Path(linkedJsDir),
            moduleKind = jsModule.moduleKind,
            testOptions = testOptions,
            testParallelism = jsModule.testParallelism.toInt
          )

        }
      },
      isResultSuccessful = _.success,
      summarize = DederTestResults.summarize
    )

  val all: Seq[Task[?, ?]] = Seq(
    fastLinkJsTask,
    testJsTask
  )
}
