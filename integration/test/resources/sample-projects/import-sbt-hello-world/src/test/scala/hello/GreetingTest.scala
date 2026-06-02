package hello

class GreetingTest extends munit.FunSuite {
  test("greet returns greeting") {
    assertEquals(Greeting.greet("World"), "Hello, World!")
  }
}
