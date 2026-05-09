# J2K Evaluation Summary

| Metric | Value |
|---|---:|
| Java files | 12 |
| Kotlin files found | 12 |
| Isolated compile passed | 0 / 0 |
| Hypothesis checks passed | 0 / 0 |
| Unsafe markers (`!!`, TODO, NotImplementedError) | 0 |

## Per-file Results

| File | Converted | Compile | Unsafe | Expectations |
|---|:---:|:---:|---:|:---:|
| `org/apache/commons/csv/Constants.java` | yes | - | 0 | - |
| `org/apache/commons/csv/CSVException.java` | yes | - | 0 | - |
| `org/apache/commons/csv/CSVFormat.java` | yes | - | 0 | - |
| `org/apache/commons/csv/CSVParser.java` | yes | - | 0 | - |
| `org/apache/commons/csv/CSVPrinter.java` | yes | - | 0 | - |
| `org/apache/commons/csv/CSVRecord.java` | yes | - | 0 | - |
| `org/apache/commons/csv/DuplicateHeaderMode.java` | yes | - | 0 | - |
| `org/apache/commons/csv/ExtendedBufferedReader.java` | yes | - | 0 | - |
| `org/apache/commons/csv/Lexer.java` | yes | - | 0 | - |
| `org/apache/commons/csv/package-info.java` | yes | - | 0 | - |
| `org/apache/commons/csv/QuoteMode.java` | yes | - | 0 | - |
| `org/apache/commons/csv/Token.java` | yes | - | 0 | - |

## Notes

- `--isolated-compile` compiles each file alone. That is useful for edge-case fixtures, but it is not a full module build.
- Module-level compilation remains the next bar for a real repository conversion.
- Report generated from `build\commons-csv-j2k-real` against Java source `work\commons-csv\src\main\java`.
