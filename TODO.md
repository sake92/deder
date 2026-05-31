
# Misc
- publish pkl configs as proper Pkl modules, good for offline/caching
- init like giter8, autoimport from sbt?
- import from other maven effective POM etc
- import from other mill
- import from other gradle
- sbt-updates for deps
- report core/build issues to metals: e.g. when dep doesnt exist
- hmm could we make a function isEnabled(p: DederProject): Boolean to mark task as not enabled, e.g. for publish, we mark it as enabled=false in pkl
- add cache metadata timestamp
- list currently loaded plugins (task, web dash etc)
- smarter modules selector, if only one with matching task (e.g. run) just do it..
- hmm, when starting fresh server maybe run just "deder" to make sure it is up, and ONLY THEN invoke proper command..
  - coz otherwise if it runs a long-ass request from BSP, console gets blocked and fails

run pkldoc on each tag, and publish to ghpages
https://pkl-lang.org/main/current/pkl-doc/index.html



## Plugins
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

- filtering
- list of tasks in each module
- current locks being held
- filtering of modules and tasks
- execute a task, if not "run" ?



