# Deder QOL and DX Improvements

This file records only items approved through the interactive review. Implementation details may be refined when each item starts, but the approved behavior and scope changes require an explicit decision.

## Approved items

### 1. Make failed tests accurately summarized and directly rerunnable

Status: approved

#### Problem

- Runtime errors make a test module unsuccessful, but `TestResultsSummary` counts only failures. The output can say `0 failed tests` for a failed module.
- Failure names may be only the test-case name. That is not enough to form a Deder test selector.
- The test runner TODO requests a command for rerunning failed tests.

#### Proposed behavior

- Keep failures and errors distinct in totals and per-module output.
- Render fully qualified selectors as `suite#test` or `suite` for suite-level failures.
- Print a copy-paste rerun command for each module with failed or errored tests.
- Always suggest the standard `test` task, including after an in-memory run, because it exists for every test module and provides isolation.
- Preserve the existing skipped-module and cross-module failure reporting.

Example:

```text
🔴 FAILED app-test (1 failed, 1 error)
       - example.MySuite#returnsValue
       - example.MySuite#throwsUnexpectedly
       Re-run:
         deder exec -t test -m app-test example.MySuite#returnsValue example.MySuite#throwsUnexpectedly
```

#### Approach

1. Add `testsErrors` to `TestResultsSummary`.
2. Add a structured rerun plan to the summary rather than storing shell strings:

   ```scala
   case class TestRerunPlan(moduleId: String, selectors: Seq[String])
   ```

3. Derive selectors from the existing `DederTestResults.suites` data. Do not change `DederTestResults` or the published test-runner model.
4. Keep shell-command rendering in the `PlainTextWritable[TestResultsSummary]` boundary.
5. Double-quote selectors containing whitespace or shell metacharacters only in rendered commands; keep selectors raw in structured data.
6. Sort modules and selectors deterministically.
7. Show rerun data in JSON as structured fields, not formatted terminal text.

#### Behavior checklist

| Behavior | Expected result | Test |
|---|---|---|
| Failure and error counts | Total includes errors; failure and error counts remain separate | `TestResultsSummarySuite` |
| Failed module rendering | Module line shows both failed and error counts | `TestResultsSummarySuite` |
| Fully qualified selectors | Suite and test case are rendered as `suite#test` | `TestResultsSummarySuite` |
| Suite-level failure | Selector is the suite name without a duplicate `#suite` suffix | `TestResultsSummarySuite` |
| Selector quoting | Names containing spaces remain one copy-paste argument | `TestResultsSummarySuite` |
| Rerun plan | One deterministic plan per failing module, using the `test` task | `TestResultsSummarySuite` |
| Successful suite | No rerun section is emitted | `TestResultsSummarySuite` |
| Cross-module/skipped failures | Existing behavior remains unchanged | Existing `TestResultsSummarySuite` test |
| Real CLI output | Failing example reports actionable selectors and rerun command | Manual `examples/multi` proof |
| End-to-end regression | Staged test project produces nonzero exit and rerun guidance | `RunTestsSuite` |

#### Verification

1. Before implementation, reproduce the current error-only summary with a focused unit fixture.
2. Prove the output against a temporary copy of a real/example project, as required by repository guidance.
3. Run:

   ```sh
   deder exec -t test -m server-test TestResultsSummarySuite
   ./scripts/run-it-tests.sh ba.sake.deder.RunTestsSuite
   ```

4. Run a targeted end-to-end test again after the implementation.

#### Decision needed

- Approved: rerun guidance always uses the standard `test` task.

### 2. Show compilation progress in the CLI

Status: approved

#### Problem

- Zinc emits `CompileStarted`, `TaskProgress`, `CompileFinished`, and `CompileFailed`.
- The BSP layer consumes those events, but CLI output discards them.
- During long builds the CLI shows only the generic 60-second “Still working...” heartbeat.

#### Proposed behavior

