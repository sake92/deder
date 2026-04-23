package ba.sake.deder.deps

class DependencyResolverCacheSuite extends munit.FunSuite {

  test("depsCacheKey is stable for same deps in different order") {
    val dep1 = coursierapi.Dependency.of("org.scala-lang", "scala-library", "2.13.12")
    val dep2 = coursierapi.Dependency.of("com.lihaoyi", "os-lib_2.13", "0.9.1")

    val key1 = DependencyResolver.depsCacheKey(Seq(dep1, dep2))
    val key2 = DependencyResolver.depsCacheKey(Seq(dep2, dep1))

    assertEquals(key1, key2, "Cache key should be order-independent (sorted)")
  }

  test("fetchFiles returns empty for empty deps") {
    val resolver = new DependencyResolver(Seq.empty)
    val result = resolver.fetchFiles(Seq.empty)
    assertEquals(result, Seq.empty)
  }
}
