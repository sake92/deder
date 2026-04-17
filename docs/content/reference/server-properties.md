---
layout: reference.html
title: Server Properties File
---

# {{page.title}}

You can use `.deder/server.properties` file to specify the server properties when client starts it.  
Example
```properties
localPath=myprojects/deder/out/server/assembly.dest/out.jar
workerThreads=32
logLevel=debug
JAVA_OPTS=-javaagent:otel.jar -Dotel.service.name=deder-server -Dotel.exporter.otlp.protocol=grpc -Dotel.exporter.otlp.endpoint=http://localhost:4317
```


If you make changes to it, make sure you restart server manually:
```shell
deder shutdown
deder
```

Availabel properties:
- `logLevel` - log level used for server log at `.deder/logs/server.log`, one of `ERROR`, `WARNING`, `INFO`, `DEBUG`, `TRACE`
- `workerThreads` - number of threads to use for executing tasks
- `maxConcurrentTestForks` - maximum number of forked test JVMs alive server-wide at any one time. Optional; defaults to the number of available CPU cores. Per-module `maxTestForks` is capped by this.
- `maxInactiveSeconds` - max number of seconds server is inactive before it shuts down automatically
- `bspEnabled` - specifies if BSP protocol is enabled. If false, the BSP Deder server is never started (useful in CI or to save memory/CPU)
- `JAVA_OPTS` - java options passed to server java process
- `localPath` - fixed local path to server JAR, useful for developing Deder and debugging
