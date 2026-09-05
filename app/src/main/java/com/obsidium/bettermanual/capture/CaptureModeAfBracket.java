package com.obsidium.bettermanual.capture;

import android.util.Log;

import com.obsidium.bettermanual.KeyEvents;
import com.obsidium.bettermanual.Preferences;
import com.obsidium.bettermanual.R;
import com.obsidium.bettermanual.camera.CameraInstance;
import com.obsidium.bettermanual.camera.CaptureSession;
import com.obsidium.bettermanual.controller.DriveModeController;
import com.obsidium.bettermanual.controller.ExposureModeController;
import com.obsidium.bettermanual.controller.FocusDriveController;
import com.obsidium.bettermanual.controller.ShutterController;
import com.obsidium.bettermanual.layout.CameraUiInterface;

public class CaptureModeAfBracket extends CaptureMode implements KeyEvents, CaptureSession.CaptureDoneEvent,FocusDriveController.FocusPostionChangedEvent {

    private final String TAG = CaptureModeAfBracket.class.getSimpleName();
    // Lets a currently-running AF bracket sequence be cancelled cleanly via
    // Delete on the main screen (see CameraUiFragment.onDeleteKeyUp()),
    // instead of Delete's other existing behavior there -- closing the whole
    // app outright -- which would skip abort()'s cleanup if a sequence
    // happened to be running. Mirrors the same fix already made for
    // CaptureModeTimelapse.
    private static CaptureModeAfBracket s_activeInstance;

    public static boolean cancelIfActive()
    {
        if (s_activeInstance != null && s_activeInstance.isActive())
        {
            Log.d(CaptureModeAfBracket.class.getSimpleName(), "Cancelling active AF bracket sequence instead of closing the app");
            s_activeInstance.abort();
            return true;
        }
        return false;
    }

    private int focusNear;
    private int focusFar;
    private int pictureCount;
    private AfBracketCaptureController afBracketCaptureController;

    private UiState uiState = UiState.None;

    @Override
    public void onFocusPostionChanged() {
        Log.d(TAG, "onFocusPostionChanged");
        if (uiState == UiState.SelectNear) {
            focusNear = FocusDriveController.GetInstance().getFocusPosition();
            updateNearTextView();
        }
        else if (uiState == UiState.SelectFar) {
            focusFar = FocusDriveController.GetInstance().getFocusPosition();
            updateFarTextView();
        }

        if (afBracketCaptureController != null)
        {
            Log.d(TAG, "onFocusPostionChanged notify afbracketController");
            afBracketCaptureController.NotifyWaitlock();
        }
    }

    public enum UiState
    {
        None,
        SelectNear,
        SelectFar,
        SelectPictureCount,
    }

    public CaptureModeAfBracket(CameraUiInterface cameraUiInterface) {
        super(cameraUiInterface);
    }

    @Override
    public int getNavigationHelpID() {
        return R.string.view_startAFBracket;
    }

    @Override
    public void reset() {
        focusNear = 0;
        focusFar = 20;
        pictureCount = 2;
    }

    @Override
    public boolean prepare() {
        if (ExposureModeController.GetInstance().getExposureMode() == null)
            return false;
        if (isActive())
            abort();
        // reset() sets focusNear/focusFar/pictureCount to sane starting values
        // (pictureCount = 2). Without this, pictureCount is left at its raw
        // Java default of 0 the first time this mode is used, and confirming
        // through Near/Far/Picture-Count without ever touching the dial divides
        // by that 0 in AfBracketCaptureController's constructor below, crashing
        // the app with an ArithmeticException.
        reset();
        FocusDriveController.GetInstance().setFocusPostionChangedEventListner(this);
        return true;
    }

