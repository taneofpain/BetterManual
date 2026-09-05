package com.obsidium.bettermanual.model;

import com.obsidium.bettermanual.camera.CameraInstance;

public class ImageStabilisationModel extends ApertureModel
{
    public ImageStabilisationModel(CameraInstance camera) {
        super(camera);
    }

    @Override
    public void setValue(int i) {
        // Previously set `value` to the state read back BEFORE toggling,
        // then commanded the camera to the OPPOSITE state -- meaning
        // getValue() (which updateImage() uses to pick the on/off icon)
        // always reflected the state from before this press, one step
        // behind whatever the camera was actually just commanded to. The
        // camera command itself was correct the whole time; only the
        // displayed icon was stale, which is exactly what would make this
        // look like it wasn't doing anything.
        final String stabilisationMode = camera.getImageStabilisationMode();
        final String newMode = stabilisationMode.equals("onetime") ? "off" : "onetime";
        camera.setImageStabilisation(newMode);
        value = newMode;
        fireOnValueChanged();
    }

    @Override
    public boolean isSupported() {
        return true;
    }
}
