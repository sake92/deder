package ba.sake.deder

class DederGlobalsSuite extends munit.FunSuite {

  override def afterEach(context: AfterEach): Unit = {
    val cpus = Runtime.getRuntime.availableProcessors()
    DederGlobals.setCompileSemaphore(cpus)
    DederGlobals.setTestForkSemaphore(cpus)
  }

  test("compile semaphore initializes with available processors") {
    val sem = DederGlobals.compileSemaphore
    assertEquals(sem.availablePermits(), Runtime.getRuntime.availableProcessors())
  }

  test("setCompileSemaphore updates the semaphore") {
    DederGlobals.setCompileSemaphore(4)
    val sem = DederGlobals.compileSemaphore
    assertEquals(sem.availablePermits(), 4)
  }

  test("setCompileSemaphore floors at 1") {
    DederGlobals.setCompileSemaphore(0)
    val sem = DederGlobals.compileSemaphore
    assertEquals(sem.availablePermits(), 1)
  }

  test("setTestForkSemaphore floors at 1") {
    DederGlobals.setTestForkSemaphore(0)
    val sem = DederGlobals.testForkSemaphore
    assertEquals(sem.availablePermits(), 1)
  }

  test("allClassesDirs: own dir first, then deps in given order (no dedup — that's Classpath's job)") {
    System.setProperty("DEDER_PROJECT_ROOT_DIR", os.pwd.toString)
    // a -> {b, c}, b -> d, c -> d : tree flatten is [b, c, d], d is the shared foundation (last)
    val dirs = DederGlobals.allClassesDirs("a", Seq("b", "c", "d"))
    assertEquals(dirs, Seq("a", "b", "c", "d").map(DederGlobals.classesDir))
  }
}
