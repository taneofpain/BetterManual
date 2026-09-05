package com.obsidium.bettermanual.controller;

import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.model.LegacyFocalLengthModel;
import com.obsidium.bettermanual.views.LegacyLensStatusView;

/**
 * Single-purpose control for adjusting focal length. Only does anything if
 * the currently selected lens is a zoom -- see LegacyFocalLengthModel for
 * why, and see LegacyLensNameController's class comment for the overall
 * three-control design.
 */
public class LegacyFocalLengthController extends ImageViewController<LegacyFocalLengthModel> {

    private static final LegacyFocalLengthController instance = new LegacyFocalLengthController();

    public static LegacyFocalLengthController GetInstance()
    {
        return instance;
    }

    private LegacyLensStatusView statusView;

    public void bindStatusView(LegacyLensStatusView v)
    {
        statusView = v;
    }

    @Override
    public void set_In_De_crase(int i)
    {
        if (model == null)
            return;
        boolean changed = model.adjust(i);
        if (!changed && statusView != null)
        {
            // Explains the no-op rather than silently doing nothing -- the
            // original app communicated "nothing to adjust" via an icon
            // color change (only "green"/interactive for zoom lenses); this
            // app's icon-tint convention is already used for dial selection
            // itself, so re-using it here would be ambiguous. Text is
            // unambiguous instead.
            statusView.showStatus("Focal:" + model.getValue() + "  (fixed lens, nothing to adjust)");
            return;
        }
        showStatusText();
    }

    @Override
    public void toggle()
    {
        if (model != null)
            model.confirmSelection();
        com.obsidium.bettermanual.LegacyLensState.GET().updatePersistentDisplay();
        showStatusText();
    }

    @Override
    public void onValueChanged()
    {
        showStatusText();
    }

    private void showStatusText()
    {
        if (statusView == null || model == null)
            return;
        // Just the focal length (with the effective-focal-length calculation
        // if a special with a real multiplier is active) -- not the lens
        // name or special item's own name, which are shown on their own
        // separate controls. See LegacyLensState.getFocalDescription().
        String text = "Focal:" + com.obsidium.bettermanual.LegacyLensState.GET().getFocalDescription();
        if (model.getLastLogError() != null)
            text += "  LOG FAIL:" + model.getLastLogError();
        statusView.showStatus(text);
    }

    @Override
    protected void updateImage()
    {
        if (view == null)
            return;
        view.setImageResource(R.drawable.legacy_focal_length);
    }

    @Override
    public int getNavigationHelpID()
    {
        return R.string.view_legacyfocal_specific;
    }
}
