package com.obsidium.bettermanual.controller;

import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.model.LongExposureNoiseReductionModel;

public class LongExposureNoiseReductionController extends ImageViewController<LongExposureNoiseReductionModel> {

    private static LongExposureNoiseReductionController longExposureNoiseReductionController = new LongExposureNoiseReductionController();

    public static LongExposureNoiseReductionController GetIntance() {
        return longExposureNoiseReductionController;
    }

    @Override
    public void toggle() {
        if (model != null)
            model.setValue(1);
    }

    @Override
    public int getNavigationHelpID() {
        return R.string.view_longexponr_specific;
    }

    @Override
    protected void updateImage() {

        if (model == null || view == null)
            return;
        if (model.getValue()) {
            view.setImageResource(R.drawable.lnr_on);
        }
        else
            view.setImageResource(R.drawable.lnr_off);
    }

}
