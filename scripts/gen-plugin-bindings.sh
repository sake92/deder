#!/bin/bash
# Generate Java bindings from a plugin's Pkl schema file.
#
# Usage: ./scripts/gen-plugin-bindings.sh <myplugin-dir>
#
# Example:
#   ./scripts/gen-plugin-bindings.sh myplugin

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <myplugin-dir>"
    echo "Example: $0 myplugin"
    exit 1
fi

PLUGIN_DIR="$1"

if [ -z "$PLUGIN_DIR" ]; then
    echo "Error: <myplugin-dir> is required"
    exit 1
fi

for file in "$PLUGIN_DIR"/resources/*.pkl; do
    if [ -f "$file" ]; then
        PKL_FILE="$file"
        PLUGIN_DIR_TEMP=$(mktemp -d --suffix=_PKL_JAVA_GEN)
        echo "Generating Java bindings from config: $PKL_FILE"

        pkl-codegen-java "$PKL_FILE" -o "$PLUGIN_DIR_TEMP"

        cp -r "$PLUGIN_DIR_TEMP/java/." "$PLUGIN_DIR/src"
        cp -r "$PLUGIN_DIR_TEMP/resources/." "$PLUGIN_DIR/resources"
    fi
done

echo "Done! Generated Java bindings in $PLUGIN_DIR"
