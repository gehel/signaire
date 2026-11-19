#!/usr/bin/env bash
# Packages the standalone application with its own Java runtime, for the system this runs on: an application image
# (a Signaire folder with a Signaire launcher; Signaire.app on macOS), so that users need no Java.
# Usage: ci/package-standalone.sh <standalone jar> <output directory> [version]
set -euo pipefail

# no realpath -m on macOS
mkdir -p "$2"
out=$(cd "$2" && pwd)
version=${3:-1.0}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# the jar extracted (the application jar and lib/): the JVM can only cache classes read from plain jars
java -Djarmode=tools -jar "$1" extract --destination "$work/input"
main_jar=$(cd "$work/input" && ls -- *.jar)
# Lucene maps its files with the foreign function API: the manifest allows it to java -jar only
jpackage --type app-image \
    --name Signaire \
    --app-version "$version" \
    --description "Swiss sign languages: search, lists and exports of the SignSuisse lexicon" \
    --input "$work/input" \
    --main-jar "$main_jar" \
    --java-options --enable-native-access=ALL-UNNAMED \
    --jlink-options "--strip-debug --no-man-pages --no-header-files" \
    --dest "$out"

case "$(uname -s)" in
    Darwin) app="$out/Signaire.app/Contents/app"; java="$out/Signaire.app/Contents/runtime/Contents/Home/bin/java" ;;
    MINGW* | MSYS* | CYGWIN*) app="$out/Signaire/app"; java="$out/Signaire/runtime/bin/java.exe" ;;
    *) app="$out/Signaire/lib/app"; java="$out/Signaire/lib/runtime/bin/java" ;;
esac

# AOT cache of the JVM (classes loaded and linked, profiles): the application starts about three times faster. A
# training run with the runtime and the jars of the image, which stops once the application is started; the JVM
# ignores a cache that does not match (another runtime, other jars).
directories="$work/training"
"$java" -XX:AOTCacheOutput="$app/signaire.aot" --enable-native-access=ALL-UNNAMED \
    -Dspring.context.exit=onRefresh \
    -jar "$app/$main_jar" \
    --server.port=0 \
    --signaire.standalone.open-browser=false \
    --signaire.bundles.directory="$directories/bundles" \
    --signaire.media.cache="$directories/media" \
    --signaire.index.directory="$directories/index" \
    --signaire.state.directory="$directories/state" \
    --signaire.config.directory="$directories/config"
# the launcher reads its options from Signaire.cfg; $APPDIR is the directory of the jars
printf '%s\n' 'java-options=-XX:AOTCache=$APPDIR/signaire.aot' >> "$app/Signaire.cfg"
echo "Packaged in $out"
