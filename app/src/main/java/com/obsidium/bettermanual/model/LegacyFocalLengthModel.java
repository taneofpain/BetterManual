package com.obsidium.bettermanual.model;

import com.obsidium.bettermanual.LegacyLensLogger;
import com.obsidium.bettermanual.LegacyLensState;
import com.obsidium.bettermanual.camera.CameraInstance;

/**
 * Adjusts the current focal length -- but only actually does anything if
 * the currently selected lens is a zoom (has a min-max range). For a fixed
 * lens there's nothing to adjust, matching how the original LegacyLenses
 * app only makes this control interactive ("green") for zoom lenses; the
 * dial turning this into a no-op for a fixed lens is the equivalent here,
 * communicated via the status text rather than an icon color change.
 */
public class LegacyFocalLengthModel extends AbstractModel<String> {

    private String lastLogError;

    public LegacyFocalLengthModel(CameraInstance camera)
    {
        super(camera);
    }

    public String getLastLogError()
    {
        return lastLogError;
    }

    /**
     * Returns true if the dial turn actually changed anything (zoom lens,
     * value moved) -- false for a fixed lens or a value already at its
     * range limit, so the controller can show an explanatory status instead
     * of silently doing nothing.
     */
    public boolean adjust(int i)
    {
        boolean changed = LegacyLensState.GET().adjustFocalLength(i);
        if (changed)
            fireOnValueChanged();
        return changed;
    }

    @Override
    public void setValue(int i)
    {
        adjust(i);
    }

    /**
     * Called when the focal length is confirmed (Enter) -- logs the full
     * combined description, same as the other two legacy-lens models.
     */
    public void confirmSelection()
    {
        lastLogError = LegacyLensLogger.log(LegacyLensState.GET().getCombinedDescription());
    }

    public boolean isCurrentLensZoom()
    {
        LegacyLensProfile lens = LegacyLensState.GET().getCurrentLens();
        return lens != null && lens.isZoom();
    }

    @Override
    public String getValue()
    {
        LegacyLensProfile lens = LegacyLensState.GET().getCurrentLens();
        if (lens == null || lens.isNone)
            return "--";
        return lens.isZoom() ? (LegacyLensState.GET().getFocalLength() + "mm") : lens.getFocalRangeLabel();
    }

    @Override
    public boolean isSupported()
    {
        return true;
    }
}
