package ba.sake.deder.publish

class GitSemVerSuite extends munit.FunSuite {

  test("parseDescribeOutput returns version when exactly on tag") {
    assertEquals(GitSemVer.parseDescribeOutput("v1.2.3"), "1.2.3")
  }

  test("parseDescribeOutput returns version without v prefix") {
    assertEquals(GitSemVer.parseDescribeOutput("v0.0.1"), "0.0.1")
  }

  test("parseDescribeOutput returns bumped SNAPSHOT when commits ahead") {
    assertEquals(GitSemVer.parseDescribeOutput("v1.2.3-5-gabcdef"), "1.2.4-SNAPSHOT")
  }

  test("parseDescribeOutput returns bumped SNAPSHOT for larger commit counts") {
    assertEquals(GitSemVer.parseDescribeOutput("v0.5.0-123-g1234567"), "0.5.1-SNAPSHOT")
  }

  test("parseDescribeOutput returns bumped SNAPSHOT when commits ahead and dirty") {
    assertEquals(GitSemVer.parseDescribeOutput("v1.2.3-5-gabcdef-dirty"), "1.2.4-SNAPSHOT")
  }

  test("parseDescribeOutput returns bumped SNAPSHOT when exactly on tag but dirty") {
    assertEquals(GitSemVer.parseDescribeOutput("v1.2.3-dirty"), "1.2.4-SNAPSHOT")
  }

  test("parseDescribeOutput returns bumped SNAPSHOT for dirty without v prefix") {
    assertEquals(GitSemVer.parseDescribeOutput("1.0.0-dirty"), "1.0.1-SNAPSHOT")
  }

  test("parseDescribeOutput preserves pre-release suffix on exact tag") {
    assertEquals(GitSemVer.parseDescribeOutput("v1.0.0-RC1"), "1.0.0-RC1")
  }

  test("parseDescribeOutput preserves pre-release suffix with alpha") {
    assertEquals(GitSemVer.parseDescribeOutput("v2.0.0-alpha.1"), "2.0.0-alpha.1")
  }

  test("parseDescribeOutput returns default for non-semver tag") {
    assertEquals(GitSemVer.parseDescribeOutput("some-random-tag"), GitSemVer.DefaultVersion)
  }

  test("parseDescribeOutput returns default for empty string") {
    assertEquals(GitSemVer.parseDescribeOutput(""), GitSemVer.DefaultVersion)
  }

  test("parseDescribeOutput handles zero patch version ahead") {
    assertEquals(GitSemVer.parseDescribeOutput("v1.0.0-1-g1234567"), "1.0.1-SNAPSHOT")
  }

  test("parseDescribeOutput handles major-only bumps") {
    assertEquals(GitSemVer.parseDescribeOutput("v2.0.0"), "2.0.0")
  }

  test("detectVersion returns version from git in a real git repo") {
    // This test will work in the deder project itself which is a git repo
    val version = GitSemVer.detectVersion(os.pwd)
    assert(version.nonEmpty, "Version should not be empty")
    assert(
      version.matches("""\d+\.\d+\.\d+.*"""),
      s"Version '$version' should match semver pattern"
    )
  }

  test("detectVersion returns default for non-git directory") {
    val tmpDir = os.temp.dir()
    try {
      val version = GitSemVer.detectVersion(tmpDir)
      assertEquals(version, GitSemVer.DefaultVersion)
    } finally {
      os.remove.all(tmpDir)
    }
  }
}

