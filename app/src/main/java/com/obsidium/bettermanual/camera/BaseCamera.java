package com.obsidium.bettermanual.camera;

import android.hardware.Camera;
import android.util.Log;
import android.util.Pair;

import com.sony.scalar.hardware.CameraEx;

import java.util.List;

/**
 * Created by KillerInk on 30.08.2017.
 */

public class BaseCamera implements CameraEventListnerInterface, CameraParameterInterface {

    private static final String TAG = BaseCamera.class.getSimpleName();

    public interface CameraEvents{
        void onCameraOpen(boolean isOpen);
    }



    CameraEx.AutoPictureReviewControl autoPictureReviewControl;
    CameraEvents cameraEventsListner;
    CameraEx.ShutterSpeedInfo shutterSpeedInfo;
    CameraEx m_camera;
    protected boolean cameraIsOpen = false;

    CameraEx.PreviewAnalizeListener previewAnalizeListener;

    CameraEx.FocusDriveListener focusDriveListener;
    CameraEx.PreviewMagnificationListener previewMagnificationListener;
    CameraEx.FocusLightStateListener focusLightStateListener;
    CameraEx.SettingChangedListener settingChangedListener;

    public CameraEx getCameraEx()
    {
        return m_camera;
    }


    public CameraEx.AutoPictureReviewControl getAutoPictureReviewControls()
    {
        return autoPictureReviewControl;
    }

    @Override
    public void setPreviewAnalizeListener(CameraEx.PreviewAnalizeListener previewAnalizeListener)
    {
        this.previewAnalizeListener = previewAnalizeListener;
    }

    @Override
    public void setFocusDriveListener(CameraEx.FocusDriveListener focusDriveListener)
    {
       this.focusDriveListener = focusDriveListener;
    }

    @Override
    public void setPreviewMagnificationListener(CameraEx.PreviewMagnificationListener previewMagnificationListener)
    {
        this.previewMagnificationListener = previewMagnificationListener;
        m_camera.setPreviewMagnificationListener(previewMagnificationListener);
    }


    @Override
    public void setCameraEventsListner(CameraEvents eventsListner)
    {
        this.cameraEventsListner = eventsListner;
    }

    @Override
    public void fireOnCameraOpen(boolean isopen)
    {
        if (cameraEventsListner != null)
        {
            cameraEventsListner.onCameraOpen(true);
        }
    }

    @Override
    public void setShutterListener(CameraEx.ShutterListener shutterListener) {
        m_camera.setShutterListener(shutterListener);
    }

    @Override
    public CameraEx.ShutterSpeedInfo getShutterSpeedInfo()
    {
        if (shutterSpeedInfo == null) {
            shutterSpeedInfo = new CameraEx.ShutterSpeedInfo();
            CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(getParameters());
            Pair<Integer, Integer> p = modifier.getShutterSpeed();
            shutterSpeedInfo.currentShutterSpeed_d = p.first;
            shutterSpeedInfo.currentShutterSpeed_n = p.second;

        }
        return shutterSpeedInfo;
    }

    protected Camera.Parameters getParameters()
    {
        return m_camera.getNormalCamera().getParameters();
    }

    protected CameraEx.ParametersModifier getModifier()
    {
        return m_camera.createParametersModifier(getParameters());
    }


    protected Camera.Parameters getEmptyParameters()
    {
        return m_camera.createEmptyParameters();
    }



    protected void setParameters(Camera.Parameters parameters)
    {
        m_camera.getNormalCamera().setParameters(parameters);
    }

    public int getExposureCompensation() {
        return getParameters().getExposureCompensation();
    }

    public void setExposureCompensation(int value) {

        Camera.Parameters parameters = getEmptyParameters();
        parameters.setExposureCompensation(value);
        setParameters(parameters);
    }

    public int getMaxExposureCompensation() {
        return getParameters().getMaxExposureCompensation();
    }

    public int getMinExposureCompensation() {
        return getParameters().getMinExposureCompensation();
    }

    public float getExposureCompensationStep() {
        float ret = 0;
        try {
            ret = getParameters().getExposureCompensationStep();
        }
        catch (NullPointerException ex)
        {
            ex.printStackTrace();
        }
        return ret;
    }

    public boolean isLongExposureNoiseReductionSupported()
    {
        try {
            getModifier().getLongExposureNR();
            return true;
        }
        catch (NoSuchMethodError ex)
        {
            ex.printStackTrace();
            return false;
        }
    }

    public void setLongExposureNoiseReduction(boolean enable)
    {
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = getCameraEx().createParametersModifier(parameters);
        modifier.setLongExposureNR(enable);
        setParameters(parameters);
    }


