#!/bin/bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./scripts/run-it-tests.sh [module-id|suite-selector]

Run all integration test modules without arguments. A module ID runs only that
module; a legacy suite selector runs in its matching module.
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
    exit 0
fi

cleanup() {
    deder shutdown >/dev/null 2>&1 || true
    sleep 1
}

trap cleanup EXIT

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

# The outer integration suites also need this branch's Scala runtime, rather
# than the older runtime bundled in the released bootstrap test runner.
echo "testRunnerLocalPath=$DEDER_TEST_RUNNER_PATH" >> .deder/server.properties
deder shutdown && sleep 1

# Publish plugin-api + config with a fixed version so the hello-plugin integration test can resolve them
# (publishes to DEDER_TMP_M2_REPO when set, otherwise to ~/.m2)
deder exec -t publishLocal -m config -m plugin-api

if (($# == 0)); then
    deder exec -t test -m jvm-integration-test
    deder exec -t test -m scalajs-integration-test
    deder exec -t test -m scalanative-integration-test
    deder exec -t test -m bsp-integration-test
else
    case "$1" in
        jvm-integration-test|scalajs-integration-test|scalanative-integration-test|bsp-integration-test)
            module="$1"
            shift
            ;;
        ba.sake.deder.bsp.*)
            module="bsp-integration-test"
            ;;
        ba.sake.deder.ScalaJsIntegrationSuite)
            module="scalajs-integration-test"
            ;;
        ba.sake.deder.ScalaNativeIntegrationSuite)
            module="scalanative-integration-test"
            ;;
        *)
            module="jvm-integration-test"
            ;;
    esac
    deder exec -t test -m "$module" "$@"
fi
