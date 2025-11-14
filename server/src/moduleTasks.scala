package bla

import java.time.Instant

object ModuleTasksRegistry {
  def getByModule(module: Module): Seq[Task[?, ?]] = module match {
    case m: JavaModule => JavaModuleTasks(m).all
    case _             => Seq.empty
  }
}

class JavaModuleTasks(module: JavaModule) {

  val compile = TaskBuilder
    .make[Seq[String]](
      name = "compile",
      transitive = true
    )
    .build { _ =>
      println(s"[module ${module.id}] Compiling java... " + Instant.now())
      /*if module.id.startsWith("c") then
        Thread.sleep(2000)
        throw RuntimeException("ughhhh")
      else*/
      Thread.sleep(1000)
      Seq("file1.class", "file2.class")
    }

  val run = TaskBuilder
    .make(name = "run")
    .dependsOn(compile)
    .build { ctx =>
      val classfiles = ctx.depResults._1
      println(s"[module ${module.id}] Running java -cp ${classfiles.mkString(" ")}")
    }

  val all: Seq[Task[?, ?]] = Seq(
    compile,
    run
  )
}
