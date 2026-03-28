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

for release_file in "${release_files[@]}"; do
  release_name="$(basename "$release_file")"
  if [[ "$release_name" != "SHA256SUMS.txt" && "$release_name" != *.apk ]]; then
    echo "Unexpected release asset present: $release_name" >&2
    exit 1
  fi
done

if gh release view "$VERSION_TAG" >/dev/null 2>&1; then
  mapfile -t existing_assets < <(gh release view "$VERSION_TAG" --json assets --jq '.assets[].name')
  for existing_asset in "${existing_assets[@]}"; do
    keep_asset=false
    for release_file in "${release_files[@]}"; do
      if [[ "$(basename "$release_file")" == "$existing_asset" ]]; then
        keep_asset=true
        break
      fi
    done
    if [[ "$keep_asset" == false ]]; then
      gh release delete-asset "$VERSION_TAG" "$existing_asset" --yes
    fi
  done
  gh release upload "$VERSION_TAG" "${release_files[@]}" --clobber
  if [[ "$(gh release view "$VERSION_TAG" --json isDraft --jq '.isDraft')" == "true" ]]; then
    gh release edit "$VERSION_TAG" --draft=false --title "$VERSION_TAG"
  fi
else
  gh release create "$VERSION_TAG" "${release_files[@]}" --title "$VERSION_TAG" --notes "ParcelPanel ${VERSION_TAG} signed APK release"
fi
