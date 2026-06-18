package ba.sake.deder.compilation

import ba.sake.tupson.JsonRW
import ba.sake.deder.{CompileResult, ModuleFailure, PlainTextWritable, Summarizable}

case class CompilationSummary(
    success: Boolean,
    totalModules: Int,
    totalErrors: Int,
    totalWarnings: Int,
    totalSourceCount: Int,
    totalDurationMillis: Long,
    modules: Map[String, CompileModuleInfo]
) derives JsonRW

case class CompileModuleInfo(
    errors: Int,
    warnings: Int,
    sourceCount: Int,
    failureReason: Option[String] = None
) derives JsonRW {
  def success: Boolean = errors == 0 && failureReason.isEmpty
}

object CompilationSummary {

  given PlainTextWritable[CompilationSummary] with {
    def write(summary: CompilationSummary): String = {
      val statusIcon = if summary.success then "✅ COMPILED" else "🔴 COMPILE FAILED"
      val moduleStr = if summary.totalModules == 1 then "1 module" else s"${summary.totalModules} modules"
      val errorsStr = s"Errors: ${summary.totalErrors}"
      val warningsStr = s"Warnings: ${summary.totalWarnings}"
      val summaryLine = s"$statusIcon  $moduleStr  │  $errorsStr  │  $warningsStr"
      val separator = "═" * summaryLine.length

      val sortedModules = summary.modules.toSeq.sortBy(_._1)
      val successfulModules = sortedModules.filter(_._2.success)
      val failedModules = sortedModules.filter(m => !m._2.success && m._2.failureReason.isEmpty)
      val skippedModules = sortedModules.filter(_._2.failureReason.isDefined)

      val successfulSection = successfulModules.map { case (moduleId, info) =>
        val warningsStr = if info.warnings > 0 then s" (${info.warnings} warnings)" else ""
        s"  ✅ COMPILED $moduleId$warningsStr"
      }.mkString("\n")

      val failedSection = failedModules.map { case (moduleId, info) =>
        s"  🔴 FAILED $moduleId (${info.errors} errors, ${info.warnings} warnings)"
      }.mkString("\n")

      val skippedSection = skippedModules.map { case (moduleId, info) =>
        s"  ⏭️  SKIPPED $moduleId (${info.failureReason.getOrElse("unknown")})"
      }.mkString("\n")

      Seq(
        separator,
        summaryLine,
        successfulSection,
        failedSection,
        skippedSection,
        separator
      ).filter(_.trim.nonEmpty).mkString("\n")
    }
  }

  given Summarizable[CompileResult, CompilationSummary] with
    def summarize(resultsMap: Seq[(String, CompileResult)], failures: Seq[ModuleFailure], totalDuration: java.time.Duration): CompilationSummary = {
      val allResults = resultsMap.map(_._2)
      val successModules = resultsMap.map { case (id, cr) =>
        id -> CompileModuleInfo(cr.errors, cr.warnings, cr.sourceCount)
      }.toMap
      val failureModules = failures.map { f =>
        f.moduleId -> CompileModuleInfo(
          errors = 0, warnings = 0, sourceCount = 0,
          failureReason = Some(f.error)
        )
      }.toMap
      val allModules = successModules ++ failureModules
      CompilationSummary(
        success = allResults.forall(_.success) && failures.isEmpty,
        totalModules = successModules.size + failureModules.size,
        totalErrors = allResults.map(_.errors).sum,
        totalWarnings = allResults.map(_.warnings).sum,
        totalSourceCount = allResults.map(_.sourceCount).sum,
        totalDurationMillis = totalDuration.toMillis(),
        modules = allModules
      )
    }
}
