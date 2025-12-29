package ba.sake.deder

import scala.jdk.CollectionConverters.*
import scala.util.Properties
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import ba.sake.tupson.*
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.atomic.AtomicReference

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

  test("deder should compile multimodule project") {
    withTestProject(testResourceDir / "sample-projects/multi") { projectPath =>
      locally {
        // default command is compile
        // and the logs go to stderr!
        val dederOutput = executeDederCommand(projectPath, "exec").err.text()
        assert(dederOutput.contains("Executing compile on module(s): backend, common, frontend, uber, uber-test"))
        val compilingCount = dederOutput.linesIterator.count(_.matches(".*compiling .* source to .*"))
        assertEquals(compilingCount, 5)
      }
      locally {
        val dederOutput = executeDederCommand(projectPath, "exec").err.text()
        assert(dederOutput.contains("Executing compile on module(s): backend, common, frontend, uber, uber-test"))
        val compilingCount = dederOutput.linesIterator.count(_.matches(".*compiling .* source to .*"))
        assertEquals(compilingCount, 0) // all compiled already
      }
      locally {
        os.write.append(projectPath / "common/src/Common.scala", "\n// some change to trigger recompilation\n")
        val dederOutput = executeDederCommand(projectPath, "exec").err.text()
        assert(dederOutput.contains("Executing compile on module(s): backend, common, frontend, uber, uber-test"))
        val compilingCount = dederOutput.linesIterator.count(_.matches(".*compiling .* source to .*"))
        assertEquals(compilingCount, 1)
      }
    }
  }

  test("deder should run multimodule project") {
    withTestProject(testResourceDir / "sample-projects/multi") { projectPath =>
      locally {
        val dederOutput = executeDederCommand(projectPath, "exec -t run -m uber arg1 arg2 arg3").out.text()
        assert(dederOutput.contains("Hello from uber module!"))
        assert(dederOutput.contains("Args = arg1, arg2, arg3"))
      }
      locally {
        // concurrent runs, non-blocking, client side
        val startTime = System.currentTimeMillis()
        val totalRuns = 10
        val results = new AtomicReference[Map[Int, String]](Map.empty)
        val threads = (1 to totalRuns).map { i =>
          new Thread(() => {
            val output = executeDederCommand(projectPath, s"exec -t run -m uber arg$i").out.text()
            results.updateAndGet(_ + (i -> output))
            ()
          })
        }
        threads.foreach(_.start())
        threads.foreach(_.join())
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        println(s"Running ${totalRuns} subprocesses took $duration ms")
        (1 to totalRuns).map { i =>
          val output = results.get()(i)
          assert(output.contains("Hello from uber module!"), s"Run #$i did not produce expected output")
          assert(output.contains(s"Args = arg$i"), s"Run #$i did not receive correct argument")
        }
        assert(duration < 5000, s"Expected concurrent execution to be under 5 seconds, but took $duration ms")
      }
    }
  }

  test("deder should write a BSP config file") {
    withTestProject(testResourceDir / "sample-projects/multi") { projectPath =>
      val bspConfigPath = projectPath / ".bsp/deder-bsp.json"
      assert(!os.exists(bspConfigPath))
      executeDederCommand(projectPath, "bsp install")
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
      // os.remove.all(tempDir)
    }
  }

  private def executeDederCommand(projectPath: os.Path, command: String): os.CommandResult = {
    val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
    val cmd = shell ++ Seq(s"$dederClientPath $command")
    os.proc(cmd).call(cwd = projectPath, stderr = os.Pipe)
  }
}
