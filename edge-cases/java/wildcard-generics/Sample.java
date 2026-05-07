package edge.generics;

import java.util.List;

public final class Sample {
    public double sum(List<? extends Number> values) {
        double total = 0.0;
        for (Number value : values) {
            total += value.doubleValue();
        }
        return total;
    }

    public void addOne(List<? super Integer> values) {
        values.add(1);
    }
}