    @Override
    public void startShooting() {
        Log.d(TAG,"startShooting");
        cameraUiInterface.hideHintMessage();
        cameraUiInterface.hideMessage();
        // Take first picture at set shutter speed
        cameraUiInterface.getActivityInterface().setBulbCapture(false);
        afBracketCaptureController = new AfBracketCaptureController(focusNear,focusFar,pictureCount);
        afBracketCaptureController.captureRange();
    }

    @Override
    public void abort() {
        if (afBracketCaptureController != null) {
            afBracketCaptureController.abort();
            afBracketCaptureController = null;
        }
        cameraUiInterface.getActivityInterface().getMainHandler().removeCallbacks(m_countDownRunnable);
        cameraUiInterface.getActivityInterface().setCaptureDoneEventListner(null);
        FocusDriveController.GetInstance().setFocusPostionChangedEventListner(null);
        uiState = UiState.None;
        //m_handler.removeCallbacks(m_timelapseRunnable);
        isActive = false;
        s_activeInstance = null;
        cameraUiInterface.showMessageDelayed("Bracketing finished");
        CameraInstance.GET().enableHwShutterButton();
        CameraInstance.GET().startPreview();

        // Update controls
        cameraUiInterface.hideHintMessage();
        cameraUiInterface.setLeftViewVisibility(true);
        ExposureModeController.GetInstance().onValueChanged();
        DriveModeController.GetInstance().onValueChanged();

        cameraUiInterface.setActiveViewFlag(Preferences.GET().getViewFlags(cameraUiInterface.getActiveViewsFlag()));
        cameraUiInterface.updateViewVisibility();
    }

    @Override
    public void increment() {
        if (uiState == UiState.SelectNear || uiState == UiState.SelectFar)
            FocusDriveController.GetInstance().set_In_De_crase(+2);

    }

    @Override
    public void decrement() {
        if (uiState == UiState.SelectNear || uiState == UiState.SelectFar)
            FocusDriveController.GetInstance().set_In_De_crase(-2);
    }

    private void updateNearTextView()
    {
        cameraUiInterface.showHintMessage("Near Focus Pos:" + focusNear);
    }

    private void updateFarTextView()
    {
        cameraUiInterface.showHintMessage("Far Focus Pos:" + focusFar);
    }

    private void updatePictureCountTextView()
    {
        cameraUiInterface.showHintMessage("PictureCount:" + pictureCount);
    }



    @Override
    public void incrementPicCount() {
        if (uiState == UiState.SelectPictureCount)
            pictureCount++;
        updatePictureCountTextView();
    }

