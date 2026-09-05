package com.obsidium.bettermanual;

import com.obsidium.bettermanual.model.LegacyLensProfile;
import com.obsidium.bettermanual.model.LegacySpecialProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Single shared source of truth for the three legacy-lens controls (name,
 * special item, focal length). These can't each be fully independent the
 * way the aspect-ratio guide's ratio/color pair are: focal length's valid
 * range depends on which lens is currently selected (a fixed 50mm lens has
 * nothing to adjust; an 80-200mm zoom does), so all three need to agree on
 * one current lens. A singleton holder, rather than three independent
 * models, is what makes that coordination simple.
 */
public class LegacyLensState {

    private static final LegacyLensState instance = new LegacyLensState();

    public static LegacyLensState GET()
    {
        return instance;
    }

    private List<LegacyLensProfile> lenses;
    private List<LegacySpecialProfile> specials; // index 0 is always "None"
    private int lensIndex;
    private int specialIndex;
    private int focalLength;
    private String loadError;
    private boolean fromUserFile;
    // Tracks the user file's lastModified() at the point it was last (successfully
    // or unsuccessfully) checked, so checkForUpdatedFile() can tell whether it's
    // worth re-reading without re-parsing the whole XML on every check.
    private long lastCheckedFileModTime = -1;

    private LegacyLensState()
    {
        reload();
    }

    /**
     * Re-reads SDCard root, PROFILES.XML (or falls back to the built-in
     * sample set).
     */
    public void reload()
    {
        LegacyLensProfileLoader.Result result = LegacyLensProfileLoader.load();
        lenses = new ArrayList<>();
        // Always first: lets the lens list itself express "using the Sony
        // kit lens, not a legacy one" -- previously there was no way to
        // "remove" a legacy lens once one had been selected, other than
        // picking whichever real profile happened to be least wrong.
        lenses.add(LegacyLensProfile.none());
        lenses.addAll(result.lenses);
        specials = new ArrayList<>();
        specials.add(new LegacySpecialProfile("None"));
        specials.addAll(result.specials);
        loadError = result.errorIfAny;
        fromUserFile = result.fromUserFile;
        lensIndex = 0;
        specialIndex = 0;
        focalLength = 0;
        java.io.File file = LegacyLensProfileLoader.getUserFile();
        lastCheckedFileModTime = file.exists() ? file.lastModified() : -1;
    }

    /**
     * Re-checks whether SDCard root, PROFILES.XML has appeared, disappeared,
     * or been modified since the last check, reloading only if something
     * actually changed. This class is a singleton whose constructor only
     * runs reload() once, the first time it's ever referenced (typically
     * very early in the app's life) -- without this, placing or editing
     * profiles.xml on the card after that point would never be picked up
     * without a full app/camera restart. Cheap to call often (just a
     * lastModified() stat, not a re-parse, unless something changed) --
     * called whenever the Lens Name control is freshly selected, a natural
     * "check for a fresh file" moment.
     */
    public void checkForUpdatedFile()
    {
        java.io.File file = LegacyLensProfileLoader.getUserFile();
        long currentModTime = file.exists() ? file.lastModified() : -1;
        if (currentModTime != lastCheckedFileModTime)
            reload();
    }

    // Permanent (non-fading) legacy lens display, drawn on the same shared
    // overlay view every status banner uses (LegacyLensStatusView) -- only
    // populated when a real legacy lens is actually selected, or the
    // detected native lens name if "None" is selected. Previously a
    // separate TextView positioned in the RelativeLayout, which turned out
    // unreliable (likely tangled up with tvHint's own visibility toggling,
    // which it was positioned relative to) -- routed through the same
    // view/mechanism the transient banners already use successfully instead.
    private com.obsidium.bettermanual.views.LegacyLensStatusView statusView;

    public void bindStatusView(com.obsidium.bettermanual.views.LegacyLensStatusView v)
    {
        statusView = v;
        updatePersistentDisplay();
    }

    /**
     * Refreshes the permanent display. Called after any of the three
     * legacy-lens controls confirms a selection (Enter) -- not on every
     * dial tick, matching the same "only act on confirm" pattern already
     * used for logging and the real native crop.
     */
    public void updatePersistentDisplay()
    {
        if (statusView == null)
            return;
        LegacyLensProfile lens = getCurrentLens();
        if (lens == null || lens.isNone)
        {
            // No legacy lens selected -- automatically show the native
            // lens's own name instead of leaving this blank, for a real
            // Sony/E-mount lens with electronic contacts (a kit lens, for
            // instance). Still index-prefixed with 0 for consistency with
            // how every other lens is numbered. Falls back to explicitly
            // showing "0:None" (not a blank display) if nothing is
            // detected -- no lens attached, a non-communicating adapter,
            // or this field simply isn't populated on this body/firmware.
            String nativeLensName = getNativeLensName();
            statusView.showPermanentStatus("0:" + (nativeLensName != null ? nativeLensName : "None"));
            return;
        }
        LegacySpecialProfile special = getCurrentSpecial();
        String text = getCurrentLensName() + "  " + getFocalDescription();
        if (special != null && special.math != 1.0)
            text += "  " + special.name;
        statusView.showPermanentStatus(text);
    }

