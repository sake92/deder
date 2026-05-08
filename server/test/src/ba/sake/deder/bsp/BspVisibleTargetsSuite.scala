package ba.sake.deder.bsp

import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.ConfigParser
import ba.sake.deder.config.DederProject.DederModule

class BspVisibleTargetsSuite extends munit.FunSuite {

  private val testProjectsDir = os.pwd / "server/test/resources/sample-projects"

  test("simple module graph with no projection: all modules stay visible by default") {
    val modules = parseModules(testProjectsDir / "simple" / "deder.pkl")
    val visible = BspVisibleTargets.visibleModuleIds(modules)
    assertEquals(visible, Set("core", "app", "app-test"))
  }

  test("cross-platform/cross-version: only latest Scala version visible per platform and test family") {
    val modules = parseModules(testProjectsDir / "cross" / "deder.pkl")
    val visible = BspVisibleTargets.visibleModuleIds(modules)
    assertEquals(
      visible,
      Set(
        "common-jvm-3.0.0",
        "common-jvm-test-3.0.0",
        "common-js-3.0.0",
        "common-js-test-3.0.0",
        "common-native-3.0.0",
        "common-native-test-3.0.0"
      )
    )
  }

  test("explicit bspVisible=false hides a module even if default-visible") {
    val modules = parseModules(testProjectsDir / "bsp-hidden" / "deder.pkl")
    val visible = BspVisibleTargets.visibleModuleIds(modules)
    assertEquals(visible, Set("app"))
  }

  test("explicit bspVisible=true keeps a module visible even if it would otherwise be filtered out") {
    val modules = parseModules(testProjectsDir / "bsp-forced-visible" / "deder.pkl")
    val visible = BspVisibleTargets.visibleModuleIds(modules)
    assertEquals(visible, Set("app-jvm-2.0.0", "app-jvm-3.0.0", "app-js-3.0.0"))
  }

  // --- helpers ---

  private def parseModules(configPath: os.Path): Seq[DederModule] =
    ConfigParser(writeJson = false).parse(configPath) match {
      case Right(project) => project.modules.asScala.toSeq
      case Left(err)      => fail(s"Failed to parse $configPath: $err")
    }
}
