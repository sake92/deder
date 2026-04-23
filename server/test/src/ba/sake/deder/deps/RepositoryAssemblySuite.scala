package ba.sake.deder.deps

import scala.jdk.CollectionConverters.*
import coursierapi.{MavenRepository as CsMavenRepository, Repository as CsRepository}

class RepositoryAssemblySuite extends munit.FunSuite {

  test("no user repos, defaults on → returns empty (Coursier applies defaults)") {
    val out = DependencyResolver.assembleRepositories(Seq.empty, includeDefaultRepos = true)
    assertEquals(out, Seq.empty)
  }

  test("user repos prepended to defaults in declared order") {
    val out = DependencyResolver.assembleRepositories(
      Seq("https://nexus.example.com/a/", "https://nexus.example.com/b/"),
      includeDefaultRepos = true
    )
    val defaults = CsRepository.defaults().asScala.toSeq
    assertEquals(out.size, 2 + defaults.size)
    val first2 = out.take(2).collect { case m: CsMavenRepository => m.getBase }
    assertEquals(first2, Seq("https://nexus.example.com/a/", "https://nexus.example.com/b/"))
    assertEquals(out.drop(2), defaults)
  }

  test("defaults off, user repos present → only user repos") {
    val out = DependencyResolver.assembleRepositories(
      Seq("https://nexus.example.com/a/"),
      includeDefaultRepos = false
    )
    assertEquals(out.size, 1)
    out.head match {
      case m: CsMavenRepository => assertEquals(m.getBase, "https://nexus.example.com/a/")
      case other                => fail(s"expected MavenRepository, got $other")
    }
  }

  test("defaults off, no user repos → throws") {
    interceptMessage[IllegalArgumentException](
      "`includeDefaultRepos = false` requires at least one entry in `repositories`."
    ) {
      DependencyResolver.assembleRepositories(Seq.empty, includeDefaultRepos = false)
    }
  }

  test("malformed URL → throws with useful message") {
    val ex = intercept[IllegalArgumentException] {
      DependencyResolver.assembleRepositories(
        Seq("not a url at all"),
        includeDefaultRepos = true
      )
    }
    assert(ex.getMessage.contains("Invalid repository URL"), s"got: ${ex.getMessage}")
  }
}
