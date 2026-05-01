package ba.sake.deder.publish

import ba.sake.deder.config.DederProject.*
import ba.sake.deder.config.DederCredentials.*

class CredentialsResolverSuite extends munit.FunSuite {

  // Helper to create a SonatypeCentralRepo with given id
  private def sonatypeCentralRepo(id: String = "sonatype-central") =
    new SonatypeCentralRepo(id)

  private def sonatypeSnapshotRepo(id: String = "sonatype-central") =
    new SonatypeSnapshotRepo(id)

  private def mavenRepo(id: String = "my-nexus", url: String = "https://nexus.example.com/releases") =
    new MavenRepo(id, url)

  private def centralCreds(): SonatypeCentralCredentials =
    new SonatypeCentralCredentials("pkl-user", "pkl-pass", "pkl-secret", "pkl-passphrase")

  private def snapshotCreds(): SonatypeSnapshotCredentials =
    new SonatypeSnapshotCredentials("pkl-user", "pkl-pass")

  private def basicCreds(): BasicAuthCredentials =
    new BasicAuthCredentials("pkl-user", "pkl-pass")

  // Helper to create DederCredentials map for tests
  private def makeCredentials(entries: java.util.Map[String, RepoCredentials]): DederCredentials = {
    new DederCredentials(entries)
  }

  test("client env overrides Pkl value") {
    val creds = makeCredentials(new java.util.HashMap[String, RepoCredentials] {
      put("sonatype-central", centralCreds())
    })
    val repo = sonatypeCentralRepo()
    val clientEnv = Map("DEDER_SONATYPE_CENTRAL_USERNAME" -> "env-user")
    val sysEnv = Map.empty[String, String]

    val result = CredentialsResolver.resolve(repo, Some(creds), clientEnv, sysEnv)
    assertEquals(result.asInstanceOf[SonatypeCentralCredentials].username, "env-user")
    assertEquals(result.asInstanceOf[SonatypeCentralCredentials].password, "pkl-pass")
  }

  test("system env overrides Pkl value") {
    val creds = makeCredentials(new java.util.HashMap[String, RepoCredentials] {
      put("sonatype-central", centralCreds())
    })
    val repo = sonatypeCentralRepo()
    val clientEnv = Map.empty[String, String]
    val sysEnv = Map("DEDER_SONATYPE_CENTRAL_USERNAME" -> "sys-user")

    val result = CredentialsResolver.resolve(repo, Some(creds), clientEnv, sysEnv)
    assertEquals(result.asInstanceOf[SonatypeCentralCredentials].username, "sys-user")
  }

  test("client env overrides system env") {
    val creds = makeCredentials(new java.util.HashMap[String, RepoCredentials] {
      put("sonatype-central", centralCreds())
    })
    val repo = sonatypeCentralRepo()
    val clientEnv = Map("DEDER_SONATYPE_CENTRAL_USERNAME" -> "client-user")
    val sysEnv = Map("DEDER_SONATYPE_CENTRAL_USERNAME" -> "sys-user")

    val result = CredentialsResolver.resolve(repo, Some(creds), clientEnv, sysEnv)
    assertEquals(result.asInstanceOf[SonatypeCentralCredentials].username, "client-user")
  }

  test("Pkl fallback when no env vars") {
    val creds = makeCredentials(new java.util.HashMap[String, RepoCredentials] {
      put("sonatype-central", centralCreds())
    })
    val repo = sonatypeCentralRepo()
    val clientEnv = Map.empty[String, String]
    val sysEnv = Map.empty[String, String]

    val result = CredentialsResolver.resolve(repo, Some(creds), clientEnv, sysEnv)
    val c = result.asInstanceOf[SonatypeCentralCredentials]
    assertEquals(c.username, "pkl-user")
    assertEquals(c.password, "pkl-pass")
    assertEquals(c.pgpSecret, "pkl-secret")
    assertEquals(c.pgpPassphrase, "pkl-passphrase")
  }

  test("env vars standalone — no Pkl file") {
    val repo = sonatypeCentralRepo()
    val clientEnv = Map(
      "DEDER_SONATYPE_CENTRAL_USERNAME" -> "env-user",
      "DEDER_SONATYPE_CENTRAL_PASSWORD" -> "env-pass",
      "DEDER_SONATYPE_CENTRAL_PGP_SECRET" -> "env-secret",
      "DEDER_SONATYPE_CENTRAL_PGP_PASSPHRASE" -> "env-pp"
    )
    val sysEnv = Map.empty[String, String]

    val result = CredentialsResolver.resolve(repo, None, clientEnv, sysEnv)
    val c = result.asInstanceOf[SonatypeCentralCredentials]
    assertEquals(c.username, "env-user")
    assertEquals(c.password, "env-pass")
    assertEquals(c.pgpSecret, "env-secret")
    assertEquals(c.pgpPassphrase, "env-pp")
  }

  test("missing field throws helpful error") {
    val repo = sonatypeCentralRepo()
    val clientEnv = Map("DEDER_SONATYPE_CENTRAL_USERNAME" -> "env-user")
    val sysEnv = Map.empty[String, String]

    val ex = intercept[RuntimeException] {
      CredentialsResolver.resolve(repo, None, clientEnv, sysEnv)
    }
    assert(ex.getMessage.contains("Missing credential 'password'"))
    assert(ex.getMessage.contains("DEDER_SONATYPE_CENTRAL_PASSWORD"))
    assert(ex.getMessage.contains("SonatypeCentralCredentials"))
  }

  test("repo id with hyphens generates correct env key") {
    val repo = mavenRepo(id = "my-nexus-internal")
    val clientEnv = Map(
      "DEDER_MY_NEXUS_INTERNAL_USERNAME" -> "env-user",
      "DEDER_MY_NEXUS_INTERNAL_PASSWORD" -> "env-pass"
    )
    val sysEnv = Map.empty[String, String]

    val result = CredentialsResolver.resolve(repo, None, clientEnv, sysEnv)
    val c = result.asInstanceOf[BasicAuthCredentials]
    assertEquals(c.username, "env-user")
    assertEquals(c.password, "env-pass")
  }

  test("each credential type resolves correctly") {
    // SonatypeSnapshotCredentials
    val snapshotRepo = sonatypeSnapshotRepo()
    val snapEnv = Map(
      "DEDER_SONATYPE_CENTRAL_USERNAME" -> "snap-user",
      "DEDER_SONATYPE_CENTRAL_PASSWORD" -> "snap-pass"
    )
    val snapResult = CredentialsResolver.resolve(snapshotRepo, None, snapEnv, Map.empty)
    val snap = snapResult.asInstanceOf[SonatypeSnapshotCredentials]
    assertEquals(snap.username, "snap-user")
    assertEquals(snap.password, "snap-pass")

    // BasicAuthCredentials
    val mvnRepo = mavenRepo(id = "my-repo")
    val mvnEnv = Map(
      "DEDER_MY_REPO_USERNAME" -> "mvn-user",
      "DEDER_MY_REPO_PASSWORD" -> "mvn-pass"
    )
    val mvnResult = CredentialsResolver.resolve(mvnRepo, None, mvnEnv, Map.empty)
    val mvn = mvnResult.asInstanceOf[BasicAuthCredentials]
    assertEquals(mvn.username, "mvn-user")
    assertEquals(mvn.password, "mvn-pass")
  }
}
