#!/bin/sh
set -e
./gradlew build
REMOTE=$(git remote get-url origin)
rm -rf build/tmp/ghpages
mkdir -p build/tmp/ghpages
cp src/main/web/* build/tmp/ghpages
cp build/bundle/* build/tmp/ghpages
(
  cd build/tmp/ghpages
  git init
  git remote add origin $REMOTE
  git add .
  git commit -m "GH PAGES"
  git push --force origin master:gh-pages
)
echo "Deploy Done"