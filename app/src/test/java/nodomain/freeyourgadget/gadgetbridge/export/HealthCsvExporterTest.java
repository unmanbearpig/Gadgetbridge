package nodomain.freeyourgadget.gadgetbridge.export;

import org.junit.Test;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HealthCsvExporterTest {
    private ActivitySample sample(int timestamp, int heartRate, int steps) {
        ActivitySample sample = mock(ActivitySample.class);
        when(sample.getTimestamp()).thenReturn(timestamp);
        when(sample.getHeartRate()).thenReturn(heartRate);
        when(sample.getSteps()).thenReturn(steps);
        when(sample.getDistanceCm()).thenReturn(-1);
        when(sample.getActiveCalories()).thenReturn(-1);
        when(sample.getRawIntensity()).thenReturn(-1);
        when(sample.getRawKind()).thenReturn(-1);
        return sample;
    }

    @Test public void exportsSortedSamplesWithOffsetAndBlankMissingValues() throws Exception {
        StringWriter writer = new StringWriter();
        HealthCsvExporter.writeSamples(writer, Arrays.asList(sample(120, 255, -1), sample(60, 72, 0)),
                10, 250, ZoneId.of("America/Santiago"));
        String[] rows = writer.toString().split("\r\n");
        assertEquals("60,1969-12-31T21:01:00-03:00,72,0,,,,", rows[1]);
        assertEquals("120,1969-12-31T21:02:00-03:00,,,,,,", rows[2]);
    }

    @Test public void dailyRowsRespectDstAndRangeRowPoolsReadings() throws Exception {
        ZoneId zone = ZoneId.of("America/New_York");
        int from = (int) LocalDate.of(2026, 3, 8).atStartOfDay(zone).toEpochSecond();
        int next = (int) LocalDate.of(2026, 3, 9).atStartOfDay(zone).toEpochSecond();
        assertEquals(23 * 3600, next - from);
        StringWriter writer = new StringWriter();
        HealthCsvExporter.writePercentiles(writer,
                Arrays.asList(sample(from - 1, 220, 0), sample(from, 60, 0), sample(next - 1, 100, 0), sample(next, 200, 0)),
                from, next - 1, 10, 250, zone);
        String[] rows = writer.toString().split("\r\n");
        assertEquals(3, rows.length);
        assertEquals("day,2026-03-08T00:00:00-05:00,2026-03-09T00:00:00-04:00,2,62.00,70.00,80.00,90.00,98.00", rows[1]);
        assertTrue(rows[2].endsWith(",2,62.00,70.00,80.00,90.00,98.00"));
    }

    @Test public void emptyDaysHaveBlankPercentilesAndLocaleIndependentDecimals() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            StringWriter writer = new StringWriter();
            HealthCsvExporter.writePercentiles(writer, Collections.emptyList(), 0, 86399, 10, 250, ZoneId.of("UTC"));
            assertTrue(writer.toString().contains(",0,,,,,\r\n"));
            writer = new StringWriter();
            HealthCsvExporter.writePercentiles(writer, Arrays.asList(sample(0, 60, 0), sample(60, 61, 0)),
                    0, 86399, 10, 250, ZoneId.of("UTC"));
            assertTrue(writer.toString().contains(",2,60.05,60.25,60.50,60.75,60.95\r\n"));
        } finally { Locale.setDefault(previous); }
    }
}
