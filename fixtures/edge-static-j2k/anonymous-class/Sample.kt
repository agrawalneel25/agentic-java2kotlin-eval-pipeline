package edge.anonymous

import java.util.Comparator

class Sample {
    fun bySuffix(suffix: String): Comparator<String> {
        return object : Comparator<String> {
            override fun compare(left: String, right: String): Int {
                return (left + suffix).compareTo(right + suffix)
            }
        }
    }
}
