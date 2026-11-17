package compatibility

class CompatibilitySuite extends munit.FunSuite {

  test("Scala 3.10 compiles and runs on the active JDK") {
    assert(Compatibility.supportedJdk)
  }
}
