package ba.sake.deder

import java.time.Instant
import ba.sake.tupson.JsonRW
import ba.sake.deder.zinc.ZincCompiler

class CoreTasks(zincCompiler: ZincCompiler) {

  val sourcesTask = TaskBuilder
    .make[DederPath](
      name = "sources",
      transitive = false,
      supportedModuleTypes = Set(ModuleType.Scala, ModuleType.Java)
    )
    .build { ctx =>
      println(s"[module ${ctx.module.id}] Resolving sources... " + Instant.now())
      DederPath("d/src")
    }

  val javacOptionsTask = TaskBuilder
    .make[Seq[String]](
      name = "javacOptions",
      transitive = false,
      supportedModuleTypes = Set(ModuleType.Scala, ModuleType.Java)
    )
    .build { ctx =>
      println(s"[module ${ctx.module.id}] Resolving javacOptions... " + Instant.now())
      Seq("-deprecation")
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
      val sources = ctx.depResults._1
      val javacOptions = ctx.depResults._2
      println(s"[module ${ctx.module.id}] Compiling java with javac ${sources}... " + Instant.now())
      Thread.sleep(1000)
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
