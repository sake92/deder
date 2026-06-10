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
      val totalModules = mmr.results.size + mmr.failures.size
      val hasFailures = mmr.failures.nonEmpty
      val moduleWord = if totalModules == 1 then "module" else "modules"
      val header = if hasFailures then s"🔴 FAIL  $totalModules $moduleWord" else s"✅ OK  $totalModules $moduleWord"

      val successLines = mmr.results.toSeq
        .sortBy(_._1)
        .map { (moduleId, _) => s"  ✅ $moduleId" }

      val failureLines = mmr.failures.sortBy(_.moduleId).map { f =>
        val cause = f.causedBy.map(m => s" (caused by failure in $m)").getOrElse("")
        s"  🔴 ${f.moduleId}: ${f.error}$cause"
      }

      val allLines = Seq(header) ++ successLines ++ failureLines
      val sepWidth = allLines.map(_.length).max
      val separator = "═" * sepWidth

      (Seq(separator) ++ allLines ++ Seq(separator)).mkString("\n")

  given [T: JsonRW: PlainTextWritable: MermaidWritable: DotWritable]: Summarizable[T, MultiModuleResults[T]] with
    def summarize(results: Seq[(String, T)], failures: Seq[ModuleFailure], totalDuration: Duration): MultiModuleResults[T] =
      MultiModuleResults(results.toMap, failures, totalDuration.toMillis())
