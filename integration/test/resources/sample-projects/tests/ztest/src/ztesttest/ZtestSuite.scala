package ztesttest

import zio.*
import zio.test.*

object ZtestSuite extends ZIOSpecDefault {
  override def spec = suite("suite1")(
    test("returnString correctly returns string") {
      val testString = "Hello World!"
      for {
        output <- returnString(testString)
      } yield assertTrue(output == testString)
    }
  )
}

def returnString(str: String): ZIO[Any, Nothing, String] =
  ZIO.succeed(str)