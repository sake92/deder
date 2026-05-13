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
# if ends with a slash, remove it
PLUGIN_DIR="${PLUGIN_DIR%/}"

if [ -z "$PLUGIN_DIR" ]; then
    echo "Error: <myplugin-dir> is required"
    exit 1
fi

shopt -s nullglob
config_files=("$PLUGIN_DIR"/resources/*.pkl)

if [ ${#config_files[@]} -eq 0 ]; then
    echo "Error: No .pkl config files found in $PLUGIN_DIR/resources"
    exit 1
fi

for file in "${config_files[@]}"; do
    if [ -f "$file" ]; then
        PKL_FILE="$file"
        PLUGIN_DIR_TEMP=$(mktemp -d --suffix=_PKL_JAVA_GEN)
        echo "Generating Java bindings from config: $PKL_FILE"

        pkl-codegen-java "$PKL_FILE" -o "$PLUGIN_DIR_TEMP"

        cp -r "$PLUGIN_DIR_TEMP/java/." "$PLUGIN_DIR/src"
        cp -r "$PLUGIN_DIR_TEMP/resources/." "$PLUGIN_DIR/resources"
        rm -rf "$PLUGIN_DIR_TEMP"
    fi
done

echo "Done! Generated Java bindings in $PLUGIN_DIR"
