#!/usr/bin/env bash
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-/home/pranav/Android/Sdk}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
BUILD_TOOLS_DIR="${ANDROID_SDK_ROOT}/build-tools/35.0.0"
APKSIGNER="${BUILD_TOOLS_DIR}/apksigner"
ZIPALIGN="${BUILD_TOOLS_DIR}/zipalign"

APK_PATH="${1:-}"
if [[ -z "$APK_PATH" ]]; then
  mapfile -d '' candidate_apks < <(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' ! -name '*-unsigned.apk' -print0 | sort -z)
  if [[ "${#candidate_apks[@]}" -ne 1 ]]; then
    echo "Expected exactly one signed release APK to verify, found ${#candidate_apks[@]}" >&2
    exit 1
  fi
  APK_PATH="${candidate_apks[0]}"
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found: $APK_PATH" >&2
  exit 1
fi

if [[ "$APK_PATH" == *-unsigned.apk ]]; then
  echo "Unsigned APK detected: $APK_PATH" >&2
  exit 1
fi

if [[ ! -x "$APKSIGNER" ]]; then
  echo "apksigner not found at $APKSIGNER" >&2
  exit 1
fi

if [[ -x "$ZIPALIGN" ]]; then
  "$ZIPALIGN" -c -P 16 -v 4 "$APK_PATH"
fi

"$APKSIGNER" verify --verbose --print-certs "$APK_PATH"
