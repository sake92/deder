package ba.sake.deder

import java.io.StringWriter

import scala.jdk.FunctionConverters.*
import scala.jdk.CollectionConverters.*
import org.jgrapht.Graph
import org.jgrapht.alg.cycle.CycleDetector
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.EdgeReversedGraph
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter

object GraphUtils {

  def checkNoCycles[V, E](g: Graph[V, E], getName: V => String): Unit = {
    val cycleDetector = new CycleDetector[V, E](g)
    val cycles = cycleDetector.findCycles().asScala
    if cycles.nonEmpty then throw DederException(s"Cycle detected: ${cycles.map(getName).mkString("->")}")
  }

  /** Projects a graph to only the vertices satisfying `isPublic`, bridging edges
   *  over internal (non-public) vertices. For each public vertex `u`, follows its
   *  outgoing edges: if a neighbor is public it is included directly; if internal
   *  it is skipped and the BFS continues until the first public descendants are
   *  found. The result is a new `SimpleDirectedGraph` with only public vertices
   *  and the bridged edges.
   */
  def projectPublic[V](
      g: Graph[V, DefaultEdge],
      isPublic: V => Boolean
  ): SimpleDirectedGraph[V, DefaultEdge] = {
    val projected = new SimpleDirectedGraph[V, DefaultEdge](classOf[DefaultEdge])
    g.vertexSet().asScala.filter(isPublic).foreach(projected.addVertex)
    g.vertexSet().asScala.filter(isPublic).foreach { u =>
      firstPublicReachable(g, u, isPublic).foreach { v =>
        projected.addEdge(u, v)
      }
    }
    projected
  }

  /** From a public vertex `u`, find all "first reachable" public vertices by
   *  following outgoing edges and skipping internal vertices.
   */
  private def firstPublicReachable[V](
      g: Graph[V, DefaultEdge],
      u: V,
      isPublic: V => Boolean
  ): Set[V] = {
    val result = scala.collection.mutable.Set[V]()
    val visited = scala.collection.mutable.Set[V]()
    val queue = scala.collection.mutable.Queue[V]()
    g.outgoingEdgesOf(u).asScala.foreach { e =>
      val neighbor = g.getEdgeTarget(e)
      if isPublic(neighbor) then result.add(neighbor)
      else if visited.add(neighbor) then queue.enqueue(neighbor)
    }
    while queue.nonEmpty do {
      val current = queue.dequeue()
      g.outgoingEdgesOf(current).asScala.foreach { e =>
        val neighbor = g.getEdgeTarget(e)
        if isPublic(neighbor) then result.add(neighbor)
        else if visited.add(neighbor) then queue.enqueue(neighbor)
      }
    }
    result.toSet
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
    collected.addAll(collectReachableVertices(g, focalVertices, depthDown))

    // BFS upstream: follow reversed edges (focal → dependents)
    val reversed = new EdgeReversedGraph[V, E](g)
    collected.addAll(collectReachableVertices(reversed, focalVertices, depthUp))

    new AsSubgraph(g, collected.asJava)
  }

  private def collectReachableVertices[V, E](
      g: Graph[V, E],
      startVertices: Set[V],
      maxDepth: Int
  ): Set[V] = {
    val visited = scala.collection.mutable.Set[V]()
    val queue = scala.collection.mutable.Queue[(V, Int)]()
    startVertices.foreach { v =>
      visited.add(v)
      queue.enqueue((v, 0))
    }
    while queue.nonEmpty do
      val (current, depth) = queue.dequeue()
      if depth < maxDepth then
        g.outgoingEdgesOf(current).asScala.foreach { e =>
          val next = g.getEdgeTarget(e)
          if visited.add(next) then queue.enqueue((next, depth + 1))
        }
    visited.toSet
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
      vertexLabel: V => String,
      extraLines: Seq[String] = Seq.empty,
      vertexCssClassProvider: V => Option[String] = (_: V) => None,
      classDefs: Map[String, String] = Map.empty
  ): String = {
    val sanitize = (s: String) => s.replaceAll("[^a-zA-Z0-9_]", "_")
    val classVertexIds = groups.toSeq
      .sortBy(_._1)
      .flatMap { case (_, vertices) => vertices.sortBy(vertexIdProvider) }
      .flatMap(v => vertexCssClassProvider(v).map(_ -> sanitize(vertexIdProvider(v))))
      .groupMap(_._1)(_._2)

    val sb = new StringBuilder
    sb.append("flowchart TD\n")
    extraLines.foreach(line => sb.append(s"  $line\n"))
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
    classDefs.toSeq.sortBy(_._1).foreach { case (cls, style) =>
      sb.append(s"  classDef $cls $style\n")
    }
    classVertexIds.toSeq.sortBy(_._1).foreach { case (cls, vertexIds) =>
      sb.append(s"  class ${vertexIds.mkString(",")} $cls\n")
    }
    sb.toString
  }
}
