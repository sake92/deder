package ba.sake.deder.testing

import ba.sake.tupson.*
import ba.sake.deder.testing.forked.ForkedTestArgs

class DederTestOptionsSuite extends munit.FunSuite {

  test("arguments without a separator remain selectors") {
    val selectors = Seq("example.%", "~example.SlowSuite", "example.Suite#one")
    assertEquals(DederTestOptions.fromArgs(selectors), DederTestOptions(selectors))
    assertEquals(DederTestOptions.fromArgs(Seq.empty), DederTestOptions(Seq.empty))
  }

  test("a separator keeps selectors separate from unchanged framework arguments") {
    val frameworkArgs = Seq("--include-tags=Slow", "--option", "a value", "--", "literal")
    val args = Seq("example.Suite", "--") ++ frameworkArgs
    assertEquals(DederTestOptions.fromArgs(args), DederTestOptions(Seq("example.Suite"), frameworkArgs))
  }

  test("framework arguments work without selectors and with an empty argument list") {
    assertEquals(
      DederTestOptions.fromArgs(Seq("--", "--exclude-tags=Slow")),
      DederTestOptions(Seq.empty, Seq("--exclude-tags=Slow"))
    )
    assertEquals(DederTestOptions.fromArgs(Seq("example.Suite", "--")), DederTestOptions(Seq("example.Suite")))
  }

  test("fork arguments preserve framework arguments through JSON") {
    val args = ForkedTestArgs(
      forkId = 0,
      discoveredTests = Seq.empty,
      testSelectors = Seq("example.Suite"),
      testParallelism = 1,
      resultsFile = "results.json",
      frameworkArgs = Seq("--include-tags=Slow", "--option", "a value")
    )
    assertEquals(args.toJson.parseJson[ForkedTestArgs], args)
  }

  test("older fork arguments default to no framework arguments") {
    val json = """{"forkId":0,"discoveredTests":[],"testSelectors":[],"testParallelism":1,"resultsFile":"results.json"}"""
    assertEquals(json.parseJson[ForkedTestArgs].frameworkArgs, Seq.empty)
  }
}
