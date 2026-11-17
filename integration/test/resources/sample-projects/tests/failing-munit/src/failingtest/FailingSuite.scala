package failingtest

class FailingSuite extends munit.FunSuite {

  test("fails with guidance") {
    assertEquals(1 + 1, 3)
  }
}
