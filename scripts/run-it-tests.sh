
rm -rf ./tmp

./scripts/gen-config-bindings.sh

deder exec -m server -m client -t assembly

export DEDER_SERVER_PATH=$(realpath .deder/out/server/assembly/out.jar)
export DEDER_CLIENT_PATH=$(realpath .deder/out/client/assembly/out.jar)

if [ $# -eq 0 ]; then
    deder exec -m integration-test -t test
else
    deder exec -m integration-test -t test $1
fi

