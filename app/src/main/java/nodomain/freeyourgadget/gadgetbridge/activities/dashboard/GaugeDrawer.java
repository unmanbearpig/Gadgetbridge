package nodomain.freeyourgadget.gadgetbridge.activities.dashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.TypedValue;
import android.widget.ImageView;

import androidx.annotation.ColorInt;

import com.google.android.material.color.MaterialColors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;

public class GaugeDrawer {
    private static final Logger LOG = LoggerFactory.getLogger(GaugeDrawer.class);
    protected @ColorInt int color_unknown = Color.argb(25, 128, 128, 128);

    /**
     * Draw a simple gauge.
     *
     * @param color     the gauge color
     * @param value     the gauge value. Range: [0, 1]
     */
    public void drawSimpleGauge(ImageView gaugeBar, final int color,
                                   final float value) {

        final int width = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                150,
                GBApplication.getContext().getResources().getDisplayMetrics()
        );

        // Draw gauge
        gaugeBar.setImageBitmap(drawSimpleGaugeInternal(
                width,
                Math.round(width * 0.075f),
                color,
                value,
                MaterialColors.getColor(gaugeBar.getContext(), R.attr.gauge_track, Color.rgb(70, 80, 94))
        ));
    }

    /**
     * @param width        Bitmap width in pixels
     * @param barWidth     Gauge bar width in pixels
     * @param filledColor  Color of the filled part of the gauge
     * @param filledFactor Factor between 0 and 1 that determines the amount of the gauge that should be filled
     * @return Bitmap containing the gauge
     */
    private Bitmap drawSimpleGaugeInternal(final int width, final int barWidth,
                                           @ColorInt final int filledColor, final float filledFactor,
                                           @ColorInt final int trackColor) {
        final Bitmap bitmap = Bitmap.createBitmap(width, barWidth * 2, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(trackColor);
        final float top = barWidth / 2f;
        final float bottom = top + barWidth;
        canvas.drawRoundRect(0, top, width, bottom, barWidth / 4f, barWidth / 4f, paint);
        if (Float.isFinite(filledFactor) && filledFactor > 0) {
            paint.setColor(filledColor);
            canvas.drawRoundRect(0, top, width * Math.min(1, filledFactor), bottom,
                    barWidth / 4f, barWidth / 4f, paint);
        }
        return bitmap;
    }

    /**
     * Draws a segmented gauge.
     *
     * @param colors             the colors of each segment
     * @param segments           the size of each segment. The sum of all segments should be 1
     * @param value              the gauge value, in range [0, 1], or -1 for no value and only segments
     * @param fadeOutsideDot     whether to fade out colors outside the dot value
     * @param gapBetweenSegments whether to introduce a small gap between the segments
     */
    public void drawSegmentedGauge(ImageView gaugeBar,
                                      final int[] colors,
                                      final float[] segments,
                                      final float value,
                                      final boolean fadeOutsideDot,
                                      final boolean gapBetweenSegments) {
        if (colors.length != segments.length) {
            LOG.error("Colors length {} differs from segments length {}", colors.length, segments.length);
            return;
        }

        final int width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 150,
                gaugeBar.getResources().getDisplayMetrics());
        final int barWidth = Math.max(2, Math.round(width * 0.06f));
        final int height = barWidth * 2;
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float top = barWidth / 2f;
        paint.setColor(MaterialColors.getColor(gaugeBar.getContext(), R.attr.gauge_track, Color.rgb(70, 80, 94)));
        canvas.drawRect(0, top, width, top + barWidth, paint);
        float position = 0;
        final float marker = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : -1;
        for (int i = 0; i < segments.length; i++) {
            if (!Float.isFinite(segments[i]) || segments[i] <= 0) continue;
            final float end = Math.min(1, position + segments[i]);
            paint.setColor(colors[i]);
            if (fadeOutsideDot && value >= 0 && (marker < position || marker > end)) paint.setAlpha(90);
            final float gap = gapBetweenSegments ? width * 0.008f : 0;
            if (end * width > position * width + gap) {
                canvas.drawRect(position * width, top, end * width - gap, top + barWidth, paint);
            }
            paint.setAlpha(255);
            position = end;
        }
        if (Float.isFinite(value) && value >= 0) {
            final float x = Math.max(2, Math.min(width - 2, marker * width));
            paint.setColor(GBApplication.getTextColor(gaugeBar.getContext()));
            paint.setStrokeWidth(Math.max(2, width * 0.012f));
            canvas.drawLine(x, 0, x, height, paint);
        }
        gaugeBar.setImageBitmap(bitmap);
    }

    public static Bitmap drawCircleGaugeSegmented(int width,
                                                  int barWidth,
                                                  final int[] colors,
                                                  final float[] segments,
                                                  final boolean gapBetweenSegments,
                                                  String text,
                                                  String lowerText,
                                                  Context context) {
        int TEXT_COLOR = GBApplication.getTextColor(context);
        int SUBTEXT_COLOR = GBApplication.getSecondaryTextColor(context);
        int height = width;
        int barMargin = (int) Math.ceil(barWidth / 2f);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeWidth(barWidth);
        paint.setColor(MaterialColors.getColor(context, R.attr.gauge_track, context.getResources().getColor(R.color.gauge_line_color)));
        canvas.drawArc(
                barMargin,
                barMargin,
                width - barMargin,
                width - barMargin,
                90,
                360,
                false,
                paint);
        paint.setStrokeWidth(barWidth);

        float remainingAngle = 360;
        float gapDegree = 1f;
        if (gapBetweenSegments) {
            int validSegments = segments.length;
            for (int i = 0; i < segments.length; i++) {
                if (segments[i] == 0) {
                    validSegments--;
                }
            }

            remainingAngle = 360 - (validSegments * gapDegree);
        }

        float angleSum = 0;
        for (int i = 0; i < segments.length; i++) {
            if (segments[i] == 0) {
                continue;
            }

            paint.setColor(colors[i]);
            paint.setStrokeWidth(barWidth);

            float startAngleDegrees = 270 + (angleSum * remainingAngle);
            float sweepAngleDegrees = segments[i] * remainingAngle;

            canvas.drawArc(
                    barMargin,
                    barMargin,
                    width - barMargin,
                    height - barMargin,
                    startAngleDegrees,
                    sweepAngleDegrees,
                    false,
                    paint
            );
            angleSum += segments[i];
            if (gapBetweenSegments) {
                angleSum += (gapDegree / 360f);
            }
        }

        Paint textPaint = new Paint();
        textPaint.setColor(TEXT_COLOR);
        float textPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, width * 0.06f, context.getResources().getDisplayMetrics());
        textPaint.setTextSize(textPixels);
        textPaint.setTextAlign(Paint.Align.CENTER);
        int yPos = (int) ((float) height / 2 - ((textPaint.descent() + textPaint.ascent()) / 2)) ;
        canvas.drawText(String.valueOf(text), width / 2f, yPos, textPaint);
        Paint textLowerPaint = new Paint();
        textLowerPaint.setColor(SUBTEXT_COLOR);
        textLowerPaint.setTextAlign(Paint.Align.CENTER);
        float textLowerPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, width * 0.025f, context.getResources().getDisplayMetrics());
        textLowerPaint.setTextSize(textLowerPixels);
        int yPosLowerText = (int) ((float) height / 2 - textPaint.ascent()) ;
        canvas.drawText(String.valueOf(lowerText), width / 2f, yPosLowerText, textLowerPaint);

        return bitmap;
    }

    public static Bitmap drawCircleGauge(int width,
                                         int barWidth,
                                         @ColorInt int filledColor,
                                         int value,
                                         int maxValue,
                                         Context context) {
        int TEXT_COLOR = GBApplication.getTextColor(context);
        int SUB_TEXT_COLOR = GBApplication.getSecondaryTextColor(context);
        int height = width;
        int barMargin = (int) Math.ceil(barWidth / 2f);
        float filledFactor = (float) value / maxValue;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(barWidth);
        paint.setColor(MaterialColors.getColor(context, R.attr.gauge_track, context.getResources().getColor(R.color.gauge_line_color)));
        canvas.drawArc(
                barMargin,
                barMargin,
                width - barMargin,
                width - barMargin,
                90,
                360,
                false,
                paint);
        paint.setStrokeWidth(barWidth);
        paint.setColor(filledColor);
        canvas.drawArc(
                barMargin,
                barMargin,
                width - barMargin,
                height - barMargin,
                270,
                360 * filledFactor,
                false,
                paint
        );

        Paint textPaint = new Paint();
        textPaint.setColor(TEXT_COLOR);
        float textPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, width * 0.06f, context.getResources().getDisplayMetrics());
        textPaint.setTextSize(textPixels);
        textPaint.setTextAlign(Paint.Align.CENTER);
        int yPos = (int) ((float) height / 2 - ((textPaint.descent() + textPaint.ascent()) / 2)) ;
        canvas.drawText(NumberFormat.getInstance().format(value), width / 2f, yPos, textPaint);
        Paint textLowerPaint = new Paint();
        textLowerPaint.setColor(SUB_TEXT_COLOR);
        textLowerPaint.setTextAlign(Paint.Align.CENTER);
        float textLowerPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, width * 0.025f, context.getResources().getDisplayMetrics());
        textLowerPaint.setTextSize(textLowerPixels);
        int yPosLowerText = (int) ((float) height / 2 - textPaint.ascent()) ;
        canvas.drawText(NumberFormat.getInstance().format(maxValue), width / 2f, yPosLowerText, textLowerPaint);

        return bitmap;
    }

    public static double normalize(final double value, final double min, final double max) {
        return normalize(value, min, max, 0, 1);
    }

    public static double normalize(final double value, final double minSource, final double maxSource, final double minTarget, final double maxTarget) {
        return ((value - minSource) * (maxTarget - minTarget)) / (maxSource - minSource) + minTarget;
    }

}
