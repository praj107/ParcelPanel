#!/usr/bin/env bash
set -euo pipefail

source version.properties

VERSION_NAME="${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}"
VERSION_TAG="v${VERSION_NAME}"

printf 'VERSION_NAME=%s\n' "$VERSION_NAME"
printf 'VERSION_TAG=%s\n' "$VERSION_TAG"

