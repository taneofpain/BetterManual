package com.obsidium.bettermanual.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

import com.sony.scalar.hardware.avio.DisplayManager;

/**
 * Draws status text for both the legacy-lens controls (name, special item,
 * focal length) and the aspect-ratio guide controls (ratio, line color) --
 * one shared view, one shared position, one shared font, rather than each
 * feature drawing its own separately (which is what AspectRatioGuideView
 * originally did for its own transient banner, at a different position and
 * text size -- consolidated here instead).
 *
 * Two independent text layers:
 * - showStatus(): transient, auto-hides after a few seconds -- used by all
 *   five controls (ratio, color, lens name, special, focal length) while
 *   actively adjusting.
 * - showPermanentStatus(): does not auto-hide, stays until explicitly
 *   changed again -- used for the "what legacy lens is currently selected"
 *   display, which should stay visible after you've confirmed a choice and
 *   moved on, not just flash briefly. Drawn on its own line so the two
 *   never overlap each other.
 */
public class LegacyLensStatusView extends View
{
    private final Paint m_textPaint = new Paint();
    private final Paint m_outlinePaint = new Paint();
    private DisplayManager.VideoRect m_videoRect;

    private String m_statusText = "";
    private final Handler m_handler = new Handler();
    private final Runnable m_clearStatusText = () -> { m_statusText = ""; invalidate(); };
    private static final long STATUS_TEXT_TIMEOUT_MS = 2500;

    private String m_permanentText = "";

    public LegacyLensStatusView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        // Matches @dimen/textSize (15dp), the same size tvHint uses for
        // things like "Enter to change Exposure Mode" -- converted properly
        // via applyDimension() rather than guessing a raw pixel value, since
        // Paint.setTextSize() takes raw pixels, not dp, unlike XML
        // android:textSize. tvHint also references
        // ?android:attr/textAppearanceLarge, a system theme attribute this
        // can't fully inspect/replicate -- bold is the most common thing
        // that adds on top of size, so matching that here is the best
        // available attempt; some residual difference between a real
        // TextView and Canvas.drawText() may remain regardless (different
        // rendering paths -- not something a size/weight match alone can
        // fully eliminate).
        float textSizePx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 10, context.getResources().getDisplayMetrics());

        m_textPaint.setAntiAlias(true);
        m_textPaint.setColor(0xFFFFFFFF);
        m_textPaint.setTextSize(textSizePx);
        m_textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        m_textPaint.setStyle(Paint.Style.FILL);

        m_outlinePaint.setAntiAlias(true);
        m_outlinePaint.setColor(0xFF000000);
        m_outlinePaint.setTextSize(textSizePx);
        m_outlinePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        m_outlinePaint.setStyle(Paint.Style.STROKE);
        m_outlinePaint.setStrokeWidth(4);
    }

    public void setVideoRect(DisplayManager.VideoRect videoRect)
    {
        m_videoRect = videoRect;
        invalidate();
    }

    public void showStatus(String text)
    {
        m_statusText = text;
        invalidate();
        m_handler.removeCallbacks(m_clearStatusText);
        m_handler.postDelayed(m_clearStatusText, STATUS_TEXT_TIMEOUT_MS);
    }

    /**
     * Sets the permanent (non-fading) line -- pass an empty string to clear
     * it (e.g. when "None" legacy lens with no native lens detected either).
     */
    public void showPermanentStatus(String text)
    {
        m_permanentText = text != null ? text : "";
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas)
    {
        canvas.drawARGB(0, 0, 0, 0);

        if (m_videoRect == null)
            return;

        // Top-left of the video area. Left margin is 16px base + roughly
        // six characters' width at this text size in total (two, then a
        // further four added on top per feedback).
        final float x = m_videoRect.pxLeft + 16 + 24 + 48;
        final float lineHeight = m_textPaint.getTextSize() + 6;
        // First line's baseline needs to clear the top edge by a full line
        // height, not sit right at pxTop (which would clip the top of the
        // glyphs).
        float y = m_videoRect.pxTop + lineHeight;

        // Permanent line drawn first, one line above the transient banner's
        // position, so the two never overlap even if both are showing at
        // once.
        if (m_permanentText != null && !m_permanentText.isEmpty())
        {
            canvas.drawText(m_permanentText, x, y, m_outlinePaint);
            canvas.drawText(m_permanentText, x, y, m_textPaint);
        }

        if (m_statusText == null || m_statusText.isEmpty())
            return;

        // drawText() doesn't wrap or honor "\n" on its own -- split and draw
        // each line separately.
        String[] lines = m_statusText.split("\n");
        for (int i = 0; i < lines.length; i++)
        {
            float lineY = y + lineHeight * (i + 1);
            canvas.drawText(lines[i], x, lineY, m_outlinePaint);
            canvas.drawText(lines[i], x, lineY, m_textPaint);
        }
    }
}
