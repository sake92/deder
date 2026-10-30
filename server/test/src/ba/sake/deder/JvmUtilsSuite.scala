package ba.sake.deder

import scala.util.Properties

class JvmUtilsSuite extends munit.FunSuite {

  private val fakeJdk = os.pwd / "fake-jdk"
  private val javaExecutable = if Properties.isWin then "java.exe" else "java"
  private val jshellExecutable = if Properties.isWin then "jshell.exe" else "jshell"

  test("javaBinary uses the configured JDK home") {
    assertEquals(JvmUtils.javaBinary(Some(fakeJdk)), (fakeJdk / "bin" / javaExecutable).toString)
  }

  test("javaBinary uses PATH when no JDK home is configured") {
    assertEquals(JvmUtils.javaBinary(None), "java")
  }

  test("jshellBinary uses the configured JDK home") {
    assertEquals(JvmUtils.jshellBinary(Some(fakeJdk)), (fakeJdk / "bin" / jshellExecutable).toString)
  }
}
