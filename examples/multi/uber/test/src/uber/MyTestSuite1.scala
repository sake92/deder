package uber

class MyTestSuite1 extends munit.FunSuite {
  test("test1") {
    println ("running test 1-1")
    val x = 1 + 1
    assertEquals(x, 2)
  }
  test("test2") {
    println ("running test 1-2")
    val x = 1 + 1
    assertEquals(x, 2)
  }
}



