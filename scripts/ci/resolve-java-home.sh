#!/usr/bin/env bash
set -euo pipefail

declare -a candidates=()

add_candidate() {
  local candidate="${1:-}"
  if [[ -n "$candidate" && -d "$candidate" ]]; then
    candidates+=("$candidate")
  fi
}

add_candidate "${JAVA_HOME:-}"

if command -v javac >/dev/null 2>&1; then
  javac_path="$(readlink -f "$(command -v javac)")"
  add_candidate "$(cd "$(dirname "$javac_path")/.." && pwd)"
fi

add_candidate "${HOME:-}/.sdkman/candidates/java/current"
add_candidate "/usr/lib/jvm/java-21-openjdk-amd64"
add_candidate "/usr/lib/jvm/java-17-openjdk-amd64"
add_candidate "/usr/lib/jvm/default-java"

for candidate in "${candidates[@]}"; do
  if [[ -x "$candidate/bin/javac" && -x "$candidate/bin/java" ]]; then
    printf '%s\n' "$candidate"
    exit 0
  fi
done

echo "Unable to locate a full JDK with javac. Set JAVA_HOME explicitly." >&2
exit 1
