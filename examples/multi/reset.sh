
rm -rf .metals/metals.log .bsp/ .deder/out/ .deder/logs/
mkdir -p .metals
touch .metals/bsp.trace.json

# assumes global install
deder shutdown
deder bsp install

DEDER_REPO_ROOT=../..
cp $DEDER_REPO_ROOT/out/server/assembly.dest/out.jar .deder/server.jar

