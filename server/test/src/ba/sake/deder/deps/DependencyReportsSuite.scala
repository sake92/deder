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
        coord = DepCoord("org.example", "c", "1.1.0"),
        artifactPath = None,
        sizeBytes = 50,
        isDirect = true,
        requestedVersions = Seq("1.1.0"),
        selectedVersions = Seq("1.1.0"),
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
      ),
      DepNode(
        coord = DepCoord("org.example", "d", "3.0.0"),
        artifactPath = None,
        sizeBytes = 150,
        isDirect = false,
        requestedVersions = Seq.empty,
        selectedVersions = Seq("3.0.0"),
        evictedRequestedVersions = Seq.empty,
        isConflict = false
      )
    ),
    edges = Seq(
      DepEdge("module:app", "dep:org.example:a:1.0.0", "direct"),
      DepEdge("module:app", "dep:org.example:c:1.1.0", "direct"),
      DepEdge("dep:org.example:a:1.0.0", "dep:org.example:b:2.0.0", "dependency"),
      DepEdge("dep:org.example:c:1.1.0", "dep:org.example:b:2.0.0", "dependency"),
      DepEdge("dep:org.example:b:2.0.0", "dep:org.example:d:3.0.0", "dependency")
    ),
    directCount = 2,
    transitiveCount = 2,
    totalSizeBytes = 500,
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

  test("DependencyReportOptions parses common args and compatibility aliases") {
    val parsed = DependencyReportOptions.fromTaskArgs(
      Seq(
        "--max-depth",
        "2",
        "--direct-only",
        "--include",
        "org.example:%",
        "--exclude",
        "org.example:c:%",
        "--why",
        "org.example:b",
        "--path",
        "all",
        "--sort",
        "name"
      )
    )
    assert(parsed.isRight)
    val options = parsed.toOption.get
    assertEquals(options.maxDepth, 1)
    assertEquals(options.includePatterns, Seq("org.example:%"))
    assertEquals(options.excludePatterns, Seq("org.example:c:%"))
    assertEquals(options.whySelector, Some("org.example:b"))
    assertEquals(options.whyPathMode, DependencyPathMode.All)
    assertEquals(options.sort, DependencySort.Name)
  }

  test("filters respect max-depth traversal") {
    val filtered = DependencyReportRenderers.applyFilters(sample, DependencyReportOptions(maxDepth = 2))
    assertEquals(filtered.nodes.map(_.coord.repr).sorted, Seq("org.example:a:1.0.0", "org.example:b:2.0.0", "org.example:c:1.1.0"))
    assertEquals(filtered.transitiveCount, 1)
  }

  test("renderWhy asks for selector when absent") {
    val rendered = DependencyReportRenderers.renderWhy(sample, DependencyReportOptions())
    assert(rendered.contains("Missing selector"))
  }

  test("renderWhy shortest path uses resolved graph edges") {
    val rendered = DependencyReportRenderers.renderWhy(
      sample,
      DependencyReportOptions(whySelector = Some("org.example:d"), whyPathMode = DependencyPathMode.Shortest)
    )
    assert(rendered.contains("shortest path: app -> org.example:a:1.0.0 -> org.example:b:2.0.0 -> org.example:d:3.0.0"))
  }

  test("renderWhy all paths includes every route to a shared transitive dependency") {
    val rendered = DependencyReportRenderers.renderWhy(
      sample,
      DependencyReportOptions(whySelector = Some("org.example:b"), whyPathMode = DependencyPathMode.All)
    )
    assert(rendered.contains("paths (2):"))
    assert(rendered.contains("app -> org.example:a:1.0.0 -> org.example:b:2.0.0"))
    assert(rendered.contains("app -> org.example:c:1.1.0 -> org.example:b:2.0.0"))
  }

  test("renderTree includes merged stats and sorts children by size") {
    val rendered = DependencyReportRenderers.renderTree(sample, DependencyReportOptions())
    assert(rendered.contains("total dependencies: 4"))
    assert(rendered.contains("total size: 500 B"))
    assert(rendered.indexOf("org.example:a:1.0.0") < rendered.indexOf("org.example:c:1.1.0"))
    assert(rendered.contains("org.example:b:2.0.0 (200 B) [evicted requested: 1.5.0]"))
  }

  test("renderHtml includes graph-aware controls and rows") {
    val rendered = DependencyReportRenderers.renderHtml(sample, DependencyReportOptions())
    assert(rendered.contains("""id="directOnly""""))
    assert(rendered.contains("shortest path"))
    assert(rendered.contains("org.example:b"))
  }
}
