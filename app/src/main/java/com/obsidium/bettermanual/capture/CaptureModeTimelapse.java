package com.obsidium.bettermanual.capture;

import android.annotation.SuppressLint;
import android.util.Log;

import com.obsidium.bettermanual.CameraUtil;
import com.obsidium.bettermanual.KeyEvents;
import com.obsidium.bettermanual.Preferences;
import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.camera.CameraInstance;
import com.obsidium.bettermanual.camera.CaptureSession;
import com.obsidium.bettermanual.camera.ShutterSpeedValue;
import com.obsidium.bettermanual.controller.DriveModeController;
import com.obsidium.bettermanual.controller.ExposureModeController;
import com.obsidium.bettermanual.layout.CameraUiInterface;
import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.hardware.avio.DisplayManager;
import com.sony.scalar.sysutil.didep.Settings;

import java.util.List;

public class CaptureModeTimelapse extends CaptureMode implements KeyEvents, CaptureSession.CaptureDoneEvent
{

    private final String TAG = CaptureModeTimelapse.class.getSimpleName();

    private int             m_timelapseInterval;    // ms
    private int             m_timelapsePicCount;
    private int             m_timelapsePicsTaken;
    private int             m_autoPowerOffTimeBackup;

    // Drift-corrected scheduling: the timestamp the last shot actually started,
    // used as the anchor for the next shot instead of measuring from "now" every
    // time. See onCaptureDone() for why this matters.
    private long            m_lastShotTime;

    // Screen-off between shots, modeled on jonasjuffinger/TimeLapse and most
    // other dedicated intervalometer apps -- meaningfully saves battery over a
    // long-running timelapse. Toggleable via the Fn key while setting up.
    private DisplayManager  m_displayManager;
    private boolean         m_screenIsOff;
    private boolean         m_screenOffEnabled;
    private final Runnable  m_wakeDisplayRunnable = this::wakeDisplay;
    // Stored (not just an inline lambda at the postDelayed call site) so
    // abort() can cancel a pending delayed capture if the user stops the
    // timelapse mid-wait, the same way it already cancels the other
    // scheduled runnables below.
    private final Runnable  m_actuallyTakePictureRunnable = this::actuallyTakePicture;

    // Lets any key press wake the screen immediately even while this class isn't
    // the active dial listener (it hands that back to the main camera UI once
    // shooting starts) -- see KeyEventHandler.onKeyDown().
    private static CaptureModeTimelapse s_activeInstance;

    // Interval ramping: linearly changes the interval from the value set in
    // TLS_SET_INTERVAL (the start) to a second, separately-set end value over
    // the course of the run. Requires a bounded (non-infinite) picture count,
    // since the ramp is driven by fraction-of-shots-taken -- with an unbounded
    // count there's no "end" to ramp toward.
    private boolean         m_rampIntervalEnabled;
    private int             m_timelapseIntervalStart;
    private int             m_timelapseIntervalEnd;
    private int             m_rampTotalShots;

