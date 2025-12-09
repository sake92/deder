# deder

Config based client-server build tool

Build the client:
```scala
./mill -i client.assembly

# build native image cli client
./mill -i client-native.nativeImage
```


Run the server manually:
```scala
# start server for a project
./mill -i server.run examples/multi
```

Run client:
```scala
# in another terminal, run commands:
cd examples/multi

../../out/client/assembly.dest/out.jar uber run

# or with native client
../../out/client-native/nativeImage.dest/native-executable uber run

```







