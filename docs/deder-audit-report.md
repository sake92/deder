# Deder Audit Report

Tested all 6 example projects (multi, java-scala, publish, scalajs, scalanative, cross) against the documentation on 2026-03-28.

## Testing Summary

| Example | compile | run | test | assembly | publishLocal | nativeLink | fastLinkJs |
|---------|---------|-----|------|----------|-------------|------------|------------|
| **multi** | PASS | PASS | PASS (expected fail) | PASS | - | - | - |
| **java-scala** | PASS (warnings) | PASS | - | - | - | - | - |
| **publish** | PASS | - | - | - | PASS | - | - |
| **scalajs** | PASS | - | PASS (expected fail) | - | - | - | PASS |
| **scalanative** | PASS | - | - | - | - | PASS | - |
| **cross** | PASS | - | - | - | - | - | - |

---

## Issues Sorted by Severity

### CRITICAL

None found. All examples compile and run correctly.

### HIGH




**3. `deder exec --help` triggers compilation instead of showing help**
- Running `deder exec --help` actually starts compiling all modules (it treats `--help` as a no-op flag and proceeds with default behavior).
- This is confusing for new users who expect `--help` to work on subcommands. The workaround `deder help -c exec` works but is non-standard.
- **Fix:** Intercept `--help` flag in the exec subcommand parser and display help instead.

### MEDIUM

**4. `design.md` uses incorrect CLI syntax**
- `docs/content/philosophy/design.md` says `deder -t compile --watch` but the correct syntax is `deder exec -t compile --watch`. The `exec` subcommand is required.
- Running `deder -t compile --watch` just prints the help message.
- **Fix:** Update the three occurrences in design.md: `deder -t compile --watch` -> `deder exec -t compile --watch`, `deder -t run -m A` -> `deder exec -t run -m A`, `deder -t compile -m A` -> `deder exec -t compile -m A`.

**5. `--ascii` plan output is unusably wide**
- `deder plan -m uber -t compile --ascii` produces output that is hundreds of thousands of characters wide (the truncated output was 391KB+). This makes it unusable in a terminal.
- `--dot` format works well and can be piped to graphviz.
- **Fix:** Either limit line width with wrapping/truncation, or make the ASCII renderer use a more compact layout (vertical instead of horizontal).

**6. `bsp` command not listed in help**
- `deder --help` does not list `bsp` or `bsp install` as available commands, but they work.
- **Fix:** Add `bsp install` to the help output.

**7. `#todo-fixme` left in published docs**
- `docs/content/index.md:136` says: `"- run main scala classes (Java doesnt.. #todo-fixme )"` This is a raw TODO left in user-facing documentation.
- **Fix:** Either fix the underlying issue (Java main class running) or remove/rewrite this line to be clearer about the limitation.

**8. Empty modules in `java-scala` example**
- `java-project/src/` and `scala-project/src/` directories exist but are completely empty. The compilation succeeds (no sources to compile) but this is confusing for someone studying the example.
- **Fix:** Add at least a trivial `Main.java` and `Main.scala` to demonstrate these module types.

**9. `integration/test/resources/third-party/sharaf/deder.pkl` uses old field names**
- Uses `_root`, `_template`, `_testTemplate`, `_jsTemplate`, `_nativeTemplate` throughout.
- This may work if those internal function-parameter names still function as aliases, but it's inconsistent with the current schema.
- **Fix:** Update to use `root`, `template`, `testTemplate`, etc.

### LOW

**10. `import --help` hangs/times out**
- `deder import --help` timed out after 30 seconds. This needs `deder help -c import` instead.
- Same issue as #3 but less commonly used.

**11. Version mismatch between client and server**
- Client reports `v0.0.21-SNAPSHOT`, server reports `v0.0.22-SNAPSHOT`. While this is expected during development, there's no warning about version mismatch.
- **Fix:** Consider adding a warning when client/server versions don't match.

**12. Annotation processor warnings in java-scala example**
- Compiling `java-scala-project` produces: `Could not determine source for class ba.sake.deder.javascalaproject.ImmutableBook$Builder` and `ImmutableBook`. These are generated classes from annotation processing, so the warnings are expected but noisy.
- **Fix:** Consider suppressing these warnings for annotation-processor-generated classes, or document this as expected behavior.

---

## Missing Functionality vs Other JVM Build Tools

Compared to **sbt**, **Mill**, **Gradle**, and **Maven**:

