package ba.sake.deder

import java.net.URLClassLoader
import java.util.concurrent.Executors
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.ModuleType
import ba.sake.deder.deps.DependencyResolverApi
import ba.sake.deder.plugin.{PluginLoader, PluginLoaderApi}

class DederProjectStatePluginReloadSuite extends munit.FunSuite {

  private val sourceProjectDir = os.pwd / "server/test/resources/sample-projects/multi"

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

  private def withState(fakeLoader: FakePluginLoader)(f: (os.Path, DederProjectState) => Unit): Unit = {
    val tempProjectDir = os.temp.dir(prefix = "deder-plugin-reload-")
    os.copy(sourceProjectDir, tempProjectDir, replaceExisting = true, createFolders = true)
    val oldRoot = System.getProperty("DEDER_PROJECT_ROOT_DIR")
    System.setProperty("DEDER_PROJECT_ROOT_DIR", tempProjectDir.toString)
    val pool = Executors.newFixedThreadPool(2)
    val state = DederProjectState(
      TasksRegistry(CoreTasks().all),
      Int.MaxValue,
      pool,
      () => (),
      pluginLoaderFactory = (_: CoreTasksApi, _: DependencyResolverApi) => fakeLoader
    )
    try f(tempProjectDir, state)
    finally {
      state.shutdown()
      pool.shutdownNow()
      if oldRoot == null then System.clearProperty("DEDER_PROJECT_ROOT_DIR")
      else System.setProperty("DEDER_PROJECT_ROOT_DIR", oldRoot)
    }
  }

  test("non-plugin config reload reuses already loaded plugin tasks") {
    val fakeLoader = new FakePluginLoader()
    withState(fakeLoader) { (projectDir, state) =>
      assertEquals(fakeLoader.loadCalls, 1)
      val dederPkl = projectDir / "deder.pkl"
      os.write.append(dederPkl, s"\n// non-plugin change ${System.nanoTime()}\n")
      state.reloadProject()
      assertEquals(fakeLoader.loadCalls, 1)
    }
  }

  test("plugin fingerprint change reloads plugin tasks exactly once") {
    val fakeLoader = new FakePluginLoader()
    withState(fakeLoader) { (_, state) =>
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
    withState(fakeLoader) { (_, state) =>
      assertEquals(fakeLoader.loadCalls, 1)
      (1 to 5).foreach(_ => state.reloadProject())
      assertEquals(fakeLoader.loadCalls, 1)
    }
  }
}
