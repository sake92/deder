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


################ import existing sbt project
deder import --from sbt

################ install shell completions
deder complete -s bash -o > ~/.local/share/bash-completion/completions/deder
# open another shell to test it
# also supports zsh, fish, powershell

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

# generate an "uber" jar, assembly of all deps and your code
deder exec -t assembly -m uber
java -jar .deder/out/uber/assembly/out.jar

# execute all tests on all test modules
deder exec -t test
# execute tests on "uber-test" module
deder exec -t test -m uber-test
# execute a specific test suite
deder exec -t test uber.MyTestSuite1
# execute test suites that start with "uber"
deder exec -t test uber.%
# execute specific test called "test1" in suite uber.MyTestSuite1
deder exec -t test uber.MyTestSuite1#test1

################ BSP
# write BSP config file for current project
# and then open it in VSCode or IntelliJ (open as a BSP project)
deder bsp install
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

## OTEL tracing

You can start [Jaeger](https://www.jaegertracing.io/) locally with docker:
```shell
docker run --rm --name jaeger \
  -p 16686:16686 \
  -p 4317:4317 \
  -p 4318:4318 \
  -p 5778:5778 \
  -p 9411:9411 \
  cr.jaegertracing.io/jaegertracing/jaeger:2.14.0
```

or Grafana LGTM:
```shell
git clone git@github.com:grafana/docker-otel-lgtm.git
cd docker-otel-lgtm
./run-lgtm.sh
```

then configure the deder server to use the OTEL java agent:
```shell
curl -L -o otel.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# set the agent options in .deder/server.properties:
JAVA_OPTS=-javaagent:../../otel.jar -Dotel.service.name=deder-server -Dotel.exporter.otlp.protocol=grpc -Dotel.exporter.otlp.endpoint=http://localhost:4317

# see examples/multi/.deder/server.properties for an example
```

The deder server only depends on OTEL API, so if no agent is provided it will just run normally without tracing.

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