| Feature | Status in Deder | Notes |
|---------|----------------|-------|
| **Dependency tree visualization** | Missing | sbt has `dependencyTree`, Mill has `ivyDepsTree`. Useful for debugging dependency conflicts. |
| **Dependency updates checker** | Missing | sbt-updates, Mill's dependency updates. Shows available version bumps. |
| **Custom task definition** | Missing | All tasks are predefined. Users can't define custom tasks in the config. sbt/Mill/Gradle all support this. |
| **Console/REPL** | Missing | sbt has `console`, Mill has `console`. Starts a Scala REPL with project classpath. |
| **Multi-repo/composite builds** | Missing | Gradle composite builds, sbt's `ProjectRef`. Building across repo boundaries. |
| **Resource generation** | Partial | `generatedSources` exists but no `generatedResources` task visible. |
| **Test filtering by tag/category** | Partial | Can filter by suite name and test name, but no tag-based filtering (sbt `testOnly -- -t tag`). |
| **Parallel test execution** | Unclear | Not documented whether tests within a suite run in parallel. |
| **Code coverage** | Missing | sbt-scoverage, JaCoCo integration. |
| **Benchmarking (JMH)** | Missing | sbt-jmh, Mill JMH module. |
| **Docker image building** | Missing | sbt-native-packager, Jib (Gradle/Maven). |
| **Release/CI automation** | Partial | Has `publish` but no release workflow (bumping versions, creating tags, changelog). |
| **Bill of Materials (BOM) support** | Missing | Maven BOM imports for dependency management. |
| **Init/scaffold command** | Missing | `sbt new`, `mill init`, `gradle init` - create project from template. |
| **Dependency exclusions** | Not documented | Not clear if you can exclude transitive dependencies. |
| **Repository configuration** | Not documented | No way to add custom Maven repositories (private artifactory, etc.) visible in config. |
| **Source JARs download** | Unclear | Not documented for IDE sources attachment beyond BSP. |
| **Multi-JDK testing** | Missing | Gradle toolchains, sbt-javaversions for matrix testing across JDK versions. |
| **Incremental test execution** | Unclear | Not documented whether only affected tests re-run after changes. |
| **Build scan/report** | Partial | OTEL tracing exists (nice!) but no HTML build report like Gradle. |

---

## Documentation Gaps

### Missing Documentation

1. **No config reference page** - There's no documentation of all available Pkl config fields. Users have to read the raw `DederProject.pkl` or guess. This is the biggest doc gap. Other build tools have extensive config references.

2. **No `server.properties` reference** - The available server config options (`logLevel`, `workerThreads`, `maxInactiveSeconds`, `bspEnabled`, `localPath`, `JAVA_OPTS`) are only discoverable by reading example files. Needs a dedicated reference page.

3. **No `clean` command documentation** - `clean` is listed in `deder --help` but not documented anywhere in the docs.

4. **No `import` command documentation** - `deder import --from sbt` is mentioned in passing in `index.md` but has no tutorial or reference explaining what it imports, how accurate it is, or what manual fixups are needed after import.

5. **No error handling / troubleshooting guide** - Common errors (Pkl evaluation failures, dependency resolution failures, compilation errors) and how to debug them.

6. **No explanation of dependency syntax** - The docs say "Dependencies are written using Coursier's Dependency syntax" and link to the coursier repo, but don't explain the `::` vs `:::` vs `:` notation for Scala/cross-platform deps. This is critical for users coming from Maven/Gradle.

7. **No `assembly` task documentation** - Assembly (uber jar) is mentioned in the index.md cheat sheet but has no dedicated tutorial.

8. **No `jar` task documentation** - Creating a regular JAR (non-assembly) is not documented.

9. **No module type reference** - Which tasks are available for which module types (Java vs Scala vs ScalaJS vs ScalaNative)? The `tasks` command shows all tasks per-module, but there's no reference for what each task does.

10. **No `mvnApps` configuration reference** - The `mvn-apps.md` howto shows a brief snippet but doesn't explain all fields of `MvnApp` or how the fmt/fmtCheck shorthand works.

11. **Missing `manifest` and `ManifestSettings` docs** - The Pkl config has `ManifestSettings` class with `mainClass`, `classpathPrefix`, `extraAttributes` but none of this is documented.

12. **No `forkEnv` / `jvmOptions` documentation** - These module config fields are not documented despite being useful for passing env vars and JVM options.

13. **No Windows support notes** - Docs mention Unix sockets but don't state whether Windows is supported, partially supported, or not supported at all.

### Documentation Quality Issues

14. **No "how it works" section for caching** - `design.md` mentions Blake3 hashing and relative paths but doesn't explain it practically. When does the cache invalidate? How do you force a rebuild?

15. **The `index.md` is doing too much** - It's simultaneously a landing page, cheat sheet, IDE setup guide, and tips section. Should be split into focused pages.

16. **No migration guide** - For users coming from sbt/Mill/Gradle, a mapping of concepts would be very helpful.

17. **Scalafmt integration not documented** - The index.md mentions `deder exec -t runMvnApp fmt` and `fmtCheck` but doesn't explain how to set up scalafmt configuration (`.scalafmt.conf`), where the scalafmt dep comes from, or how to configure it.
