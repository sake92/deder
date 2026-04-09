# Deder Task Caching: Design Analysis

## Task Types (current design)

- **`TaskImpl`** (created via `TaskBuilder.make[T].build{}`): always re-executes. Computes a real `outputHash = Hashable[T].hashStr(result)` after each run. Returns `changed = true` unconditionally (it always ran).
- **`CachedTask`** (created via `CachedTaskBuilder.make[T].build{}`): compares `inputsHash` (hash of all dep `outputHash`es) against stored metadata. Only re-executes if hash mismatch. Returns real `outputHash` and accurate `changed` flag. Reserved for tasks that can be skipped when inputs are unchanged.

### Leaf task subclasses of `TaskImpl`
- `SourceFileTask` — outputs a `DederPath`. Its `Hashable[DederPath]` hashes the *actual file/dir contents* recursively. Fast.
- `SourceFilesTask` — same, for multiple paths.
- `ConfigValueTask` — outputs a config value `T`. Its `Hashable[T]` hashes the config value.

All three always run (they're `TaskImpl`). They produce a real `outputHash` so downstream `CachedTask`s can detect changes.

### `Hashable[T]` derivation
All `JsonRW[T]` types automatically get a low-priority `Hashable[T]` derived by hashing the JSON string. Explicit instances (e.g. `DederPath`, `os.Path`, `String`) take priority and hash more efficiently/accurately.

### `CachedTaskBuilder` — for future skippable tasks
`CachedTaskBuilder` is defined and ready to use. Currently no tasks use it, but it is the right tool for computation tasks like `compileTask` once we want build-cache semantics.

---

## The Hash Chain — How it Works

### All `TaskImpl` chain: `TaskImpl ← TaskImpl ← TaskImpl`
All always run. `outputHash` propagates correctly — downstream tasks can build on accurate hashes.

### With a `CachedTask`: `CachedTask ← TaskImpl ← TaskImpl`
1. Leaf `TaskImpl` (e.g. SourceFileTask): runs, `outputHash = hash(file_contents)`.
2. Middle `TaskImpl`: runs, `outputHash = hash(its_result)`.
3. Top `CachedTask`: `inputsHash = hash(middle.outputHash)`. If middle's output changed → recomputes. Otherwise → skips.

→ **Correct. The hash chain propagates cleanly through both `TaskImpl` and `CachedTask`.**

### `CachedTask ← CachedTask`
Both may skip. Bottom skips if its inputs unchanged. Top skips if bottom's `outputHash` unchanged.

→ **Also correct.**

---

## Comparison With Other Build Tools

### Mill
- **Every task is cached by default.** No distinction between "always run" and "skip if cached" — all tasks are the latter.
- Inputs are hashed (previous task output hashes + file content hashes). If hash matches cached → skip.
- `changed` propagates: if an intermediate task's output didn't change, downstream tasks are skipped.
- Outputs are stored content-addressed in `out/` as JSON.

### sbt
- **Tasks are NOT cached by default.** `Def.task` always re-runs.
- File-based caching is opt-in via `FileFunction.cached` / `Tracked`.
- Zinc incremental compilation is separate from sbt's task caching.

### Bazel
- **Everything is hermetic and CAS-cached.** Inputs are fully declared. Same inputs always → same outputs.
- No "run always" concept. Caching is the default, bypass is the exception.
- Hermetic: file access outside declared inputs is illegal (sandbox).

### Deder (current design)
- `TaskImpl` = always runs, but tracks output hash for downstream use. Leaf tasks are `TaskImpl`.
- `CachedTask` = may skip if `inputsHash` matches; reserved for computation tasks (future).
- Zinc handles incremental compilation internally (separate from deder caching).
- Closer to sbt today; the infrastructure exists to approach Mill-style full caching incrementally.

---

## Design Gaps & Issues

### 1. `CachedTask` is unused for computation tasks
All ~30+ tasks in `CoreTasks`, `ScalaJsTasks`, `ScalaNativeTasks` use `TaskImpl`. `CachedTask` / `CachedTaskBuilder` are ready but not yet applied to things like `compileTask`. Converting `compileTask` would give build-cache semantics (skip Zinc on unchanged inputs).

### 2. No short-circuiting on `changed = false`
Even if a `CachedTask` says `changed = false`, the executor still runs all downstream tasks. In Mill, if an intermediate task's output hash didn't change, downstream tasks are skipped.

The executor uses `changed` only for `TaskExecResult` (surfaced to the caller), not for stage skipping. This is acceptable now, but would be a valuable optimization once more tasks become `CachedTask`.

### 3. `CachedTask` hashes only first-level transitive deps
```scala
val allDepResults = depResults ++ transitiveResults.headOption.getOrElse(Seq.empty) // only first level for hashing
```
A change deep in the transitive dep chain may not invalidate a `CachedTask` that only indirectly depends on it.

### 4. `Hashable[os.Path]` throws on non-existent non-file non-dir paths
```scala
} else {
  throw DederException(s"Cannot hash path: ${value}")
}
```
`DederPath.Hashable` handles `!os.exists` by returning `""`. But `Hashable[os.Path]` directly throws if the path is neither a file nor a dir.

### 5. `Hashable[os.Path]` ignores file names in hash (known TODO)
```scala
// TODO add file path into hash
```
Directory hashing only hashes file contents, not file names. Renaming files in a directory wouldn't change the hash.

---

## Should We Cache More?

### `compileTask` (currently `TaskImpl`)
`compileTask` returns `DederPath` (path to classes dir). `DederPath` already has both `JsonRW` and `Hashable` (hashes dir contents). Converting `compileTask` to `CachedTask` via `CachedTaskBuilder` is feasible:
- `inputsHash` = hash of all dep outputs (source hashes, classpath, scalac options, ...)
- If `inputsHash` matches → skip Zinc entirely → big speedup on cold starts / CI
- If not → run Zinc (which still does incremental compilation internally)

**This is essentially a "build cache" on top of incremental compilation, like Mill.**

All `compileTask` deps are `TaskImpl` and now produce real `outputHash`es — the blocker is removed.

### General guideline
- Tasks that read from filesystem or config → `TaskImpl` leaf (done: SourceFileTask, ConfigValueTask)
- Pure transformations over hashable inputs (e.g. classpath merging) → `CachedTask` with deps (future)
- Compilation → `CachedTask` (future)
- Tasks with side effects that must always run (e.g. `run`, `test`) → `TaskImpl`

---

## Tests to Add

### Unit tests (pure task behavior, no real files needed)

1. **`CachedTask` basic caching**: With dep on a `TaskImpl` — second run with same dep output → uses cache (execute fn not called again).
2. **`CachedTask` invalidation**: Change the dep's output → top `CachedTask` recomputes.
3. **`changed` flag accuracy**:
   - `CachedTask` cache hit → `changed = false`
   - `CachedTask` cache miss + output same as before → `changed = false`
   - `CachedTask` cache miss + output different → `changed = true`
4. **`TaskImpl` always runs**: Called twice with same inputs → execute fn called both times. But `outputHash` is consistent.
5. **Corrupted metadata.json**: Corrupt the JSON for a `CachedTask` → falls back to recompute, no exception surfaced.
6. **Hash chain**: `CachedTask ← TaskImpl ← TaskImpl` — change bottom TaskImpl output → middle TaskImpl output changes → top CachedTask is invalidated.

### Integration tests

7. **Compilation cache**: Run compile on unchanged project → verify Zinc is not invoked on second run (relevant once `compileTask` becomes `CachedTask`).
8. **Incremental invalidation**: Change one source file → only affected modules recompile.
9. **Config change invalidation**: Change a config value in `deder.pkl` → tasks depending on that config re-run.

---

## Open Questions

- Do we want a "force" flag to bypass cache (like `mill clean` or Bazel's `--cache_test_results=no`)?
- Should short-circuiting on `changed = false` be implemented before or after converting computation tasks to `CachedTask`?
- Is there a risk of cache poisoning from partial writes to `metadata.json` during a crash? `TupsonException` handles corrupted reads, but a partial write may produce valid but stale JSON.
- Should `Hashable[os.Path]` fix the TODO and include file names in the directory hash?


## Current State

### Two task types
- **`TaskImpl`** (created via `TaskBuilder.make[T].build{}`): always re-executes. Returns `outputHash = ""`, `changed = true` unconditionally.
- **`CachedTask`** (created via `CachedTaskBuilder.make[T].build{}`): compares `inputsHash` (hash of all dep `outputHash`es) against stored metadata. Only re-executes if hash mismatch. Returns real `outputHash` and accurate `changed` flag.

### Current CachedTask subclasses (all leaf tasks, no deps)
- `SourceFileTask` — outputs a `DederPath`. Its `Hashable[DederPath]` hashes the *actual file/dir contents* recursively. Fast.
- `SourceFilesTask` — same, for multiple paths.
- `ConfigValueTask` — outputs a config value `T`. Its `Hashable[T]` hashes the config value string.

All three have `EmptyTuple` deps. This triggers a **critical branch** in `CachedTask.executeUnsafe`:

```scala
val hasDeps = allDepResults.nonEmpty
val newRes = if hasDeps && inputsHash == cachedTaskResult.inputsHash then
  // use cache
else computeTaskResult()
```

→ **`hasDeps = false` always forces recompute.** This is intentional for leaf tasks (they're fast — just hash a path/config value), but is a semantic trap: a `CachedTask` with no deps always runs.

### `CachedTaskBuilder` exists but is unused in CoreTasks
All ~30+ tasks in `CoreTasks.scala` use `TaskBuilder` (non-cached). `CachedTaskBuilder` is defined but only used by `SourceFileTask`, `SourceFilesTask`, `ConfigValueTask`.

---

## The Hash Chain — How it Works (and Breaks)

### Happy path: `CachedTask ← CachedTask ← CachedTask`
1. Leaf `CachedTask` (SourceFileTask): always runs, computes `outputHash = hash(file_contents)`.
2. Middle `CachedTask`: `inputsHash = hash(leaf.outputHash)`. If file changed → `inputsHash` changes → recomputes → produces new `outputHash`.
3. Top `CachedTask`: `inputsHash = hash(middle.outputHash)`. Correctly invalidated when middle changes.

→ **Correct. The hash chain propagates cleanly.**

### Broken: `CachedTask ← TaskImpl ← CachedTask`
1. Bottom `CachedTask` (leaf): runs, produces `outputHash = hash(file_contents)`.
2. Middle `TaskImpl`: always runs. `outputHash = ""` regardless of what it computed.
3. Top `CachedTask`: `inputsHash = hash("")` = **constant**. Gets cached once and *never invalidated*, even if the bottom CachedTask's output changes.

→ **Stale cache forever. Silent wrong behavior.**

### Also broken: `CachedTask ← TaskImpl` (top is CachedTask)
Same problem. Top CachedTask caches based on `hash("")` from the TaskImpl dep.

### Fine: `TaskImpl ← CachedTask` (top is TaskImpl)
Top always runs regardless. The CachedTask's result is used as input, but caching/invalidation doesn't matter since the top runs always anyway.

### Fine: `TaskImpl ← TaskImpl`
Both always run. No caching anywhere.

---

## Comparison With Other Build Tools

### Mill
- **Every task is cached by default.** No distinction between "cached" and "uncached" tasks.
- Inputs are hashed (previous task output hashes + file content hashes). If hash matches cached → skip.
- `changed` propagates: if an intermediate task's output didn't change, downstream tasks are skipped.
- Outputs are stored content-addressed in `out/` as JSON.

### sbt
- **Tasks are NOT cached by default.** `Def.task` always re-runs.
- File-based caching is opt-in via `FileFunction.cached` / `Tracked`.
- Zinc incremental compilation is separate from sbt's task caching.

### Bazel
- **Everything is hermetic and CAS-cached.** Inputs are fully declared. Same inputs always → same outputs.
- No "run always" concept. Caching is the default, bypass is the exception.
- Hermetic: file access outside declared inputs is illegal (sandbox).

### Deder (current)
- Hybrid, closer to sbt: leaf tasks cached, computation tasks always run.
- Zinc handles incremental compilation internally (separate from deder caching).
- Key gap vs Mill: hash chain is broken when `TaskImpl` sits between two `CachedTask`s.

---

## Design Gaps & Issues

### 1. `TaskImpl.outputHash = ""` breaks the hash chain
Any `CachedTask` downstream of a `TaskImpl` will have a constant `inputsHash` → stale cache.

**Possible fix:** Make `TaskImpl` compute and store `outputHash` too (even though it always re-runs). This way the chain propagates correctly even in mixed graphs.

### 2. `hasDeps = false` → always recompute in CachedTask
Leaf tasks with `EmptyTuple` deps always recompute. This is fast and intentional, but should be documented. The naming is confusing — "CachedTask" that always runs is not "cached."

**Alternative name:** `TrackedTask` (tracks output hash, detects changes, but doesn't skip execution). The `CachedTask` name implies skipping — it only skips when `hasDeps = true` AND hash matches.

### 3. No short-circuiting on `changed = false`
Even if a dep task says `changed = false`, the executor still runs all downstream tasks. In Mill, if an intermediate task's output hash didn't change, downstream tasks can be skipped.

The executor gets `changed` per task but only uses it for `TaskExecResult` (surfaced to the caller). It doesn't use `changed` to skip stages.

**Impact:** Currently acceptable since all computation tasks are `TaskImpl` (always `changed = true`). But if we convert computation tasks to `CachedTask`, we'd want to short-circuit.

### 4. `CachedTask` result caching depends on transitive deps — first level only
```scala
val allDepResults = depResults ++ transitiveResults.headOption.getOrElse(Seq.empty) // only first level for hashing
```
Only the first level of transitive deps is included in `inputsHash`. This means a change deep in the dep chain may not invalidate a `CachedTask` that only transitively depends on it.

### 5. `Hashable[os.Path]` throws on non-existent non-file non-dir paths
```scala
} else {
  throw DederException(s"Cannot hash path: ${value}")
}
```
`DederPath.Hashable` handles `!os.exists` by returning `""`. But `Hashable[os.Path]` directly throws. If someone creates a `CachedTask` that returns an `os.Path` directly and the path doesn't exist, it crashes.

### 6. `Hashable[os.Path]` ignores file names in hash (known TODO)
```scala
// TODO add file path into hash
```
Directory hashing only hashes file contents, not file names. Renaming files in a directory wouldn't change the hash.

---

## Sandwich Combos — Summary Table

| Pattern | Behavior | Issue? |
|---|---|---|
| `Cached ← Cached ← Cached` | Correct invalidation chain | None |
| `Cached ← TaskImpl ← Cached` | Top Cached never invalidated | **Bug: stale cache** |
| `TaskImpl ← Cached ← Cached` | Bottom tasks cached correctly, top always runs | OK (top always runs) |
| `Cached ← TaskImpl` | Same as broken pattern above | **Bug: stale cache** |
| `TaskImpl ← Cached` | Cached caches based on dep `outputHash=""` = constant | OK-ish if dep output is stable |
| `TaskImpl ← TaskImpl` | Both always run | Correct (no caching) |

→ **The rule**: a `CachedTask` downstream of a `TaskImpl` will always have a constant `inputsHash`. This effectively disables its caching. The combo should either be disallowed at construction time, or `TaskImpl` must emit a real `outputHash`.

---

## Should We Cache More?

### `compileTask` (currently `TaskImpl`)
`compileTask` returns `DederPath` (path to classes dir). `DederPath` already has both `JsonRW` and `Hashable` (hashes dir contents). Converting `compileTask` to `CachedTask` is feasible:
- `inputsHash` = hash of all dep outputs (source hashes, classpath, scalac options, ...)
- If `inputsHash` matches → skip Zinc entirely → big speedup on cold starts / CI
- If not → run Zinc (which still does incremental compilation internally)

**This is essentially a "build cache" on top of incremental compilation, like Mill.**

Blockers:
1. All `compileTask` deps are `TaskImpl` (`outputHash = ""`), so `inputsHash` would always be constant → never skip.
2. Fix: convert the entire dep chain to `CachedTask`, or at minimum have `TaskImpl` compute `outputHash`.

### General guideline (proposed)
- Tasks that read from the filesystem or config → `CachedTask` leaf (already done)
- Pure transformations over hashable inputs (e.g. classpath merging) → `CachedTask` with deps (currently `TaskImpl`)
- Tasks with side effects that must always run (e.g. `run`, `test`) → `TaskImpl` or a new `SideEffectTask` concept
- Compilation → `CachedTask` once the full dep chain is `CachedTask`

---

## Proposed Design Decisions

1. **Should `TaskImpl` emit a real `outputHash`?**
   - Yes → enables mixed graphs, preserves the hash chain through non-cached tasks
   - No → mixed graphs remain broken; must document or enforce that `CachedTask` cannot depend on `TaskImpl`

2. **Should we introduce short-circuiting based on `changed = false`?**
   - If a `CachedTask` says `changed = false`, skip all downstream tasks in that stage
   - Prerequisite: all intermediate tasks must compute honest `outputHash`s

3. **Should we convert `compileTask` (and its deps) to `CachedTask`?**
   - High value: skips compilation on unchanged inputs (build cache)
   - Requires converting the whole dep chain first

4. **Should disallowed combos be enforced at compile time?**
   - Currently `CachedTaskBuilder.dependsOn` accepts any `Task[T2, ?]` including `TaskImpl`
   - Could restrict to only accept `CachedTask` deps (type-level enforcement)

5. **Rename `CachedTask`-with-no-deps to `TrackedTask`?**
   - Clearer semantics: "always runs, but tracks output hash for downstream invalidation"
   - `CachedTask` with deps = "may skip if inputs unchanged"

---

## Tests to Add

### Unit tests (pure `CachedTask` behavior, no real files needed)

1. **Basic caching**: `CachedTask` with dep on another `CachedTask` — second run with same dep output → uses cache (execute fn not called again).
2. **Invalidation**: Change the dep's output → top `CachedTask` recomputes.
3. **`changed` flag accuracy**:
   - Cache hit → `changed = false`
   - Cache miss + output same as before → `changed = false`
   - Cache miss + output different → `changed = true`
4. **`hasDeps = false` always recomputes**: A `CachedTask` with `EmptyTuple` deps always calls execute, even on second run. But `changed` correctly reflects whether output changed.
5. **Corrupted metadata.json**: Corrupt the JSON → falls back to recompute, no exception surfaced.
6. **Sandwich `CachedTask ← TaskImpl ← CachedTask`**: Assert that the top `CachedTask` is never re-executed even when the bottom changes. Documents the known bug.

### Integration tests

7. **Compilation cache**: Run compile on unchanged project → verify Zinc is not invoked on second run (relevant once `compileTask` becomes `CachedTask`).
8. **Incremental invalidation**: Change one source file → only affected modules recompile.
9. **Config change invalidation**: Change a config value in `deder.pkl` → tasks depending on that config re-run.

---

## Open Questions

- Do we want a "force" flag to bypass cache (like `mill clean` or Bazel's `--cache_test_results=no`)?
- Should `outputHash` be persisted even for `TaskImpl` (for downstream chain), or is always-recompute better?
- Is there a risk of cache poisoning from partial writes to `metadata.json` during a crash? `TupsonException` handles corrupted reads, but a partial write may produce valid but stale JSON.
- Should `Hashable[os.Path]` fix the TODO and include file names in the directory hash?
