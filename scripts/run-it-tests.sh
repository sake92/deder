
rm -rf ./tmp

./scripts/gen-config-bindings.sh

deder exec -t assembly -m server -m client -m test-runner

export DEDER_CLIENT_PATH=$(realpath .deder/out/client/assembly/out.jar)
export DEDER_SERVER_PATH=$(realpath .deder/out/server/assembly/out.jar)
export DEDER_TEST_RUNNER_PATH=$(realpath .deder/out/test-runner/assembly/out.jar)

if [ $# -eq 0 ]; then
    deder exec -t test -m integration-test 
else
    deder exec -t test -m integration-test $1
fi

