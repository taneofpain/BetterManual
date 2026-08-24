package com.obsidium.bettermanual.camera;

import android.hardware.Camera;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;

import com.obsidium.bettermanual.Preferences;
import com.obsidium.bettermanual.controller.ApertureController;
import com.obsidium.bettermanual.controller.BatteryObserverController;
import com.obsidium.bettermanual.controller.DriveModeController;
import com.obsidium.bettermanual.controller.ExposureCompensationController;
import com.obsidium.bettermanual.controller.ExposureHintController;
import com.obsidium.bettermanual.controller.ExposureModeController;
import com.obsidium.bettermanual.controller.FocusDriveController;
import com.obsidium.bettermanual.controller.HistogramController;
import com.obsidium.bettermanual.controller.ImageStabilisationController;
import com.obsidium.bettermanual.controller.IsoController;
import com.obsidium.bettermanual.controller.LongExposureNoiseReductionController;
import com.obsidium.bettermanual.controller.ShutterController;
import com.obsidium.bettermanual.model.ApertureModel;
import com.obsidium.bettermanual.model.BatteryObserverModel;
import com.obsidium.bettermanual.model.DriveModeModel;
import com.obsidium.bettermanual.model.ExposureCompensationModel;
import com.obsidium.bettermanual.model.ExposureHintModel;
import com.obsidium.bettermanual.model.ExposureModeModel;
import com.obsidium.bettermanual.model.FocusDriveModel;
import com.obsidium.bettermanual.model.HistogramModel;
import com.obsidium.bettermanual.model.ImageStabilisationModel;
import com.obsidium.bettermanual.model.IsoModel;
import com.obsidium.bettermanual.model.LongExposureNoiseReductionModel;
import com.obsidium.bettermanual.model.ShutterModel;
import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.hardware.CameraSequence;
import com.sony.scalar.meta.FaceInfo;

import java.util.List;
import java.util.StringTokenizer;


public class CameraInstance extends BaseCamera implements  CameraSequence.ShutterSequenceCallback   {
    private final String TAG = CameraInstance.class.getSimpleName();

//    public CameraSequence cameraSequence;

    final int SETSURFACE = 0;
    private static CameraInstance INSTANCE = new CameraInstance();
    private SurfaceHolder surfaceHolder;
    private ApertureModel apertureModel;
    private ShutterModel shutterModel;
    private IsoModel isoModel;
    private ExposureCompensationModel exposureCompensationModel;
    private ExposureHintModel exposureHintModel;
    private ExposureModeModel exposureModeModel;
    private DriveModeModel driveModeModel;
    private ImageStabilisationModel imageStabilisationModel;
    private LongExposureNoiseReductionModel longExposureNoiseReductionModel;
    private HistogramModel histogramModel;
    private FocusDriveModel focusDriveModel;
    private BatteryObserverModel batteryObserverModel;


    private CameraInstance() {
        super();
    }

    public static CameraInstance GET()
    {
        return INSTANCE;
    }

    public void startCamera() {
/*        CameraEx.OpenOptions options = new CameraEx.OpenOptions();
        options.setPreview(true);*/
        Log.d(TAG, "Open Cam");
        try {
            m_camera = CameraEx.open(0, null);
        }
        catch (RuntimeException ex) {
            // If the native open fails (camera busy, hardware not ready yet, etc.)
            // m_camera stays null and we bail out cleanly instead of crashing the
            // whole process. closeCamera() already tolerates m_camera being null.
            Log.d(TAG, "startCamera failed");
            ex.printStackTrace();
            cameraIsOpen = false;
            return;
        }
        cameraIsOpen = true;
        /*cameraSequence = CameraSequence.open(m_camera);
        setOptions(null);
        cameraSequence.setShutterSequenceCallback(this);*/
        Log.d(TAG, "Cam open");
        initCamera();
        fireOnCameraOpen(true);

    }

    public void initParameters()
    {



        apertureModel = new ApertureModel(this);
        m_camera.setApertureChangeListener(apertureModel);
        ApertureController.GetInstance().bindModel(apertureModel);

        shutterModel = new ShutterModel(this);
        m_camera.setShutterSpeedChangeListener(shutterModel);
        ShutterController.GetInstance().bindModel(shutterModel);

        isoModel = new IsoModel(this);
        m_camera.setAutoISOSensitivityListener(isoModel);
        IsoController.GetInstance().bindModel(isoModel);

        exposureCompensationModel = new ExposureCompensationModel(this);
        ExposureCompensationController.GetInstance().bindModel(exposureCompensationModel);

        exposureHintModel = new ExposureHintModel(this);
        m_camera.setProgramLineRangeOverListener(exposureHintModel);
        ExposureHintController.GetInstance().bindModel(exposureHintModel);

        exposureModeModel = new ExposureModeModel(this);
        ExposureModeController.GetInstance().bindModel(exposureModeModel);

        driveModeModel = new DriveModeModel(this);
        DriveModeController.GetInstance().bindModel(driveModeModel);

        if (isImageStabSupported()) {
            imageStabilisationModel = new ImageStabilisationModel(this);
            ImageStabilisationController.GetInstance().bindModel(imageStabilisationModel);
        }

        if (isLongExposureNoiseReductionSupported())
        {
            longExposureNoiseReductionModel = new LongExposureNoiseReductionModel(this);
            LongExposureNoiseReductionController.GetIntance().bindModel(longExposureNoiseReductionModel);
        }

        focusDriveModel = new FocusDriveModel(this);
        m_camera.setFocusDriveListener(focusDriveModel);
        FocusDriveController.GetInstance().bindModel(focusDriveModel);

        histogramModel = new HistogramModel(this);
        m_camera.setPreviewAnalizeListener(histogramModel);
        HistogramController.GetInstance().bindModel(histogramModel);

        batteryObserverModel = new BatteryObserverModel(this);
        BatteryObserverController.GetInstance().bindModel(batteryObserverModel);
        batteryObserverModel.start();

        applySettings();
        //dumpParameter();
    }

