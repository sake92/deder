#!/bin/bash
set -euo pipefail

echo "Generating Java config bindings from PKL files..."

### PKL codegen
# use temp dir to avoid touching unchanged files (triggers watcher → cache invalidation)
# and to map the folder structure to flat-style
TMP_GEN="config/tmp_gen"
rm -rf "$TMP_GEN"

if [ ! -f pkl-codegen-java ]; then
    curl -L -o pkl-codegen-java https://github.com/apple/pkl/releases/download/0.30.2/pkl-codegen-java
fi
chmod +x pkl-codegen-java
./pkl-codegen-java config/DederProject.pkl config/DederCredentials.pkl config/DederTpolecat.pkl config/DederTypelevel.pkl config/DederPlugins.pkl -o "$TMP_GEN"

rsync -rc --delete --no-t "$TMP_GEN/java/ba" config/src/

# normalize .properties files: strip timestamp comment for deterministic output and caching
find "$TMP_GEN/resources" -name "*.properties" -exec sed -i '/^#/d' {} \;
rsync -rc --delete --no-t "$TMP_GEN/resources/" config/resources/

rm -rf "$TMP_GEN"

### Bundle all .pkl files (except *Test.pkl) as Pkl module-path classpath resources
mkdir -p config/resources/ba/sake/deder/config
for pklfile in config/*.pkl; do
    basename=$(basename "$pklfile")
    if [[ "$basename" != *"Test.pkl" ]]; then
        cp "$pklfile" config/resources/ba/sake/deder/config/
    fi
done
