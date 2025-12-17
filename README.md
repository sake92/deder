# deder


Config based client-server build tool

See [dev docs](docs/dev/README.md) for more technical details

EXPERIMENTAL!  
THERE ARE ZERO TESTS! ZERO!!!


## Concepts
- project is a root project, your git repo
- modules are "subprojects", like common/frontend..
- modules have tasks defined for them

See [examples](examples/multi/) for a realistic example project.


## Building locally

Build the client:
```scala
# executable JAR
./mill client.assembly
# or as graalvm native image
./mill client-native.nativeImage
# AND PUT IT IN PATH !!!

# build server
./mill server.assembly
```


Run client:
```scala
# in another terminal, run commands:
cd examples/multi
./reset # copy the server JAR etc

################
# print version of client and server
deder version

################
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

################
# by default executes compile on all modules
deder

# execute compile explicitly, on all modules
deder -t compile

# execute compile explicitly, on uber module
deder -t run -m uber

# execute test on uber-test module
deder -t test -m uber-test

################ BSP
# write BSP config file for current project
deder bsp install

# start a BSP client for current project
deder bsp

# shutdown server
deder shutdown
```

## IDE setup

Run `deder bsp install` and just open with VSCode or IntelliJ (open as a BSP project).

Currently working features:
- import of project
- navigation
- compilation
- run main scala classes (Java doesnt.. #todo-fixme )

If you work on server code, after you build it you can run `./reset` in examples/multi
