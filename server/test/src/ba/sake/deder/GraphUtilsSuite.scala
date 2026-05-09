package ba.sake.deder

import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge

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
      vertexClassProvider = v => Some(s"stage${stageByVertex(v)}"),
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
}
