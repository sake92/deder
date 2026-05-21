
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
./reset.sh
```

For local development, use `localPath` in your project's `.deder/server.properties` to point to your local server build:

```properties
# .deder/server.properties
localPath=/path/to/your/deder/.deder/out/server/assembly/out.jar
testRunnerLocalPath=/path/to/your/deder/.deder/out/test-runner/assembly/out.jar
```

**Note:** When using localPath or testRunnerLocalPath, the global artifact cache is bypassed - the artifact is copied directly without caching.
This ensures you're always testing your latest local build (no need to copy JARs manually, client will do it).
The `early-access` version also skips caching to ensure you get the latest pre-release features.

----

## Running unit tests on server
deder exec -t test -m server-test

## Running unit tests on test-common
deder exec -t testInMemory -m test-common-test

## Running integration tests

This will build the server and client, and run the integration tests:

```shell
# run all
./scripts/run-it-tests.sh
# or just one
./scripts/run-it-tests.sh ba.sake.deder.bsp.BspIntegrationSuite
```

---


## Plugin development

For a public overview of what plugins are and how to write one, see the
[Plugins how-to](https://sake92.github.io/deder/howtos/plugins.html).

This section covers contributor-level concerns: what to do when the plugin API or the
shared Pkl schema changes.

### Rebuilding after `plugin-api` changes

If you modify anything under `plugin-api/` or `config/`, publish both modules to your
local `.m2` repository before building dependent modules or running integration tests:

```shell
deder exec -t publishLocal -m config -m plugin-api
```

This publishes the `config` and `plugin-api` modules locally.

### Regenerating config bindings

If you modify `config/DederProject.pkl` (e.g. to add or rename fields on `DederPlugin`),
regenerate the Java config bindings:

```shell
./scripts/gen-config-bindings.sh
```

Commit the generated files together with the Pkl schema change.

### Testing plugin integration

After publishing locally and rebuilding the server (see *Building locally* above), you can
smoke-test a plugin by pointing one of the `examples/` projects at your local server build
via `localPath` in `.deder/server.properties` and wiring up a test plugin via its `deps` in
`deder.pkl`.

Automated integration tests for plugin behaviour live under `integration/`; run a single
suite with:

```shell
./scripts/run-it-tests.sh ba.sake.deder.PluginIntegrationSuite
```
