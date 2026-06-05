# Deder Audit Report

## Missing Functionality vs Other JVM Build Tools


| Feature | Status in Deder | Notes |
|---------|----------------|-------|
| **Dependency updates checker** | Missing | sbt-updates, Mill's dependency updates. Shows available version bumps. |
| **Code coverage** | Missing | sbt-scoverage, JaCoCo integration. |
| **Benchmarking (JMH)** | Missing | sbt-jmh, Mill JMH module. |
| **Bill of Materials (BOM) support** | Missing | Maven BOM imports for dependency management. |
| **Init/scaffold command** | Missing | `sbt new`, `mill init`, `gradle init` - create project from template. |
| **Multi-JDK testing** | Missing | Gradle toolchains, sbt-javaversions for matrix testing across JDK versions. |

---

## Documentation Gaps

5. **No error handling / troubleshooting guide** - Common errors (Pkl evaluation failures, dependency resolution failures, compilation errors) and how to debug them.


16. **No migration guide** - For users coming from sbt/Mill/Gradle, a mapping of concepts would be very helpful.

17. **Scalafmt integration not documented** - The index.md mentions `deder exec -t runMvnApp fmt` and `fmtCheck` but doesn't explain how to set up scalafmt configuration (`.scalafmt.conf`), where the scalafmt dep comes from, or how to configure it.
