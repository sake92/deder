package uber

class MyTestSuite2 extends munit.FunSuite {
  test("suite2_test1") {
    println ("running test 123")
    val x = 1 + 13
    assertEquals(x, 2)
  }
}



