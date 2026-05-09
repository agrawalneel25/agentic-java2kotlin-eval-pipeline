# J2K Evaluation Summary

| Metric | Value |
|---|---:|
| Java files | 6 |
| Kotlin files found | 6 |
| Isolated compile passed | 0 / 0 |
| Hypothesis checks passed | 0 / 0 |
| Unsafe markers (`!!`, TODO, NotImplementedError) | 0 |

## Per-file Results

| File | Converted | Compile | Unsafe | Expectations |
|---|:---:|:---:|---:|:---:|
| `anonymous-class/Sample.java` | yes | - | 0 | - |
| `framework-annotations/Sample.java` | yes | - | 0 | - |
| `nested-builders/Sample.java` | yes | - | 0 | - |
| `static-constants/Sample.java` | yes | - | 0 | - |
| `try-with-resources/Sample.java` | yes | - | 0 | - |
| `wildcard-generics/Sample.java` | yes | - | 0 | - |

## Notes

- `--isolated-compile` compiles each file alone. That is useful for edge-case fixtures, but it is not a full module build.
- Module-level compilation remains the next bar for a real repository conversion.
- Report generated from `build\edge-static-j2k-real` against Java source `edge-cases\java`.
