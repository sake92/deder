package ba.sake.deder

import scala.jdk.CollectionConverters.*
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph

class GraphUtilsSuite extends munit.FunSuite {

  test("generateMermaidWithSubgraphs supports stage labels and colors") {
    val g = new DefaultDirectedGraph[String, DefaultEdge](classOf[DefaultEdge])
    Seq(
      "common.compileClasspath",
      "common.compile",
      "backend.compile"
    ).foreach(g.addVertex)
    g.addEdge("common.compileClasspath", "common.compile")
    g.addEdge("common.compileClasspath", "backend.compile")

    val stageByVertex = Map(
      "common.compileClasspath" -> 0,
      "common.compile" -> 1,
      "backend.compile" -> 1
    )
    val mermaid = GraphUtils.generateMermaidWithSubgraphs(
      g,
      groups = Map(
        "backend" -> Seq("backend.compile"),
        "common" -> Seq("common.compileClasspath", "common.compile")
      ),
      vertexIdProvider = identity,
      vertexLabel = v => s"${v.split("\\.").last} (#${stageByVertex(v)})",
      extraLines = Seq("%% #0 = evaluated first stage"),
      vertexCssClassProvider = v => Some(s"stage${stageByVertex(v)}"),
      classDefs = Map(
        "stage0" -> "fill:#e8f0fe,stroke:#1a73e8,color:#0b1f44",
        "stage1" -> "fill:#e6f4ea,stroke:#137333,color:#0d2e1a"
      )
    )

    assert(mermaid.contains("""flowchart TD"""))
    assert(mermaid.contains("""%% #0 = evaluated first stage"""))
    assert(mermaid.contains("""common_compileClasspath["compileClasspath (#0)"]"""))
    assert(mermaid.contains("""common_compile["compile (#1)"]"""))
    assert(mermaid.contains("""backend_compile["compile (#1)"]"""))
    assert(mermaid.contains("""classDef stage0 fill:#e8f0fe,stroke:#1a73e8,color:#0b1f44"""))
    assert(mermaid.contains("""classDef stage1 fill:#e6f4ea,stroke:#137333,color:#0d2e1a"""))
    assert(mermaid.contains("""class common_compileClasspath stage0"""))
    assert(mermaid.contains("""class backend_compile,common_compile stage1"""))
  }

  test("projectPublic: keeps all vertices when all are public") {
    val g = new SimpleDirectedGraph[String, DefaultEdge](classOf[DefaultEdge])
    Seq("a", "b", "c").foreach(g.addVertex)
    g.addEdge("a", "b")
    g.addEdge("b", "c")

    val projected = GraphUtils.projectPublic(g, _ => true)
    assertEquals(projected.vertexSet().asScala.toSet, Set("a", "b", "c"))
    assertEquals(
      projected.edgeSet().asScala.map(e => projected.getEdgeSource(e) -> projected.getEdgeTarget(e)).toSet,
      Set("a" -> "b", "b" -> "c")
    )
  }

  test("projectPublic: removes internal vertex and bridges edges") {
    // a(public) -> b(internal) -> c(public)
    // expected: a -> c
    val g = new SimpleDirectedGraph[String, DefaultEdge](classOf[DefaultEdge])
    Seq("a", "b", "c").foreach(g.addVertex)
    g.addEdge("a", "b")
    g.addEdge("b", "c")

    val internal = Set("b")
    val projected = GraphUtils.projectPublic(g, v => !internal.contains(v))
    assertEquals(projected.vertexSet().asScala.toSet, Set("a", "c"))
    assertEquals(
      projected.edgeSet().asScala.map(e => projected.getEdgeSource(e) -> projected.getEdgeTarget(e)).toSet,
      Set("a" -> "c")
    )
  }

  test("projectPublic: bridges over a chain of internal vertices") {
    // a(public) -> b(internal) -> c(internal) -> d(public)
    // expected: a -> d
    val g = new SimpleDirectedGraph[String, DefaultEdge](classOf[DefaultEdge])
    Seq("a", "b", "c", "d").foreach(g.addVertex)
    g.addEdge("a", "b")
    g.addEdge("b", "c")
    g.addEdge("c", "d")

    val internal = Set("b", "c")
    val projected = GraphUtils.projectPublic(g, v => !internal.contains(v))
    assertEquals(projected.vertexSet().asScala.toSet, Set("a", "d"))
    assertEquals(
      projected.edgeSet().asScala.map(e => projected.getEdgeSource(e) -> projected.getEdgeTarget(e)).toSet,
      Set("a" -> "d")
    )
  }

  test("projectPublic: bridges stop at first public vertex, not transitively") {
    // a(public) -> b(internal) -> c(public) -> d(public)
    // b bridges a->c; c->d is a direct public edge
    val g = new SimpleDirectedGraph[String, DefaultEdge](classOf[DefaultEdge])
    Seq("a", "b", "c", "d").foreach(g.addVertex)
    g.addEdge("a", "b")
    g.addEdge("b", "c")
    g.addEdge("c", "d")

    val internal = Set("b")
    val projected = GraphUtils.projectPublic(g, v => !internal.contains(v))
    assertEquals(projected.vertexSet().asScala.toSet, Set("a", "c", "d"))
    assertEquals(
      projected.edgeSet().asScala.map(e => projected.getEdgeSource(e) -> projected.getEdgeTarget(e)).toSet,
      Set("a" -> "c", "c" -> "d")
    )
  }

  test("projectPublic: multiple public targets from one internal bridge") {
    // a(public) -> b(internal) -> c(public)
    //                           -> d(public)
    val g = new SimpleDirectedGraph[String, DefaultEdge](classOf[DefaultEdge])
    Seq("a", "b", "c", "d").foreach(g.addVertex)
    g.addEdge("a", "b")
    g.addEdge("b", "c")
    g.addEdge("b", "d")

    val internal = Set("b")
    val projected = GraphUtils.projectPublic(g, v => !internal.contains(v))
    assertEquals(projected.vertexSet().asScala.toSet, Set("a", "c", "d"))
    assertEquals(
      projected.edgeSet().asScala.map(e => projected.getEdgeSource(e) -> projected.getEdgeTarget(e)).toSet,
      Set("a" -> "c", "a" -> "d")
    )
  }

  test("projectPublic: removes isolated internal vertices") {
    val g = new SimpleDirectedGraph[String, DefaultEdge](classOf[DefaultEdge])
    Seq("a", "b").foreach(g.addVertex) // b is internal with no connections
    val internal = Set("b")
    val projected = GraphUtils.projectPublic(g, v => !internal.contains(v))
    assertEquals(projected.vertexSet().asScala.toSet, Set("a"))
    assertEquals(projected.edgeSet().asScala.toSet, Set.empty)
  }
}
