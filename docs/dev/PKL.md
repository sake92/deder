

curl -L -o pkl-codegen-java 'https://github.com/apple/pkl/releases/download/0.29.0/pkl-codegen-java'

./pkl-codegen-java --version

./scripts/gen-config-bindings.sh

## Why Pkl?

Scala builds can become very complex.
It is not so uncommon to have many combinatios on multiple axis:
- scala version
- platform: JVM/JS/Native
- linux/mac/win

This makes JSON/YAML/XML not so attractive.
You have to invent new syntax for loops, conditionals etc.

Pkl makes this easy, plus it has types, docs etc.
And it has IDE support.