    @Override
    public void decrementPicCount() {
        if (uiState == UiState.SelectPictureCount)
            pictureCount--;
        if (pictureCount < 2)
            pictureCount = 2;
        updatePictureCountTextView();
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
    public boolean onUpperDialChanged(int value) {
        return false;
    }

    @Override
    public boolean onLowerDialChanged(int value) {
        if (uiState == UiState.SelectNear || uiState == UiState.SelectFar)
        {
            if (value <0)
                decrement();
            else
                increment();
        }
        if (uiState == UiState.SelectPictureCount)
        {
            if (value < 0)
                decrementPicCount();
            else
                incrementPicCount();
        }
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

    @Override
    public boolean onEnterKeyUp() {
        if (uiState == UiState.None)
        {
            if (prepare()) {
                uiState = UiState.SelectNear;
                updateNearTextView();
            }
        }
        else if(uiState == UiState.SelectNear)
        {
            uiState = UiState.SelectFar;
            updateFarTextView();
        }
        else if(uiState == UiState.SelectFar)
        {
            uiState = UiState.SelectPictureCount;
            updatePictureCountTextView();
        }
        else if(uiState == UiState.SelectPictureCount)
        {
            cameraUiInterface.showHintMessage(cameraUiInterface.getActivityInterface().getResString(R.string.icon_lowerDial)
                    + "to set picture count,"
                    + cameraUiInterface.getActivityInterface().getResString(R.string.icon_enterButton)
                    + " to confirm");
            uiState = UiState.None;
            cameraUiInterface.getActivityInterface().getDialHandler().setDialEventListner((KeyEvents)cameraUiInterface);
            cameraUiInterface.getActivityInterface().setCaptureDoneEventListner(this);
            s_activeInstance = this;
            startCountDown();
        }

        return false;
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

    @Override
    public void onCaptureDone() {
        if (afBracketCaptureController != null)
            afBracketCaptureController.NotifyCaptureWaitlock();
    }

    private class AfBracketCaptureController
    {
        private int[] focusPositions;
        private Thread captureThread;
        private Object waitlock = new Object();
        private Object captureWaitLock = new Object();
        private boolean doFocus = false;

        public AfBracketCaptureController(int minFocusPos, int maxFocusPos, int picturCount)
        {
            // Belt-and-suspenders: prepare() now calls reset(), which sets
            // pictureCount to 2, but nothing stops a future caller from getting
            // here with 0 again. Dividing by picturCount below crashes the app
            // outright (ArithmeticException) if it's ever 0, so floor it.
            if (picturCount < 2)
                picturCount = 2;
            focusPositions = new int[picturCount];
            int dif = maxFocusPos -minFocusPos;
            int step = dif/picturCount;
            focusPositions[0] = minFocusPos;
            for (int i = 1; i < picturCount;i++)
            {
                focusPositions[i] = minFocusPos + step*i;
            }
            Log.d(TAG, "NearFocus:" + focusNear + " FarFocus:" +focusFar +" Dif:" + dif + " Step:" + step + "PicCount:" + picturCount);
        }

        public void NotifyCaptureWaitlock()
        {
            if (!doFocus)
                return;
            synchronized (captureWaitLock)
            {
                captureWaitLock.notify();
            }
        }

        public void NotifyWaitlock()
        {
            if (!doFocus)
                return;
            synchronized (waitlock)
            {
                waitlock.notify();
            }
        }


        public void abort()
        {
            doFocus = false;
            captureThread.interrupt();
        }

        private int getStep(int diff)
        {
            if (diff < 0)
                diff *= -1;
            if (diff > 25)
                return   4;
            else if (diff > 20)
                return 3;
            else if (diff > 15)
                return 2;
            else
                return 1;
        }

        public void captureRange()
        {
            doFocus = true;
            captureThread = new Thread(){
                @Override
                public void run() {
                    for (int i = 0; i< focusPositions.length; i++)
                    {
                        if (!doFocus)
                            return;
                        int newpos = focusPositions[i];
                        int currentPos =FocusDriveController.GetInstance().getFocusPosition();
                        Log.d(TAG, "new Pos:" + newpos + " currentPos:" + currentPos);
                        while (newpos != currentPos && !Thread.currentThread().isInterrupted() && doFocus) {
                            synchronized (waitlock) {
                                final int dif = newpos - currentPos;
                                Log.d(TAG, "Diff:" + dif);
                                cameraUiInterface.getActivityInterface().getMainHandler().post(()-> {
                                    if (dif < 0)
                                        FocusDriveController.GetInstance().set_In_De_crase(-getStep(dif));
                                    else
                                        FocusDriveController.GetInstance().set_In_De_crase(getStep(dif));
                                });

                                try {
                                    if (doFocus)
                                        waitlock.wait(20);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            currentPos =FocusDriveController.GetInstance().getFocusPosition();
                        }


                        //wait for capture complete
                        synchronized (captureWaitLock)
                        {
                            if (doFocus)
                                cameraUiInterface.getActivityInterface().getMainHandler().post(()-> {
                                    CameraInstance.GET().takePicture();
                                });
                            try {
                                if (doFocus)
                                    captureWaitLock.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    cameraUiInterface.getActivityInterface().getMainHandler().post(()-> {
                        Log.d(TAG,"abort");
                        CaptureModeAfBracket.this.abort();
                    });
                }
            };
            captureThread.start();
        }
    }
}
