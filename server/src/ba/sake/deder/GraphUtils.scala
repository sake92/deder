package ba.sake.deder

import java.io.StringWriter

import scala.jdk.FunctionConverters.*
import scala.jdk.CollectionConverters.*
import org.jgrapht.Graph
import org.jgrapht.alg.cycle.CycleDetector
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter

object GraphUtils {

  def checkNoCycles[V, E](g: Graph[V, E], getName: V => String): Unit = {
    val cycleDetector = new CycleDetector[V, E](g)
    val cycles = cycleDetector.findCycles().asScala
    if cycles.nonEmpty then throw DederException(s"Cycle detected: ${cycles.map(getName).mkString("->")}")
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

  def generateAscii[V, E](g: Graph[V, E], vertexLabelProvider: V => String): String = {
    import org.scalameta.ascii.graph.Graph as AsciiGraph
    import org.scalameta.ascii.layout.GraphLayout
    val vertices = g.vertexSet().asScala.toSet.map(vertexLabelProvider)
    val edges = g
      .edgeSet()
      .asScala
      .toList
      .map(e => (vertexLabelProvider(g.getEdgeSource(e)), vertexLabelProvider(g.getEdgeTarget(e))))
    val graph = AsciiGraph(vertices = vertices, edges = edges)
    GraphLayout.renderGraph(graph)
  }
}
