package ba.sake.deder.compilation

import ba.sake.tupson.JsonRW
import ba.sake.deder.{CompileResult, PlainTextWritable, Summarizable}

case class CompilationSummary(
    success: Boolean,
    totalModules: Int,
    totalErrors: Int,
    totalWarnings: Int,
    totalSourceCount: Int,
    modules: Map[String, CompileModuleInfo]
) derives JsonRW

case class CompileModuleInfo(
    errors: Int,
    warnings: Int,
    sourceCount: Int
) derives JsonRW {
  def success: Boolean = errors == 0
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
      val failedModules = sortedModules.filter(!_._2.success)

      val successfulSection = successfulModules.map { case (moduleId, info) =>
        val warningsStr = if info.warnings > 0 then s" (${info.warnings} warnings)" else ""
        s"  ✅ COMPILED $moduleId$warningsStr"
      }.mkString("\n")

      val failedSection = failedModules.map { case (moduleId, info) =>
        s"  🔴 FAIL $moduleId (${info.errors} errors, ${info.warnings} warnings)"
      }.mkString("\n")

      Seq(
        separator,
        summaryLine,
        successfulSection,
        failedSection,
        separator
      ).filter(_.trim.nonEmpty).mkString("\n")
    }
  }

  given Summarizable[CompileResult, CompilationSummary] with
    def summarize(resultsMap: Seq[(String, CompileResult)]): CompilationSummary = {
      val allResults = resultsMap.map(_._2)
      val modules = resultsMap.map { case (id, cr) =>
        id -> CompileModuleInfo(cr.errors, cr.warnings, cr.sourceCount)
      }.toMap
      CompilationSummary(
        success = allResults.forall(_.success),
        totalModules = resultsMap.size,
        totalErrors = allResults.map(_.errors).sum,
        totalWarnings = allResults.map(_.warnings).sum,
        totalSourceCount = allResults.map(_.sourceCount).sum,
        modules = modules
      )
    }
}
