package ba.sake.deder.cli

import ba.sake.deder.{DotWritable, MermaidWritable, GraphUtils}
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge

object GraphRenderInstances:
  given DotWritable[Graph[?, DefaultEdge]] with
    def write(g: Graph[?, DefaultEdge]): String =
      val g2 = g.asInstanceOf[Graph[Any, DefaultEdge]]
      GraphUtils.generateDOT(g2, v => v.toString, v => Map("label" -> v.toString))

  given MermaidWritable[Graph[?, DefaultEdge]] with
    def write(g: Graph[?, DefaultEdge]): String =
      val g2 = g.asInstanceOf[Graph[Any, DefaultEdge]]
      GraphUtils.generateMermaid(g2, v => v.toString, v => v.toString)