- Add project-global `compileProgressIntervalMs` to `.deder/server.properties`.
- Default to 500 ms; `0` disables all dedicated compile progress output.
- Reject negative values as invalid server configuration.
- Report module-specific compile start and completion immediately when enabled.
- Rate-limit progress globally: at most one progress line per interval across all concurrent compiles.
- Emit the latest observed `current/total` progress when the interval budget allows it.
- Do not emit repeated lines with unchanged progress.
- Send progress to stderr as normal `INFO` log messages, preserving stdout for summaries and JSON.
- Treat emitted progress as visible activity so the generic heartbeat does not duplicate it.
- Reset module progress state on success or failure so later compilations report normally.

Example:

```text
[common] Compiling 57 source files...
[common] Compile progress: 15/57 (26%)
[backend] Compiling 34 source files...
[common] Compile progress: 43/57 (75%)
[common] Compiled in 1.8s
[backend] Compiled in 874ms
```

#### Approach

1. Add `compileProgressIntervalMs: Long = 500` to `ServerProperties`, validate it as non-negative, and expose it through `DederGlobals`.
2. Add a focused `CliCompileProgress` translator owned by `CliClientMessageHandler`.
3. Translate only `taskName == "compile"` notifications; other task progress remains unchanged.
4. Track compile start time and source count per module.
5. Use a shared monotonic-clock gate across modules so concurrent compiles cannot multiply output frequency.
6. Emit through `CliServerMessage.log`, which already handles module coloring and `NO_COLOR`.
7. Do not print warning counts from successful `CompileFinished` events until Zinc supplies accurate values; it currently emits `(0, 0)` on that path.
8. Clear a module’s state when its compile finishes or fails.
9. Keep the generic heartbeat as a fallback for periods with no visible compile-progress event.

#### Behavior checklist

| Behavior | Expected result | Test |
|---|---|---|
| Compile start | One module-tagged line includes the source count | `CliCompileProgressSuite` |
| Default throttle | Progress lines are separated by at least 500 ms | `CliCompileProgressSuite` |
| Latest progress | The next allowed line uses current/total from the most recent event | `CliCompileProgressSuite` |
| Duplicate progress | No line is emitted when current/total did not change | `CliCompileProgressSuite` |
| Concurrent modules | All modules share one global progress budget | `CliCompileProgressSuite` |
| Disable option | Interval `0` emits no dedicated compile progress lines | `CliCompileProgressSuite` |
| Invalid interval | A negative server property fails configuration validation | `ServerPropertiesSuite` |
| Successful completion | Emits elapsed time and clears state | `CliCompileProgressSuite` |
| Failed completion | Emits an error, clears state, and does not emit success | `CliCompileProgressSuite` |
| Unrelated task progress | Ignored by the compile reporter | `CliCompileProgressSuite` |
| CLI wiring | A clean real compile prints start/progress/completion guidance on stderr | `IntegrationSuite` |

#### Verification

1. Prove against a clean temporary copy of a real project with a multi-source compile.
2. Run the focused unit suite.
3. Run the single affected integration test.
4. Check default, custom, and zero intervals in text and JSON output.
5. Document the property and its restart requirement in the server-properties reference.

#### Decision approved

- Default interval: 500 ms.
- The interval is project-global configuration in `.deder/server.properties`.
- `0` disables dedicated compile progress output.
- Throttling is global across concurrent compiles to prevent output multiplication.

### 3. Fix plugin and plugin-task discovery

Status: approved

#### Problem

- `deder plugins` claims tasks appear with debug logging, but `DederCliPluginsOptions` has no log-level option and non-exec requests default to `INFO`.
- As a result, CLI users cannot reveal plugin task names through the documented path.
- `deder plugins -f dot` and `-f mermaid` emit an error but exit successfully.

#### Proposed behavior

- Show configured plugins and their task names in plain output by default.
- Sort task names deterministically.
- Preserve configured plugin order and keep the existing truncated load-error output for failed plugins.
- Keep JSON output structured through `LoadedPluginInfo`; do not embed presentation text.
- Reject `dot` and `mermaid` with exit code `1`, an error log, and no success output.

Example:

```text
hello-plugin (hello)
broken-plugin (error: Failed to load plugin 'broken-plugin': ...)
```

#### Approach

1. Remove the effectively unreachable `debug` switch from `PluginsOutput`.
2. Render task names when present and sort them.
3. Keep JSON serialization based directly on `LoadedPluginInfo`.
4. Change the unsupported-format branch to record an error on the OTEL span and send `Exit(1)`.
5. Keep configured plugin ordering; only task names are sorted.

