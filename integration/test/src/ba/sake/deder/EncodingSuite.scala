package ba.sake.deder

class EncodingSuite extends BaseIntegrationSuite {

  private val asciiEnv = Map("LC_ALL" -> "C", "LANG" -> "C")
  private val quietFlags = Seq("--log-level", "warning", "--no-color")

  private def executeAscii(project: os.Path, args: String*): os.CommandResult =
    os.proc(Seq("java", "-jar", dederClientPath) ++ args)
      .call(cwd = project, env = asciiEnv, stderr = os.Pipe, check = false)

  test("ASCII locale preserves analysis across restarts and incremental Java and Scala edits") {
    val osName = System.getProperty("os.name")
    assume(osName.startsWith("Linux"), s"LC_ALL=C selects an ASCII filesystem encoding on Linux; found $osName")
    val project = stagedServerProject("sample-projects/multi", Map("logLevel" -> "DEBUG"))
    val common = project / "common/src/Common.scala"
    val javaSource = project / "common/src/JavaValue.java"
    val main = project / "uber/src/Main.scala"
    os.write(project / "common/src/Independent.scala", "package common\nobject Independent { val value = 17 }\n")
    os.write(
      javaSource,
      "package common; public class JavaValue { public static String value() { return \"java1\"; } }\n"
    )
    os.write.over(
      main,
      os.read(main).replace(
        "println(frontend.Frontend.value)",
        "println(frontend.Frontend.value)\n    println(common.JavaValue.value())"
      )
    )

    def compile(): Unit = {
      val result = executeAscii(project, (Seq("exec", "-t", "compile") ++ quietFlags)*)
      assertEquals(result.exitCode, 0, result.err.text())
      // Compilation can finish before analysis serialization fails. Exit status alone missed this.
      for module <- Seq("common", "frontend", "backend", "uber") do {
        val analysis = project / ".deder/out" / module / "compile/zinc/inc_compile.zip"
        assert(os.isFile(analysis), s"Missing analysis: $module")
      }
    }

    def restart(): Unit = {
      val stopped = executeAscii(project, "shutdown")
      assertEquals(stopped.exitCode, 0, stopped.err.text())
      Thread.sleep(1000)
    }

    try {
      compile()
      restart()
      os.write.over(common, os.read(common).replace("komon1", "ascii-scala"))
      val log = project / ".deder/logs/server.log"
      val scalaOffset = os.read.lines(log).size
      compile()
      val scalaCompiles = os.read.lines(log).drop(scalaOffset).filter(_.contains("] compiling "))
      assertEquals(scalaCompiles.size, 1, scalaCompiles.mkString("\n"))
      assert(scalaCompiles.head.contains("[common] compiling 1 Scala source to "), scalaCompiles.head)
      restart()
      os.write.over(javaSource, os.read(javaSource).replace("java1", "ascii-java"))
      val javaOffset = os.read.lines(log).size
      compile()
      val javaCompiles = os.read.lines(log).drop(javaOffset).filter(_.contains("] compiling "))
      assertEquals(javaCompiles.size, 1, javaCompiles.mkString("\n"))
      assert(javaCompiles.head.contains("[common] compiling 1 Java source to "), javaCompiles.head)
      val runtime = executeAscii(project, (Seq("exec", "-m", "uber", "-t", "run") ++ quietFlags)*)
      assertEquals(runtime.exitCode, 0, runtime.err.text())
      val lines = runtime.out.text().linesIterator.toSet
      assert(lines.contains("frontend + ascii-scala"), runtime.out.text())
      assert(lines.contains("ascii-java"), runtime.out.text())
    } finally {
      executeAscii(project, "shutdown")
      Thread.sleep(1000)
    }
  }
}
