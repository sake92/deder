
- jar
- uber assembly
- tests via sbt test interface..


- if project is in bad state (e.g. config invalid), metals cannot import it,
    and even if you fix it, there is no way to reimport it properly

- maybe use "CI" env var to disable BSP/semanticdb in CI runners?
    - and enable locally by default..

- add BSP integration tests from Metals and testkit??
    - https://scalameta.org/metals/docs/integrations/new-build-tool/#automated-tests



# Add more commands

- clean -t mytask, or all if no -t


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












