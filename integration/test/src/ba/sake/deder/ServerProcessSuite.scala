package ba.sake.deder

import scala.concurrent.duration.*

class ServerProcessSuite extends BaseIntegrationSuite {

  override def munitTimeout = 2.minute

  test("server lock should prevent multiple servers for the same project") {
    withTestProject("sample-projects/multi") { projectPath =>
      // 1. Start first server via CLI (blocks until server responds = fully up)
      executeDederCommand(projectPath, "version")

      // 2. Verify lock file exists and contains a live PID
      val lockFile = projectPath / ".deder/server.lock"
      assert(os.exists(lockFile), "Lock file should exist after server starts")
      val server1Pid = os.read(lockFile).trim.toLong
      val procHandle = java.lang.ProcessHandle.of(server1Pid)
      assert(
        procHandle.isPresent && procHandle.get().isAlive,
        s"Server PID $server1Pid should be alive"
      )

      // 3. Try to start a second server directly (bypasses CLI client retry loop)
      val result2 = os.proc(
        "java", "-cp", dederServerPath,
        "ba.sake.deder.ServerMain",
        "--root-dir", projectPath.toString
      ).call(cwd = projectPath, check = false, mergeErrIntoOut = true)

      // 4. Second server must fail — lock is held by first server
      assertEquals(result2.exitCode, 1, "Second server must exit with code 1 (lock held)")
      assert(
        result2.out.text().contains("Could not acquire server lock"),
        s"Second server output must mention lock failure. Got: ${result2.out.text().take(500)}"
      )

      // 5. First server still functional
      val dederRes = executeDederCommand(projectPath, "version")
      assert(
        dederRes.out.text().contains("Server version:"),
        "First server should still respond after second server attempted"
      )
    }
  }
}
