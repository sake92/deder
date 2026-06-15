package ba.sake.deder

import ba.sake.tupson.JsonRW

case class CompileResult(
    classesDir: DederPath,
    errors: Int,
    warnings: Int,
    sourceCount: Int,
    diagnostics: List[FileDiagnostics] = Nil,
    // The producing compile task's own inputsHash. Used as the cache "output token" so downstream
    // tasks key on "compiled with inputs X" instead of re-hashing the (huge, non-deterministic)
    // class-file tree. Empty when unknown (e.g. BSP-synthesized failure results).
    inputsHash: String = ""
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
  // Output token: hash the PRODUCER'S inputsHash, not the class-file tree. The inputsHash already
  // captures every input to compile (sources, options, scalaVersion, compiler/dependency jars, and
  // — transitively — upstream modules' compile outputs), so for deterministic compilation it
  // uniquely identifies the output. This avoids content-hashing the (potentially huge, e.g. 14k
  // files) and non-deterministic classes dir on every cache-miss — the edit→compile hot path.
  // `classesDir` is deliberately NOT hashed; `diagnostics` is EXCLUDED (a BSP replay artifact, not
  // a build output — hashing it would churn jar/publish caches on a warning text change).
  given Hashable[CompileResult] with {
    def hashStr(value: CompileResult): String =
      s"${value.inputsHash}-${value.errors}-${value.warnings}-${value.sourceCount}".hashStr
  }
}