    // Holy Grail: for a slow lighting change (classic case: a sunset/sunrise
    // transition) this keeps the exposure correct over the course of the run,
    // adjusting ISO first, then shutter speed as a second stage once ISO hits
    // its user-set ceiling. Both ceilings are configured in setup below.
    //
    // This went through two earlier designs before landing here:
    // 1. The camera's native Auto ISO + an ISO ceiling (setISOAutoMax/
    //    AutoISOSensitivityListener) -- the same mechanism exposed in the
    //    camera's own menus. Not actually implemented on the a5100 (confirmed
    //    via NoSuchMethodError on real hardware), even though the equivalent
    //    setting exists in the camera's own UI -- just not exposed through
    //    this app's API surface on this body.
    // 2. The camera's internal exposure metering value (getProperExposureLevel/
    //    ProperExposureLevelCallback). This worked (readings updated live and
    //    the sign was right), but testing showed its scale is impractical to
    //    use directly: real readings were in the tens of thousands, while a
    //    single ISO step only moved it by ~40-50 -- meaning hundreds of shots
    //    to converge on correct exposure via one-step-at-a-time correction,
    //    with no documented units to know how much to jump instead.
    //
    // This uses the live preview's own luminance histogram instead --
    // already continuously available via CameraInstance.getHistogram() (the
    // same proven data driving the on-screen histogram view), and something
    // we can reason about in real, well-understood photographic terms: mean
    // scene brightness compared against a middle-grey target, converted to an
    // EV correction via log2 of that ratio. That lets a correction jump
    // multiple ISO/shutter steps in one shot when genuinely far off, rather
    // than crawling one step at a time regardless of how large the error is.
    private boolean         m_holyGrailEnabled;
    private List<Integer>   m_supportedIsos;
    private int             m_holyGrailMaxIsoPos;       // index into m_supportedIsos
    private int             m_holyGrailBaseIsoPos;
    // Tracks the ISO/shutter position this class has actually commanded.
    // Nothing else in this app changes ISO/shutter speed mid-timelapse, so
    // this stays authoritative without needing to read either back live.
    private int             m_holyGrailCurrentIsoPos;
    private int             m_holyGrailMaxShutterIndex; // index into CameraUtil.SHUTTER_SPEED_VALUES
    private int             m_holyGrailBaseShutterIndex;
    private int             m_holyGrailCurrentShutterIndex;
    // Set in confirmAndStart()'s catch block if Holy Grail fails to actually
    // start. showMessageDelayed() alone isn't enough to surface this:
    // startShooting() calls hideMessage() on every shot including the very
    // first one, wiping out a one-shot failure message before there's any
    // real chance to read it. This gets folded into the ongoing per-shot
    // status text in onCaptureDone() instead, so it stays visible for the
    // whole run rather than flashing and disappearing.
    private String          m_holyGrailFailureReason;
    // Last computed correction, shown in the status text as a diagnostic.
    private float            m_holyGrailLastMeanBrightness;
    private float            m_holyGrailLastEvDiff;
    // Detects corrections that aren't actually taking effect: if EVdiff isn't
    // shrinking despite applying real steps, something is disconnected --
    // e.g. some Sony bodies have a "Live View Display"/"Setting Effect" menu
    // option that, when off, keeps the live preview auto-brightened for
    // visibility regardless of the actual manual exposure settings, which
    // would make the histogram (read from that same preview) blind to every
    // ISO/shutter change this makes. Whatever the cause, this should never
    // keep blindly ramping ISO toward the ceiling on a signal that
    // demonstrably isn't responding -- after a few corrections in a row with
    // no real improvement, stop and say so, rather than continuing to guess.
    private float            m_holyGrailEvDiffAtLastCorrection = Float.NaN;
    private int              m_holyGrailStallCount;
    private static final int HOLYGRAIL_MAX_STALLED_CORRECTIONS = 3;
    // How many convergence attempts to allow before the countdown/first shot,
    // trying to get exposure right up front rather than on real, kept frames
    // early in the run -- see confirmAndStart()/runHolyGrailPreflight(). At
    // ~2.5s (HOLYGRAIL_SETTLE_MS) per attempt, 10 is a ~25s worst case, which
    // is a reasonable one-time cost for a run that might go on for hours.
    private static final int HOLYGRAIL_MAX_PREFLIGHT_ATTEMPTS = 10;
    private int              m_holyGrailPreflightAttempts;
    // True only during the preflight metering pass -- this is what
    // onDeleteKeyUp() checks to know whether Delete/Trash should cancel it.
    // Reachable at all only because beginCountdown() (not confirmAndStart())
    // is what hands the dial listener back to the main UI -- see the comment
    // there.
    private boolean          m_holyGrailPreflightActive;
    private final Runnable   m_holyGrailPreflightRunnable = this::runHolyGrailPreflight;
    // Target mean brightness as a fraction of full range (0..1). This is an
    // approximation of "18% grey" after typical gamma correction, not an
    // exact colorimetric calculation for this specific camera's output
    // pipeline -- may need empirical tuning. A simple full-frame average
    // also can't distinguish "evenly grey scene" from "half black, half
    // white", the way the camera's own evaluative/matrix metering might;
    // it's the same basic limitation any average-brightness auto-exposure
    // has, not something specific to this implementation.
    private static final float HOLYGRAIL_TARGET_MEAN = 0.45f;
    // Below this many EV of error, don't bother correcting -- avoids
    // twitching on normal frame-to-frame metering noise under stable light.
    private static final float HOLYGRAIL_EV_DEADZONE = 0.2f;
    // Hard cap on how many ISO/shutter steps to move in a single shot, even
    // if the computed correction calls for more. A real sustained change
    // gets caught up to over a few shots instead of jumping all at once --
    // kept small (a 2/3-stop max, not the 2 stops this started at) after
    // testing showed even a couple of large corrections applied back to back
    // overshot badly into overexposure. See HOLYGRAIL_SETTLE_MS below for the
    // other half of taming that: not just a smaller step, but a mandatory
    // real-time gap between corrections so each one is actually judged
    // against its own settled result rather than stacking on the last one.
    private static final int HOLYGRAIL_MAX_STEPS_PER_SHOT = 2; // 2/3 stop, at 1/3-stop steps
    // Real elapsed time to wait after applying a correction before evaluating
    // for another one -- confirmed via a manual test (changing ISO by hand
    // with the on-screen histogram visible) that this camera's histogram
    // takes 1-2 real seconds to catch up to an exposure change. This was
    // originally a "wait N shots" counter instead of real time, which is only
    // as good as the configured interval happens to be -- a fast interval
    // could mean "2 shots" was well under 1-2 seconds, re-correcting before
    // the previous change had actually shown up yet. A comfortable margin
    // above the confirmed settle time avoids that regardless of interval.
    private static final long HOLYGRAIL_SETTLE_MS = 2500;
    private long             m_holyGrailLastCorrectionTime;

    private final int TLS_SET_NONE = 0;
    private final int TLS_SET_INTERVAL = 1;
    private final int TLS_SET_PICCOUNT = 2;
    private final int TLS_SET_SCREENOFF_TOGGLE = 3;
    private final int TLS_SET_RAMP_TOGGLE = 4;
    private final int TLS_SET_RAMP_END_INTERVAL = 5;
    private final int TLS_SET_HOLYGRAIL_TOGGLE = 6;
    private final int TLS_SET_HOLYGRAIL_MAXISO = 7;
    private final int TLS_SET_HOLYGRAIL_MAXSHUTTER = 8;
    private int currentdial = TLS_SET_NONE;


    public CaptureModeTimelapse(CameraUiInterface manualActivity)
    {
        super(manualActivity);
    }

    /**
     * Called from KeyEventHandler.onKeyDown() on every key press so the screen
     * comes back immediately if the user reaches for a button while it's off,
     * even though this class isn't the active dial listener during shooting
     * (see onEnterKeyUp(), which hands the listener back to the main camera UI
     * before the countdown/capture sequence starts). Safe no-op otherwise.
     */
    public static void wakeDisplayIfOff()
    {
        if (s_activeInstance != null)
            s_activeInstance.wakeDisplay();
    }

    /**
     * Cancels a currently-running timelapse (Holy Grail or not) if one is
     * active. Called from CameraUiFragment.onDeleteKeyUp() -- see the
     * comment there for why: on the main screen, Delete's existing behavior
     * is to close the whole app outright, which would abruptly kill an
     * in-progress timelapse without the chance to cleanly restore whatever
     * ISO/exposure settings Holy Grail was mid-adjusting, which is worse than
     * doing nothing. Returns true if it cancelled something (the caller
     * should treat the key as handled and not also close the app), false if
     * there was nothing running to cancel (caller should fall through to its
     * normal behavior).
     */
    public static boolean cancelIfActive()
    {
        if (s_activeInstance != null && s_activeInstance.isActive())
        {
            Log.d(CaptureModeTimelapse.class.getSimpleName(), "Cancelling active timelapse instead of closing the app");
            s_activeInstance.abort();
            return true;
        }
        return false;
    }

    private void turnScreenOff()
    {
        if (!m_screenOffEnabled)
            return;
        try
        {
            if (m_displayManager == null)
                m_displayManager = new DisplayManager();
            m_displayManager.switchDisplayOutputTo(DisplayManager.DEVICE_ID_NONE);
            m_screenIsOff = true;
        }
        catch (Exception e)
        {
            // Not supported on some camera models -- just leave the screen on.
            e.printStackTrace();
        }
    }

