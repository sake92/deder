# AGENTS.md

## Project Overview

Deder is a **client-server JVM build tool** for Scala/Java projects. 
Configuration is defined in [Pkl](https://pkl-lang.org/) (`deder.pkl`), the server compiles via Zinc, and communication happens over Unix domain sockets. 
It implements the [BSP (Build Server Protocol)](https://build-server-protocol.github.io/) for IDE integration.

Prefer to use these tools/skills if available:
- "scalex" for scala/java definitions, implementations, usages, imports, members, scaladoc, codebase overview, package 
API surface, files, annotated symbols, file contents etc

## Git Worktree (READ THIS FIRST)

**NEVER work directly on `main` unless the user explicitly tells you to.**

Before making any code changes:
1. Create an isolated worktree: `git worktree add .worktrees/<descriptive-branch-name>`
2. The branch name should describe the change (e.g., `fix/server-restart-hardening`, `feat/watch-ignore-paths`)
3. All edits, commits, and builds happen inside the worktree
4. This keeps `main` clean and avoids conflicts with the running Deder server
5. When finished, always run `deder shutdown && sleep 1` to stop the Deder server — especially before switching branches or deleting the worktree
6. A running Deder server holds `.deder/server-cli.sock` and `.deder/server-bsp.sock`. Leaving it alive can block socket binding on the next worktree, cause stale lockfiles, or leave orphaned compile daemons consuming resources

**Every feature, fix, or refactor starts with a worktree. No exceptions.**

## Architecture

```
client (Java, native-image) --Unix socket--> server (Scala 3)
                                               ├── CLI server  (.deder/server-cli.sock)
                                               └── BSP server  (.deder/server-bsp.sock)
```

- **`client/`** — Pure Java CLI client. Sends JSON messages over Unix socket, receives streamed responses. GraalVM native-image compatible.
- **`server/`** — Scala 3 server (`ba.sake.deder`). Long-running daemon with file watching, concurrent task execution, Zinc compilation, and BSP support.
- **`config/`** — Java module with Pkl-generated data classes from `config/DederProject.pkl`. Regenerate with `./scripts/gen-config-bindings.sh`.
- **`integration/`** — Integration tests that build server+client JARs and exercise real projects in `integration/test/resources/sample-projects/`.

## Build System

This project uses Deder to build itself (`deder.pkl` build file). Key commands:

```sh
./scripts/gen-config-bindings.sh   # regenerate Pkl→Java config bindings (required before first build)
./scripts/build-jars.sh            # build client, server and test-runner fat JAR (assembly)
deder exec -t test -m server-test  # run unit tests (munit)
./scripts/run-it-tests.sh          # build everything + run integration tests
./scripts/run-it-tests.sh ba.sake.deder.bsp.BspIntegrationSuite  # single IT suite
```

Check out @README.md @CONTRIBUTING.md @docs/content/reference/server-properties.md @docs/content/reference/cheatsheet.md for more details if needed.

## Key Patterns

- prefer running single unit test 
- prefer running single integration test, because they take very long and can be flaky

### Task DAG
The core abstraction is `Task[T, Deps]` (`server/src/ba/sake/deder/Task.scala`). Tasks form a typed DAG:
- `ConfigValueTask` — reads from Pkl config, cached by value hash
- `SourceFileTask`/`SourceFilesTask` — tracks source dirs, triggers watch mode
- `CachedTask` — skips re-execution when input hashes match (`metadata.json` in `.deder/out/<module>/<task>/`)
- `TaskBuilder.make[T]("name").dependsOn(otherTask).build { ctx => ... }` — fluent task construction
- Tasks are defined in `CoreTasks.scala` and registered via `TasksRegistry`
- **CRITICAL**: Any data class that is a task result type (`T` in `Task[T, Deps, S]`) and contains `DederPath` or `os.Path` fields MUST have a custom `Hashable[T]` instance. The low-priority JSON-based `Hashable` fallback serializes paths as strings — missing actual file/directory content changes. Without a custom `Hashable`, downstream `CachedTask`s will hit stale cache entries because their `inputsHash` (computed from upstream `outputHash`es) never changes. See `CompileResult` for an example of a custom `Hashable` that includes content hashing.
- **CRITICAL (the other direction)**: A task must **never** content-hash its own outputs as cache *inputs*. `Hashable[os.Path]`/`Hashable[DederPath]` hash directory **contents**, so a task must not `dependsOn` any directory it (or a downstream task) writes into. This is a *filesystem* feedback loop, **not** a graph cycle — the DAG stays acyclic, so cycle detection won't catch it — and it makes the task never cache-hit plus re-hash a large output tree (e.g. ~14k `.class` files) on every build, including no-ops. If you need an output path at execute time (e.g. the module's own classes dir on the compiler classpath for javac annotation processing), **derive it** in `execute` (`ctx.out / os.up / "classes"`), do not `dependsOn` it. Build outputs live under `.deder/out/<module>/<task>/`; a content-hashed input pointing inside that tree is the smell. See `docs/content/reference/caching.md` → "Anti-pattern: never content-hash a task's own outputs" (tracks the current `compile` → `semanticdbDir` / `classes` offenders).

### Task Execution Flow
1. `TasksResolver` builds a JGraphT `SimpleDirectedGraph[TaskInstance, DefaultEdge]` from modules × tasks
2. `ExecutionPlanner` computes topologically-sorted execution stages (parallelizable groups)
3. `TasksExecutor` runs stages concurrently using a fixed thread pool, with per-`TaskInstance` locking

### Configuration
- Project config: `deder.pkl` (amends `config/DederProject.pkl`)
- Server config: `.deder/server.properties` (logLevel, maxInactiveSeconds, bspEnabled)
- Config classes are **generated Java code** in `config/src/` — never edit directly; modify `config/DederProject.pkl` and run the gen script

### Module Types
`ModuleType` enum: `JAVA`, `SCALA`, `SCALA_TEST`, `SCALA_JS`, `SCALA_NATIVE`. Task availability is filtered by `supportedModuleTypes` on each task.

### Client-Server Protocol
CLI communication uses newline-delimited JSON over Unix sockets. Message types are in `cli/CliClientMessage.scala` and `cli/CliServerMessage.scala`. BSP uses JSON-RPC via `bsp4j` / `lsp4j`.

### Paths
`DederPath` wraps project-root-relative `os.SubPath`. Always resolve to absolute via `.absPath`. The project root is set via `DederGlobals.projectRootDir` (from system property `DEDER_PROJECT_ROOT_DIR`).

## Conventions

- **Scala 3.7.x** with `os-lib` for filesystem ops, `tupson` for JSON serialization, `mainargs` for CLI parsing
- **Logging**: `StrictLogging` trait from scala-logging (backed by Logback)
- **OpenTelemetry**: tracing spans wrap BSP/CLI requests (`OTEL.TRACER`, see `OTEL.scala`). Use `traced()` / `javaFuture()` wrappers in BSP server
- **Test framework**: munit for both unit and integration tests
- **Examples**: `examples/` contains working sample projects (multi-module, cross-platform, ScalaJS, etc.). Each has a `reset.sh` that copies the server JAR and runs `deder bsp install`
- **Design docs**: Never commit files in `docs/superpowers/` directory
- **Git**: Do not commit anything without explicit user permission
- **Git**: See the `## Git Worktree` section at the top of this file — always use worktrees for code changes

## Output Layout

All build artifacts go under `.deder/out/<moduleId>/<taskName>/`. Cache metadata is in `metadata.json` per task. Server logs go to `.deder/logs/`.

## Testing

**Make a change work in a real/example project FIRST, then write the tests.** When fixing behavior (especially caching/incremental/build-graph changes), build the server and verify the actual behavior against an `examples/` scenario (or another real project) — confirm it does what you intended — *before* writing or tuning integration tests. Don't iterate on a test harness to infer whether the fix works; prove it in a real project, then encode that as a test. (A loose `String.contains` assertion or harness quirk can give false greens — e.g. `"...for compile"` matches `compilerJars`/`compileOnlyDeps`.)

Check @CONTRIBUTING.md for technical details, how to test server, client, test-runner changes locally.  

Use "deder shutdown && sleep 1" to kill the server.

You can use one of @examples/ scenarios to test changes, just make sure to revert when you finish testing.

You can clean results/cache/metadata per task and per module with "deder clean -m mymodule -t mytask".  
Or leave out both to clean everything.

Keep integration test classes rather small, because then it is easier to run them one by one.  
Run minimal affected integration tests, rarely all of them.

Never run `deder` directly inside `integration/test/resources/sample-projects/**`. Those directories are shared fixtures, and Deder creates runtime artifacts in `.deder/` (including Unix sockets) that can pollute later tests. Always copy the fixture to `tmp/` first via `withTestProject` / `stageTestProject` or an equivalent temp staging helper, and when copying fixtures ignore any existing top-level `.deder/` directory instead of propagating it.

## JarJar Shading (`jarjar-abrams 1.16.0`)

Assembly shading is configured via `shadeRulesFile` on a module in `deder.pkl`.  
The rules file uses standard jarjar directives (`rule`, `keep`, `zap`).  
Implementation: `PublishTasks.scala` reads the config → `JarUtils.scala` calls `Shader.parseRulesFile` + `Shader.shadeFile`.

### Critical pitfalls learned from shading the test-runner

1. **`keep` drops unreachable classes — NOT a "preserve" directive.**  
   In jarjar-abrams, `keep` marks classes as roots for dependency analysis; all classes not reachable from roots are **discarded**. Use a no-op `rule` to protect classes from renaming: `rule sbt.** sbt.@1`.

2. **Jarjar rewrites ALL bytecode references matching any rule pattern**, even for classes not in the JAR itself (e.g. `java.lang.Object`).  
   Always add no-op rules for JDK/standard-library packages BEFORE any shade rules:
   ```
   rule java.** java.@1
   rule javax.** javax.@1
   rule scala.** scala.@1
   ```

3. **Broad wildcard patterns (`*.**`) cause `ArrayIndexOutOfBoundsException`** in jarjar-abrams' `ScalaSigAnnotationVisitor` when rewriting Scala signature annotations.  
   Use targeted package-specific rules (`ba.sake.tupson.** → shaded.ba.sake.tupson.@1`) instead of catch-all patterns.

4. **Tupson's `@type` discriminator uses simple names** (e.g. `"ForkStarted"`, not FQCN), so the JSON protocol is unaffected by shading.  
   Bytecode references to renamed classes are rewritten, but string literals (including tupson's `@type` values) are not.

5. **The `mainClass` in `deder.pkl` and the server's hardcoded main-class string must match the post-shading name.**  
   The `ForkedTestOrchestrator` (in the unshaded server JAR) must reference the shaded main class name when spawning forked test JVMs.
