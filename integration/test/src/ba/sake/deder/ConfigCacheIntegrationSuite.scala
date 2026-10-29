package ba.sake.deder

import java.time.Instant
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*
import scala.util.Using
import ba.sake.tupson.*

class ConfigCacheIntegrationSuite extends BaseIntegrationSuite {

  private case class CacheMetadata(inputsHash: String, cachedAt: Instant) derives JsonRW
  private case class ListedTask(name: String) derives JsonRW

  for task <- Seq("fastLinkJs", "fullLinkJs", "linkJs") do
    test(s"$task invalidates on linker config and module kind changes") {
      withTestProject("sample-projects/scalajs") { project =>
        build(project, "frontend", task)
        val original = metadata(project, "frontend", task)
        val compileBefore = metadata(project, "frontend", "compile")
        build(project, "frontend", task)
        assertEquals(metadata(project, "frontend", task), original)

        editConfig(project, "// My custom header", "// Changed header")
        build(project, "frontend", task)
        val afterHeader = metadata(project, "frontend", task)
        assertNotEquals(afterHeader.inputsHash, original.inputsHash)
        val jsFile = project / ".deder/out/frontend" / task / "main.js"
        assert(os.read(jsFile).startsWith("// Changed header\n"))
        assertEquals(metadata(project, "frontend", "compile"), compileBefore)

        editConfig(project, "es-module", "commonjs-module")
        build(project, "frontend", task)
        assertNotEquals(metadata(project, "frontend", task).inputsHash, afterHeader.inputsHash)
        val result = os.proc("node", jsFile).call(cwd = project, stderr = os.Pipe)
        assertEquals(result.out.text().trim, "Hello, Scala.js!")
        assertEquals(metadata(project, "frontend", "compile"), compileBefore)
      }
    }

  test("compile order changes invalidate compilation without source edits") {
    withJavaProject { project =>
      build(project, "app", "compile")
      val original = metadata(project, "app", "compile")
      build(project, "app", "compile")
      assertEquals(metadata(project, "app", "compile"), original)

      editConfig(project, "java-then-scala", "mixed")
      build(project, "app", "compile")
      assertNotEquals(metadata(project, "app", "compile").inputsHash, original.inputsHash)
      val classes = project / ".deder/out/app/compile/classes"
      val result = os.proc("java", "-cp", classes, "app.Main").call(cwd = project)
      assertEquals(result.out.text().trim, "hello")
    }
  }

  test("assembly tracks shading rule contents, path changes and removal") {
    withJavaProject { project =>
      val rules = project / "app/shade.rules"
      os.write(rules, "rule app.** first.app.@1\n")
      editConfig(project, "// shading", "shadeRulesFile = \"shade.rules\"")
      build(project, "app", "assembly")
      val original = metadata(project, "app", "assembly")
      val compileBefore = metadata(project, "app", "compile")
      assertAssemblyMain(project, "first.app.Main")
      build(project, "app", "assembly")
      assertEquals(metadata(project, "app", "assembly"), original)

      os.write.over(rules, "rule app.** second.app.@1\n")
      build(project, "app", "assembly")
      assertNotEquals(metadata(project, "app", "assembly").inputsHash, original.inputsHash)
      assertAssemblyMain(project, "second.app.Main")

      // Absolute paths outside the module remain supported and content-hashed.
      val otherRules = project / "other.rules"
      os.write(otherRules, "rule app.** third.app.@1\n")
      editConfig(project, "\"shade.rules\"", s"\"$otherRules\"")
      build(project, "app", "assembly")
      assertAssemblyMain(project, "third.app.Main")

      os.write.over(otherRules, "rule app.** fourth.app.@1\n")
      build(project, "app", "assembly")
      assertAssemblyMain(project, "fourth.app.Main")

      editConfig(project, s"shadeRulesFile = \"$otherRules\"", "// shading")
      build(project, "app", "assembly")
      assertAssemblyMain(project, "app.Main")
      assertEquals(metadata(project, "app", "compile"), compileBefore)
    }
  }