    private void wakeDisplay()
    {
        if (m_screenIsOff && m_displayManager != null)
        {
            m_displayManager.switchDisplayOutputTo(DisplayManager.DEVICE_ID_PANEL);
        }
        m_screenIsOff = false;
    }

    // Holy Grail requires Manual exposure mode. In any auto mode (Aperture
    // Priority, Shutter Priority, Program), the camera is already continuously
    // re-metering and driving one or more exposure parameters itself every
    // frame -- our own ISO/shutter nudges would just get silently overridden
    // by its next metering cycle. This is also just how real Holy Grail
    // technique is shot: any camera-driven auto-metering between frames is
    // the source of visible flicker, which manual control is there to avoid.
    private boolean isInManualExposureMode()
    {
        try
        {
            return CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE.equals(CameraInstance.GET().getSceneMode());
        }
        catch (RuntimeException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void toggle() {
        if (isActive())
        {
            abort();
        }
        else {
            cameraUiInterface.getActivityInterface().getDialHandler().setDialEventListner(this);
            onEnterKeyUp();
        }
    }

    @Override
    public int getNavigationHelpID() {
        return R.string.view_Timelapse;
    }

    public void reset()
    {
        m_timelapsePicCount = 0;
        currentdial = TLS_SET_INTERVAL;
        updateTimelapsePictureCount();
    }

    @Override
    public boolean prepare() {
        if (isActive())
            abort();
        else
        {
            cameraUiInterface.setLeftViewVisibility(false);
            currentdial = TLS_SET_INTERVAL;
            m_timelapseInterval = 1000;
            m_timelapsePicsTaken = 0;
            m_screenOffEnabled = Preferences.GET().getTimelapseScreenOff();
            m_rampIntervalEnabled = false;
            m_holyGrailEnabled = false;
            m_holyGrailFailureReason = null;
            updateTimelapseInterval();
            showSetupHint();

            // Not supported on some camera models
            try
            {
                m_autoPowerOffTimeBackup = Settings.getAutoPowerOffTime();
            }
            catch (NoSuchMethodError e)
            {
            }
        }
        return true;
    }

    private void showSetupHint()
    {
        String hint;
        switch (currentdial)
        {
            case TLS_SET_PICCOUNT:
                hint = cameraUiInterface.getActivityInterface().getResString(R.string.icon_lowerDial) + " to set picture count";
                break;
            case TLS_SET_SCREENOFF_TOGGLE:
                hint = cameraUiInterface.getActivityInterface().getResString(R.string.icon_lowerDial) + " to enable/disable, screen-off between shots: " + (m_screenOffEnabled ? "ON" : "OFF");
                break;
            case TLS_SET_RAMP_TOGGLE:
                hint = cameraUiInterface.getActivityInterface().getResString(R.string.icon_lowerDial) + " to enable/disable, interval ramping: " + (m_rampIntervalEnabled ? "ON" : "OFF")
                        + (m_timelapsePicCount == 0 ? " (needs a picture limit)" : "");
                break;
            case TLS_SET_RAMP_END_INTERVAL:
                hint = cameraUiInterface.getActivityInterface().getResString(R.string.icon_lowerDial) + " to set the END interval (ramps from your start interval to this)";
                break;
            case TLS_SET_HOLYGRAIL_TOGGLE:
                hint = cameraUiInterface.getActivityInterface().getResString(R.string.icon_lowerDial) + " to enable/disable, Holy Grail exposure ramp: " + (m_holyGrailEnabled ? "ON" : "OFF")
                        + (isInManualExposureMode() ? "" : " (requires Manual exposure mode)");
                break;
            case TLS_SET_HOLYGRAIL_MAXISO:
                hint = cameraUiInterface.getActivityInterface().getResString(R.string.icon_lowerDial) + " to set MAX ISO ceiling";
                break;
            case TLS_SET_HOLYGRAIL_MAXSHUTTER:
                hint = cameraUiInterface.getActivityInterface().getResString(R.string.icon_lowerDial) + " to set MAX shutter speed ceiling";
                break;
            default:
                hint = cameraUiInterface.getActivityInterface().getResString(R.string.icon_lowerDial) + " to set timelapse interval";
                break;
        }
        cameraUiInterface.showHintMessage(hint + ", "
                + cameraUiInterface.getActivityInterface().getResString(R.string.icon_enterButton) + " to confirm");
    }

    @Override
    public void startShooting() {
        Log.d(TAG,"startShooting");
        wakeDisplay();
        cameraUiInterface.hideHintMessage();
        cameraUiInterface.hideMessage();
        try
        {
            Settings.setAutoPowerOffTime(m_timelapseInterval / 1000 * 2);
        }
        catch (NoSuchMethodError e)
        {
        }
        cameraUiInterface.getActivityInterface().setBulbCapture(false);
        cameraUiInterface.getActivityInterface().setCaptureDoneEventListner(this);

        long captureDelayMs = m_holyGrailEnabled ? applyHolyGrailExposureCorrection() : 0;

        if (captureDelayMs > 0)
        {
            // A correction was just applied -- wait for it to actually settle
            // before capturing, not just before the *next* evaluation.
            // Otherwise this shot itself gets taken mid-transition, using
            // settings partway between the old and new value, which is
            // exactly the kind of "the change didn't really take yet" state
            // that caused problems here before.
            Log.d(TAG, "Holy Grail: waiting " + captureDelayMs + "ms for correction to settle before capturing");
            cameraUiInterface.getActivityInterface().getMainHandler().postDelayed(m_actuallyTakePictureRunnable, captureDelayMs);
        }
        else
        {
            actuallyTakePicture();
        }
    }

    private void actuallyTakePicture()
    {
        // Recorded here, not at the top of startShooting(), since Holy Grail
        // may have just delayed the actual capture -- the drift-corrected
        // scheduling in onCaptureDone() needs to measure from when the shot
        // really started, not from whenever startShooting() was first called.
        m_lastShotTime = System.currentTimeMillis();
        CameraInstance.GET().takePicture();
    }

    /**
     * Meters and applies any needed ISO/shutter correction *before* each
     * shot, using the live preview's own histogram (already continuously
     * updated by CameraInstance.getHistogram() -- no request/response
     * round-trip needed, unlike the metering-API approaches this replaced).
     * See the field comments above for why this is the third design Holy
     * Grail has gone through.
     */
    private long applyHolyGrailExposureCorrection()
    {
        if (m_supportedIsos == null || m_supportedIsos.isEmpty())
            return 0;

        short[] histogram = CameraInstance.GET().getHistogram();
        if (histogram == null || histogram.length == 0)
        {
            Log.d(TAG, "Holy Grail: no histogram data available yet, skipping this shot's correction");
            return 0;
        }

        // Mean luminance, normalized to 0..1 across the histogram's bin range.
        long weightedSum = 0;
        long totalCount = 0;
        for (int bin = 0; bin < histogram.length; bin++)
        {
            weightedSum += (long) bin * histogram[bin];
            totalCount += histogram[bin];
        }
        if (totalCount == 0)
        {
            Log.d(TAG, "Holy Grail: empty histogram (all-black frame or no data yet), skipping this shot's correction");
            return 0;
        }
        float meanBrightness = (float) weightedSum / totalCount / (histogram.length - 1);
        m_holyGrailLastMeanBrightness = meanBrightness;

        // EV difference via log2 of the brightness ratio -- standard
        // photographic exposure math: doubling/halving brightness is exactly
        // 1 stop. Floor meanBrightness so a totally black frame doesn't take
        // log2(anything/0) to infinity.
        float evDiff = (float) (Math.log(HOLYGRAIL_TARGET_MEAN / Math.max(meanBrightness, 0.001f)) / Math.log(2));
        m_holyGrailLastEvDiff = evDiff;

        if (Math.abs(evDiff) <= HOLYGRAIL_EV_DEADZONE)
        {
            Log.d(TAG, String.format("Holy Grail: mean %.3f, %.2f EV off target -- within deadzone, no change", meanBrightness, evDiff));
            return 0;
        }

        long msSinceLastCorrection = System.currentTimeMillis() - m_holyGrailLastCorrectionTime;
        if (msSinceLastCorrection < HOLYGRAIL_SETTLE_MS)
        {
            // Still measuring the result of the last correction -- don't
            // stack another one on top of a state that may not have fully
            // settled yet. This is the mean/EVdiff still logged/shown above
            // so it's visible that Holy Grail is watching, just not acting.
            Log.d(TAG, String.format("Holy Grail: mean %.3f, %.2f EV off target -- letting last correction settle (%dms/%dms)",
                    meanBrightness, evDiff, msSinceLastCorrection, HOLYGRAIL_SETTLE_MS));
            return 0;
        }

        // Stall check: if a previous correction was applied and had time to
        // settle, but EVdiff hasn't actually improved, the correction isn't
        // taking effect on whatever the histogram is measuring -- see the
        // field comment on m_holyGrailStallCount for why. Comparing against
        // half the deadzone as the "did it improve at all" bar is deliberately
        // loose -- this is meant to catch "completely disconnected", not to
        // second-guess normal, working, gradual correction.
        if (!Float.isNaN(m_holyGrailEvDiffAtLastCorrection)
                && Math.abs(evDiff) >= Math.abs(m_holyGrailEvDiffAtLastCorrection) - (HOLYGRAIL_EV_DEADZONE / 2f))
        {
            ++m_holyGrailStallCount;
            Log.d(TAG, String.format("Holy Grail: EVdiff %.2f didn't improve from %.2f after a settled correction (stall %d/%d)",
                    evDiff, m_holyGrailEvDiffAtLastCorrection, m_holyGrailStallCount, HOLYGRAIL_MAX_STALLED_CORRECTIONS));
            if (m_holyGrailStallCount >= HOLYGRAIL_MAX_STALLED_CORRECTIONS)
            {
                Log.d(TAG, "Holy Grail: exposure corrections aren't affecting the metered brightness, disabling for the rest of this run");
                m_holyGrailEnabled = false;
                m_holyGrailFailureReason = "corrections aren't affecting metered brightness";
                cameraUiInterface.showMessageDelayed("Holy Grail DISABLED: " + m_holyGrailFailureReason);
                return 0;
            }
        }
        else
        {
            m_holyGrailStallCount = 0;
        }
        m_holyGrailEvDiffAtLastCorrection = evDiff;

        // 1/3-stop steps (matches the ISO list's granularity and the shutter
        // table's granularity -- both already confirmed third-stop spaced).
        int stepsNeeded = Math.round(evDiff * 3);
        stepsNeeded = Math.max(-HOLYGRAIL_MAX_STEPS_PER_SHOT, Math.min(HOLYGRAIL_MAX_STEPS_PER_SHOT, stepsNeeded));

        Log.d(TAG, String.format("Holy Grail: mean %.3f, %.2f EV off target, moving %d step(s)", meanBrightness, evDiff, stepsNeeded));

        int appliedSteps = 0;

        if (stepsNeeded > 0)
        {
            // Under-exposed: raise ISO first, then shutter once ISO is maxed.
            int isoSteps = Math.min(stepsNeeded, m_holyGrailMaxIsoPos - m_holyGrailCurrentIsoPos);
            if (isoSteps > 0)
            {
                m_holyGrailCurrentIsoPos += isoSteps;
                CameraInstance.GET().setISOSensitivity(m_supportedIsos.get(m_holyGrailCurrentIsoPos));
                stepsNeeded -= isoSteps;
                appliedSteps += isoSteps;
            }
            if (stepsNeeded > 0)
            {
                int shutterSteps = Math.min(stepsNeeded, m_holyGrailMaxShutterIndex - m_holyGrailCurrentShutterIndex);
                if (shutterSteps > 0)
                {
                    m_holyGrailCurrentShutterIndex += shutterSteps;
                    CameraInstance.GET().adjustShutterSpeed(-shutterSteps);
                    appliedSteps += shutterSteps;
                }
            }
        }
        else
        {
            // Over-exposed: recover shutter first (it was the last thing
            // extended), then ISO, symmetric with how they were raised.
            int shutterSteps = Math.min(-stepsNeeded, m_holyGrailCurrentShutterIndex - m_holyGrailBaseShutterIndex);
            if (shutterSteps > 0)
            {
                m_holyGrailCurrentShutterIndex -= shutterSteps;
                CameraInstance.GET().adjustShutterSpeed(shutterSteps);
                stepsNeeded += shutterSteps;
                appliedSteps += shutterSteps;
            }
            if (stepsNeeded < 0)
            {
                int isoSteps = Math.min(-stepsNeeded, m_holyGrailCurrentIsoPos - m_holyGrailBaseIsoPos);
                if (isoSteps > 0)
                {
                    m_holyGrailCurrentIsoPos -= isoSteps;
                    CameraInstance.GET().setISOSensitivity(m_supportedIsos.get(m_holyGrailCurrentIsoPos));
                    appliedSteps += isoSteps;
                }
            }
        }

        if (appliedSteps > 0)
        {
            m_holyGrailLastCorrectionTime = System.currentTimeMillis();
            return HOLYGRAIL_SETTLE_MS;
        }
        return 0;
    }

    @Override
    public void abort() {
        cameraUiInterface.getActivityInterface().getMainHandler().removeCallbacks(m_countDownRunnable);
        cameraUiInterface.getActivityInterface().getMainHandler().removeCallbacks(m_timelapseRunnable);
        cameraUiInterface.getActivityInterface().getMainHandler().removeCallbacks(m_wakeDisplayRunnable);
        cameraUiInterface.getActivityInterface().getMainHandler().removeCallbacks(m_actuallyTakePictureRunnable);
        cameraUiInterface.getActivityInterface().getMainHandler().removeCallbacks(m_holyGrailPreflightRunnable);
        // Normally beginCountdown() is what hands the dial listener back to
        // the main UI, once preflight (if any) has finished. If abort() is
        // reached before that -- e.g. Delete cancelling the preflight metering
        // pass -- beginCountdown() never ran, so this class would otherwise
        // stay stuck as the active listener after cleanup, leaving the main
        // UI unable to receive key input. Safe to call unconditionally even
        // when beginCountdown() already did this.
        m_holyGrailPreflightActive = false;
        cameraUiInterface.getActivityInterface().getDialHandler().setDialEventListner((KeyEvents)cameraUiInterface);
        isActive = false;
        cameraUiInterface.showMessageDelayed("Timelapse finished");
        CameraInstance.GET().enableHwShutterButton();
        CameraInstance.GET().startPreview();

        if (m_holyGrailEnabled)
        {
            try
            {
                // Restore whatever ISO was actually set before Holy Grail
                // started adjusting it, in case it's not already back there.
                if (m_supportedIsos != null && m_holyGrailBaseIsoPos < m_supportedIsos.size())
                    CameraInstance.GET().setISOSensitivity(m_supportedIsos.get(m_holyGrailBaseIsoPos));
            }
            catch (RuntimeException e)
            {
                e.printStackTrace();
            }
        }

        wakeDisplay();
        if (m_displayManager != null)
        {
            m_displayManager.finish();
            m_displayManager = null;
        }
        s_activeInstance = null;

            // Update controls
        cameraUiInterface.getActivityInterface().getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                cameraUiInterface.hideHintMessage();
                cameraUiInterface.setLeftViewVisibility(true);
                ExposureModeController.GetInstance().onValueChanged();
                DriveModeController.GetInstance().onValueChanged();

                cameraUiInterface.setActiveViewFlag(Preferences.GET().getViewFlags(cameraUiInterface.getActiveViewsFlag()));
                cameraUiInterface.updateViewVisibility();
            }
        });


