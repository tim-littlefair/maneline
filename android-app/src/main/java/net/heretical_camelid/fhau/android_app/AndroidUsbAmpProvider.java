package net.heretical_camelid.fhau.android_app;

import android.annotation.SuppressLint;

import net.heretical_camelid.fhau.lib.*;
import net.heretical_camelid.fhau.lib.interfaces.IAmpProvider;
import net.heretical_camelid.fhau.lib.registries.FenderJsonPresetRegistry;
import net.heretical_camelid.fhau.lib.registries.PresetRegistryBase;
import net.heretical_camelid.fhau.lib.registries.PresetSuiteRegistry;

import java.util.ArrayList;
import java.util.HashMap;

import static net.heretical_camelid.fhau.android_app.MainActivity.appendToLog;

public class AndroidUsbAmpProvider implements IAmpProvider {

    MainActivity m_mainActivity;
    DeviceTransportUsbHid m_deviceTransportUsbHid;
    LTSeriesProtocol m_protocol;

    FenderJsonPresetRegistry m_presetRegistry;
    PresetSuiteRegistry m_presetSuiteRegistry;
    String m_ampModel;
    String m_firmwareVersion;

    AndroidUsbAmpProvider(
        MainActivity mainActivity
    ) {
        m_mainActivity = mainActivity;
        m_presetRegistry = new FenderJsonPresetRegistry(null);
        m_protocol = new LTSeriesProtocol(m_presetRegistry,true);
        m_deviceTransportUsbHid = new DeviceTransportUsbHid(m_mainActivity, this);
    }

    public boolean getFirmwareVersionAndPresets() {
        String[] firmwareVersionHolder = new String[] { null };
        int startupStatus = m_protocol.doStartup(firmwareVersionHolder);
        m_firmwareVersion = firmwareVersionHolder[0];
        appendToLog("Firmware Version: " + m_firmwareVersion);
        int presetNamesStatus = m_protocol.getPresetNamesList();
        appendToLog(String.format(
            "Amp contains %d unique presets", m_presetRegistry.uniquePresetCount()
        ));

        return startupStatus==AbstractMessageProtocolBase.STATUS_OK;
    }

    public PresetRegistryBase getPresetRegistry() {
        return m_presetRegistry;
    }

    @Override
    public void switchPreset(int slotIndex) {
        m_protocol.switchPreset(slotIndex);
    }

    @Override
    public ArrayList<PresetSuiteRegistry.PresetSuiteEntry> buildAmpBasedPresetSuites(int maxPresetsPerSuite, int targetPresetsPerSuite, int maxAmpsPerSuite) {
        m_presetSuiteRegistry = new PresetSuiteRegistry(m_presetRegistry);
        return m_presetSuiteRegistry.buildPresetSuites(
            maxPresetsPerSuite, targetPresetsPerSuite, maxAmpsPerSuite
        );
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public ProviderState_e attemptConnection() {
        ProviderState_e state = m_deviceTransportUsbHid.attemptUsbHidConnection();
        if(state==ProviderState_e.PROVIDER_DEVICE_CONNECTION_SUCCEEDED) {
            String[] startupItemWrapper = new String[2];
            m_protocol.setDeviceTransport(m_deviceTransportUsbHid);
            m_protocol.doStartup(startupItemWrapper);
            m_protocol.getPresetNamesList();
            m_protocol.startHeartbeatThread();
            buildAmpBasedPresetSuites(9, 7, 3);
        }
        return state;
    }

    public void switchSuite(int position) {
        String suiteName = m_presetSuiteRegistry.nameAt(position);
        HashMap<Integer,FenderJsonPresetRegistry.Record> suitePresetRecords = m_presetSuiteRegistry.recordsAt(position);
        m_mainActivity.suiteSelected(suiteName, suitePresetRecords);
    }
}

