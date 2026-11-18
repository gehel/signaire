#!/usr/bin/env bash
# Packages the standalone application with its own Java runtime, for the system this runs on: an application image
# (a Signaire folder with a Signaire launcher; Signaire.app on macOS), so that users need no Java.
# Usage: ci/package-standalone.sh <standalone jar> <output directory> [version]
set -euo pipefail

# no realpath -m on macOS
mkdir -p "$2"
out=$(cd "$2" && pwd)
version=${3:-1.0}
input=$(mktemp -d)
trap 'rm -rf "$input"' EXIT
cp "$1" "$input/signaire-standalone.jar"
# the main class is the one of the jar (the Spring Boot launcher); Lucene maps its files with the foreign function
# API, which the manifest of the jar allows only with java -jar
jpackage --type app-image \
    --name Signaire \
    --app-version "$version" \
    --description "Swiss sign languages: search, lists and exports of the SignSuisse lexicon" \
    --input "$input" \
    --main-jar signaire-standalone.jar \
    --java-options --enable-native-access=ALL-UNNAMED \
    --jlink-options "--strip-debug --no-man-pages --no-header-files" \
    --dest "$out"
echo "Packaged in $out"
