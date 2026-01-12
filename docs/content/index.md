
# Deder

## Concepts
- project is the root of your git repo
- modules are "subprojects", like common/frontend/app..
- modules have tasks defined for them

See [examples/multi](https://github.com/sake92/deder/tree/main/examples/multi) for a working example project.

## Installation

1. download `deder-client.jar` from [early release](https://github.com/sake92/deder/releases/tag/early-access)
1. rename it to just `deder` 
1. do `chmod +x deder`
1. put it in `PATH`


## Example commands

```shell
################ basic commands
# prints help
deder

# prints version
deder version

# shutdown server
deder shutdown


################ explore the build
# list modules
# supports flags: --json, --ascii, --dot
deder modules

################
# list tasks
# supports flags: --json, --ascii, --dot
deder tasks

################
# print execution plan for a task
# supports flags: --json, --ascii, --dot
deder plan -m common -t compileClasspath


################ run tasks
# by default executes compile on all modules
deder exec

# execute compile explicitly, on all modules
deder exec -t compile

# execute run explicitly, on uber module
deder exec -t run -m uber

# execute run in watch mode
deder exec -t run -m frontend --watch
# even in multiple terminals at the same time!!!
deder exec -t run -m backend --watch

# execute test on uber-test module
deder exec -t test -m uber-test


################ BSP
# write BSP config file for current project
deder bsp install

# start a BSP client for current project
deder bsp

```

## Dependencies

Dependencies are written using Coursier's Dependency syntax.  
See https://github.com/coursier/dependency for more details

## IDE setup

Run `deder bsp install` and just open with VSCode or IntelliJ (open as a BSP project).
The `reset.sh` script does this for you..

Currently working features:
- import of project
- navigation
- compilation and diagnostics
- run main scala classes (Java doesnt.. #todo-fixme )




## Tips & Tricks

deder plan -m common -t compileClasspath --dot | dot -Tsvg > bla.svg





