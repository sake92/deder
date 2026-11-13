# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build

ARG DEDER_VERSION=early-access

# Alpine's clang package does not include the GCC link runtime files.
RUN apk add --no-cache clang gcc curl

RUN mkdir -p /opt/deder \
    && curl -fsSL -o /opt/deder/deder-client.jar \
      "https://github.com/sake92/deder/releases/download/${DEDER_VERSION}/deder-client.jar"

WORKDIR /src/examples/scalanative

COPY . .

# Use the release selected above instead of this example's repository-local config.
RUN sed -i \
      "s#^amends .*DederProject[.]pkl.*#amends \"https://sake92.github.io/deder/config/${DEDER_VERSION}/DederProject.pkl\"#" \
      deder.pkl \
    && grep -Fq "/config/${DEDER_VERSION}/DederProject.pkl" deder.pkl

RUN java -jar /opt/deder/deder-client.jar exec \
      --log-level warning \
      --no-color \
      -t nativeLink \
      -m cli

FROM alpine:3.23

COPY --from=build \
  /src/examples/scalanative/.deder/out/cli/nativeLink/cli \
  /usr/local/bin/scalanative-example

ENTRYPOINT ["scalanative-example"]
