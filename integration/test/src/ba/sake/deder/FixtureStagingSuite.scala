package ba.sake.deder

class FixtureStagingSuite extends BaseIntegrationSuite {

  test("stageTestProject rewrites remote config URLs in staged pkl files") {
    val tempDir = os.pwd / "tmp" / s"fixture-stage-pkl-${System.currentTimeMillis()}"

    stageTestProject(os.RelPath("sample-projects/hello-plugin"), tempDir)

    val stagedDederPkl = os.read(tempDir / "deder.pkl")
    val stagedPluginPkl = os.read(tempDir / "resources/HelloPluginModule.pkl")

    assert(!stagedDederPkl.contains("https://sake92.github.io/deder/config/"))
    assert(stagedPluginPkl.contains("https://sake92.github.io/deder/config/"))
  }

  test("stageTestProject can stage into an existing destination") {
    val tempDir = os.pwd / "tmp" / s"fixture-stage-existing-${System.currentTimeMillis()}"

    stageTestProject(os.RelPath("sample-projects/multi"), tempDir)
    stageTestProject(os.RelPath("sample-projects/multi"), tempDir)

    assert(os.exists(tempDir / "backend/src"))
    assert(os.exists(tempDir / "frontend/src"))
  }
}
