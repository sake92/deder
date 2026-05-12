package ba.sake.deder

import java.net.URLClassLoader
import java.util.concurrent.Executors
import scala.collection.mutable
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.ModuleType
import ba.sake.deder.deps.DependencyResolverApi
import ba.sake.deder.plugin.{PluginLoader, PluginLoaderApi}

class DederProjectStatePluginReloadSuite extends munit.FunSuite {

  private val TestWorkerThreads = 2
  private def testProjectConfig(baseConfigPath: os.Path) =
    s"""amends "${baseConfigPath.toString}"
       |
       |modules {
       |  new ScalaModule {
       |    id = "core"
       |    scalaVersion = "3.0.0"
       |  }
       |  new ScalaModule {
       |    id = "app"
       |    scalaVersion = "3.0.0"
       |  }
       |  new ScalaTestModule {
       |    id = "app-test"
       |    root = "app/test"
       |    scalaVersion = "3.0.0"
       |  }
       |}
       |""".stripMargin

  private class CloseTrackingClassLoader(val id: String) extends URLClassLoader(Array.empty, getClass.getClassLoader) {
    @volatile var wasClosed = false
    override def close(): Unit = {
      wasClosed = true
      super.close()
    }
    override def toString: String = s"CloseTrackingClassLoader($id)"
  }

  private case class FakeLoadedPlugin(
      pluginId: String,
      taskName: String,
      classLoader: CloseTrackingClassLoader
  )

  private class FakePluginLoader extends PluginLoaderApi {
    @volatile var fingerprintValue: String = "fp1"
    @volatile var loadCalls = 0
    private val loadResults = mutable.LinkedHashMap.empty[String, Either[String, Seq[FakeLoadedPlugin]]]

    def configureSuccess(fingerprint: String, plugins: FakeLoadedPlugin*): Unit =
      loadResults.update(fingerprint, Right(plugins.toSeq))

    def configureFailure(fingerprint: String, errorMessage: String = "Simulated plugin load failure"): Unit =
      loadResults.update(fingerprint, Left(errorMessage))

    override def extractPluginDeps(project: DederProject): Seq[(String, String)] = Seq.empty

    override def fingerprint(project: DederProject, pklFile: os.Path): Either[String, String] =
      Right(fingerprintValue)

    override def load(pklFile: os.Path): Either[String, PluginLoader.PluginLoadResult] = {
      loadCalls += 1
      loadResults.getOrElse(fingerprintValue, Left(s"No fake load result configured for $fingerprintValue")) match {
        case Left(error) => Left(error)
        case Right(plugins) =>
          Right(
            PluginLoader.PluginLoadResult(
              plugins.map { plugin =>
                val task = TaskBuilder
                  .make[String](plugin.taskName, supportedModuleTypes = Set(ModuleType.SCALA))
                  .build(_ => "ok")
                PluginLoader.LoadedPlugin(plugin.pluginId, Seq(task), plugin.classLoader)
              }
            )
          )
      }
    }
  }

  private def withTempProjectDir(f: os.Path => Unit): Unit = {
    val projectDir = os.temp.dir(prefix = "plugin-reload-suite-")
    os.write.over(projectDir / "deder.pkl", testProjectConfig(os.pwd / "config" / "DederProject.pkl"))
    os.makeDir.all(projectDir / "core")
    os.makeDir.all(projectDir / "app" / "test")
    val previousProjectRoot = Option(System.getProperty("DEDER_PROJECT_ROOT_DIR"))
    System.setProperty("DEDER_PROJECT_ROOT_DIR", projectDir.toString)
    try f(projectDir)
    finally {
      previousProjectRoot match {
        case Some(value) => System.setProperty("DEDER_PROJECT_ROOT_DIR", value)
        case None        => System.clearProperty("DEDER_PROJECT_ROOT_DIR")
      }
      if os.exists(projectDir) then os.remove.all(projectDir)
    }
  }

  private def withState(fakeLoader: FakePluginLoader)(f: DederProjectState => Unit): Unit =
    withTempProjectDir { projectDir =>
      val pool = Executors.newFixedThreadPool(TestWorkerThreads)
      val state = DederProjectState(
        TasksRegistry(CoreTasks().all),
        Int.MaxValue,
        pool,
        () => (),
        pluginLoaderFactory = (_: CoreTasksApi, _: DependencyResolverApi) => fakeLoader,
        configFilePath = projectDir / "deder.pkl"
      )
      try f(state)
      finally {
        state.shutdown()
        pool.shutdownNow()
      }
    }

  private def activePluginTaskNames(state: DederProjectState): Set[String] =
    state.readState(useLastGood = false) match {
      case Left(error) => fail(s"Expected loaded project state, got: $error")
      case Right(stateData) =>
        stateData.tasksRegistry.all.map(_.name).filter(_.startsWith("pluginTask")).toSet
    }

