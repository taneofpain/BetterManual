package com.obsidium.bettermanual;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;

import com.sony.scalar.provider.AvindexStore;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Shared logic for writing a timestamped line to a plain text file on
 * external storage, with the timestamp corrected against the camera's real
 * clock rather than Android's. Extracted out of AspectRatioGuideLogger so
 * that feature and the legacy-lens logging feature don't each carry their
 * own copy of the clock-correction logic -- see AspectRatioGuideLogger's own
 * history/comments for the full story of why this correction exists at all
 * (short version: this camera's Android app layer and its own internal
 * clock are genuinely two separate, unsynced clocks, confirmed on real
 * hardware -- camera set to September 2024, logged timestamps read 1970).
 *
 * Both this app's own aspect-ratio guide and vlousada/LegacyLenses (a
 * similar app built on the same OpenMemories framework) hit the identical
 * pair of underlying platform limitations that make this file-logging
 * approach necessary in the first place: no reliable "photo taken" listener
 * for ordinary single-shot photography, and no EXIF-writing support on most
 * camera generations including this one. Log-file-plus-timestamp is the
 * workaround for both features, so the file-writing mechanics belong here,
 * shared, rather than duplicated per feature.
 */
public class TimestampedFileLogger {

    /**
     * Returns the correction offset (real time minus Android system time, in
     * ms) derived from the most recently captured photo's accurate
     * timestamp, or null if there's no photo to anchor to yet (fresh card,
     * or none taken this session).
     */
    private static Long getClockOffsetMs() {
        try {
            Context context = Preferences.GET() != null ? Preferences.GET().getContext() : null;
            if (context == null)
                return null;

            ContentResolver resolver = context.getContentResolver();
            android.net.Uri uri = AvindexStore.Images.Media.getContentUri(AvindexStore.getExternalMediaIds()[0]);
            Cursor cursor = resolver.query(uri, AvindexStore.Images.Media.ALL_COLUMNS, null, null,
                    AvindexStore.Images.ImageColumns.CONTENT_CREATED_LOCAL_DATE_TIME + " DESC");
            if (cursor == null)
                return null;
            try {
                if (cursor.getCount() == 0 || !cursor.moveToFirst())
                    return null;
                long androidNow = System.currentTimeMillis();
                long realTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(
                        AvindexStore.Images.ImageColumns.CONTENT_CREATED_LOCAL_DATE_TIME));
                return realTimestamp - androidNow;
            }
            finally {
                cursor.close();
            }
        }
        catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    /**
     * Appends one timestamped line ("<timestamp> | <message>") to the given
     * file on external storage, creating it (and its parent directory) if
     * needed. Returns null on success, or a short description of what went
     * wrong.
     *
     * fileName must be a short, DOS-8.3-style name (<=8 chars before the
     * extension) -- confirmed on real hardware that this camera's storage
     * layer silently rejects longer names with FileNotFoundException even
     * though the parent directory exists and WRITE_EXTERNAL_STORAGE is
     * declared, almost certainly because SD cards are FAT32-formatted for
     * DCF/photo-storage compliance and this camera enforces strict 8.3
     * naming for anything outside its own DCIM path.
     */
    public static String log(String fileName, String message) {
        long correctedNow = System.currentTimeMillis();
        Long offset = getClockOffsetMs();
        if (offset != null)
            correctedNow += offset;
        // If offset is null (no photo to anchor to yet), correctedNow just
        // stays as the raw, likely-wrong system clock reading -- flagged in
        // the log line itself below so a 1970-looking entry is recognizable
        // as "no reference available yet" rather than silently wrong.

        String timestamp;
        try {
            timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(correctedNow));
        }
        catch (Throwable t) {
            t.printStackTrace();
            timestamp = String.valueOf(correctedNow);
        }
        if (offset == null)
            timestamp += " (uncorrected, no photo yet)";

        try {
            File file = new File(android.os.Environment.getExternalStorageDirectory(), fileName);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                return "couldn't create " + parent.getAbsolutePath();

            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
            writer.append(timestamp).append(" | ").append(message);
            writer.newLine();
            writer.close();
            return null;
        }
        catch (Throwable t) {
            // Throwable, not just IOException: also catches SecurityException
            // and anything else unanticipated, rather than letting it
            // propagate uncaught.
            t.printStackTrace();
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }
}
