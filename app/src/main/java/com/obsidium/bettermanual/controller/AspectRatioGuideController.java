package com.obsidium.bettermanual.controller;

import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.model.AspectRatioGuideModel;
import com.obsidium.bettermanual.views.AspectRatioGuideView;

/**
 * Controls the aspect-ratio guide's ratio only -- single-purpose, matching
 * how every other control in this app works (dial adjusts one thing, no
 * mode-switching). Line color is a separate control,
 * AspectRatioGuideColorController, on its own icon.
 *
 * This started out combined into one icon with the color control, switching
 * between them via Enter. That went through several redesigns (a
 * back-and-forth toggle, a "which mode am I in" flag reset on reselection,
 * an idle timeout, then coupling color-cycling directly into every Enter
 * press) and each one had a real bug: either the flag got stuck in the wrong
 * state across some navigation path, or -- the last version -- every Enter
 * press to confirm the ratio also silently cycled the color, which wasn't
 * wanted at all. The common thread: trying to fit two independent settings
 * through one control's limited inputs (a dial and a single Enter press) is
 * inherently constrained, and no other control in this app needs to do that.
 * Splitting them back into two single-purpose icons removes the whole
 * problem rather than patching it further.
 *
 * Dial adjusts the ratio live (guide overlay updates every tick, no log
 * entry yet). Enter confirms it -- writes the log entry and applies the
 * real native crop, if the ratio is 3:2/16:9 -- exactly once per confirmed
 * choice, not per tick. See AspectRatioGuideModel.setValue()/
 * confirmSelection() for that split.
 */
public class AspectRatioGuideController extends ImageViewController<AspectRatioGuideModel> {

    private static final AspectRatioGuideController instance = new AspectRatioGuideController();

    public static AspectRatioGuideController GetInstance()
    {
        return instance;
    }

    private AspectRatioGuideView guideView;
    // Status text now goes through this shared view (also used by the
    // legacy-lens controls), not guideView's own drawing -- consolidated so
    // every control's status text renders at the same place, same font. See
    // AspectRatioGuideView's class comment for why.
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
        if (model != null)
            model.confirmSelection();
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
            guideView.setRatio(model.getRatio());
    }

    private void showStatusText()
    {
        if (statusView == null || model == null)
            return;
        String text = "Ratio:" + model.getValue();
        // Surfaces AspectRatioGuideLogger write failures directly on screen --
        // this used to only reach logcat via e.printStackTrace(), not
        // realistic to expect access to when trying to diagnose why the log
        // file wasn't appearing.
        if (model.getLastLogError() != null)
            text += "  LOG FAIL:" + model.getLastLogError();
        statusView.showStatus(text);
    }

    @Override
    protected void updateImage()
    {
        if (view == null)
            return;
        // A real, local project asset (res/drawable/aspect_ratio_guide.png),
        // not a borrowed system icon -- see the file itself for editing.
        // Follows the same pattern LongExposureNoiseReductionController uses
        // for its own real icon (R.drawable.lnr_on/lnr_off): a plain PNG in
        // res/drawable/, safely renderable the same way that one already is,
        // unlike a vector drawable, which risks not rendering correctly on
        // this old/custom Android runtime.
        view.setImageResource(R.drawable.aspect_ratio_guide);
    }

    @Override
    public int getNavigationHelpID()
    {
        return R.string.view_aspectratioguide_specific;
    }
}
