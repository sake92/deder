
- jar
- uber assembly
- tests via sbt test interface..


- if project is in bad state (e.g. config invalid), metals cannot import it,
    and even if you fix it, there is no way to reimport it properly

- maybe use "CI" env var to disable BSP/semanticdb in CI runners?
    - and enable locally by default..

- add BSP integration tests from Metals and testkit??
    - https://scalameta.org/metals/docs/integrations/new-build-tool/#automated-tests

