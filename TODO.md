

# Misc
- maybe use https://github.com/encalmo/graphs coz zero deps?
- shade test-runner to avoid classpath issues.. but first make shading work good
- quick shutdown and start causes issues, plus race with metals&bsp when manually shutdown
- init like giter8, autoimport from sbt?

# Import
- cross scala modules are wack.. no common Pkl code..
- relativize imported folders to repo root or module root..
- import from other maven effective POM etc
- import from other mill
- import from other gradle


# Docs
- run pkldoc on each tag, and publish to ghpages
https://pkl-lang.org/main/current/pkl-doc/index.html

# Client

# Compilers


## Plugins
- more precise reload/unload
- add starter github repo


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
- java main classes cant be run..??


## Packaging, publishing
- test if user/pass works for other rpos: nexus etc
- publish for github packages etc


## Web server dashboard

Locally would be interesting to have a dashboard with nice overview:
- list of modules
- list of tasks in each module
- current requests in flight, and locks being held
- filtering of modules and tasks
- execute a task, if not "run" ?
- HTMX and polling #simple


