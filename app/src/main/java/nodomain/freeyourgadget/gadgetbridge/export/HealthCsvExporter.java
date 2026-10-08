package nodomain.freeyourgadget.gadgetbridge.export;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.util.HeartRatePercentiles;

/** UTF-8 CSV with explicit timestamps and blank fields for unmeasured values. */
public final class HealthCsvExporter {
    private HealthCsvExporter() {}

    public static void writeSamples(Writer writer, List<? extends ActivitySample> samples,
                                    int minimumHeartRate, int maximumHeartRate, ZoneId zone) throws IOException {
        writer.write("timestamp_epoch_seconds,timestamp_iso8601,heart_rate_bpm,steps,distance_cm,active_calories,raw_intensity,raw_kind\r\n");
        final List<ActivitySample> ordered = new ArrayList<>(samples);
        ordered.sort(Comparator.comparingInt(ActivitySample::getTimestamp));
        for (ActivitySample sample : ordered) {
            final int hr = sample.getHeartRate();
            writer.write(sample.getTimestamp() + "," + iso(sample.getTimestamp(), zone) + ","
                    + (hr > 0 && hr != 255 && hr >= minimumHeartRate && hr <= maximumHeartRate ? hr : "") + ","
                    + measured(sample.getSteps()) + "," + measured(sample.getDistanceCm()) + ","
                    + measured(sample.getActiveCalories()) + "," + measured(sample.getRawIntensity()) + ","
                    + measured(sample.getRawKind()) + "\r\n");
        }
    }

    public static void writePercentiles(Writer writer, List<? extends ActivitySample> samples,
                                       int from, int through, int minimum, int maximum, ZoneId zone) throws IOException {
        writer.write("period,start_iso8601,end_exclusive_iso8601,valid_samples,p5_bpm,p25_bpm,p50_bpm,p75_bpm,p95_bpm\r\n");
        final List<ActivitySample> ordered = new ArrayList<>(samples);
        ordered.sort(Comparator.comparingInt(ActivitySample::getTimestamp));
        int cursor = 0;
        ZonedDateTime day = Instant.ofEpochSecond(from).atZone(zone).toLocalDate().atStartOfDay(zone);
        while (day.toEpochSecond() <= through) {
            final long start = Math.max(from, day.toEpochSecond());
            final long end = Math.min((long) through + 1, day.plusDays(1).toEpochSecond());
            final List<Integer> readings = new ArrayList<>();
            while (cursor < ordered.size() && ordered.get(cursor).getTimestamp() < start) cursor++;
            while (cursor < ordered.size() && ordered.get(cursor).getTimestamp() < end) {
                readings.add(ordered.get(cursor++).getHeartRate());
            }
            writeSummary(writer, "day", start, end, readings, minimum, maximum, zone);
            day = day.plusDays(1);
        }
        final List<Integer> readings = new ArrayList<>();
        for (ActivitySample sample : ordered) {
            if (sample.getTimestamp() >= from && sample.getTimestamp() <= through) readings.add(sample.getHeartRate());
        }
        writeSummary(writer, "range", from, (long) through + 1, readings, minimum, maximum, zone);
    }

    private static void writeSummary(Writer writer, String period, long start, long end,
                                     List<Integer> readings, int minimum, int maximum, ZoneId zone) throws IOException {
        final HeartRatePercentiles stats = new HeartRatePercentiles(readings, minimum, maximum);
        writer.write(period + "," + iso(start, zone) + "," + iso(end, zone) + "," + stats.getCount());
        for (int percentile : new int[]{5, 25, 50, 75, 95}) {
            writer.write("," + (stats.getCount() == 0 ? "" : String.format(Locale.ROOT, "%.2f", stats.getPercentile(percentile))));
        }
        writer.write("\r\n");
    }

    private static String measured(int value) {
        return value < 0 ? "" : Integer.toString(value);
    }

    private static String iso(long seconds, ZoneId zone) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochSecond(seconds).atZone(zone));
    }
}
