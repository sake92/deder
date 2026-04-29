# AGENTS.md

## Project Overview

Deder is a **client-server JVM build tool** for Scala/Java projects. Configuration is defined in [Pkl](https://pkl-lang.org/) (`deder.pkl`), the server compiles via Zinc, and communication happens over Unix domain sockets. It implements the [BSP (Build Server Protocol)](https://build-server-protocol.github.io/) for IDE integration.

Prefer to use these tools/skills if available:
- "scalex" for scala/java definitions, implementations, usages, imports, members, scaladoc, codebase overview, package 
API surface, files, annotated symbols, file contents etc

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
deder exec -t assembly -m server   # build server fat JAR
deder exec -t assembly -m client   # build client fat JAR
deder exec -t test -m server-test  # run unit tests (munit)
./scripts/run-it-tests.sh          # build everything + run integration tests
./scripts/run-it-tests.sh ba.sake.deder.bsp.BspIntegrationSuite  # single IT suite
```

Check out @README.md @CONTRIBUTING.md @docs/content/reference/server-properties.md @docs/content/reference/cheatsheet.md for more details if needed.

## Key Patterns

- prefer running single test and integration test (because they take very long)

### Task DAG
The core abstraction is `Task[T, Deps]` (`server/src/ba/sake/deder/Task.scala`). Tasks form a typed DAG:
- `ConfigValueTask` — reads from Pkl config, cached by value hash
- `SourceFileTask`/`SourceFilesTask` — tracks source dirs, triggers watch mode
- `CachedTask` — skips re-execution when input hashes match (`metadata.json` in `.deder/out/<module>/<task>/`)
- `TaskBuilder.make[T]("name").dependsOn(otherTask).build { ctx => ... }` — fluent task construction
- Tasks are defined in `CoreTasks.scala` and registered via `TasksRegistry`

### Task Execution Flow
1. `TasksResolver` builds a JGraphT `SimpleDirectedGraph[TaskInstance, DefaultEdge]` from modules × tasks
2. `ExecutionPlanner` computes topologically-sorted execution stages (parallelizable groups)
3. `TasksExecutor` runs stages concurrently using a fixed thread pool, with per-`TaskInstance` locking

### Configuration
- Project config: `deder.pkl` (amends `config/DederProject.pkl`)
- Server config: `.deder/server.properties` (logLevel, workerThreads, maxInactiveSeconds, bspEnabled)
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

## Output Layout

All build artifacts go under `.deder/out/<moduleId>/<taskName>/`. Cache metadata is in `metadata.json` per task. Server logs go to `.deder/logs/`.

## Testing

Check @CONTRIBUTING.md for technical details, how to test server, client, test-runner changes locally.  

Keep integration test classes rather small, because then it is easier to run them one by one.  
Run minimal affected integration tests, rarely all of them.

