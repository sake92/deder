
mkdir -p docs/static/config

for tag in $(git tag --sort=-creatordate); do
  echo "Processing version: $tag"
  mkdir -p docs/static/config/$tag
  git show $tag:config/DederProject.pkl > docs/static/config/$tag/DederProject.pkl
done

flatmark build -i docs
