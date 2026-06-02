package ba.sake.deder

import java.time.Duration
import ba.sake.tupson.JsonRW

case class ModuleFailure(moduleId: String, error: String, causedBy: Option[String]) derives JsonRW

/** Cross-module aggregation: given per-module results of type T, produce a summary of type S. */
trait Summarizable[T, S](using
    val jsonRW: JsonRW[S],
    val plainTextW: PlainTextWritable[S],
    val mermaidW: MermaidWritable[S],
    val dotW: DotWritable[S]
):
  def summarize(results: Seq[(String, T)], failures: Seq[ModuleFailure], totalDuration: Duration): S

/** Default result wrapper for multi-module task execution. */
case class MultiModuleResults[T](results: Map[String, T], failures: Seq[ModuleFailure], totalDurationMillis: Long) derives JsonRW

object MultiModuleResults:
  given [T](using ptw: PlainTextWritable[T]): PlainTextWritable[MultiModuleResults[T]] with
    def write(mmr: MultiModuleResults[T]): String =
      val successSection = mmr.results.toSeq
        .sortBy(_._1)
        .map { (moduleId, result) =>
          val body = ptw.write(result)
          Seq(s"[${moduleId}]", body).filter(_.trim.nonEmpty).mkString("\n")
        }
        .mkString("\n")

      val failureSection = mmr.failures.sortBy(_.moduleId).map { f =>
        val cause = f.causedBy.map(m => s" (caused by failure in $m)").getOrElse("")
        s"  🔴 ${f.moduleId}: ${f.error}$cause"
      }.mkString("\n")

      val failureBlock = if failureSection.nonEmpty then s"\nFailed modules:\n$failureSection" else ""
      Seq(successSection, failureBlock).filter(_.nonEmpty).mkString("\n")

  given [T: JsonRW: PlainTextWritable: MermaidWritable: DotWritable]: Summarizable[T, MultiModuleResults[T]] with
    def summarize(results: Seq[(String, T)], failures: Seq[ModuleFailure], totalDuration: Duration): MultiModuleResults[T] =
      MultiModuleResults(results.toMap, failures, totalDuration.toMillis())
