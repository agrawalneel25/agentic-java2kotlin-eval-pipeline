#!/usr/bin/env bash
set -euo pipefail

mkdir -p work
if [ ! -d work/commons-csv/.git ]; then
  git clone --depth 1 --branch rel/commons-csv-1.14.1 https://github.com/apache/commons-csv.git work/commons-csv
fi

find work/commons-csv/src/main/java -name '*.java' | sort | wc -l
