package ba.sake.deder.publish

import ba.sake.deder.config.DederProject.PomSettings as PklPomSettings
import ba.sake.deder.config.DederProject.PomLicense
import ba.sake.deder.config.DederProject.PomDeveloper
import ba.sake.deder.config.DederProject.PomScm

class PublishValidatorSuite extends munit.FunSuite {

  private def validPomSettings(): PklPomSettings =
    new PklPomSettings(
      "com.example",
      "test-lib",
      "1.0.0",
      "Test Library",
      "A test library",
      "https://example.com",
      java.util.List.of(new PomLicense("MIT", "https://opensource.org/licenses/MIT")),
      java.util.List.of(new PomDeveloper("dev1", "Developer One", "dev1@example.com")),
      new PomScm("https://github.com/example/repo", "scm:git:github.com:example/repo.git", null, null)
    )

  test("valid pomSettings should not throw") {
    PublishValidator.validateForSonatypeCentral("my-module", validPomSettings(), "1.0.0")
  }

  test("null pomSettings") {
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", null, "1.0.0")
    }
    assert(ex.getMessage.contains("pomSettings is required"))
  }

  test("empty groupId") {
    val pom = new PklPomSettings(
      "", "test-lib", "1.0.0", "Test Library", "A test library",
      "https://example.com",
      java.util.List.of(new PomLicense("MIT", "https://opensource.org/licenses/MIT")),
      java.util.List.of(new PomDeveloper("dev1", "Developer One", "dev1@example.com")),
      new PomScm("https://github.com/example/repo", "scm:git:github.com:example/repo.git", null, null)
    )
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    assert(ex.getMessage.contains("groupId"))
  }

  test("null resolved version") {
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", validPomSettings(), null)
    }
    assert(ex.getMessage.contains("version"))
  }

  test("empty resolved version") {
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", validPomSettings(), "")
    }
    assert(ex.getMessage.contains("version"))
  }

  test("version ending with -SNAPSHOT") {
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", validPomSettings(), "1.0.0-SNAPSHOT")
    }
    assert(ex.getMessage.contains("version"))
    assert(ex.getMessage.contains("SNAPSHOT"))
  }

  test("missing description (null)") {
    val pom = validPomSettings().withDescription(null)
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    assert(ex.getMessage.contains("description"))
  }

  test("empty description") {
    val pom = validPomSettings().withDescription("")
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    assert(ex.getMessage.contains("description"))
  }

  test("missing url (null)") {
    val pom = validPomSettings().withUrl(null)
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    assert(ex.getMessage.contains("url"))
  }

  test("empty url") {
    val pom = validPomSettings().withUrl("")
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    assert(ex.getMessage.contains("url"))
  }

  test("null licenses list") {
    val pom = validPomSettings().withLicenses(null)
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    assert(ex.getMessage.contains("licenses"))
  }

  test("empty licenses list") {
    val pom = validPomSettings().withLicenses(java.util.List.of())
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    assert(ex.getMessage.contains("licenses"))
  }

  test("null developers list") {
    val pom = validPomSettings().withDevelopers(null)
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    assert(ex.getMessage.contains("developers"))
  }

  test("empty developers list") {
    val pom = validPomSettings().withDevelopers(java.util.List.of())
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    assert(ex.getMessage.contains("developers"))
  }

  test("null scm") {
    val pom = validPomSettings().withScm(null)
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    assert(ex.getMessage.contains("scm.url"))
    assert(ex.getMessage.contains("scm.connection"))
  }

  test("null scm.url only") {
    val pom = validPomSettings().withScm(
      new PomScm(null, "scm:git:github.com:example/repo.git", null, null)
    )
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    assert(ex.getMessage.contains("scm.url"))
    assert(!ex.getMessage.contains("scm.connection"))
  }

  test("null scm.connection only") {
    val pom = validPomSettings().withScm(
      new PomScm("https://github.com/example/repo", null, null, null)
    )
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    assert(ex.getMessage.contains("scm.connection"))
    assert(!ex.getMessage.contains("scm.url"))
  }

  test("multiple missing fields") {
    val pom = new PklPomSettings(
      "com.example", "test-lib", null, "Test Library",
      null, null,
      java.util.List.of(),
      java.util.List.of(),
      null
    )
    val ex = intercept[RuntimeException] {
      PublishValidator.validateForSonatypeCentral("my-module", pom, "1.0.0")
    }
    val msg = ex.getMessage
    assert(msg.contains("description"))
    assert(msg.contains("url"))
    assert(msg.contains("licenses"))
    assert(msg.contains("developers"))
    assert(msg.contains("scm.url"))
    assert(msg.contains("scm.connection"))
  }
}