            try
            {
                Settings.setAutoPowerOffTime(m_autoPowerOffTimeBackup);
            }
            catch (NoSuchMethodError e)
            {
            }
        currentdial = TLS_SET_NONE;

    }

    @Override
    public void decrement()
    {
        switch (currentdial)
        {
            case TLS_SET_PICCOUNT:
                decrementPicCount();
                break;
            case TLS_SET_SCREENOFF_TOGGLE:
                m_screenOffEnabled = false;
                Preferences.GET().setTimelapseScreenOff(false);
                showSetupHint();
                break;
            case TLS_SET_RAMP_TOGGLE:
                m_rampIntervalEnabled = false;
                showSetupHint();
                break;
            case TLS_SET_HOLYGRAIL_TOGGLE:
                m_holyGrailEnabled = false;
                showSetupHint();
                break;
            case TLS_SET_RAMP_END_INTERVAL:
                m_timelapseIntervalEnd = stepInterval(m_timelapseIntervalEnd, -1);
                updateRampEndInterval();
                break;
            case TLS_SET_HOLYGRAIL_MAXISO:
                if (m_holyGrailMaxIsoPos > 1)
                    --m_holyGrailMaxIsoPos;
                updateHolyGrailMaxIso();
                break;
            case TLS_SET_HOLYGRAIL_MAXSHUTTER:
                if (m_holyGrailMaxShutterIndex > m_holyGrailBaseShutterIndex)
                    --m_holyGrailMaxShutterIndex;
                updateHolyGrailMaxShutter();
                break;
            default:
                m_timelapseInterval = stepInterval(m_timelapseInterval, -1);
                updateTimelapseInterval();
                break;
        }
    }

    @Override
    public void increment()
    {
        switch (currentdial)
        {
            case TLS_SET_PICCOUNT:
                incrementPicCount();
                break;
            case TLS_SET_SCREENOFF_TOGGLE:
                m_screenOffEnabled = true;
                Preferences.GET().setTimelapseScreenOff(true);
                showSetupHint();
                break;
            case TLS_SET_RAMP_TOGGLE:
                // Ramping needs a bounded picture count to ramp toward; ignore
                // the "enable" turn otherwise rather than accepting a setting
                // that can't actually do anything.
                if (m_timelapsePicCount > 0)
                    m_rampIntervalEnabled = true;
                showSetupHint();
                break;
            case TLS_SET_HOLYGRAIL_TOGGLE:
                if (isInManualExposureMode())
                    m_holyGrailEnabled = true;
                showSetupHint();
                break;
            case TLS_SET_RAMP_END_INTERVAL:
                m_timelapseIntervalEnd = stepInterval(m_timelapseIntervalEnd, 1);
                updateRampEndInterval();
                break;
            case TLS_SET_HOLYGRAIL_MAXISO:
                if (m_supportedIsos != null && m_holyGrailMaxIsoPos < m_supportedIsos.size() - 1)
                    ++m_holyGrailMaxIsoPos;
                updateHolyGrailMaxIso();
                break;
            case TLS_SET_HOLYGRAIL_MAXSHUTTER:
                if (m_holyGrailMaxShutterIndex < CameraUtil.SHUTTER_SPEED_VALUES.length - 1)
                    ++m_holyGrailMaxShutterIndex;
                updateHolyGrailMaxShutter();
                break;
            default:
                m_timelapseInterval = stepInterval(m_timelapseInterval, 1);
                updateTimelapseInterval();
                break;
        }
    }

    // Shared stepping used by both the base interval and the ramp's end
    // interval: 100ms steps below 1s, 1s steps at/above 1s.
    private int stepInterval(int interval, int direction)
    {
        if (direction < 0)
        {
            if (interval > 0)
                interval -= (interval <= 1000) ? 100 : 1000;
        }
        else
        {
            interval += (interval < 1000) ? 100 : 1000;
        }
        return interval;
    }

    private String formatInterval(int interval)
    {
        if (interval == 0)
            return "No delay";
        else if (interval < 1000)
            return String.format("%d msec", interval);
        else if (interval == 1000)
            return "1 second";
        else
            return String.format("%d seconds", interval / 1000);
    }

    private void updateTimelapseInterval()
    {
        cameraUiInterface.showMessage(formatInterval(m_timelapseInterval));
    }

    private void updateRampEndInterval()
    {
        cameraUiInterface.showMessage("End: " + formatInterval(m_timelapseIntervalEnd));
    }

    private void updateTimelapsePictureCount()
    {
        if (m_timelapsePicCount == 0)
            cameraUiInterface.showMessage("No picture limit");
        else
            cameraUiInterface.showMessage(String.format("%d pictures", m_timelapsePicCount));
    }

    private void updateHolyGrailMaxIso()
    {
        if (m_supportedIsos == null || m_holyGrailMaxIsoPos >= m_supportedIsos.size())
            return;
        cameraUiInterface.showMessage("Max ISO: " + m_supportedIsos.get(m_holyGrailMaxIsoPos));
    }

    private void updateHolyGrailMaxShutter()
    {
        ShutterSpeedValue v = CameraUtil.SHUTTER_SPEED_VALUES[m_holyGrailMaxShutterIndex];
        cameraUiInterface.showMessage("Max shutter: " + v.getShutterSpeed());
    }

    public void decrementPicCount()
    {
        if (m_timelapsePicCount > 0)
            --m_timelapsePicCount;
        updateTimelapsePictureCount();
    }

    public void incrementPicCount()
    {
        ++m_timelapsePicCount;
        updateTimelapsePictureCount();
    }

    private final Runnable  m_timelapseRunnable = () -> startShooting();

    @Override
    public boolean onUpperDialChanged(int value) {
        return false;
    }

    @Override
    public boolean onLowerDialChanged(int value) {
        if (value < 0)
            decrement();
        else
            increment();
        return false;
    }

    @Override
    public boolean onUpKeyDown() {
        return false;
    }

    @Override
    public boolean onUpKeyUp() {
        return false;
    }

    @Override
    public boolean onDownKeyDown() {
        return false;
    }

    @Override
    public boolean onDownKeyUp() {
        return false;
    }

    @Override
    public boolean onLeftKeyDown() {
        return false;
    }

    @Override
    public boolean onLeftKeyUp() {
        return false;
    }

    @Override
    public boolean onRightKeyDown() {
        return false;
    }

    @Override
    public boolean onRightKeyUp() {
        return false;
    }

    @Override
    public boolean onEnterKeyDown() {

        return false;
    }

    @SuppressLint("SuspiciousIndentation")
    @Override
    public boolean onEnterKeyUp() {
        Log.d(TAG,"onEnterKeyDown" + currentdial);
        switch (currentdial)
        {
            case TLS_SET_NONE:
                if (prepare())
                    updateTimelapseInterval();
                break;

            case TLS_SET_INTERVAL:
                m_timelapseIntervalStart = m_timelapseInterval;
                m_timelapseIntervalEnd = m_timelapseInterval * 3;
                currentdial = TLS_SET_PICCOUNT;
                showSetupHint();
                updateTimelapsePictureCount();
                break;

            case TLS_SET_PICCOUNT:
                currentdial = TLS_SET_SCREENOFF_TOGGLE;
                showSetupHint();
                break;

            case TLS_SET_SCREENOFF_TOGGLE:
                currentdial = TLS_SET_RAMP_TOGGLE;
                showSetupHint();
                break;

            case TLS_SET_RAMP_TOGGLE:
                if (m_rampIntervalEnabled)
                {
                    currentdial = TLS_SET_RAMP_END_INTERVAL;
                    showSetupHint();
                    updateRampEndInterval();
                }
                else
                {
                    currentdial = TLS_SET_HOLYGRAIL_TOGGLE;
                    showSetupHint();
                }
                break;

            case TLS_SET_RAMP_END_INTERVAL:
                currentdial = TLS_SET_HOLYGRAIL_TOGGLE;
                showSetupHint();
                break;

            case TLS_SET_HOLYGRAIL_TOGGLE:
                if (m_holyGrailEnabled)
                {
                    m_supportedIsos = CameraInstance.GET().getSupportedISOSensitivities();
                    int currentIso = CameraInstance.GET().getISOSensitivity();
                    m_holyGrailBaseIsoPos = 1;
                    if (m_supportedIsos != null)
                    {
                        int idx = m_supportedIsos.indexOf(currentIso);
                        if (idx > 0) // skip index 0 (Auto) even if somehow matched
                            m_holyGrailBaseIsoPos = idx;
                    }
                    m_holyGrailCurrentIsoPos = m_holyGrailBaseIsoPos;
                    m_holyGrailMaxIsoPos = (m_supportedIsos != null && !m_supportedIsos.isEmpty())
                            ? m_supportedIsos.size() - 1 : 1;
                    m_holyGrailBaseShutterIndex = CameraUtil.getShutterValueIndex(CameraInstance.GET().getShutterSpeed());
                    if (m_holyGrailBaseShutterIndex < 0)
                        m_holyGrailBaseShutterIndex = 0;
                    m_holyGrailMaxShutterIndex = Math.min(m_holyGrailBaseShutterIndex + 10, CameraUtil.SHUTTER_SPEED_VALUES.length - 1);
                    m_holyGrailCurrentShutterIndex = m_holyGrailBaseShutterIndex;
                    currentdial = TLS_SET_HOLYGRAIL_MAXISO;
                    showSetupHint();
                    updateHolyGrailMaxIso();
                }
                else
                    confirmAndStart();
                break;

            case TLS_SET_HOLYGRAIL_MAXISO:
                currentdial = TLS_SET_HOLYGRAIL_MAXSHUTTER;
                showSetupHint();
                updateHolyGrailMaxShutter();
                break;

            case TLS_SET_HOLYGRAIL_MAXSHUTTER:
                confirmAndStart();
                break;
        }
        return false;
    }

    private void confirmAndStart()
    {
        Log.d(TAG, "onEnterKeyDown startCountDown");

        m_rampTotalShots = m_timelapsePicCount;
        // Ready to apply a correction on the first shot if needed, not
        // artificially blocked by a settle window left over from a previous run.
        m_holyGrailLastCorrectionTime = 0;
        m_holyGrailEvDiffAtLastCorrection = Float.NaN;
        m_holyGrailStallCount = 0;
        m_holyGrailPreflightAttempts = 0;

        if (m_holyGrailEnabled)
        {
            try
            {
                if (m_supportedIsos == null || m_supportedIsos.isEmpty())
                    throw new IllegalStateException("no supported ISO list");
                if (!isInManualExposureMode())
                    throw new IllegalStateException("not in Manual exposure mode");
                if (CameraInstance.GET().getHistogram() == null)
                    throw new IllegalStateException("no histogram data available");
            }
            catch (RuntimeException e)
            {
                Log.d(TAG, "Holy Grail could not be started, disabling for this run: " + e.getMessage());
                e.printStackTrace();
                m_holyGrailEnabled = false;
                m_holyGrailFailureReason = !isInManualExposureMode()
                        ? "needs Manual exposure mode"
                        : "setup failed (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
                cameraUiInterface.showMessageDelayed("Holy Grail DISABLED: " + m_holyGrailFailureReason);
            }
        }

        s_activeInstance = this;

        if (m_holyGrailEnabled)
        {
            // Get exposure right before the countdown/first shot, not during
            // it. Without this the first several real, kept frames of the
            // timelapse are the ones Holy Grail is still figuring exposure
            // out on -- fine to correct for later in the run when the light
            // is genuinely changing, but wasteful right at the very start
            // when nothing has changed yet and it's purely catching up from
            // "wherever manual settings happened to be left".
            cameraUiInterface.showMessage("Holy Grail: metering... (Delete to cancel)");
            m_holyGrailPreflightActive = true;
            runHolyGrailPreflight();
        }
        else
        {
            beginCountdown();
        }
    }

    private void beginCountdown()
    {
        m_holyGrailPreflightActive = false;
        // Hand the dial listener back to the main UI here, not at the top of
        // confirmAndStart() like every other capture mode does it -- for
        // Holy Grail specifically, this class needs to stay the active
        // listener through the preflight metering pass too, so Delete/Trash
        // can cancel it (see onDeleteKeyUp()). Every other path (no preflight
        // needed) reaches here immediately, so behaves exactly as before.
        cameraUiInterface.getActivityInterface().getDialHandler().setDialEventListner((KeyEvents)cameraUiInterface);
        startCountDown();
        currentdial = TLS_SET_NONE;
    }

    /**
     * Runs the same convergence loop as applyHolyGrailExposureCorrection()
     * uses between shots, but before the countdown/first shot even starts.
     * Keeps calling itself (via postDelayed, using the settle time the
     * correction itself reports needing) until either exposure has converged
     * -- applyHolyGrailExposureCorrection() returns 0 because EVdiff is
     * within the deadzone -- or HOLYGRAIL_MAX_PREFLIGHT_ATTEMPTS is hit,
     * whichever comes first, then proceeds to the real countdown either way.
     */
    private void runHolyGrailPreflight()
    {
        long delay = applyHolyGrailExposureCorrection();
        ++m_holyGrailPreflightAttempts;

        if (delay > 0 && m_holyGrailEnabled && m_holyGrailPreflightAttempts < HOLYGRAIL_MAX_PREFLIGHT_ATTEMPTS)
        {
            if (m_supportedIsos != null && m_holyGrailCurrentIsoPos < m_supportedIsos.size())
                cameraUiInterface.showMessage(String.format("Holy Grail: metering... ISO %d (%d/%d) (Delete to cancel)",
                        m_supportedIsos.get(m_holyGrailCurrentIsoPos), m_holyGrailPreflightAttempts, HOLYGRAIL_MAX_PREFLIGHT_ATTEMPTS));
            cameraUiInterface.getActivityInterface().getMainHandler().postDelayed(m_holyGrailPreflightRunnable, delay);
        }
        else
        {
            if (m_holyGrailPreflightAttempts >= HOLYGRAIL_MAX_PREFLIGHT_ATTEMPTS)
                Log.d(TAG, "Holy Grail: preflight metering hit its attempt limit without fully converging, starting anyway");
            cameraUiInterface.hideMessage();
            beginCountdown();
        }
    }

    @Override
    public boolean onFnKeyDown() {
        return false;
    }

    @Override
    public boolean onFnKeyUp() {
        return false;
    }

    @Override
    public boolean onAelKeyDown() {
        return false;
    }

    @Override
    public boolean onAelKeyUp() {
        return false;
    }

    @Override
    public boolean onMenuKeyDown() {
        return false;
    }

    @Override
    public boolean onMenuKeyUp() {
        return false;
    }

    @Override
    public boolean onFocusKeyDown() {
        return false;
    }

    @Override
    public boolean onFocusKeyUp() {
        return false;
    }

    @Override
    public boolean onShutterKeyDown() {
        return false;
    }

    @Override
    public boolean onShutterKeyUp() {
        return false;
    }

    @Override
    public boolean onPlayKeyDown() {
        return false;
    }

    @Override
    public boolean onPlayKeyUp() {
        return false;
    }

    @Override
    public boolean onMovieKeyDown() {
        return false;
    }

    @Override
    public boolean onMovieKeyUp() {
        return false;
    }

    @Override
    public boolean onC1KeyDown() {
        return false;
    }

    @Override
    public boolean onC1KeyUp() {
        return false;
    }

    @Override
    public boolean onLensAttached() {
        return false;
    }

    @Override
    public boolean onLensDetached() {
        return false;
    }

    @Override
    public boolean onModeDialChanged(int value) {
        return false;
    }

    @Override
    public boolean onZoomTeleKey() {
        return false;
    }

    @Override
    public boolean onZoomWideKey() {
        return false;
    }

    @Override
    public boolean onZoomOffKey() {
        return false;
    }

    @Override
    public boolean onDeleteKeyDown() {
        return false;
    }

    @Override
    public boolean onDeleteKeyUp() {
        // Only meaningful during the preflight metering pass -- this class
        // isn't the active dial listener at any other point where Delete
        // could reach it (the main UI owns that during the countdown and
        // actual shooting; see toggle()/abort() for how those get cancelled
        // instead). See the field comment on m_holyGrailPreflightActive.
        if (m_holyGrailPreflightActive)
        {
            Log.d(TAG, "Holy Grail preflight cancelled by user");
            abort();
            return true;
        }
        return false;
    }

    @Override
    public void onCaptureDone() {
        Log.d(TAG,"onCaptureDone");
        ++m_timelapsePicsTaken;
        if (m_timelapsePicCount < 0 || m_timelapsePicCount == 1) {
            abort();
            cameraUiInterface.getActivityInterface().setCaptureDoneEventListner(null);
            Log.d(TAG, "abort Timelapse");
        }
        else
        {
            if (m_timelapsePicCount != 0)
                --m_timelapsePicCount;

            // Live Holy Grail status, shown every shot regardless of interval
            // length -- this is the fastest way to tell, just by watching the
            // screen, whether it's actually doing anything: whether setup
            // actually succeeded, whether ISO/shutter are actually changing
            // shot to shot. If setup failed, m_holyGrailFailureReason (set in
            // confirmAndStart()'s catch block) is shown here instead,
            // continuously for the whole run -- a one-shot showMessageDelayed()
            // there isn't enough, since startShooting() calls hideMessage() on
            // every shot including the very first one, wiping out a one-shot
            // message before there's any real chance to read it.
            String holyGrailStatus;
            if (m_holyGrailEnabled && m_supportedIsos != null && m_holyGrailCurrentIsoPos < m_supportedIsos.size())
                holyGrailStatus = String.format(" | HG ISO:%d shutter:%s mean:%.2f EVdiff:%.2f settle:%dms/%dms", m_supportedIsos.get(m_holyGrailCurrentIsoPos),
                        CameraUtil.SHUTTER_SPEED_VALUES[m_holyGrailCurrentShutterIndex].getShutterSpeed(), m_holyGrailLastMeanBrightness, m_holyGrailLastEvDiff,
                        Math.min(System.currentTimeMillis() - m_holyGrailLastCorrectionTime, HOLYGRAIL_SETTLE_MS), HOLYGRAIL_SETTLE_MS);
            else if (m_holyGrailFailureReason != null)
                holyGrailStatus = " | HG DISABLED: " + m_holyGrailFailureReason;
            else
                holyGrailStatus = "";

            if (m_timelapseInterval >= 1000 || m_holyGrailEnabled || m_holyGrailFailureReason != null)
            {
                if (m_timelapsePicCount > 0)
                    cameraUiInterface.showMessageDelayed(String.format("%d pictures remaining", m_timelapsePicCount) + holyGrailStatus);
                else
                    cameraUiInterface.showMessageDelayed(String.format("%d pictures taken", m_timelapsePicsTaken) + holyGrailStatus);
            }

            if (m_rampIntervalEnabled && m_rampTotalShots > 0)
            {
                // Linear ramp from the start interval to the end interval,
                // driven by fraction of planned shots taken so far. This is
                // computed every shot (not just once) so it stays correct even
                // if decrementPicCount()/incrementPicCount() were never touched
                // mid-run (they aren't reachable once shooting starts anyway,
                // but this keeps the math self-contained regardless).
                float fraction = Math.min(1f, (float) m_timelapsePicsTaken / (float) m_rampTotalShots);
                m_timelapseInterval = Math.round(m_timelapseIntervalStart
                        + (m_timelapseIntervalEnd - m_timelapseIntervalStart) * fraction);
                Log.d(TAG, "Ramping interval to " + m_timelapseInterval + "ms (" + (fraction * 100) + "% through)");
            }

            if (m_timelapseInterval == 0) {
                startShooting();
                return;
            }

            // Drift-corrected scheduling: measure the wait from the last shot's
            // actual start time, not from "now" (which has already drifted by
            // however long this shot took to capture and process). Without this,
            // each shot's processing time silently adds onto the requested
            // interval and shots creep later and later over a long timelapse --
            // that's the "fixed delay" problem the original timelapse had.
            long remaining = m_lastShotTime + m_timelapseInterval - System.currentTimeMillis();
            Log.d(TAG, "next Capture in " + remaining + "ms (requested interval " + m_timelapseInterval + "ms)");

            if (remaining <= 0)
            {
                // Capture + processing already took longer than the requested
                // interval -- shoot again right away instead of waiting a full
                // extra interval on top of the overrun.
                cameraUiInterface.getActivityInterface().getMainHandler().post(m_timelapseRunnable);
                return;
            }

            // Screen off for the bulk of the wait, waking a couple of seconds
            // before the next shot so there's a moment to see the status message
            // and the camera can settle before triggering. wakeDisplay() is also
            // reachable instantly from any key press via wakeDisplayIfOff().
            final long wakeMarginMs = 2000;
            if (remaining > wakeMarginMs)
            {
                turnScreenOff();
                cameraUiInterface.getActivityInterface().getMainHandler().postDelayed(m_wakeDisplayRunnable, remaining - wakeMarginMs);
            }
            cameraUiInterface.getActivityInterface().getMainHandler().postDelayed(m_timelapseRunnable, remaining);
        }
    }
}
