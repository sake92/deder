package ba.sake.deder.scalajstest

class JsTestSuite1 extends munit.FunSuite {

  test("successful test") {
    println("running test 1-1")
    NodejsConsole.info("Hello from Scala.js test!")
    val x = 1 + 1
    assertEquals(x, 2)
  }

  test("ignored test".ignore) {
    println("running test 1-2")
    val x = 1 + 1
    assertEquals(x, 2)
  }

}