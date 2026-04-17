---
layout: howto.html
title: Running tests in parallel
---

# {{ page.title }}

By default, Deder runs test classes **serially** within a single test JVM. This matches sbt (`Test / testForkedParallel = false`) and Mill (`testForked`), and avoids flakiness caused by tests that share mutable state (static fields, `System.setProperty`, security providers, etc.).

If your tests are safe to run concurrently within the same JVM, opt in per-module with the `testParallelism` field:

```pkl
// deder.pkl — example for a Scala test module
testTemplate = (template.asTest()) {
  testParallelism = 8
  // ...
}
```

Supported on:

- `JavaTestModule`
- `ScalaTestModule`
- `ScalaJsTestModule`
- `ScalaNativeTestModule`

Default is `1` (serial). Valid range is `1` to `256`.

## When should I increase it?

Increase `testParallelism` only if **all** of the following hold for the module:

- No tests mutate static fields, system properties, security providers, default locale/timezone, or other JVM-global state.
- No tests write to shared temp files or shared ports.
- Test frameworks that do their own intra-class parallelism (JUnit Jupiter `@Execution(CONCURRENT)`, ZIO Test, weaver, etc.) still work; the `testParallelism` knob controls **class-level** concurrency only — framework-level parallelism is independent.

If unsure, leave it at `1`. A single flaky test run is worth more than a few seconds saved.

## When should I leave it at 1?

- Legacy code with static singletons, caches, or process-level shared state.
- Tests with side effects on shared infrastructure (DB connections, mock servers on fixed ports).
- CI environments where reproducibility is more important than wall-clock time.

## Trade-offs vs. multi-process parallelism

Intra-JVM parallelism only shares one JVM's warm-up cost. For larger test suites where you want process-level isolation too (like Mill's `testParallel` workers), that's tracked as future work in Deder.
