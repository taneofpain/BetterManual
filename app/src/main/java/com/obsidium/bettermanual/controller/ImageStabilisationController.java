package com.obsidium.bettermanual.controller;

import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.model.ImageStabilisationModel;

public class ImageStabilisationController extends ImageViewController<ImageStabilisationModel> {

    private static ImageStabilisationController imageStabilisationController = new ImageStabilisationController();

    public static ImageStabilisationController GetInstance()
    {
        return imageStabilisationController;
    }


    @Override
    public void toggle() {
        model.setValue(1);
    }

    @Override
    public int getNavigationHelpID() {
        return R.string.view_imagestab_specific;
    }

    @Override
    protected void updateImage() {
        if (view == null || model == null)
            return;

        final String stabilisationMode = model.getValue();

        if (stabilisationMode != null && stabilisationMode.equals("onetime"))
            view.setImageResource(view.getResources().getInteger(R.integer.p_16_dd_parts_fn_1_5_layer_sel_steadyshot_on));
        else
            view.setImageResource(view.getResources().getInteger(R.integer.p_16_dd_parts_fn_1_5_layer_sel_steadyshot_off));

    }
}
