package ba.sake.deder

import ba.sake.tupson.JsonRW

/** Cross-module aggregation: given per-module results of type T, produce a summary of type S. */
trait Summarizable[T, S](using val sJsonRw: JsonRW[S], val sPlainWritable: PlainTextWritable[S]):
  def summarize(results: Seq[(String, T)]): S

/** Default result wrapper for multi-module task execution. */
case class MultiModuleResults[T](results: Map[String, T]) derives JsonRW

object MultiModuleResults:
  given [T: PlainTextWritable]: PlainTextWritable[MultiModuleResults[T]] with
    def write(mmr: MultiModuleResults[T]): String =
      mmr.results.toSeq.sortBy(_._1).map { (moduleId, result) =>
        val body = summon[PlainTextWritable[T]].write(result)
        if body.nonEmpty then s"## ${moduleId}\n${body}"
        else moduleId
      }.mkString("\n\n")

  given [T: JsonRW: PlainTextWritable]: Summarizable[T, MultiModuleResults[T]] with
    def summarize(results: Seq[(String, T)]): MultiModuleResults[T] =
      MultiModuleResults(results.toMap)
