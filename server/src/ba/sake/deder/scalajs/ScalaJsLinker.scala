package ba.sake.deder.scalajs

import scala.concurrent.{Await, ExecutionContext, Future}
import org.scalajs.linker.*
import org.scalajs.linker.interface.*
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

import scala.concurrent.duration.Duration

class ScalaJsLinker(notifications: ServerNotificationsLogger, moduleId: String)(using ExecutionContext) {

  def link(
      irContainers: Seq[os.Path], // classesDir + all JARs in compileClasspath
      outputDir: os.Path,
      mainClass: Option[String],
      moduleKind: ModuleKind = ModuleKind.NoModule
  ): Unit = {
    notifications.add(ServerNotification.logDebug("Linking: " + irContainers))

    val linkerConfig = StandardConfig()
      .withModuleKind(moduleKind)
      .withClosureCompiler(false)
    // TODO true for production/minified builds
    // TODO optimized build, incremental...

    val linker = StandardImpl.linker(linkerConfig)
    val cache = StandardImpl.irFileCache().newCache

    val irFiles = PathIRContainer
      .fromClasspath(irContainers.map(_.toNIO))
      .flatMap { case (irContainers, paths) =>
        cache.cached(irContainers)
      }

    val moduleInitializers = mainClass.map { mc => ModuleInitializer.mainMethodWithArgs(mc, "main") }.toSeq

    val output = PathOutputDirectory(outputDir.toNIO)
    val logger = new DederScalaJsLogger(notifications, moduleId)
    val res = irFiles
      .flatMap { files =>
        linker.link(files, moduleInitializers, output, logger)
      }

    val report = Await.result(res, Duration.Inf)
    val publicModulePaths = report.publicModules.map(_.jsFileName).map(outputDir / _)
    notifications.add(
      ServerNotification.logInfo("Linking succeeded: " + publicModulePaths.mkString(", "))
    )
  }
}
