package ba.sake.deder

import ba.sake.deder.config.DederProject.{JavaModule, ModuleType, ScalaModule}
import ba.sake.deder.deps.DependencyResolver

import java.time.Instant
import scala.jdk.CollectionConverters.*
import ba.sake.tupson.JsonRW
import ba.sake.deder.zinc.{DederZincLogger, ZincCompiler}
import coursier.parse.DependencyParser

class CoreTasks(zincCompiler: ZincCompiler) {

  // source dirs
  val sourcesTask = TaskBuilder
    .make[Seq[DederPath]](
      name = "sources",
      transitive = false,
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA),
      cached = false
    )
    .build { ctx =>
      ctx.module match {
        case m: JavaModule  => m.sources.asScala.toSeq.map(s => DederPath(os.SubPath(s"${m.root}/${s}")))
        case m: ScalaModule => m.sources.asScala.toSeq.map(s => DederPath(os.SubPath(s"${m.root}/${s}")))
        case _              => ???
      }
    }

  val javacOptionsTask = TaskBuilder
    .make[Seq[String]](
      name = "javacOptions",
      transitive = false,
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
    .make[Seq[String]](
      name = "compile",
      transitive = true,
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .dependsOn(sourcesTask)
    .dependsOn(javacOptionsTask)
    .build { ctx =>
      val sourceDirs = ctx.depResults._1
      val sourceFiles = sourceDirs
        .flatMap { sourceDir =>
          os.walk(
            DederGlobals.projectRootDir / sourceDir.path,
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
      )// TODO scala3-library
      val scalaReflectJar = DependencyResolver.fetchOne(
        DependencyParser.dependency(s"org.scala-lang:scala-reflect:${scalaVersion}", scalaVersion).toOption.get
      ) // TODO only for scala 2
      val zincCacheFile =
        DederGlobals.projectRootDir / os.RelPath(s".deder/out/${ctx.module.id}/compile/inc_compile.zip")
      val classesDir = DederGlobals.projectRootDir / os.RelPath(s".deder/out/${ctx.module.id}/compile/classes")
      val zincLogger = new DederZincLogger(ctx.notifications)
      zincCompiler.compile(
        scalaVersion,
        scalaCompilerJar,
        Seq(scalaLibraryJar),
        Some(scalaReflectJar),
        zincCacheFile,
        sourceFiles,
        classesDir,
        scalacOptions,
        javacOptions,
        zincLogger
      )
      Seq("file1.class", "file2.class")
    }

  val runTask = TaskBuilder
    .make[String](
      name = "run",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .dependsOn(compileTask)
    .build { ctx =>
      val classfiles = ctx.depResults._1
      "runRes"
    }

  val all: Seq[Task[?, ?]] = Seq(
    sourcesTask,
    javacOptionsTask,
    compileTask,
    runTask
  )
}
