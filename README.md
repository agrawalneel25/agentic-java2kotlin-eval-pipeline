# Agentic Java2Kotlin Eval Pipeline

Java-to-Kotlin conversion evaluation harness built around IntelliJ's static J2K converter. Three parts:

- an IntelliJ Platform runner that calls the static J2K converter
- a Kotlin evaluator for converted output
- reports for Apache Commons CSV and a small edge-case corpus

I used Apache Commons CSV instead of spring-petclinic because it is small enough to inspect by hand, but still has real Java API surface: builders, enums, exceptions, package-private helpers, parser state, and compatibility constraints.

## Layout

- `src/main/kotlin/j2keval` - Kotlin evaluator
- `runner/` - IntelliJ Platform `ApplicationStarter` for static J2K
- `edge-cases/java` - custom Java stress cases
- `fixtures/edge-static-j2k` - committed converted outputs for repeatable tests
- `reports/` - generated Markdown and JSONL outputs
- `SUMMARY.md` - short task summary
- `EDGE_CASE_REPORT.md` - edge-case findings
- `HEADLESS_J2K.md` - notes on the IntelliJ runner boundary

## Run Locally

Use Java 21. On my machine I used IntelliJ's bundled JBR:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.1.1\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat test
```

Run the edge-case evaluator:

```powershell
.\gradlew.bat run --args="--java edge-cases/java --kotlin fixtures/edge-static-j2k --expectations edge-cases/expectations.txt --report reports/EDGE_CASE_RESULTS.md --jsonl reports/edge-case-results.jsonl --isolated-compile"
```

On Linux/macOS:

```bash
./gradlew test
bash scripts/run-edge-eval.sh
```

Fetch the benchmark and run the evaluator after conversion:

```bash
bash scripts/fetch-benchmark.sh
bash scripts/run-static-j2k.sh work/commons-csv/src/main/java build/commons-csv-j2k-real
./gradlew run --args="--java work/commons-csv/src/main/java --kotlin build/commons-csv-j2k-real --report reports/COMMONS_CSV.md --jsonl reports/commons-csv.jsonl"
```

## Static J2K Runner

The static converter lives inside the IntelliJ IDEA Kotlin plugin, not behind a stable standalone CLI. The runner in this repo opens a temporary IntelliJ project, attaches a JDK/source root, waits for smart mode, and calls `NewJavaToKotlinConverter.elementsToKotlin`.

Local command:

```bash
bash scripts/run-static-j2k.sh edge-cases/java build/edge-static-j2k-real
```

CI builds the runner and checks the generated reports. It does not run `runIde` live because IntelliJ sandbox startup can hang in headless Linux. The local command above was the live conversion path.

## Current Results

Edge corpus:

| Metric | Value |
|---|---:|
| Java files | 6 |
| Kotlin files found | 6 |
| Isolated compile passed | 4 / 6 |
| Hypothesis checks passed | 7 / 7 |
| Unsafe markers | 0 |

Apache Commons CSV 1.14.1:

| Metric | Value |
|---|---:|
| Java files | 12 |
| Converted Kotlin files | 12 |
| Unsafe markers | 0 |

The useful failure is `try-with-resources`: J2K preserves `use`, but returns `reader.readLine()` from a function declared as non-null `String`. Kotlin sees `readLine()` as `String?`, so the isolated compile check fails. `ReadLineNullabilityFix` is a small Kotlin post-processing example for that case.

## Reports

- `SUMMARY.md`
- `EDGE_CASE_REPORT.md`
- `HEADLESS_J2K.md`
- `reports/EDGE_CASE_RESULTS.md`
- `reports/EDGE_CASE_LIVE_J2K.md`
- `reports/COMMONS_CSV.md`
- `reports/COMMONS_CSV_SOURCE_SCAN.md`

