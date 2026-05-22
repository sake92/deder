---
layout: reference.html
title: Task caching
---

# Task caching

Deder caches task outputs on disk so tasks whose inputs haven't changed don't re-run. This is separate from Scala incremental compilation: the `compile` task itself always re-invokes Zinc, and Zinc does incremental compilation internally.

## How caching behaves

### Cached vs always-run tasks

Whether a task is *cached* or *always runs* is noted in the [Task Reference](/reference/tasks.html) — that page is the authoritative inventory.

To verify against source, grep `server/src` for `CachedTaskBuilder` (cached). Always-run tasks extend `TaskImpl` — this includes `TaskBuilder`-constructed tasks as well as `ConfigValueTask` (config reads) and `SourceFileTask`/`SourceFilesTask` (source-file tracking).

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
compile (TaskImpl) ─────────────────► always runs
                                       outputHash = hash(classes dir)
         │
         ▼
assembly (CachedTask) ──────────────► skips if compile's outputHash unchanged
```

### `Hashable[T]` derivation

Explicit instances live in `server/src/ba/sake/deder/Hashable.scala`: `Int`, `String`, `Boolean`, `os.Path`, `Option[T]`, `Seq[T]`, `Map[K,V]`. A low-priority given derives `Hashable[T]` from `JsonRW[T]` by hashing the JSON string, so any config type is automatically hashable. Explicit instances take priority — `os.Path` hashes file/directory contents (recursively, with leaf names bound to child hashes) rather than the path string.

### Known limitations

- **Transitive dep hashing is first-level only.** A change deep in the dep chain doesn't necessarily invalidate downstream cache. Only if the direct task deps results have changed, the cached task will be reevaluated
- **`Hashable[os.Path]` throws on non-file non-directory existing paths** (e.g. sockets). Missing paths return `""`.
