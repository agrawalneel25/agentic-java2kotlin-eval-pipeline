#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: scripts/run-static-j2k.sh <java-source-dir> <kotlin-output-dir>" >&2
  exit 2
fi

input="$1"
output="$2"
mkdir -p "$output"

if [ -n "${J2K_RUNNER_CMD:-}" ]; then
  cmd="${J2K_RUNNER_CMD//\{input\}/$input}"
  cmd="${cmd//\{output\}/$output}"
  eval "$cmd"
else
  ./gradlew :runner:runIde --args="j2k $input $output"
fi
