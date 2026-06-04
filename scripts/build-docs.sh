#!/bin/bash
set -euo pipefail

mkdir -p docs/static/config

# Always include early-access config from main
shopt -s extglob
mkdir -p docs/static/config/early-access
# copy one level of config files, excluding test files
cp config/!(*Test.pkl) docs/static/config/early-access

# skip early-access tags, as they are already included from main, see above
for tag in $(git tag --sort=-creatordate | grep -v 'early-access'); do
  echo "Processing version: $tag"
  mkdir -p docs/static/config/$tag
  # Copy all non-test config files if they exist
  for file in $(git ls-tree -r --name-only $tag:config | grep -v 'Test.pkl'); do
    if git cat-file -e $tag:config/$file 2>/dev/null; then
      git show $tag:config/$file > docs/static/config/$tag/$file
    fi
  done

done

flatmark build -i docs
