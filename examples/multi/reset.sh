
rm -rf .metals/ .bsp/ .deder/out/ .deder/logs/
mkdir -p .metals
touch .metals/bsp.trace.json

deder shutdown
deder bsp install
