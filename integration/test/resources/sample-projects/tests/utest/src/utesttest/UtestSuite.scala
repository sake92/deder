package utesttest

import utest.*

class UtestSuite extends TestSuite {

  val tests = Tests {
    test("test1") {
      println("running test1")
      val x = 1 + 1
      assert(x == 2)
    }
  }

}
