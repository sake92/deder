package lib
class LibTest extends munit.FunSuite {
  test("add") { assertEquals(Lib.add(1, 2), 3) }
  test("platform name is non-empty") { assert(Lib.platformName.nonEmpty) }
}
