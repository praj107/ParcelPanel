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
find app/build/outputs/mapping/release -maxdepth 2 -type f -exec cp --parents {} "$TARGET_DIR/" \; 2>/dev/null || true

printf 'PACKAGED_DIR=%s\n' "$TARGET_DIR"

