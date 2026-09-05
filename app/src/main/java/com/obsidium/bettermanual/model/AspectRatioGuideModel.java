package com.obsidium.bettermanual.model;

import com.obsidium.bettermanual.AspectRatioGuideLogger;
import com.obsidium.bettermanual.Preferences;
import com.obsidium.bettermanual.camera.CameraInstance;
import com.sony.scalar.hardware.CameraEx;

import java.util.List;

/**
 * Cycles through a small set of aspect ratios for the guide overlay. Two of
 * them (3:2 and 16:9 -- this camera's actual native capture ratios) also
 * apply a real crop in the camera's own capture pipeline
 * (CameraEx.ParametersModifier.IMAGE_ASPECT_RATIO_*), not just a guide. The
 * rest (1:1, 4:5, 6:7, 21:9, 65:24) have no real native equivalent on this
 * body -- there's no way through this app's camera API to make the sensor
 * crop to those on capture, so they stay guide-only; you'd crop to them
 * afterward. (1:1 does exist as a generic constant in the framework and can
 * show up in getSupportedImageAspectRatios(), but isn't actually a distinct
 * native ratio on this body -- selecting it is a silent no-op/fallback to
 * 3:2 rather than a real 1:1 crop, so it's treated as guide-only here.) This
 * is a V1 framework API (the oldest, most broadly implemented tier), so it's
 * a reasonable bet for actually being supported, but every native-crop call
 * is still verified against
 * getSupportedImageAspectRatios() and wrapped defensively -- if it's not
 * actually there on this body, the native camera setting is just left alone
 * and the guide stays compositional for 3:2/16:9 too on that body.
 *
 * Index 0 is always "off" (no guide drawn, and any native crop this class
 * applied gets restored back to whatever it was before).
 */
public class AspectRatioGuideModel extends AbstractModel<String> {

    public static final String[] LABELS = {"AR OFF", "1:1", "4:5", "6:7", "3:2", "16:9", "21:9", "65:24"};
    public static final float[]  RATIOS = {0f, 1f / 1f, 4f / 5f, 6f / 7f, 3f / 2f, 16f / 9f, 21f / 9f, 65f / 24f};

    // Index-matched to LABELS/RATIOS above. Originally this also tried to
    // treat "1:1" as a third native option, since IMAGE_ASPECT_RATIO_1_1
    // exists generically in the framework's constant list -- but on this
    // body 1:1 isn't actually a distinct native ratio (the camera's real
    // natives are 3:2 and 16:9 only); the API can report a ratio as
    // "supported" via getSupportedImageAspectRatios() without the hardware
    // actually treating it as anything other than a silent fallback to 3:2.
    // So 1:1 stays guide-only now, same as 4:5/6:7/21:9/65:24.
    private static final int INDEX_3_2 = 4;
    private static final int INDEX_16_9 = 5;

    private int index;
    // The camera's real native aspect ratio before this class ever touched
    // it, captured once so it can be restored when moving from 3:2/16:9 to a
    // guide-only ratio (or Off) -- otherwise the camera would stay natively
    // cropped even after switching to a ratio this class never applied.
    private String originalNativeRatio;
    private boolean nativeRatioCaptured;

    // Result of the most recent AspectRatioGuideLogger.log() call -- null if
    // it succeeded, otherwise a short description of what went wrong.
    // Exposed so the controller can show a failure directly in the on-screen
    // status banner instead of it only ever reaching logcat, which isn't
    // realistic to expect access to.
    private String lastLogError;

    public String getLastLogError()
    {
        return lastLogError;
    }

    public AspectRatioGuideModel(CameraInstance camera)
    {
        super(camera);
        index = Preferences.GET().getAspectRatioGuideIndex();
        if (index < 0 || index >= LABELS.length)
            index = 0;
        // Baseline entry so the log always has a starting point, even if the
        // dial is never touched this session -- otherwise there'd be no way
        // to know the mode active for photos taken before the first change.
        lastLogError = AspectRatioGuideLogger.log(LABELS[index] + " (session start)");
    }

    @Override
    public void setValue(int i)
    {
        // Only updates the in-memory/persisted choice and the live guide
        // overlay (via the controller's updateGuideView(), called right
        // after this) -- deliberately does *not* touch the real camera or
        // write to the log. Both of those used to happen on every single
        // dial tick, which meant: (a) the log filled up with every value
        // passed through while just cycling to find the one you actually
        // wanted, not just the one you settled on, and (b) if you cycled
        // past 3:2 or 16:9 on the way to something else, the camera's real
        // aspect ratio would briefly get set to that native crop and then
        // unset again -- unnecessary native reconfiguration while just
        // browsing options. Both now only happen once, in confirmSelection()
        // below, called when the choice is actually confirmed (Enter,
        // moving from ratio-adjust to color-adjust).
        int direction = i < 0 ? -1 : 1;
        index = ((index + direction) % LABELS.length + LABELS.length) % LABELS.length;
        Preferences.GET().setAspectRatioGuideIndex(index);
        fireOnValueChanged();
    }

    /**
     * Called once, when the ratio choice is confirmed (Enter, moving from
     * ratio-adjust to color-adjust) -- not on every dial tick. Applies the
     * real native crop (3:2/16:9 only) and writes the log entry, exactly
     * once per confirmed choice.
     */
    public void confirmSelection()
    {
        applyNativeCropIfSupported();
        lastLogError = AspectRatioGuideLogger.log(LABELS[index]);
    }

    private void applyNativeCropIfSupported()
    {
        if (camera == null)
            return;
        try
        {
            if (!nativeRatioCaptured)
            {
                originalNativeRatio = camera.getImageAspectRatio();
                nativeRatioCaptured = true;
            }

            String target = null;
            if (index == INDEX_3_2)
                target = CameraEx.ParametersModifier.IMAGE_ASPECT_RATIO_3_2;
            else if (index == INDEX_16_9)
                target = CameraEx.ParametersModifier.IMAGE_ASPECT_RATIO_16_9;

            if (target != null)
            {
                List<String> supported = camera.getSupportedImageAspectRatios();
                if (supported != null && supported.contains(target))
                    camera.setImageAspectRatio(target);
                // If this body doesn't actually support it, leave the real
                // camera setting alone -- the overlay above still shows the
                // intended crop, it just stays compositional for this ratio
                // on this body too, same as the ratios with no native
                // equivalent at all.
            }
            else if (originalNativeRatio != null)
            {
                // Guide-only ratio or Off -- make sure the camera isn't left
                // natively cropped from a previous 3:2/16:9 selection.
                camera.setImageAspectRatio(originalNativeRatio);
            }
        }
        catch (NoSuchMethodError | RuntimeException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public String getValue()
    {
        return LABELS[index];
    }

    public float getRatio()
    {
        return RATIOS[index];
    }

    @Override
    public boolean isSupported()
    {
        return true;
    }
}
