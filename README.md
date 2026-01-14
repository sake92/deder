# deder


Config based, concurrent-first client-server build tool

See [dev docs](docs/dev/README.md) for more technical details

☢️ EXPERIMENTAL! ☢️


## Concepts
- project is the root of your git repo
- modules are "subprojects", like common/frontend/app..
- modules have tasks defined for them

See [examples/multi](examples/multi/) for a working example project.

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

# execute "compile" task explicitly, on all modules
deder exec -t compile
# compile the "common" module
deder exec -t compile -m common
# compile modules that start with "uber"
deder exec -t compile -m uber%

# run the "uber" module
deder exec -t run -m uber

# execute "run" in watch mode
deder exec -t run -m frontend --watch
# even in multiple terminals at the same time!!!
deder exec -t run -m backend --watch

# execute all tests on all test modules
deder exec -t test
# execute tests on "uber-test" module
deder exec -t test -m uber-test
# execute a specific test suite
deder exec -t test uber.MyTestSuite1
# execute test suites that start with "uber"
deder exec -t test uber.%

################ BSP
# write BSP config file for current project
deder bsp install

# start a BSP client for current project
deder bsp

```

## IDE setup

Run `deder bsp install` and just open with VSCode or IntelliJ (open as a BSP project).
The `reset.sh` script does this for you..

Currently working features:
- import of project
- navigation
- compilation and diagnostics
- run main scala classes (Java doesnt.. #todo-fixme )
- run tests

If you work on server code, after you build it you can run `./reset.sh` in examples/multi


## Building locally

Build the server and client:
```shell
./scripts/gen-config-bindings.sh
./mill server.assembly

# client executable JAR
./mill client.assembly
# or as native client
./mill client-native.nativeImage

# AND PUT CLIENT IN PATH !!! for example:
cp out/client/assembly.dest/out.jar /usr/local/bin/deder
cp out/client-native/nativeImage.dest/native-executable /usr/local/bin/deder

# then you can run commands:
cd examples/multi
# start from clean state, copy the server JAR etc
./reset 
```


