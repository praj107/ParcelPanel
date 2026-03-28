#!/usr/bin/env bash
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME must be set." >&2
  exit 1
fi

export ANDROID_HOME
export ANDROID_SDK_ROOT
CMDLINE_TOOLS_BIN="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin"
if [[ ! -d "$CMDLINE_TOOLS_BIN" ]]; then
  CMDLINE_TOOLS_BIN="$(find "${ANDROID_SDK_ROOT}/cmdline-tools" -maxdepth 2 -type d -name bin | sort | tail -n 1)"
fi
if [[ -z "${CMDLINE_TOOLS_BIN:-}" || ! -d "$CMDLINE_TOOLS_BIN" ]]; then
  echo "Unable to locate Android cmdline-tools inside ${ANDROID_SDK_ROOT}." >&2
  exit 1
fi
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$CMDLINE_TOOLS_BIN:$PATH"

SYSTEM_IMAGE="system-images;android-26;google_apis;x86_64"
AVD_NAME="${AVD_NAME:-parcelpanel-api26}"

set +o pipefail
yes | sdkmanager --licenses >/dev/null
set -o pipefail
sdkmanager \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "emulator" \
  "$SYSTEM_IMAGE"

avdmanager delete avd -n "$AVD_NAME" >/dev/null 2>&1 || true
printf 'no\n' | avdmanager create avd \
  -n "$AVD_NAME" \
  -k "$SYSTEM_IMAGE" \
  --device "pixel" \
  --force >/dev/null

cleanup() {
  adb emu kill >/dev/null 2>&1 || true
  if [[ -n "${EMULATOR_PID:-}" ]]; then
    kill "$EMULATOR_PID" >/dev/null 2>&1 || true
    wait "$EMULATOR_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

emulator \
  -avd "$AVD_NAME" \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -no-snapshot \
  -gpu swiftshader_indirect \
  -camera-back none \
  -camera-front none &
EMULATOR_PID=$!

adb wait-for-device

boot_completed=""
for _ in $(seq 1 120); do
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$boot_completed" == "1" ]]; then
    break
  fi
  sleep 5
done

if [[ "$boot_completed" != "1" ]]; then
  echo "Android emulator failed to boot within timeout." >&2
  adb logcat -d || true
  exit 1
fi

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell input keyevent 82 || true

./gradlew --no-daemon connectedDebugAndroidTest
