package ba.sake.deder

class WildcardUtilsSuite extends munit.FunSuite {

  test("getMatches should match entries to selector") {
    val entries = List("common", "backend", "frontend", "uber", "uber-test")
    assertEquals(WildcardUtils.getMatches(entries, "common"), Seq("common"))
    assertEquals(WildcardUtils.getMatches(entries, "com%"), Seq("common"))
    assertEquals(WildcardUtils.getMatches(entries, "%c%"), Seq("common", "backend"))
    assertEquals(WildcardUtils.getMatches(entries, "%test"), Seq("uber-test"))
    // avoid duplicates
    assertEquals(WildcardUtils.getMatches(entries, Seq("%test", "%-test")), Seq("uber-test"))
  }
}
