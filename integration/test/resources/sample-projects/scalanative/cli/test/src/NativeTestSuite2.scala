package ba.sake.deder.scalanativetest

class NativeTestSuite2 extends munit.FunSuite {

  test("failing native test") {
    println("running native test 2-1")
    val x = 1 + 13
    assertEquals(x, 2)
  }

}
