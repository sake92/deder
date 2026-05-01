#!/usr/bin/env bash
set -euo pipefail

echo "=== Setting up hello-plugin-usage ==="

# Clean
rm -rf .metals/ .bsp/ .deder/out/ .deder/logs/ .deder/server.jar

# First publish the hello-plugin if not already done
(cd ../hello-plugin && ./reset.sh)

# Install BSP
mkdir -p .metals
touch .metals/bsp.trace.json
deder bsp install

echo "=== Ready! Try: deder exec -m app -t hello ==="
