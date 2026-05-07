# J2K Evaluation Summary

| Metric | Value |
|---|---:|
| Java files | 6 |
| Kotlin files found | 6 |
| Isolated compile passed | 4 / 6 |
| Hypothesis checks passed | 7 / 7 |
| Unsafe markers (`!!`, TODO, NotImplementedError) | 0 |

## Per-file Results

| File | Converted | Compile | Unsafe | Expectations |
|---|:---:|:---:|---:|:---:|
| `anonymous-class/Sample.java` | yes | yes | 0 | 1 / 1 |
| `framework-annotations/Sample.java` | yes | no | 0 | 1 / 1 |
| `nested-builders/Sample.java` | yes | yes | 0 | 1 / 1 |
| `static-constants/Sample.java` | yes | yes | 0 | 1 / 1 |
| `try-with-resources/Sample.java` | yes | no | 0 | 1 / 1 |
| `wildcard-generics/Sample.java` | yes | yes | 0 | 2 / 2 |

## Notes

- `--isolated-compile` compiles each file alone. That is useful for edge-case fixtures, but it is not a full module build.
- Module-level compilation remains the next bar for a real repository conversion.
- Report generated from `fixtures\edge-static-j2k` against Java source `edge-cases\java`.