    @Override
    public boolean getLongeExposureNR() {
        return getModifier().getLongExposureNR();
    }

    public void setFocusMode(String value)
    {
        Log.d(TAG, "setFocusmode:" +value);
        Camera.Parameters parameters = getEmptyParameters();
        parameters.setFocusMode(value);
        setParameters(parameters);
    }

    public void setSceneMode(String value)
    {
        Log.d(TAG, "setSceneMode:" +value);
        Camera.Parameters parameters = getEmptyParameters();
        parameters.setSceneMode(value);
        setParameters(parameters);
    }

    public String getSceneMode()
    {
        return getParameters().getSceneMode();
    }

    // Native lens identification, for a real Sony/E-mount lens with
    // electronic contacts (as opposed to a manual legacy lens, which has no
    // way to communicate with the body at all -- that's the whole reason
    // the separate legacy-lens profile system exists). Confirmed real API,
    // though a mix of V7/V14 vintage fields, so still worth guarding at the
    // call site rather than assuming every field is populated on every body.
    public com.sony.scalar.hardware.CameraEx.LensInfo getLensInfo()
    {
        return m_camera.getLensInfo();
    }

    // Real, in-camera cropping -- not the aspect-ratio guide overlay. Only
    // some ratios have a native equivalent here (1:1 and 16:9, out of the
    // guide's full list); the rest (4:5, 6:7, 21:9, 65:24) have no matching
    // constant at all and can only ever be a compositional guide. This is a
    // "V1" framework API, the oldest/most broadly-implemented tier, so it's
    // a reasonable bet for actually being supported -- unlike some of the
    // newer APIs used elsewhere in this app that turned out not to be.
    public java.util.List<String> getSupportedImageAspectRatios()
    {
        return getModifier().getSupportedImageAspectRatios();
    }

    public String getImageAspectRatio()
    {
        return getModifier().getImageAspectRatio();
    }

    public void setDriveMode(String value)
    {
        Log.d(TAG, "setDriveMode:" +value);
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = getCameraEx().createParametersModifier(parameters);
        modifier.setDriveMode(value);
        setParameters(parameters);
    }

    public String getDriveMode()
    {
        return getModifier().getDriveMode();
    }

    public void setImageAspectRatio(String value)
    {
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = getCameraEx().createParametersModifier(parameters);
        modifier.setImageAspectRatio(value);
        setParameters(parameters);
    }

    public void setImageQuality(String value)
    {
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(parameters);
        modifier.setPictureStorageFormat(value);
        setParameters(parameters);
    }

    public void setBurstDriveSpeed(String value)
    {
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(parameters);
        modifier.setBurstDriveSpeed(value);
        setParameters(parameters);
    }

    public String getBurstDriveSpeed()
    {
        return getModifier().getBurstDriveSpeed();
    }

    public boolean isAutoShutterSpeedLowLimitSupported()
    {
        return getModifier().isSupportedAutoShutterSpeedLowLimit();
    }

    public void setAutoShutterSpeedLowLimit(int value)
    {
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(parameters);
        modifier.setAutoShutterSpeedLowLimit(value);
        setParameters(parameters);
    }

    public int getAutoShutterSpeedLowLimit()
    {
        return getModifier().getAutoShutterSpeedLowLimit();
    }

    public void setSelfTimer(int value)
    {
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(parameters);
        modifier.setSelfTimer(value);
        setParameters(parameters);
    }

    public List<Integer> getSupportedISOSensitivities()
    {
        return getModifier().getSupportedISOSensitivities();
    }

    public int getISOSensitivity()
    {
        return getModifier().getISOSensitivity();
    }

    public void setISOSensitivity(int value)
    {
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(parameters);
        modifier.setISOSensitivity(value);
        setParameters(parameters);
    }

    // Used by the timelapse's Holy Grail mode: caps how far the camera's own
    // native Auto ISO metering is allowed to raise ISO when ISO is set to Auto
    // (0). This is a real, documented Sony camera parameter (same one exposed
    // in the camera's own menus as "ISO AUTO Max"), not something reimplemented
    // in this app -- the camera's own metering does the actual exposure
    // tracking, we're just fencing it in.
    public int getISOAutoMax()
    {
        return getModifier().getISOAutoMax();
    }

    public void setISOAutoMax(int value)
    {
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(parameters);
        modifier.setISOAutoMax(value);
        setParameters(parameters);
    }

    // Fires after every completed exposure with the actual settings the camera
    // used. Used by the timelapse's Holy Grail mode as the trigger point to
    // request a fresh exposure reading (see getProperExposureLevel() below).
    public void setExposureCompleteListener(CameraEx.ExposureCompleteListener listener)
    {
        m_camera.setExposureCompleteListener(listener);
    }

