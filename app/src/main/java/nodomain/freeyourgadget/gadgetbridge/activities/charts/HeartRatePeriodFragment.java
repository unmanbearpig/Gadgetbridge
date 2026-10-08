package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_BPM;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.HeartRateSample;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;
import nodomain.freeyourgadget.gadgetbridge.util.HeartRatePercentiles;
import nodomain.freeyourgadget.gadgetbridge.export.HealthDataExport;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;
import nodomain.freeyourgadget.gadgetbridge.util.TimeWeightedAverageAccumulator;

public class HeartRatePeriodFragment extends AbstractChartFragment<HeartRatePeriodFragment.HeartRatePeriodData> {

    protected static final Logger LOG = LoggerFactory.getLogger(HeartRatePeriodFragment.class);

    static int DATA_INVALID = -1;

    protected int HEARTRATE_COLOR;
    protected int HEARTRATE_MIN_COLOR;
    protected int HEARTRATE_RESTING_COLOR;
    protected int HEARTRATE_MAX_COLOR;
    protected int CHART_TEXT_COLOR;
    protected int BACKGROUND_COLOR;
    protected int DESCRIPTION_COLOR;
    protected int LEGEND_TEXT_COLOR;
    protected int TEXT_COLOR;

    private TextView mDateView;
    private LinearLayout hrStatsContainer;
    private LineChart hrLineChart;
    private int TOTAL_DAYS;
    private HealthDataExport csvExport;
    private static final int[] PERCENTILES = {5, 25, 50, 75, 95};
    private int[] percentileColors;

    @Override
    protected boolean isSingleDay() {
        return TOTAL_DAYS == 1;
    }

    public static HeartRatePeriodFragment newInstance(int totalDays) {
        HeartRatePeriodFragment fragmentFirst = new HeartRatePeriodFragment();
        Bundle args = new Bundle();
        args.putInt("totalDays", totalDays);
        fragmentFirst.setArguments(args);
        return fragmentFirst;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        csvExport = new HealthDataExport(this);
        TOTAL_DAYS = getArguments() != null ? getArguments().getInt("totalDays") : 0;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_heart_rate, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> getChartsHost().enableSwipeRefresh(scrollY == 0));

        mDateView = rootView.findViewById(R.id.hr_date_view);
        hrLineChart = rootView.findViewById(R.id.heart_rate_line_chart);
        hrStatsContainer = rootView.findViewById(R.id.hr_stats_container);

        rootView.findViewById(R.id.hr_export_samples).setOnClickListener(v -> exportCsv(false));
        rootView.findViewById(R.id.hr_export_percentiles).setOnClickListener(v -> exportCsv(true));
        setupChart();
        refresh();
        setupLegend(hrLineChart);

