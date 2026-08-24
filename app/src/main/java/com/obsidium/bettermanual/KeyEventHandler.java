package com.obsidium.bettermanual;

import android.util.Log;
import android.view.KeyEvent;

import com.sony.scalar.sysutil.ScalarInput;

/**
 * Created by KillerInk on 24.08.2017.
 */

public class KeyEventHandler {

    private final  String TAG = KeyEventHandler.class.getSimpleName();

    private KeyEvents dialEventListner;
    private KeyEvents defaultListner;
    private final boolean log = false;

    // The a5100 only has a single control dial (it reports as DIAL_1 / "upper" dial),
    // unlike the a6000 which has two independent dials. Every KeyEvents implementor in
    // this app puts its real adjustment logic in onLowerDialChanged (DIAL_2) and leaves
    // onUpperDialChanged as a no-op, since that's all the a6000 ever needed. When this
    // flag is set, DIAL_1 events are routed to onLowerDialChanged instead, so the a5100's
    // one dial actually drives the existing adjustment logic instead of hitting the
    // unused onUpperDialChanged stub. Off by default so a6000 behaviour is unchanged.
    private boolean singleDialMode = false;

    public KeyEventHandler(KeyEvents defaultListner)
    {
        this.defaultListner = defaultListner;
    }

    public void setSingleDialMode(boolean singleDialMode)
    {
        this.singleDialMode = singleDialMode;
    }

    public void setDefaultListner()
    {
        this.dialEventListner = defaultListner;
    }

    public void setDialEventListner(KeyEvents eventListner)
    {
        this.dialEventListner = eventListner;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        if (dialEventListner == null)
            return true;
        if (log)
            Log.d(TAG,"onKeyUp " + getKeyString(event.getScanCode()));
        if (dialEventListner != null) {
            switch (event.getScanCode()) {
                case ScalarInput.ISV_KEY_UP:
                    return dialEventListner.onUpKeyUp();
                case ScalarInput.ISV_KEY_DOWN:
                    return dialEventListner.onDownKeyUp();
                case ScalarInput.ISV_KEY_LEFT:
                    return dialEventListner.onLeftKeyUp();
                case ScalarInput.ISV_KEY_RIGHT:
                    return dialEventListner.onRightKeyUp();
                case ScalarInput.ISV_KEY_ENTER:
                    return dialEventListner.onEnterKeyUp();

                case ScalarInput.ISV_DIAL_1_CLOCKWISE:
                case ScalarInput.ISV_DIAL_1_COUNTERCW:
                case ScalarInput.ISV_DIAL_2_CLOCKWISE:
                case ScalarInput.ISV_DIAL_2_COUNTERCW:
                    return true;

                case ScalarInput.ISV_KEY_FN:
                    return dialEventListner.onFnKeyUp();
                case ScalarInput.ISV_KEY_AEL:
                    return dialEventListner.onAelKeyUp();
                case ScalarInput.ISV_KEY_MENU:
                case ScalarInput.ISV_KEY_SK1:
                    return dialEventListner.onMenuKeyUp();
                case ScalarInput.ISV_KEY_S1_1:
                    return dialEventListner.onFocusKeyUp();
                case ScalarInput.ISV_KEY_S1_2:
                    return true;
                case ScalarInput.ISV_KEY_S2:
                    return dialEventListner.onShutterKeyUp();
                case ScalarInput.ISV_KEY_PLAY:
                    return dialEventListner.onPlayKeyUp();
                case ScalarInput.ISV_KEY_STASTOP:
                    return dialEventListner.onMovieKeyUp();
                case ScalarInput.ISV_KEY_CUSTOM1:
                    return dialEventListner.onC1KeyUp();
                case ScalarInput.ISV_KEY_DELETE:
                case ScalarInput.ISV_KEY_SK2:
                    return dialEventListner.onDeleteKeyUp();
                case ScalarInput.ISV_KEY_LENS_ATTACH:
                    return dialEventListner.onLensDetached();
            }
        }
        return true;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (dialEventListner == null)
            return true;
        if (log)
            Log.d(TAG,"onKeyDown " + getKeyString(event.getScanCode()));
        switch (event.getScanCode()) {
            case ScalarInput.ISV_KEY_UP:
                return dialEventListner.onUpKeyDown();
            case ScalarInput.ISV_KEY_DOWN:
                return dialEventListner.onDownKeyDown();
            case ScalarInput.ISV_KEY_LEFT:
                return dialEventListner.onLeftKeyDown();
            case ScalarInput.ISV_KEY_RIGHT:
                return dialEventListner.onRightKeyDown();
            case ScalarInput.ISV_KEY_ENTER:
                return dialEventListner.onEnterKeyDown();

            case ScalarInput.ISV_DIAL_1_CLOCKWISE:
                return singleDialMode ? dialEventListner.onLowerDialChanged(1) : dialEventListner.onUpperDialChanged(1);
            case ScalarInput.ISV_DIAL_1_COUNTERCW:
                return singleDialMode ? dialEventListner.onLowerDialChanged(-1) : dialEventListner.onUpperDialChanged(-1);
            case ScalarInput.ISV_DIAL_2_CLOCKWISE:
                return dialEventListner.onLowerDialChanged(1);
            case ScalarInput.ISV_DIAL_2_COUNTERCW:
                return dialEventListner.onLowerDialChanged(-1);

            case ScalarInput.ISV_KEY_FN:
                return dialEventListner.onFnKeyDown();
            case ScalarInput.ISV_KEY_AEL:
                return dialEventListner.onAelKeyDown();
            case ScalarInput.ISV_KEY_MENU:
            case ScalarInput.ISV_KEY_SK1:
                return dialEventListner.onMenuKeyDown();
            case ScalarInput.ISV_KEY_S1_1:
                return dialEventListner.onFocusKeyDown();
            case ScalarInput.ISV_KEY_S1_2:
                return true;
            case ScalarInput.ISV_KEY_S2:
                return dialEventListner.onShutterKeyDown();
            case ScalarInput.ISV_KEY_PLAY:
                return dialEventListner.onPlayKeyDown();
            case ScalarInput.ISV_KEY_STASTOP:
                return dialEventListner.onMovieKeyDown();
            case ScalarInput.ISV_KEY_CUSTOM1:
                return dialEventListner.onC1KeyDown();
            case ScalarInput.ISV_KEY_DELETE:
            case ScalarInput.ISV_KEY_SK2:
                return dialEventListner.onDeleteKeyDown();
            case ScalarInput.ISV_KEY_LENS_ATTACH:
                return dialEventListner.onLensAttached();
            case ScalarInput.ISV_KEY_MODE_DIAL:
                return dialEventListner.onModeDialChanged(getDialStatus(ScalarInput.ISV_KEY_MODE_DIAL));
            case ScalarInput.ISV_KEY_ZOOM_OFF: // zoom not active
                return dialEventListner.onZoomOffKey();
            case ScalarInput.ISV_KEY_ZOOM_TELE: //zoom in
                return dialEventListner.onZoomTeleKey();
            case ScalarInput.ISV_KEY_ZOOM_WIDE: //zoom out
                return dialEventListner.onZoomWideKey();
        }
        return true;
    }
    protected int getDialStatus(int key) {
        return ScalarInput.getKeyStatus(key).status;
    }

