package com.obsidium.bettermanual;

/**
 * Logs the current legacy lens setup (name, special item, focal length) as
 * one combined line, with a timestamp, via TimestampedFileLogger -- same
 * mechanics and same reasoning as AspectRatioGuideLogger (see that class):
 * no reliable "photo taken" listener exists for ordinary photography, and
 * this camera doesn't support writing this information into EXIF, both
 * confirmed independently by vlousada/LegacyLenses hitting the identical
 * pair of limitations on the same underlying framework. A timestamped log
 * line, cross-referenced against photo file timestamps in post, is the
 * workaround.
 */
public class LegacyLensLogger {

    // 7 characters + ".TXT" -- same short, DOS-8.3-safe length class as
    // "CROPLOG.TXT"/"BMANUAL.TXT". See TimestampedFileLogger.log()'s comment
    // for why that length matters on this camera's storage layer.
    private static final String FILE_NAME = "LENSLOG.TXT";

    /**
     * Returns null on success, or a short description of what went wrong.
     */
    public static String log(String description)
    {
        return TimestampedFileLogger.log(FILE_NAME, description);
    }
}
