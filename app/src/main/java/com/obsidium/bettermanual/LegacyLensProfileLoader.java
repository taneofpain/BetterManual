package com.obsidium.bettermanual;

import com.obsidium.bettermanual.model.LegacyLensProfile;
import com.obsidium.bettermanual.model.LegacySpecialProfile;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads legacy lens and "special" item (teleconverter, focal-reducer, etc.)
 * profiles from an XML file, matching the profile concept in vlousada/
 * LegacyLenses: users can maintain their own profiles.xml on the SD card,
 * and the app falls back to a small built-in sample set if none is found.
 *
 * Schema confirmed directly against a real profiles.xml sample (this parser
 * originally had to guess at the structure from the README's field-by-field
 * description alone, since the linked schema.xml/profiles.xml sample files
 * pointed at an unreachable repo path -- since corrected against a real
 * file):
 *
 * <pre>{@code
 * <Contents>
 *   <LENSES>
 *     <Lens>
 *       <name>Canon FD 50mm f/1.4 SSC</name>
 *       <mount>FD</mount>
 *       <focal>50</focal>                 <!-- fixed: single number -->
 *       <apertures>1.4-16/3</apertures>   <!-- "/3" = third-stop steps -->
 *     </Lens>
 *     <Lens>
 *       <name>Canon EF 24-105 L USM IS</name>
 *       <mount>EF</mount>
 *       <focal>24-105</focal>             <!-- zoom: min-max range -->
 *       <apertures>4.0-22/3</apertures>
 *     </Lens>
 *   </LENSES>
 *   <SPECIALS>
 *     <Special>
 *       <name>TC 1.4x</name>
 *       <math>1.4</math>                  <!-- effective-focal-length multiplier -->
 *       <description>Tele converter 1.4x C/Y</description>
 *     </Special>
 *   </SPECIALS>
 * </Contents>
 * }</pre>
 *
 * Tag names are matched case-insensitively (the real file capitalizes
 * <Lens>/<Special>/<LENSES>/<SPECIALS>), and this parser doesn't require any
 * particular nesting under a specific root/container -- it just looks for
 * <Lens>/<Special> elements wherever they appear, so it isn't fragile to
 * container-tag naming specifically.
 *
 * User file location: SDCard root, PROFILES.XML -- same root directory this
 * app's log files (BMANUAL.TXT/CROPLOG.TXT/LENSLOG.TXT) already write to
 * successfully, rather than the LLEGACY subfolder LegacyLenses itself
 * documents using -- that subfolder path was confirmed correct via the
 * on-screen diagnostic path, but the file still wasn't found there, so this
 * moved to the simpler, already-proven-working root location instead.
 */
public class LegacyLensProfileLoader {

    // Same root directory as the log files (BMANUAL.TXT/CROPLOG.TXT/
    // LENSLOG.TXT), not a subfolder -- confirmed via the on-screen
    // diagnostic path that the app was correctly looking at
    // /mnt/sdcard/LLEGACY/profiles.xml, but the file wasn't found there.
    // Simplest, most direct fix: look in the same place the log files
    // already write to successfully, with the exact filename given.
    private static final String USER_FILE_NAME = "PROFILES.XML";

    public static class Result {
        public final List<LegacyLensProfile> lenses;
        public final List<LegacySpecialProfile> specials;
        public final boolean fromUserFile;
        public final String errorIfAny;

        Result(List<LegacyLensProfile> lenses, List<LegacySpecialProfile> specials, boolean fromUserFile, String errorIfAny)
        {
            this.lenses = lenses;
            this.specials = specials;
            this.fromUserFile = fromUserFile;
            this.errorIfAny = errorIfAny;
        }
    }

    public static File getUserFile()
    {
        return new File(android.os.Environment.getExternalStorageDirectory(), USER_FILE_NAME);
    }

    public static Result load()
    {
        File userFile = getUserFile();
        if (userFile.exists())
        {
            try
            {
                // Explicit UTF-8, not FileReader (which uses the platform's
                // default charset -- not guaranteed to be UTF-8, and this
                // format's real sample data includes non-ASCII characters,
                // e.g. "Görlitz". A silent mis-decode here wouldn't
                // necessarily throw, but could produce corrupted names or,
                // depending on how the bad bytes land, a parse failure that
                // falls back to the built-in 5-lens sample -- which would
                // look exactly like "only a few items loaded" without it
                // being obvious the real file was never successfully read.
                Result parsed = parse(new InputStreamReader(new FileInputStream(userFile), "UTF-8"));
                if (!parsed.lenses.isEmpty())
                    return new Result(parsed.lenses, parsed.specials, true, null);
                // Parsed successfully but found nothing usable -- fall
                // through to the built-in defaults rather than leaving the
                // user with an empty, non-functional lens list.
            }
            catch (Throwable t)
            {
                t.printStackTrace();
                return new Result(builtInLenses(), builtInSpecials(), false,
                        "couldn't read " + userFile.getAbsolutePath() + ": " + t.getClass().getSimpleName());
            }
        }
        return new Result(builtInLenses(), builtInSpecials(), false, null);
    }

