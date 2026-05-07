package edge.anonymous;

import java.util.Comparator;

public final class Sample {
    public Comparator<String> bySuffix(String suffix) {
        return new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return (left + suffix).compareTo(right + suffix);
            }
        };
    }
}
