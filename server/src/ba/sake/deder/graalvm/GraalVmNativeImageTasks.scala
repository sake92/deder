package ba.sake.deder.graalvm

import java.io.File
import scala.util.Properties
import scala.jdk.CollectionConverters.*
import com.typesafe.scalalogging.StrictLogging
import ba.sake.deder.config.DederProject.{JavaModule, ModuleType}
import ba.sake.deder.{*, given}

class GraalVmNativeImageTasks(coreTasks: CoreTasks)  extends StrictLogging {

  val graalvmHomeTask = ConfigValueTask[Option[os.Path]](
    name = "graalvmHome",
    supportedModuleTypes = Set(ModuleType.JAVA, ModuleType.SCALA),
    execute = { ctx =>
      ctx.module match {
        case m: JavaModule if m.graalvm != null =>
          Option(m.graalvm.graalvmHome)
            .map(os.Path(_))
            .orElse(sys.env.get("GRAALVM_HOME").map(os.Path(_)))
        case _ => None
      }
    }
  )

  val nativeImageOptionsTask = ConfigValueTask[Seq[String]](
    name = "nativeImageOptions",
    supportedModuleTypes = Set(ModuleType.JAVA, ModuleType.SCALA),
    execute = { ctx =>
      ctx.module match {
        case m: JavaModule if m.graalvm != null => m.graalvm.nativeImageOptions.asScala.toSeq
        case _                                  => Seq.empty
      }
    }
  )

  val nativeIncludedResourcesOptionsTask = CachedTaskBuilder
    .make[Seq[String]](
      name = "nativeIncludedResourcesOptions",
      supportedModuleTypes = Set(ModuleType.JAVA, ModuleType.SCALA)
    )
    .dependsOn(coreTasks.resourcesTask)
    .build { ctx =>
      val resourceDirs = ctx.depResults._1
      val resourceFiles = resourceDirs
        .map(_.absPath)
        .filter(os.isDir(_))
        .flatMap { dir =>
          os.walk(dir)
            .filter(os.isFile(_))
            .map(_.subRelativeTo(dir).toString)
        }
      if resourceFiles.isEmpty then Seq.empty
      else Seq(s"-H:IncludeResources=${resourceFiles.mkString("|")}")
    }

  val graalvmNativeImageTask = CachedTaskBuilder
    .make[os.Path](
      name = "graalvmNativeImage",
      supportedModuleTypes = Set(ModuleType.JAVA, ModuleType.SCALA)
    )
    .dependsOn(coreTasks.runClasspathTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .dependsOn(graalvmHomeTask)
    .dependsOn(nativeImageOptionsTask)
    .dependsOn(nativeIncludedResourcesOptionsTask)
    .build { ctx =>
      val (runClasspath, finalMainClass, graalvmHome, nativeImageOptions, includedResourcesOptions) = ctx.depResults

      ctx.module match {
        case m: JavaModule if m.graalvm == null =>
          throw DederException(
            s"Module '${ctx.module.id}' does not have a 'graalvm' section in deder.pkl. " +
              "Add a 'graalvm { }' block to enable the graalvmNativeImage task."
          )
        case _ =>
      }

      val nativeImageTool = graalvmHome match {
        case Some(home) =>
          val toolName = if Properties.isWin then "native-image.cmd" else "native-image"
          val toolPath = home / "bin" / toolName
          if !os.exists(toolPath) then throw DederException(s"native-image tool not found at: $toolPath")
          toolPath
        case None =>
          throw DederException(
            "GraalVM home not configured. Set 'graalvm.graalvmHome' in deder.pkl " +
              "or set the GRAALVM_HOME environment variable (and restart the server)."
          )
      }

      val mainClass = finalMainClass.getOrElse(
        throw DederException(
          s"No main class found for module '${ctx.module.id}'. " +
            "Set 'mainClass' in deder.pkl."
        )
      )

      val executableName = "native-executable"
      val cp = runClasspath.map(_.toString).mkString(File.pathSeparator)
      val command = Seq(nativeImageTool.toString) ++
        nativeImageOptions ++
        includedResourcesOptions ++
        Seq("-cp", cp, mainClass, (ctx.out / executableName).toString)

      ctx.notifications.add(
        ServerNotification.logInfo(s"Running native-image for module '${ctx.module.id}'", Some(ctx.module.id))
      )

      val outputHandler = os.ProcessOutput.Readlines { line =>
        ctx.notifications.add(ServerNotification.logInfo(line, Some(ctx.module.id)))
      }
      os.makeDir.all(ctx.out)
      logger.info(s"Executing command: ${command.mkString(" ")}")
      os.proc(command).call(cwd = ctx.out, stdout = outputHandler, stderr = outputHandler)

      val ext = if Properties.isWin then ".exe" else ""
      val executable = ctx.out / s"${executableName}${ext}"
      if !os.exists(executable) then throw DederException(s"native-image produced no executable at: $executable")
      executable
    }

  val all: Seq[Task[?, ?]] = Seq(
    graalvmHomeTask,
    nativeImageOptionsTask,
    nativeIncludedResourcesOptionsTask,
    graalvmNativeImageTask
  )

}
