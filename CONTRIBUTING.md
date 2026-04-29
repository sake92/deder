
## Building locally

Build the server and client:
```shell
./scripts/gen-config-bindings.sh
deder exec -t assembly -m server

# client executable JAR
deder exec -t assembly -m client
# or as native client
deder exec -t graalvmNativeImage -m client

# AND PUT CLIENT IN PATH !!! for example:
cp .deder/out/client/assembly/out.jar /usr/local/bin/deder
cp .deder/out/client/graalvmNativeImage/native-executable /usr/local/bin/deder

# then you can run commands:
cd examples/multi
# start from clean state, copy the server JAR etc
./reset
```

For local development, use `localPath` in your project's `.deder/server.properties` to point to your local server build:

```properties
# .deder/server.properties
localPath=/path/to/your/deder/out/server/assembly.dest/out.jar
testRunnerLocalPath=/path/to/your/deder/out/test-runner/assembly.dest/out.jar
```

**Note:** When using localPath or testRunnerLocalPath, the global artifact cache is bypassed - the artifact is copied directly without caching. 
This ensures you're always testing your latest local build (no need to copy JARs manually, client will do it). 
The `early-access` version also skips caching to ensure you get the latest pre-release features.

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



