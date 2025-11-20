

rm -rf config/src
rm -rf config/resources

./pkl-codegen-java config/DederProject.pkl -o config/src


mv config/src/resources config/resources

mv config/src/java/* config/src
rmdir config/src/java
