#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: scripts/run-static-j2k.sh <java-source-dir> <kotlin-output-dir>" >&2
  exit 2
fi

if [ -z "${J2K_RUNNER_CMD:-}" ]; then
  cat >&2 <<'EOF'
J2K_RUNNER_CMD is not set.

JetBrains' static J2K converter is coupled to IntelliJ IDEA internals rather than exposed as a stable CLI.
This repo keeps the conversion step as an explicit hook:

  J2K_RUNNER_CMD="/path/to/headless-j2k --input {input} --output {output}"

The evaluator and edge-case reports are reproducible without that hook.
EOF
  exit 3
fi

input="$1"
output="$2"
mkdir -p "$output"
cmd="${J2K_RUNNER_CMD//\{input\}/$input}"
cmd="${cmd//\{output\}/$output}"
eval "$cmd"
