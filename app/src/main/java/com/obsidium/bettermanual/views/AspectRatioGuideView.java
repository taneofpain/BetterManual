package com.obsidium.bettermanual.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.sony.scalar.hardware.avio.DisplayManager;

/**
 * Draws a centered crop-guide rectangle for a target aspect ratio over the
 * live camera preview, dimming the area outside it so what would be kept vs.
 * cropped is unambiguous at a glance. Modeled directly on GridView (same
 * package) -- same VideoRect-based positioning so the guide lines up exactly
 * with the actual displayed video, not just the raw view bounds, which can
 * differ if the preview is letter/pillarboxed.
 *
 * This used to also draw its own transient status text banner, at a
 * different position/font than the legacy-lens controls' banner
 * (LegacyLensStatusView) -- consolidated into that shared view instead, so
 * every control's status text (ratio, line color, lens name, special item,
 * focal length) renders in the same place with the same font. This view now
 * only draws the crop-guide rectangle itself.
 *
 * This can only ever be a compositional aid for most ratios: nothing in this
 * app's camera API lets the sensor actually crop to an arbitrary ratio like
 * 21:9. 3:2 and 16:9 are the exceptions -- see AspectRatioGuideModel.
 */
public class AspectRatioGuideView extends View
{
    private final Paint m_borderPaint = new Paint();
    private final Paint m_dimPaint = new Paint();
    private DisplayManager.VideoRect m_videoRect;
    // width / height of the target guide. 0 (or less) means "off" -- draw
    // nothing, matching AspectRatioGuideModel's index-0 "AR OFF" entry.
    private float m_ratio;

    public AspectRatioGuideView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        m_borderPaint.setAntiAlias(true);
        m_borderPaint.setStyle(Paint.Style.STROKE);
        m_borderPaint.setStrokeWidth(3);
        m_borderPaint.setARGB(255, 255, 255, 255);
        m_dimPaint.setARGB(140, 0, 0, 0);
    }

    public void setVideoRect(DisplayManager.VideoRect videoRect)
    {
        m_videoRect = videoRect;
        invalidate();
    }

    public void setRatio(float ratio)
    {
        m_ratio = ratio;
        invalidate();
    }

    public void setColor(int color)
    {
        m_borderPaint.setColor(color);
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas)
    {
        canvas.drawARGB(0, 0, 0, 0);

        if (m_videoRect == null || m_ratio <= 0f)
            return;

        final float videoLeft = m_videoRect.pxLeft;
        final float videoTop = m_videoRect.pxTop;
        final float videoRight = m_videoRect.pxRight;
        final float videoBottom = m_videoRect.pxBottom;
        final float videoW = videoRight - videoLeft;
        final float videoH = videoBottom - videoTop;
        if (videoW <= 0 || videoH <= 0)
            return;

        // Largest rectangle at m_ratio (width/height) that fits fully inside
        // the actual displayed video, centered -- standard "fit inside, then
        // center" letterbox/pillarbox math.
        final float cropW, cropH;
        if (m_ratio > videoW / videoH)
        {
            // Target is wider than the live video -- keep full width, guide
            // letterboxes top/bottom (e.g. 21:9 or 65:24 guide on a 3:2 body).
            cropW = videoW;
            cropH = videoW / m_ratio;
        }
        else
        {
            // Target is taller/narrower than the live video -- keep full
            // height, guide pillarboxes left/right (e.g. a 4:5 or 1:1 guide).
            cropH = videoH;
            cropW = videoH * m_ratio;
        }
        final float left = videoLeft + (videoW - cropW) / 2f;
        final float top = videoTop + (videoH - cropH) / 2f;
        final float right = left + cropW;
        final float bottom = top + cropH;

        // Dim everything outside the guide rectangle -- a thin border alone
        // is easy to miss at a glance; shading what's cropped out isn't.
        canvas.drawRect(videoLeft, videoTop, videoRight, top, m_dimPaint);       // above
        canvas.drawRect(videoLeft, bottom, videoRight, videoBottom, m_dimPaint); // below
        canvas.drawRect(videoLeft, top, left, bottom, m_dimPaint);              // left
        canvas.drawRect(right, top, videoRight, bottom, m_dimPaint);            // right

        canvas.drawRect(left, top, right, bottom, m_borderPaint);
    }
}
