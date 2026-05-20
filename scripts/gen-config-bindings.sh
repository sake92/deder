echo "Generating Java config bindings from PKL files..."

# generate to temp dir to avoid touching unchanged files (triggers watcher → cache invalidation)
TMP_SRC="config/src.tmp"
rm -rf "$TMP_SRC"

if [ ! -f pkl-codegen-java ]; then
    curl -L -o pkl-codegen-java https://github.com/apple/pkl/releases/download/0.30.2/pkl-codegen-java
fi

chmod +x pkl-codegen-java
./pkl-codegen-java config/DederProject.pkl config/DederCredentials.pkl config/DederTpolecat.pkl config/DederTypelevel.pkl -o "$TMP_SRC"

# rearrange generated structure
mv "$TMP_SRC/resources" config/resources.tmp
mv "$TMP_SRC/java/"* "$TMP_SRC/"
rmdir "$TMP_SRC/java"

# normalize .properties files: strip timestamp comment for deterministic output
find "$TMP_SRC" -name "*.properties" -exec sed -i '/^#/d' {} \;
find config/resources.tmp -name "*.properties" -exec sed -i '/^#/d' {} \; 2>/dev/null || true

# sync generated sources: only overwrite files whose content actually changed
rsync -rc --delete --no-t "$TMP_SRC/" config/src/
rm -rf "$TMP_SRC"

# Bundle all .pkl files (except *Test.pkl) as Pkl module-path classpath resources
mkdir -p config/resources/ba/sake/deder/config
for pklfile in config/*.pkl; do
    basename=$(basename "$pklfile")
    if [[ "$basename" != *"Test.pkl" ]]; then
        rsync -c --no-t "$pklfile" config/resources/ba/sake/deder/config/
    fi
done

# sync generated resources
if [ -d config/resources.tmp ]; then
    rsync -rc --delete --no-t config/resources.tmp/ config/resources/
    rm -rf config/resources.tmp
fi
