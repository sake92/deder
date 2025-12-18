
## Tests

- optionally run as separate JVM process(es), for isolation etc..
- on first run just randomly distribute between workers, record stats per test/suite
- on next run use stats to figure out how to exec more performantly

## CLI
- autocomplete

## Misc

- maybe use "CI" env var to disable BSP/semanticdb in CI runners?
    - and enable locally by default..

- temp override of settings, say scalacOptions when running BSP request??? hmmm

- use proper logger for server, or at least add timestamps


## Watch files
- source task type
- config input task type
- watch whole project at once, always
- if user runs -t compile --watch
    - store this req as "in flight"
    - if a file changes, find source tasks that depend on it
        - if this compile task depends on it, rerun it

## BSP
- java main classes cant be run..??
- test run dosnt work, although Metals should cover DAP (same should work for Mill...)
- add BSP integration tests from Metals and testkit??
    - https://scalameta.org/metals/docs/integrations/new-build-tool/#automated-tests


## Packaging, publishing

- jar
- uber assembly
- publish

## Tracing
- otel
- jaeger

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

https://github.com/pf4j/pf4j

- just give it CoreTasks so it can make deps
- add `runsBefore` so that graph can be made properly
- define its config in Pkl and distribute in its JAR or??
- reconfigure if its config changes
- reload if dep changed, force always in dev mode?
- unload if removed from project





