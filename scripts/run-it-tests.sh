
rm -rf ./tmp

./scripts/gen-config-bindings.sh

./scripts/build-jars.sh

export DEDER_CLIENT_PATH=$(realpath .deder/out/client/assembly/out.jar)
export DEDER_SERVER_PATH=$(realpath .deder/out/server/assembly/out.jar)
export DEDER_TEST_RUNNER_PATH=$(realpath .deder/out/test-runner/assembly/out.jar)

# Publish plugin-api to local M2 so the hello-plugin integration test sample can depend on it
deder exec -t publishLocal -m plugin-api

# Capture the published plugin-api version from local M2
export DEDER_PLUGIN_API_VERSION=$(ls ~/.m2/repository/ba/sake/deder-plugin-api_3/ 2>/dev/null | head -1)
echo "Plugin API version: $DEDER_PLUGIN_API_VERSION"

if [ $# -eq 0 ]; then
    deder exec -t test -m integration-test 
else
    deder exec -t test -m integration-test $1
fi