    private static Result parse(java.io.Reader reader) throws Exception
    {
        List<LegacyLensProfile> lenses = new ArrayList<>();
        List<LegacySpecialProfile> specials = new ArrayList<>();

        XmlPullParser parser = android.util.Xml.newPullParser();
        parser.setInput(reader);

        // Field accumulators for whichever <Lens> or <Special> block we're
        // currently inside.
        String currentTag = null;
        boolean inLens = false;
        boolean inSpecial = false;
        String name = null, mount = null, focal = null, apertures = null, description = null;
        double math = 1.0;

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT)
        {
            if (event == XmlPullParser.START_TAG)
            {
                String tag = parser.getName();
                if ("lens".equalsIgnoreCase(tag))
                {
                    inLens = true;
                    name = mount = focal = apertures = null;
                }
                else if ("special".equalsIgnoreCase(tag))
                {
                    inSpecial = true;
                    name = description = null;
                    math = 1.0;
                }
                currentTag = tag;
            }
            else if (event == XmlPullParser.TEXT)
            {
                if (currentTag == null)
                {
                    // no-op
                }
                else if ("name".equalsIgnoreCase(currentTag))
                    name = parser.getText().trim();
                else if (inLens && "mount".equalsIgnoreCase(currentTag))
                    mount = parser.getText().trim();
                else if (inLens && "focal".equalsIgnoreCase(currentTag))
                    focal = parser.getText().trim();
                else if (inLens && "apertures".equalsIgnoreCase(currentTag))
                    apertures = parser.getText().trim();
                else if (inSpecial && "math".equalsIgnoreCase(currentTag))
                {
                    try { math = Double.parseDouble(parser.getText().trim()); }
                    catch (NumberFormatException e) { math = 1.0; }
                }
                else if (inSpecial && "description".equalsIgnoreCase(currentTag))
                    description = parser.getText().trim();
            }
            else if (event == XmlPullParser.END_TAG)
            {
                String tag = parser.getName();
                if ("lens".equalsIgnoreCase(tag))
                {
                    if (name != null && focal != null)
                    {
                        int[] range = parseFocalRange(focal);
                        if (range != null)
                            lenses.add(new LegacyLensProfile(name, mount, range[0], range[1], apertures));
                    }
                    inLens = false;
                }
                else if ("special".equalsIgnoreCase(tag))
                {
                    if (name != null)
                        specials.add(new LegacySpecialProfile(name, math, description));
                    inSpecial = false;
                }
                currentTag = null;
            }
            event = parser.next();
        }

        return new Result(lenses, specials, false, null);
    }

    /**
     * Parses "50" (fixed) or "80-200" (zoom range) into a [min, max] pair.
     * Returns null if unparseable, so a malformed entry is skipped rather
     * than crashing the whole load.
     */
    private static int[] parseFocalRange(String focal)
    {
        try
        {
            if (focal.contains("-"))
            {
                String[] parts = focal.split("-", 2);
                return new int[] { Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) };
            }
            int v = Integer.parseInt(focal.trim());
            return new int[] { v, v };
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * Small built-in sample set, used whenever no valid user profiles.xml
     * exists -- so the feature is usable out of the box, and so there's a
     * concrete example to look at/copy from when creating a real
     * SDCard root, PROFILES.XML. Pulled directly from a real sample file
     * (a representative subset, not the full ~60-lens list).
     */
    private static List<LegacyLensProfile> builtInLenses()
    {
        List<LegacyLensProfile> list = new ArrayList<>();
        list.add(new LegacyLensProfile("Canon FD 50mm f/1.4 SSC", "FD", 50, 50, "1.4-16/3"));
        list.add(new LegacyLensProfile("Canon FL 28mm f/3.5", "FD", 28, 28, "3.5-16/3"));
        list.add(new LegacyLensProfile("Canon EF 24-105 L USM IS", "EF", 24, 105, "4.0-22/3"));
        list.add(new LegacyLensProfile("Canon FD 80-200 f/4.0 L", "FD", 80, 200, "4.0-32/3"));
        list.add(new LegacyLensProfile("Samyang 14mm f/2.8 ED ASPH IF UMC", "F", 14, 14, "2.8-22/3"));
        return list;
    }

    private static List<LegacySpecialProfile> builtInSpecials()
    {
        List<LegacySpecialProfile> list = new ArrayList<>();
        list.add(new LegacySpecialProfile("LT II", 0.72, "Lens Turbo II Focal Reducer 0.72x"));
        list.add(new LegacySpecialProfile("TC 1.4x", 1.4, "Tele converter 1.4x C/Y"));
        list.add(new LegacySpecialProfile("Tube 12mm", 0.12, "Extension tube 12mm"));
        return list;
    }
}
