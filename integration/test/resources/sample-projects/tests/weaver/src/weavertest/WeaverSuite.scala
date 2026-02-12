package weavertest

import weaver.SimpleIOSuite
import cats.effect._

object WeaverSuite extends SimpleIOSuite {

  pureTest("non-effectful (pure) test") {
    expect("hello".size == 5)
  }

  private val random = IO(java.util.UUID.randomUUID())

  test("test with side-effects") {
    for {
      x <- random
      y <- random
    } yield expect(x != y)
  }
}
