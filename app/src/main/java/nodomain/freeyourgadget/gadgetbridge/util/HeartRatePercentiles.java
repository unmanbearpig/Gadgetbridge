package nodomain.freeyourgadget.gadgetbridge.util;

import java.util.List;

/** Sample-based percentiles. Missing readings are excluded, never replaced with zero. */
public final class HeartRatePercentiles {
    private final int[] sorted;

    public HeartRatePercentiles(List<Integer> readings, int minimum, int maximum) {
        sorted = readings.stream().filter(value -> value != null && value > 0
                && value >= minimum && value <= maximum).mapToInt(Integer::intValue).sorted().toArray();
    }

    public int getCount() {
        return sorted.length;
    }

    /** Linear interpolation at (n - 1) * percentile / 100, also known as R-7. */
    public double getPercentile(int percentile) {
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("Percentile must be between 0 and 100");
        }
        if (sorted.length == 0) return Double.NaN;
        final double position = (sorted.length - 1) * percentile / 100.0;
        final int lower = (int) Math.floor(position);
        final int upper = (int) Math.ceil(position);
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
    }
}
