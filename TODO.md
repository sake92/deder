
- jar
- uber assembly
- tests via sbt test interface..

- max inactive period for server, shutdown if no reqs in flight for 10 mins

- if project is in bad state (e.g. config invalid), metals cannot import it,
    and even if you fix it, there is no way to reimport it properly

- maybe use "CI" env var to disable BSP/semanticdb in CI runners?
    - and enable locally by default..

- temp override of settings, say scalacOptions when running BSP request??? hmmm

## Watch files
- source task type
- config input task type
- watch whole project at once, always
- if user runs -t compile --watch
    - store this req as "in flight"
    - if a file changes, find source tasks that depend on it
        - if this compile task depends on it, rerun it

## BSP
- report compilation progress
- report compile errors
- test discovery
- test run
- add BSP integration tests from Metals and testkit??
    - https://scalameta.org/metals/docs/integrations/new-build-tool/#automated-tests

## Tests

- always a separate JVM process, 1 or more for isolation..
- on first run just randomly distribute between workers, record stats
    - on next run use stats to figure out how to exec more performantly

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

- clean -t mytask, or all if no -t

- version, print both client and server version

Mill-inspired:
- resolve -m my*module -t my*task --json
    - list tasks on a module
    - list modules that have this task..
    - or combine

- inspect a task instance, list its direct dependencies

- plan --json, list how Deder plans to execute a task
    - maybe in stages

- visualize ?? or maybe not, make a web server dashboard, no need to open files
    - maybe ASCII art ???????? #infinite #possibilities
- visualizePlan, subgraph of execution

- init ofc, import from other build tools...


## Plugins

https://github.com/pf4j/pf4j

- just give it CoreTasks so it can make deps
- add `runsBefore` so that graph can be made properly
- define its config in Pkl and distribute in its JAR or??
- reconfigure if its config changes
- reload if dep changed, force always in dev mode?
- unload if removed from project





