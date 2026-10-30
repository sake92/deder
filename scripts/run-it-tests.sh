#!/bin/bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./scripts/run-it-tests.sh [--shard jvm-js|native|bsp] [suite-selector]

Run the integration tests, optionally selecting one independent shard. Without
--shard, run the existing integration and BSP suites in sequence.
EOF
}

die() {
    echo "run-it-tests.sh: $*" >&2
    echo "Try --help for usage." >&2
    exit 2
}

shard=""
suite_selector=""
while (($# > 0)); do
    case "$1" in
        --help|-h)
            usage
            exit 0
            ;;
        --shard)
            (($# >= 2)) || die "--shard requires one of: jvm-js, native, bsp"
            [[ -n "$2" ]] || die "--shard requires one of: jvm-js, native, bsp"
            [[ -z "$shard" ]] || die "--shard may only be specified once"
            shard="$2"
            shift 2
            ;;
        --*)
            die "unknown option: $1"
            ;;
        *)
            [[ -z "$suite_selector" ]] || die "only one suite selector may be specified"
            suite_selector="$1"
            shift
            ;;
    esac
done

case "$shard" in
    ""|jvm-js|native|bsp) ;;
    *) die "invalid shard '$shard'; expected jvm-js, native, or bsp" ;;
esac

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

run_test_task() {
    local module="$1"
    shift
    deder exec -t test -m "$module" "$@"
}

case "$shard" in
    jvm-js)
        selectors=()
        [[ -z "$suite_selector" ]] || selectors+=("$suite_selector")
        selectors+=("~ba.sake.deder.ScalaNativeIntegrationSuite")
        run_test_task integration-test "${selectors[@]}"
        ;;
    native)
        if [[ -n "$suite_selector" ]]; then
            run_test_task integration-test "$suite_selector"
        else
            run_test_task integration-test ba.sake.deder.ScalaNativeIntegrationSuite
        fi
        ;;
    bsp)
        if [[ -n "$suite_selector" ]]; then
            run_test_task bsp-integration-test "$suite_selector"
        else
            run_test_task bsp-integration-test
        fi
        ;;
    "")
        if [[ -n "$suite_selector" ]]; then
            run_test_task integration-test "$suite_selector"
        else
            run_test_task integration-test
            run_test_task bsp-integration-test
        fi
        ;;
esac
