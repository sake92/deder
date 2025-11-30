# deder

Config based client-server build tool

```scala
# build native image cli client
./mill -i cli.nativeImage

# start server for a project
./mill -i server.run examples/multi

# in another terminal, run commands:
cd examples/multi
../../out/cli/nativeImage.dest/native-executable uber run
```