    private void applySettings() {
        setFocusMode(CameraEx.ParametersModifier.FOCUS_MODE_MANUAL);
        final String sceneMode = Preferences.GET().getSceneMode();
        setSceneMode(sceneMode);
        setDriveMode(Preferences.GET().getDriveMode());
        setBurstDriveSpeed(Preferences.GET().getBurstDriveSpeed());
        // Minimum shutter speed
        if(isAutoShutterSpeedLowLimitSupported()) {
            if (sceneMode.equals(CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE))
                setAutoShutterSpeedLowLimit(-1);
            else
                setAutoShutterSpeedLowLimit(Preferences.GET().getMinShutterSpeed());
        }
        // Disable self timer
        setSelfTimer(0);
        // Force aspect ratio to 3:2
        setImageAspectRatio(CameraEx.ParametersModifier.IMAGE_ASPECT_RATIO_3_2);
        setImageQuality(CameraEx.ParametersModifier.PICTURE_STORAGE_FMT_RAW);
        setRedEyeReduction(CameraEx.ParametersModifier.RED_EYE_REDUCTION_MODE_OFF);
        setFlashType(CameraEx.ParametersModifier.FLASH_TYPE_SLOW_SYNC);
        setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        m_camera.setFlashChargingStateListener(new CameraEx.FlashChargingStateListener() {
            @Override
            public void onChanged(int i, CameraEx cameraEx) {
                Log.d(TAG,"FlashChargingStateChanged" + i);
            }
        });
        m_camera.setFlashEmittingListener(new CameraEx.FlashEmittingListener() {
            @Override
            public void onFlash(boolean b, CameraEx cameraEx) {
                Log.d(TAG,"FlashEmittingListner changed onFlash " + b);
            }
        });
        m_camera.setFocusLightStateListener(new CameraEx.FocusLightStateListener() {
            @Override
            public void onChanged(boolean b, boolean b1, CameraEx cameraEx) {
                Log.d(TAG,"FocusLightstateChanger onChange " + b + " " + b1);
            }
        });
        /*try {
            m_camera.setCaptureStatusListener(new CameraEx.OnCaptureStatusListener() {
                @Override
                public void onEnd(int i, int i1, CameraEx cameraEx) {
                    Log.d(TAG, "onCaptureStatusListner END");
                }

                @Override
                public void onStart(int i, CameraEx cameraEx) {
                    Log.d(TAG, "onCaptureStatusListner Start");
                }
            });
        }
        catch (NoClassDefFoundError ex)
        {
            ex.printStackTrace();
        }*/
        m_camera.setFaceDetectionListener(new CameraEx.FaceDetectionListener() {
            @Override
            public void onFaceDetected(FaceInfo[] faceInfos, CameraEx cameraEx) {
                Log.d(TAG,"onFaceDetected");
            }
        });
    }

