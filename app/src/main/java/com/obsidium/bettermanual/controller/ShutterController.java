package com.obsidium.bettermanual.controller;

import android.util.Log;
import android.view.View;

import com.obsidium.bettermanual.ActivityInterface;
import com.obsidium.bettermanual.MainActivity;
import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.capture.CaptureModeBulb;
import com.obsidium.bettermanual.model.ExposureModeModel;
import com.obsidium.bettermanual.model.ShutterModel;
import com.sony.scalar.hardware.CameraEx;

public class ShutterController extends TextViewController<ShutterModel> {

    private final String TAG = ShutterController.class.getSimpleName();

    public interface ShutterSpeedEvent
    {
        void onChanged();
    }

    private static ShutterController shutterController = new ShutterController();

    public static ShutterController GetInstance()
    {
        return shutterController;
    }

    private ShutterSpeedEvent shutterSpeedEventListner;
    private ActivityInterface activityInterface;

    public void bindActivityInterface(ActivityInterface activityInterface)
    {
        this.activityInterface = activityInterface;
    }


    @Override
    public void toggle() {
        if (ExposureModeController.GetInstance().getExposureMode() == ExposureModeModel.ExposureModes.aperture && !model.getValue().equals("BULB") && activityInterface != null)
            activityInterface.loadFragment(MainActivity.FRAGMENT_MIN_SHUTTER);
        else if (model.getValue().equals("BULB") && CaptureModeBulb.GetInstance() != null)
            CaptureModeBulb.GetInstance().toggle();

    }

    @Override
    public int getNavigationHelpID() {
        return R.string.view_shutter_specific;
    }

    public CameraEx.ShutterSpeedInfo getShutterSpeedInfo()
    {
        if (model != null)
            return model.getShutterSpeedInfo();
        return null;
    }

    public void setShutterSpeedEventListner(ShutterSpeedEvent eventListner)
    {
        this.shutterSpeedEventListner = eventListner;
    }

    @Override
    public void onValueChanged() {
        Log.d(TAG, "onValueChanged()");
        super.onValueChanged();
        if (shutterSpeedEventListner != null)
            shutterSpeedEventListner.onChanged();
        if (view != null && view.getVisibility() == View.GONE && model != null)
            view.setVisibility(View.VISIBLE);
    }
}
