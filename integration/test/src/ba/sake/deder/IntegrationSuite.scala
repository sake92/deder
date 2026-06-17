package ba.sake.deder

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.*
import scala.util.Properties
import ba.sake.tupson.*

class IntegrationSuite extends BaseIntegrationSuite {

  test("deder should work with multimodule project") {
    withTestProject("sample-projects/multi") { projectPath =>
      // deder version
      locally {
        val dederRes = executeDederCommand(projectPath, "version")
        val dederOutput = dederRes.out.text()
        assert(dederOutput.contains("Client version: "))
        assert(dederOutput.contains("Server version: "))
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
        val dederRes = executeDederCommand(projectPath, "modules", "--format", "json")
        val dederOutput = dederRes.out.text()
        List("common", "frontend", "backend", "uber", "uber-test").foreach { moduleId =>
          assert(dederOutput.contains(moduleId), s"Module '$moduleId' not found in 'deder modules --format json' output")
        }
      }
      locally {
        val dederRes = executeDederCommand(projectPath, "modules", "--format", "dot")
        val dederOutput = dederRes.out.text()
        List("common", "frontend", "backend", "uber", "uber-test").foreach { moduleId =>
          assert(dederOutput.contains(moduleId), s"Module '$moduleId' not found in 'deder modules --format dot' output")
        }
      }
      // deder tasks
      locally {
        val dederRes = executeDederCommand(projectPath, "tasks", "-m", "common")
        val dederOutput = dederRes.out.text()
        List("sources", "compile", "run").foreach { taskName =>
          assert(dederOutput.contains(taskName), s"Task '$taskName' not found in 'deder tasks -m common' output")
        }
        // verify feature tags and legend appear
        assert(dederOutput.contains("⚡"), "output should contain cached emoji")
        assert(dederOutput.contains("📁"), "output should contain source-aware emoji")
        assert(dederOutput.contains("⚡ = cached"), "legend should include cached description")
      }
      locally {
        val dederRes = executeDederCommand(projectPath, "tasks", "-m", "uber-test")
        val dederOutput = dederRes.out.text()
        List("sources", "compile", "test").foreach { taskName =>
          assert(dederOutput.contains(taskName), s"Task '$taskName' not found in 'deder tasks -m uber-test' output")
        }
      }
      // deder tasks --format json
      locally {
        val dederRes = executeDederCommand(projectPath, "tasks", "-m", "common", "--format", "json")
        val dederOutput = dederRes.out.text()
        assert(dederOutput.contains("\"name\":"), "JSON tasks output should contain name field")
        assert(dederOutput.contains("\"features\":"), "JSON tasks output should contain features array")
        assert(dederOutput.contains("\"source-aware\""), "at least one task should have source-aware feature")
        assert(dederOutput.contains("\"cached\""), "at least one task should have cached feature")
      }
      // deder tasks --format densejson
      locally {
        val dederRes = executeDederCommand(projectPath, "tasks", "-m", "common", "--format", "densejson")
        val dederOutput = dederRes.out.text()
        assert(dederOutput.contains("\"name\":"), "DenseJson tasks output should contain name field")
        assert(dederOutput.contains("\"features\":"), "DenseJson tasks output should contain features array")
      }
      
    }
  }

