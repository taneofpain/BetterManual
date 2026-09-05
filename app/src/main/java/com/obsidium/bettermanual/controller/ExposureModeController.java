package com.obsidium.bettermanual.controller;

import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.model.ExposureModeModel;

public class ExposureModeController extends ImageViewController<ExposureModeModel> {

    private static ExposureModeController exposureModeController = new ExposureModeController();

    public static ExposureModeController GetInstance()
    {
        return exposureModeController;
    }



    @Override
    public void toggle() {
        model.setValue(1);
    }


    @Override
    public int getNavigationHelpID() {
        return R.string.view_exposuremode_specific;
    }


    @Override
    protected void updateImage()
    {
        if (model == null || view == null)
            return;

        ExposureModeModel.ExposureModes mode = model.getValue();
        //log(String.format("updateImage %s\n", mode));
        if (mode == ExposureModeModel.ExposureModes.manual)
        {
            //noinspection ResourceType
            view.setImageResource(view.getResources().getInteger(R.integer.s_16_dd_parts_osd_icon_mode_m));
        }
        else if (mode == ExposureModeModel.ExposureModes.aperture)
        {
            //noinspection ResourceType
            view.setImageResource(view.getResources().getInteger(R.integer.s_16_dd_parts_osd_icon_mode_a));
        }
        else if (mode == ExposureModeModel.ExposureModes.shutter)
        {
            //noinspection ResourceType
            view.setImageResource(view.getResources().getInteger(R.integer.s_16_dd_parts_osd_icon_mode_s));
        }
        else
        {
            //noinspection ResourceType
            view.setImageResource(view.getResources().getInteger(R.integer.p_dialogwarning));
        }
    }

    public ExposureModeModel.ExposureModes getExposureMode()
    {
        if (model != null)
            return model.getValue();
        else
            return null;
    }
}