    /**
     * Queries the camera's own native lens identification (CameraEx.
     * getLensInfo().LensName) -- only meaningful for a real Sony/E-mount
     * lens with electronic contacts; a manual legacy lens has no way to
     * communicate with the body at all, which is the entire reason the
     * separate legacy-lens profile system exists. Returns null if nothing
     * is reported (no lens attached, a non-communicating adapter in use, or
     * this specific field just isn't populated on this body/firmware) --
     * defensive, since this mixes "V7"/"V14"-vintage fields not guaranteed
     * present on every body, similar to other native APIs elsewhere in this
     * app that turned out not to be universally supported.
     */
    private String getNativeLensName()
    {
        try
        {
            com.sony.scalar.hardware.CameraEx.LensInfo info =
                    com.obsidium.bettermanual.camera.CameraInstance.GET().getLensInfo();
            if (info != null && info.LensName != null && !info.LensName.isEmpty())
                return info.LensName;
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        return null;
    }

    public LegacyLensProfile getCurrentLens()
    {
        if (lenses == null || lenses.isEmpty())
            return null;
        return lenses.get(lensIndex);
    }

    /**
     * Current lens's name prefixed with its position in the list -- 0 is
     * always "None", 1 is the first real profile (from profiles.xml or the
     * built-in sample), 2 the second, and so on. Shown consistently
     * everywhere the lens name appears (status banner, persistent display,
     * log entry) via this one method, so all three stay in agreement rather
     * than each formatting the number separately.
     */
    /**
     * Plain lens name, no index/colon prefix -- used for on-screen display
     * (status banner, permanent line), where the full name is already
     * unambiguous on its own. getLensNameWithIndex() below is kept for the
     * log entry specifically, where the number is genuinely useful for
     * cross-referencing against profiles.xml's order later.
     */
    public String getCurrentLensName()
    {
        LegacyLensProfile lens = getCurrentLens();
        return lens != null ? lens.name : "(no lens profiles loaded)";
    }

    public String getLensNameWithIndex()
    {
        LegacyLensProfile lens = getCurrentLens();
        String name = lens != null ? lens.name : "(no lens profiles loaded)";
        return lensIndex + ":" + name;
    }

    public LegacySpecialProfile getCurrentSpecial()
    {
        if (specials == null || specials.isEmpty())
            return null;
        return specials.get(specialIndex);
    }

    public int getFocalLength()
    {
        return focalLength;
    }

    public void cycleLens(int direction)
    {
        if (lenses == null || lenses.isEmpty())
            return;
        lensIndex = ((lensIndex + (direction < 0 ? -1 : 1)) % lenses.size() + lenses.size()) % lenses.size();
        // New lens -- reset focal length to its minimum (its default/fixed
        // value), since the old value may be outside the new lens's range
        // or meaningless for a fixed lens.
        focalLength = lenses.get(lensIndex).focalMin;
    }

    public void cycleSpecial(int direction)
    {
        if (specials == null || specials.isEmpty())
            return;
        specialIndex = ((specialIndex + (direction < 0 ? -1 : 1)) % specials.size() + specials.size()) % specials.size();
    }

    /**
     * Returns true if the focal length actually changed. False (a no-op)
     * for a fixed lens -- there's nothing to adjust, matching how the
     * original LegacyLenses app only makes this interactive ("green") for
     * zoom lenses.
     */
    public boolean adjustFocalLength(int direction)
    {
        LegacyLensProfile lens = getCurrentLens();
        if (lens == null || !lens.isZoom())
            return false;
        int newValue = focalLength + (direction < 0 ? -1 : 1);
        if (newValue < lens.focalMin)
            newValue = lens.focalMin;
        if (newValue > lens.focalMax)
            newValue = lens.focalMax;
        if (newValue == focalLength)
            return false;
        focalLength = newValue;
        return true;
    }

    /**
     * Just the focal-length portion, including the effective focal length
     * (base x the active special's math multiplier) if relevant -- extracted
     * so LegacyFocalLengthController can show this on its own, without also
     * pulling in the lens name and special name the way getCombinedDescription()
     * does for the log. Showing the full combined description on every
     * individual control's status text turned out to be more confusing than
     * helpful in practice -- e.g. the ratio control showing the special
     * item's name too, when Special is its own separate control -- so each
     * control's on-screen status now only shows its own value again; only
     * the log entry (confirmSelection() -> getCombinedDescription()) still
     * captures the full combined picture, since that's genuinely useful
     * there (one line fully describing the setup for cross-referencing).
     */
    public String getFocalDescription()
    {
        LegacyLensProfile lens = getCurrentLens();
        if (lens == null || lens.isNone)
            return "--";
        LegacySpecialProfile special = getCurrentSpecial();
        String focalDesc = focalLength + "mm";
        if (special != null && special.math != 1.0)
            focalDesc += String.format(java.util.Locale.US, " (effective %.0fmm)", focalLength * special.math);
        return focalDesc;
    }

    /**
     * One combined line describing the full current lens setup -- logged as
     * a single entry (rather than three separate, harder-to-correlate log
     * streams) whenever any of the three controls is confirmed, so a single
     * log line fully answers "what lens/special/focal length was active at
     * this timestamp". No longer prefixed with the profiles.xml source/path
     * diagnostic (getSourceLabel()) -- that was only needed on screen while
     * tracking down why the file wasn't loading, and was still leaking into
     * every log line here even after being removed from the status banner.
     */
    public String getCombinedDescription()
    {
        LegacySpecialProfile special = getCurrentSpecial();
        String specialName = special != null ? special.name : "None";
        return "Lens:" + getLensNameWithIndex() + " | Special:" + specialName + " | Focal:" + getFocalDescription();
    }
}
