
rm -rf ./tmp

./scripts/gen-config-bindings.sh

./scripts/build-jars.sh

export DEDER_CLIENT_PATH=$(realpath .deder/out/client/assembly/out.jar)
export DEDER_SERVER_PATH=$(realpath .deder/out/server/assembly/out.jar)
export DEDER_TEST_RUNNER_PATH=$(realpath .deder/out/test-runner/assembly/out.jar)

# Shared local Maven repository for integration tests.
# Plugin artifacts are published here and consumers resolve from here.
export DEDER_TMP_M2_REPO=$(realpath tmp/m2)
mkdir -p "$DEDER_TMP_M2_REPO"

# Publish plugin-api with a fixed version so the hello-plugin integration test can resolve it
# (publishes to DEDER_TMP_M2_REPO when set, otherwise to ~/.m2)
VERSION=0.1.0-SNAPSHOT deder exec -t publishLocal -m plugin-api

export DEDER_PLUGIN_API_VERSION=0.1.0-SNAPSHOT
echo "Plugin API version: $DEDER_PLUGIN_API_VERSION"
echo "Shared M2 repo: $DEDER_TMP_M2_REPO"

if [ $# -eq 0 ]; then
    deder exec -t test -m integration-test 
else
    deder exec -t test -m integration-test $1
fi

