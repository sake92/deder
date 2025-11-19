package ba.sake.deder

import ba.sake.deder.deps.DependencyResolver

import java.time.Instant
import ba.sake.tupson.JsonRW
import ba.sake.deder.zinc.ZincCompiler
import coursier.parse.DependencyParser

class CoreTasks(zincCompiler: ZincCompiler) {

  val sourcesTask = TaskBuilder
    .make[Seq[DederPath]](
      name = "sources",
      transitive = false,
      supportedModuleTypes = Set(ModuleType.Scala, ModuleType.Java)
    )
    .build { ctx =>
      ctx.module match {
        case m: JavaModule => m.sources
        case _             => ???
      }
    }

  val javacOptionsTask = TaskBuilder
    .make[Seq[String]](
      name = "javacOptions",
      transitive = false,
      supportedModuleTypes = Set(ModuleType.Scala, ModuleType.Java)
    )
    .build { ctx =>
      ctx.module match {
        case m: JavaModule => m.javacOptions
        case _             => ???
      }
    }

  val compileTask = TaskBuilder
    .make[Seq[String]](
      name = "compile",
      transitive = true,
      supportedModuleTypes = Set(ModuleType.Scala, ModuleType.Java)
    )
    .dependsOn(sourcesTask)
    .dependsOn(javacOptionsTask)
    .build { ctx =>
      val sourceDirs = ctx.depResults._1
      val sourceFiles = sourceDirs.flatMap { sourceDir =>
        os.walk(
          os.pwd / sourceDir.path,
          skip = p => {
            if os.isDir(p) then false
            else if os.isFile(p) then p.ext != "scala" && p.ext != "java"
            else true
          }
        )
      }
      val javacOptions = ctx.depResults._2
      val scalacOptions = javacOptions
      val scalaVersion = ctx.module match {
        case m: JavaModule => "2.13.17"
        case _             => ???
      }
      val scalaCompilerJar = DependencyResolver.fetchOne(
        DependencyParser.dependency(s"org.scala-lang:scala-compiler:${scalaVersion}", scalaVersion).toOption.get
      )
      val scalaLibraryJar = DependencyResolver.fetchOne(
        DependencyParser.dependency(s"org.scala-lang:scala-library:${scalaVersion}", scalaVersion).toOption.get
      )
      println(scalaLibraryJar)
      val scalaReflectJar = DependencyResolver.fetchOne(
        DependencyParser.dependency(s"org.scala-lang:scala-reflect:${scalaVersion}", scalaVersion).toOption.get
      ) // TODO only for scala 2
      val zincCacheFile = os.pwd / os.RelPath(s".deder/out/${ctx.module.id}/compile/inc_compile.zip")
      val classesDir = os.pwd / os.RelPath(s".deder/out/${ctx.module.id}/compile/classes")
      zincCompiler.compile(
        scalaVersion,
        scalaCompilerJar,
        Seq(scalaLibraryJar),
        Some(scalaReflectJar),
        zincCacheFile,
        sourceFiles,
        classesDir,
        scalacOptions,
        javacOptions
      )
      Seq("file1.class", "file2.class")
    }

  val runTask = TaskBuilder
    .make[String](
      name = "run",
      supportedModuleTypes = Set(ModuleType.Scala, ModuleType.Java)
    )
    .dependsOn(compileTask)
    .build { ctx =>
      val classfiles = ctx.depResults._1
      println(s"[module ${ctx.module.id}] Running java -cp ${classfiles.mkString(" ")}")
      "runRes"
    }

  val all: Seq[Task[?, ?]] = Seq(
    sourcesTask,
    javacOptionsTask,
    compileTask,
    runTask
  )
}
