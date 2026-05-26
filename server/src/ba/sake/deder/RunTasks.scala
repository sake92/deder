package ba.sake.deder

import java.io.File
import scala.jdk.CollectionConverters.*
import com.typesafe.scalalogging.StrictLogging
import ba.sake.deder.config.DederProject.{
  CompileOrder => ConfigCompileOrder,
  JavaModule,
  JavaTestModule,
  ModuleType,
  ScalaJsModule,
  ScalaJsTestModule,
  ScalaModule,
  ScalaNativeModule,
  ScalaNativeTestModule,
  ScalaTestModule
}
import ba.sake.deder.config.DederProject
import ba.sake.deder.deps.Dependency
import ba.sake.deder.deps.{DepTree, given}

class RunTasks(coreTasks: CoreTasks) extends StrictLogging{
    import coreTasks.*

  val runTask = TaskBuilder
    .make[Seq[String]](
      name = "run",
      singleton = true,
      category = "Run"
    )
    .dependsOn(runClasspathTask)
    .dependsOn(mainClassesTask)
    .dependsOn(finalMainClassTask)
    .dependsOn(jvmOptionsTask)
    .build { ctx =>
      val (runClasspath, discoveredMainClasses, finalMainClass, jvmOptions) = ctx.depResults
      finalMainClass match {
        case Some(mc) =>
          val cp = runClasspath.map(_.toString)
          val cmd = Seq("java") ++ jvmOptions ++ Seq("-cp", cp.mkString(File.pathSeparator), mc) ++ ctx.args
          logger.debug(s"Client should run command: ${cmd}")
          val forkEnv = ctx.module match {
            case m: JavaModule => m.forkEnv.asScala.to(Map)
            case _             => Map.empty
          }
          ctx.notifications.add(ServerNotification.RunSubprocess(cmd, forkEnv, ctx.watch))
          cmd
        case None =>
          if discoveredMainClasses.length > 1 then
            throw new Exception(
              s"Multiple main classes discovered for module '${ctx.module.id}': ${discoveredMainClasses.mkString(", ")}. " +
                "Please specify which one to run in deder.pkl or use runMain task with main class argument."
            )
          else throw new Exception(s"No main class specified for module: ${ctx.module.id}")
      }
    }

  val runMainTask = TaskBuilder
    .make[Seq[String]](
      name = "runMain",
      singleton = true,
      category = "Run"
    )
    .dependsOn(runClasspathTask)
    .dependsOn(mainClassesTask)
    .dependsOn(jvmOptionsTask)
    .build { ctx =>
      val (runClasspath, discoveredMainClasses, jvmOptions) = ctx.depResults
      val selectedMainClass = ctx.args.headOption.getOrElse(
        throw RuntimeException(
          "No main class specified to run. Please provide the main class as an argument. " +
            s"Possible candidates: ${discoveredMainClasses.mkString(", ")}"
        )
      )
      val finalMainClass = discoveredMainClasses.find(_ == selectedMainClass)
      finalMainClass match {
        case Some(mc) =>
          val cp = runClasspath.map(_.toString)
          val cmd = Seq("java") ++ jvmOptions ++ Seq("-cp", cp.mkString(File.pathSeparator), mc) ++ ctx.args.tail
          logger.debug(s"Client should run command: ${cmd}")
          val forkEnv = ctx.module match {
            case m: JavaModule => m.forkEnv.asScala.to(Map)
            case _             => Map.empty
          }
          ctx.notifications.add(ServerNotification.RunSubprocess(cmd, forkEnv, ctx.watch))
          cmd
        case None =>
          throw new RuntimeException(
            s"Class '${selectedMainClass}' with main method not found for module '${ctx.module.id}'. " +
              s"Possible candidates: ${discoveredMainClasses.mkString(", ")}"
          )
      }
    }

