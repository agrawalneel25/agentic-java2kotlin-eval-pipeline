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
    classes = Regex("(?<!enum )\\bclass\\s+\\w+").findAll(text).count(),
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
    wildcardGenerics = Regex("<(out|in)\\s+\\w+>").findAll(text).count(),
    nullChecks = Regex("[?][.:]|\\?:|==\\s*null|!=\\s*null").findAll(text).count(),
)

fun compileKotlinFile(file: Path): CompileStatus {
    val kotlinc = findKotlinc() ?: return CompileStatus.notAttempted
    val output = Files.createTempFile("j2k-eval-", ".jar")
    return try {
        fun runCompile(sourceFile: Path): CompileStatus {
            val process = ProcessBuilder(kotlinc, sourceFile.toString(), "-d", output.toString())
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().readText()
            val passed = process.waitFor() == 0
            return CompileStatus(true, passed, text.take(4000))
        }

        val firstResult = runCompile(file)
        if (!firstResult.passed) {
            val originalContent = file.readText()
            val fixedContent = ReadLineNullabilityFix.apply(originalContent)
            if (fixedContent != originalContent) {
                file.writeText(fixedContent)
                val retryResult = runCompile(file)
                if (retryResult.passed) {
                    return retryResult
                } else {
                    file.writeText(originalContent)
                    return firstResult
                }
            }
        }
        firstResult
    } finally {
        Files.deleteIfExists(output)
    }
}

fun findKotlinc(): String? {
    val fromEnv = System.getenv("KOTLINC")
    if (!fromEnv.isNullOrBlank() && File(fromEnv).exists()) return fromEnv
    val isWindows = System.getProperty("os.name").startsWith("Windows")
    val candidates = if (isWindows) listOf("kotlinc.bat", "kotlinc") else listOf("kotlinc")
    val pathHit = (System.getenv("PATH") ?: "")
        .split(File.pathSeparator)
        .flatMap { dir -> candidates.map { Path.of(dir, it).toFile() } }
        .firstOrNull { it.exists() }
    if (pathHit != null) return pathHit.absolutePath

    error("kotlinc not found on PATH. Please install Kotlin and ensure kotlinc is available.")
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
        appendLine("## Feature Delta")
        appendLine()
        appendLine("| Feature | Java (total) | Kotlin (total) | Delta |")
        appendLine("|---|---:|---:|---:|")
        val totalJava = scores.fold(FeatureCounts(0,0,0,0,0,0,0,0,0)) { acc, s ->
            acc + s.metrics.javaFeatures
        }
        val totalKotlin = scores.fold(FeatureCounts(0,0,0,0,0,0,0,0,0)) { acc, s ->
            acc + s.metrics.kotlinFeatures
        }
        appendLine("| classes | ${totalJava.classes} | ${totalKotlin.classes} | ${totalKotlin.classes - totalJava.classes} |")
        appendLine("| interfaces | ${totalJava.interfaces} | ${totalKotlin.interfaces} | ${totalKotlin.interfaces - totalJava.interfaces} |")
        appendLine("| enums | ${totalJava.enums} | ${totalKotlin.enums} | ${totalKotlin.enums - totalJava.enums} |")
        appendLine("| lambdas | ${totalJava.lambdas} | ${totalKotlin.lambdas} | ${totalKotlin.lambdas - totalJava.lambdas} |")
        appendLine("| null checks | ${totalJava.nullChecks} | ${totalKotlin.nullChecks} | ${totalKotlin.nullChecks - totalJava.nullChecks} |")
        appendLine("| wildcard generics | ${totalJava.wildcardGenerics} | ${totalKotlin.wildcardGenerics} | ${totalKotlin.wildcardGenerics - totalJava.wildcardGenerics} |")
        appendLine("| static members | ${totalJava.staticMembers} | ${totalKotlin.staticMembers} | ${totalKotlin.staticMembers - totalJava.staticMembers} |")
        appendLine("| anonymous classes | ${totalJava.anonymousClasses} | ${totalKotlin.anonymousClasses} | ${totalKotlin.anonymousClasses - totalJava.anonymousClasses} |")
        appendLine("| annotations | ${totalJava.annotations} | ${totalKotlin.annotations} | ${totalKotlin.annotations - totalJava.annotations} |")
        appendLine()
        appendLine("## Notes")
        appendLine()
        appendLine("- `--isolated-compile` compiles each file alone. That is useful for edge-case fixtures, but it is not a full module build.")
        appendLine("- Module-level compilation remains the next bar for a real repository conversion.")
        appendLine("- Report generated from `${args.kotlinDir}` against Java source `${args.javaDir}`.")
    }
}

private operator fun FeatureCounts.plus(other: FeatureCounts) = FeatureCounts(
    classes = this.classes + other.classes,
    interfaces = this.interfaces + other.interfaces,
    enums = this.enums + other.enums,
    annotations = this.annotations + other.annotations,
    staticMembers = this.staticMembers + other.staticMembers,
    anonymousClasses = this.anonymousClasses + other.anonymousClasses,
    lambdas = this.lambdas + other.lambdas,
    wildcardGenerics = this.wildcardGenerics + other.wildcardGenerics,
    nullChecks = this.nullChecks + other.nullChecks,
)

fun renderJsonl(scores: List<FileScore>): String =
    scores.joinToString(separator = "\n", postfix = "\n") { score ->
        val jf = score.metrics.javaFeatures
        val kf = score.metrics.kotlinFeatures
        buildString {
            append("{")
            append("\"file\":\"${score.id.replace("\"", "\\\"")}\",")
            append("\"converted\":${score.converted},")
            append("\"compileAttempted\":${score.compileStatus.attempted},")
            append("\"compilePassed\":${score.compileStatus.passed},")
            append("\"unsafeMarkers\":${score.metrics.unsafeMarkers},")
            append("\"lineCount\":${score.metrics.lineCount},")
            append("\"expectationPassed\":${score.expectations.count { it.passed }},")
            append("\"expectationTotal\":${score.expectations.size},")
            append("\"javaFeatures\":{")
            append("\"classes\":${jf.classes},\"interfaces\":${jf.interfaces},\"enums\":${jf.enums},")
            append("\"lambdas\":${jf.lambdas},\"nullChecks\":${jf.nullChecks},\"wildcardGenerics\":${jf.wildcardGenerics},")
            append("\"staticMembers\":${jf.staticMembers},\"anonymousClasses\":${jf.anonymousClasses},\"annotations\":${jf.annotations}")
            append("},")
            append("\"kotlinFeatures\":{")
            append("\"classes\":${kf.classes},\"interfaces\":${kf.interfaces},\"enums\":${kf.enums},")
            append("\"lambdas\":${kf.lambdas},\"nullChecks\":${kf.nullChecks},\"wildcardGenerics\":${kf.wildcardGenerics},")
            append("\"staticMembers\":${kf.staticMembers},\"anonymousClasses\":${kf.anonymousClasses},\"annotations\":${kf.annotations}")
            append("}")
            append("}")
        }
    }

fun yes(value: Boolean) = if (value) "yes" else "no"
