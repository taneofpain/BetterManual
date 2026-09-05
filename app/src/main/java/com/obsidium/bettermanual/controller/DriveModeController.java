package com.obsidium.bettermanual.controller;

import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.model.DriveModeModel;
import com.sony.scalar.hardware.CameraEx;

public class DriveModeController extends ImageViewController<DriveModeModel> {

    private static DriveModeController driveModeController = new DriveModeController();

    public static DriveModeController GetInstance()
    {
        return driveModeController;
    }

    @Override
    public void toggle() {
        if (model != null)
            model.toggle();
    }


    @Override
    public int getNavigationHelpID() {
        return R.string.view_drivemode_specific;
    }

    @Override
    protected void updateImage() {
        if (model == null || view == null)
            return;
        final String driveMode = model.getValue();
        if (driveMode == null)
            return;
        if (driveMode.equals(CameraEx.ParametersModifier.DRIVE_MODE_SINGLE))
        {
            //noinspection ResourceType
            view.setImageResource(view.getResources().getInteger(R.integer.p_drivemode_n_001));
        }
        else if (driveMode.equals(CameraEx.ParametersModifier.DRIVE_MODE_BURST))
        {
            final String burstDriveSpeed = model.getBurstDriveSpeed();
            if (burstDriveSpeed.equals(CameraEx.ParametersModifier.BURST_DRIVE_SPEED_LOW))
            {
                //noinspection ResourceType
                view.setImageResource(view.getResources().getInteger(R.integer.p_drivemode_n_003));
            }
            else if (burstDriveSpeed.equals(CameraEx.ParametersModifier.BURST_DRIVE_SPEED_HIGH))
            {
                //noinspection ResourceType
                view.setImageResource(view.getResources().getInteger(R.integer.p_drivemode_n_002));
            }
        }
        else //if (driveMode.equals("bracket"))
        {
            // Don't really care about this here
            //noinspection ResourceType
            view.setImageResource(view.getResources().getInteger(R.integer.p_dialogwarning));
        }
    }
}
