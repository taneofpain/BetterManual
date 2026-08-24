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

public class CaptureModeTimelapse extends CaptureMode implements KeyEvents, CaptureSession.CaptureDoneEvent, CameraEx.ExposureCompleteListener
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
    // transition) this lets the camera's own native Auto ISO metering track
    // the change up to a user-set ISO ceiling. If Auto ISO alone isn't enough
    // (peaks at the ceiling and the shot is still under-exposed), the shutter
    // speed itself is gradually extended as a second stage, up to a separate
    // user-set shutter-speed ceiling. Both ceilings are real, documented Sony
    // camera parameters (ISOAutoMax is the same "ISO AUTO Max" the camera's own
    // menus expose) -- the camera does the actual metering; this only fences it
    // in and extends the shutter once Auto ISO runs out of room. Shutter speed
    // never goes faster than whatever was manually set going into the
    // timelapse -- only slower, and only up to the configured ceiling.
    private boolean         m_holyGrailEnabled;
    private List<Integer>  m_supportedIsos;
    private int             m_holyGrailMaxIsoPos;      // index into m_supportedIsos
    private int             m_holyGrailMaxShutterIndex; // index into CameraUtil.SHUTTER_SPEED_VALUES
    private int             m_holyGrailBaseShutterIndex;
    private int             m_holyGrailOriginalIso;
    // Once Auto ISO reports it's within this fraction of the ceiling, treat it
    // as "maxed out" and start extending the shutter instead. Sony's Auto ISO
    // doesn't necessarily hit the ceiling exactly, so this gives it a margin.
    private static final float HOLYGRAIL_ISO_CEILING_MARGIN = 0.92f;
    // Once Auto ISO drops back below this fraction of the ceiling, it's safe to
    // start bringing the (previously extended) shutter speed back down again
    // -- e.g. recovering during a sunrise rather than only handling sunset.
    private static final float HOLYGRAIL_ISO_RECOVERY_MARGIN = 0.6f;

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
                hint = cameraUiInterface.getActivityInterface().getResString(R.string.icon_lowerDial) + " to enable/disable, Holy Grail exposure ramp: " + (m_holyGrailEnabled ? "ON" : "OFF");
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
        m_lastShotTime = System.currentTimeMillis();
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
        CameraInstance.GET().takePicture();
    }

    @Override
    public void abort() {
        cameraUiInterface.getActivityInterface().getMainHandler().removeCallbacks(m_countDownRunnable);
        cameraUiInterface.getActivityInterface().getMainHandler().removeCallbacks(m_timelapseRunnable);
        cameraUiInterface.getActivityInterface().getMainHandler().removeCallbacks(m_wakeDisplayRunnable);
        isActive = false;
        cameraUiInterface.showMessageDelayed("Timelapse finished");
        CameraInstance.GET().enableHwShutterButton();
        CameraInstance.GET().startPreview();

        if (m_holyGrailEnabled)
        {
            try
            {
                CameraInstance.GET().setExposureCompleteListener(null);
                // Restore whatever ISO was actually set before Holy Grail
                // switched the camera to Auto ISO, so the user isn't left in
                // Auto silently.
                CameraInstance.GET().setISOSensitivity(m_holyGrailOriginalIso);
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
                    m_holyGrailMaxIsoPos = (m_supportedIsos != null && !m_supportedIsos.isEmpty())
                            ? m_supportedIsos.size() - 1 : 1;
                    m_holyGrailBaseShutterIndex = CameraUtil.getShutterValueIndex(CameraInstance.GET().getShutterSpeed());
                    if (m_holyGrailBaseShutterIndex < 0)
                        m_holyGrailBaseShutterIndex = 0;
                    m_holyGrailMaxShutterIndex = Math.min(m_holyGrailBaseShutterIndex + 10, CameraUtil.SHUTTER_SPEED_VALUES.length - 1);
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
        Log.d(TAG, "onEnterKeyDown setDefaultDialListner");
        cameraUiInterface.getActivityInterface().getDialHandler().setDialEventListner((KeyEvents)cameraUiInterface);
        Log.d(TAG, "onEnterKeyDown startCountDown");

        m_rampTotalShots = m_timelapsePicCount;

        if (m_holyGrailEnabled)
        {
            // setISOAutoMax/setExposureCompleteListener are far less universally
            // implemented than the auto-power-off calls above, which is exactly
            // why those are wrapped in try/catch already -- these need the same
            // treatment. Without it, a camera model whose firmware doesn't
            // expose these throws NoSuchMethodError here, uncaught, crashing
            // the app the instant Holy Grail confirms and starts. Also guard
            // against m_supportedIsos somehow being null -- getSupportedISOSensitivities()
            // isn't guaranteed non-null, and .get() on a null list is an
            // immediate NullPointerException.
            try
            {
                if (m_supportedIsos == null || m_supportedIsos.isEmpty())
                    throw new IllegalStateException("no supported ISO list");
                m_holyGrailOriginalIso = CameraInstance.GET().getISOSensitivity();
                // Switch to Auto ISO so the camera's own real-time metering
                // tracks the changing light; we only fence in how far it can go.
                CameraInstance.GET().setISOSensitivity(0);
                CameraInstance.GET().setISOAutoMax(m_supportedIsos.get(m_holyGrailMaxIsoPos));
                CameraInstance.GET().setExposureCompleteListener(this);
            }
            catch (NoSuchMethodError | RuntimeException e)
            {
                Log.d(TAG, "Holy Grail not supported on this camera, disabling for this run");
                e.printStackTrace();
                m_holyGrailEnabled = false;
                cameraUiInterface.showMessageDelayed("Holy Grail not supported on this camera");
                // If ISO was already switched to Auto before the failure hit,
                // put it back -- otherwise abort()'s restore step below is
                // skipped (it's gated on m_holyGrailEnabled, now false) and the
                // camera would be left stuck in Auto ISO.
                try
                {
                    CameraInstance.GET().setISOSensitivity(m_holyGrailOriginalIso);
                }
                catch (RuntimeException ex)
                {
                    ex.printStackTrace();
                }
            }
        }

        s_activeInstance = this;
        startCountDown();
        currentdial = TLS_SET_NONE;
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
        return false;
    }

    /**
     * CameraEx.ExposureCompleteListener -- fires after every completed
     * exposure with the settings the camera actually used. Only meaningful
     * while Holy Grail is active (it's only registered in confirmAndStart()
     * and unregistered in abort()). This is the second stage of Holy Grail:
     * Auto ISO (set up in confirmAndStart()) handles exposure tracking on its
     * own for as long as it can; this only steps in once Auto ISO reports it's
     * pinned near its ceiling and the shot still needed more exposure, at which
     * point it starts gradually slowing the shutter speed instead. It also
     * recovers the shutter back toward the original speed if conditions
     * brighten again (e.g. mid-sequence during a sunrise rather than sunset).
     *
     * One caveat I can't fully verify without testing on real hardware: exactly
     * how promptly/precisely Auto ISO settles per shot on this body. The step
     * size here is deliberately small (one shutter-table step per shot) so
     * even if it reacts a shot or two later than ideal, it won't visibly jump.
     */
    @Override
    public void onDone(int i, CameraEx.ExposureInfo info, CameraEx camera)
    {
        if (!m_holyGrailEnabled || m_supportedIsos == null || m_supportedIsos.isEmpty())
            return;

        int maxIso = m_supportedIsos.get(m_holyGrailMaxIsoPos);
        int currentShutterIndex = CameraUtil.getShutterValueIndex(CameraInstance.GET().getShutterSpeed());
        if (currentShutterIndex < 0)
            return; // couldn't match the current shutter speed to our table -- don't guess

        if (info.IsoSpeedRate >= maxIso * HOLYGRAIL_ISO_CEILING_MARGIN
                && currentShutterIndex < m_holyGrailMaxShutterIndex)
        {
            // Auto ISO is maxed out and the shot still needed more exposure --
            // buy one more shutter-table step of exposure for next time.
            Log.d(TAG, "Holy Grail: ISO " + info.IsoSpeedRate + " near ceiling " + maxIso + ", extending shutter");
            CameraInstance.GET().adjustShutterSpeed(-1);
        }
        else if (info.IsoSpeedRate < maxIso * HOLYGRAIL_ISO_RECOVERY_MARGIN
                && currentShutterIndex > m_holyGrailBaseShutterIndex)
        {
            // Conditions brightened enough that Auto ISO has comfortable
            // headroom again -- bring the shutter back down a step, but never
            // faster than what was manually set going into the timelapse.
            Log.d(TAG, "Holy Grail: ISO " + info.IsoSpeedRate + " has headroom, recovering shutter");
            CameraInstance.GET().adjustShutterSpeed(1);
        }
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
            if (m_timelapseInterval >= 1000)
            {
                if (m_timelapsePicCount > 0)
                    cameraUiInterface.showMessageDelayed(String.format("%d pictures remaining", m_timelapsePicCount));
                else
                    cameraUiInterface.showMessageDelayed(String.format("%d pictures taken", m_timelapsePicsTaken));
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
