# deder

Config based client-server build tool

See [dev docs](docs/dev/README.md) for more technical details

## Concepts
- project is a root project, your git repo
- modules are "subprojects", like common/frontend..
- modules have tasks defined for them

## Status

Supported commands:

- `deder -t mytask`, executes a task for each module that has it
- `deder -t mytask -m mymodule`, executes a task for this specific module
- `deder -t compile -m mymodule --json`, executes compile task for mymodule and prints its result in json format
- `deder -t run -m mymodule`, runs the main class for mymodule (#nonblocking! client-side)
- `deder shutdown`, stops the Deder server for current project
- `deder bsp install`, writes BSP config file for current project
- `deder bsp`, starts a BSP client for current project

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

deder -t compile

deder -t run -m uber
```

## IDE setup

Run `deder bsp install` and just open with VSCode or IntelliJ (open as a BSP project).

Currently working features:
- import of project
- navigation
- compilation
- run main scala classes (Java doesnt.. #todo-fixme )

If you work on server code, after you build it you can run `./reset` in examples/multi
