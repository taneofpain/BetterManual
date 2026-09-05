package com.obsidium.bettermanual.model;

import com.obsidium.bettermanual.Preferences;
import com.obsidium.bettermanual.camera.CameraInstance;
import com.sony.scalar.hardware.CameraEx;

public class ExposureModeModel extends AbstractModel<ExposureModeModel.ExposureModes> {


    public ExposureModeModel(CameraInstance camera) {
        super(camera);
        // getSceneMode() is a direct pass-through to the native camera API
        // with no null-safety of its own -- calling .equals() on it directly
        // (three times, previously) would NPE immediately in this
        // constructor if it ever returned null, crashing the app on every
        // camera open before the UI even shows. Captured once into a local
        // with a null guard instead of calling the native getter three times.
        String sceneMode = camera.getSceneMode();
        if (sceneMode == null)
            value = ExposureModes.other;
        else if (sceneMode.equals(CameraEx.ParametersModifier.SCENE_MODE_APERTURE_PRIORITY))
            value = ExposureModes.aperture;
        else if (sceneMode.equals(CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE))
            value = ExposureModes.manual;
        else if (sceneMode.equals(CameraEx.ParametersModifier.SCENE_MODE_SHUTTER_PRIORITY))
            value = ExposureModes.shutter;
        else
            value = ExposureModes.other;
    }

    public enum ExposureModes { manual, aperture, shutter, other }

    @Override
    public void setValue(int i) {
        toggle();
    }

    @Override
    public ExposureModes getValue() {
        return value;
    }

    public String getStringValue()
    {
        switch (value)
        {
            case aperture:
                return CameraEx.ParametersModifier.SCENE_MODE_APERTURE_PRIORITY;
            case manual:
                return CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE;
            case shutter:
                return CameraEx.ParametersModifier.SCENE_MODE_SHUTTER_PRIORITY;
            default:
                return CameraEx.ParametersModifier.SCENE_MODE_APERTURE_PRIORITY;
        }
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    private void toggle()
    {

        final String newMode;
        switch (value)
        {
            case manual:
                newMode = CameraEx.ParametersModifier.SCENE_MODE_APERTURE_PRIORITY;
                value = ExposureModes.aperture;
                /*if (activity.getDialMode() != CameraUiFragment.DialMode.mode)
                    activity.setDialMode(activity.getAperture().haveApertureControl() ? CameraUiFragment.DialMode.aperture : CameraUiFragment.DialMode.iso);*/
                camera.setAutoShutterSpeedLowLimit(Preferences.GET().getMinShutterSpeed());
                break;
            default:
                newMode = CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE;
                value = ExposureModes.manual;
                /*if (activity.getDialMode() != CameraUiFragment.DialMode.mode)
                    activity.setDialMode(CameraUiFragment.DialMode.shutter);*/
                camera.setAutoShutterSpeedLowLimit(-1);
                break;
        }
        camera.setSceneMode(newMode);
        fireOnValueChanged();
    }
}
