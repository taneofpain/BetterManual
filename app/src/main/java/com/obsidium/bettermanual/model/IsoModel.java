package com.obsidium.bettermanual.model;

import com.obsidium.bettermanual.camera.CameraInstance;
import com.sony.scalar.hardware.CameraEx;

import java.util.List;

public class IsoModel extends AbstractModel<String> implements CameraEx.AutoISOSensitivityListener {


    private int m_curIso;
    private int currentIsoPos;
    private List<Integer> m_supportedIsos;

    public IsoModel(CameraInstance cameraInstance)
    {
        super(cameraInstance);
        this.m_supportedIsos = camera.getSupportedISOSensitivities();
        // getSupportedISOSensitivities() is a direct pass-through to the
        // native camera API with no null-safety of its own -- if it ever
        // returns null (unsupported, or a transient failure during camera
        // init), getIsoPos() below immediately NPEs on m_supportedIsos.size(),
        // crashing the app on every single camera open before the UI even
        // shows. Falling back to an empty list keeps this class functional
        // (ISO just can't be adjusted) instead of taking the whole app down.
        if (this.m_supportedIsos == null)
            this.m_supportedIsos = new java.util.ArrayList<>();
        m_curIso = camera.getISOSensitivity();
        value = "\uE488 " + String.valueOf(0) + (m_curIso == 0 ? "(A)" : "");
        currentIsoPos = getIsoPos(m_curIso);
        fireOnValueChanged();
        fireOnSupportedChanged();

    }

    private int getIsoPos(int isovalue)
    {
        for (int i= 0; i< m_supportedIsos.size();i++)
        {
            if (m_supportedIsos.get(i) == isovalue)
                return i;
        }
        return 0;
    }

    @Override
    public void setValue(int i) {
        if (m_supportedIsos == null || m_supportedIsos.isEmpty())
            return;

        currentIsoPos += i;

        if (currentIsoPos < 0)
            currentIsoPos = 0;
        if (currentIsoPos >= m_supportedIsos.size())
            currentIsoPos = m_supportedIsos.size()-1;

        m_curIso = m_supportedIsos.get(currentIsoPos);
        value = String.format("\uE488 %s", (m_curIso == 0 ? "AUTO" : String.valueOf(m_curIso)));

        camera.setISOSensitivity(m_curIso);
        fireOnValueChanged();
    }

    public void toggle()
    {
        m_curIso = getCurrentIso() == 0 ? getFirstManualIso() : 0;
        value = String.format("\uE488 %s", (m_curIso == 0 ? "AUTO" : String.valueOf(m_curIso)));

        camera.setISOSensitivity(m_curIso);
        fireOnValueChanged();
    }

    @Override
    public String getValue() {
        return value;
    }


    @Override
    public boolean isSupported() {
        return true;
    }

    //AutoIsoSensitivityListner
    @Override
    public void onChanged(int i, CameraEx cameraEx) {
        value = "\uE488 " + String.valueOf(i) + (m_curIso == 0 ? "(A)" : "");
        fireOnValueChanged();
    }


    public int getCurrentIso()
    {
        return m_curIso;
    }

    public int getFirstManualIso() {
        // Index 1 is "the first manual ISO after Auto at index 0" -- assumes
        // at least 2 entries. Every real camera has multiple ISO settings,
        // but this was previously unguarded against a shorter list too.
        if (m_supportedIsos == null || m_supportedIsos.size() < 2)
            return 0;

        return m_supportedIsos.get(1);
    }
}
