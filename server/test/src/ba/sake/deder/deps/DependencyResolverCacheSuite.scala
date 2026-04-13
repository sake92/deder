package ba.sake.deder.deps

class DependencyResolverCacheSuite extends munit.FunSuite {

  test("depsCacheKey is stable for same deps in different order") {
    // Access private method via reflection
    val method = DependencyResolver.getClass.getDeclaredMethod(
      "depsCacheKey",
      classOf[Seq[coursierapi.Dependency]]
    )
    method.setAccessible(true)

    val dep1 = coursierapi.Dependency.of("org.scala-lang", "scala-library", "2.13.12")
    val dep2 = coursierapi.Dependency.of("com.lihaoyi", "os-lib_2.13", "0.9.1")

    val key1 = method.invoke(DependencyResolver, Seq(dep1, dep2)).asInstanceOf[String]
    val key2 = method.invoke(DependencyResolver, Seq(dep2, dep1)).asInstanceOf[String]

    assertEquals(key1, key2, "Cache key should be order-independent (sorted)")
  }

  test("fetchFiles returns empty for empty deps") {
    val result = DependencyResolver.fetchFiles(Seq.empty)
    assertEquals(result, Seq.empty)
  }
}
