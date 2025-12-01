package ba.sake.deder

import java.io.File
import scala.jdk.CollectionConverters.*
import dependency.parser.DependencyParser
import dependency.api.ops.*
import dependency.ScalaParameters
import ba.sake.tupson.JsonRW
import ba.sake.deder.zinc.{DederZincLogger, ZincCompiler}
import ba.sake.deder.config.DederProject.{DederModule, JavaModule, ModuleType, ScalaModule}
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.deps.given

class CoreTasks(zincCompiler: ZincCompiler) {

  // source dirs
  val sourcesTask = CachedTaskBuilder
    .make[Seq[DederPath]](
      name = "sources",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .build { ctx =>
      val sources = ctx.module match {
        case m: JavaModule  => m.sources.asScala.toSeq.map(s => DederPath(os.SubPath(s"${m.root}/${s}")))
        case m: ScalaModule => m.sources.asScala.toSeq.map(s => DederPath(os.SubPath(s"${m.root}/${s}")))
        case _              => ???
      }
      // println(s"Module: ${ctx.module.id} sources: " + sources)
      sources
    }

  val javacOptionsTask = CachedTaskBuilder
    .make[Seq[String]](
      name = "javacOptions",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .build { ctx =>
      ctx.module match {
        case m: JavaModule  => m.javacOptions.asScala.toSeq
        case m: ScalaModule => m.javacOptions.asScala.toSeq
        case _              => ???
      }
    }

  val scalaVersionTask = TaskBuilder
    .make[String](
      name = "scalaVersion",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .build { ctx =>
      ctx.module match {
        case m: JavaModule  => "2.13.17" // dummy default scala version
        case m: ScalaModule => m.scalaVersion
        case _              => ???
      }
    }

  val dependenciesTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "dependencies",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(scalaVersionTask)
    .build { ctx =>
      val scalaVersion = ctx.depResults._1
      val depDeclarations = ctx.module match {
        case m: JavaModule  => m.deps.asScala.toSeq
        case m: ScalaModule => m.deps.asScala.toSeq
        case _              => Seq.empty
      }
      val coursierDeps = depDeclarations
        .map(depDecl => DependencyParser.parse(depDecl).toOption.get.applyParams(ScalaParameters(scalaVersion)))
        .map(_.toCs)
      (DependencyResolver.fetch(
        coursierDeps,
        Some(ctx.notifications)
      ) ++ ctx.transitiveResults.flatten.flatten).reverse.distinct.reverse
    }

  val compileTask = TaskBuilder
    .make[DederPath](
      name = "compile",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(scalaVersionTask)
    .dependsOn(sourcesTask)
    .dependsOn(javacOptionsTask)
    .dependsOn(dependenciesTask)
    .build { ctx =>
      val scalaVersion = ctx.depResults._1
      val sourceDirs = ctx.depResults._2: Seq[DederPath]
      val javacOptions = ctx.depResults._3
      val dependencies = ctx.depResults._4: Seq[os.Path]
      val sourceFiles = sourceDirs
        .flatMap { sourceDir =>
          os.walk(
            sourceDir.absPath,
            skip = p => {
              if os.isDir(p) then false
              else if os.isFile(p) then !(p.ext == "scala" || p.ext == "java")
              else true
            }
          )
        }
        .filter(os.isFile)

      val scalacOptions = javacOptions

      val scalaCompilerJar = DependencyResolver.fetchOne(
        DependencyParser
          .parse(s"org.scala-lang:scala-compiler:${scalaVersion}")
          .toOption
          .get
          .applyParams(ScalaParameters(scalaVersion))
          .toCs
      )
      val scalaLibraryJar = DependencyResolver.fetchOne(
        DependencyParser
          .parse(s"org.scala-lang:scala-library:${scalaVersion}")
          .toOption
          .get
          .applyParams(ScalaParameters(scalaVersion))
          .toCs
      ) // TODO scala3-library
      val scalaReflectJar = DependencyResolver.fetchOne(
        DependencyParser
          .parse(s"org.scala-lang:scala-reflect:${scalaVersion}")
          .toOption
          .get
          .applyParams(ScalaParameters(scalaVersion))
          .toCs
      ) // TODO only for scala 2
      val zincCacheFile = ctx.out / "inc_compile.zip"
      val classesDir = ctx.out / "classes"
      val zincLogger = new DederZincLogger(ctx.notifications, ctx.module.id)
      // TODO go level by level

      val additionalCompileClasspath = ctx.transitiveResults.flatten.map(_.absPath) ++ dependencies
      println(
        s"Compiling module: ${ctx.module.id} with " +
          s"scalaVersion: ${scalaVersion} " +
          s"sourceDirs: ${sourceDirs} " +
          s"additionalCompileClasspath: ${additionalCompileClasspath} " +
          s"scalacOptions: ${scalacOptions}" +
          s"javacOptions: ${javacOptions}"
      )
      zincCompiler.compile(
        scalaVersion,
        scalaCompilerJar,
        Seq(scalaLibraryJar),
        Some(scalaReflectJar),
        additionalCompileClasspath,
        zincCacheFile,
        sourceFiles,
        classesDir,
        scalacOptions,
        javacOptions,
        zincLogger
      )
      DederPath(classesDir)
    }

  val runClasspathTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "runClasspath",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(dependenciesTask)
    .dependsOn(compileTask)
    .build { ctx =>
      val dependencies: Seq[os.Path] = ctx.depResults._1
      val classesDir: DederPath = ctx.depResults._2

      val mandatoryDeps = ctx.module match {
        case m: JavaModule => Seq.empty
        case m: ScalaModule =>
          DependencyResolver.fetch(
            Seq(
              DependencyParser
                .parse(s"org.scala-lang:scala-library:${m.scalaVersion}")
                .toOption
                .get
                .applyParams(ScalaParameters(m.scalaVersion))
                .toCs
            )
          )
        case _ => Seq.empty
      }

      // println(s"Resolved deps: " + allDeps)
      // classdirs that are last in each module are pushed last in final classpath
      (Seq(classesDir).map(
        _.absPath
      ) ++ ctx.transitiveResults.flatten.flatten ++ mandatoryDeps ++ dependencies).reverse.distinct.reverse
    }

  val runTask = TaskBuilder
    .make[String](
      name = "run",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .dependsOn(runClasspathTask)
    .build { ctx =>
      val runClasspath = ctx.depResults._1: Seq[os.Path]
      val cp = runClasspath.map(_.toString)
      val mainClass = ctx.module match {
        case m: JavaModule  => m.mainClass
        case m: ScalaModule => m.mainClass
        case _              => ???
      }
      val cmd = Seq("java", "-cp", cp.mkString(File.pathSeparator), mainClass)
      println(s"Running command: " + cmd)
      ctx.notifications.add(ServerNotification.RunSubprocess(cmd))
      ""
    }

  val all: Seq[Task[?, ?]] = Seq(
    sourcesTask,
    scalaVersionTask,
    javacOptionsTask,
    dependenciesTask,
    compileTask,
    runClasspathTask,
    runTask
  )
}
