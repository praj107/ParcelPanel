#!/usr/bin/env bash
set -euo pipefail

RELEASE_TYPE="${1:-patch}"
source version.properties

case "$RELEASE_TYPE" in
  patch)
    VERSION_PATCH=$((VERSION_PATCH + 1))
    ;;
  minor)
    VERSION_MINOR=$((VERSION_MINOR + 1))
    VERSION_PATCH=0
    ;;
  major)
    VERSION_MAJOR=$((VERSION_MAJOR + 1))
    VERSION_MINOR=0
    VERSION_PATCH=0
    ;;
  chore)
    ;;
  *)
    echo "Unsupported release type: $RELEASE_TYPE" >&2
    exit 1
    ;;
esac

cat > version.properties <<EOF
VERSION_MAJOR=${VERSION_MAJOR}
VERSION_MINOR=${VERSION_MINOR}
VERSION_PATCH=${VERSION_PATCH}
EOF

VERSION_NAME="${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}"
VERSION_TAG="v${VERSION_NAME}"

printf 'VERSION_NAME=%s\n' "$VERSION_NAME"
printf 'VERSION_TAG=%s\n' "$VERSION_TAG"

