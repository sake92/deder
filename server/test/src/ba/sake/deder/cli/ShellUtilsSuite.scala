package ba.sake.deder.cli

class ShellUtilsSuite extends munit.FunSuite {

  test("splits simple command") {
    val (tokens, cursorIdx) = ShellUtils.shellSplit("deder exec -m common", 20)
    assertEquals(tokens, Seq("deder", "exec", "-m", "common"))
    assertEquals(cursorIdx, 3)
  }

  test("cursor at start is in first word") {
    val (_, cursorIdx) = ShellUtils.shellSplit("deder exec", 0)
    assertEquals(cursorIdx, 0)
  }

  test("cursor in middle of word") {
    val (tokens, cursorIdx) = ShellUtils.shellSplit("deder ex", 7)
    assertEquals(tokens, Seq("deder", "ex"))
    assertEquals(cursorIdx, 1)
  }

  test("cursor at end after space adds empty token") {
    val (tokens, cursorIdx) = ShellUtils.shellSplit("deder exec ", 11)
    assertEquals(tokens, Seq("deder", "exec", ""))
    assertEquals(cursorIdx, 2)
  }

  test("single quotes protect spaces") {
    val (tokens, cursorIdx) = ShellUtils.shellSplit("echo 'hello world' done", 23)
    assertEquals(tokens, Seq("echo", "hello world", "done"))
    assertEquals(cursorIdx, 2)
  }

  test("double quotes protect spaces") {
    val input = "echo \"hello world\" done"
    val (tokens, cursorIdx) = ShellUtils.shellSplit(input, input.length)
    assertEquals(tokens, Seq("echo", "hello world", "done"))
    assertEquals(cursorIdx, 2)
  }

  test("backslash escapes in double quotes") {
    val input = "echo \"hello \\\" world\\\"\" done"
    val (tokens, _) = ShellUtils.shellSplit(input, input.length)
    assertEquals(tokens, Seq("echo", "hello \" world\"", "done"))
  }

  test("backslash escapes outside quotes") {
    val input = "echo hello\\ world done"
    val (tokens, _) = ShellUtils.shellSplit(input, input.length)
    assertEquals(tokens, Seq("echo", "hello world", "done"))
  }

  test("empty input") {
    val (tokens, cursorIdx) = ShellUtils.shellSplit("", 0)
    assertEquals(tokens, Seq.empty)
    assertEquals(cursorIdx, -1)
  }

  test("whitespace only") {
    val (tokens, cursorIdx) = ShellUtils.shellSplit("   ", 1)
    assertEquals(tokens, Seq.empty)
    assertEquals(cursorIdx, -1)
  }

  test("multiple consecutive spaces") {
    val (tokens, cursorIdx) = ShellUtils.shellSplit("a   b", 5)
    assertEquals(tokens, Seq("a", "b"))
    assertEquals(cursorIdx, 1)
  }
}