    // The a5100 doesn't implement the ISOAutoMax/AutoISOSensitivityListener
    // pair (confirmed via NoSuchMethodError on real hardware), even though the
    // same Auto-ISO-with-ceiling capability exists in the camera's own menus --
    // it just isn't exposed through this API on this body. This is the
    // fallback used instead: request a metered "how far off is the current
    // exposure" reading, delivered asynchronously to the registered callback.
    // This is a V3 framework API (vs. ISOAutoMax/ExposureCompleteListener being
    // V6+), so it's a reasonable bet for being more broadly supported, but
    // that's not guaranteed either -- callers should still handle it failing.
    public void getProperExposureLevel()
    {
        m_camera.getProperExposureLevel();
    }

    public void setProperExposureLevelCallback(CameraEx.ProperExposureLevelCallback callback)
    {
        m_camera.setProperExposureLevelCallback(callback);
    }

    public void setPreviewMagnification(int factor, Pair position)
    {
        m_camera.setPreviewMagnification(factor, position);
    }

    @Override
    public void stopPreviewMagnification() {
        m_camera.stopPreviewMagnification();
    }

    public List<Integer> getSupportedPreviewMagnification() {
        return getModifier().getSupportedPreviewMagnification();
    }

    public void decrementShutterSpeed(){
        m_camera.decrementShutterSpeed();
    }
    public void incrementShutterSpeed()
    {
        m_camera.incrementShutterSpeed();
    }

    public void decrementAperture(){
        m_camera.decrementAperture();
    }

    public void incrementAperture(){
        m_camera.incrementAperture();
    }


    public int getAperture() {
        return getModifier().getAperture();
    }

    @Override
    public boolean isImageStabSupported() {
        try {
            getModifier().getAntiHandBlurMode();

            return true;
        }
        catch (NoSuchMethodError ex) {
            ex.printStackTrace();
            return false;
        }
    }

    @Override
    public String getImageStabilisationMode() {
        return getModifier().getAntiHandBlurMode();
    }

    @Override
    public void setImageStabilisation(String enable) {
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(parameters);
        modifier.setAntiHandBlurMode(enable);
        setParameters(parameters);
    }

    @Override
    public List<String> getSupportedImageStabModes() {

        return getModifier().getSupportedAntiHandBlurModes();
    }

    @Override
    public boolean isLiveSlowShutterSupported() {
        try {
            return getModifier().isSupportedSlowShutterLiveviewMode();
        }
        catch (NoSuchMethodError ex)
        {
            ex.printStackTrace();
            return false;
        }
    }

    public void setLiveSlowShutter(String liveSlowShutter)
    {
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(parameters);
        modifier.setSlowShutterLiveviewMode(liveSlowShutter);
        setParameters(parameters);
    }

    @Override
    public String getLiveSlowShutter() {
        return getModifier().getSlowShutterLiveviewMode();
    }

    @Override
    public String[] getSupportedLiveSlowShutterModes() {
        return new String[] { getModifier().SLOW_SHUTTER_LIVEVIEW_MODE_OFF,getModifier().SLOW_SHUTTER_LIVEVIEW_MODE_ON};
    }



    public Pair getShutterSpeed()
    {
        return getModifier().getShutterSpeed();
    }

    public void adjustShutterSpeed(int val)
    {
        m_camera.adjustShutterSpeed(val);
    }


    public void setFocusPosition(int pos)
    {
        if (pos < 0)
            m_camera.startOneShotFocusDrive(CameraEx.FOCUS_DRIVE_DIRECTION_NEAR,pos*-1);
        else
            m_camera.startOneShotFocusDrive(CameraEx.FOCUS_DRIVE_DIRECTION_FAR,pos);
    }

    public void setRedEyeReduction(String enable)
    {
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(parameters);
        modifier.setRedEyeReductionMode(enable);
        setParameters(parameters);
    }

    public void setFlashMode(String enable)
    {
        Camera.Parameters parameters = getEmptyParameters();
        parameters.setFlashMode(enable);
        setParameters(parameters);
    }

    public void setFlashType(String enable)
    {
        Camera.Parameters parameters = getEmptyParameters();
        CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(parameters);
        modifier.setFlashType(enable);
        setParameters(parameters);
    }


    //returns always [0,0,0] when used with mf, dont know if its works with af
    /*public float[]getFocusDistances()
    {
        Camera.Parameters parameters = m_camera.getNormalCamera().getParameters();
        float ar[] = new float[3];
        parameters.getFocusDistances(ar);
        return ar;
    }*/
}
