package ba.sake.deder.scalajs

import ba.sake.deder.config.DederProject.{ModuleType, ScalaJsModule, ScalaJsTestModule}
import ba.sake.deder.testing.{DederTestOptions, DederTestResults, JUnitXmlReportWriter, TestResultsSummary}
import ba.sake.deder.*

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
      val jsModule = ctx.module.asInstanceOf[ScalaJsModule]
      val linker = new ScalaJsLinker(ctx.notifications, ctx.module.id)
      linker.linkFast(
        irContainers = irContainers,
        outputDir = ctx.out,
        moduleInitializers = moduleInitializers,
        jsModuleKind = jsModule.moduleKind,
        linkerConfig = jsModule.linkerConfig
      )
      // TODO thread pool..
      ctx.out.toString
    }

  val fullLinkJsTask = CachedTaskBuilder
    .make[String](
      name = "fullLinkJs",
      supportedModuleTypes = Set(ModuleType.SCALA_JS, ModuleType.SCALA_JS_TEST)
    )
    .dependsOn(coreTasks.runClasspathTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .build { ctx =>
      val (classpath, mainClass) = ctx.depResults
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
      val jsModule = ctx.module.asInstanceOf[ScalaJsModule]
      val linker = new ScalaJsLinker(ctx.notifications, ctx.module.id)
      linker.linkFull(
        irContainers = classpath,
        outputDir = ctx.out,
        moduleInitializers = moduleInitializers,
        jsModuleKind = jsModule.moduleKind,
        linkerConfig = jsModule.linkerConfig
      )
      ctx.out.toString
    }

  val linkJsTask = CachedTaskBuilder
    .make[String](
      name = "linkJs",
      supportedModuleTypes = Set(ModuleType.SCALA_JS, ModuleType.SCALA_JS_TEST)
    )
    .dependsOn(coreTasks.runClasspathTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .build { ctx =>
      val (classpath, mainClass) = ctx.depResults
      val irContainers = classpath
      os.makeDir.all(ctx.out)
      import scala.concurrent.ExecutionContext.Implicits.global
      val jsModule = ctx.module.asInstanceOf[ScalaJsModule]
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
        jsModuleKind = jsModule.moduleKind,
        linkerConfig = jsModule.linkerConfig
      )
      ctx.out.toString
    }

  val runJsTask = TaskBuilder
    .make[Seq[String]](
      name = "runJs",
      singleton = true,
      supportedModuleTypes = Set(ModuleType.SCALA_JS)
    )
    .dependsOn(fastLinkJsTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .build { ctx =>
      val (linkedJsDir, finalMainClass) = ctx.depResults
      finalMainClass match {
        case Some(mc) =>
          val linkedJsPath = os.Path(linkedJsDir) / "main.js"
          val cmd = Seq("node", "--enable-source-maps", linkedJsPath.toString) ++ ctx.args
          ctx.notifications.add(ServerNotification.RunSubprocess(cmd, Map.empty, ctx.watch))
          cmd
        case None =>
          throw new Exception(s"No main class specified for Scala.js module: ${ctx.module.id}. " +
            "Please add a 'mainClass' to the module in deder.pkl, or ensure exactly one @main method is defined.")
      }
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
          val results = runner.run(
            discoveredTests = discoveredTests,
            linkedJsDir = os.Path(linkedJsDir),
            moduleKind = jsModule.moduleKind,
            testOptions = testOptions,
            testParallelism = { val n = jsModule.testParallelism.toInt; if n == 0 then Runtime.getRuntime.availableProcessors() else n }
          )
          JUnitXmlReportWriter.outputDir(ctx.module, ctx.out).foreach(JUnitXmlReportWriter.writeReports(results, _))
          results
        }
      },
      isResultSuccessful = _.success,
      summarize = (results, notifs) => TestResultsSummary.summarize(results.map((m, r) => m.id -> r), notifs)
    )

  val all: Seq[Task[?, ?]] = Seq(
    fastLinkJsTask,
    fullLinkJsTask,
    linkJsTask,
    runJsTask,
    testJsTask
  )
}
