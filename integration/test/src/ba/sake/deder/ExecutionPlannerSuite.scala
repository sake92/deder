package ba.sake.deder

import scala.jdk.CollectionConverters.*

class IntegrationSuite extends munit.FunSuite {

  test("deder should work amazingly") {
    val testResourceDir = os.Path(System.getenv("MILL_TEST_RESOURCE_DIR"))
    val dederClientPath = System.getenv("DEDER_CLIENT_PATH")
    val dederServerPath = System.getenv("DEDER_SERVER_PATH")
    val projectPath = testResourceDir / "sample-projects/multi"
    println(
      (
        testResourceDir,
        dederClientPath,
        dederServerPath,
        projectPath
      )
    )
    os.write.over(projectPath / ".deder/server.properties", s"localPath=$dederServerPath\n")
    val versionOutput = os.proc("bash", "-c", s"$dederClientPath version").call(cwd = projectPath)
    assert(versionOutput.out.text().contains("Server version: 0.0.1"))
    os.proc("bash", "-c", s"$dederClientPath shutdown").call(cwd = projectPath)
  }

}
