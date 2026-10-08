package nodomain.freeyourgadget.gadgetbridge.export

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GB
import java.time.Instant
import java.time.ZoneId

/** Saves the selected device and range across the document picker and configuration changes. */
class HealthDataExport(private val fragment: Fragment) {
    private var pending: Bundle? = fragment.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)

    init {
        fragment.savedStateRegistry.registerSavedStateProvider(STATE_KEY) { pending ?: Bundle() }
    }

    private val picker = fragment.registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val request = pending
        pending = null
        if (uri != null && request != null) {
            val device = BundleCompat.getParcelable(request, "device", GBDevice::class.java)
            if (device != null) fragment.lifecycleScope.launch {
                try {
                    val context = GBApplication.getContext()
                    withContext(Dispatchers.IO) {
                        val samples = GBApplication.acquireDbReadOnly().use { db ->
                            device.deviceCoordinator.getSampleProvider(device, db.daoSession)
                                ?.getAllActivitySamplesHighRes(request.getInt("from"), request.getInt("through"))
                                ?: error("Device does not provide activity samples")
                        }
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: error("Cannot open destination")
                        output.bufferedWriter(Charsets.UTF_8).use { writer ->
                            val zone = ZoneId.of(request.getString("zone"))
                            if (request.getBoolean("summary")) {
                                HealthCsvExporter.writePercentiles(writer, samples, request.getInt("from"),
                                    request.getInt("through"), request.getInt("minimum"), request.getInt("maximum"), zone)
                            } else {
                                HealthCsvExporter.writeSamples(writer, samples, request.getInt("minimum"), request.getInt("maximum"), zone)
                            }
                        }
                    }
                    GB.toast(context, R.string.health_export_success, Toast.LENGTH_LONG, GB.INFO)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    GB.toast(GBApplication.getContext(), fragment.getString(R.string.health_export_failed, e.localizedMessage), Toast.LENGTH_LONG, GB.ERROR, e)
                }
            }
        }
    }

    fun launch(device: GBDevice, from: Int, through: Int, summary: Boolean) {
        val bounds = HeartRateUtils.getInstance()
        val zone = ZoneId.systemDefault()
        pending = Bundle().apply {
            putParcelable("device", device)
            putInt("from", from)
            putInt("through", through)
            putInt("minimum", bounds.minHeartRate)
            putInt("maximum", bounds.maxHeartRate)
            putBoolean("summary", summary)
            putString("zone", zone.id)
        }
        val start = Instant.ofEpochSecond(from.toLong()).atZone(zone).toLocalDate()
        val end = Instant.ofEpochSecond(through.toLong()).atZone(zone).toLocalDate()
        picker.launch("gadgetbridge-${if (summary) "percentiles" else "samples"}-$start-$end.csv")
    }

    private companion object { const val STATE_KEY = "health_csv_export" }
}