#### Behavior checklist

| Behavior | Expected result | Test |
|---|---|---|
| Loaded plugin task names | Plain output includes all task names without a debug flag | `CliPluginsOutputSuite` |
| Deterministic tasks | Task names are sorted | `CliPluginsOutputSuite` |
| Plugin ordering | Configured plugin order is preserved | `CliPluginsOutputSuite` |
| Failed plugin | Existing error text remains visible and truncated | `CliPluginsOutputSuite` |
| JSON output | Structured task names remain available | `CliClientMessageSuite` or integration |
| Unsupported DOT/Mermaid | Error and exit code `1`, no success output | Integration |
| Plugin CLI wiring | `deder plugins` reports real tasks from the loaded plugin | `PluginIntegrationSuite` |

#### Verification

1. Use the existing hello-plugin consumer as the real-project proof.
2. Run the focused unit suites.
3. Run the existing plugin integration suite.
4. Run one small integration case for an unsupported format.

#### Decision approved

- Plugin task names are visible by default without an opt-in flag.

### 4. Update the Scala 3.10 JDK compatibility matrix

Status: approved

Source: [Scala JDK Compatibility](https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html)

Verified 2026-11-17:

- Scala 3.10.0 is the minimum Scala version for JDK 27.
- Scala 3.10.1 is the minimum Scala version for JDK 28.
- Scala 3.8 and newer require JDK 17 or newer.
- Deder itself remains on Scala 3.9 LTS; this item covers building and testing Scala 3.10 consumer projects.

#### Problem

- Deder's `JdkUtils` matrix stops at JDK 26 and Scala 3.3.8.
- Deder has no CI matrix proving that the server and Zinc integration work with Scala 3.10 projects on newer supported JDKs.
- Deder's minimum server JDK is 21, so JDK 17 cannot be part of the server-runtime CI matrix even though Scala 3.10 supports it.

#### Proposed behavior

- Teach `JdkUtils` these minimums:

  | JDK | Minimum Scala 3 version |
  |---|---|
  | 27 | 3.10.0 |
  | 28 | 3.10.1 |

- Keep the Deder compiler/runtime on Scala 3.9 LTS.
- Add a minimal Scala 3.10.1 compile-and-test fixture.
- Add a compatibility smoke job over JDK 21, 25, 26, 27, and 28.
- Treat JDK 28 as early access using `28-ea` unless GitHub provides a GA version when implementation starts.
- Keep the existing full unit and integration jobs on JDK 21 to avoid multiplying expensive CI work.

#### Approach

1. Add JDK 27 and 28 entries to `JdkUtils.minimumVersions`.
2. Add a minimal `scala-310-compat` integration fixture using Scala 3.10.1 and MUnit.
3. Add one focused integration case that compiles and runs the fixture.
4. Reuse the server and test-runner JAR artifacts built by the existing Linux CI job.
5. Add a small `jdk-compat` matrix job using `actions/setup-java@v5` and the existing Zulu distribution.
6. Use `21`, `25`, `26`, `27`, and `28-ea` as matrix values.
7. Run the single Scala 3.10 fixture test in each matrix entry with `localPath`/`testRunnerLocalPath`.
8. Do not restore Deder output caches across JDK versions.
9. Document the tested JDK range without changing the documented minimum server JDK.

#### Behavior checklist

| Behavior | Expected result | Test |
|---|---|---|
| JDK 27 threshold | Scala 3.10.0 is accepted; Scala 3.9.x is rejected | `JdkUtilsSuite` |
| JDK 28 threshold | Scala 3.10.1 is accepted; Scala 3.10.0 is rejected | `JdkUtilsSuite` |
| Scala 3.10 fixture | Scala 3.10.1 compiles and tests on JDK 21 | Integration |
| JDK 25/26 compatibility | The same fixture compiles and tests | CI matrix |
| JDK 27 compatibility | The same fixture compiles and tests | CI matrix |
| JDK 28-ea compatibility | The same fixture compiles and tests | CI matrix |
| Existing Deder build | Deder itself remains on Scala 3.9 LTS | Unit/assembly CI |

#### Verification

