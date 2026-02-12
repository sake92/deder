package scalatesttest

import org.scalatest.funsuite.AnyFunSuite

class ScalatestSuite extends AnyFunSuite {

  test("test1") {
    println("running test1")
    val x = 1 + 1
    assert(x == 2)
  }

}
