package ba.sake.deder

import java.net.URLClassLoader
import java.util.concurrent.Executors
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.ModuleType
import ba.sake.deder.deps.DependencyResolverApi
import ba.sake.deder.plugin.{PluginLoader, PluginLoaderApi}

class DederProjectStatePluginReloadSuite extends munit.FunSuite {

  private val sourceProjectDir = os.pwd / "server/test/resources/sample-projects/multi"
  private val TestWorkerThreads = 2

  private class CloseTrackingClassLoader extends URLClassLoader(Array.empty, getClass.getClassLoader) {
    @volatile var wasClosed = false
    override def close(): Unit = {
      wasClosed = true
      super.close()
    }
  }

  private class FakePluginLoader extends PluginLoaderApi {
    @volatile var fingerprintValue: String = "fp1"
    @volatile var taskName: String = "pluginTaskA"
    @volatile var failLoad = false
    @volatile var loadCalls = 0
    @volatile var latestClassLoader: Option[CloseTrackingClassLoader] = None

    override def extractPluginDeps(project: DederProject): Seq[(String, String)] = Seq.empty

    override def fingerprint(project: DederProject, pklFile: os.Path): Either[String, String] =
      Right(fingerprintValue)

    override def load(pklFile: os.Path): Either[String, PluginLoader.PluginLoadResult] = {
      loadCalls += 1
      if failLoad then Left("Simulated plugin load failure")
      else {
        val classLoader = new CloseTrackingClassLoader()
        latestClassLoader = Some(classLoader)
        val pluginTask = TaskBuilder
          .make[String](taskName, supportedModuleTypes = Set(ModuleType.SCALA))
          .build(_ => "ok")
        Right(PluginLoader.PluginLoadResult(Seq(pluginTask), Some(classLoader)))
      }
    }
  }

  private def withState(fakeLoader: FakePluginLoader)(f: DederProjectState => Unit): Unit = {
    val pool = Executors.newFixedThreadPool(TestWorkerThreads)
    val state = DederProjectState(
      TasksRegistry(CoreTasks().all),
      Int.MaxValue,
      pool,
      () => (),
      pluginLoaderFactory = (_: CoreTasksApi, _: DependencyResolverApi) => fakeLoader,
      configFilePath = sourceProjectDir / "deder.pkl"
    )
    try f(state)
    finally {
      state.shutdown()
      pool.shutdownNow()
    }
  }

  test("reload with unchanged plugin fingerprint reuses already loaded plugin tasks") {
    val fakeLoader = new FakePluginLoader()
    withState(fakeLoader) { state =>
      assertEquals(fakeLoader.loadCalls, 1)
      state.reloadProject()
      assertEquals(fakeLoader.loadCalls, 1)
    }
  }

  test("plugin fingerprint change reloads plugin tasks exactly once") {
    val fakeLoader = new FakePluginLoader()
    withState(fakeLoader) { state =>
      assertEquals(fakeLoader.loadCalls, 1)
      val firstClassLoader = fakeLoader.latestClassLoader.getOrElse(fail("Expected initial plugin classloader"))

      fakeLoader.fingerprintValue = "fp2"
      fakeLoader.taskName = "pluginTaskB"
      state.reloadProject()
      assertEquals(fakeLoader.loadCalls, 2)
      val secondClassLoader = fakeLoader.latestClassLoader.getOrElse(fail("Expected reloaded plugin classloader"))

      state.reloadProject()
      assertEquals(fakeLoader.loadCalls, 2)
      assert(firstClassLoader.wasClosed, "Old plugin classloader should be closed after plugin change")
      assert(!secondClassLoader.wasClosed, "Current plugin classloader must stay open")
    }
  }

  test("repeated reloads with unchanged plugin fingerprint do not trigger duplicate plugin loads") {
    val fakeLoader = new FakePluginLoader()
    withState(fakeLoader) { state =>
      assertEquals(fakeLoader.loadCalls, 1)
      (1 to 5).foreach(_ => state.reloadProject())
      assertEquals(fakeLoader.loadCalls, 1)
    }
  }
}
