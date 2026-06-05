#!/bin/bash
set -euo pipefail

./scripts/gen-config-bindings.sh

echo "Building client, server, and test-runner JARs..."

deder exec -t assembly -m client -m server -m test-runner
