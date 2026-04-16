---
layout: reference.html
title: Task caching
---

# Task caching

Deder caches task outputs on disk so tasks whose inputs haven't changed don't re-run. This is separate from Scala incremental compilation: the `compile` task itself always re-invokes Zinc, and Zinc does incremental compilation internally.

## How caching behaves

### Cached vs always-run tasks

The lists below are accurate as of deder 0.3.4. To verify against current source, grep `server/src` for `CachedTaskBuilder` (cached) and `= TaskBuilder` (always-run).

**Cached** (skip when inputs unchanged):

| Task | What it produces |
|---|---|
| `sourceFiles` | List of source file paths |
| `dependencies` | Direct module dependencies |
| `allDependencies` | Dependencies including transitive |
| `mandatoryDependencies` | Dependencies that must be present |
| `compileClasspath` | Classpath used for compilation |
| `allClassesDirs` | Class output directories across modules |
| `compilerDeps` | Compiler dependency resolution |
| `compilerJars` | Resolved compiler JARs |
| `scalacPlugins` | Scalac plugin JARs |
| `javacAnnotationProcessors` | Annotation processor JARs |
| `scalaSemanticdbVersion` | Resolved SemanticDB version |
| `runClasspath` | Classpath used at runtime |
| `mainClasses` | Discovered main classes |
| `finalMainClass` | Selected main class |
| `testClasses` | Test class discovery |
| `fastLinkJs` | Scala.js fast link output |
| `nativeLink` | Scala Native link output |
| `jar` | Module JAR |
| `allJars` | Aggregated JARs |
| `sourcesJar` | Sources JAR |
| `javadocJar` | Javadoc JAR |
| `assembly` | Uber JAR |
| `finalManifestSettings` | Resolved JAR manifest settings |
| `moduleDepsPomSettings` | POM dep settings |
| `publishArtifacts` | Artifact set to publish |

**Always runs:**

| Task | What it does |
|---|---|
| `generatedSources` | Runs source generators |
| `classes` | Resolves module class output directory |
| `semanticdbDir` | Resolves SemanticDB output directory |
| `compile` | Invokes Zinc |
| `run` | Runs the module |
| `runMain` | Runs a specified main class |
| `runMvnApp` | Runs a Maven-application entry point |
| `fix` | Runs Scalafix |
| `fixCheck` | Runs Scalafix in check mode |
| `test` | Runs tests |
| `testJs` | Runs Scala.js tests |
| `testNative` | Runs Scala Native tests |
| `publishLocal` | Publishes to local Ivy repository |
| `publish` | Publishes to remote repository |

`compile` is "always runs" from deder's perspective, but Zinc skips unchanged sources internally — so unchanged compilations are still cheap even without deder-level caching.

### Where cached data lives

Every cached task writes a `metadata.json` (plus any task-specific artifacts) under:

```
.deder/out/<module-id>/<task-name>/metadata.json
```

`metadata.json` stores the task's stored value, `inputsHash`, and `outputHash`.

### What invalidates a cached task

- Source **file content** change.
- Source **filename** change within a source directory
- Config value change in `deder.pkl` that the task reads.
- Any dependency task's `outputHash` changing (propagates up the chain).

### Clearing the cache

`deder clean -m <module-id>` removes `.deder/out/<module>/` entirely (all cached artifacts and metadata for that module). Multiple `-m` flags clean multiple modules:

```shell
deder clean -m mymodule
deder clean -m mod1 -m mod2
```

Clean requires exact module ids. Wildcard patterns that work with `exec` (e.g. `mymodule%`) are **not** supported by `clean`.

No CLI command today clears a single task on one module or clears the whole project. Manual fallbacks:

- Single task: `rm -rf .deder/out/<module>/<task>/`
- Whole project: `rm -rf .deder/out/`

## How caching works

### Two task kinds

- **`TaskImpl`** — always executes. Computes `outputHash = Hashable[T].hashStr(result)` so downstream tasks can detect changes. No `inputsHash` is stored; `changed` is always reported as `true`.
- **`CachedTask`** — computes `inputsHash = hash(deps' outputHashes joined)`. If a `metadata.json` with the same `inputsHash` already exists on disk, the stored result is reused; otherwise the task re-executes and writes new metadata. `changed = newOutputHash != oldOutputHash`. A `CachedTask` must have at least one dep — enforced at compile time via `Deps <:< NonEmptyTuple` on `CachedTaskBuilder.build`.

### The hash chain

```
SourceFileTask (CachedTask leaf) ──► outputHash = hash(file contents)
         │
         ▼
compileClasspath (CachedTask) ──────► inputsHash = hash(dep outputHashes)
         │                             outputHash = hash(result)
         ▼
compile (TaskImpl) ─────────────────► always runs
                                       outputHash = hash(classes dir)
         │
         ▼
assembly (CachedTask) ──────────────► skips if compile's outputHash unchanged
```

### `Hashable[T]` derivation

Explicit instances live in `server/src/ba/sake/deder/Hashable.scala`: `Int`, `String`, `Boolean`, `os.Path`, `Option[T]`, `Seq[T]`, `Map[K,V]`. A low-priority given derives `Hashable[T]` from `JsonRW[T]` by hashing the JSON string, so any config type is automatically hashable. Explicit instances take priority — `os.Path` hashes file/directory contents (recursively, with leaf names bound to child hashes) rather than the path string.

### Known limitations

- **Transitive dep hashing is first-level only.** `inputsHash` folds in `depResults ++ transitiveResults.headOption` (see `Task.scala:217`). A change deep in the dep chain that doesn't surface in a first-level output won't invalidate downstream cache.
- **No `changed`-based short-circuiting.** A `CachedTask` reporting `changed = false` doesn't stop downstream tasks from running; the flag is only surfaced in task results for reporting.
- **`Hashable[os.Path]` throws on non-file non-directory existing paths** (e.g. sockets). Missing paths return `""`.
