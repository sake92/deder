
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

----


## Running integration tests

This will build the server and client, and run the integration tests:

```shell
# run all
./scripts/run-it-tests.sh
# or just one
./scripts/run-it-tests.sh ba.sake.deder.bsp.BspIntegrationSuite
```

---

## OTEL tracing

OTEL is really useful for debugging and understanding what is going on in the server.  
It can potentially be used for performance monitoring.  
You can see exactly which tasks are taking time, which tasks are executed in parallel etc.


First you will need to have an OTEL collector running, and then configure the deder server to use the OTEL java agent.
You can start [Jaeger](https://www.jaegertracing.io/) locally with docker, which will give you a nice UI to see the traces:
```shell
docker run --rm --name jaeger \
  -p 16686:16686 \
  -p 4317:4317 \
  -p 4318:4318 \
  -p 5778:5778 \
  -p 9411:9411 \
  cr.jaegertracing.io/jaegertracing/jaeger:2.14.0
```

or Grafana LGTM (also has logs and metrics support!):
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


