package com.obsidium.bettermanual.model;

import com.obsidium.bettermanual.Preferences;
import com.obsidium.bettermanual.camera.CameraInstance;

/**
 * Lets the aspect-ratio guide's border color be set to match whatever the
 * camera's own native focus-peaking color is set to. This app has no way to
 * actually read that setting -- there's no getter for it anywhere in the
 * available camera API, only caution/warning IDs and key-mapping IDs for
 * launching the peaking menu itself -- so this is a manual choice you set
 * once to match, not something detected automatically. Same three colors
 * Sony's own peaking menu offers.
 */
public class AspectRatioGuideColorModel extends AbstractModel<String> {

    // Same reasoning as AspectRatioGuideModel's labels -- kept short since
    // this row is now split 7 ways instead of 5.
    public static final String[] LABELS = {"RED", "YEL", "WHT"};
    public static final int[]    COLORS = {
            android.graphics.Color.rgb(255, 0, 0),
            android.graphics.Color.rgb(255, 255, 0),
            android.graphics.Color.rgb(255, 255, 255)
    };

    private int index;

    public AspectRatioGuideColorModel(CameraInstance camera)
    {
        super(camera);
        index = Preferences.GET().getAspectRatioGuideColorIndex();
        if (index < 0 || index >= LABELS.length)
            index = 0;
    }

    @Override
    public void setValue(int i)
    {
        int direction = i < 0 ? -1 : 1;
        index = ((index + direction) % LABELS.length + LABELS.length) % LABELS.length;
        Preferences.GET().setAspectRatioGuideColorIndex(index);
        fireOnValueChanged();
    }

    @Override
    public String getValue()
    {
        return LABELS[index];
    }

    public int getColor()
    {
        return COLORS[index];
    }

    @Override
    public boolean isSupported()
    {
        return true;
    }
}
