package munittest

class MunitSuite extends munit.FunSuite {

  test("test1") {
    println("running test1")
    val x = 1 + 1
    assertEquals(x, 2)
  }

}
