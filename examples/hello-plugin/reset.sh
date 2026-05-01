#!/usr/bin/env bash
set -euo pipefail

echo "=== Building and publishing hello-plugin to local Maven ==="

# Clean
rm -rf .metals/ .bsp/ .deder/out/ .deder/logs/ .deder/server.jar

# Make sure Deder's plugin-api is published first
(cd ../.. && deder exec -t publishLocal -m plugin-api)

# Install BSP and build
deder bsp install
deder exec -t publishLocal -m hello-plugin

echo "=== hello-plugin published to ~/.m2 ==="
