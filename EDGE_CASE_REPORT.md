# Edge-case Report

The edge-case corpus is designed to test converter behavior that usually gets hidden in larger repositories.

| Case | Hypothesis | Result |
|---|---|---|
| anonymous-class | Anonymous `Comparator` should survive as an object expression. | Passed. Output contains `object : Comparator<String>`. |
| wildcard-generics | `? extends Number` should become `out Number`. | Passed. |
| wildcard-generics | `? super Integer` should become `in Int`. | Passed. |
| static-constants | primitive/String `static final` fields should become `const val`. | Passed on committed fixture. |
| try-with-resources | `try (...)` should become `use`. | Passed shape check, failed isolated compile because `readLine()` is nullable. |
| framework-annotations | annotations and values should survive conversion. | Passed shape check, failed isolated compile due annotation/module context. |
| nested-builders | recursive self-type builder should preserve bound. | Passed. |

## Failure Detail: `readLine()`

Java:

```java
public String firstLine(Reader input) throws IOException {
    try (BufferedReader reader = new BufferedReader(input)) {
        return reader.readLine();
    }
}
```

Converted Kotlin shape:

```kotlin
BufferedReader(input).use { reader ->
    return reader.readLine()
}
```

The problem is that `BufferedReader.readLine()` returns `String?`, while the method returns `String`. That is a real semantic mismatch, not formatting noise.

## Proposed Kotlin Post-processor

`ReadLineNullabilityFix` rewrites direct `return reader.readLine()` into:

```kotlin
return reader.readLine() ?: ""
```

This is deliberately narrow. A production fixer would need method-contract context before choosing the fallback value.
