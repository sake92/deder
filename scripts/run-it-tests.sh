#!/bin/bash
set -euo pipefail

export DEDER_PLUGIN_API_VERSION="it-test-version"
export VERSION="$DEDER_PLUGIN_API_VERSION"

# Clean up any lingering server artifacts in sample projects
find integration/test/resources/sample-projects -name ".deder" -type d -exec rm -rf {} + 2>/dev/null || true

# tests run in tmp/
rm -rf ./tmp
# A restored CI output cache can contain reports from an older or fuller run.
# Remove only integration reports so a local invocation does not erase unit reports.
find .deder/out/integration-test .deder/out/bsp-integration-test \
    -type f -path '*/reports/junit/TEST-*.xml' -delete 2>/dev/null || true
# Shared local Maven repository for integration tests.
# Plugin artifacts are published here and consumers resolve from here.
mkdir -p tmp/m2
export DEDER_TMP_M2_REPO=$(realpath tmp/m2)

./scripts/build-jars.sh
export DEDER_CLIENT_PATH=$(realpath .deder/out/client/assembly/out.jar)
export DEDER_SERVER_PATH=$(realpath .deder/out/server/assembly/out.jar)
export DEDER_TEST_RUNNER_PATH=$(realpath .deder/out/test-runner/assembly/out.jar)

# Use this branch's server for framework-argument support and its matching Scala runtime.
printf 'localPath=%s\ntestRunnerLocalPath=%s\n' \
    "$DEDER_SERVER_PATH" "$DEDER_TEST_RUNNER_PATH" >> .deder/server.properties
deder shutdown && sleep 1

# Publish plugin-api + config with a fixed version so the hello-plugin integration test can resolve them
# (publishes to DEDER_TMP_M2_REPO when set, otherwise to ~/.m2)
deder exec -t publishLocal -m config -m plugin-api

# Forward suite selectors and framework arguments unchanged to both test modules.
deder exec -t test -m integration-test "$@"
deder exec -t test -m bsp-integration-test "$@"
