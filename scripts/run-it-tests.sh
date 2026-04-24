
rm -rf ./tmp

./scripts/gen-config-bindings.sh

./mill -i server.assembly
./mill -i client.assembly       

export DEDER_SERVER_PATH=$(realpath out/server/assembly.dest/out.jar)
export DEDER_CLIENT_PATH=$(realpath out/client/assembly.dest/out.jar)

if [ $# -eq 0 ]; then
    ./mill -i integration.test
else
    ./mill -i integration.test.testOnly $1
fi

# TODO
#deder exec -m server -t assembly
#deder exec -m client -t assembly
#export DEDER_SERVER_PATH=$(realpath .deder/out/server/assembly/out.jar)
#export DEDER_CLIENT_PATH=$(realpath .deder/out/client/assembly/out.jar)
#if [ $# -eq 0 ]; then
#    deder exec -m integration-test -t test
#else
#    deder exec -m integration-test -t test $1
#fi

