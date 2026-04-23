
rm -rf ./tmp

./scripts/gen-config-bindings.sh

#deder exec -m server -t assembly
#deder exec -m client -t assembly

# TODO pass these as file paths instead of env vars
# coz we need to restart deder server
# or we can propagate env vars from client to server, but that seems more complex
export DEDER_SERVER_PATH=$(realpath .deder/out/server/assembly/out.jar)
export DEDER_CLIENT_PATH=$(realpath .deder/out/client/assembly/out.jar)

deder shutdown
sleep 1
deder
sleep 1


if [ $# -eq 0 ]; then
    deder exec -m integration-test -t test
else
    deder exec -m integration-test -t test $1
fi

