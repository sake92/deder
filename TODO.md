
# Docs
- run pkldoc on each tag, and publish to ghpages
https://pkl-lang.org/main/current/pkl-doc/index.html

# Misc

- Console/REPL
- speed up assembly task, steal from mill
- maybe use https://github.com/encalmo/graphs coz zero deps?
- deps graph  https://www.scala-sbt.org/sbt-dependency-graph/index.html

# Client
- pass env vars in request, and merge'm when running subprocesses, e.g. forked tests

# Caching
- harden the abstraction
- polish the docs

# Deps
- custom repos

# CI
- more package managers?

## Config

## GraalVM native image
- add reachability metadata for common libs 
   https://github.com/com-lihaoyi/mill/blob/1.1.5/libs/javalib/src/mill/javalib/NativeImageModule.scala#L365

## Scala.js
- run
- fullopt
- configuration..

## Scala Native
- run
- fullopt
- configuration..

## Tests

- print number of suites


## CLI
- --mermaid diagrams
    - modules as subgraphs https://mermaid.js.org/syntax/flowchart.html#subgraphs

## Misc
- temp override of settings, say scalacOptions when running BSP request??? hmmm

## BSP
- more interactive / resiliency tests
- java main classes cant be run..??


## Packaging, publishing

- publish for github packages etc


## Web server dashboard

Locally would be interesting to have a dashboard with nice overview:
- list of modules
- list of tasks in each module
- current requests in flight, and locks being held
- filtering of modules and tasks
- execute a task, if not "run" ?
- HTMX and polling #simple

# Add more commands

- init, import from other build tools...


## Plugins

https://github.com/pf4j/pf4j ? no, too complex

- maybe just use a simple ServiceLoader with start() + configure(): Seq[Task[?]] + stop()
- just give it CoreTasks so it can make deps
- add `runsBefore` so that graph can be made properly
- define its config in Pkl and distribute in its JAR or??
- reconfigure if its config changes
- reload if dep changed, force always in dev mode?
- unload if removed from project





