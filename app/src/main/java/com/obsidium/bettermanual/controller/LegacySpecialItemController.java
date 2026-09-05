package com.obsidium.bettermanual.controller;

import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.model.LegacySpecialItemModel;
import com.obsidium.bettermanual.views.LegacyLensStatusView;

/**
 * Single-purpose control for cycling "special" items (teleconverters etc.,
 * e.g. "TC 1.4x"). See LegacyLensNameController's class comment for the
 * overall design (three separate controls sharing state, each single-
 * purpose).
 */
public class LegacySpecialItemController extends ImageViewController<LegacySpecialItemModel> {

    private static final LegacySpecialItemController instance = new LegacySpecialItemController();

    public static LegacySpecialItemController GetInstance()
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
        if (model != null)
            model.setValue(i);
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
        String text = "Special:" + model.getValue();
        if (model.getLastLogError() != null)
            text += "  LOG FAIL:" + model.getLastLogError();
        statusView.showStatus(text);
    }

    @Override
    protected void updateImage()
    {
        if (view == null)
            return;
        view.setImageResource(R.drawable.legacy_special_item);
    }

    @Override
    public int getNavigationHelpID()
    {
        return R.string.view_legacyspecial_specific;
    }
}
