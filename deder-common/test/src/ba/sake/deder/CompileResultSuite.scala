package ba.sake.deder

class CompileResultSuite extends munit.FunSuite {

  test("hash keys on inputsHash, NOT classesDir content (no per-build class-tree hashing)") {
    val a = CompileResult(DederPath("mod-a/classes"), errors = 0, warnings = 0, sourceCount = 3, inputsHash = "H1")
    // Same inputsHash, DIFFERENT classesDir path: must hash identically (classesDir is not hashed).
    val b = a.copy(classesDir = DederPath("totally/different/dir"))
    assertEquals(Hashable[CompileResult].hashStr(a), Hashable[CompileResult].hashStr(b))
  }

  test("hash changes when inputsHash changes (downstream invalidates on real input change)") {
    val a = CompileResult(DederPath("mod-a/classes"), errors = 0, warnings = 0, sourceCount = 3, inputsHash = "H1")
    val b = a.copy(inputsHash = "H2")
    assertNotEquals(Hashable[CompileResult].hashStr(a), Hashable[CompileResult].hashStr(b))
  }

  test("hash distinguishes success from failure for the same inputs (BSP/downstream see the flip)") {
    val ok = CompileResult(DederPath("mod-a/classes"), errors = 0, warnings = 0, sourceCount = 3, inputsHash = "H1")
    val failed = ok.copy(errors = 1)
    assertNotEquals(Hashable[CompileResult].hashStr(ok), Hashable[CompileResult].hashStr(failed))
  }

  test("hash ignores diagnostics (warning-text churn must not invalidate downstream)") {
    val base = CompileResult(DederPath("mod-a/classes"), errors = 0, warnings = 1, sourceCount = 3, inputsHash = "H1")
    val withDiag = base.copy(diagnostics =
      List(FileDiagnostics(DederPath("mod-a/src/X.scala"), Nil))
    )
    assertEquals(Hashable[CompileResult].hashStr(base), Hashable[CompileResult].hashStr(withDiag))
  }
}
