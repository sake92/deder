---
layout: reference.html
title: Testing
---

# Testing

## Tasks

Deder provides two test tasks for JVM modules (`java-test`, `scala-test`):

| Task | Command | Execution |
|---|---|---|
| `test` | `deder exec -t test` | Forked JVM(s) — isolated, safe for CI |
| `testInMemory` | `deder exec -t testInMemory` | In-process (same JVM as server) — fast, no startup overhead |

Use `test` (forked) for CI and when tests rely on isolation (static state, `System.setProperty`, shared ports). Use `testInMemory` during development for a faster feedback loop.

> ScalaJS and Scala Native only have `test` — they use their own runners and are unaffected by this split.

---

## Execution modes

Deder supports three independent parallelism knobs that compose together (forked `test` task only).

### Serial execution

To run test classes one at a time inside a single forked JVM (same behaviour as sbt's `Test / testForkedParallel = false` and Mill's `testForked`), set both to `1`:

```pkl
testTemplate = (template.asTest()) {
  maxTestForks    = 1
  testParallelism = 1
}
```

### Intra-JVM thread parallelism (`testParallelism`)

`testParallelism` controls how many test **classes** run concurrently inside one JVM using a fixed thread pool. Framework-level parallelism (JUnit Jupiter `@Execution(CONCURRENT)`, ZIO Test, weaver, etc.) is independent and unaffected.

```pkl
testTemplate = (template.asTest()) {
  testParallelism = 8   // up to 8 test classes at once in the same JVM
}
```

Safe to increase only when all tests in the module avoid JVM-global mutable state (static fields, `System.setProperty`, security providers, default locale/timezone, shared ports, shared temp files).

Valid range: `1`–`256`.

### Multi-fork parallelism (`maxTestForks`)

`maxTestForks` spawns multiple forked JVMs for the same module. Test classes are distributed across forks upfront using **LPT (Longest-Processing-Time) bin packing**: the slowest known class is placed first, spreading work as evenly as possible. Effective fork count is `min(maxTestForks, discoveredTestClasses)` — Deder never spawns an empty fork.

```pkl
testTemplate = (template.asTest()) {
  maxTestForks = 4   // up to 4 forked JVMs for this module
}
```

Multi-fork and `testParallelism` compose: each of the N forks independently runs up to `testParallelism` classes in parallel.

Valid range: `1`–`64`.

### Server-wide fork cap (`maxConcurrentTestForks`)

The server-wide semaphore in `.deder/server.properties` caps the total number of live forked JVMs across **all** concurrent test tasks. Per-module `maxTestForks` is capped by this value.

```properties
maxConcurrentTestForks=16   # default: Runtime.availableProcessors()
```

### Summary

| Setting | Scope | Default | Effect |
|---|---|---|---|
| `testParallelism` | Per module (Pkl) | `0` (all CPUs) | Test classes run concurrently per forked JVM |
| `maxTestForks` | Per module (Pkl) | `0` (all CPUs) | Forked JVMs per module |
| `maxConcurrentTestForks` | Server-wide (`.deder/server.properties`) | CPU cores | Hard cap on total live forks |

Example: `maxTestForks=4`, `testParallelism=8`, `maxConcurrentTestForks=16` — up to 4 JVMs for the module, each running 8 class threads, server allows at most 16 forks total across all modules.

---

## Stdout capture

### In-process tests (`testInMemory`)

The `testInMemory` task runs tests directly inside the server JVM using `InMemoryTestOrchestrator`. Deder installs a `TeePrintStream` over `System.out` and `System.err` before any test code runs. Output is line-buffered (max 8 KB per flush). When a test suite is active, output is routed to server notifications tagged with the module ID so it appears in the client in real time.

### Forked tests (`test`)

Each forked JVM installs `SuiteOutputCapture` as a replacement `PrintStream` on `System.out` at startup. All output goes through this stream.

The capture layer groups output by suite using a `ThreadLocal` buffer:

- When a suite starts, a per-suite buffer is opened on that thread.
- Any `println` on that thread is written into the buffer rather than directly to stdout.
- When the suite finishes, the buffered text is flushed as a single `SuiteCompleted` envelope.
- Output that arrives outside any open suite window (JVM startup messages, framework initialisation) is collected in a separate unattributed buffer and emitted line-by-line as `UnattributedOutput` envelopes.

The orchestrator reads the forked JVM's stdout line-by-line and parses each line that starts with the sentinel prefix `@@DEDER-FORK@@ ` as a JSON envelope. All other lines (including stderr from a separate stream) are forwarded directly to server notifications.

#### Envelope types

| Envelope | When emitted | Key fields |
|---|---|---|
| `ForkStarted` | JVM has initialised | `forkId` |
| `SuiteStarted` | Suite execution begins | `suiteName`, `threadId` |
| `SuiteCompleted` | Suite execution ends | `suiteName`, `threadId`, `capturedOutput` |
| `UnattributedOutput` | Output outside a suite window | `text` |
| `ForkCompleted` | All suites finished | `forkId`, test totals |

The orchestrator also reads a results JSON file written by the fork to disk on exit; the stdout protocol is for live streaming only.

---

## Test history

Deder records per-class timing and status after every test run to enable accurate LPT distribution in subsequent runs.

### Location

```
.deder/out/<module-id>/test-history.json
```

### Format

```json
{
  "stats": {
    "com.example.FastTest": {
      "durationMs": 150,
      "lastStatus": "passed",
      "lastRunEpochMs": 1713607200000
    },
    "com.example.SlowTest": {
      "durationMs": 5000,
      "lastStatus": "failed",
      "lastRunEpochMs": 1713607200000
    }
  }
}
```

Each key is the fully qualified class name. `durationMs` is the wall-clock time for all tests in that class combined.

### Merge behaviour

After each run, results from all forks are merged into the existing history file. Classes that were not executed in the current run retain their previous stats unchanged. Writes are atomic (temp file + rename).

History is advisory: load and save failures are silently ignored. Deleting the file is safe — Deder falls back to assigning the median duration of known classes to unknown ones, then distributes them last.
