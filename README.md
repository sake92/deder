# Deder


Config based, concurrent-first, client-server JVM build tool.

:construction: Work in progress, expect rough edges and breaking changes.  

Feedback and [contributions](CONTRIBUTING.md) are very welcome! :heart:

## Current status and features
- CLI client with shell completions
- BSP client with support for VSCode and IntelliJ
- exploring the build with listing modules, tasks, and execution plans (visualized as ASCII or DOT graphs)
- packaging module as JAR, uber JAR, Scala.js bundle, Scala Native executable
- executing tests
- watch mode with concurrent execution in multiple terminals
- import from sbt (experimental, feedback needed)
- OTEL tracing support

## Adoption and feedback
Deder is used in some of my OSS projects:
- https://github.com/sake92/sharaf
- https://github.com/sake92/regenesca
- https://github.com/sake92/squery
- https://github.com/sake92/tupson

## Concepts
- *project* is the root of your git repo
- *module* is a "subproject", like `common`/`frontend`/`app`..
- *task* is something you can run on a module, like `compile`/`test`/`run`/`assembly`..

The [examples](examples) folder has working projects that show you how to use Deder to build, cross-build, test, publish, use ScalaJS and ScalaNative etc.

## Installation

### Homebrew (macOS and Linux)

```shell
brew tap sake92/tap
brew install deder
```

### Manual installation

1. download `deder-client.jar` (or a native version) from [early release](https://github.com/sake92/deder/releases/tag/early-access)
1. rename it to just `deder` 
1. do `chmod +x deder`
1. put it in `PATH`


## Example commands

```shell
################ basic commands
# prints help
deder

# prints version
deder version

# shutdown server
deder shutdown


################ import existing sbt project
deder import --from sbt

################ install shell completions
deder complete -s bash -o > ~/.local/share/bash-completion/completions/deder
# open another shell to test it
# also supports zsh, fish, powershell

################ explore the build
# list modules
# supports flags: --json, --ascii, --dot
deder modules

################
# list tasks
# supports flags: --json, --ascii, --dot
deder tasks

################
# print execution plan for a task
# supports flags: --json, --ascii, --dot
deder plan -m common -t compileClasspath


################ run tasks
# by default executes compile on all modules
deder exec

# execute "compile" task explicitly, on all modules
deder exec -t compile
# compile the "common" module
deder exec -t compile -m common
# compile modules that start with "uber"
deder exec -t compile -m uber%

# run the "uber" module
deder exec -t run -m uber

# execute "run" in watch mode
deder exec -t run -m frontend --watch
# even in multiple terminals at the same time!!!
deder exec -t run -m backend --watch

# generate an "uber" jar, assembly of all deps and your code
deder exec -t assembly -m uber
java -jar .deder/out/uber/assembly/out.jar

# execute all tests on all test modules
deder exec -t test
# execute tests on "uber-test" module
deder exec -t test -m uber-test
# execute a specific test suite
deder exec -t test uber.MyTestSuite1
# execute test suites that start with "uber"
deder exec -t test uber.%
# execute specific test called "test1" in suite uber.MyTestSuite1
deder exec -t test uber.MyTestSuite1#test1

################ BSP
# write BSP config file for current project
# and then open it in VSCode or IntelliJ (open as a BSP project)
deder bsp install

################ Misc
# format with scalafmt
deder exec -t runMvnApp fmt
deder exec -t runMvnApp fmtCheck
```

## IDE setup

Run `deder bsp install` and just open with VSCode or IntelliJ (open as a BSP project).
The `reset.sh` script does this for you in examples...

Currently working features:
- import of project
- navigation
- compilation and diagnostics
- run main scala classes (Java doesnt.. #todo-fixme )
- run tests

If you work on server code, after you build it you can run `./reset.sh` in examples/multi



## Contributing

See [dev docs](docs/dev/README.md) for how to build Deder locally, run integration tests, setup tracing etc.

