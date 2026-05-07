$ErrorActionPreference = 'Stop'

if (Test-Path reports) {
    New-Item -ItemType Directory -Force reports | Out-Null
}

.\gradlew.bat run --args="--java edge-cases/java --kotlin fixtures/edge-static-j2k --expectations edge-cases/expectations.txt --report reports/EDGE_CASE_RESULTS.md --jsonl reports/edge-case-results.jsonl --isolated-compile"
