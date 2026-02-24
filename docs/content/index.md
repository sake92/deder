---
title: Deder Docs
description: Deder Build Tool Docs
pagination:
  enabled: false
---

# Deder

Deder is a config-based, concurrent-first client-server build tool designed for JVM projects (Java and Scala).  
It uses [Pkl](https://pkl-lang.org/) for configuration, providing type safety, IDE support, and powerful abstraction capabilities.

## Key Concepts

A **project** is the root directory where your build configuration lives. (usually git repo root)

**Modules** are subprojects within your project (like `common`, `frontend`, `backend`, etc.).
They can depend on each other, forming a directed acyclic graph (DAG).

**Tasks** are operations that can be performed on modules (like `compile`, `run`, `test`).
Each module type has a predefined set of tasks available.

## Site Map
- [Tutorials](/tutorials)
  {% for tut in site.data.project.tutorials %}- [{{ tut.label }}]({{ tut.url}})
  {% endfor %}


## At a glance

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
```

## Dependencies

Dependencies are written using Coursier's Dependency syntax.  
See https://github.com/coursier/dependency for more details

## IDE setup

Run `deder bsp install` and just open with VSCode or IntelliJ (open as a BSP project).
The `reset.sh` script does this for you..

Currently working features:
- import of project
- navigation
- compilation and diagnostics
- run main scala classes (Java doesnt.. #todo-fixme )




## Tips & Tricks

Generate an SVG of the dependency graph for a specific task:
```shell
deder plan -m common -t compileClasspath --dot | dot -Tsvg > bla.svg
```



