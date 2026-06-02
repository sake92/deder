package ba.sake.deder

class DederGlobalsSuite extends munit.FunSuite {

  test("compile semaphore initializes with available processors") {
    val sem = DederGlobals.compileSemaphore
    assertEquals(sem.availablePermits(), Runtime.getRuntime.availableProcessors())
  }

  test("setCompileSemaphore updates the semaphore") {
    DederGlobals.setCompileSemaphore(4)
    val sem = DederGlobals.compileSemaphore
    assertEquals(sem.availablePermits(), 4)
    DederGlobals.setCompileSemaphore(Runtime.getRuntime.availableProcessors())
  }

  test("setCompileSemaphore floors at 1") {
    DederGlobals.setCompileSemaphore(0)
    val sem = DederGlobals.compileSemaphore
    assertEquals(sem.availablePermits(), 1)
    DederGlobals.setCompileSemaphore(Runtime.getRuntime.availableProcessors())
  }

  test("setTestForkSemaphore floors at 1") {
    DederGlobals.setTestForkSemaphore(0)
    val sem = DederGlobals.testForkSemaphore
    assertEquals(sem.availablePermits(), 1)
    DederGlobals.setTestForkSemaphore(Runtime.getRuntime.availableProcessors())
  }
}
