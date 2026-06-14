---
layout: reference.html
title: Task caching
---

# Task caching

Deder caches task outputs on disk so tasks whose inputs haven't changed don't re-run. This is separate from Scala incremental compilation: when `compile` does run it invokes Zinc, which does incremental compilation internally.

## How caching behaves

### Cached vs always-run tasks

Whether a task is *cached* or *always runs* is noted in the [Task Reference](/reference/tasks.html) — that page is the authoritative inventory.

To verify against source, grep `server/src` for `CachedTaskBuilder` (cached). Always-run tasks extend `TaskImpl` — this includes `TaskBuilder`-constructed tasks as well as `ConfigValueTask` (config reads) and `SourceFileTask`/`SourceFilesTask` (source-file tracking).

`compile` is a `CachedTask`, so it *should* skip when its inputs are unchanged — but see the "never content-hash a task's own outputs" anti-pattern below: it currently has self-referential dependencies that keep it from hitting. Even when it does run, Zinc skips unchanged sources internally.

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

`deder clean -t <task-name>` removes `.deder/out/<module>/<task>/` for the specified task across all modules. Combine `-m` and `-t` to target a specific task on specific modules:

```shell
deder clean -t compile
deder clean -m mymodule -t test
deder clean -m mod% -t compile%
```

Wildcard patterns (`%`) are supported for both `-m` and `-t` flags.

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
compile (CachedTask) ───────────────► skips if its inputs are unchanged
                                       outputHash = hash(classes dir)
         │
         ▼
assembly (CachedTask) ──────────────► skips if compile's outputHash unchanged
```

### `Hashable[T]` derivation

Explicit instances live in `server/src/ba/sake/deder/Hashable.scala`: `Int`, `String`, `Boolean`, `os.Path`, `Option[T]`, `Seq[T]`, `Map[K,V]`. A low-priority given derives `Hashable[T]` from `JsonRW[T]` by hashing the JSON string, so any config type is automatically hashable. Explicit instances take priority — `os.Path` hashes file/directory contents (recursively, with leaf names bound to child hashes) rather than the path string.

### Anti-pattern: never content-hash a task's own outputs

`Hashable[os.Path]` / `Hashable[DederPath]` hash directory **contents** recursively, and a cache key is built from its dependencies' `outputHash`es. Therefore a task must **never** take a content-hashed dependency (`dependsOn`) on a directory that the task itself — or any task downstream of it — writes into.

This is **not** a task-graph cycle, so cycle detection will not catch it. The DAG stays acyclic (e.g. `compile → compileClasspath → allClassesDirs → classes`); the coupling is through the **filesystem**: `compile` writes the class dir that one of its own dependencies content-hashes. The consequences:

- **The task can never cache-hit.** Each run rewrites its output → its own next input hash changes → it re-runs forever. Compounded by the fact that `.class` / `.semanticdb` bytes are *not* byte-stable across otherwise-identical recompiles.
- **Every build pays a full content re-hash** of a large output tree — e.g. ~14k `.class` files for a ~1.3k-source module is several seconds, even on a no-op.

Rules:

1. A task's cache key must be derived only from its true **inputs** (sources, compiler options, *upstream* module outputs) — never its own outputs.
2. If a task needs an output path at **execute time** (e.g. the module's own classes dir on the compiler classpath, needed for javac annotation processing where generated Java sources feed Scala), **derive the path** in `execute` (`ctx.out / os.up / "classes"`) — do not add a content-hashed `dependsOn`.
3. Represent a produced directory's "version" by its **producer's input hash** (identity), not by re-hashing its bytes. Cheaper, and immune to non-deterministic compiler output.
4. Build outputs live under `.deder/out/<module>/<task>/`. A content-hashed cache input pointing inside that tree (for the same or a downstream module) is the smell to watch for — consider it a bug.

> **Tracked offenders (2026-06-14):** `compileTask` depends on `semanticdbDirTask`, and (via `compileClasspathTask → allClassesDirsTask`) on its own `classesTask` — both directories `compile` writes. Until they're removed from `compile`'s cache key (derive the paths at execute time instead), `compile` re-runs Zinc and re-hashes its class tree on every no-op.

### Known limitations

- **Transitive dep hashing is first-level only.** A change deep in the dep chain doesn't necessarily invalidate downstream cache. Only if the direct task deps results have changed, the cached task will be reevaluated
- **`Hashable[os.Path]` throws on non-file non-directory existing paths** (e.g. sockets). Missing paths return `""`.
