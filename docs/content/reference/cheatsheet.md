---
layout: reference.html
title: Cheatsheet
---

# {{page.title}}

> For the full task inventory with descriptions, see the [Task Reference](/reference/tasks.html).

```shell
deder version

# shutdown server
deder shutdown

################ clean module output / caches

deder clean                      # clean all modules
deder clean -m mymodule          # clean specific module
deder clean -m mod1 -m mod2      # clean multiple modules
deder clean -m mod%              # clean modules matching wildcard
deder clean -m mod% -m ~mod-test # exclude module from wildcard match (~ negates)
deder clean -t compile           # clean compile task on all modules
deder clean -m mymodule -t test  # clean test task on specific module

deder import                          # autodetect build tool
deder import --from sbt               # explicit override

################ install shell completions
deder complete -s bash -o > ~/.local/share/bash-completion/completions/deder
# open another shell to test it
# also supports zsh, fish, powershell

################ explore the build
# list modules
# supports --format json|mermaid|dot
deder modules

################
# list tasks
# supports --format json|mermaid|dot
deder tasks

################
# print execution plan for a task
# supports --format json|mermaid|dot
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
# compile modules that start with "uber", except "uber-test"
deder exec -t compile -m uber% -m ~uber-test

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

################ Misc
# write BSP config file
deder bsp install

# fix with scalafix
deder exec -t fix
deder exec -t fixCheck

# format with scalafmt
deder exec -t runMvnApp -m mymodule fmt
deder exec -t runMvnApp -m mymodule fmtCheck
```

## Custom repositories

Declare extra Maven repositories in `deder.pkl`:

```pkl
repositories {
  new MavenRepository { url = "https://nexus.example.com/releases/" }
}
# includeDefaultRepos = false  # force empty default repos, no local .m2 and Maven Central
```

See [Repositories](/reference/repositories.html) for details.

## Dependency Exclusions

Exclude transitive dependencies at declaration time using Coursier's `,exclude=` syntax:

```pkl
deps {
  "org.example:lib:1.0"
  // exclude a specific transitive dependency:
  "org.other:lib:2.0,exclude=org.example%%transitive-dep"
  // multiple exclusions are comma-separated:
  "org.another:lib:3.0,exclude=org.foo%foo-lib,exclude=org.bar%%bar-lib"
}
```

Use `%%` for Scala cross-versioned modules and `%` for plain Java modules in the exclusion coordinates.