  // default command is compile
  // and the logs go to stderr!
  test("deder should compile multimodule project") {
    withTestProject("sample-projects/multi") { projectPath =>
      locally {
        val dederOutputJson = executeDederCommand(projectPath, "exec", "-m", "uber", "-t", "compileClasspath", "--format", "json").out.text()
        val dederOutput = dederOutputJson.parseJson[MultiModuleResults[Seq[String]]]
        val uberCompileClasspath = dederOutput.results("uber")
        assert(uberCompileClasspath(0).endsWith("/.deder/out/uber/compile/classes"))
        assert(uberCompileClasspath(1).endsWith("/.deder/out/backend/compile/classes"))
        assert(uberCompileClasspath(2).endsWith("/.deder/out/frontend/compile/classes"))
        assert(uberCompileClasspath(3).endsWith("/.deder/out/common/compile/classes"))
        assert(uberCompileClasspath.exists(_.contains("scala3-library_3-3.7.1.jar")))
        assert(uberCompileClasspath.exists(_.contains("scala-library-2.13.16.jar")))
      }
      locally {
        val dederOutput = executeDederCommand(projectPath, "exec").err.text()
        assert(dederOutput.contains("Executing 'compile' task on modules: backend, common, frontend, uber, uber-test"))
        val compilingCount = dederOutput.linesIterator.count(_.matches(".*compiling .* source to .*"))
        assertEquals(compilingCount, 5)
      }
      locally {
        val dederOutput = executeDederCommand(projectPath, "exec").err.text()
        assert(dederOutput.contains("Executing 'compile' task on modules: backend, common, frontend, uber, uber-test"))
        val compilingCount = dederOutput.linesIterator.count(_.matches(".*compiling .* source to .*"))
        assertEquals(compilingCount, 0) // all compiled already
      }
      locally {
        os.write.append(projectPath / "common/src/Common.scala", "\n// some change to trigger recompilation\n")
        val dederOutput = executeDederCommand(projectPath, "exec").err.text()
        assert(dederOutput.contains("Executing 'compile' task on modules: backend, common, frontend, uber, uber-test"))
        val compilingCount = dederOutput.linesIterator.count(_.matches(".*compiling .* source to .*"))
        assertEquals(compilingCount, 1)
      }
    }
  }

  test("deder should run multimodule project") {
    withTestProject("sample-projects/multi") { projectPath =>
      locally {
        val dederOutput = executeDederCommand(projectPath, "exec", "-t", "run", "-m", "uber", "arg1", "arg2", "arg3").out.text()
        assert(dederOutput.contains("Args = arg1, arg2, arg3"))
      }
      locally {
        // concurrent runs, non-blocking, client side
        val startTime = System.currentTimeMillis()
        val totalRuns = 5
        val results = new AtomicReference[Map[Int, String]](Map.empty)
        val threads = (1 to totalRuns).map { i =>
          new Thread(() => {
            val output = executeDederCommand(projectPath, "exec", "-t", "run", "-m", "uber", s"arg$i").out.text()
            results.updateAndGet(_ + (i -> output))
            ()
          })
        }
        threads.foreach(_.start())
        threads.foreach(_.join())
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        (1 to totalRuns).foreach { i =>
          val output = results.get()(i)
          assert(output.contains(s"Args = arg$i"), s"Run #$i did not receive correct argument")
        }
        val maxExpectedDurationMs = 45_000 // Full integration runs are noisy; keep the concurrency check generous.
        assert(
          duration < maxExpectedDurationMs,
          s"Expected concurrent execution to be under ${maxExpectedDurationMs}ms, but took $duration ms"
        )
      }
    }
  }

  test("deder should run tests in multimodule/uber-test") {
    withTestProject("sample-projects/multi") { projectPath =>
      locally {
        val dederOutput = executeDederCommand(projectPath, "exec", "-m", "uber-test", "-t", "test").err.text()
        // println(s"Test output:\n$dederOutput")
        // assert(resText.contains("Args = argA, argB, argC"))
      }
    }
  }

  test("deder should assembly multimodule/uber and run it") {
    withTestProject("sample-projects/multi") { projectPath =>
      locally {
        executeDederCommand(projectPath, "exec", "-m", "uber", "-t", "assembly")
        val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
        val command = s"java -cp ${projectPath / ".deder/out/uber/assembly/out.jar"} uber.Main argA argB argC"
        val cmd = shell ++ Seq(command)
        val res = os.proc(cmd).call(cwd = projectPath, stderr = os.Pipe)
        val resText = res.out.text()
        assert(resText.contains("Args = argA, argB, argC"))
      }
    }
  }