1. Run the focused `JdkUtilsSuite`.
2. Run the Scala 3.10 fixture test locally on the available JDK.
3. Confirm matrix jobs use the locally built server/test-runner artifacts, not only the released bootstrap.
4. Confirm the full Ubuntu suite and release assembly still use Scala 3.9 LTS.

#### Decision approved

- Smoke matrix: JDK 21, 25, 26, 27, and 28-ea.
- Deder's own compiler/runtime remains on Scala 3.9 LTS.
- The matrix uses one minimal Scala 3.10.1 compile-and-test fixture rather than multiplying the full test suite.

### 5. Expose task descriptions through `deder tasks`

Status: approved

#### Problem

- `Task` and the public plugin `TaskInfo` already carry descriptions and metadata.
- The CLI defines a smaller private `TaskInfo` containing only the task name and feature tags.
- Plain `deder tasks` therefore shows categories and names but no explanation of what tasks do.
- Most built-in tasks have no description assigned even though the task reference documents their behavior.

#### Proposed behavior

- Keep default `deder tasks` categorized and one line per task.
- Show a concise description beside every public task by default.
- Include `description` and `category` in JSON output.
- Show feature tags as today.
- Populate descriptions for public built-in tasks, prioritizing Build, Configuration, Dependencies, Verification, Run, and Publishing tasks.
- Leave internal tasks out of the public listing as they are today.
- Keep `-l` / `--log-level` exclusively for diagnostic logging; do not use it to change the amount of task information.

Example:

```text
server:
  Build:
    compile       Compile Scala and Java sources with Zinc.
    classes       Return the module's class output directory.
  Verification:
    test          Run tests in isolated forked JVM processes.
```

#### Approach

1. Extend the CLI task projection with `description` and `category`.
2. Keep one task per line and align descriptions using the longest visible task name in each module/category.
3. Include descriptions and categories in JSON.
4. Add concise descriptions to public built-in task declarations.
5. Do not add descriptions to internal tasks unless they become part of the public surface.
6. Keep descriptions single-line and focused on user-visible behavior.
7. Use `-m <module>` to reduce an overly broad list rather than overloading logging controls.

#### Behavior checklist

| Behavior | Expected result | Test |
|---|---|---|
| Default output | One aligned description per public task | `TasksOutputSuite` |
| Log-level separation | `-l` does not change task output | `TasksOutputSuite` |
| Missing description | Rendering remains stable without a dangling separator | `TasksOutputSuite` |
| Structured metadata | JSON contains name, category, description, and feature tags | `TasksOutputSuite` |
| Built-in descriptions | Representative public tasks have non-empty descriptions | Integration |
| Plugin descriptions | Plugin-supplied descriptions appear automatically | Existing plugin integration |

#### Verification

1. Run `deder tasks` and `deder tasks -m server` against the Deder project.
2. Compare representative descriptions with the task reference to avoid contradictory copy.
3. Run focused unit tests.
4. Run one integration assertion for detailed output.

#### Decision approved

- Show concise task descriptions by default.
- Do not add `--details`.
- Do not make `-l` control task detail.
- Keep the existing `LogLevel` enums and naming; all remaining uses represent actual log/message severity.

### 6. Reject invalid module filters in `deder tasks`

Status: approved

#### Problem

- `deder tasks -m typo` currently renders an empty task result and exits successfully.
- The module filter is applied only in the plain-text renderer; JSON output currently serializes every module despite a supplied `-m`.

#### Proposed behavior

- Accept one or more `-m` selectors, including `%` inclusion wildcards and `~` exclusions.
- Resolve selectors with the same wildcard/recommendation utility used by the other CLI commands.
- On an unknown module, show the closest module IDs when available and exit `1`.
- Apply the module filter consistently to plain, JSON, dense JSON, DOT, and Mermaid output.
- Preserve current behavior when `-m` is omitted.

#### Approach

1. Replace the single optional module ID with multiple selectors.
2. Resolve selectors against `tasksResolver.allModules` using the existing recommendation utility.
3. Build a filtered task/module projection once before rendering.
4. Pass only the filtered modules into the output model so every formatter shares the same contract.

#### Behavior checklist

