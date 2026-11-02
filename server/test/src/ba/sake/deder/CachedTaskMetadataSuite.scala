package ba.sake.deder

import java.nio.file.FileSystemException
import java.time.Instant
import ba.sake.tupson.{*, given}
import ba.sake.deder.config.ConfigParser
import ba.sake.deder.deps.DependencyResolver

class CachedTaskMetadataSuite extends munit.FunSuite {

  private lazy val project = ConfigParser(writeJson = false)
    .parse(os.pwd / "server/test/resources/sample-projects/multi/deder.pkl")
    .fold(error => fail(error), identity)
  private val input = TaskBuilder.make[String]("input").build(_ => "input")
  private val inputResult = TaskResult("input", "", "input-hash")
  private val notifications = ServerNotificationsLogger(_ => ())
  private val resolver = DependencyResolver(Seq.empty, CacheStatsRegistry())

  private def withProject(testCode: os.Path => Unit): Unit = {
    val root = os.temp.dir()
    try ProjectContext.run(ProjectContext(root)) { testCode(root) }
    finally os.remove.all(root)
  }

  private def execute[T, Deps <: Tuple, S](
      task: Task[T, Deps, S],
      dependencies: Seq[TaskResult[?]] = Seq(inputResult)
  ): (TaskResult[T], Boolean, Boolean) =
    task.executeUnsafe(
      project,
      project.modules.get(0),
      dependencies,
      Seq.empty,
      Seq.empty,
      Seq.empty,
      false,
      notifications,
      resolver
    )

  private def metadata(root: os.Path, task: String = "producer"): os.Path =
    root / ".deder/out" / project.modules.get(0).id / task / "metadata.json"

  test("default and explicit zero versions reuse legacy metadata without rewriting it") {
    withProject { root =>
      val legacyInputsHash = HashUtils.hashStr("""{"args":[],"depOutputHashes":["input-hash"]}""")
      val legacyResult = TaskResult("legacy", legacyInputsHash, "legacy-output", Instant.EPOCH)
      val storedJson = legacyResult.toJson(spaces = 2, sort = true)
      os.write(metadata(root), storedJson, createFolders = true)
      val builder = CachedTaskBuilder.make[String]("producer").dependsOn(input)
      val defaultTask = builder.build(_ => fail("Legacy entry should be reused"))
      val zeroTask = builder.withCacheVersion(0).build(_ => fail("Version zero should reuse legacy entry"))

      assertEquals(execute(defaultTask), (legacyResult, false, true))
      assertEquals(execute(zeroTask), (legacyResult, false, true))
      assertEquals(os.read(metadata(root)), storedJson)
    }
  }

  test("version survives dependency and replay builders and invalidates only when changed") {
    withProject { root =>
      var executions = 0
      def producer(version: Int) = CachedTaskBuilder
        .make[String]("producer")
        .withCacheVersion(version)
        .dependsOn(input)
        .withCliReplay((_, _, _) => ())
        .build { ctx =>
          executions += 1
          ctx.inputsHash
        }

      val first = execute(producer(1))
      assertEquals(first._3, false)
      assertEquals(execute(producer(1)), (first._1, false, true))
      val bumped = execute(producer(2))
      assertEquals(bumped._3, false)
      assertNotEquals(bumped._1.inputsHash, first._1.inputsHash)
      assertEquals(execute(producer(2)), (bumped._1, false, true))
      assertEquals(executions, 2)
      assertEquals(os.read(metadata(root)).parseJson[TaskResult[String]], bumped._1)
    }
  }

  test("summarized builder preserves the cache version") {
    withProject { _ =>
      def producer(version: Int) = CachedTaskBuilder
        .make[String]("producer")
        .dependsOn(input)
        .withCacheVersion(version)
        .buildSummarized[MultiModuleResults[String]](_.inputsHash)

      val first = execute(producer(1))
      assertEquals(execute(producer(1))._3, true)
      val bumped = execute(producer(2))
      assertEquals(bumped._3, false)
      assertNotEquals(bumped._1.inputsHash, first._1.inputsHash)
    }
  }

