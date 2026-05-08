# Task #1 - Solution

GitHub repo:

`https://github.com/agrawalneel25/agentic-java2kotlin-eval-pipeline`

I built a Kotlin evaluation harness for static Java-to-Kotlin conversion. The pipeline targets Apache Commons CSV 1.14.1 as the real-world benchmark instead of spring-petclinic, and it includes a custom edge-case corpus designed to stress J2K behavior.

The evaluator is written in Kotlin:

```text
src/main/kotlin/j2keval/Main.kt
```

It reads a Java source directory and a converted Kotlin directory, then writes:

- Markdown report
- JSONL metrics
- per-file conversion status
- optional isolated `kotlinc` compile result
- hypothesis checks from regex expectations
- unsafe marker counts for `!!`, `TODO`, and `NotImplementedError`

The GitHub Action runs:

```text
.github/workflows/eval.yml
```

It tests the evaluator, builds and validates the IntelliJ static-J2K runner module, runs the edge-case evaluation, and fetches the Apache Commons CSV benchmark source.

I also added a static J2K runner:

```text
runner/src/main/kotlin/j2k/runner/J2KStarter.kt
```

It is an IntelliJ `ApplicationStarter` named `j2k`. It opens a temporary project, attaches a JDK and Java source root, waits for indexing to finish, then calls `NewJavaToKotlinConverter.elementsToKotlin`.

The script `scripts/run-static-j2k.sh` runs that runner by default. It can also use `J2K_RUNNER_CMD` if the runner is provided externally. In CI, I build and validate the runner, but keep full conversion execution optional because `runIde` can hang under headless Linux before the starter dispatches. I documented this boundary instead of hiding it.

Current edge-case results:

| Metric | Value |
|---|---:|
| Java files | 6 |
| Kotlin files found | 6 |
| Isolated compile passed | 4 / 6 |
| Hypothesis checks passed | 7 / 7 |
| Unsafe markers | 0 |

Reports:

- `SUMMARY.md`
- `EDGE_CASE_REPORT.md`
- `reports/EDGE_CASE_RESULTS.md`
- `reports/COMMONS_CSV_SOURCE_SCAN.md`

The clearest finding is the `try-with-resources` case. J2K-style output preserves `use`, but returns `reader.readLine()` from a method declared as non-null `String`. Kotlin treats `readLine()` as nullable, so the isolated compile check catches the mismatch. I added `ReadLineNullabilityFix` as a small Kotlin post-processing rule that demonstrates how one identified failure could be repaired.

Local verification:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.1.1\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat test
.\gradlew.bat run --args="--java edge-cases/java --kotlin fixtures/edge-static-j2k --expectations edge-cases/expectations.txt --report reports/EDGE_CASE_RESULTS.md --jsonl reports/edge-case-results.jsonl --isolated-compile"
```
