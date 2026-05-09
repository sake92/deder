package ba.sake.deder

import java.io.StringWriter

import scala.jdk.FunctionConverters.*
import scala.jdk.CollectionConverters.*
import org.jgrapht.Graph
import org.jgrapht.alg.cycle.CycleDetector
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.EdgeReversedGraph
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import org.jgrapht.traverse.BreadthFirstIterator

object GraphUtils {

  def checkNoCycles[V, E](g: Graph[V, E], getName: V => String): Unit = {
    val cycleDetector = new CycleDetector[V, E](g)
    val cycles = cycleDetector.findCycles().asScala
    if cycles.nonEmpty then throw DederException(s"Cycle detected: ${cycles.map(getName).mkString("->")}")
  }

  /** Returns a subgraph containing only vertices reachable from any focal vertex
    * within `depthDown` hops (following edges) and `depthUp` hops (following reversed edges).
    * Focal vertices are always included. `Int.MaxValue` means unlimited.
    */
  def subgraphAround[V, E](
      g: Graph[V, E],
      focalVertices: Set[V],
      depthDown: Int,
      depthUp: Int
  ): Graph[V, E] = {
    require(depthDown >= 0, s"depthDown must be non-negative, got: $depthDown")
    require(depthUp >= 0, s"depthUp must be non-negative, got: $depthUp")

    val collected = scala.collection.mutable.Set[V]()
    collected.addAll(focalVertices)

    // BFS downstream: follow edges (focal → dependencies)
    focalVertices.foreach { v =>
      val bfs =
        if depthDown == Int.MaxValue then new BreadthFirstIterator(g, v)
        else new BreadthFirstIterator(g, v, depthDown.toDouble)
      while bfs.hasNext do collected.add(bfs.next())
    }

    // BFS upstream: follow reversed edges (focal → dependents)
    val reversed = new EdgeReversedGraph(g)
    focalVertices.foreach { v =>
      val bfs =
        if depthUp == Int.MaxValue then new BreadthFirstIterator(reversed, v)
        else new BreadthFirstIterator(reversed, v, depthUp.toDouble)
      while bfs.hasNext do collected.add(bfs.next())
    }

    new AsSubgraph(g, collected.asJava)
  }

  def generateDOT[V, E](
      g: Graph[V, E],
      vertexIdProvider: V => String,
      vertexAttributeProvider: V => Map[String, String]
  ): String = {
    val vertexIdProvider0 = (v: V) => vertexIdProvider(v).replaceAll("[-.]", "_")
    val exporter = new DOTExporter[V, E](vertexIdProvider0.asJava)
    exporter.setVertexAttributeProvider { v =>
      vertexAttributeProvider(v)
        .mapValues(DefaultAttribute.createAttribute)
        .toMap
        .asJava
    }
    val writer = new StringWriter()
    exporter.exportGraph(g, writer)
    writer.toString
  }

  /** Generates a flat Mermaid flowchart (no subgraphs). Used for modules graph. */
  def generateMermaid[V, E](
      g: Graph[V, E],
      vertexIdProvider: V => String,
      vertexLabel: V => String
  ): String = {
    val sanitize = (s: String) => s.replaceAll("[^a-zA-Z0-9_]", "_")
    val sb = new StringBuilder
    sb.append("flowchart TD\n")
    g.vertexSet().asScala.toSeq.sortBy(vertexIdProvider).foreach { v =>
      val id = sanitize(vertexIdProvider(v))
      val label = vertexLabel(v)
      sb.append(s"""  $id["$label"]\n""")
    }
    g.edgeSet().asScala.foreach { e =>
      val src = sanitize(vertexIdProvider(g.getEdgeSource(e)))
      val tgt = sanitize(vertexIdProvider(g.getEdgeTarget(e)))
      sb.append(s"  $src --> $tgt\n")
    }
    sb.toString
  }

  /** Generates a Mermaid flowchart with subgraphs — one per group key.
    * Used for tasks/plan graphs where each module becomes a subgraph box.
    */
  def generateMermaidWithSubgraphs[V, E](
      g: Graph[V, E],
      groups: Map[String, Seq[V]],
      vertexIdProvider: V => String,
      vertexLabel: V => String
  ): String = {
    val sanitize = (s: String) => s.replaceAll("[^a-zA-Z0-9_]", "_")
    val sb = new StringBuilder
    sb.append("flowchart TD\n")
    groups.toSeq.sortBy(_._1).foreach { case (groupId, vertices) =>
      val sgId = sanitize(groupId)
      sb.append(s"""  subgraph $sgId["$groupId"]\n""")
      vertices.sortBy(vertexIdProvider).foreach { v =>
        val id = sanitize(vertexIdProvider(v))
        val label = vertexLabel(v)
        sb.append(s"""    $id["$label"]\n""")
      }
      sb.append("  end\n")
    }
    g.edgeSet().asScala.foreach { e =>
      val src = sanitize(vertexIdProvider(g.getEdgeSource(e)))
      val tgt = sanitize(vertexIdProvider(g.getEdgeTarget(e)))
      sb.append(s"  $src --> $tgt\n")
    }
    sb.toString
  }
}
