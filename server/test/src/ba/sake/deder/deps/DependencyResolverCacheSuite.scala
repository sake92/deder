package ba.sake.deder.deps

import ba.sake.deder.deps.Dependency

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

  test("buildDepTree detects requested version clashes even after resolution picks one winner") {
    val resolver = new DependencyResolver(Seq.empty)
    val deps = Seq(
      Dependency.make("org.jsoup:jsoup:1.21.1", "3.7.4"),
      Dependency.make("org.jsoup:jsoup:1.16.2", "3.7.4"),
      Dependency.make("org.jsoup:jsoup:1.1.2", "3.7.4")
    )

    val tree = resolver.buildDepTree(deps)
    val jsoupConflict = tree.conflicts.find(_.coordinate == "org.jsoup:jsoup")

    assert(jsoupConflict.nonEmpty)
    assert(jsoupConflict.get.isConflict)
    assertEquals(jsoupConflict.get.requestedVersions.keySet, Set("1.21.1", "1.16.2", "1.1.2"))
    assertEquals(jsoupConflict.get.resolvedVersion, "1.21.1")
  }
}
