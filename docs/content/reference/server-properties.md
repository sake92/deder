---
layout: reference.html
title: Server Properties File
---

# {{page.title}}

You can use `.deder/server.properties` to specify server properties when the client starts it.
Example:
```properties
localPath=myprojects/deder/.deder/out/server/assembly/out.jar
testRunnerLocalPath=myprojects/deder/.deder/out/test-runner/assembly/out.jar
logLevel=debug
JAVA_OPTS=-javaagent:otel.jar -Dotel.service.name=my-project -Dotel.exporter.otlp.protocol=grpc -Dotel.exporter.otlp.endpoint=http://localhost:4317
```


If you make changes to it, make sure you restart the server manually:
```shell
deder shutdown
deder version
```

Available properties:
- `logLevel` - log level used for server log at `.deder/logs/server.log`, one of `ERROR`, `WARNING`, `INFO`, `DEBUG`, `TRACE`
- `maxInactiveSeconds` - max number of seconds server is inactive before it shuts down automatically
- `JAVA_OPTS` - java options passed to server java process
- `maxConnectSeconds` - max number of seconds the client waits for the server to start before giving up. Defaults to `30`. Increase this on slow machines or CI environments.
- `localPath` - fixed local path to server JAR, useful for developing Deder and debugging. **Note:** When using localPath, the global cache is bypassed - the artifact is copied directly without caching. This ensures you're always testing your latest local build.
- `testRunnerLocalPath` - fixed local path to test-runner JAR, useful for developing Deder and debugging. **Note:** When using testRunnerLocalPath, the global cache is bypassed - the artifact is copied directly without caching.
- `forkTestFlushIntervalMs` - interval in milliseconds for flushing partial test output from forked test JVMs. Setting to `0` disables periodic flushing (output only appears when each test suite completes). Defaults to `1000` (1 second).
- `taskLockTimeoutSeconds` - maximum number of seconds to wait when acquiring a task lock before giving up. If another request is holding the lock (e.g., a long-running compilation), the waiting request will fail with a timeout error instead of blocking indefinitely. Set to `0` to disable (unlimited wait). Defaults to `600` (10 minutes).
