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
./mill -i server.run --root-dir examples/multi
```

Run client:
```scala
# in another terminal, run commands:
cd examples/multi

../../out/client/assembly.dest/out.jar -t run -m uber

# or with native client
../../out/client-native/nativeImage.dest/native-executable -t run -m uber

```







