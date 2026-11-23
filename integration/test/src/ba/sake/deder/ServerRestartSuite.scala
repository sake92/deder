package ba.sake.deder

import scala.concurrent.duration.*

class ServerRestartSuite extends BaseIntegrationSuite {

  test("empty PID in a held lock cannot admit a second server") {
    withTestProject("sample-projects/multi") { root =>
      assertEquals(executeDederCommand(root, "version").exitCode, 0)
      val lock = root / ".deder/server.lock"
      val owner = os.read(lock)
      try {
        os.write.over(lock, "")
        val contender = os.proc("java", "-cp", dederServerPath, "ba.sake.deder.ServerMain",
          "--root-dir", root.toString).call(cwd = root, check = false, timeout = 10000)
        assertEquals(contender.exitCode, 1)
        assertEquals(os.read(lock), "")
        assertEquals(executeDederCommand(root, "version").exitCode, 0)
      } finally os.write.over(lock, owner)
    }
  }

  test("shutdown terminates the project server when its CLI socket is unreachable") {
    withTestProject("sample-projects/multi") { root =>
      assertEquals(executeDederCommand(root, "version").exitCode, 0)
      val owner = ProcessHandle.of(os.read(root / ".deder/server.lock").trim.toLong).orElseThrow()
      val socket = root / ".deder/server-cli.sock"
      val moved = root / ".deder/unreachable.sock"
      os.move(socket, moved)
      try {
        assertEquals(executeDederCommand(root, "shutdown").exitCode, 0)
        assert(!owner.isAlive)
      } finally if owner.isAlive then os.move(moved, socket)
    }
  }

  test("rapid restart loop — deder shutdown then deder version should not hang") {
    withTestProject("sample-projects/multi") { projectPath =>
      for (i <- 1 to 5) {
        // Shutdown
        val shutdownRes = executeDederCommand(projectPath, "shutdown")
        assertEquals(shutdownRes.exitCode, 0, s"Iteration $i: shutdown should succeed")

        // Wait a bit for old server to fully exit
        Thread.sleep(500)

        // Start new server via any command
        val execRes = executeDederCommand(projectPath, "version")
        assertEquals(execRes.exitCode, 0, s"Iteration $i: version command should succeed after restart")
        assert(
          execRes.out.text().contains("Server version:"),
          s"Iteration $i: should get server version output. Got: ${execRes.out.text().take(500)}"
        )
      }
    }
  }

  test("no deder.pkl — should fail with clear error, not create minimal file") {
    withTestProject("sample-projects/multi") { projectPath =>
      // Delete deder.pkl
      os.remove(projectPath / "deder.pkl")

      // Run deder — should NOT create a new deder.pkl
      val res = executeDederCommand(projectPath, "exec", "-t", "compile")
      assert(res.exitCode != 0, "Should fail when deder.pkl is missing")

      // Verify no deder.pkl was created
      assert(!os.exists(projectPath / "deder.pkl"),
        "Client must NOT create a deder.pkl file automatically")
    }
  }

  test("server lock is released after shutdown — new server starts cleanly") {
    withTestProject("sample-projects/multi") { projectPath =>
      // Start server
      val res1 = executeDederCommand(projectPath, "version")
      assertEquals(res1.exitCode, 0, "Server should start")

      // Shutdown
      val shutdownRes = executeDederCommand(projectPath, "shutdown")
      assertEquals(shutdownRes.exitCode, 0, "Shutdown should succeed")

      // Immediately restart — should succeed (proves lock was released)
      val res2 = executeDederCommand(projectPath, "version")
      assertEquals(res2.exitCode, 0, "Server should restart cleanly after shutdown")
    }
  }
}
