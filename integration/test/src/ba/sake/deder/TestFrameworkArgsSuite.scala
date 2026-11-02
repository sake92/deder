package ba.sake.deder

class TestFrameworkArgsSuite extends BaseIntegrationSuite {

  for task <- Seq("test", "testInMemory") do {
    test(s"$task forwards MUnit tag filters and preserves suite selectors") {
      withTestProject(os.RelPath("sample-projects/framework-args")) { project =>
        val markers = project / "markers.txt"

        def run(args: Seq[String], expected: Seq[String]): Unit = {
          os.write.over(markers, "")
          val command = Seq("exec", "--no-color", "-t", task, "-m", "app-test") ++ args
          val result = executeDederCommand(project, command*)
          assertEquals(result.exitCode, 0, result.out.text() + result.err.text())
          assertEquals(os.read.lines(markers), expected)
        }

        run(Seq.empty, Seq("FAST", "SLOW"))
        run(Seq("tagsprobe.TagsSuite"), Seq("FAST", "SLOW"))
        run(Seq("--", "--", "--exclude-tags=Slow"), Seq("FAST"))
        run(Seq("--", "--", "--include-tags=Slow"), Seq("SLOW"))
        run(Seq("tagsprobe.TagsSuite", "--", "--exclude-tags=Slow"), Seq("FAST"))
      }
    }
  }
}
