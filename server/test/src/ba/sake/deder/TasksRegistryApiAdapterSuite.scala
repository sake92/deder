package ba.sake.deder

import ba.sake.deder.config.DederProject.ModuleType

class TasksRegistryApiAdapterSuite extends munit.FunSuite {

  test("allTasks returns all registered tasks") {
    val task1 = TaskBuilder.make[String]("compile",
      supportedModuleTypes = Set[ModuleType](ModuleType.SCALA))
      .build { _ => "ok" }
    val task2 = TaskBuilder.make[String]("test")
      .build { _ => "ok" }
    val registry = TasksRegistry(Seq(task1, task2))
    val api = TasksRegistryApiAdapter(registry)

    val all = api.allTasks
    assertEquals(all.size, 2)
    assertEquals(all.map(_.name).toSet, Set("compile", "test"))
  }

  test("tasksFor filters by module type") {
    val scalaTask = TaskBuilder.make[String]("compile",
      supportedModuleTypes = Set[ModuleType](ModuleType.SCALA))
      .build { _ => "ok" }
    val javaTask = TaskBuilder.make[String]("javac",
      supportedModuleTypes = Set[ModuleType](ModuleType.JAVA))
      .build { _ => "ok" }
    val universalTask = TaskBuilder.make[String]("clean",
      supportedModuleTypes = Set.empty[ModuleType])
      .build { _ => "ok" }
    val registry = TasksRegistry(Seq(scalaTask, javaTask, universalTask))
    val api = TasksRegistryApiAdapter(registry)

    val scalaTasks = api.tasksFor(ModuleType.SCALA)
    assertEquals(scalaTasks.map(_.name).toSet, Set("compile", "clean"))

    val javaTasks = api.tasksFor(ModuleType.JAVA)
    assertEquals(javaTasks.map(_.name).toSet, Set("javac", "clean"))
  }

  test("TaskInfo has correct metadata") {
    val task = TaskBuilder.make[String]("myTask",
      category = "test-cat",
      kind = TaskKind.SourceGenerator,
      supportedModuleTypes = Set[ModuleType](ModuleType.SCALA),
      transitive = true,
      singleton = false,
      internal = false)
      .build { _ => "ok" }
    val registry = TasksRegistry(Seq(task))
    val api = TasksRegistryApiAdapter(registry)

    val info = api.allTasks.head
    assertEquals(info.name, "myTask")
    assertEquals(info.category, "test-cat")
    assertEquals(info.kind, TaskKind.SourceGenerator)
    assertEquals(info.supportedModuleTypes, Seq(ModuleType.SCALA))
    assertEquals(info.transitive, true)
    assertEquals(info.singleton, false)
    assertEquals(info.internal, false)
    assertEquals(info.featureTags, Seq.empty) // not a CachedTask/SourceFileTask/etc, no auto-tags
  }
}
