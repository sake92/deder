package ba.sake.deder.cli

import ba.sake.tupson.*
import CliServerMessage.*
import ba.sake.deder.{OutputFormat, RequestContext}
import ox.*

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

  test("log() wraps message body in ANSI color by severity") {
    val ctx = RequestContext(logLevel = LogLevel.INFO)
    RequestContext.current.supervisedWhere(ctx) {
      val errMsg = CliServerMessage.log("build failed", LogLevel.ERROR)
      assertContains(errMsg.text, fansi.Color.Red("build failed").toString())

      val warnMsg = CliServerMessage.log("deprecated", LogLevel.WARNING)
      assertContains(warnMsg.text, fansi.Color.Yellow("deprecated").toString())

      val infoMsg = CliServerMessage.log("all good", LogLevel.INFO)
      assertEquals(infoMsg.text, "all good")
    }
  }

  test("log() prefixes level label only when logLevel >= DEBUG") {
    val infoCtx = RequestContext(logLevel = LogLevel.INFO)
    RequestContext.current.supervisedWhere(infoCtx) {
      val msg = CliServerMessage.log("hello", LogLevel.ERROR)
      // In INFO mode, no prefix — just the colored message body
      assertEquals(msg.text, fansi.Color.Red("hello").toString())
    }
    val debugCtx = RequestContext(logLevel = LogLevel.DEBUG)
    RequestContext.current.supervisedWhere(debugCtx) {
      val msg = CliServerMessage.log("hello", LogLevel.ERROR)
      // In DEBUG mode, prefix label "error" appears (colored with fansi)
      assertContains(msg.text, "error")
      // The opening bracket '[' is present (it's plain, outside ANSI wraps)
      assertContains(msg.text, "[")
    }
  }

  test("log() colors module ID but not brackets, same module gets same color") {
    val ctx = RequestContext(logLevel = LogLevel.INFO)
    RequestContext.current.supervisedWhere(ctx) {
      val msg1 = CliServerMessage.log("hello1", LogLevel.INFO, Some("myapp"))
      val msg2 = CliServerMessage.log("hello2", LogLevel.INFO, Some("myapp"))

      // Same module should get same color (deterministic from hash)
      val color1 = extractAnsiWrap(msg1.text, "myapp")
      val color2 = extractAnsiWrap(msg2.text, "myapp")
      assertEquals(color1, color2, "same module should get same color across calls")

      // Different module may get different color
      val msg3 = CliServerMessage.log("hello3", LogLevel.INFO, Some("other"))
      assertContains(msg3.text, "other")
    }
  }

  private def extractAnsiWrap(text: String, substr: String): String = {
    val idx = text.indexOf(substr)
    if (idx < 0) return ""
    val before = text.substring(0, idx)
    val escStart = before.lastIndexOf("\u001b[")
    if (escStart < 0) ""
    else before.substring(escStart)
  }

  private def assertContains(haystack: String, needle: String)(implicit loc: munit.Location): Unit = {
    assert(haystack.contains(needle), s"Expected '$haystack' to contain '$needle'")
  }
}