  test("producer version changes propagate through compile output identity to downstream cache") {
    withProject { _ =>
      def producer(version: Int) = CachedTaskBuilder
        .make[CompileResult]("producer")
        .withCacheVersion(version)
        .dependsOn(input)
        .build { ctx => CompileResult(DederPath(ctx.out / "classes"), 0, 0, 1, inputsHash = ctx.inputsHash) }
      var consumerExecutions = 0
      val consumer = CachedTaskBuilder
        .make[String]("consumer")
        .dependsOn(producer(1))
        .build { _ =>
          consumerExecutions += 1
          "packaged"
        }

      val first = execute(producer(1))._1
      assertEquals(execute(consumer, Seq(first))._3, false)
      assertEquals(execute(consumer, Seq(first))._3, true)
      val bumped = execute(producer(2))._1
      assertNotEquals(bumped.outputHash, first.outputHash)
      assertEquals(execute(consumer, Seq(bumped))._3, false)
      assertEquals(execute(consumer, Seq(bumped))._3, true)
      assertEquals(consumerExecutions, 2)
    }
  }

  test("interrupted rebuild removes old metadata before modifying outputs and recovers") {
    withProject { root =>
      var executions = 0
      val original = CachedTaskBuilder.make[String]("producer").dependsOn(input).build { ctx =>
        executions += 1
        os.write.over(ctx.out / "artifact.txt", "complete", createFolders = true)
        "complete"
      }
      execute(original)
      assert(os.exists(metadata(root)))
      val interrupted = CachedTaskBuilder
        .make[String]("producer")
        .withCacheVersion(1)
        .dependsOn(input)
        .build { ctx =>
          assert(!os.exists(metadata(root)), "Old metadata must be gone before execution")
          os.write.over(ctx.out / "artifact.txt", "partial")
          throw new InterruptedException("Interrupted after modifying the artifact")
        }

      // MUnit's intercept deliberately rethrows InterruptedException; catch this simulated cancellation directly.
      val interruption =
        try {
          execute(interrupted)
          fail("Expected interrupted execution")
        } catch {
          case error: InterruptedException => error
        }
      assertEquals(interruption.getMessage, "Interrupted after modifying the artifact")
      assert(!os.exists(metadata(root)))
      assertEquals(os.read(metadata(root) / os.up / "artifact.txt"), "partial")
      // Returning to the original inputs must rebuild, rather than authorize the partial artifact.
      assertEquals(execute(original)._3, false)
      assertEquals(os.read(metadata(root) / os.up / "artifact.txt"), "complete")
      assertEquals(execute(original)._3, true)
      assertEquals(executions, 2)
    }
  }

  test("task JSON errors propagate once instead of being mistaken for corrupt metadata") {
    withProject { root =>
      val original = CachedTaskBuilder.make[String]("producer").dependsOn(input).build(_ => "complete")
      execute(original)
      var attempts = 0
      val broken = CachedTaskBuilder.make[String]("producer").dependsOn(input).withCacheVersion(1).build { _ =>
        attempts += 1
        "{".parseJson[String]
      }

      intercept[TupsonException](execute(broken))
      assertEquals(attempts, 1)
      assert(!os.exists(metadata(root)))
    }
  }

  test("failed metadata publication removes its temporary file and permits recovery") {
    withProject { root =>
      val destination = metadata(root)
      // A directory at the destination makes the actual filesystem atomic move fail.
      val producer = CachedTaskBuilder.make[String]("producer").dependsOn(input).build { _ =>
        os.makeDir.all(destination)
        "complete"
      }
      intercept[FileSystemException](execute(producer))
      assert(os.isDir(destination))
      assertEquals(os.list(destination / os.up).map(_.last), Seq("metadata.json"))
      os.remove(destination)

      val recovered = CachedTaskBuilder.make[String]("producer").dependsOn(input).build(_ => "complete")
      val result = execute(recovered)
      assertEquals(result._3, false)
      assertEquals(os.read(destination).parseJson[TaskResult[String]], result._1)
      assertEquals(execute(recovered)._3, true)
      assertEquals(os.list(destination / os.up).map(_.last), Seq("metadata.json"))
    }
  }

  test("metadata publication replaces the destination rather than modifying its existing file") {
    withProject { root =>
      val destination = metadata(root)
      val originalJson = TaskResult("original", "old-inputs", "old-output").toJson
      os.write(destination, originalJson, createFolders = true)
      val previous = destination / os.up / "previous.json"
      java.nio.file.Files.createLink(previous.toNIO, destination.toNIO)
      val replacement = TaskResult("replacement", "new-inputs", "new-output")

      CacheMetadata.write(destination, replacement.toJson)

      assertEquals(os.read(destination).parseJson[TaskResult[String]], replacement)
      assertEquals(os.read(previous), originalJson, "Existing readers must retain the old complete file")
      assertEquals(os.list(destination / os.up).map(_.last).toSet, Set("metadata.json", "previous.json"))
    }
  }
}
