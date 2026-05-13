

# Misc
- autodetect sbt for import
- maybe use https://github.com/encalmo/graphs coz zero deps?
- color instead of quotes for "Executing 'mytask'"?
- hide some internal tasks like deps resolving etc, helper assemblyDeps.. (rebind edges when displaying)
- color stages in plan
- shade test-runner to avoid classpath issues.. but first make shading work good
- quick shutdown and start causes issues, plus race with metals&bsp when manually shutdown


# Import
- cross scala modules are wack..
- relativize imported folders to repo root or module root..

# Docs
- run pkldoc on each tag, and publish to ghpages
https://pkl-lang.org/main/current/pkl-doc/index.html

# Client

# Compilers


# Caching
- harden the abstraction
- polish the docs

# Deps

# CI
- more package managers?

## Config
- revisit Pkl packaging, it would be useful in airgapped envs (cached locally),
  - but users would still use http for early-access, not big deal I guess

## GraalVM native image

## Scala.js

## Scala Native

## Tests
- stdout not captured when fork
- periodic flush, every 1s?


## CLI

## Misc
- temp override of settings, say scalacOptions when running BSP request??? hmmm

## BSP
- check if futures caching logic is ok with Metals..?
- still lingering compilations.. maybe I broke it in recent refactors?
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

- init like giter8
- import from other build tools... maven effective POM etc


## Plugins

https://github.com/pf4j/pf4j ? no, too complex

- maybe just use a simple ServiceLoader with start() + configure(): Seq[Task[?]] + stop()
- just give it CoreTasks so it can make deps
- add `runsBefore` so that graph can be made properly
- define its config in Pkl and distribute in its JAR or??
- reconfigure if its config changes
- reload if dep changed, force always in dev mode?
- unload if removed from project





