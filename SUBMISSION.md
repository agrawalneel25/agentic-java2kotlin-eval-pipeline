# Evaluation Notes

Repository: https://github.com/agrawalneel25/agentic-java2kotlin-eval-pipeline

## What I built

A Java-to-Kotlin conversion evaluation harness that runs against IntelliJ's static J2K converter. It has three parts:

- `runner/` - an IntelliJ Platform `ApplicationStarter` that opens a temporary project, waits for smart mode, and calls `NewJavaToKotlinConverter.elementsToKotlin`. This is the only way to drive the static converter headlessly; it is not exposed as a stable standalone CLI.
- `src/main/kotlin/j2keval/` - a Kotlin evaluator that scores converted output against Java source. It checks file coverage, isolated per-file compilation via `kotlinc`, hypothesis patterns, and unsafe markers (`!!`, TODO, NotImplementedError).
- Two corpora: Apache Commons CSV 1.14.1 as the real-world target, and a six-file edge-case corpus designed to surface specific converter behaviors.

## Why Apache Commons CSV

I did not use spring-petclinic. Commons CSV is small enough to inspect by hand but has real Java API shape: builders, enums, exceptions, package-private helpers, parser state, and public API constraints. It is also a release-pinned open-source library, so results are reproducible.

## Results

Edge corpus (six files, using committed fixtures from `fixtures/edge-static-j2k`):

| Metric | Value |
|---|---:|
| Java files | 6 |
| Kotlin files found | 6 |
| Isolated compile passed | 4 / 6 |
| Hypothesis checks passed | 7 / 7 |
| Unsafe markers | 0 |

Real-world benchmark, Apache Commons CSV 1.14.1 (12 files, structural check only):

| Metric | Value |
|---|---:|
| Java files | 12 |
| Converted Kotlin files | 12 |
| Unsafe markers | 0 |

## The interesting failure

`try-with-resources/Sample.java`:

```java
public String firstLine(Reader input) throws IOException {
    try (BufferedReader reader = new BufferedReader(input)) {
        return reader.readLine();
    }
}
```

J2K converts `try (...)` to `use` correctly, but returns `reader.readLine()` from a function declared as non-null `String`. Kotlin's `BufferedReader.readLine()` returns `String?`, so the isolated compile fails. This is a real semantic mismatch, not a formatting issue.

`ReadLineNullabilityFix` in `src/main/kotlin/j2keval/ReadLineNullabilityFix.kt` shows the post-processing shape: detect the specific unsafe pattern, rewrite the return, re-evaluate.

## How to run

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.1.1\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat test
.\gradlew.bat run --args="--java edge-cases/java --kotlin fixtures/edge-static-j2k --expectations edge-cases/expectations.txt --report reports/EDGE_CASE_RESULTS.md --jsonl reports/edge-case-results.jsonl --isolated-compile"
```

Linux/macOS:

```bash
./gradlew test
bash scripts/run-edge-eval.sh
```

## Scope and limits

CI runs the evaluator tests, builds the runner module, runs the edge-case evaluator, fetches Commons CSV, and checks the committed reports. The live `runIde` conversion is a local step because the IntelliJ sandbox can hang under headless Linux. Committed reports in `reports/` were produced from an actual local J2K run, not mocked output.

Module-level compilation of Commons CSV remains the next bar. Isolated per-file compilation catches individual nullability issues but cannot check cross-file type references.

