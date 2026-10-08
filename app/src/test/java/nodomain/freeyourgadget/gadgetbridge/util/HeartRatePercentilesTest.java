package nodomain.freeyourgadget.gadgetbridge.util;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class HeartRatePercentilesTest {
    @Test public void interpolatesSortedObservationsWithoutChangingInput() {
        final java.util.List<Integer> input = Arrays.asList(100, 40, 80, 60);
        final HeartRatePercentiles stats = new HeartRatePercentiles(input, 10, 250);
        assertEquals(4, stats.getCount());
        assertEquals(43, stats.getPercentile(5), 0.00001);
        assertEquals(55, stats.getPercentile(25), 0.00001);
        assertEquals(70, stats.getPercentile(50), 0.00001);
        assertEquals(85, stats.getPercentile(75), 0.00001);
        assertEquals(97, stats.getPercentile(95), 0.00001);
        assertEquals(Arrays.asList(100, 40, 80, 60), input);
    }
    @Test public void filtersSentinelsAndRespectsConfiguredBounds() {
        final HeartRatePercentiles stats = new HeartRatePercentiles(Arrays.asList(null, -1, 0, 9, 50, 180, 251, 255), 50, 180);
        assertEquals(2, stats.getCount());
        assertEquals(115, stats.getPercentile(50), 0.00001);
    }
    @Test public void handlesEmptySingletonAndRepeatedReadings() {
        assertTrue(Double.isNaN(new HeartRatePercentiles(Collections.emptyList(), 10, 250).getPercentile(50)));
        assertEquals(72, new HeartRatePercentiles(Collections.singletonList(72), 10, 250).getPercentile(95), 0);
        assertEquals(72, new HeartRatePercentiles(Arrays.asList(72, 72, 72), 10, 250).getPercentile(5), 0);
    }
    @Test public void rangePercentilesPoolSamplesRatherThanDailyPercentiles() {
        final HeartRatePercentiles stats = new HeartRatePercentiles(Arrays.asList(60, 60, 60, 60, 60, 120), 10, 250);
        assertEquals(60, stats.getPercentile(50), 0);
        assertEquals(105, stats.getPercentile(95), 0.00001);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidPercentile() {
        new HeartRatePercentiles(Collections.singletonList(72), 10, 250).getPercentile(101);
    }
}
