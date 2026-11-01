package ba.sake.deder

import ox.*

class ProjectContextSuite extends munit.FunSuite {

  test("project root access fails outside a project scope") {
    val error = intercept[IllegalStateException](DederGlobals.projectRootDir)
    assert(error.getMessage.contains("No project context is active"))
  }

  test("project root propagates into Ox forks and does not leak after the scope") {
    val root = os.temp.dir()
    val forkRoot = ProjectContext.run(ProjectContext(root)) {
      forkUser {
        DederGlobals.projectRootDir
      }.join()
    }

    assertEquals(forkRoot, root)
    val error = intercept[IllegalStateException](DederGlobals.projectRootDir)
    assert(error.getMessage.contains("No project context is active"))
  }

  test("captured project root binds across a raw thread boundary") {
    val root = os.temp.dir()
    val observed = new java.util.concurrent.atomic.AtomicReference[os.Path]()

    ProjectContext.run(ProjectContext(root)) {
      val thread = Thread.ofVirtual().start(ProjectContext.runnable {
        observed.set(DederGlobals.projectRootDir)
      })
      thread.join()
    }

    assertEquals(observed.get(), root)
    val error = intercept[IllegalStateException](DederGlobals.projectRootDir)
    assert(error.getMessage.contains("No project context is active"))
  }
}
