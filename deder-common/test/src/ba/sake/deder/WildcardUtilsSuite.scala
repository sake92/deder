package ba.sake.deder

class WildcardUtilsSuite extends munit.FunSuite {

  val entries = List("common", "backend", "frontend", "uber", "uber-test")

  test("getMatches should match entries to selector") {
    assertEquals(WildcardUtils.getMatches(entries, "common"), Seq("common"))
    assertEquals(WildcardUtils.getMatches(entries, "com%"), Seq("common"))
    assertEquals(WildcardUtils.getMatches(entries, "%c%"), Seq("common", "backend"))
    assertEquals(WildcardUtils.getMatches(entries, "%test"), Seq("uber-test"))
    // avoid duplicates
    assertEquals(WildcardUtils.getMatches(entries, Seq("%test", "%-test")), Seq("uber-test"))
  }

  test("getMatches with Seq should support negation (~) prefix") {
    assertEquals(
      WildcardUtils.getMatches(List("a", "ab", "abc", "bc"), Seq("a%", "~ab%")).toSet,
      Set("a")
    )
  }

  test("getMatches with Seq should support only negations") {
    assertEquals(
      WildcardUtils.getMatches(List("a", "ab", "abc", "bc"), Seq("~ab", "~bc")).toSet,
      Set("a", "abc")
    )
  }

  test("getMatches with Seq should support no includes only negations") {
    assertEquals(
      WildcardUtils.getMatches(List("a", "ab", "bc"), Seq("~a")).toSet,
      Set("ab", "bc")
    )
  }

  test("getMatches with Seq should return empty when all excluded") {
    assertEquals(
      WildcardUtils.getMatches(List("a", "ab", "abc"), Seq("ab%", "~ab%")).toSet,
      Set.empty[String]
    )
  }

  test("getMatches with Seq should preserve existing union behavior") {
    assertEquals(
      WildcardUtils.getMatches(List("a", "ab", "abc", "bc"), Seq("a%", "ab%")).toSet,
      Set("a", "ab", "abc")
    )
  }

  test("getMatchesOrRecommendations should use negation and return recommendations when empty") {
    val result = WildcardUtils.getMatchesOrRecommendations(entries, Seq("uber%", "~uber%"))
    assert(result.isLeft, "expected Left with recommendations but got Right")
    assert(result.left.getOrElse(Seq.empty).nonEmpty)
  }

  test("getMatchesOrRecommendations should work with negation producing non-empty result") {
    val result = WildcardUtils.getMatchesOrRecommendations(entries, Seq("uber%", "~uber-test"))
    assert(result.isRight)
    assertEquals(result.getOrElse(Seq.empty).toSet, Set("uber"))
  }
}
