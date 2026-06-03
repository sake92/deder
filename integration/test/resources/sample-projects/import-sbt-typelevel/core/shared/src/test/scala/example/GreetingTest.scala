package example
class GreetingTest extends munit.FunSuite {
  test("hi") { assertEquals(Greeting.hi, "hi from typelevel") }
}
