#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "GH_TOKEN is required" >&2
  exit 1
fi

source version.properties

VERSION_NAME="${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}"
RELEASE_TAG="${RELEASE_TAG:-v${VERSION_NAME}}"
RELEASE_BRANCH="${RELEASE_BRANCH:-main}"
FORCE_RELEASE_TAG_UPDATE="${FORCE_RELEASE_TAG_UPDATE:-false}"
GIT_USER_NAME="${GIT_USER_NAME:-ParcelPanel Jenkins}"
GIT_USER_EMAIL="${GIT_USER_EMAIL:-parcelpanel-jenkins@local}"

origin_url="$(git remote get-url origin)"
repo_path="$(printf '%s' "$origin_url" | sed -E 's#^https://github.com/##; s#^git@github.com:##; s#\.git$##')"

if [[ "$repo_path" == "$origin_url" ]]; then
  echo "Unsupported origin URL: $origin_url" >&2
  exit 1
fi

auth_url="https://x-access-token:${GH_TOKEN}@github.com/${repo_path}.git"

git config user.name "$GIT_USER_NAME"
git config user.email "$GIT_USER_EMAIL"

git add version.properties
if ! git diff --cached --quiet; then
  git commit -m "Release ${RELEASE_TAG}"
fi

if [[ "$FORCE_RELEASE_TAG_UPDATE" == "true" ]]; then
  if git rev-parse "${RELEASE_TAG}" >/dev/null 2>&1; then
    git tag -d "${RELEASE_TAG}" >/dev/null 2>&1 || true
  fi
  git tag -a "${RELEASE_TAG}" -m "Release ${RELEASE_TAG}"
elif ! git rev-parse "${RELEASE_TAG}" >/dev/null 2>&1; then
  git tag -a "${RELEASE_TAG}" -m "Release ${RELEASE_TAG}"
fi

git push "$auth_url" "HEAD:${RELEASE_BRANCH}"

if [[ "$FORCE_RELEASE_TAG_UPDATE" == "true" ]]; then
  git push "$auth_url" "refs/tags/${RELEASE_TAG}" --force
elif git ls-remote --exit-code --tags "$auth_url" "refs/tags/${RELEASE_TAG}" >/dev/null 2>&1; then
  echo "Remote tag ${RELEASE_TAG} already exists"
else
  git push "$auth_url" "refs/tags/${RELEASE_TAG}"
fi
