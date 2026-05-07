package edge.constants

import java.time.Duration

class Sample private constructor() {
    companion object {
        const val API_VERSION: String = "v1"
        const val DEFAULT_LIMIT: Int = 1000
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
