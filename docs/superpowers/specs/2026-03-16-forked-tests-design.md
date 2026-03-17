# Forked Test Execution — Phase 1

## Problem

Currently, JVM tests (SCALA_TEST modules) run in-process within the Deder server JVM using classloader isolation. This has drawbacks:
- User test code can crash or destabilize the long-running build server
- `jvmOptions` from config don't apply to test execution (tests share the server's JVM settings)
- No true memory/process isolation between test runs and the build daemon

## Approach

Spawn a separate JVM subprocess for test execution. Test discovery happens in-process (it's fast and uses the existing classloader infrastructure). The discovered test class names and fingerprints are passed to the forked JVM, which only needs to load frameworks and run the tests. Results are written to a temp file as JSON, while stdout/stderr are forwarded to the server for real-time output.

This is Phase 1 of a broader vision:
- **Phase 1 (this spec):** Single forked JVM per test module, temp-file results protocol
- **Phase 2 (future):** Multiple worker JVMs, H2 coordination, adaptive test distribution based on recorded stats

## Architecture

```
Deder Server (in-process)
  ├── Test Discovery (classloader, framework fingerprinting)
  │    └── produces: list of (className, fingerprint) tuples
  ├── Constructs: classpath, discovered test list, framework names, jvmOptions
  ├── Creates temp results file path
  │
  └── os.proc(javaBinary, ...) → Forked JVM subprocess
       ├── ForkedTestMain (entry point)
       │    ├── Receives discovered test classes + fingerprints via args file
       │    ├── Loads frameworks via sbt testing interface
       │    ├── Creates DederTestRunner with the pre-discovered tests
       │    ├── Runs tests (stdout/stderr flow naturally to server)
       │    └── Writes DederTestResults JSON to temp results file
       ├── Stdout → forwarded to server notifications (real-time test output)
       ├── Stderr → forwarded to server notifications (errors/warnings)
       └── Exit code: 0 = success, non-zero = failures or errors

Server reads temp results file after process exits → DederTestResults
```

## Components

### 1. Config: `fork` option on `ScalaTestModule`

In `config/DederProject.pkl`, add to `ScalaTestModule`:

```pkl
class ScalaTestModule extends ScalaModule {
  fork: Boolean = true
  testFrameworks: Listing<String> = [...]
}
```

- `fork = true` (default): tests run in a forked JVM subprocess
- `fork = false`: tests run in-process with classloader isolation (current behavior)

Only `ScalaTestModule` gets this option in Phase 1. ScalaJS and ScalaNative test modules already run tests out-of-process via Node.js / native binary respectively.

### 2. Communication Protocol

The forked process uses **three channels**:

1. **Stdout** — real-time test output (pass/fail lines, framework logs, user printlns). The server reads line-by-line and forwards to `ServerNotificationsLogger`. No structured format needed — this is human-readable output, same as the current in-process mode.

2. **Stderr** — error output. Forwarded to notifications as error messages.

3. **Temp results file** — structured JSON written atomically after all tests complete. Contains the final `DederTestResults`. The server reads this file after the process exits.

**Args file** (server → fork): The server writes a JSON file with the test execution parameters before spawning the subprocess. This avoids command-line length limits when there are many test classes.

```json
{
  "classesDir": "/path/to/.deder/out/myModule/classes",
  "frameworks": ["munit.Framework", "org.scalatest.tools.Framework"],
  "tests": [
    {"className": "com.example.MySuite", "fingerprint": {"type": "subclass", "superclassName": "munit.Suite", "isModule": true}},
    {"className": "com.example.OtherSuite", "fingerprint": {"type": "annotated", "annotationName": "org.junit.Test", "isModule": false}}
  ],
  "testSelectors": ["com.example.MySuite#testFoo"],
  "workerThreads": 4,
  "resultsFile": "/tmp/deder-test-results-abc123.json"
}
```

**Results file** (fork → server):
```json
{
  "total": 10, "passed": 8, "failed": 1, "errors": 0, "skipped": 1,
  "duration": 1234,
  "failedTestNames": ["com.example.MySuite#testBar"]
}
```

This is exactly the existing `DederTestResults` serialized via tupson `JsonRW`.

### 3. `ForkedTestMain` — Entry point for forked JVM

A new `main` class: `ba.sake.deder.testing.ForkedTestMain`

**Arguments:**
- Single arg: path to the args JSON file

**Behavior:**
1. Reads the args file
2. Creates a classloader from the classpath (no isolation needed — this IS a separate process)
3. Loads test frameworks via reflection from the framework class names
4. Constructs `(Framework, Seq[(String, Fingerprint)])` tuples from the pre-discovered test list
5. Creates `DederTestRunner` with the standard `DederTestEventHandler` (writes colored output to stdout)
6. Runs tests using `DederTestOptions` from the args
7. Writes `DederTestResults` JSON to the results file path from args
8. Exits with code 0 (all passed) or 1 (failures/errors)

**Key detail:** `ForkedTestMain` reuses `DederTestRunner` with the standard `DederTestEventHandler`. However, some adaptations are needed for the forked context:
- **No `OutputCaptureContext`**: In the fork, stdout/stderr go directly to the process streams (which the server reads). No need for the `OutputCaptureContext.withCapture` wrapper.
- **No cancellation tokens**: `RequestContext` and `DederGlobals.cancellationTokens` don't exist in the fork. Cancellation is handled externally by the server calling `destroyForcibly()` on the process. The `DederTestRunner.executeTasks()` cancellation check will be a no-op since `RequestContext.id` is null in the fork.
- **Logger**: Uses a `DederTestLogger` that writes to stdout directly (not via `ServerNotificationsLogger`). The server captures these streams for real-time output forwarding.

### 4. `ForkedTestOrchestrator` — Server-side subprocess manager

A new class: `ba.sake.deder.testing.ForkedTestOrchestrator`

**Responsibilities:**
- Performs test discovery in-process (reuses existing `DederTestDiscovery`)
- Writes the args JSON file to `.deder/out/<module>/test/fork-args.json`
- Constructs the `java` command with proper classpath, jvmOptions
- Resolves the `java` binary: uses `module.javaHome` if configured, otherwise `JAVA_HOME` env, otherwise `java` on PATH
- Spawns the subprocess via `os.proc(...).spawn()` with stdout/stderr piped
- Reads stdout/stderr line-by-line from the spawned process streams (on separate threads) and forwards to `ServerNotificationsLogger` for real-time output
- Calls `process.waitFor()` after setting up stream readers
- After process exits, reads the temp results file and deserializes `DederTestResults`
- Cleans up temp files
- If the process crashes (no results file), constructs an error `DederTestResults`

**Classpath for the forked JVM:**
The forked JVM needs two things on its classpath:
1. **User test classpath** — the module's runtime classpath (test code + all dependencies). This already includes `sbt-test-interface` as a transitive dependency of the test framework.
2. **Deder harness classes** — `ForkedTestMain`, `DederTestRunner`, `DederTestDiscovery`, etc. These come from the Deder server JAR.

**Classpath construction:** The user's test classpath goes first. The server JAR is appended last. This ensures that `sbt.testing.*` classes come from the user's dependencies (same version as their test framework), avoiding `LinkageError`. The Deder harness classes don't conflict because they live in the `ba.sake.deder` package which won't appear in user code.

**Server JAR resolution:** The server JAR path is resolved from `.deder/server.jar` relative to the project root (`DederGlobals.projectRootDir`). During development (running from IDE/classes), the server constructs a classpath from its classes directory and dependency JARs via the system classloader's URL entries instead.

**Working directory:** The forked process CWD is set to `DederGlobals.projectRootDir` (the project root).

**Environment variables:** The forked process inherits the server's environment. No custom env var configuration in Phase 1.

### 5. Changes to `testTask` in `CoreTasks.scala`

The existing `testTask` gains a fork check:

```scala
val testTask = TaskBuilder
  .make[DederTestResults](
    name = "test",
    supportedModuleTypes = Set(ModuleType.SCALA_TEST)
  )
  .dependsOn(classesTask)
  .dependsOn(runClasspathTask)
  .buildWithSummary(
    execute = { ctx =>
      val (classesDir, runClasspath) = ctx.depResults
      val runtimeClasspath = (Seq(classesDir) ++ runClasspath).reverse.distinct.reverse
      val module = ctx.module.asInstanceOf[ScalaTestModule]
      val frameworkClassNames = module.testFrameworks.asScala.toSeq
      val testOptions = DederTestOptions(ctx.args)

      if (module.fork) {
        ForkedTestOrchestrator.run(
          classesDir = classesDir,
          runtimeClasspath = runtimeClasspath,
          frameworkClassNames = frameworkClassNames,
          jvmOptions = module.jvmOptions.asScala.toSeq,
          javaHome = Option(module.javaHome),
          testOptions = testOptions,
          notifications = ctx.notifications,
          moduleId = ctx.module.id,
          outDir = ctx.out,
          workerThreads = DederGlobals.testWorkerThreads
        )
      } else {
        // In-process execution (current behavior, unchanged)
        OutputCaptureContext.withCapture(ctx.notifications, ctx.module.id) {
          ClassLoaderUtils.withIsolatedClassLoader(runtimeClasspath, TestClassLoaderSharedPrefixes) { classLoader =>
            val logger = DederTestLogger(ctx.notifications, ctx.module.id)
            val testDiscovery = DederTestDiscovery(...)
            val frameworkTests = testDiscovery.discover()
            Using.resource(Executors.newFixedThreadPool(DederGlobals.testWorkerThreads)) { executorService =>
              val testRunner = DederTestRunner(executorService, frameworkTests, classLoader, logger)
              testRunner.run(testOptions)
            }
          }
        }
      }
    },
    isResultSuccessful = _.success,
    summarize = DederTestResults.summarize
  )
```

### 6. `jvmOptions` and `javaHome` propagation

When `fork = true`:
- `jvmOptions` from the module config are passed to the forked JVM command
- `javaHome` from the module config determines which JVM binary to use

```
<javaHome>/bin/java <jvmOptions> -cp <classpath> ba.sake.deder.testing.ForkedTestMain <argsFile>
```

This is a new benefit — currently `jvmOptions` and `javaHome` are ignored for test execution.

## What stays the same

- **Test discovery logic** — same `DederTestDiscovery` class, same fingerprint matching (runs in-process)
- **Test running logic** — same `DederTestRunner`, same framework runner calls (runs in fork)
- **ScalaJS tests** — unchanged, already out-of-process via Node.js
- **ScalaNative tests** — unchanged, already out-of-process via native binary
- **Test output format** — same colored pass/fail output in the terminal
- **Test selectors** — same `className#testName` and wildcard patterns

## Edge cases

- **Cancellation:** When a test run is cancelled, the orchestrator calls `process.destroyForcibly()` to kill the forked process. This ensures the process is terminated even if it ignores SIGTERM. Note: child processes spawned by test code (e.g., embedded servers) may survive — this is acceptable for Phase 1.

- **Process crash without results file:** If the forked JVM crashes (OOM, segfault, etc.) before writing the results file, the orchestrator returns a failed `DederTestResults` with `errors = 1` and includes the captured stderr output in the error notification.

- **Partial test execution:** If the fork crashes mid-run, results from already-completed tests are lost (they were only visible in real-time stdout). The results file is only written after all tests complete. This is acceptable for Phase 1 — the user sees the real-time output and knows what ran.

- **Args file / results file location:** Both are written under `.deder/out/<module>/test/` to keep them with other task output. The results file uses a unique name per run to avoid races.

- **Development mode:** When running the server from an IDE (no server JAR), the server constructs the harness classpath from its classes directory and dependency JARs. This is resolved via the system classloader's URL entries. If resolution fails, falls back to in-process with a warning log.

- **Classpath length:** On some OSes, very long classpaths can exceed command-line limits. The classpath is passed via `-cp` flag which goes through the OS. If this becomes an issue (future), we can switch to a classpath file (`@argfile` or `-cp @file` with Java 9+).

## File changes

| File | Change |
|------|--------|
| `config/DederProject.pkl` | Add `fork: Boolean = true` to `ScalaTestModule` |
| `config/src/...` | Regenerate Pkl bindings |
| `server/src/.../testing/ForkedTestMain.scala` | New: entry point for forked JVM |
| `server/src/.../testing/ForkedTestOrchestrator.scala` | New: spawns/manages forked process, does discovery |
| `server/src/.../testing/ForkedTestProtocol.scala` | New: args file JSON types, serializable `Fingerprint` data classes (`SerializableSubclassFingerprint`, `SerializableAnnotatedFingerprint`) with `JsonRW`, and reconstruction to real `sbt.testing.Fingerprint` instances |
| `server/src/.../CoreTasks.scala` | Modify `testTask` to branch on `fork` config |

## Testing

- Integration tests with `fork = true` (default) — verify tests pass in forked mode
- Integration tests with `fork = false` — verify in-process mode still works
- Test with jvmOptions (e.g., `-Xmx128m`) to verify they apply in forked mode
- Test cancellation — verify forked process is killed
- Verify real-time test output appears during forked execution (not just at the end)

## Future (Phase 2 notes)

- Multiple worker JVMs for parallel test execution across processes
- H2 database for storing per-test/suite timing stats
- Adaptive test distribution: first run randomly distributes, subsequent runs use recorded stats to balance worker load
- Potentially configurable `testWorkers: Int` in config
- `fork` option for ScalaJS/ScalaNative test modules
