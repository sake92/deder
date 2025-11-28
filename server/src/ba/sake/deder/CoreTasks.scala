package ba.sake.deder

import ba.sake.deder.config.DederProject.{DederModule, JavaModule, ModuleType, ScalaModule}
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.deps.given

import scala.jdk.CollectionConverters.*
import ba.sake.tupson.JsonRW
import ba.sake.deder.zinc.{DederZincLogger, ZincCompiler}
import coursier.parse.DependencyParser

import java.io.File

class CoreTasks(zincCompiler: ZincCompiler) {

  // source dirs
  val sourcesTask = CachedTaskBuilder
    .make[Seq[DederPath]](
      name = "sources",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .build { ctx =>
      ctx.module match {
        case m: JavaModule  => m.sources.asScala.toSeq.map(s => DederPath(os.SubPath(s"${m.root}/${s}")))
        case m: ScalaModule => m.sources.asScala.toSeq.map(s => DederPath(os.SubPath(s"${m.root}/${s}")))
        case _              => ???
      }
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

  val compileTask = TaskBuilder
    .make[DederPath](
      name = "compile",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(sourcesTask)
    .dependsOn(javacOptionsTask)
    .build { ctx =>
      val sourceDirs = ctx.depResults._1: Seq[DederPath]
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
      val javacOptions = ctx.depResults._2
      val scalacOptions = javacOptions
      val scalaVersion = ctx.module match {
        case m: JavaModule  => "2.13.17" // dummy default scala version
        case m: ScalaModule => m.scalaVersion
        case _              => ???
      }
      val scalaCompilerJar = DependencyResolver.fetchOne(
        DependencyParser.dependency(s"org.scala-lang:scala-compiler:${scalaVersion}", scalaVersion).toOption.get
      )
      val scalaLibraryJar = DependencyResolver.fetchOne(
        DependencyParser.dependency(s"org.scala-lang:scala-library:${scalaVersion}", scalaVersion).toOption.get
      ) // TODO scala3-library
      val scalaReflectJar = DependencyResolver.fetchOne(
        DependencyParser.dependency(s"org.scala-lang:scala-reflect:${scalaVersion}", scalaVersion).toOption.get
      ) // TODO only for scala 2
      val zincCacheFile =
        DederGlobals.projectRootDir / os.SubPath(s".deder/out/${ctx.module.id}/compile/inc_compile.zip")
      val classesDir = os.SubPath(s".deder/out/${ctx.module.id}/compile/classes")
      val zincLogger = new DederZincLogger(ctx.notifications)
      // TODO go level by level

      val additionalCompileClasspath = ctx.transitiveResults.flatten.map(_.absPath)
      // println(s"Compile module: ${ctx.module.id} with additionalCompileClasspath: " + additionalCompileClasspath)
      zincCompiler.compile(
        scalaVersion,
        scalaCompilerJar,
        Seq(scalaLibraryJar),
        Some(scalaReflectJar),
        additionalCompileClasspath,
        zincCacheFile,
        sourceFiles,
        DederGlobals.projectRootDir / classesDir,
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
    .dependsOn(compileTask)
    .build { ctx =>
      val classesDir: DederPath = ctx.depResults._1

      val allDeps = ctx.module match {
        case m: JavaModule => Seq.empty
        case m: ScalaModule =>
          DependencyResolver.fetch(
            DependencyParser.dependency(s"org.scala-lang:scala-library:${m.scalaVersion}", m.scalaVersion).toOption.get
          )
        case _ => Seq.empty
      }

      // println(s"Resolved deps: " + allDeps)
      // TODO should the maven deps be pushed to end?
      // classdirs that are last in each module are pushed last in final classpath
      (Seq(classesDir).map(_.absPath) ++ ctx.transitiveResults.flatten.flatten ++ allDeps).reverse.distinct.reverse
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
    javacOptionsTask,
    compileTask,
    runClasspathTask,
    runTask
  )
}