  test("native linker settings invalidate linking without recompiling sources") {
    withTestProject("sample-projects/scalanative") { project =>
      build(project, "cli", "nativeLink")
      val original = metadata(project, "cli", "nativeLink")
      val compileBefore = metadata(project, "cli", "compile")
      build(project, "cli", "nativeLink")
      assertEquals(metadata(project, "cli", "nativeLink"), original)

      editConfig(project, "scalaNativeVersion = \"0.5.10\"", "scalaNativeVersion = \"0.5.10\"; check = true")
      build(project, "cli", "nativeLink")
      assertNotEquals(metadata(project, "cli", "nativeLink").inputsHash, original.inputsHash)
      assertEquals(metadata(project, "cli", "compile"), compileBefore)
      val binary = os.read(project / ".deder/out/cli/nativeLink/metadata.json").parseJson[TaskResult[String]].value
      val result = os.proc(binary).call(cwd = project, stderr = os.Pipe)
      assertEquals(result.out.text().trim, "Hello from Scala Native!")
    }
  }

  test("configuration inputs are hidden from task listings, plans and completion") {
    withTestProject("sample-projects/scalajs") { project =>
      val listed = command(project, "tasks", "-m", "frontend", "--format", "json")
        .out.text().parseJson[Map[String, Seq[ListedTask]]]
      val names = listed("frontend").map(_.name).toSet
      assert(names.contains("linkJs"))
      assert(!names.contains("moduleKind"))
      assert(!names.contains("linkerConfig"))
      assert(!names.contains("compileOrder"))

      val plan = command(project, "plan", "-m", "frontend", "-t", "linkJs", "--format", "dot").out.text()
      assert(!plan.contains("frontend.moduleKind"))
      assert(!plan.contains("frontend.linkerConfig"))
      val line = "deder exec -t moduleK"
      val completions = command(project, "complete", "--shell", "bash", "--command-line", line, "--cursor-pos", line.length.toString)
        .out.text().linesIterator.toSet
      assert(!completions.contains("moduleKind"))

      // Hidden tasks remain executable for troubleshooting.
      val kind = command(project, "exec", "-m", "frontend", "-t", "moduleKind", "--format", "json")
        .out.text().parseJson[MultiModuleResults[String]]
      assertEquals(kind.results("frontend"), "ES_MODULE")
    }
  }

  private def withJavaProject(body: os.Path => Unit): Unit =
    withTestProject("sample-projects/multi") { project =>
      os.write.over(project / "deder.pkl", """amends "../../config/DederProject.pkl"
        |modules {
        |  new JavaModule {
        |    id = "app"
        |    publish = false
        |    mainClass = "app.Main"
        |    compileOrder = "java-then-scala"
        |    // shading
        |  }
        |}
        |""".stripMargin)
      os.write(project / "app/src/Main.java",
        """package app;
          |public class Main {
          |  public static void main(String[] args) { System.out.println("hello"); }
          |}
          |""".stripMargin, createFolders = true)
      body(project)
    }

  private def metadata(project: os.Path, module: String, task: String): CacheMetadata =
    os.read(project / ".deder/out" / module / task / "metadata.json").parseJson[CacheMetadata]

  private def editConfig(project: os.Path, before: String, after: String): Unit = {
    val config = project / "deder.pkl"
    val original = os.read(config)
    assert(original.contains(before), s"Missing configuration text: $before")
    os.write.over(config, original.replace(before, after))
    // Restart so cache assertions do not depend on watcher/reload timing.
    command(project, "shutdown")
  }

  private def build(project: os.Path, module: String, task: String): Unit = {
    command(project, "exec", "-m", module, "-t", task, "--log-level", "warning", "--no-color")
    ()
  }

  private def command(project: os.Path, args: String*): os.CommandResult = {
    val result = executeDederCommand(project, args*)
    assertEquals(result.exitCode, 0, result.out.text() + result.err.text())
    result
  }

  private def assertAssemblyMain(project: os.Path, mainClass: String): Unit = {
    val jar = project / ".deder/out/app/assembly/out.jar"
    val classEntry = mainClass.replace('.', '/') + ".class"
    Using.resource(new ZipFile(jar.toIO)) { zip =>
      val entries = zip.entries().asScala.map(_.getName).toSet
      assert(entries.contains(classEntry), s"Missing $classEntry")
      val appClasses = entries.filter(_.endsWith("app/Main.class"))
      assertEquals(appClasses, Set(classEntry))
    }
    val result = os.proc("java", "-cp", jar, mainClass).call(cwd = project, stderr = os.Pipe)
    assertEquals(result.out.text().trim, "hello")
  }
}
