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

find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' -exec cp {} "$TARGET_DIR/" \;
find app/build/outputs/bundle/release -maxdepth 1 -type f -name '*.aab' -exec cp {} "$TARGET_DIR/" \;
find app/build/outputs/mapping/release -type f -print0 2>/dev/null | while IFS= read -r -d '' mapping_file; do
  relative_name="${mapping_file#app/build/outputs/mapping/release/}"
  cp "$mapping_file" "${TARGET_DIR}/${relative_name//\//-}"
done

if command -v sha256sum >/dev/null 2>&1; then
  checksum_file="$TARGET_DIR/SHA256SUMS.txt"
  : > "$checksum_file"
  find "$TARGET_DIR" -maxdepth 1 -type f \( -name '*.apk' -o -name '*.aab' \) -print0 | while IFS= read -r -d '' artifact; do
    sha256sum "$artifact" >> "$checksum_file"
  done
fi

printf 'PACKAGED_DIR=%s\n' "$TARGET_DIR"
