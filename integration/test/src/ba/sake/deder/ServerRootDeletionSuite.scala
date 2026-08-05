package ba.sake.deder

class ServerRootDeletionSuite extends BaseIntegrationSuite {

  test("server shuts down when project root directory is deleted") {
    val projectPath = stagedServerProject("sample-projects/multi", dirSuffix = System.nanoTime().toString)
    var serverPid: Long = -1
    try {
      // Start server
      val startRes = executeDederCommand(projectPath, "version")
      assertEquals(startRes.exitCode, 0, "Server should start")

      val lockFile = projectPath / ".deder/server.lock"
      assert(os.exists(lockFile), "Lock file should exist after server starts")
      serverPid = os.read(lockFile).trim.toLong
      val procHandle = java.lang.ProcessHandle.of(serverPid)
      assert(
        procHandle.isPresent && procHandle.get().isAlive,
        s"Server PID $serverPid should be alive"
      )

      // Delete the whole project root (simulates removing a git worktree)
      os.remove.all(projectPath)

      // Server must exit on its own (root checker cadence is 10s) — poll up to 60s
      val deadline = System.currentTimeMillis() + 60_000
      while procHandle.isPresent && procHandle.get().isAlive && System.currentTimeMillis() < deadline do
        Thread.sleep(500)

      val stillAlive = procHandle.isPresent && procHandle.get().isAlive
      assert(!stillAlive, s"Server PID $serverPid should have exited after project root deletion")
    } finally {
      // If the test failed and the server is still alive, kill it to avoid leaking
      if serverPid > 0 then
        val procHandle = java.lang.ProcessHandle.of(serverPid)
        if procHandle.isPresent && procHandle.get().isAlive then procHandle.get().destroy()
      if os.exists(projectPath) then os.remove.all(projectPath)
    }
  }

  test("server stays alive when only deder.pkl is deleted") {
    val projectPath = stagedServerProject("sample-projects/multi", dirSuffix = System.nanoTime().toString)
    var serverPid: Long = -1
    try {
      val startRes = executeDederCommand(projectPath, "version")
      assertEquals(startRes.exitCode, 0, "Server should start")

      val lockFile = projectPath / ".deder/server.lock"
      serverPid = os.read(lockFile).trim.toLong
      val procHandle = java.lang.ProcessHandle.of(serverPid)
      assert(
        procHandle.isPresent && procHandle.get().isAlive,
        s"Server PID $serverPid should be alive"
      )

      // Delete only the config file — the project root still exists
      os.remove(projectPath / "deder.pkl")

      // Wait longer than one root-checker cycle (10s) — server must NOT shut down
      Thread.sleep(15_000)

      assert(
        procHandle.isPresent && procHandle.get().isAlive,
        s"Server PID $serverPid should stay alive when only deder.pkl is deleted"
      )

      // Server should still respond to commands
      val res = executeDederCommand(projectPath, "version")
      assertEquals(res.exitCode, 0, "Server should still respond after deder.pkl deletion")
      assert(
        res.out.text().contains("Server version:"),
        s"Should get server version output. Got: ${res.out.text().take(500)}"
      )
    } finally {
      // Project dir is intact — normal shutdown
      if os.exists(projectPath) then
        executeDederCommand(projectPath, "shutdown")
        os.remove.all(projectPath)
    }
  }
}
