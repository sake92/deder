# Deder Audit Report

**5. TODO `--ascii` plan output is unusably wide**
- `deder plan -m uber -t compile --ascii` produces output that is hundreds of thousands of characters wide (the truncated output was 391KB+). This makes it unusable in a terminal.
- `--dot` format works well and can be piped to graphviz.
- **Fix:** Either limit line width with wrapping/truncation, or make the ASCII renderer use a more compact layout (vertical instead of horizontal).

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
| **Dependency exclusions** | Not documented | Not clear if you can exclude transitive dependencies. |
| **Custom task definition** | Missing | All tasks are predefined. Users can't define custom tasks in the config. sbt/Mill/Gradle all support this. |
| **Console/REPL** | Missing | sbt has `console`, Mill has `console`. Starts a Scala REPL with project classpath. |
| **Resource generation** | Partial | `generatedSources` exists but no `generatedResources` task visible. |
| **Parallel test execution** | Unclear | Not documented whether tests within a suite run in parallel. |
| **Code coverage** | Missing | sbt-scoverage, JaCoCo integration. |
| **Benchmarking (JMH)** | Missing | sbt-jmh, Mill JMH module. |
| **Bill of Materials (BOM) support** | Missing | Maven BOM imports for dependency management. |
| **Init/scaffold command** | Missing | `sbt new`, `mill init`, `gradle init` - create project from template. |
| **Repository configuration** | Not documented | No way to add custom Maven repositories (private artifactory, etc.) visible in config. |
| **Multi-JDK testing** | Missing | Gradle toolchains, sbt-javaversions for matrix testing across JDK versions. |
| **Incremental test execution** | Unclear | Not documented whether only affected tests re-run after changes. |

---

## Documentation Gaps

### Missing Documentation

1. **No config reference page** - There's no documentation of all available Pkl config fields. Users have to read the raw `DederProject.pkl` or guess. This is the biggest doc gap. Other build tools have extensive config references.



4. **No `import` command documentation** - `deder import --from sbt` is mentioned in passing in `index.md` but has no tutorial or reference explaining what it imports, how accurate it is, or what manual fixups are needed after import.

5. **No error handling / troubleshooting guide** - Common errors (Pkl evaluation failures, dependency resolution failures, compilation errors) and how to debug them.


9. **No module type reference** - Which tasks are available for which module types (Java vs Scala vs ScalaJS vs ScalaNative)? 
The `tasks` command shows all tasks per-module, but there's no reference for what each task does.



13. **No Windows support notes** - Docs mention Unix sockets but don't state whether Windows is supported, partially supported, or not supported at all.

### Documentation Quality Issues

14. **No "how it works" section for caching** - `design.md` mentions Blake3 hashing and relative paths but doesn't explain it practically. When does the cache invalidate? How do you force a rebuild?


16. **No migration guide** - For users coming from sbt/Mill/Gradle, a mapping of concepts would be very helpful.

17. **Scalafmt integration not documented** - The index.md mentions `deder exec -t runMvnApp fmt` and `fmtCheck` but doesn't explain how to set up scalafmt configuration (`.scalafmt.conf`), where the scalafmt dep comes from, or how to configure it.
