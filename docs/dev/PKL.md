

curl -L -o pkl-codegen-java 'https://github.com/apple/pkl/releases/download/0.29.0/pkl-codegen-java'

./pkl-codegen-java --version

./pkl-codegen-java config/Project.pkl -o config/src

