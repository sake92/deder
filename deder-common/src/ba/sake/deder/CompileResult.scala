package ba.sake.deder

import ba.sake.tupson.JsonRW

case class CompileResult(
    classesDir: DederPath,
    errors: Int,
    warnings: Int,
    sourceCount: Int,
    diagnostics: List[FileDiagnostics] = Nil
) derives JsonRW {
  def success: Boolean = errors == 0
}

// One source file's diagnostics. A list (not a Map) because tupson cannot derive JsonRW
// for a Map with a non-String key (DederPath). Clean files are present with an empty list.
case class FileDiagnostics(file: DederPath, diagnostics: List[CompileDiagnostic]) derives JsonRW

// `Compile*` prefix avoids clashing with bsp4j's Diagnostic/Range/DiagnosticSeverity, so the
// BSP layer can import both packages without qualification gymnastics.
case class CompileDiagnostic(
    range: CompileRange,
    severity: CompileSeverity,
    message: String,
    code: Option[String]
) derives JsonRW

case class CompileRange(startLine: Int, startChar: Int, endLine: Int, endChar: Int) derives JsonRW

enum CompileSeverity derives JsonRW {
  case Error, Warning, Info, Hint
}

object CompileResult {
  // Custom Hashable that includes actual class file content hash, not just the path string.
  // `diagnostics` is intentionally EXCLUDED: it is a BSP replay artifact, not a build output.
  // Hashing it would churn downstream caches (jar, publishArtifacts) when only a warning
  // message text changes.
  given Hashable[CompileResult] with {
    def hashStr(value: CompileResult): String =
      val classesHash = Hashable[DederPath].hashStr(value.classesDir)
      val combined = s"${classesHash}-${value.errors}-${value.warnings}-${value.sourceCount}"
      combined.hashStr
  }
}
