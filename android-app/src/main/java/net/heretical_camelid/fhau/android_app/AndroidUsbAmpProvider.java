package net.heretical_camelid.fhau.android_app;

import android.annotation.SuppressLint;

import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Message;
import net.heretical_camelid.fhau.lib.*;
import net.heretical_camelid.fhau.lib.interfaces.IAmpProvider;
import net.heretical_camelid.fhau.lib.registries.PresetRecord;
import net.heretical_camelid.fhau.lib.registries.PresetRegistry;
import net.heretical_camelid.fhau.lib.registries.SuiteRecord;
import net.heretical_camelid.fhau.lib.registries.SuiteRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AndroidUsbAmpProvider implements IAmpProvider {

    MainActivity m_mainActivity;
    UsbBroadcastReceiver m_usbReceiver;
    PendingIntent m_permissionIntent;

    DeviceTransportUsbHid m_deviceTransportUsbHid;
    LTSeriesProtocol m_protocol;

    PresetRegistry m_presetRegistry;
    SuiteRegistry m_suiteRegistry;
    String m_ampModel;
    String m_firmwareVersion;

    AndroidUsbAmpProvider(
        MainActivity mainActivity
    ) {
        m_mainActivity = mainActivity;
        m_deviceTransportUsbHid = new DeviceTransportUsbHid(m_mainActivity, this);
        m_presetRegistry = new PresetRegistry(null);
        m_protocol = new LTSeriesProtocol(m_presetRegistry,true);
        m_suiteRegistry = new SuiteRegistry(m_presetRegistry);
    }

    public boolean getFirmwareVersionAndPresets() {
        String[] firmwareVersionHolder = new String[] { null };
        int startupStatus = m_protocol.doStartup(firmwareVersionHolder);
        m_firmwareVersion = firmwareVersionHolder[0];
        m_mainActivity.appendToLog("Firmware Version: " + m_firmwareVersion);

        // The Mustang LT40S with firmware 1.0.7 has 60 presets
        // According to the internet, Mustang LT50, Mustang/Rumble LT25
        // had 30 presets on early firmware, but have since had updated
        // firmware released which supports 60 presets.
        // If/when we get success reports from devices other than the
        // LT40S we should update the values below.
        // Experiments with LT40S/firmware 1.0.7 show that
        // _on_that_device_firmware_combination_ there are no adverse
        // consequences of requesting a preset out of the storage range
        // of the amp.
        int firstPreset = 1;
        int lastPreset = 60;
        int presetNamesStatus = m_protocol.getPresetNamesList(firstPreset, lastPreset);

        m_mainActivity.appendToLog(String.format(
            "Amp contains %d unique presets", m_presetRegistry.uniquePresetCount()
        ));

        return startupStatus==AbstractMessageProtocolBase.STATUS_OK;
    }

    public PresetRegistry getPresetRegistry() {
        return m_presetRegistry;
    }

    @Override
    public void switchPreset(int slotIndex) {
        PresetRecord presetRecord = m_presetRegistry.get(slotIndex);
        if(presetRecord == null) {
            m_mainActivity.appendToLog("No preset found for slot id " + slotIndex);
        } else {
            m_protocol.switchPreset(slotIndex);
            Bundle msgData = new Bundle();
            msgData.putInt(MainActivity.MESSAGE_SLOT_INDEX, slotIndex);
            msgData.putString(
                MainActivity.MESSAGE_PRESET_NAME,
                presetRecord.displayName().replaceAll("( )+", " ").strip()
            );
            msgData.putString(
                MainActivity.MESSAGE_PRESET_EFFECTS,
                presetRecord.effects(
                    PresetRecord.EffectsLevelOfDetails.MODULES_AND_PARAMETERS
                )
            );
            Message presetChangeMsg = new Message();
            presetChangeMsg.what = MessageType_e.MESSAGE_PRESET_SELECTED.ordinal();
            presetChangeMsg.setData(msgData);
            m_mainActivity.m_providerHandler.sendMessage(presetChangeMsg);
        }
    }

    @Override
    public SuiteRegistry getSuiteRegistry() {
        return m_suiteRegistry;
    }

    @Override
    public SuiteRecord buildPresetSuite(
        String suiteName,
        ArrayList<HashMap<String, String>> presets,
        Set<Integer> remainingPresetIndices
    ) {
        SuiteRecord newPSE = m_suiteRegistry.createPresetSuiteEntry(
            suiteName, presets
        );
        for(int slotIndex: newPSE.getSlotIndices()) {
            remainingPresetIndices.remove(slotIndex);
        }
        return newPSE;
    }

    @Override
    public ArrayList<SuiteRecord> loadCuratedPresetSuites() {
        return null;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public ProviderState_e attemptConnection() {
        ProviderState_e state = m_deviceTransportUsbHid.attemptUsbHidConnection();
        if(state==ProviderState_e.PROVIDER_DEVICE_CONNECTION_SUCCEEDED) {
            String[] startupItemWrapper = new String[2];
            m_protocol.setDeviceTransport(m_deviceTransportUsbHid);
            m_protocol.doStartup(startupItemWrapper);

            // The Mustang LT40S with firmware 1.0.7 has 60 presets
            // According to the internet, Mustang LT50, Mustang/Rumble LT25
            // had 30 presets on early firmware, but have since had updated
            // firmware released which supports 60 presets.
            // If/when we get success reports from devices other than the
            // LT40S we should update the values below.
            // Experiments with LT40S/firmware 1.0.7 show that
            // _on_that_device_firmware_combination_ there are no adverse
            // consequences of requesting a preset out of the storage range
            // of the amp.
            int firstPreset = 1;
            int lastPreset = 60;
            m_protocol.getPresetNamesList(firstPreset, lastPreset);

            m_protocol.startHeartbeatThread();
        }
        return state;
    }

    public void switchSuite(int position) {
        String suiteName = m_suiteRegistry.nameAt(position);
        Map<Integer, PresetRecord> suitePresetRecords = m_suiteRegistry.recordsAt(position);
        m_mainActivity.suiteSelected(suiteName, suitePresetRecords);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    void registerForPermissionIntent() {
        if (m_usbReceiver != null) {
           m_mainActivity.appendToLog("Already registered for permission intent");
        } else {
            m_usbReceiver = new UsbBroadcastReceiver();

            Intent basePermissionIntent = new Intent(DeviceTransportUsbHid.ACTION_USB_PERMISSION);
            basePermissionIntent.setPackage("net.heretical_camelid.fhau.android_app");
            // should use setClass instead of setPackage but not sure which class is
            // the right target
            basePermissionIntent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false);
            m_permissionIntent = PendingIntent.getBroadcast(
                m_mainActivity, 0,
                basePermissionIntent,
                // Android documentation recommends against using
                // mutable pending intents, but in this case it
                // seems the PI must be mutable to allow the
                // USB service to update EXTRA_PERMISSION_GRANTED
                // to indicate that the device can be used.
                PendingIntent.FLAG_MUTABLE
            );
            m_mainActivity.appendToLog("Registered for permission intent");
        }
    }
}