  private def retainedClassLoaders(state: DederProjectState): Seq[URLClassLoader] = {
    val field = classOf[DederProjectState].getDeclaredFields.find { field =>
      field.getName == "loadedPlugins" || field.getName.endsWith("loadedPlugins")
    }.getOrElse(fail("Could not find DederProjectState.loadedPlugins field"))
    field.setAccessible(true)
    val loadedPlugins = field.get(state)
    loadedPlugins.getClass.getMethods
      .find(method => method.getName == "classLoaders" && method.getParameterCount == 0)
      .map(_.invoke(loadedPlugins).asInstanceOf[Seq[URLClassLoader]])
      .getOrElse(fail("Loaded plugins state does not expose classLoaders"))
  }

  private def assertRetainedClassLoaders(state: DederProjectState, expected: Seq[CloseTrackingClassLoader]): Unit = {
    val retained = retainedClassLoaders(state)
    assertEquals(
      retained.size,
      expected.size,
      clues(s"Retained loaders: ${retained.mkString(", ")}; expected: ${expected.mkString(", ")}")
    )
    expected.foreach { loader =>
      assert(
        retained.contains(loader),
        clues(s"Expected retained loaders to include ${loader.id}, but got: ${retained.mkString(", ")}")
      )
    }
  }

  private def assertAllClosed(loaders: Seq[CloseTrackingClassLoader]): Unit =
    loaders.foreach(loader => assert(loader.wasClosed, clues(s"Expected ${loader.id} to be closed")))

  private def assertAllOpen(loaders: Seq[CloseTrackingClassLoader]): Unit =
    loaders.foreach(loader => assert(!loader.wasClosed, clues(s"Expected ${loader.id} to stay open")))

  test("reload with unchanged plugin fingerprint reuses the current plugin loader set") {
    val fakeLoader = new FakePluginLoader()
    val initialLoaders = Seq(
      new CloseTrackingClassLoader("fp1-plugin-a"),
      new CloseTrackingClassLoader("fp1-plugin-b")
    )
    fakeLoader.configureSuccess(
      "fp1",
      FakeLoadedPlugin("plugin-a", "pluginTaskA", initialLoaders.head),
      FakeLoadedPlugin("plugin-b", "pluginTaskB", initialLoaders(1))
    )

    withState(fakeLoader) { state =>
      assertEquals(fakeLoader.loadCalls, 1)
      assertEquals(activePluginTaskNames(state), Set("pluginTaskA", "pluginTaskB"))
      assertAllOpen(initialLoaders)

      state.reloadProject()

      assertEquals(fakeLoader.loadCalls, 1)
      assertEquals(activePluginTaskNames(state), Set("pluginTaskA", "pluginTaskB"))
      assertAllOpen(initialLoaders)
    }
  }

  test("plugin fingerprint change closes the previous loader set and retains the new loader set") {
    val fakeLoader = new FakePluginLoader()
    val firstLoaders = Seq(
      new CloseTrackingClassLoader("fp1-plugin-a"),
      new CloseTrackingClassLoader("fp1-plugin-b")
    )
    val secondLoaders = Seq(
      new CloseTrackingClassLoader("fp2-plugin-a"),
      new CloseTrackingClassLoader("fp2-plugin-b")
    )
    fakeLoader.configureSuccess(
      "fp1",
      FakeLoadedPlugin("plugin-a", "pluginTaskA", firstLoaders.head),
      FakeLoadedPlugin("plugin-b", "pluginTaskB", firstLoaders(1))
    )
    fakeLoader.configureSuccess(
      "fp2",
      FakeLoadedPlugin("plugin-a", "pluginTaskC", secondLoaders.head),
      FakeLoadedPlugin("plugin-b", "pluginTaskD", secondLoaders(1))
    )

    withState(fakeLoader) { state =>
      assertEquals(fakeLoader.loadCalls, 1)
      fakeLoader.fingerprintValue = "fp2"

      state.reloadProject()

      assertEquals(fakeLoader.loadCalls, 2)
      assertEquals(activePluginTaskNames(state), Set("pluginTaskC", "pluginTaskD"))
      assertAllClosed(firstLoaders)
      assertAllOpen(secondLoaders)
      assertRetainedClassLoaders(state, secondLoaders)
    }
  }

  test("failed plugin reload keeps the previous active loader set retained and open") {
    val fakeLoader = new FakePluginLoader()
    val initialLoaders = Seq(
      new CloseTrackingClassLoader("fp1-plugin-a"),
      new CloseTrackingClassLoader("fp1-plugin-b")
    )
    fakeLoader.configureSuccess(
      "fp1",
      FakeLoadedPlugin("plugin-a", "pluginTaskA", initialLoaders.head),
      FakeLoadedPlugin("plugin-b", "pluginTaskB", initialLoaders(1))
    )
    fakeLoader.configureFailure("fp2")

    withState(fakeLoader) { state =>
      assertEquals(fakeLoader.loadCalls, 1)
      assertEquals(activePluginTaskNames(state), Set("pluginTaskA", "pluginTaskB"))
      fakeLoader.fingerprintValue = "fp2"

      state.reloadProject()

      assertEquals(fakeLoader.loadCalls, 2)
      assertEquals(activePluginTaskNames(state), Set("pluginTaskA", "pluginTaskB"))
      assertAllOpen(initialLoaders)
      assertRetainedClassLoaders(state, initialLoaders)
    }
  }
}
