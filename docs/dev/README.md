
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



