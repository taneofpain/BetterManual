package com.obsidium.bettermanual.model;

/**
 * One "special" item, matching vlousada/LegacyLenses' concept of the same
 * name -- typically a teleconverter or focal-reducer, e.g. "TC 1.4x" or
 * "LT II". Confirmed against a real profiles.xml sample: each entry has a
 * <math> multiplier (e.g. 1.4 for a 1.4x teleconverter, 0.72 for a 0.72x
 * focal reducer) applied to the base lens's focal length to get the
 * effective focal length, plus a free-text <description>.
 */
public class LegacySpecialProfile {

    public final String name;
    public final double math;
    public final String description;

    public LegacySpecialProfile(String name, double math, String description)
    {
        this.name = name;
        this.math = math;
        this.description = description;
    }

    /** "None"/no-multiplier convenience constructor. */
    public LegacySpecialProfile(String name)
    {
        this(name, 1.0, null);
    }
}
