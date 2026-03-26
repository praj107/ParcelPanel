#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "GH_TOKEN is required" >&2
  exit 1
fi

source version.properties
VERSION_NAME="${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}"
VERSION_TAG="v${VERSION_NAME}"
RELEASE_DIR="releases/${VERSION_TAG}"

if [[ ! -d "$RELEASE_DIR" ]]; then
  echo "Release directory not found: $RELEASE_DIR" >&2
  exit 1
fi

if gh release view "$VERSION_TAG" >/dev/null 2>&1; then
  gh release upload "$VERSION_TAG" "$RELEASE_DIR"/* --clobber
else
  gh release create "$VERSION_TAG" "$RELEASE_DIR"/* --draft --title "$VERSION_TAG" --notes "ParcelPanel ${VERSION_TAG}"
fi

