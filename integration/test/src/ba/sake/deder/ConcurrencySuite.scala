package ba.sake.deder

import scala.util.Random
import ba.sake.deder.config.DederProject.ModuleType

import java.util.UUID

class ConcurrencySuite extends munit.FunSuite {
  val testResourceDir = os.Path(System.getenv("MILL_TEST_RESOURCE_DIR"))

  test("executeTask should guard against concurrent executions of the same task") {
    withTestProject(testResourceDir / "sample-projects/multi") { projectPath =>
      val coreTasks = CoreTasks()
      val tasksRegistry = TasksRegistry(coreTasks)
      var globalVar = 0
      val task1 = TaskBuilder
        .make[String](name = "task1", supportedModuleTypes = Set(ModuleType.SCALA))
        .dependsOn(coreTasks.compileTask)
        .build { _ =>
          Thread.sleep(Random.nextInt(10))
          globalVar += 1
          ""
        }
      tasksRegistry.add(task1)
      val dederExecutorService = java.util.concurrent.Executors.newFixedThreadPool(8)
      val state = DederProjectState(tasksRegistry, Int.MaxValue, dederExecutorService, () => ())
      val serverNotificationsLogger = new ServerNotificationsLogger(_ => ())
      // simulate clients calling "task1" concurrently
      val clientsCount = 10
      val clientExecutorService = java.util.concurrent.Executors.newFixedThreadPool(32)
      val clientFutures = (1 to clientsCount).map { _ =>
        clientExecutorService.submit(() => {
          val requestId = UUID.randomUUID().toString
          state.executeTasks(requestId, Seq("common"), "task1", Seq.empty, false, serverNotificationsLogger, false)
        })
      }
      clientFutures.foreach(_.get()) // wait for all clients to finish
      clientExecutorService.shutdown()
      dederExecutorService.shutdown()
      assertEquals(globalVar, clientsCount)
    }
  }

  test("executeTask should serialize locks by task instance id") {
    withTestProject(testResourceDir / "sample-projects/multi") { projectPath =>
      val coreTasks = CoreTasks()
      val tasksRegistry = TasksRegistry(coreTasks)
      var globalVar = 0
      val task1 = TaskBuilder
        .make[String](name = "task1", supportedModuleTypes = Set(ModuleType.SCALA))
        .dependsOn(coreTasks.compileTask)
        .build { _ =>
          Thread.sleep(Random.nextInt(10))
          globalVar += 1
          ""
        }
      val task2 = TaskBuilder
        .make[String](name = "task2", supportedModuleTypes = Set(ModuleType.SCALA))
        .dependsOn(task1)
        .build { _ =>
          Thread.sleep(Random.nextInt(10))
          globalVar += 1
          ""
        }
      tasksRegistry.add(task1)
      tasksRegistry.add(task2)
      val dederExecutorService = java.util.concurrent.Executors.newFixedThreadPool(8)
      val state = DederProjectState(tasksRegistry, Int.MaxValue, dederExecutorService, () => ())
      val serverNotificationsLogger = new ServerNotificationsLogger(_ => ())
      // simulate clients calling random tasks concurrently
      val taskNames = Seq("compile", "sources", "javacOptions")
      val clientsCount = 100
      val clientExecutorService = java.util.concurrent.Executors.newFixedThreadPool(32)
      val clientFutures = (1 to clientsCount).map { i =>
        clientExecutorService.submit(() => {
          // half of the clients always call "task2", the other half random tasks
          val taskName = if i % 2 == 0 then "task2" else taskNames(Random.nextInt(taskNames.length))
          val requestId = UUID.randomUUID().toString
          state.executeTasks(requestId, Seq("common"), taskName, Seq.empty, false, serverNotificationsLogger, false)
        })
      }
      clientFutures.foreach(_.get()) // wait for all clients to finish
      clientExecutorService.shutdown()
      dederExecutorService.shutdown()
      // count gets incremented by task1+task2
      assertEquals(globalVar, clientsCount)
    }
  }

  // just for easier setup of test project, config and all
  private def withTestProject(testProjectPath: os.Path)(testCode: os.Path => Unit): Unit = {
    // mill test runs in sandbox folder, so it is safe to create temp folders here
    val tempDir = os.pwd / testProjectPath.last / s"temp-${System.currentTimeMillis()}"
    os.copy(testProjectPath, tempDir, createFolders = true, replaceExisting = true)
    System.setProperty("DEDER_PROJECT_ROOT_DIR", tempDir.toString)
    testCode(tempDir)
  }
}
