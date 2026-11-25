#!/usr/bin/env bash
# Installs the packages of the Android SDK that the build of the app needs (ANDROID_HOME): its platform and build
# tools, as the POMs name them; with --emulator, the emulator and the system image of AndroidIT too.
# The Android CLI (`android sdk`) when the command-line tools have it, sdkmanager (deprecated) otherwise.
# Usage: ci/android-sdk.sh [--emulator]
set -euo pipefail

: "${ANDROID_HOME:?the Android SDK}"
platform=$(./mvnw -B -q -N help:evaluate -Dexpression=android.platform -DforceStdout -Pandroid)
tools=$(./mvnw -B -q -pl signaire-android help:evaluate -Dexpression=android.buildTools.version -DforceStdout -Pandroid)
packages=("platforms/$platform" "build-tools/$tools")
if [[ "${1:-}" == "--emulator" ]]; then
    packages+=("platform-tools" "emulator" "system-images/android-34/google_apis/x86_64")
fi

bin="$ANDROID_HOME/cmdline-tools/latest/bin"
if [[ -x "$bin/android" ]]; then
    "$bin/android" --no-metrics sdk install "${packages[@]}"
else
    yes | "$bin/sdkmanager" --licenses > /dev/null || true
    "$bin/sdkmanager" "${packages[@]//\//;}"
fi
