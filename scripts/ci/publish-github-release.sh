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
mapfile -d '' release_files < <(find "$RELEASE_DIR" -maxdepth 1 -type f -print0 | sort -z)

if [[ ! -d "$RELEASE_DIR" ]]; then
  echo "Release directory not found: $RELEASE_DIR" >&2
  exit 1
fi

if [[ "${#release_files[@]}" -eq 0 ]]; then
  echo "No release assets found in $RELEASE_DIR" >&2
  exit 1
fi

if gh release view "$VERSION_TAG" >/dev/null 2>&1; then
  gh release upload "$VERSION_TAG" "${release_files[@]}" --clobber
else
  gh release create "$VERSION_TAG" "${release_files[@]}" --draft --title "$VERSION_TAG" --notes "ParcelPanel ${VERSION_TAG}"
fi
