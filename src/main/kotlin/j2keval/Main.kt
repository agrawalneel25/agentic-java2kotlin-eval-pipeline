package j2keval

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

data class Args(
    val javaDir: Path,
    val kotlinDir: Path,
    val report: Path,
    val jsonl: Path,
    val expectations: Path?,
    val isolatedCompile: Boolean,
)

data class FileScore(
    val id: String,
    val javaPath: Path,
    val kotlinPath: Path?,
    val converted: Boolean,
    val compileStatus: CompileStatus,
    val metrics: Metrics,
    val expectations: List<ExpectationResult>,
)

data class Metrics(
    val javaFeatures: FeatureCounts,
    val kotlinFeatures: FeatureCounts,
    val lineCount: Int,
    val unsafeMarkers: Int,
)

data class FeatureCounts(
    val classes: Int,
    val interfaces: Int,
    val enums: Int,
    val annotations: Int,
    val staticMembers: Int,
    val anonymousClasses: Int,
    val lambdas: Int,
    val wildcardGenerics: Int,
    val nullChecks: Int,
)

data class CompileStatus(
    val attempted: Boolean,
    val passed: Boolean,
    val diagnostics: String,
) {
    companion object {
        val notAttempted = CompileStatus(false, false, "kotlinc not found or compile disabled")
    }
}

data class Expectation(
    val id: String,
    val file: String,
    val pattern: Regex,
    val description: String,
)

data class ExpectationResult(
    val id: String,
    val passed: Boolean,
    val description: String,
)

fun main(rawArgs: Array<String>) {
    val args = parseArgs(rawArgs.toList())
    val expectations = args.expectations?.let(::readExpectations).orEmpty()
    val scores = scoreCorpus(args, expectations)
    args.report.parent.createDirectories()
    args.jsonl.parent.createDirectories()
    args.report.writeText(renderReport(scores, args))
    args.jsonl.writeText(renderJsonl(scores))

    val failedExpectations = scores.sumOf { score -> score.expectations.count { !it.passed } }
    if (failedExpectations > 0) {
        error("$failedExpectations expectation checks failed")
    }
}

fun parseArgs(args: List<String>): Args {
    fun value(flag: String): String {
        val index = args.indexOf(flag)
        require(index >= 0 && index + 1 < args.size) { "missing $flag" }
        return args[index + 1]
    }

    val report = Path.of(value("--report"))
    val jsonlIndex = args.indexOf("--jsonl")
    val jsonl = if (jsonlIndex >= 0) {
        Path.of(args[jsonlIndex + 1])
    } else {
        report.resolveSibling(report.fileName.toString().removeSuffix(".md") + ".jsonl")
    }

    val expectationsIndex = args.indexOf("--expectations")
    return Args(
        javaDir = Path.of(value("--java")),
        kotlinDir = Path.of(value("--kotlin")),
        report = report,
        jsonl = jsonl,
        expectations = if (expectationsIndex >= 0) Path.of(args[expectationsIndex + 1]) else null,
        isolatedCompile = "--isolated-compile" in args,
    )
}

fun scoreCorpus(args: Args, expectations: List<Expectation>): List<FileScore> {
    val javaFiles = Files.walk(args.javaDir).use { stream ->
        stream.filter { it.isRegularFile() && it.extension == "java" }
            .sorted()
            .toList()
    }

    return javaFiles.map { javaFile ->
        val relative = javaFile.relativeTo(args.javaDir)
        val kotlinFile = args.kotlinDir.resolve(relative.toString().removeSuffix(".java") + ".kt")
        val kotlinText = if (Files.exists(kotlinFile)) kotlinFile.readText() else ""
        val javaText = javaFile.readText()
        val fileExpectations = expectations.filter { it.file == relative.toString().replace(File.separatorChar, '/') }
        FileScore(
            id = relative.toString().replace(File.separatorChar, '/'),
            javaPath = javaFile,
            kotlinPath = kotlinFile.takeIf { Files.exists(it) },
            converted = Files.exists(kotlinFile),
            compileStatus = if (Files.exists(kotlinFile) && args.isolatedCompile) compileKotlinFile(kotlinFile) else CompileStatus.notAttempted,
            metrics = Metrics(
                javaFeatures = countJavaFeatures(javaText),
                kotlinFeatures = countKotlinFeatures(kotlinText),
                lineCount = kotlinText.lineSequence().count { it.isNotBlank() },
                unsafeMarkers = Regex("!!|TODO\\(|NotImplementedError").findAll(kotlinText).count(),
            ),
            expectations = fileExpectations.map { expectation ->
                ExpectationResult(
                    id = expectation.id,
                    passed = expectation.pattern.containsMatchIn(kotlinText),
                    description = expectation.description,
                )
            },
        )
    }
}

fun countJavaFeatures(text: String) = FeatureCounts(
    classes = Regex("\\bclass\\s+\\w+").findAll(text).count(),
    interfaces = Regex("\\binterface\\s+\\w+").findAll(text).count(),
    enums = Regex("\\benum\\s+\\w+").findAll(text).count(),
    annotations = Regex("@\\w+").findAll(text).count(),
    staticMembers = Regex("\\bstatic\\b").findAll(text).count(),
    anonymousClasses = Regex("new\\s+[\\w.<>]+\\s*\\([^)]*\\)\\s*\\{").findAll(text).count(),
    lambdas = Regex("->").findAll(text).count(),
    wildcardGenerics = Regex("[?]\\s+(extends|super)").findAll(text).count(),
    nullChecks = Regex("==\\s*null|!=\\s*null").findAll(text).count(),
)

