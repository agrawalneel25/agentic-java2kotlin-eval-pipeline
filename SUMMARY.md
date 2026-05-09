# Summary

This repo contains a Kotlin evaluation harness for the Agentic Java2Kotlin Eval Pipeline task.

## Pipeline

1. Fetch a real-world Java project, Apache Commons CSV 1.14.1.
2. Run static J2K through the IntelliJ Platform runner module in `runner/`, or through `J2K_RUNNER_CMD` when configured.
3. Evaluate the produced Kotlin with the Kotlin evaluator in `src/main/kotlin/j2keval`.
4. Write Markdown and JSONL reports.
5. Run the same evaluator on a custom edge-case corpus with explicit hypotheses.

## Real-world Benchmark

I chose Apache Commons CSV instead of spring-petclinic. It is a small real library rather than a demo app, and its codebase still has enough shape to test J2K behavior: builders, enums, exceptions, package-private helpers, parser state, and public API constraints.

The static J2K runner converts all 12 Java files in `src/main/java`. The Kotlin evaluator then matches those outputs against the Java source tree and writes `reports/COMMONS_CSV.md`.

| Metric | Value |
|---|---:|
| Java files | 12 |
| Converted Kotlin files | 12 |
| Unsafe markers | 0 |

## Edge-case Corpus

The custom corpus tests:

- anonymous classes with captured variables
- wildcard generics
- static constants
- try-with-resources
- framework-like annotations
- recursive generic builders

Current edge-case result:

| Metric | Value |
|---|---:|
| Java files | 6 |
| Kotlin files found | 6 |
| Isolated compile passed | 4 / 6 |
| Hypothesis checks passed | 7 / 7 |
| Unsafe markers | 0 |

## Finding

The most useful failure is the `try-with-resources` case. The converted Kotlin preserves `use`, but returns `reader.readLine()` from a method declared to return non-null `String`. Kotlin correctly treats `readLine()` as nullable. I added `ReadLineNullabilityFix` as a targeted post-processing example.

## Limitation

The Kotlin evaluator and static J2K runner run in CI. The Apache Commons CSV analysis is structural: it checks generated file coverage and simple Kotlin risk markers. Module-level compilation with the original project dependencies remains the next bar.
