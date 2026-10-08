package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.util.HeartRatePercentiles
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetActions
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** Full-width charts of the selected day. Each device keeps its own heart rate series. */
abstract class HistoryChartWidget(private val heartRate: Boolean) : GBWidget<HistoryChartWidget.Data> {
    override val defaultColumns = 2
    override val allowedColumns = listOf(2, 4)

    override fun isSupportedBy(device: GBDevice): Boolean = if (heartRate)
        device.deviceCoordinator.supportsHeartRateMeasurement(device)
    else device.deviceCoordinator.supportsStepCounter(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val series = scope.devices.map { device ->
            val samples = scope.db { db ->
                device.deviceCoordinator.getSampleProvider(device, db.daoSession)
                    ?.getAllActivitySamplesHighRes(scope.query.timeFrom, scope.query.timeTo)
                    ?.sortedBy { it.timestamp } ?: emptyList()
            }
            Series(device.aliasOrName, samples,
                device.deviceCoordinator.getMaxHeartRateMeasurementsGapMinutes(device) * 60)
        }
        return Data(scope.query.timeFrom, scope.query.timeTo, series)
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup): View {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            addView(TextView(context).apply {
                text = context.getString(label)
                textSize = 18f
                setTextColor(GBApplication.getTextColor(context))
            })
            addView(TextView(context).apply {
                textSize = 14f
                setTextColor(GBApplication.getSecondaryTextColor(context))
            })
            val chart = if (heartRate) LineChart(context) else BarChart(context)
            chart.description.isEnabled = false
            chart.isTouchEnabled = false
            chart.noDataText = context.getString(R.string.no_data)
            chart.noDataTextColor = GBApplication.getSecondaryTextColor(context)
            chart.axisRight.isEnabled = false
            chart.axisLeft.apply {
                textColor = GBApplication.getSecondaryTextColor(context)
                isDrawGridLinesEnabled = false
                isDrawAxisLineEnabled = false
            }
            chart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = GBApplication.getSecondaryTextColor(context)
                isDrawGridLinesEnabled = false
                isDrawAxisLineEnabled = false
                labelCount = 5
            }
            chart.legend.textColor = GBApplication.getSecondaryTextColor(context)
            chart.legend.isWordWrapEnabled = true
            addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (160 * density).toInt()))
        }
    }

    override fun bind(view: View, config: WidgetConfig, data: Data) {
        val layout = view as LinearLayout
        (layout.getChildAt(0) as TextView).text = config.title(view.context, this)
        val summary = layout.getChildAt(1) as TextView
        val chart = layout.getChildAt(2) as BarLineChartBase<*>
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        val hours = (data.through.toLong() + 1 - data.from) / 3600f
        chart.xAxis.axisMinimum = 0f
        chart.xAxis.axisMaximum = hours
        chart.xAxis.valueFormatter = com.github.mikephil.charting.formatter.IAxisValueFormatter { value, _ -> format.format(Date(data.from.toLong() * 1000L + (value * 3_600_000).toLong())) }
        val accent = if (GBApplication.isDarkThemeEnabled()) Color.rgb(139, 220, 251) else Color.rgb(0, 110, 148)
        val colors = intArrayOf(accent, Color.rgb(137, 232, 186), Color.rgb(255, 141, 171), Color.rgb(195, 169, 255))
        chart.legend.isEnabled = data.series.size > 1
        if (heartRate) {
            val utils = HeartRateUtils.getInstance()
            val sets = mutableListOf<ILineDataSet<*>>()
            val readings = mutableListOf<Int>()
            data.series.forEachIndexed { index, series ->
                var entries = mutableListOf<Entry<Any>>()
                var previous: Int? = null
                fun flush() {
                    if (entries.isEmpty()) return
                    sets.add(LineDataSet(entries, series.name).apply {
                        color = colors[index % colors.size]
                        circleColors = listOf(color)
                        circleRadius = 2f
                        isDrawCirclesEnabled = entries.size == 1
                        isDrawValuesEnabled = false
                        lineWidth = 1.5f
                    })
                    entries = mutableListOf()
                }
                for (sample in series.samples) {
                    if (!utils.isValidHeartRateValue(sample.heartRate)) continue
                    if (previous != null && sample.timestamp - previous > series.maximumGapSeconds) flush()
                    readings.add(sample.heartRate)
                    entries.add(Entry((sample.timestamp - data.from) / 3600f, sample.heartRate.toFloat(), null, null))
                    previous = sample.timestamp
                }
                flush()
            }
            val stats = HeartRatePercentiles(readings, utils.minHeartRate, utils.maxHeartRate)
            summary.text = if (stats.count == 0) view.context.getString(R.string.stats_empty_value)
                else view.context.getString(R.string.dashboard_hr_percentiles,
                    NumberFormat.getNumberInstance().format(stats.getPercentile(50)),
                    NumberFormat.getNumberInstance().format(stats.getPercentile(95)))
            chart.axisLeft.axisMinimum = (readings.minOrNull()?.minus(10) ?: utils.minHeartRate).coerceAtLeast(0).toFloat()
            chart.axisLeft.axisMaximum = (readings.maxOrNull()?.plus(10) ?: utils.maxHeartRate).toFloat()
            if (sets.isEmpty()) (chart as LineChart).clear() else (chart as LineChart).data = LineData(sets)
        } else {
            val buckets = Array(ceil(hours).toInt()) { FloatArray(data.series.size) }
            var measured = false
            data.series.forEachIndexed { index, series ->
                for (sample in series.samples) {
                    val hour = (sample.timestamp - data.from) / 3600
                    if (hour in buckets.indices && sample.steps >= 0) {
                        buckets[hour][index] += sample.steps
                        measured = true
                    }
                }
            }
            summary.text = if (!measured) view.context.getString(R.string.stats_empty_value)
                else NumberFormat.getIntegerInstance().format(buckets.sumOf { it.sum().toDouble() })
            chart.axisLeft.axisMinimum = 0f
            if (!measured) (chart as BarChart).clear() else {
                val entries = buckets.mapIndexed { hour, counts -> BarEntry(hour + 0.5f, counts.toList()) }
                val set = BarDataSet(entries, "").apply {
                    setColors(*IntArray(data.series.size) { colors[it % colors.size] })
                    stackLabels = data.series.map { it.name }
                    isDrawValuesEnabled = false
                }
                (chart as BarChart).data = BarData(set).apply { barWidth = 0.8f }
            }
        }
        view.contentDescription = "${config.title(view.context, this)}, ${summary.text}"
        chart.invalidate()
    }

    override fun onClick(view: View, config: WidgetConfig, timestamp: Int) {
        WidgetActions.openChart(view.context, config, this, if (heartRate) "heartrate" else "stepsweek", label, "", timestamp)
    }

    data class Series(val name: String, val samples: List<ActivitySample>, val maximumGapSeconds: Int)
    data class Data(val from: Int, val through: Int, val series: List<Series>)
}

object HeartRateHistoryWidget : HistoryChartWidget(true) {
    override val id = "hr_history"
    override val label = R.string.dashboard_hr_history
    override val icon = R.drawable.ic_heartrate
}

object ActivityHistoryWidget : HistoryChartWidget(false) {
    override val id = "activity_history"
    override val label = R.string.dashboard_activity_history
    override val icon = R.drawable.ic_steps
}