fun countKotlinFeatures(text: String) = FeatureCounts(
    classes = Regex("\\bclass\\s+\\w+").findAll(text).count(),
    interfaces = Regex("\\binterface\\s+\\w+").findAll(text).count(),
    enums = Regex("\\benum\\s+class\\s+\\w+").findAll(text).count(),
    annotations = Regex("@\\w+").findAll(text).count(),
    staticMembers = Regex("\\bcompanion\\s+object\\b|\\bconst\\s+val\\b|@JvmStatic").findAll(text).count(),
    anonymousClasses = Regex("object\\s*:\\s*[\\w.<>]+").findAll(text).count(),
    lambdas = Regex("->").findAll(text).count(),
    wildcardGenerics = Regex("\\b(out|in)\\s+\\w+").findAll(text).count(),
    nullChecks = Regex("[?][.:]|\\?:|==\\s*null|!=\\s*null").findAll(text).count(),
)

fun compileKotlinFile(file: Path): CompileStatus {
    val kotlinc = findKotlinc() ?: return CompileStatus.notAttempted
    val output = Files.createTempFile("j2k-eval-", ".jar")
    return try {
        val process = ProcessBuilder(kotlinc, file.toString(), "-d", output.toString())
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().readText()
        val passed = process.waitFor() == 0
        CompileStatus(true, passed, text.take(4000))
    } finally {
        Files.deleteIfExists(output)
    }
}

fun findKotlinc(): String? {
    val fromEnv = System.getenv("KOTLINC")
    if (!fromEnv.isNullOrBlank() && File(fromEnv).exists()) return fromEnv
    val pathHit = System.getenv("PATH")
        .split(File.pathSeparator)
        .map { Path.of(it, if (System.getProperty("os.name").startsWith("Windows")) "kotlinc.bat" else "kotlinc").toFile() }
        .firstOrNull { it.exists() }
    if (pathHit != null) return pathHit.absolutePath

    val ideaKotlinc = Path.of(
        "C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.1.1/plugins/Kotlin/kotlinc/bin/kotlinc.bat"
    ).toFile()
    return ideaKotlinc.takeIf { it.exists() }?.absolutePath
}

fun readExpectations(path: Path): List<Expectation> =
    path.readText().lineSequence()
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        .map { line ->
            val parts = line.split('|', limit = 4)
            require(parts.size == 4) { "bad expectation line: $line" }
            Expectation(parts[0], parts[1], Regex(parts[2]), parts[3])
        }
        .toList()

fun renderReport(scores: List<FileScore>, args: Args): String {
    val converted = scores.count { it.converted }
    val compileAttempted = scores.count { it.compileStatus.attempted }
    val compilePassed = scores.count { it.compileStatus.passed }
    val expectationChecks = scores.sumOf { it.expectations.size }
    val expectationPassed = scores.sumOf { score -> score.expectations.count { it.passed } }
    val unsafe = scores.sumOf { it.metrics.unsafeMarkers }

    return buildString {
        appendLine("# J2K Evaluation Summary")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|---|---:|")
        appendLine("| Java files | ${scores.size} |")
        appendLine("| Kotlin files found | $converted |")
        appendLine("| Isolated compile passed | $compilePassed / $compileAttempted |")
        appendLine("| Hypothesis checks passed | $expectationPassed / $expectationChecks |")
        appendLine("| Unsafe markers (`!!`, TODO, NotImplementedError) | $unsafe |")
        appendLine()
        appendLine("## Per-file Results")
        appendLine()
        appendLine("| File | Converted | Compile | Unsafe | Expectations |")
        appendLine("|---|:---:|:---:|---:|:---:|")
        for (score in scores) {
            val expectationCell = if (score.expectations.isEmpty()) {
                "-"
            } else {
                "${score.expectations.count { it.passed }} / ${score.expectations.size}"
            }
            val compileCell = when {
                !score.compileStatus.attempted -> "-"
                score.compileStatus.passed -> "yes"
                else -> "no"
            }
            appendLine("| `${score.id}` | ${yes(score.converted)} | $compileCell | ${score.metrics.unsafeMarkers} | $expectationCell |")
        }
        appendLine()
        appendLine("## Notes")
        appendLine()
        appendLine("- `--isolated-compile` compiles each file alone. That is useful for edge-case fixtures, but it is not a full module build.")
        appendLine("- Module-level compilation remains the next bar for a real repository conversion.")
        appendLine("- Report generated from `${args.kotlinDir}` against Java source `${args.javaDir}`.")
    }
}

fun renderJsonl(scores: List<FileScore>): String =
    scores.joinToString(separator = "\n", postfix = "\n") { score ->
        listOf(
            "file" to score.id,
            "converted" to score.converted.toString(),
            "compileAttempted" to score.compileStatus.attempted.toString(),
            "compilePassed" to score.compileStatus.passed.toString(),
            "unsafeMarkers" to score.metrics.unsafeMarkers.toString(),
            "expectationPassed" to score.expectations.count { it.passed }.toString(),
            "expectationTotal" to score.expectations.size.toString(),
        ).joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"$key\":\"${value.replace("\"", "\\\"")}\""
        }
    }

fun yes(value: Boolean) = if (value) "yes" else "no"
