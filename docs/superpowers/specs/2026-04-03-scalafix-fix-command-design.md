# Scalafix "fix" Command Design

**Date:** 2026-04-03  
**Status:** Approved

## Overview

Add `fix` and `fixCheck` commands to Deder, similar to the existing `fmt`/`fmtCheck` pattern. The commands will use scalafix-cli to run automated code refactoring and linting rules.

**Key features:**
- Two commands: `fix` (applies changes) and `fixCheck` (verifies without modifying)
- Supports both syntactic and semantic scalafix rules
- Configuration via `.scalafix.conf` file
- Fixed scalafix-cli version: `0.12.1`

## Architecture

The implementation creates two new dedicated tasks (`fixTask` and `fixCheckTask`) in `CoreTasks.scala` that:

1. Depend on compilation tasks to ensure semanticdb files exist
2. Fetch scalafix-cli dependency via `DependencyResolver`
3. Load scalafix in a classloader and invoke its main method directly
4. Pass appropriate arguments (sourceroot, classpath, source files)

**Task dependencies:**
- `sourcesTask` - get source directories
- `scalaVersionTask` - resolve scalafix-cli dependency
- `compileTask` - ensure semanticdb files are generated
- `compileClasspathTask` - provide classpath for semantic rules
- `semanticdbDirTask` - semanticdb output directory

**Supported module types:**
- `SCALA`
- `SCALA_TEST`
- `SCALA_JS`
- `SCALA_JS_TEST`
- `SCALA_NATIVE`
- `SCALA_NATIVE_TEST`

## Configuration

**User configuration:**

1. **`.scalafix.conf` file** (primary configuration):
   ```hocon
   rules = [RemoveUnusedImports, ProcedureSyntax]
   ExplicitResultTypes {
     fatalWarnings = true
   }
   ```

**Fixed scalafix-cli version:** `0.12.1` (hardcoded in CoreTasks.scala)

**Semanticdb requirements:**
- `semanticdbEnabled = true` must be set in module config (already supported)
- Compilation must run before fix to generate `.semanticdb` files
- Scalafix will use `--classpath` and `--sourceroot` pointing to compiled classes

**Command usage:**
```bash
deder fix              # Apply fixes
deder fixCheck         # Check without modifying (for CI)
deder fix --help       # Show scalafix CLI help
```

## Implementation Details

### Changes to `CoreTasks.scala`

**Added `fixTask` (lines 918-956):**
```scala
val fixTask = TaskBuilder
  .make[Unit](
    name = "fix",
    singleton = true,
    supportedModuleTypes = Set(
      ModuleType.SCALA,
      ModuleType.SCALA_TEST,
      ModuleType.SCALA_JS,
      ModuleType.SCALA_JS_TEST,
      ModuleType.SCALA_NATIVE,
      ModuleType.SCALA_NATIVE_TEST
    )
  )
  .dependsOn(sourcesTask)
  .dependsOn(scalaVersionTask)
  .dependsOn(compileTask)
  .dependsOn(compileClasspathTask)
  .dependsOn(semanticdbDirTask)
  .build { ctx =>
    val (sources, scalaVersion, _, compileClasspath, semanticdbDir) = ctx.depResults

    val scalafixDep = "ch.epfl.scala:scalafix-cli_2.13:0.12.1"
    val dependency = Dependency.make(scalafixDep, scalaVersion)
    val jars = DependencyResolver.fetchFiles(Seq(dependency), Some(ctx.notifications))

    val sourcePaths = sources.map(_.absPath).filter(os.exists(_)).map(_.toString)
    val args = Array[String](
      "--sourceroot",
      DederGlobals.projectRootDir.toString,
      "--classpath",
      compileClasspath.mkString(File.pathSeparator)
    ) ++ sourcePaths ++ ctx.args

    ClassLoaderUtils.withClassLoader(jars, parent = null) { classLoader =>
      val scalafixClass = classLoader.loadClass("scalafix.cli.Cli")
      val scalafixMain = scalafixClass.getMethod("main", classOf[Array[String]])
      scalafixMain.invoke(null, args)
    }
  }
```

**Added `fixCheckTask` (lines 958-997):**
Same as `fixTask` but with `--test` flag added to args (line 989).

**Registered in `all` sequence (lines 1429-1430):**
```scala
fixTask,
fixCheckTask,
```

### Key Implementation Decisions

1. **Dedicated tasks vs extending runMvnAppTask:** Created separate tasks because scalafix needs compilation dependencies (classpath, semanticdb) that would force all mvn apps (including fmt) to compile first.

2. **In-process execution:** Load scalafix in a classloader and invoke main method directly (similar to javadoc generation), rather than using subprocess delegation.

3. **Error handling:** Scalafix CLI throws exceptions on failure, which propagate naturally through the task system.

4. **No version override:** Fixed scalafix-cli version for simplicity, similar to scalafmt approach.

## Testing Strategy

**Manual testing:**
1. Create a test Scala module with `.scalafix.conf`
2. Run `deder fix` and verify fixes are applied
3. Run `deder fixCheck` and verify it checks without modifying
4. Test with semantic rules (requires semanticdb enabled)
5. Test with syntactic rules only
6. Verify error messages when running on Java modules

**Integration tests:**
- Add integration test in `integration/test/resources/sample-projects/`
- Test both fix and fixCheck commands
- Test with various scalafix rules

## Future Enhancements

- Add scalafix version override in `deder.pkl` if needed
- Add caching for scalafix results
- Support custom scalafix rules from dependencies
