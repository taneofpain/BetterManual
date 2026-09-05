package com.obsidium.bettermanual.controller;

import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.model.ExposureCompensationModel;

public class ExposureCompensationController extends TextViewController<ExposureCompensationModel> {
    private static ExposureCompensationController exposureCompensationController = new ExposureCompensationController();

    public static ExposureCompensationController GetInstance()
    {
        return exposureCompensationController;
    }

    @Override
    public void toggle() {

    }

    @Override
    public int getNavigationHelpID() {
        return R.string.view_ev;
    }
}
