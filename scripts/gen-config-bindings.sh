echo "Generating Java config bindings from PKL files..."

rm -rf config/src
rm -rf config/resources

if [ ! -f pkl-codegen-java ]; then
    curl -L -o pkl-codegen-java https://github.com/apple/pkl/releases/download/0.30.2/pkl-codegen-java
fi

chmod +x pkl-codegen-java
./pkl-codegen-java config/DederProject.pkl config/DederCredentials.pkl config/DederTpolecat.pkl config/DederTypelevel.pkl -o config/src

mv config/src/resources config/resources

mv config/src/java/* config/src
rmdir config/src/java

# Bundle all .pkl files (except *Test.pkl) as Pkl module-path classpath resources
mkdir -p config/resources/pkl/ba/sake/deder/config
for pklfile in config/*.pkl; do
    basename=$(basename "$pklfile")
    if [[ "$basename" != *"Test.pkl" ]]; then
        cp "$pklfile" config/resources/pkl/ba/sake/deder/config/
    fi
done
