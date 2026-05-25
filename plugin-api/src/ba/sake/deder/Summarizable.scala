package ba.sake.deder

import ba.sake.tupson.JsonRW

/** Cross-module aggregation: given per-module results of type T, produce a summary of type S. */
trait Summarizable[T, S](using
    val jsonRW: JsonRW[S],
    val plainTextW: PlainTextWritable[S],
    val mermaidW: MermaidWritable[S],
    val dotW: DotWritable[S]
):
  def summarize(results: Seq[(String, T)]): S

/** Default result wrapper for multi-module task execution. */
case class MultiModuleResults[T](results: Map[String, T]) derives JsonRW

object MultiModuleResults:
  given [T](using ptw: PlainTextWritable[T]): PlainTextWritable[MultiModuleResults[T]] with
    def write(mmr: MultiModuleResults[T]): String =
      mmr.results.toSeq
        .sortBy(_._1)
        .map { (moduleId, result) =>
          val body = ptw.write(result)
          Seq(s"[${moduleId}]", body).filter(_.trim.nonEmpty).mkString("\n")

        }
        .mkString("\n")

  given [T: JsonRW: PlainTextWritable: MermaidWritable: DotWritable]: Summarizable[T, MultiModuleResults[T]] with
    def summarize(results: Seq[(String, T)]): MultiModuleResults[T] =
      MultiModuleResults(results.toMap)