    public String getKeyString(int key)
    {
        switch (key)
        {
            case ScalarInput.ISV_KEY_UP:
                return "Up";
            case ScalarInput.ISV_KEY_DOWN:
                return "Down";
            case ScalarInput.ISV_KEY_LEFT:
                return "Left";
            case ScalarInput.ISV_KEY_RIGHT:
                return "Right";
            case ScalarInput.ISV_KEY_ENTER:
                return "Enter";

            case ScalarInput.ISV_DIAL_1_CLOCKWISE:
                return "UpperDialChanged_Clock";
            case ScalarInput.ISV_DIAL_1_COUNTERCW:
                return "UpperDialChanged_ CW";
            case ScalarInput.ISV_DIAL_2_CLOCKWISE:
                return "LowerDialChanged_Clock";
            case ScalarInput.ISV_DIAL_2_COUNTERCW:
                return "LowerDialChanged_CW";

            case ScalarInput.ISV_KEY_FN:
                return "FnKeyDown";
            case ScalarInput.ISV_KEY_AEL:
                return "AelKey";
            case ScalarInput.ISV_KEY_MENU:
            case ScalarInput.ISV_KEY_SK1:
                return "MenuKeyDown";
            case ScalarInput.ISV_KEY_S1_1:
                return "FocusKeyDown";
            case ScalarInput.ISV_KEY_S1_2:
                return "ISV_KEY_S1_2";
            case ScalarInput.ISV_KEY_S2:
                return "Shutter";
            case ScalarInput.ISV_KEY_PLAY:
                return "Play";
            case ScalarInput.ISV_KEY_STASTOP:
                return "Movie";
            case ScalarInput.ISV_KEY_CUSTOM1:
                return"C1";
            case ScalarInput.ISV_KEY_DELETE:
            case ScalarInput.ISV_KEY_SK2:
                return "Delete";
            case ScalarInput.ISV_KEY_LENS_ATTACH:
                return "LensAttached";
            case ScalarInput.ISV_KEY_MODE_DIAL:
                return "ModeDialChanged";
            case ScalarInput.ISV_KEY_ZOOM_OFF: // zoom not active
                return "ZoomOff";
            case ScalarInput.ISV_KEY_ZOOM_TELE: //zoom in
                return "ZoomTele";
            case ScalarInput.ISV_KEY_ZOOM_WIDE: //zoom out
                return "ZoomWideKey";
        }
        return String.valueOf(key);
    }
}