  test("deder should write a BSP config file") {
    withTestProject("sample-projects/multi") { projectPath =>
      val bspConfigPath = projectPath / ".bsp/deder-bsp.json"
      assert(!os.exists(bspConfigPath))
      executeDederCommand(projectPath, "bsp", "install")
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
      assert(bspConfigJson.name == "deder-bsp", s"Expected 'name' to be 'deder-bsp', but got: ${bspConfigJson.name}")
      // assert(bspConfigJson.version == "0.0.1")
      assert(
        bspConfigJson.bspVersion == "2.2.0-M2",
        s"Expected 'bspVersion' to be '2.2.0-M2', but got: ${bspConfigJson.bspVersion}"
      )
      assert(
        bspConfigJson.argv.last == "bsp",
        s"Expected last element of 'argv' to be 'bsp', but got: ${bspConfigJson.argv.last}"
      )
      val languages = bspConfigJson.languages
      assert(languages.contains("java"), s"Expected 'languages' to contain 'java', but got: ${bspConfigJson.languages}")
      assert(
        languages.contains("scala"),
        s"Expected 'languages' to contain 'scala', but got: ${bspConfigJson.languages}"
      )
    }
  }

  test("deder exec should support negation in module filters") {
    withTestProject("sample-projects/multi") { projectPath =>
      // exec with -m common -m ~nonexistent: select common, negation of nonexistent has no effect
      val dederRes = executeDederCommand(projectPath, "exec", "-m", "common", "-m", "~nonexistent", "-t", "compileClasspath", "--format", "json")
      assert(dederRes.exitCode == 0, s"exec failed with exit=${dederRes.exitCode}: ${dederRes.err.text()}")
    }
  }

  test("deder plan should support negation in module filters") {
    withTestProject("sample-projects/multi") { projectPath =>
      // plan with -m uber% -m ~uber-test: exclude uber-test from uber% match
      val dederRes = executeDederCommand(projectPath, "plan", "-m", "uber%", "-m", "~uber-test", "-t", "compile")
      val dederOutput = dederRes.out.text()
      assert(dederOutput.contains("uber.compile"), s"uber.compile not found in output: ${dederOutput}")
      assert(!dederOutput.contains("uber-test.compile"), s"uber-test.compile should have been excluded: ${dederOutput}")
    }
  }

  test("deder clean should support negation in task filter") {
    withTestProject("sample-projects/multi") { projectPath =>
      // clean with negated task filter should not crash
      val dederRes = executeDederCommand(projectPath, "clean", "-m", "common", "-t", "~nonExistentTask")
      val dederOutput = dederRes.out.text()
      assert(dederRes.exitCode == 0, s"clean failed: ${dederOutput}")
    }
  }

  test("deder tool listing and error handling") {
    withTestProject("sample-projects/multi") { projectPath =>
      locally {
        val dederRes = executeDederCommand(projectPath, "tool", "nonexistent")
        val dederErr = dederRes.err.text()
        assert(dederErr.contains("not found"), s"Expected 'not found' error but got: $dederErr")
        assert(dederRes.exitCode != 0)
      }
    }
  }

  test("deder should skip downstream modules when upstream compile fails") {
    withTestProject("sample-projects/multi") { projectPath =>
      val commonFile = projectPath / "common/src/Common.scala"
      val original = os.read(commonFile)
      val broken = original.replace(
        """  val value = "komon1"""",
        """  val value = "komon1"
            |  val x: String = 42 // intentional compile error""".stripMargin
      )
      try {
        os.write.over(commonFile, broken)
        val dederRes = executeDederCommand(projectPath, "exec")
        val stdout = dederRes.out.text()

        // The CompilationSummary (with FAIL/SKIPPED/COMPILED) is an Output message → stdout
        assert(stdout.contains("FAIL common"), s"Expected 'FAIL common' in stdout, got:\n$stdout")
        assert(!stdout.contains("SKIPPED common"),
          s"Expected common to FAIL (not be SKIPPED) — it is the module with the compile error.\nstdout:\n$stdout")

        val downstreamModules = Seq("backend", "frontend", "uber", "uber-test")
        downstreamModules.foreach { mod =>
          assert(!stdout.contains(s"COMPILED $mod"),
            s"Expected '$mod' to be SKIPPED, not COMPILED, because upstream common failed.\nstdout:\n$stdout")
          assert(stdout.contains(s"SKIPPED $mod"),
            s"Expected '$mod' to appear as SKIPPED in stdout.\nstdout:\n$stdout")
        }

        assert(dederRes.exitCode != 0, s"Expected non-zero exit code when compile fails, got ${dederRes.exitCode}")
      } finally {
        os.write.over(commonFile, original)
      }
    }
  }
}
