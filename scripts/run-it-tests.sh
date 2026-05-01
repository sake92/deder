
rm -rf ./tmp

./scripts/gen-config-bindings.sh

./scripts/build-jars.sh

export DEDER_CLIENT_PATH=$(realpath .deder/out/client/assembly/out.jar)
export DEDER_SERVER_PATH=$(realpath .deder/out/server/assembly/out.jar)
export DEDER_TEST_RUNNER_PATH=$(realpath .deder/out/test-runner/assembly/out.jar)

# Publish plugin-api to local M2 with a fixed version so the hello-plugin integration test can resolve it
VERSION=0.1.0-SNAPSHOT deder exec -t publishLocal -m plugin-api

export DEDER_PLUGIN_API_VERSION=0.1.0-SNAPSHOT
echo "Plugin API version: $DEDER_PLUGIN_API_VERSION"

if [ $# -eq 0 ]; then
    deder exec -t test -m integration-test 
else
    deder exec -t test -m integration-test $1
fi

