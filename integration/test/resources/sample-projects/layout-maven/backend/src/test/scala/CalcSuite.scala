package backend

import munit.FunSuite

class CalcSuite extends FunSuite {
  test("add works") {
    assertEquals(Calc.add(2, 3), 5)
  }
}
