#!/bin/bash
# Generate Java bindings from a plugin's Pkl schema file.
#
# Usage: ./scripts/gen-plugin-bindings.sh <pkl-file> -o <output-dir>
#
# Example:
#   ./scripts/gen-plugin-bindings.sh HelloPlugin.pkl -o src

set -euo pipefail

if [ $# -lt 3 ]; then
    echo "Usage: $0 <pkl-file> -o <output-dir>"
    echo "Example: $0 HelloPlugin.pkl -o src"
    exit 1
fi

PKL_FILE="$1"
shift
OUTPUT_DIR=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        -o) OUTPUT_DIR="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

if [ -z "$OUTPUT_DIR" ]; then
    echo "Error: -o <output-dir> is required"
    exit 1
fi

echo "Generating Java bindings from $PKL_FILE..."

# Download pkl-codegen-java if needed (shared with gen-config-bindings.sh)
if [ ! -f pkl-codegen-java ]; then
    echo "Downloading pkl-codegen-java..."
    curl -L -o pkl-codegen-java https://github.com/apple/pkl/releases/download/0.30.2/pkl-codegen-java
    chmod +x pkl-codegen-java
fi

# Run codegen
./pkl-codegen-java "$PKL_FILE" -o "$OUTPUT_DIR"

# Flatten nested java directory if pkl-codegen-java created one
if [ -d "$OUTPUT_DIR/java" ]; then
    mv "$OUTPUT_DIR/java"/* "$OUTPUT_DIR/" 2>/dev/null || true
    rmdir "$OUTPUT_DIR/java" 2>/dev/null || true
fi

# Move generated resources if any
if [ -d "$OUTPUT_DIR/resources" ]; then
    echo "Note: generated resources/ directory created at $OUTPUT_DIR/resources"
fi

echo "Done! Generated Java bindings in $OUTPUT_DIR"
