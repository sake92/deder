# Deder Audit Report

**5. `--ascii` plan output is unusably wide**
- `deder plan -m uber -t compile --ascii` produces output that is hundreds of thousands of characters wide (the truncated output was 391KB+). This makes it unusable in a terminal.
- `--dot` format works well and can be piped to graphviz.
- **Fix:** Either limit line width with wrapping/truncation, or make the ASCII renderer use a more compact layout (vertical instead of horizontal).

**8. Empty modules in `java-scala` example**
- `java-project/src/` and `scala-project/src/` directories exist but are completely empty. The compilation succeeds (no sources to compile) but this is confusing for someone studying the example.
- **Fix:** Add at least a trivial `Main.java` and `Main.scala` to demonstrate these module types.

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



4. **No `import` command documentation** - `deder import --from sbt` is mentioned in passing in `index.md` but has no tutorial or reference explaining what it imports, how accurate it is, or what manual fixups are needed after import.

5. **No error handling / troubleshooting guide** - Common errors (Pkl evaluation failures, dependency resolution failures, compilation errors) and how to debug them.


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
