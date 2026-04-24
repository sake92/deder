package ba.sake.deder

class CliClientParamsSuite extends munit.FunSuite {

  test("client env var passes through when no forkEnv conflict") {
    val clientEnv = Map("CI" -> "true", "MY_SECRET" -> "abc")
    val forkEnv   = Map("JAVA_OPTS" -> "-Xmx512m")
    val merged    = clientEnv ++ forkEnv
    assertEquals(merged.get("CI"), Some("true"))
    assertEquals(merged.get("MY_SECRET"), Some("abc"))
    assertEquals(merged.get("JAVA_OPTS"), Some("-Xmx512m"))
  }

  test("forkEnv wins over client env on conflict") {
    val clientEnv = Map("MY_VAR" -> "from-client")
    val forkEnv   = Map("MY_VAR" -> "from-config")
    val merged    = clientEnv ++ forkEnv
    assertEquals(merged("MY_VAR"), "from-config")
  }

  test("empty client env leaves forkEnv unchanged") {
    val clientEnv = Map.empty[String, String]
    val forkEnv   = Map("K" -> "v")
    val merged    = clientEnv ++ forkEnv
    assertEquals(merged, forkEnv)
  }
}
