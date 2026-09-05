package com.obsidium.bettermanual;

/**
 * Logs every aspect-ratio guide change, with a timestamp, via
 * TimestampedFileLogger -- see that class for the shared file-writing and
 * clock-correction mechanics (extracted there so this feature and the
 * legacy-lens logging feature don't duplicate that logic).
 *
 * This exists because there's no reliable way in this app's camera API to
 * tag an individual photo with which crop mode was active when it was
 * taken -- ordinary single-shot photography never fires any app-level
 * "photo taken" callback at all (confirmed independently by
 * vlousada/LegacyLenses, a similar app built on the same framework, which
 * lists this as an unsolved limitation too).
 *
 * Instead: every crop-mode change is logged with a timestamp. In post, any
 * photo whose own file timestamp falls after one log line and before the
 * next was shot while that first line's mode was active. This works because
 * changing the guide is entirely this app's own UI event -- fully reliable,
 * unlike anything tied to the shutter.
 */
public class AspectRatioGuideLogger {

    // 7 characters + ".TXT" -- same length class as "BMANUAL.TXT", which is
    // confirmed to actually write successfully on this device. See
    // TimestampedFileLogger.log()'s comment for why the short name matters.
    private static final String FILE_NAME = "CROPLOG.TXT";

    public static java.io.File getFile() {
        return new java.io.File(android.os.Environment.getExternalStorageDirectory(), FILE_NAME);
    }

    /**
     * Returns null on success, or a short description of what went wrong.
     */
    public static String log(String ratioLabel) {
        return TimestampedFileLogger.log(FILE_NAME, ratioLabel);
    }
}
