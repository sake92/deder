

# Deder

Config based, concurrent-first, client-server JVM build tool.

:construction: Work in progress, expect some rough edges and breaking changes.

Feedback and [contributions](CONTRIBUTING.md) are very welcome! :heart:

## Current status and features
- CLI client with shell completions
- BSP client with support for VSCode and IntelliJ
- exploring the build with listing modules, tasks, and execution plans (visualized as DOT graphs)
- packaging module as JAR, uber JAR, Scala.js bundle, Scala Native executable
- executing tests
- module REPL
- watch mode with concurrent execution in multiple terminals
- import from sbt
- OTEL tracing support
- basic plugins support with automatic reloading

## Adoption and feedback
Deder is used in some of my OSS projects:
- https://github.com/sake92/deder (yes, it bootstraps itself!)
- https://github.com/sake92/regenesca
- https://github.com/sake92/squery
- https://github.com/sake92/tupson
- https://github.com/sake92/sharaf

## Concepts
- *project* is the root of your git repo
- *module* is a "subproject", like `common`/`frontend`/`app`..
- *task* is something you can run on a module, like `compile`/`test`/`run`/`assembly`..

The [examples](examples) folder has working projects that show you how to use Deder to build, cross-build, test, publish, and use Scala.js / Scala Native.

## Installation

Deder needs JDK 21+.

### macOS and Linux (Homebrew)

```shell
brew tap sake92/tap
brew install deder
```

### Windows 10+

```shell
scoop bucket add sake92 https://github.com/sake92/scoop-bucket.git
scoop install deder
```

### Early-access client installation

1. download `deder-client.jar` (or a native version) from [early release](https://github.com/sake92/deder/releases/tag/early-access)
1. rename it to just `deder`
1. do `chmod +x deder`
1. put it in `PATH`


## Common commands

```shell
deder exec                          # compile all modules (default task)
deder exec -t compile -m common     # compile a specific module
deder exec -t test                  # run all tests
deder exec -t test -m mymod-test    # run tests for one module
deder exec -t assembly -m app       # build an uber JAR
deder exec -t run -m app --watch    # run with file-watching
deder import                        # import project from sbt
deder bsp install                   # write BSP config for IDE import
deder shutdown                      # stop the background server
```

For the full command reference see the [Cheatsheet](https://sake92.github.io/deder/reference/cheatsheet.html).

## IDE setup

Lates Metals version supports Deder import out of the box.

For IntelliJ you need to run `deder bsp install` first and then open as a BSP project.

Supported features:
- import of project
- navigation
- compilation and diagnostics
- run main classes
- run tests

## Plugins

Deder supports custom tasks via a plugin API.
See the [Plugins how-to](https://sake92.github.io/deder/howtos/plugins.html) to get started.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to build Deder locally, run integration tests, setup tracing etc.


