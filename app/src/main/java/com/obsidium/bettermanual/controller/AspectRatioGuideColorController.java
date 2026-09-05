package com.obsidium.bettermanual.controller;

import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.model.AspectRatioGuideColorModel;
import com.obsidium.bettermanual.views.AspectRatioGuideView;

/**
 * Controls the aspect-ratio guide's border color only -- single-purpose,
 * separate from AspectRatioGuideController (the ratio control). See that
 * class's comment for why these two were split back into separate icons
 * after a combined single-icon design kept producing real bugs.
 *
 * Dial adjusts color live; there's nothing to "confirm" here (color isn't
 * logged and doesn't affect the camera, unlike the ratio), so Enter doesn't
 * need to do anything beyond re-showing the current status.
 */
public class AspectRatioGuideColorController extends ImageViewController<AspectRatioGuideColorModel> {

    private static final AspectRatioGuideColorController instance = new AspectRatioGuideColorController();

    public static AspectRatioGuideColorController GetInstance()
    {
        return instance;
    }

    private AspectRatioGuideView guideView;
    private com.obsidium.bettermanual.views.LegacyLensStatusView statusView;

    public void bindGuideView(AspectRatioGuideView v)
    {
        guideView = v;
        updateGuideView();
    }

    public void bindStatusView(com.obsidium.bettermanual.views.LegacyLensStatusView v)
    {
        statusView = v;
    }

    @Override
    public void set_In_De_crase(int i)
    {
        if (model != null)
            model.setValue(i);
        updateGuideView();
        showStatusText();
    }

    @Override
    public void toggle()
    {
        // Nothing to confirm -- color takes effect immediately per tick, and
        // isn't logged or applied to the camera. Just re-shows the status.
        showStatusText();
    }

    @Override
    public void onValueChanged()
    {
        updateGuideView();
    }

    private void updateGuideView()
    {
        if (guideView != null && model != null)
            guideView.setColor(model.getColor());
    }

    private void showStatusText()
    {
        if (statusView == null || model == null)
            return;
        statusView.showStatus("Line:" + model.getValue());
    }

    @Override
    protected void updateImage()
    {
        if (view == null)
            return;
        // Real local project asset, res/drawable/aspect_ratio_guide_color.png
        // -- see AspectRatioGuideController's updateImage() for why this is
        // a plain PNG rather than a vector drawable.
        view.setImageResource(R.drawable.aspect_ratio_guide_color);
    }

    @Override
    public int getNavigationHelpID()
    {
        return R.string.view_aspectratioguidecolor_specific;
    }
}
