package j2keval

object ReadLineNullabilityFix {
    private val directReturn = Regex("return\\s+([A-Za-z_][A-Za-z0-9_]*)\\.readLine\\(\\)")

    fun apply(kotlinText: String): String =
        directReturn.replace(kotlinText) { match ->
            val receiver = match.groupValues[1]
            "return $receiver.readLine() ?: \"\""
        }
}
