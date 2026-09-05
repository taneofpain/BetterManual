package com.obsidium.bettermanual.controller;

import android.graphics.Color;

import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.model.LegacyLensNameModel;
import com.obsidium.bettermanual.views.LegacyLensStatusView;

/**
 * Single-purpose control for cycling legacy lens profiles -- one of three
 * separate controls (see LegacySpecialItemController, LegacyFocalLength
 * Controller) sharing state via LegacyLensState, but each with its own
 * icon/dial/Enter, deliberately not combined into one control. See
 * AspectRatioGuideController's class comment for why: combining independent
 * settings into one control's limited inputs (a dial and a single Enter
 * press) repeatedly produced real bugs there, and every other control in
 * this app is already single-purpose.
 *
 * Dial cycles the lens profile live. Enter confirms -- logs the full
 * combined lens/special/focal-length description as one line (see
 * LegacyLensNameModel.confirmSelection()), not per tick.
 */
public class LegacyLensNameController extends ImageViewController<LegacyLensNameModel> {

    private static final LegacyLensNameController instance = new LegacyLensNameController();

    public static LegacyLensNameController GetInstance()
    {
        return instance;
    }

    private LegacyLensStatusView statusView;

    public void bindStatusView(LegacyLensStatusView v)
    {
        statusView = v;
    }

    @Override
    public void setColorToView(Integer color)
    {
        super.setColorToView(color);
        if (color != null && color == Color.GREEN)
        {
            // Freshly selected -- a natural moment to check whether
            // SDCard root, PROFILES.XML has appeared or changed since it
            // was last read, since this singleton's own reload() otherwise
            // only ever runs once, the first time it's referenced. Without
            // this, placing/editing the file after that point would never
            // be picked up short of a full app/camera restart.
            com.obsidium.bettermanual.LegacyLensState.GET().checkForUpdatedFile();
            com.obsidium.bettermanual.LegacyLensState.GET().updatePersistentDisplay();
            showStatusText();
        }
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
        // Just this control's own value -- the profiles.xml source/path
        // diagnostic (source label, load error) that used to be appended
        // here was only needed while tracking down why the file wasn't
        // being found; removed now that it's confirmed working.
        String text = "Lens:" + model.getValue();
        // Surfaces LegacyLensLogger write failures directly on screen -- see
        // AspectRatioGuideController for why this matters (unrealistic to
        // expect logcat/ADB access to diagnose a silent write failure).
        if (model.getLastLogError() != null)
            text += "  LOG FAIL:" + model.getLastLogError();
        statusView.showStatus(text);
    }

    @Override
    protected void updateImage()
    {
        if (view == null)
            return;
        view.setImageResource(R.drawable.legacy_lens_name);
    }

    @Override
    public int getNavigationHelpID()
    {
        return R.string.view_legacylensname_specific;
    }
}
