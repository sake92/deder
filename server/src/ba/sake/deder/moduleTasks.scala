package ba.sake.deder

import java.time.Instant
import ba.sake.tupson.JsonRW

object ModuleTasksRegistry {
  def getByModule(module: Module): Seq[Task[?, ?]] = module match {
    case m: JavaModule => JavaModuleTasks(m).all
    case _             => Seq.empty
  }
}

class JavaModuleTasks(module: JavaModule) {

 

  val sourcesTask = TaskBuilder
    .make[DederPath](
      name = "sources",
      transitive = false
    )
    .build { _ =>
      println(s"[module ${module.id}] Resolving sources... " + Instant.now())
      DederPath("d/src")
    }

  val javacOptionsTask = TaskBuilder
    .make[Seq[String]](
      name = "javacOptions",
      transitive = false
    )
    .build { ctx =>
      println(s"[module ${module.id}] Resolving javacOptions... " + Instant.now())
      Seq("-deprecation")
    }

  val compileTask = TaskBuilder
    .make[Seq[String]](
      name = "compile",
      transitive = true
    )
    .dependsOn(sourcesTask)
    .dependsOn(javacOptionsTask)
    .build { ctx =>
      val sources = ctx.depResults._1
      val javacOptions = ctx.depResults._2
      println(s"[module ${module.id}] Compiling java with javac ${sources}... " + Instant.now())
      Thread.sleep(1000)
      Seq("file1.class", "file2.class")
    }

  val runTask = TaskBuilder
    .make[String](name = "run")
    .dependsOn(compileTask)
    .build { ctx =>
      val classfiles = ctx.depResults._1
      println(s"[module ${module.id}] Running java -cp ${classfiles.mkString(" ")}")
      "runRes"
    }

  val all: Seq[Task[?, ?]] = Seq(
    sourcesTask,
    javacOptionsTask,
    compileTask,
    runTask
  )
}
