package ba.sake.deder.cli

import ba.sake.tupson.*
import CliServerMessage.*

class CliServerMessageSuite extends munit.FunSuite {

  test("CliServerMessage should be serializable to JSON") {
    locally {
      val msg = Output("This is some output")
      val json = msg.toJson
      val parsed = json.parseJson[CliServerMessage]
      assertEquals(parsed, msg)
    }
    locally {
      val msg = Log("This is a log message", LogLevel.INFO)
      val json = msg.toJson
      val parsed = json.parseJson[CliServerMessage]
      assertEquals(parsed, msg)
    }
    locally {
      val msg = RunSubprocess(Seq("echo", "Hello, World!"), Map.empty, watch = false)
      val json = msg.toJson
      val parsed = json.parseJson[CliServerMessage]
      assertEquals(parsed, msg)
    }
    locally {
      val msg = Exit(0, serverShuttingDown = true)
      val json = msg.toJson
      val parsed = json.parseJson[CliServerMessage]
      assertEquals(parsed, msg)
    }
  }
}
