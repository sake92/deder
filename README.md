# deder

Config based client-server build tool

Build a client:
```scala
./mill -i cli.assembly

# build native image cli client
./mill -i cli.nativeImage
```


Run a server:
```scala
# start server for a project
./mill -i server.run examples/multi
```

Run a client:
```scala
# in another terminal, run commands:
cd examples/multi

../../out/cli/assembly.dest/out.jar uber run

# or with native client
../../out/cli/nativeImage.dest/native-executable uber run

```







