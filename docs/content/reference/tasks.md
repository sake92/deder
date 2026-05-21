---
layout: reference.html
title: Task Reference
---

# Task Reference

Deder's built-in tasks are grouped into categories that broadly match the CLI (`deder tasks`).
Most tasks can be invoked with `deder exec -t <task> -m <module>`.

> **Note:** A small number of public tasks have no category assigned in source and therefore appear under **Other** in `deder tasks` output. They are collected in the [Other](#other) section at the bottom of this page.

> **Caching:** whether a task is *cached* (skipped when inputs are unchanged) or *always runs* is documented in [Task caching](/reference/caching.html).

---

## Build

These tasks compile source code and manage the compile output directory.

| Task | Module types | What it does |
|---|---|---|
| `compile` | all | Invokes Zinc to compile Scala/Java sources. Always runs (Zinc handles incremental compilation internally). |
| `classes` | all | Returns the class output directory for the module. Always runs. |
| `semanticdb` | all | Returns the SemanticDB output directory for the module. Always runs. |

---

## Configuration

Configuration tasks read values from `deder.pkl` and are the building blocks for higher-level tasks.
They are useful in `deder plan` output and can be inspected directly.

| Task | Module types | What it does |
|---|---|---|
| `sources` | all | Returns the list of configured source root directories. |
| `sourceFiles` | all | Returns all `.scala`/`.java` source files found under the source roots. Cached. |
| `generatedSources` | all | Returns the directory for annotation-processor-generated sources. |
| `resources` | all | Returns the list of configured resource root directories. |
| `javaHome` | all | Returns the configured JDK home path (`javaHome` in `deder.pkl`), or `None`. |
| `javaVersion` | all | Returns the configured `--release` target version for `javac`. |
| `javacOptions` | all | Returns the `javacOptions` list from `deder.pkl`. |
| `scalacOptions` | Scala | Returns the `scalacOptions` list from `deder.pkl`. |
| `scalaVersion` | Scala | Returns the Scala version configured for the module. |
| `jvmOptions` | all | Returns the `jvmOptions` list from `deder.pkl` (passed to `java` on `run` / `test`). |
| `semanticdbEnabled` | all | Returns whether SemanticDB generation is enabled for the module. |
| `javaSemanticdbVersion` | all | Returns the `semanticdb-javac` version to use. |
| `scalaSemanticdbVersion` | Scala | Returns the `semanticdb-scalac` (Scala 2) or built-in (Scala 3) SemanticDB version. Cached. |

---

## Dependencies

Dependency tasks resolve and fetch Maven/Ivy coordinates declared in `deder.pkl`.

| Task | Module types | What it does |
|---|---|---|
| `deps` | all | Returns the raw dependency declarations from `deder.pkl`. |
| `compileOnlyDeps` | all | Returns compile-only dependency declarations (excluded from the runtime classpath). |
| `repositories` | all | Returns the effective list of Maven repositories (user-configured + defaults). |
| `compileClasspath` | all | Resolves and fetches all JARs needed for compilation. Cached. |
| `scalacPluginDeps` | Scala | Returns Scalac plugin dependency declarations from `deder.pkl`. |
| `scalacPlugins` | Scala | Fetches the Scalac plugin JARs. Cached. |
| `javacAnnotationProcessorDeps` | all | Returns annotation-processor dependency declarations from `deder.pkl`. |
| `javacAnnotationProcessors` | all | Fetches the annotation-processor JARs. Cached. |
| `compilerDeps` | all | Returns the Scala compiler dependency coordinates for the configured `scalaVersion` (used by Scala-based tasks). Cached. |

---

## Verification

| Task | Module types | What it does |
|---|---|---|
| `test` | `JAVA_TEST`, `SCALA_TEST` | Runs tests in a forked JVM. Supports test-suite and test-name filters as task arguments. Always runs. |
| `testInMemory` | `JAVA_TEST`, `SCALA_TEST` | Runs tests in the server's own JVM (no fork). Faster but shares the server classpath. Always runs. |
| `testClasses` | `JAVA_TEST`, `SCALA_TEST`, `SCALA_JS_TEST`, `SCALA_NATIVE_TEST` | Discovers test classes and test frameworks from the compiled classes. Cached. |
| `fix` | Scala, Scala.js & Scala Native (incl. test variants) | Runs Scalafix rewrites on source files. Always runs. |
| `fixCheck` | Scala, Scala.js & Scala Native (incl. test variants) | Runs Scalafix in check-only mode (fails if rewrites would be needed). Always runs. |

**Test filtering examples:**

```shell
deder exec -t test -m mymodule-test                 # all tests in module
deder exec -t test -m mymodule-test com.example.%   # suites matching prefix
deder exec -t test -m mymodule-test com.example.FooSuite#test1  # single test
```

---

## Run

| Task | Module types | What it does |
|---|---|---|
| `run` | all | Runs the module's configured (or auto-detected) main class in a forked process. Supports `--watch`. Always runs. |
| `runMain` | all | Runs an explicitly named main class. Pass the class name as the first task argument. Always runs. |
| `runMvnApp` | all | Runs a named Maven application entry point declared in `mvnApps` in `deder.pkl`. Built-in apps: `fmt` and `fmtCheck` (Scalafmt, Scala modules only). Always runs. |
| `runClasspath` | all | Resolves the full runtime classpath. Cached. |
| `mainClasses` | all | Discovers all classes with a `main` method. Cached. |
| `mainClass` | all | Returns the explicitly configured `mainClass` from `deder.pkl`. |
| `finalMainClass` | all | Returns the resolved main class: explicit config wins, falls back to the single discovered main. Cached. |

**Examples:**

```shell
deder exec -t run -m myapp
deder exec -t run -m myapp --watch
deder exec -t runMain -m myapp com.example.AltMain
deder exec -t runMvnApp -m myapp fmt      # format with Scalafmt
deder exec -t runMvnApp -m myapp fmtCheck # check formatting
```

---

## Publishing

Publishing tasks build JARs, assemble uber-JARs, and publish Maven artifacts.

| Task | Module types | What it does |
|---|---|---|
| `jar` | all | Builds a regular module JAR (compiled classes + resources + manifest). Cached. |
| `allJars` | all | Collects the JARs from this module and all its transitive module dependencies. Cached. |
| `assemblyDeps` | `JAVA`, `JAVA_TEST`, `SCALA`, `SCALA_TEST` | Resolves and merges all dependency JARs into a single fat dependency JAR consumed by `assembly`. Cached. |
| `assembly` | `JAVA`, `JAVA_TEST`, `SCALA`, `SCALA_TEST` | Builds an uber-JAR (all classes + all dependency JARs merged). Optionally applies jarjar shading rules. Cached. |
| `moduleDepsPomSettings` | all | Collects POM settings from this module and its direct module dependencies, used when generating the POM for publishing. Cached. |
| `sourcesJar` | all | Builds a `-sources.jar`. Only produced when `pomSettings` is configured. Cached. |
| `javadocJar` | all | Builds a `-javadoc.jar`. Only produced when `pomSettings` is configured. Cached. |
| `publishArtifacts` | all | Assembles all artifacts (JAR, sources JAR, Javadoc JAR, POM XML) into a staging directory ready for publishing. Only produces output when `pomSettings` is configured. Cached. |
| `version` | all | Returns the resolved module version: `pomSettings.version` if set, otherwise auto-detected from git tags (SemVer). Always runs. |
| `finalManifest` | all | Merges `manifest` config with the resolved main class and version. Cached. |
| `publishLocal` | all | Publishes the module to the local Maven repository (`~/.m2/repository`) or configured `publishLocalTo` path. Requires `publish = true` in `deder.pkl`. Always runs. |
| `publish` | all | Publishes the module to a remote Maven repository. Requires publish credentials (`publishTo`) in config/credentials. Always runs. |

**Examples:**

```shell
deder exec -t assembly -m myapp
java -jar .deder/out/myapp/assembly/out.jar

deder exec -t publishLocal -m mylib
deder exec -t publish -m mylib
```

> For jar shading (`assembly` only), set `shadeRulesFile` on the module in `deder.pkl` to point to a jarjar rules file.

---

## REPL

| Task | Module types | What it does |
|---|---|---|
| `replDeps` | all | Returns the Scala REPL/compiler dependency coordinates for the configured `scalaVersion`. Returns an empty list for non-Scala modules. Cached. |
| `replJars` | all | Fetches the JAR files listed in `replDeps`. Cached. |
| `repl` | `JAVA`, `JAVA_TEST`, `SCALA`, `SCALA_TEST` | Starts an interactive REPL with the module's runtime classpath. Scala modules get a Scala REPL; Java modules get `jshell`. Always runs. |

```shell
deder exec -t repl -m mymodule
```

---

## Scala.js

All tasks in this section apply to modules of type `SCALA_JS` or `SCALA_JS_TEST`.

| Task | Module types | What it does |
|---|---|---|
| `fastLinkJs` | `SCALA_JS`, `SCALA_JS_TEST` | Links JS in fast/development mode (no full optimisation). Cached. |
| `fullLinkJs` | `SCALA_JS`, `SCALA_JS_TEST` | Links JS with full optimisation (production mode). Cached. |
| `linkJs` | `SCALA_JS`, `SCALA_JS_TEST` | Links JS with the mode configured in `deder.pkl` (delegates to `fastLinkJs` or `fullLinkJs`). Cached. |
| `runJs` | `SCALA_JS` | Runs the linked JS output with Node.js. Always runs. |
| `test` | `SCALA_JS_TEST` | Runs Scala.js tests via Node.js. Always runs. |

```shell
deder exec -t fastLinkJs -m myjsmodule
deder exec -t runJs -m myjsmodule
deder exec -t test -m myjsmodule-test
```

---

## Scala Native

All tasks in this section apply to modules of type `SCALA_NATIVE` or `SCALA_NATIVE_TEST`.

> **Note:** `fastNativeLink`, `fullNativeLink`, and `runNative` have no category in source and appear under **Other** in `deder tasks` output; they are listed in the [Other](#other) section below.

| Task | Module types | What it does |
|---|---|---|
| `nativeLink` | `SCALA_NATIVE`, `SCALA_NATIVE_TEST` | Links a native binary with the mode configured in `deder.pkl`. Cached. |
| `test` | `SCALA_NATIVE_TEST` | Runs Scala Native tests. Always runs. |

```shell
deder exec -t nativeLink -m mynativemodule       # mode from config
deder exec -t test -m mynativemodule-test
```

---

## GraalVM

GraalVM tasks apply to `JAVA` and `SCALA` modules that have a `graalvm { }` block in `deder.pkl`.

| Task | Module types | What it does |
|---|---|---|
| `graalvmHome` | `JAVA`, `SCALA` | Returns the GraalVM installation path (`graalvm.graalvmHome` in config, or `$GRAALVM_HOME` env var). |
| `nativeImageOptions` | `JAVA`, `SCALA` | Returns the extra `native-image` CLI flags from `graalvm.nativeImageOptions` in `deder.pkl`. |
| `nativeIncludedResourcesOptions` | `JAVA`, `SCALA` | Scans the module's resource directories and returns the `-H:IncludeResources=…` option for `native-image`. Cached. |
| `graalvmReachabilityMetadataOptions` | `JAVA`, `SCALA` | Downloads the GraalVM reachability-metadata archive and returns the `-H:ConfigurationFileDirectories=…` flags for matching dependencies. Cached. |
| `graalvmNativeImage` | `JAVA`, `SCALA` | Compiles the module to a native executable using `native-image`. Downloads and applies GraalVM reachability metadata automatically. Cached. |

```shell
deder exec -t graalvmNativeImage -m myapp
.deder/out/myapp/graalvmNativeImage/native-executable
```

> Add a `graalvm { }` block to the module in `deder.pkl` with at minimum `graalvmHome` (or set the `$GRAALVM_HOME` environment variable) to enable these tasks.

---

## Other

These tasks have no category assigned in source and therefore appear under **Other** in `deder tasks` output.

| Task | Module types | What it does |
|---|---|---|
| `manifest` | all | Returns the `manifest` settings from `deder.pkl`. |
| `pomSettings` | all | Returns the resolved POM settings (group ID, artifact ID with Scala suffix, version). |
| `fastNativeLink` | `SCALA_NATIVE`, `SCALA_NATIVE_TEST` | Links a native binary in fast/debug mode. Cached. |
| `fullNativeLink` | `SCALA_NATIVE`, `SCALA_NATIVE_TEST` | Links a native binary with full optimisation (release mode). Cached. |
| `runNative` | `SCALA_NATIVE` | Runs the fast-linked native binary. Always runs. |
