# Scala Native example

Build and run the executable locally:

```shell
deder exec -t nativeLink -m cli
./.deder/out/cli/nativeLink/cli
```

Build a small Alpine container:

```shell
docker build -t deder-scalanative-example .
docker run --rm deder-scalanative-example
```

The build stage uses Temurin JDK 21 Alpine and Deder's JAR client. The final
stage contains only Alpine and the musl-linked Scala Native executable. See
[Build a Scala Native app in Alpine](https://sake92.github.io/deder/howtos/alpine-containers.html)
for details.
