package ba.sake.deder.deps

class DependencyReportsSuite extends munit.FunSuite {

  private val sample = DependencyGraphData(
    moduleId = "app",
    nodes = Seq(
      DepNode(
        coord = DepCoord("org.example", "a", "1.0.0"),
        artifactPath = None,
        sizeBytes = 100,
        isDirect = true,
        requestedVersions = Seq("1.0.0"),
        selectedVersions = Seq("1.0.0"),
        evictedRequestedVersions = Seq.empty,
        isConflict = false
      ),
      DepNode(
        coord = DepCoord("org.example", "b", "2.0.0"),
        artifactPath = None,
        sizeBytes = 200,
        isDirect = false,
        requestedVersions = Seq("1.5.0"),
        selectedVersions = Seq("2.0.0"),
        evictedRequestedVersions = Seq("1.5.0"),
        isConflict = true
      )
    ),
    edges = Seq(
      DepEdge("module:app", "dep:org.example:a:1.0.0", "direct"),
      DepEdge("module:app", "dep:org.example:b:2.0.0", "transitive")
    ),
    directCount = 1,
    transitiveCount = 1,
    totalSizeBytes = 300,
    conflicts = Seq(
      DepConflict(
        key = "org.example:b",
        requestedVersions = Seq("1.5.0"),
        selectedVersions = Seq("2.0.0"),
        evictedRequestedVersions = Seq("1.5.0"),
        requestedPathExamples = Seq("app -> org.example:b:1.5.0")
      )
    )
  )

  test("DependencyReportOptions parses common args") {
    val parsed = DependencyReportOptions.fromTaskArgs(
      Seq("--max-depth", "1", "--include", "org.example:%", "--exclude", "org.example:b:%", "--why", "org.example:b")
    )
    assert(parsed.isRight)
    val options = parsed.toOption.get
    assertEquals(options.maxDepth, 1)
    assertEquals(options.includePatterns, Seq("org.example:%"))
    assertEquals(options.excludePatterns, Seq("org.example:b:%"))
    assertEquals(options.whySelector, Some("org.example:b"))
  }

  test("filters keep only direct deps when direct-only requested") {
    val filtered = DependencyReportRenderers.applyFilters(sample, DependencyReportOptions(directOnly = true))
    assertEquals(filtered.nodes.map(_.coord.repr), Seq("org.example:a:1.0.0"))
    assertEquals(filtered.transitiveCount, 0)
  }

  test("renderWhy asks for selector when absent") {
    val rendered = DependencyReportRenderers.renderWhy(sample, DependencyReportOptions())
    assert(rendered.contains("Missing selector"))
  }

  test("renderers include conflict markers") {
    val rendered = DependencyReportRenderers.renderTree(sample, DependencyReportOptions())
    assert(rendered.contains("Conflict summary"))
    assert(rendered.contains("evicted requested"))
  }
}
