
# Misc
- replace jackson with avaje jsonb
- publish pkl configs as proper Pkl modules, good for offline/caching
- maybe use https://github.com/encalmo/graphs coz zero deps?
- init like giter8, autoimport from sbt?
- max timeout when waiting for lock and bail out, report to bsp??
- import from other maven effective POM etc
- import from other mill
- import from other gradle
- sbt-updates for deps
- show running task in web dash
- periodic print tasks that are still running, maybe every 5 mins so user knows it is still compiling or whatever

- run pkldoc on each tag, and publish to ghpages
https://pkl-lang.org/main/current/pkl-doc/index.html



## Plugins
- more precise plugins reload/unload
- add plugin starter github repo

# Caching
- harden the abstraction
- polish the docs


## Misc
- temp override of settings, say scalacOptions when running BSP request??? hmmm

## BSP
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


