#!/bin/bash
set -euo pipefail

export DEDER_PLUGIN_API_VERSION="it-test-version"
export VERSION="$DEDER_PLUGIN_API_VERSION"

# Clean up any lingering server artifacts in sample projects
find integration/test/resources/sample-projects -name ".deder" -type d -exec rm -rf {} + 2>/dev/null || true

# tests run in tmp/
rm -rf ./tmp
# Shared local Maven repository for integration tests.
# Plugin artifacts are published here and consumers resolve from here.
mkdir -p tmp/m2
export DEDER_TMP_M2_REPO=$(realpath tmp/m2)

./scripts/build-jars.sh
export DEDER_CLIENT_PATH=$(realpath .deder/out/client/assembly/out.jar)
export DEDER_SERVER_PATH=$(realpath .deder/out/server/assembly/out.jar)
export DEDER_TEST_RUNNER_PATH=$(realpath .deder/out/test-runner/assembly/out.jar)

# Publish plugin-api + config with a fixed version so the hello-plugin integration test can resolve them
# (publishes to DEDER_TMP_M2_REPO when set, otherwise to ~/.m2)
deder exec -t publishLocal -m config -m plugin-api

if [ $# -eq 0 ]; then
    deder exec -t test -m integration-test 
else
    deder exec -t test -m integration-test $1
fi
