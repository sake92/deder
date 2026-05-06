
mkdir -p docs/static/config

# Always include early-access config from main
shopt -s extglob
mkdir -p docs/static/config/early-access
# copy one level of config files, excluding test files
cp config/!(*Test.pkl) docs/static/config/early-access

for tag in $(git tag --sort=-creatordate); do
  echo "Processing version: $tag"
  mkdir -p docs/static/config/$tag
  git show $tag:config/DederProject.pkl > docs/static/config/$tag/DederProject.pkl
  # Copy DederInternals if it exists in this tag
  if git cat-file -e $tag:config/DederInternals.pkl 2>/dev/null; then
    git show $tag:config/DederInternals.pkl > docs/static/config/$tag/DederInternals.pkl
  fi
  if git cat-file -e $tag:config/DederCredentials.pkl 2>/dev/null; then
    git show $tag:config/DederCredentials.pkl > docs/static/config/$tag/DederCredentials.pkl
  fi
done

flatmark build -i docs
