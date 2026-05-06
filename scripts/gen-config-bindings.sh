
echo "Generating Java config bindings from PKL files..."

rm -rf config/src
rm -rf config/resources

if [ ! -f pkl-codegen-java ]; then
    curl -L -o pkl-codegen-java https://github.com/apple/pkl/releases/download/0.30.2/pkl-codegen-java
fi

chmod +x pkl-codegen-java
./pkl-codegen-java config/DederProject.pkl config/DederCredentials.pkl -o config/src


mv config/src/resources config/resources

mv config/src/java/* config/src
rmdir config/src/java

# Bundle Pkl schema files as resources so they are available via modulepath: imports
mkdir -p config/resources/ba/sake/deder/config
cp config/DederProject.pkl config/resources/ba/sake/deder/config/
cp config/DederInternals.pkl config/resources/ba/sake/deder/config/
