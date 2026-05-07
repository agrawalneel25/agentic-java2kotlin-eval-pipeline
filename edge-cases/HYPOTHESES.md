# Edge-case Hypotheses

I used small Java files where the desired Kotlin shape is easy to inspect. The goal is not to make a large benchmark. The goal is to make failures local enough that a reviewer can tell whether the metric is fair.

| ID | Java pattern | Hypothesis |
|---|---|---|
| anonymous-class | Anonymous `Comparator` with captured variable | J2K should produce an `object : Comparator<T>` or lambda without losing the captured variable. |
| wildcard-generics | `List<? extends Number>` and `List<? super Integer>` | J2K should map these to `out Number` and `in Int` rather than star projections. |
| static-constants | `public static final` constants | J2K should use `const val` for primitive/String constants and a companion object for non-const values. |
| try-with-resources | `try (BufferedReader reader = ...)` | J2K should convert to `use` and preserve close semantics. |
| framework-annotations | Repeated Spring-like annotations and default values | J2K should preserve annotations and named arguments. |
| nested-builders | Generic self-type builder | J2K should not erase the self type or turn fluent calls into unsafe casts. |
