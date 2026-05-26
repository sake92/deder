package uber

class MyTestSuite1 extends munit.FunSuite {

  test("successful test") {
    println("running test 1-1")
    val x = 1 + 1
    var count = 0
    while count < 10 do {
      println("sleeping..")
      Thread.sleep(500)
      count += 1
    }
    assertEquals(x, 2)
  }

  test("ignored test".ignore) {
    println("running test 1-2")
    val x = 1 + 1
    assertEquals(x, 2)
  }

}
