
rm -rf .metals/ .bsp/ .scala-build/ .idea/ .vscode/ .deder/out/ .deder/logs/
mkdir -p .metals
touch .metals/bsp.trace.json

deder shutdown
#deder bsp install
