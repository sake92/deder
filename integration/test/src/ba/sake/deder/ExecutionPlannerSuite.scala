package ba.sake.deder

import scala.jdk.CollectionConverters.*
import scala.util.Properties
import ba.sake.tupson.*

class IntegrationSuite extends munit.FunSuite {

  private val testResourceDir = os.Path(System.getenv("MILL_TEST_RESOURCE_DIR"))
  private val dederClientPath = System.getenv("DEDER_CLIENT_PATH")
  private val dederServerPath = System.getenv("DEDER_SERVER_PATH")

  test("deder should work with multimodule project") {
    withTestProject(testResourceDir / "sample-projects/multi") { projectPath =>
      // deder version
      locally {
        val dederRes = executeDederCommand(projectPath, "version")
        val dederOutput = dederRes.out.text()
        assert(dederOutput.contains("Client version: 0.0.1"))
        assert(dederOutput.contains("Server version: 0.0.1"))
      }
      // deder modules
      locally {
        val dederRes = executeDederCommand(projectPath, "modules")
        val dederOutput = dederRes.out.text()
        List("common", "frontend", "backend", "uber", "uber-test").foreach { moduleId =>
          assert(dederOutput.contains(moduleId), s"Module '$moduleId' not found in 'deder modules' output")
        }
      }
      locally {
        val dederRes = executeDederCommand(projectPath, "modules --json")
        val dederOutput = dederRes.out.text()
        List("common", "frontend", "backend", "uber", "uber-test").foreach { moduleId =>
          assert(dederOutput.contains(moduleId), s"Module '$moduleId' not found in 'deder modules --json' output")
        }
      }
      locally {
        val dederRes = executeDederCommand(projectPath, "modules --ascii")
        val dederOutput = dederRes.out.text()
        List("common", "frontend", "backend", "uber", "uber-test").foreach { moduleId =>
          assert(dederOutput.contains(moduleId), s"Module '$moduleId' not found in 'deder modules --ascii' output")
        }
      }
      locally {
        val dederRes = executeDederCommand(projectPath, "modules --dot")
        val dederOutput = dederRes.out.text()
        List("common", "frontend", "backend", "uber", "uber-test").foreach { moduleId =>
          assert(dederOutput.contains(moduleId), s"Module '$moduleId' not found in 'deder modules --dot' output")
        }
      }
      // deder tasks
      locally {
        val dederRes = executeDederCommand(projectPath, "tasks -m common")
        val dederOutput = dederRes.out.text()
        List("sources", "compile", "run").foreach { taskName =>
          assert(dederOutput.contains(taskName), s"Task '$taskName' not found in 'deder tasks -m common' output")
        }
      }
      locally {
        val dederRes = executeDederCommand(projectPath, "tasks -m uber-test")
        val dederOutput = dederRes.out.text()
        List("sources", "compile", "test").foreach { taskName =>
          assert(dederOutput.contains(taskName), s"Task '$taskName' not found in 'deder tasks -m uber-test' output")
        }
      }
      // deder plan
      locally {
        val dederRes = executeDederCommand(projectPath, "plan -m common -t compile")
        val dederOutput = dederRes.out.text()
        println(dederOutput)
        assertEquals(
          dederOutput,
          """|Stage #0:
             |  common.classes
             |  common.deps
             |  common.generatedSources
             |  common.javaSemanticdbVersion
             |  common.javacAnnotationProcessorDeps
             |  common.javacOptions
             |  common.scalaSemanticdbVersion
             |  common.scalaVersion
             |  common.scalacOptions
             |  common.scalacPluginDeps
             |  common.semanticdbEnabled
             |  common.sources
             |Stage #1:
             |  common.allClassesDirs
             |  common.dependencies
             |  common.javacAnnotationProcessors
             |  common.scalacPlugins
             |Stage #2:
             |  common.allDependencies
             |Stage #3:
             |  common.compileClasspath
             |Stage #4:
             |  common.compile
             |""".stripMargin
        )
      }
    }
  }

  test("deder should write a BSP config file") {
    withTestProject(testResourceDir / "sample-projects/multi") { projectPath =>
      val bspConfigPath = projectPath / ".bsp/deder-bsp.json"
      assert(!os.exists(bspConfigPath))
      val dederRes = executeDederCommand(projectPath, "bsp install")
      assert(os.exists(bspConfigPath))
      case class BspConfig(
          name: String,
          version: String,
          bspVersion: String,
          argv: List[String],
          languages: List[String]
      ) derives JsonRW
      val bspConfig = os.read(bspConfigPath)
      val bspConfigJson = bspConfig.parseJson[BspConfig]
      assert(bspConfigJson.name == "deder-bsp")
      assert(bspConfigJson.version == "0.0.1")
      assert(bspConfigJson.bspVersion == "2.2.0-M2")
      assert(bspConfigJson.argv.exists(_.contains("deder")))
      assert(bspConfigJson.argv.last == "bsp")
      val languages = bspConfigJson.languages
      assert(languages.contains("java"))
      assert(languages.contains("scala"))
    }
  }

  private def withTestProject(testProjectPath: os.Path)(testCode: os.Path => Unit): Unit = {
    // mill test runs in sandbox folder, so it is safe to create temp folders here
    val tempDir = os.pwd / testProjectPath.last / s"temp-${System.currentTimeMillis()}"
    try {
      os.copy(testProjectPath, tempDir, createFolders = true, replaceExisting = true)
      os.write.over(tempDir / ".deder/server.properties", s"localPath=$dederServerPath\n", createFolders = true)
      testCode(tempDir)
    } finally {
      executeDederCommand(tempDir, "shutdown")
      //os.remove.all(tempDir)
    }
  }

  private def executeDederCommand(projectPath: os.Path, command: String): os.CommandResult = {
    val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
    val cmd = shell ++ Seq(s"$dederClientPath $command")
    os.proc(cmd).call(cwd = projectPath)
  }
}
