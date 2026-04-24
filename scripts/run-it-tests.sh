
rm -rf ./tmp

./scripts/gen-config-bindings.sh

deder exec -m server -m client -t assembly

export DEDER_SERVER_PATH=$(realpath .deder/out/server/assembly/out.jar)
export DEDER_CLIENT_PATH=$(realpath .deder/out/client/assembly/out.jar)

if [ $# -eq 0 ]; then
    deder exec -t test -m integration-test 
else
    deder exec -t test -m integration-test $1
fi

