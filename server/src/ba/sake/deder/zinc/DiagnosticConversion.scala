package ba.sake.deder.zinc

import ba.sake.deder.{CompileDiagnostic, CompileRange, CompileSeverity, DederPath}
import xsbti.{Problem, Severity as XSeverity}

object DiagnosticConversion {

  /** Convert one Zinc problem to a deder CompileDiagnostic, applying Zinc's 1-indexed -> 0-indexed
    * line conversion (mirrors the existing BSP inline conversion in DederBspServer).
    */
  def toDiagnostic(p: Problem): CompileDiagnostic = {
    val pos = p.position
    val startLine = pos.startLine().orElse(1) - 1
    val startChar = pos.startColumn().orElse(1)
    val endLine = pos.endLine().orElse(pos.startLine().orElse(1)) - 1
    val endChar = pos.endColumn().orElse(pos.startColumn().orElse(0))
    val severity = p.severity() match {
      case XSeverity.Error => CompileSeverity.Error
      case XSeverity.Warn  => CompileSeverity.Warning
      case XSeverity.Info  => CompileSeverity.Info
    }
    val code = Option(p.category()).filter(_.nonEmpty)
    CompileDiagnostic(CompileRange(startLine, startChar, endLine, endChar), severity, p.message(), code)
  }

  /** Group problems (that have a source file) by source file, converting to DederPath keys.
    * Every file in `allSources` is present as a key; clean files map to Nil.
    * Problems without a source position are dropped here (the live path surfaces those as messages).
    * Problems located OUTSIDE `allSources` are dropped too: after a rename/delete, Zinc's stored
    * analysis can still hold SourceInfos for removed files; publishing them would re-surface
    * stale diagnostics for files that no longer exist.
    */
  def groupByFile(
      problems: Seq[Problem],
      allSources: Seq[os.Path],
      projectRoot: os.Path
  ): Map[DederPath, List[CompileDiagnostic]] = {
    val base: Map[DederPath, List[CompileDiagnostic]] =
      allSources.map(p => DederPath(p.subRelativeTo(projectRoot)) -> List.empty[CompileDiagnostic]).toMap

    val grouped: Map[DederPath, List[CompileDiagnostic]] =
      problems
        .filter(_.position.sourceFile.isPresent)
        .groupBy { p =>
          val f = os.Path(p.position.sourceFile.get.getAbsoluteFile.toPath)
          DederPath(f.subRelativeTo(projectRoot))
        }
        .map { case (k, ps) => k -> ps.map(toDiagnostic).toList }

    val inScope = grouped.filter((k, _) => base.contains(k))
    base ++ inScope
  }
}
