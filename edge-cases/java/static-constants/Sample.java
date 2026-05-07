package edge.constants;

import java.time.Duration;

public final class Sample {
    public static final String API_VERSION = "v1";
    public static final int DEFAULT_LIMIT = 1000;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private Sample() {
    }
}
