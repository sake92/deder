---
layout: howto.html
title: Build a Scala Native app in Alpine
---

# {{page.title}}

Use a multi-stage Docker build when you want Deder to run in Alpine and ship a
small image containing only the Scala Native executable.

1. Install Alpine's clang and GCC packages in the build stage:

   ```dockerfile
   FROM eclipse-temurin:21-jdk-alpine AS build
   RUN apk add --no-cache clang gcc curl
   ```

   Temurin provides the JDK 21 required by Deder's server. `gcc` supplies
   `crtbeginS.o`, `libgcc`, and `libgcc_s`, which Scala Native's link step
   requires on Alpine.

2. Run Deder from `deder-client.jar`:

   ```dockerfile
   RUN java -jar /opt/deder/deder-client.jar exec \
         --log-level warning \
         --no-color \
         -t nativeLink \
         -m app
   ```

   The released native Deder client is glibc-linked, so use the JAR client in
   the Alpine build stage. Deder reads the server version from the active
   `amends` URL in `deder.pkl`, then downloads the matching server and
   test-runner artifacts. No `server.properties` version override is needed.

3. Copy the linked executable into the runtime stage:

   ```dockerfile
   FROM alpine:3.23
   COPY --from=build /src/.deder/out/app/nativeLink/app /usr/local/bin/app
   ENTRYPOINT ["app"]
   ```

The [Scala Native example Dockerfile](https://github.com/sake92/deder/blob/main/examples/scalanative/Dockerfile)
combines these stages for the `cli` module:

```shell
cd examples/scalanative
docker build -t deder-scalanative-example .
docker run --rm deder-scalanative-example
```

The example uses the `early-access` Deder release by default by rewriting its
repository-local config import to the versioned public URL. Select a tagged
release for a reproducible build:

```shell
docker build \
  --build-arg DEDER_VERSION=v0.22.2 \
  -t deder-scalanative-example .
```

The final image in this example is approximately 15 MB. If your application
uses additional native libraries, install those libraries in the runtime stage
as well.
