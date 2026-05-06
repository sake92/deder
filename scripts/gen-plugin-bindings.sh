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

pkl-codegen-java "$PKL_FILE" -o "$OUTPUT_DIR"

# TODO Revisit and polish this script !!!
shopt -s globstar
#for file in $OUTPUT_DIR/java/**/*.java; do
  #  mv "$file" "$OUTPUT_DIR"
#done
#rm -rf "$OUTPUT_DIR/java"

#for file in $OUTPUT_DIR/resources/**; do
    #mv "$file" "$OUTPUT_DIR/.."
#done
#rm -rf "$OUTPUT_DIR/resources"

echo "Done! Generated Java bindings in $OUTPUT_DIR"
