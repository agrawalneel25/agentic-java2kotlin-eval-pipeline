#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: scripts/run-static-j2k.sh <java-source-dir> <kotlin-output-dir>" >&2
  exit 2
fi

input="$(cd "$1" && pwd)"
mkdir -p "$2"
output="$(cd "$2" && pwd)"

if [ -n "${J2K_RUNNER_CMD:-}" ]; then
  cmd="${J2K_RUNNER_CMD//\{input\}/$input}"
  cmd="${cmd//\{output\}/$output}"
  eval "$cmd"
else
  ./gradlew :runner:runIde --args="j2k $input $output"
fi