        return rootView;
    }

    private void exportCsv(boolean summary) {
        final Pair<Integer, Integer> range = getStartAndEndTS();
        csvExport.launch(getChartsHost().getDevice(), range.getKey(), range.getValue(), summary);
    }

    private HeartRatePercentiles percentiles(List<? extends ActivitySample> samples) {
        final List<Integer> readings = new ArrayList<>(samples.size());
        for (ActivitySample sample : samples) readings.add(sample.getHeartRate());
        final HeartRateUtils utils = HeartRateUtils.getInstance();
        return new HeartRatePercentiles(readings, utils.getMinHeartRate(), utils.getMaxHeartRate());
    }

    public boolean supportsHeartRateRestingMeasurement() {
        final GBDevice device = getChartsHost().getDevice();
        return device.getDeviceCoordinator().supportsHeartRateRestingMeasurement(device);
    }

    protected List<? extends AbstractActivitySample> getActivitySamples(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        SampleProvider<? extends ActivitySample> provider = device.getDeviceCoordinator().getSampleProvider(device, db.getDaoSession());
        return provider.getAllActivitySamplesHighRes(tsFrom, tsTo);
    }

    @Override
    public String getTitle() {
        return getString(R.string.heart_rate);
    }

    @Override
    protected void init() {
        Prefs prefs = GBApplication.getPrefs();
        CHART_TEXT_COLOR = GBApplication.getSecondaryTextColor(requireContext());
        DESCRIPTION_COLOR = LEGEND_TEXT_COLOR = TEXT_COLOR = GBApplication.getTextColor(requireContext());
        if (prefs.getBoolean("chart_heartrate_color", false)) {
            HEARTRATE_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_heartrate_alternative);
        } else {
            HEARTRATE_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_heartrate);
        }
        percentileColors = new int[]{
                ContextCompat.getColor(requireContext(), R.color.hr_percentile_5),
                ContextCompat.getColor(requireContext(), R.color.hr_percentile_25),
                ContextCompat.getColor(requireContext(), R.color.hr_percentile_50),
                ContextCompat.getColor(requireContext(), R.color.hr_percentile_75),
                ContextCompat.getColor(requireContext(), R.color.hr_percentile_95)
        };
        // Keep percentile lines readable when the user chooses the light theme.
        if (!GBApplication.isDarkThemeEnabled()) percentileColors = new int[]{
                Color.rgb(25, 75, 95), Color.rgb(30, 105, 130), Color.rgb(0, 110, 148),
                Color.rgb(55, 125, 145), Color.rgb(60, 70, 80)};
        HEARTRATE_MIN_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_heartrate_minimum);
        HEARTRATE_MAX_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_heartrate_maximum);
        HEARTRATE_RESTING_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_heartrate_resting);
    }

    private HeartRateData fetchHeartRateDataForDay(DBHandler db, GBDevice device, int startTs) {
        int endTs = toTimestamp(DateTimeUtils.shiftByDays(new Date(startTs * 1000L), 1)) - 1;
        List<? extends ActivitySample> samples = getActivitySamples(db, device, startTs, endTs);
        final HeartRateUtils heartRateUtilsInstance = HeartRateUtils.getInstance();

        int restingHeartRate = DATA_INVALID;
        if (supportsHeartRateRestingMeasurement()) {
            final var provider = device.getDeviceCoordinator()
                    .getHeartRateRestingSampleProvider(device, db.getDaoSession());
            if (provider != null) {
                restingHeartRate = provider.getAllSamples(startTs * 1000L, endTs * 1000L).stream()
                        .max(Comparator.comparingLong(HeartRateSample::getTimestamp))
                        .map(HeartRateSample::getHeartRate)
                        .orElse(DATA_INVALID);
            }
        }

        final int maxHRGapMinutes = device.getDeviceCoordinator().getMaxHeartRateMeasurementsGapMinutes(device);
        final TimeWeightedAverageAccumulator accumulator = new TimeWeightedAverageAccumulator(60 * maxHRGapMinutes, 60);
        for (int i = 0; i < samples.size(); i++) {
            final ActivitySample sample = samples.get(i);
            if (heartRateUtilsInstance.isValidHeartRateValue(sample.getHeartRate())) {
                accumulator.add(sample.getTimestamp(), sample.getHeartRate());
            }
        }

        final int average = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getAverage()) : DATA_INVALID;
        final int minimum = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getMin()) : DATA_INVALID;
        final int maximum = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getMax()) : DATA_INVALID;

        return new HeartRateData(samples, restingHeartRate, average, minimum, maximum);
    }

    @Override
    protected HeartRatePeriodData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        Pair<Integer, Integer> startAndEndTs = getStartAndEndTS();
        final int startTs = startAndEndTs.getKey();

        List<HeartRateData> result = new ArrayList<>();
        for (int i = 0; i < TOTAL_DAYS; i++) {
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(startTs * 1000L);
            day.add(Calendar.DATE, i);
            HeartRateData dayData = fetchHeartRateDataForDay(db, device, toTimestamp(day.getTime()));
            result.add(dayData);
        }
        return new HeartRatePeriodData(result);
    }

    @Override
    protected void renderCharts() {
        hrLineChart.invalidate();
    }

    private void setupChart() {
        hrLineChart.setBackgroundColor(BACKGROUND_COLOR);
        hrLineChart.getDescription().setTextColor(DESCRIPTION_COLOR);
        hrLineChart.getDescription().setEnabled(false);
        hrLineChart.setNoDataText(getString(R.string.no_data));
        hrLineChart.setNoDataTextColor(TEXT_COLOR);

        XAxis x = hrLineChart.getXAxis();
        x.setDrawLabelsEnabled(true);
        x.setDrawGridLinesEnabled(false);
        x.setEnabled(true);
        x.setTextColor(CHART_TEXT_COLOR);
        x.setDrawLimitLinesBehindDataEnabled(true);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);

        YAxis yAxisLeft = hrLineChart.getAxisLeft();
        yAxisLeft.setEnabled(true);
        YAxis yAxisRight = hrLineChart.getAxisRight();
        yAxisRight.setDrawLabelsEnabled(true);

        YAxis[] yAxisArr = {yAxisLeft, yAxisRight};
        for (YAxis y : yAxisArr) {
            y.setAxisMaximum(HeartRateUtils.getInstance().getMaxHeartRate());
            y.setAxisMinimum(HeartRateUtils.getInstance().getMinHeartRate());
            y.setDrawGridLinesEnabled(false);
            y.setDrawTopYLabelEntryEnabled(true);
            y.setTextColor(CHART_TEXT_COLOR);
        }

        refresh();
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
        List<LegendEntry> legendEntries = new ArrayList<>();
        if (TOTAL_DAYS == 1) {
            LegendEntry raw = new LegendEntry();
            raw.setLabel(getTitle());
            raw.setFormColor(HEARTRATE_COLOR);
            legendEntries.add(raw);
        }
        for (int i = 0; i < PERCENTILES.length; i++) {
            LegendEntry entry = new LegendEntry();
            entry.setLabel(getString(R.string.hr_percentile_label, PERCENTILES[i]));
            entry.setFormColor(percentileColors[i]);
            legendEntries.add(entry);
        }

        chart.getLegend().setEntries(legendEntries);
        chart.getLegend().setTextColor(LEGEND_TEXT_COLOR);
        chart.getLegend().setWordWrapEnabled(true);
    }

    protected LineDataSet createHeartRateDataSet(final List<Entry> values, int color) {
        LineDataSet dataSet = new LineDataSet(values, "Heart Rate");
        dataSet.setLineWidth(1.5f);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setCubicIntensity(0.1f);
        dataSet.setDrawCirclesEnabled(false);
        dataSet.setDrawValuesEnabled(false);
        dataSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        dataSet.setColor(color);
        dataSet.setValueTextColor(TEXT_COLOR);
        dataSet.setValueTextSize(10f);
        return dataSet;
    }

    private Pair<Integer, Integer> getStartAndEndTS() {
        Date lastDay = DateTimeUtils.dayStart(getEndDate());
        int startTs = toTimestamp(DateTimeUtils.shiftByDays(lastDay, -(TOTAL_DAYS - 1)));
        int endTs = toTimestamp(DateTimeUtils.shiftByDays(lastDay, 1)) - 1;
        return Pair.of(startTs, endTs);
    }

    private void setStatistics(int average, int minimum, int maximum, int resting, HeartRatePercentiles percentiles) {
        hrStatsContainer.removeAllViews();

        final WorkoutValueFormatter workoutValueFormatter = new WorkoutValueFormatter();
        final List<StatTileData> stats = new ArrayList<>();

        for (int percentile : PERCENTILES) {
            stats.add(new StatTileData(percentiles.getCount() == 0 ? getString(R.string.stats_empty_value)
                    : workoutValueFormatter.formatValue(percentiles.getPercentile(percentile), UNIT_BPM),
                    getString(R.string.hr_percentile_label, percentile)));
        }
        stats.add(new StatTileData(java.text.NumberFormat.getIntegerInstance().format(percentiles.getCount()),
                getString(R.string.hr_valid_samples)));

        stats.add(new StatTileData(
                minimum > 0 ? workoutValueFormatter.formatValue(minimum, UNIT_BPM) : getString(R.string.stats_empty_value),
                getString(R.string.hr_minimum)
        ));

        stats.add(new StatTileData(
                maximum > 0 ? workoutValueFormatter.formatValue(maximum, UNIT_BPM) : getString(R.string.stats_empty_value),
                getString(R.string.hr_maximum)
        ));

        stats.add(new StatTileData(
                average > 0 ? workoutValueFormatter.formatValue(average, UNIT_BPM) : getString(R.string.stats_empty_value),
                getString(R.string.hr_average)
        ));

        if (supportsHeartRateRestingMeasurement()) {
            stats.add(new StatTileData(
                    resting > 0 ? workoutValueFormatter.formatValue(resting, UNIT_BPM) : getString(R.string.stats_empty_value),
                    getString(R.string.hr_resting)
            ));
        }

        StatTileGridUtilKt.addStatTileGrid(hrStatsContainer, requireContext(), stats, 0);

        hrLineChart.getAxisLeft().setAxisMinimum(HeartRateUtils.getInstance().getMinHeartRate());
        hrLineChart.getAxisRight().setAxisMinimum(HeartRateUtils.getInstance().getMinHeartRate());
        hrLineChart.getAxisLeft().setAxisMaximum(HeartRateUtils.getInstance().getMaxHeartRate());
        hrLineChart.getAxisRight().setAxisMaximum(HeartRateUtils.getInstance().getMaxHeartRate());
        if (minimum > 0) {
            hrLineChart.getAxisLeft().setAxisMinimum(Math.max(minimum - 30, 0));
            hrLineChart.getAxisRight().setAxisMinimum(Math.max(minimum - 30, 0));
        }
        if (maximum > 0) {
            hrLineChart.getAxisLeft().setAxisMaximum(maximum + 30);
            hrLineChart.getAxisRight().setAxisMaximum(maximum + 30);
        }
    }

    @Override
    protected void updateChartsnUIThread(HeartRatePeriodData data) {
        Pair<Integer, Integer> startAndEndTs = getStartAndEndTS();
        final int startTs = startAndEndTs.getKey();
        final int endTs = startAndEndTs.getValue();

        //Date date = new Date((long) endTs * 1000);
        mDateView.setText(DateTimeUtils.formatDaysUntil(TOTAL_DAYS, getTSEnd()));
        final XAxis x = hrLineChart.getXAxis();
        if (TOTAL_DAYS == 1) {
            setOneDayData(data.samples.get(0), startTs, endTs);
            x.setAxisMinimum(0f);
            x.setAxisMaximum(endTs - startTs);
        } else {
            setMultipleDaysData(data, startTs, endTs);
            x.setAxisMinimum(0);
            // If the timestamp is used as XAxis, the chart library formats
            // the labels not at 0:00, which causes a shift in the labels
            x.setAxisMaximum(TOTAL_DAYS - 1);
        }
    }

    private void setOneDayData(HeartRateData data, int startTs, int endTs) {
        Date date = new Date((long) endTs * 1000);
        String formattedDate = new SimpleDateFormat("E, MMM dd").format(date);
        mDateView.setText(formattedDate);

        HeartRateUtils heartRateUtilsInstance = HeartRateUtils.getInstance();
        final GBDevice device = getChartsHost().getDevice();
        final int maxHRGapMinutes = device.getDeviceCoordinator().getMaxHeartRateMeasurementsGapMinutes(device);
        final List<Entry> lineEntries = new ArrayList<>();
        List<? extends ActivitySample> samples = data.samples;
        final TimestampTranslation tsTranslation = new TimestampTranslation();
        tsTranslation.shorten(startTs);

        final List<ILineDataSet<?>> lineDataSets = new ArrayList<>();
        int lastTs = 0;
        for (int i = 0; i < samples.size(); i++) {
            final ActivitySample sample = samples.get(i);
            if (!heartRateUtilsInstance.isValidHeartRateValue(sample.getHeartRate())) {
                continue;
            }
            final int ts = sample.getTimestamp();
            final int shortTs = tsTranslation.shorten(ts);
            if (lastTs == 0 || (ts - lastTs) <= 60 * maxHRGapMinutes) {
                lineEntries.add(new Entry<>(shortTs, sample.getHeartRate(), null, null));
            } else {
                if (!lineEntries.isEmpty()) {
                    List<Entry> clone = new ArrayList<>(lineEntries.size());
                    clone.addAll(lineEntries);
                    lineDataSets.add(createHeartRateDataSet(clone, HEARTRATE_COLOR));
                    lineEntries.clear();
                }
                lineEntries.add(new Entry<>(shortTs, sample.getHeartRate(), null, null));
            }
            lastTs = ts;
        }
        hrLineChart.getXAxis().setValueFormatter(new SampleXLabelFormatter(tsTranslation, "HH:mm"));
        if (!lineEntries.isEmpty()) {
            lineDataSets.add(createHeartRateDataSet(lineEntries, HEARTRATE_COLOR));
        }

        setStatistics(data.average, data.minimum, data.maximum, data.restingHeartRate, percentiles(data.samples));

        hrLineChart.setData(new LineData(lineDataSets));
        hrLineChart.getAxisLeft().removeAllLimitLines();

        hrLineChart.getAxisRight().removeAllLimitLines();
        final HeartRatePercentiles stats = percentiles(data.samples);
        if (stats.getCount() > 0) {
            for (int i = 0; i < PERCENTILES.length; i++) {
                final LimitLine line = new LimitLine((float) stats.getPercentile(PERCENTILES[i]),
                        getString(R.string.hr_percentile_label, PERCENTILES[i]));
                line.setLineWidth(1f);
                line.enableDashedLine(8f, 6f, 0f);
                line.setLineColor(percentileColors[i]);
                line.setTextColor(TEXT_COLOR);
                hrLineChart.getAxisRight().addLimitLine(line);
            }
        }
    }

    private void setMultipleDaysData(HeartRatePeriodData data, int startTs, int endTs) {
        List<HeartRateData> samples = data.samples;
        final Accumulator avgAccumulator = new Accumulator();
        final Accumulator minAccumulator = new Accumulator();
        final Accumulator maxAccumulator = new Accumulator();
        final Accumulator restingAccumulator = new Accumulator();


        for (int i = 0; i < samples.size(); i++) {
            final HeartRateData hrData = samples.get(i);
            if (hrData.average > 0) {
                avgAccumulator.add(hrData.average);
            }
            if (hrData.minimum > 0) {
                minAccumulator.add(hrData.minimum);
            }
            if (hrData.maximum > 0) {
                maxAccumulator.add(hrData.maximum);
            }
            if (hrData.restingHeartRate > 0) {
                restingAccumulator.add(hrData.restingHeartRate);
            }
        }

        final String fmt = TOTAL_DAYS == 7 ? "EEE" : "dd";
        SimpleDateFormat formatDay = new SimpleDateFormat(fmt, Locale.getDefault());
        IAxisValueFormatter formatter = (value, axis) -> {
            final Calendar day = Calendar.getInstance();
            day.setTimeInMillis(startTs * 1000L);
            day.add(Calendar.DATE, (int) value);
            return formatDay.format(day.getTime());
        };
        hrLineChart.getXAxis().setValueFormatter(formatter);

        final int average = avgAccumulator.getCount() > 0 ? (int) Math.round(avgAccumulator.getAverage()) : DATA_INVALID;
        final int minimum = minAccumulator.getCount() > 0 ? (int) Math.round(minAccumulator.getMin()) : DATA_INVALID;
        final int maximum = maxAccumulator.getCount() > 0 ? (int) Math.round(maxAccumulator.getMax()) : DATA_INVALID;
        final int restingAvg = restingAccumulator.getCount() > 0 ? (int) Math.round(restingAccumulator.getAverage()) : DATA_INVALID;
        final List<ActivitySample> allSamples = new ArrayList<>();
        for (HeartRateData day : samples) allSamples.addAll(day.samples);
        setStatistics(average, minimum, maximum, restingAvg, percentiles(allSamples));

        List<ILineDataSet<?>> dataSets = new ArrayList<>();
        hrLineChart.getAxisLeft().removeAllLimitLines();
        hrLineChart.getAxisRight().removeAllLimitLines();
        final List<HeartRatePercentiles> daily = new ArrayList<>();
        for (HeartRateData day : samples) daily.add(percentiles(day.samples));
        for (int p = 0; p < PERCENTILES.length; p++) {
            List<Entry> entries = new ArrayList<>();
            for (int day = 0; day <= daily.size(); day++) {
                if (day == daily.size() || daily.get(day).getCount() == 0) {
                    if (!entries.isEmpty()) {
                        final LineDataSet set = createHeartRateDataSet(entries, percentileColors[p]);
                        set.setDrawCirclesEnabled(true);
                        set.setCircleColor(percentileColors[p]);
                        set.setCircleRadius(2f);
                        dataSets.add(set);
                        entries = new ArrayList<>();
                    }
                } else {
                    entries.add(new Entry<>(day, (float) daily.get(day).getPercentile(PERCENTILES[p]), null, null));
                }
            }
        }

        hrLineChart.setData(new LineData(dataSets));
    }

    protected static class HeartRatePeriodData extends ChartsData {
        public List<HeartRateData> samples;

        protected HeartRatePeriodData(List<HeartRateData> samples) {
            this.samples = samples;
        }
    }

    protected static class HeartRateData extends ChartsData {
        public List<? extends ActivitySample> samples;
        public int restingHeartRate;
        public int average;
        public int minimum;
        public int maximum;

        protected HeartRateData(List<? extends ActivitySample> samples, int restingHeartRate, int average, int minimum, int maximum) {
            this.samples = samples;
            this.restingHeartRate = restingHeartRate;
            this.average = average;
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.union(
                ChartDataRange.ofActivitySamples(device.getDeviceCoordinator().getSampleProvider(device, db.getDaoSession())),
                ChartDataRange.ofSamples(device.getDeviceCoordinator().getHeartRateRestingSampleProvider(device, db.getDaoSession()))
        );
    }
}
