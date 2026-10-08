package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Date;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HeartRatePeriodFragmentTest extends TestBase {
    @Test public void loadsActivityHeartRateWhenOptionalRestingProviderIsMissing() throws Exception {
        ChartsHost host = mock(ChartsHost.class);
        GBDevice device = mock(GBDevice.class);
        DeviceCoordinator coordinator = mock(DeviceCoordinator.class);
        SampleProvider provider = mock(SampleProvider.class);
        when(host.getDevice()).thenReturn(device);
        when(host.getEndDate()).thenReturn(new Date(1791460800000L));
        when(device.getDeviceCoordinator()).thenReturn(coordinator);
        when(coordinator.supportsHeartRateRestingMeasurement(device)).thenReturn(true);
        when(coordinator.getSampleProvider(device, daoSession)).thenReturn(provider);
        when(provider.getAllActivitySamplesHighRes(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        HeartRatePeriodFragment fragment = new HeartRatePeriodFragment() {
            @Override protected ChartsHost getChartsHost() { return host; }
        };
        Field days = HeartRatePeriodFragment.class.getDeclaredField("TOTAL_DAYS");
        days.setAccessible(true);
        days.setInt(fragment, 1);
        HeartRatePeriodFragment.HeartRatePeriodData data = fragment.refreshInBackground(host, dbHandler, device);
        assertEquals(1, data.samples.size());
        assertTrue(data.samples.get(0).samples.isEmpty());
        assertEquals(-1, data.samples.get(0).restingHeartRate);
    }
}
