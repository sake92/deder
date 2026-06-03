package lib

class LibTest extends munit.FunSuite {
  test("adds numbers") {
    assertEquals(Lib.add(2, 3), 5)
  }
}
