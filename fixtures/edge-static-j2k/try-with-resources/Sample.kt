package edge.resources

import java.io.BufferedReader
import java.io.IOException
import java.io.Reader

class Sample {
    @Throws(IOException::class)
    fun firstLine(input: Reader): String {
        BufferedReader(input).use { reader ->
            return reader.readLine()
        }
    }
}
