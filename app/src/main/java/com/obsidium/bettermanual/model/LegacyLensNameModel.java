package com.obsidium.bettermanual.model;

import com.obsidium.bettermanual.LegacyLensLogger;
import com.obsidium.bettermanual.LegacyLensState;
import com.obsidium.bettermanual.camera.CameraInstance;

/**
 * Cycles through the loaded legacy lens profiles (LegacyLensState). See that
 * class for why lens/special/focal length share one state holder instead of
 * being fully independent.
 */
public class LegacyLensNameModel extends AbstractModel<String> {

    private String lastLogError;

    public LegacyLensNameModel(CameraInstance camera)
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
        LegacyLensState.GET().cycleLens(i);
        fireOnValueChanged();
    }

    /**
     * Called when the lens choice is confirmed (Enter) -- logs the full
     * combined lens/special/focal-length description as one line, the same
     * "confirm, don't log every tick" pattern already used for the
     * aspect-ratio guide's ratio control.
     */
    public void confirmSelection()
    {
        lastLogError = LegacyLensLogger.log(LegacyLensState.GET().getCombinedDescription());
    }

    @Override
    public String getValue()
    {
        return LegacyLensState.GET().getCurrentLensName();
    }

    @Override
    public boolean isSupported()
    {
        return true;
    }
}