    public void closeCamera() {
        // If the camera never finished opening (e.g. onPause() lands before the
        // posted startCamera() runnable from surfaceCreated() has run), m_camera
        // and everything below it is still null. Every model field is null-checked
        // individually further down, but m_camera itself never was, so this used to
        // throw an uncaught NPE here -- crashing the whole process and leaving the
        // battery BroadcastReceiver registered (never reaching batteryObserverModel
        // .stop() below). That crash is what looks like the app "restarting itself":
        // the OS auto-relaunches it per the resume_key policy in notifyAppInfo().
        if (m_camera == null) {
            cameraIsOpen = false;
            return;
        }

        cameraIsOpen = false;
        Log.d(TAG, "closeCamera");

        if (exposureModeModel != null)
            Preferences.GET().setSceneMode(exposureModeModel.getStringValue());
        // Drive mode and burst speed
        if (driveModeModel != null)
            Preferences.GET().setDriveMode(driveModeModel.getValue());
        try {
            Preferences.GET().setBurstDriveSpeed(getBurstDriveSpeed());
        }
        catch (NullPointerException ex)
        {
            ex.printStackTrace();
        }

        ApertureController.GetInstance().bindModel(null);
        m_camera.setApertureChangeListener(null);
        apertureModel = null;

        ShutterController.GetInstance().bindModel(null);
        m_camera.setShutterSpeedChangeListener(null);
        shutterModel = null;

        IsoController.GetInstance().bindModel(null);
        m_camera.setAutoISOSensitivityListener(null);
        isoModel = null;

        ExposureCompensationController.GetInstance().bindModel(null);
        exposureCompensationModel = null;

        ExposureHintController.GetInstance().bindModel(null);
        exposureHintModel = null;

        ExposureModeController.GetInstance().bindModel(null);
        exposureModeModel = null;

        DriveModeController.GetInstance().bindModel(null);
        driveModeModel = null;

        HistogramController.GetInstance().bindModel(null);
        m_camera.setPreviewAnalizeListener(null);
        histogramModel = null;

        FocusDriveController.GetInstance().bindModel(null);
        m_camera.setFocusDriveListener(null);
        focusDriveModel = null;

        batteryObserverModel.stop();
        BatteryObserverController.GetInstance().bindModel(null);
        batteryObserverModel = null;

        /*cameraSequence.setShutterSequenceCallback(null);
        cameraSequence.release();*/
        m_camera.getNormalCamera().stopPreview();
        m_camera.getNormalCamera().release();
        m_camera.release();
        m_camera = null;
        surfaceHolder = null;
    }

    public void setSurfaceHolder(SurfaceHolder surface) {
        this.surfaceHolder = surface;
    }

    public void startPreview() {
        Log.d(TAG,"startPreview");
        getCameraEx().getNormalCamera().startPreview();
    }

    public void stopPreview() {
        Log.d(TAG,"stopPreview");
        getCameraEx().getNormalCamera().stopPreview();
    }

    public void enableHwShutterButton() {
        Log.d(TAG,"enableHwShutterButton");
        m_camera.startDirectShutter();

    }

    public void disableHwShutterButton() {
        m_camera.stopDirectShutter(null);
    }

    public void cancelCapture()
    {
        Log.d(TAG,"cancelCapture");
        getCameraEx().cancelTakePicture();
    }

    public void takePicture()
    {
        //hw shutter button must get stopped else burstableTakePicture does not trigger
        getCameraEx().stopDirectShutter(new CameraEx.DirectShutterStoppedCallback() {
            @Override
            public void onShutterStopped(CameraEx cameraEx) {
                cameraEx.burstableTakePicture();
            }
        });

    }

    @Override
    public void onShutterSequence(CameraSequence.RawData rawData, CameraSequence cameraSequence) {
        Log.d(TAG,"onShutterSequence");
        m_camera.cancelTakePicture();
        //cameraSequence.setReleaseLock(true);
    }

  /*  @Override
    public void onSplitShutterSequence(CameraSequence.RawData rawData, CameraSequence.SplitExposureProgressInfo splitExposureProgressInfo, CameraSequence cameraSequence) {
        Log.d(TAG, "onSplitShutterSequence();");
        cameraSequence.setReleaseLock(false);
    }

    @Override
    public void onShutterSequence(CameraSequence.RawData rawData, CameraSequence cameraSequence) {
        Log.d(TAG,"onShutterSequence");
        cameraSequence.setReleaseLock(false);
    }*/


    public void initCamera()
    {
        autoPictureReviewControl = new CameraEx.AutoPictureReviewControl();
        m_camera.setAutoPictureReviewControl(getAutoPictureReviewControls());
        autoPictureReviewControl.setPictureReviewTime(0);
        autoPictureReviewControl.cancelAutoPictureReview();

        initParameters();

        //when false cameraparameters contains only the active parameters, but supported stuff is missing
        m_camera.withSupportedParameters(true);
    }



    /*public void setOptions(CameraSequence.Options paramOptions)
    {

            if (paramOptions == null) {
                paramOptions = new CameraSequence.Options();
            }
            paramOptions.setOption("AUTO_RELEASE_LOCK_ENABLED", true);
        cameraSequence.setReleaseLock(false);
    }*/

    private void dumpParameter() {
        StringTokenizer localStringTokenizer = new StringTokenizer(((Camera.Parameters)getParameters()).flatten(), ";");
        while (localStringTokenizer.hasMoreElements())
            Log.d(TAG, localStringTokenizer.nextToken());

        List<String> tmp = null;
        if (isImageStabSupported())
        {
            Log.d(TAG,"getSupportedImageStabModes");
            logList(getSupportedImageStabModes());
        }
        if (isLiveSlowShutterSupported()) {
            Log.d(TAG, "getSupportedLiveSlowShutterModes");
            logArray(getSupportedLiveSlowShutterModes());
        }
    }

    private void logList(List<String> list)
    {
        String st = new String();
        for (String s : list)
            st += s+",";
        Log.d(TAG,st);
    }
    private void logArray(String[] list)
    {
        String st = new String();
        for (String s : list)
            st += s+",";
        Log.d(TAG,st);
    }
}

