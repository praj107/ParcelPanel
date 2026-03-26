#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/javac" ]]; then
  export JAVA_HOME="$(./scripts/ci/resolve-java-home.sh)"
fi
export ANDROID_HOME="${ANDROID_HOME:-/home/pranav/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

./gradlew --no-daemon assembleRelease bundleRelease
