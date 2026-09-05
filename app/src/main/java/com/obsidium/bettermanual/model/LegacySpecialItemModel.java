package com.obsidium.bettermanual.model;

import com.obsidium.bettermanual.LegacyLensLogger;
import com.obsidium.bettermanual.LegacyLensState;
import com.obsidium.bettermanual.camera.CameraInstance;

/**
 * Cycles through the loaded "special" items (teleconverters etc.), e.g.
 * "None", "TC 1.4x", "TC 2x". See LegacyLensState for why this shares state
 * with the lens-name and focal-length controls.
 */
public class LegacySpecialItemModel extends AbstractModel<String> {

    private String lastLogError;

    public LegacySpecialItemModel(CameraInstance camera)
    {
        super(camera);
    }

    public String getLastLogError()
    {
        return lastLogError;
    }

    @Override
    public void setValue(int i)
    {
        LegacyLensState.GET().cycleSpecial(i);
        fireOnValueChanged();
    }

    /**
     * Called when the special-item choice is confirmed (Enter) -- logs the
     * full combined description, same as LegacyLensNameModel.
     * confirmSelection().
     */
    public void confirmSelection()
    {
        lastLogError = LegacyLensLogger.log(LegacyLensState.GET().getCombinedDescription());
    }

    @Override
    public String getValue()
    {
        LegacySpecialProfile special = LegacyLensState.GET().getCurrentSpecial();
        return special != null ? special.name : "None";
    }

    @Override
    public boolean isSupported()
    {
        return true;
    }
}
