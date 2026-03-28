#!/usr/bin/env bash
set -euo pipefail

VERSION=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      VERSION="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$VERSION" ]]; then
  source version.properties
  VERSION="${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}"
fi

TARGET_DIR="releases/v${VERSION}"
mkdir -p "$TARGET_DIR"
rm -f "${TARGET_DIR:?}/"*

mapfile -d '' release_apks < <(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' ! -name '*-unsigned.apk' -print0 | sort -z)
if [[ "${#release_apks[@]}" -ne 1 ]]; then
  echo "Expected exactly one signed release APK, found ${#release_apks[@]}" >&2
  exit 1
fi

artifact_name="ParcelPanel-v${VERSION}.apk"
cp "${release_apks[0]}" "${TARGET_DIR}/${artifact_name}"

if command -v sha256sum >/dev/null 2>&1; then
  checksum_file="$TARGET_DIR/SHA256SUMS.txt"
  (
    cd "$TARGET_DIR"
    sha256sum "$artifact_name" > "$(basename "$checksum_file")"
  )
fi

printf 'PACKAGED_DIR=%s\n' "$TARGET_DIR"
