# Commons CSV Source Scan (pre-conversion baseline)

This is the evaluator run against the Java source *before* J2K conversion, with an empty Kotlin output directory. It confirms the evaluator can enumerate the 12 source files and that 0 Kotlin outputs exist at that point. Compare against `COMMONS_CSV.md`, which shows the same run after J2K produced output.

| Metric | Value |
|---|---:|
| Java files | 12 |
| Kotlin files found | 0 |
| Isolated compile passed | 0 / 0 |
| Hypothesis checks passed | 0 / 0 |
| Unsafe markers (`!!`, TODO, NotImplementedError) | 0 |

## Per-file Results

| File | Converted | Compile | Unsafe | Expectations |
|---|:---:|:---:|---:|:---:|
| `org/apache/commons/csv/Constants.java` | no | - | 0 | - |
| `org/apache/commons/csv/CSVException.java` | no | - | 0 | - |
| `org/apache/commons/csv/CSVFormat.java` | no | - | 0 | - |
| `org/apache/commons/csv/CSVParser.java` | no | - | 0 | - |
| `org/apache/commons/csv/CSVPrinter.java` | no | - | 0 | - |
| `org/apache/commons/csv/CSVRecord.java` | no | - | 0 | - |
| `org/apache/commons/csv/DuplicateHeaderMode.java` | no | - | 0 | - |
| `org/apache/commons/csv/ExtendedBufferedReader.java` | no | - | 0 | - |
| `org/apache/commons/csv/Lexer.java` | no | - | 0 | - |
| `org/apache/commons/csv/package-info.java` | no | - | 0 | - |
| `org/apache/commons/csv/QuoteMode.java` | no | - | 0 | - |
| `org/apache/commons/csv/Token.java` | no | - | 0 | - |

## Notes

- `--isolated-compile` compiles each file alone. That is useful for edge-case fixtures, but it is not a full module build.
- Module-level compilation remains the next bar for a real repository conversion.
- Report generated from `work\empty-kotlin` against Java source `work\commons-csv\src\main\java`.
