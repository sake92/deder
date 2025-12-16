package uber

class MyTest extends munit.FunSuite {

  test("basic") {
    println ("running test 123")
    val x = 1 + 1
    assertEquals(x, 2)
  }
}

