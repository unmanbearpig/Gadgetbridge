package nodomain.freeyourgadget.gadgetbridge.activities.dashboard;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import org.junit.Test;
import static org.junit.Assert.*;

@org.robolectric.annotation.Config(sdk = 28)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class GaugeDrawerTest extends TestBase {
    @Test public void simpleBarIsLinearAndClampsOverflow() {
        ImageView view = new ImageView(app);
        GaugeDrawer drawer = new GaugeDrawer();
        drawer.drawSimpleGauge(view, Color.CYAN, 0.5f);
        Bitmap bitmap = ((BitmapDrawable) view.getDrawable()).getBitmap();
        assertTrue(bitmap.getHeight() < bitmap.getWidth() / 4);
        assertEquals(Color.CYAN, bitmap.getPixel(bitmap.getWidth() / 4, bitmap.getHeight() / 2));
        assertNotEquals(Color.CYAN, bitmap.getPixel(bitmap.getWidth() * 3 / 4, bitmap.getHeight() / 2));
        drawer.drawSimpleGauge(view, Color.CYAN, 4f);
        bitmap = ((BitmapDrawable) view.getDrawable()).getBitmap();
        assertEquals(Color.CYAN, bitmap.getPixel(bitmap.getWidth() * 3 / 4, bitmap.getHeight() / 2));
    }
    @Test public void segmentedBarUsesStraightAdjacentBands() {
        ImageView view = new ImageView(app);
        new GaugeDrawer().drawSegmentedGauge(view, new int[]{Color.CYAN, Color.GREEN},
                new float[]{0.5f, 0.5f}, -1, false, false);
        Bitmap bitmap = ((BitmapDrawable) view.getDrawable()).getBitmap();
        assertTrue(bitmap.getHeight() < bitmap.getWidth() / 4);
        assertEquals(Color.CYAN, bitmap.getPixel(bitmap.getWidth() / 4, bitmap.getHeight() / 2));
        assertEquals(Color.GREEN, bitmap.getPixel(bitmap.getWidth() * 3 / 4, bitmap.getHeight() / 2));
    }
}
