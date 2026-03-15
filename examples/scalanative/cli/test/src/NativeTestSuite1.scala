package ba.sake.deder.scalanativetest

class NativeTestSuite1 extends munit.FunSuite {

  test("successful native test") {
    println("running native test 1-1")
    val x = 1 + 1
    assertEquals(x, 2)
  }

  test("ignored native test".ignore) {
    println("running native test 1-2")
    val x = 1 + 1
    assertEquals(x, 2)
  }

}