  val runMvnAppTask = TaskBuilder
    .make[Seq[String]](
      name = "runMvnApp",
      category = "Run"
    )
    .dependsOn(sourcesTask)
    .dependsOn(scalaVersionTask)
    .dependsOn(jvmOptionsTask)
    .build { ctx =>
      val (sources, scalaVersion, jvmOptions) = ctx.depResults
      val sourcePaths = sources.map(_.absPath).filter(os.exists(_)).map(_.toString)

      val userMvnAppsMap: Map[String, DederProject.MvnApp] = ctx.module match {
        case m: JavaModule => m.mvnApps.asScala.toMap
        case _             => Map.empty
      }
      val autoMvnAppsMap: Map[String, DederProject.MvnApp] = ctx.module match {
        case m: ScalaModule =>
          val scalafmtDeps = List("org.scalameta:scalafmt-cli_2.13:3.10.7")
          val scalafmtMain = "org.scalafmt.cli.Cli"
          Map(
            "fmt" -> DederProject.MvnApp(scalafmtDeps.asJava, scalafmtMain, sourcePaths.asJava),
            "fmtCheck" -> DederProject.MvnApp(scalafmtDeps.asJava, scalafmtMain, (Seq("--check") ++ sourcePaths).asJava)
          )
        case _ => Map.empty
      }
      val effectiveMap = (userMvnAppsMap ++ autoMvnAppsMap).map { case (name, app) =>
        name -> (app.deps.asScala.toSeq, app.mainClass, app.args.asScala.toSeq)
      }

      val mvnAppName = ctx.args.headOption.getOrElse(
        throw new RuntimeException(
          s"No maven app name specified. Available maven apps: ${effectiveMap.keys.mkString(", ")}"
        )
      )
      effectiveMap.get(mvnAppName) match {
        case Some((deps, mainClass, args)) =>
          val dependencies = deps.map(Dependency.make(_, scalaVersion))
          logger.info(
            s"Resolving dependencies for maven app '${mvnAppName}': ${dependencies.map(_.toString).mkString(", ")}"
          )
          val jars = ctx.dependencyResolver.fetchFiles(dependencies, Some(ctx.notifications))
          logger.info(s"Resolved jars for maven app '${mvnAppName}': ${jars.map(_.toString).mkString(", ")}")
          val cp = jars.map(_.toString).mkString(File.pathSeparator)
          val commandArgs = args ++ ctx.args.tail
          val cmd = Seq("java") ++ jvmOptions ++ Seq("-cp", cp, mainClass) ++ commandArgs
          logger.info(s"Running maven app '${mvnAppName}': ${cmd}")
          val forkEnv = ctx.module match {
            case m: JavaModule => m.forkEnv.asScala.to(Map)
            case _             => Map.empty
          }
          ctx.notifications.add(ServerNotification.RunSubprocess(cmd, forkEnv, false))
          cmd
        case _ =>
          throw new RuntimeException(
            s"Maven app '${mvnAppName}' not found for module '${ctx.module.id}'. " +
              s"Available maven apps: ${effectiveMap.keys.mkString(", ")}"
          )
      }
    }

  val replDepsTask = CachedTaskBuilder
    .make[Seq[Dependency]](
      name = "replDeps",
      category = "REPL"
    )
    .dependsOn(scalaVersionTask)
    .build { ctx =>
      val scalaVersion = ctx.depResults._1
      ctx.module match {
        case _: ScalaModule =>
          if scalaVersion.startsWith("3.") then
            // scala3-repl is a separate artifact only from 3.8+; older 3.x include the REPL in scala3-compiler
            val minorVersion = scalaVersion.split("\\.").lift(1).flatMap(_.toIntOption).getOrElse(0)
            if minorVersion >= 8 then Seq(Dependency.make(s"org.scala-lang::scala3-repl:${scalaVersion}", scalaVersion))
            else Seq(Dependency.make(s"org.scala-lang::scala3-compiler:${scalaVersion}", scalaVersion))
          else
            Seq(
              Dependency.make(s"org.scala-lang:scala-compiler:${scalaVersion}", scalaVersion),
              Dependency.make(s"org.scala-lang:scala-reflect:${scalaVersion}", scalaVersion)
            )
        case _ => Seq.empty
      }
    }

  val replJarsTask = CachedTaskBuilder
    .make[Seq[os.Path]](
      name = "replJars",
      category = "REPL"
    )
    .dependsOn(replDepsTask)
    .build { ctx =>
      val replDeps = ctx.depResults._1
      if replDeps.isEmpty then Seq.empty
      else ctx.dependencyResolver.fetchFiles(replDeps)
    }

  val replTask = TaskBuilder
    .make[Seq[String]](
      name = "repl",
      singleton = true,
      supportedModuleTypes = Set(
        ModuleType.JAVA,
        ModuleType.JAVA_TEST,
        ModuleType.SCALA,
        ModuleType.SCALA_TEST
      ),
      category = "REPL"
    )
    .dependsOn(runClasspathTask)
    .dependsOn(scalaVersionTask)
    .dependsOn(jvmOptionsTask)
    .dependsOn(javaHomeTask)
    .dependsOn(replJarsTask)
    .build { ctx =>
      val (runClasspath, scalaVersion, jvmOptions, javaHome, replJars) = ctx.depResults
      val forkEnv = ctx.module match {
        case m: JavaModule => m.forkEnv.asScala.to(Map)
        case _             => Map.empty
      }
      val cmd = ctx.module match {
        case _: ScalaModule =>
          val replCp = replJars.map(_.toString).mkString(File.pathSeparator)
          val userClasspath = runClasspath.map(_.toString).mkString(File.pathSeparator)
          val mainClass =
            if scalaVersion.startsWith("3.") then "dotty.tools.repl.Main"
            else "scala.tools.nsc.MainGenericRunner"
          Seq("java") ++ jvmOptions ++ Seq("-cp", replCp, mainClass, "-classpath", userClasspath) ++ ctx.args
        case _ =>
          val jshellBin = javaHome.map(h => (h / "bin" / "jshell").toString).getOrElse("jshell")
          val userClasspath = runClasspath.map(_.toString).mkString(File.pathSeparator)
          Seq(jshellBin, "--class-path", userClasspath) ++ ctx.args
      }
      logger.debug(s"Client should run command: ${cmd}")
      ctx.notifications.add(ServerNotification.RunSubprocess(cmd, forkEnv, false))
      cmd
    }

  // order matters for dependency resolution!!
  val all: Seq[Task[?, ?, ?]] = Seq(
    runTask,
    runMainTask,
    runMvnAppTask,
    replDepsTask,
    replJarsTask,
    replTask
  )
}
