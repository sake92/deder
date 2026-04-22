package ba.sake.deder.graalvm

import java.io.File
import scala.util.Properties
import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.DederProject.{JavaModule, ModuleType}
import ba.sake.deder.{*, given}

class GraalVmNativeImageTasks(coreTasks: CoreTasks) {

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

  val nativeImageTask = CachedTaskBuilder
    .make[os.Path](
      name = "nativeImage",
      supportedModuleTypes = Set(ModuleType.JAVA, ModuleType.SCALA)
    )
    .dependsOn(coreTasks.runClasspathTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .dependsOn(graalvmHomeTask)
    .dependsOn(nativeImageOptionsTask)
    .build { ctx =>
      val (runClasspath, finalMainClass, graalvmHome, nativeImageOptions) = ctx.depResults

      ctx.module match {
        case m: JavaModule if m.graalvm == null =>
          throw DederException(
            s"Module '${ctx.module.id}' does not have a 'graalvm' section in deder.pkl. " +
              "Add a 'graalvm { }' block to enable the nativeImage task."
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
        Seq("-cp", cp, mainClass, (ctx.out / executableName).toString)

      ctx.notifications.add(
        ServerNotification.logInfo(s"Running native-image for module '${ctx.module.id}'", Some(ctx.module.id))
      )

      val outputHandler = os.ProcessOutput.Readlines { line =>
        ctx.notifications.add(ServerNotification.logInfo(line, Some(ctx.module.id)))
      }
      os.proc(command).call(cwd = ctx.out, stdout = outputHandler, stderr = outputHandler)

      val ext = if Properties.isWin then ".exe" else ""
      val executable = ctx.out / s"${executableName}${ext}"
      if !os.exists(executable) then throw DederException(s"native-image produced no executable at: $executable")
      executable
    }

  val all: Seq[Task[?, ?]] = Seq(
    graalvmHomeTask,
    nativeImageOptionsTask,
    nativeImageTask
  )

}