| Behavior | Expected result | Test |
|---|---|---|
| Valid module | Only that module's tasks are rendered | Integration |
| Unknown module | Error and exit code `1` | Integration |
| Included wildcard | `%` selects every matching module | Unit/integration |
| Excluded wildcard | `~` excludes matching modules | Unit/integration |
| Multiple selectors | Multiple `-m` flags compose | Unit/integration |
| Suggestion | Similar module IDs are offered when available | Unit/integration |
| JSON filter | JSON contains only the requested module | `TasksOutputSuite` |
| Graph formats | DOT and Mermaid contain only the requested module | `TasksOutputSuite` |
| No filter | All public modules are rendered as today | `TasksOutputSuite` |

#### Verification

1. Run valid and invalid `deder tasks -m ...` commands against the multi-module sample project.
2. Compare plain and JSON output for the same valid filter.
3. Run focused unit tests.

#### Decision approved

- Support `%` inclusion and `~` exclusion patterns.
- Accept multiple `-m` selectors.
- Apply the module filter consistently across every output format.

## Deferred ideas

### 7. Explain task execution and cache outcomes

Status: deferred

#### Why deferred

- `deder exec -l debug` already logs cache hits with `Using cached result for <task>`.
- The proposed trace would not explain why an input hash changed, which is the main diagnostic question.
- Adding `--explain` would require changing `TaskExecResult` and threading a full-result observer through the executor for limited new information.
- Keep this out of the current batch unless a concrete cache/minimality investigation requires richer per-request tracing.

#### Problem

- `TaskExecResult.Success` already records `changed` and `fromCache`, but neither reaches the CLI.
- Users can see that a build took time or finished, but not which tasks executed, cache-hit, changed outputs, or were skipped.
- Full cache-invalidation reasons are not retained, so claiming to explain why an input hash changed would require additional previous-metadata state.

#### Proposed behavior

- Add `deder exec --explain`.
- Keep the normal task summary unchanged.
- When enabled, print a compact execution trace to stderr:

  ```text
  Execution:
    [CACHE] common.sourceFiles
    [RUN]   common.compile — output changed — 1.42s
    [RUN]   backend.compile — output unchanged — 830ms
    [SKIP]  uber.compile — dependency failed
  ```

- Include all task instances from the requested execution graph, not only the target task.
- Preserve stdout for normal task summaries and JSON output.
- Do not claim a cache-miss reason in this version; report only observed outcome, output-change state, and duration.

#### Approach

1. Add `explain: Flag` to `DederCliExecOptions`.
2. Add task duration to each `TaskExecResult` variant so explanation output does not depend on lifetime aggregate statistics.
3. Add an optional per-result observer to `TasksExecutor.execute`; collect results only when explanation is requested.
4. Thread the collected trace through `DederProjectState.executeCLI`.
5. Add a focused formatter for execution rows and deterministic ordering by stage/task ID.
6. Emit the explanation through normal `INFO` logs after execution completes.
7. Keep watch mode behavior unchanged in the first version; each rerun explains through the existing notification stream.

#### Behavior checklist

| Behavior | Expected result | Test |
|---|---|---|
| Cache hit | Marked `CACHE` | `ExecutionExplanationSuite` |
| Executed with changed output | Marked `RUN` with `output changed` | `ExecutionExplanationSuite` |
| Executed with unchanged output | Marked `RUN` with `output unchanged` | `ExecutionExplanationSuite` |
| Failed task | Marked `FAIL` with the existing error | `ExecutionExplanationSuite` |
| Skipped task | Marked `SKIP` with the failed dependency | `ExecutionExplanationSuite` |
| Duration | Each completed task has its own duration | `ExecutionExplanationSuite` |
| Default behavior | No explanation output without `--explain` | Integration |
| Real build | A clean/incremental pair demonstrates the trace | Manual/integration |
| JSON stdout | Remains valid and free of explanation text | Integration |

#### Verification

1. Run a real project once with `--explain` after cleaning, then again without source changes.
2. Verify both traces against the actual task graph.
3. Run focused executor/formatter tests.
4. Confirm normal and JSON output remain unchanged without `--explain`.

#### Decision needed

- Confirm the first version reports observed execution outcome, output-change state, and duration, but not full cache-input reasons. Recommended, because those values already exist and require no speculative invalidation model.
