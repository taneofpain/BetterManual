package com.obsidium.bettermanual.model;

/**
 * One legacy lens profile, matching the fields described in vlousada/
 * LegacyLenses' profiles.xml schema: name, mount, focal length (a single
 * value for a fixed lens, or a min-max range for a zoom), and an apertures
 * spec (kept as the raw string -- this app doesn't parse or enforce
 * aperture ranges, since the three UI items actually requested are lens
 * name, special item, and focal length only, not aperture management).
 */
public class LegacyLensProfile {

    public final String name;
    public final String mount;
    public final int focalMin;
    public final int focalMax;
    public final String aperturesRaw;
    public final boolean isNone;

    public LegacyLensProfile(String name, String mount, int focalMin, int focalMax, String aperturesRaw)
    {
        this(name, mount, focalMin, focalMax, aperturesRaw, false);
    }

    private LegacyLensProfile(String name, String mount, int focalMin, int focalMax, String aperturesRaw, boolean isNone)
    {
        this.name = name;
        this.mount = mount;
        this.focalMin = Math.min(focalMin, focalMax);
        this.focalMax = Math.max(focalMin, focalMax);
        this.aperturesRaw = aperturesRaw;
        this.isNone = isNone;
    }

    /**
     * Sentinel "no legacy lens" entry -- lets the lens list itself express
     * "using the Sony kit lens, not a legacy one", rather than only being
     * able to pick from real profiles. Always sits at index 0.
     */
    public static LegacyLensProfile none()
    {
        return new LegacyLensProfile("None", null, 0, 0, null, true);
    }

    public boolean isZoom()
    {
        return focalMax > focalMin;
    }

    /**
     * Display string for the focal length itself, e.g. "50mm" for a fixed
     * lens or "24-105mm" for a zoom's full range.
     */
    public String getFocalRangeLabel()
    {
        return isZoom() ? (focalMin + "-" + focalMax + "mm") : (focalMin + "mm");
    }
}
