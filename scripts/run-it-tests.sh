
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

