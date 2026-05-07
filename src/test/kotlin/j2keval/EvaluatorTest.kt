package j2keval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvaluatorTest {
    @Test
    fun `counts Java anonymous classes and wildcard generics`() {
        val counts = countJavaFeatures(
            """
            class Sample {
                Runnable r = new Runnable() { public void run() {} };
                void copy(java.util.List<? extends Number> xs) {}
            }
            """.trimIndent()
        )

        assertEquals(1, counts.classes)
        assertEquals(1, counts.anonymousClasses)
        assertEquals(1, counts.wildcardGenerics)
    }

    @Test
    fun `counts Kotlin object expressions and variance`() {
        val counts = countKotlinFeatures(
            """
            class Sample {
                val r = object : Runnable { override fun run() {} }
                fun copy(xs: List<out Number>) {}
            }
            """.trimIndent()
        )

        assertEquals(1, counts.classes)
        assertEquals(1, counts.anonymousClasses)
        assertEquals(1, counts.wildcardGenerics)
    }

    @Test
    fun `expectation parser keeps regex body`() {
        val line = "wildcard|Sample.java|List<out Number>|wildcard should become declaration-site variance"
        val parts = line.split('|', limit = 4)
        assertEquals(4, parts.size)
        assertTrue(Regex(parts[2]).containsMatchIn("fun copy(xs: List<out Number>)"))
    }

    @Test
    fun `readLine nullability fix makes return non-null`() {
        val fixed = ReadLineNullabilityFix.apply(
            """
            fun firstLine(reader: java.io.BufferedReader): String {
                return reader.readLine()
            }
            """.trimIndent()
        )

        assertTrue("return reader.readLine() ?: \"\"" in fixed)
    }
}
