# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build

# Alpine's clang package does not include the GCC link runtime files.
RUN apk add --no-cache clang gcc curl

ARG DEDER_VERSION

WORKDIR /src/examples/scalanative

COPY . .

RUN mkdir -p /opt/deder \
    && version="${DEDER_VERSION:-$(sed -n 's#^amends "https://sake92.github.io/deder/config/\([^/]*\)/DederProject.pkl"#\1#p' deder.pkl)}" \
    && test -n "${version}" \
    && if [ -n "${DEDER_VERSION}" ]; then \
         mkdir -p .deder \
         && printf 'version=%s\n' "${DEDER_VERSION}" > .deder/server.properties; \
       fi \
    && curl -fsSL -o /opt/deder/deder-client.jar \
      "https://github.com/sake92/deder/releases/download/${version}/deder-client.jar"

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
