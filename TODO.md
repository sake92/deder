
## Config


## Tests

- optionally run as separate JVM process(es), for isolation etc..
- on first run just randomly distribute between workers, record stats per test/suite
- on next run use stats to figure out how to exec more performantly
- use sqlite for coordination

## CLI
- autocomplete
- --mermaid diagrams
    - modules as subgraphs https://mermaid.js.org/syntax/flowchart.html#subgraphs

## Misc

- maybe use "CI" env var to disable BSP/semanticdb in CI runners?
    - and enable locally by default..

- temp override of settings, say scalacOptions when running BSP request??? hmmm

- use proper logger for server, or at least add timestamps


## BSP
- java main classes cant be run..??
- test run doesnt work, although Metals should cover DAP (same should work for Mill...)
- add BSP integration tests from Metals and testkit??
    - https://scalameta.org/metals/docs/integrations/new-build-tool/#automated-tests


## Packaging, publishing

- jar
- uber assembly
- publish

## Tracing
- otel
- https://grafana.com/docs/opentelemetry/docker-lgtm/
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

https://github.com/pf4j/pf4j ?

- maybe just use a simple ServiceLoader with start() + configure(): Seq[Task[?]] + stop()

- just give it CoreTasks so it can make deps
- add `runsBefore` so that graph can be made properly
- define its config in Pkl and distribute in its JAR or??
- reconfigure if its config changes
- reload if dep changed, force always in dev mode?
- unload if removed from project





