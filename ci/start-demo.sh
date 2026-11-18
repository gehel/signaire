#!/usr/bin/env bash
# Starts Signaire on bundles of invented signs, for the measures of CI (Lighthouse, sitespeed.io), on
# http://localhost:8080. Expects the jars to be built, and Docker. Usage: ci/start-demo.sh [work directory]
set -euo pipefail

work=$(realpath -m "${1:-target/demo}")
mkdir -p "$work"
docker compose up -d
./mvnw -B -ntp -q -pl signaire-it exec:java -Dexec.classpathScope=test \
    -Dexec.mainClass=fr.ledcom.signaire.it.DemoBundles -Dexec.args="$work/bundles"
jar=$(find signaire-web/target -maxdepth 1 -name 'signaire-web-*.jar' ! -name '*-sources.jar' | head -1)
XDG_DATA_HOME="$work/data" XDG_CACHE_HOME="$work/cache" XDG_CONFIG_HOME="$work/config" \
    nohup java -jar "$jar" --signaire.bundles.directory="$work/bundles" > "$work/web.log" 2>&1 &
echo $! > "$work/web.pid"
for _ in $(seq 1 150); do
    if curl -sf 'http://localhost:8080/?q=arbre' | grep -q '/signs/90000'; then
        echo "Signaire is ready on http://localhost:8080"
        exit 0
    fi
    sleep 2
done
echo "Signaire did not start, see $work/web.log" >&2
tail -50 "$work/web.log" >&2
exit 1
