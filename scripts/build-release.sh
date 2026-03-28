#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/javac" ]]; then
  export JAVA_HOME="$(./scripts/ci/resolve-java-home.sh)"
fi
export ANDROID_HOME="${ANDROID_HOME:-/home/pranav/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

if [[ -f release-signing.properties ]]; then
  set -a
  source release-signing.properties
  set +a
fi

required_vars=(
  PARCELPANEL_SIGNING_STORE_FILE
  PARCELPANEL_SIGNING_STORE_PASSWORD
  PARCELPANEL_SIGNING_KEY_ALIAS
  PARCELPANEL_SIGNING_KEY_PASSWORD
)

for key in "${required_vars[@]}"; do
  if [[ -z "${!key:-}" ]]; then
    echo "Missing signing configuration: ${key}" >&2
    exit 1
  fi
done

if [[ ! -f "${PARCELPANEL_SIGNING_STORE_FILE}" ]]; then
  echo "Signing keystore not found: ${PARCELPANEL_SIGNING_STORE_FILE}" >&2
  exit 1
fi

./gradlew --no-daemon assembleRelease
bash scripts/ci/verify-release-apk.sh
