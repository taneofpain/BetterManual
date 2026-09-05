package com.obsidium.bettermanual.model;

import com.obsidium.bettermanual.camera.CameraInstance;
import com.sony.scalar.hardware.CameraEx;

public class DriveModeModel extends AbstractModel<String> {


    public DriveModeModel(CameraInstance camera) {
        super(camera);
    }

    @Override
    public void setValue(int i) {
        toggle();
    }

    @Override
    public String getValue() {
        if (value == null || value.isEmpty())
            value = camera.getDriveMode();
        return value;
    }

    public String getBurstDriveSpeed()
    {
        return camera.getBurstDriveSpeed();
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    public void toggle() {
        // getDriveMode() is a direct pass-through to the native camera API
        // with no null-safety of its own -- calling .equals() on it directly
        // would NPE if it ever returned null. Treated as "single" if so,
        // same fallback this method's own else-branch already uses for any
        // other unrecognized value.
        final String driveMode = camera.getDriveMode();
        final String newBurstSpeed;
        if (driveMode != null && driveMode.equals(CameraEx.ParametersModifier.DRIVE_MODE_SINGLE))
        {
            value = CameraEx.ParametersModifier.DRIVE_MODE_BURST;
            newBurstSpeed = CameraEx.ParametersModifier.BURST_DRIVE_SPEED_HIGH;
        }
        else if (driveMode != null && driveMode.equals(CameraEx.ParametersModifier.DRIVE_MODE_BURST))
        {
            final String burstDriveSpeed = CameraInstance.GET().getBurstDriveSpeed();
            if (burstDriveSpeed != null && burstDriveSpeed.equals(CameraEx.ParametersModifier.BURST_DRIVE_SPEED_LOW))
            {
                value = CameraEx.ParametersModifier.DRIVE_MODE_SINGLE;
                newBurstSpeed = burstDriveSpeed;
            }
            else
            {
                value = driveMode;
                newBurstSpeed = CameraEx.ParametersModifier.BURST_DRIVE_SPEED_LOW;
            }
        }
        else
        {
            // Anything else...
            value = CameraEx.ParametersModifier.DRIVE_MODE_SINGLE;
            newBurstSpeed = CameraEx.ParametersModifier.BURST_DRIVE_SPEED_HIGH;
        }

        CameraInstance.GET().setDriveMode(value);
        CameraInstance.GET().setBurstDriveSpeed(newBurstSpeed);
        fireOnValueChanged();
    }
}
