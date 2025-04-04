package net.heretical_camelid.fhau.android_app;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.hardware.usb.UsbDevice;

import android.hardware.usb.UsbManager;
import com.benlypan.usbhid.OnUsbHidDeviceListener;
import com.benlypan.usbhid.UsbHidDevice;

import net.heretical_camelid.fhau.lib.*;
import net.heretical_camelid.fhau.lib.interfaces.IAmpProvider;
import net.heretical_camelid.fhau.lib.registries.FenderJsonPresetRegistry;
import net.heretical_camelid.fhau.lib.registries.PresetRegistryBase;
import net.heretical_camelid.fhau.lib.registries.PresetSuiteRegistry;

import java.util.ArrayList;
import java.util.HashMap;

import static net.heretical_camelid.fhau.android_app.MainActivity.appendToLog;

public class AndroidUsbAmpProvider implements OnUsbHidDeviceListener, IAmpProvider {
    final static String ACTION_USB_PERMISSION = "net.heretical_camelid.fhau.android_app.USB_PERMISSION";

    MainActivity m_mainActivity;

    String m_firmwareVersion;
    boolean m_permissionRequested = false;
    boolean m_connectionSucceeded=false;

    DeviceTransportUsbHid m_deviceTransportUsbHid;

    UsbManager m_usbManager;
    UsbBroadcastReceiver m_usbReceiver;
    PendingIntent m_permissionIntent;
    UsbHidDevice m_usbHidDevice;
    UsbDevice m_usbDevice;

    AbstractMessageProtocolBase m_protocol;

    FenderJsonPresetRegistry m_presetRegistry;
    PresetSuiteRegistry m_presetSuiteRegistry;

    ProviderState_e m_state;

    AndroidUsbAmpProvider(
        MainActivity mainActivity
    ) {
        m_state = ProviderState_e.PROVIDER_INITIAL;
        m_mainActivity = mainActivity;
        m_usbManager = (UsbManager) m_mainActivity.getSystemService(Context.USB_SERVICE);
        m_presetRegistry = new FenderJsonPresetRegistry(null);
        m_protocol = new LTSeriesProtocol(m_presetRegistry,true);

        // m_usbReceiver is used as an indicator for whether the permission request
        // has been done, so we do not instantiate it until we have a device on
        // which permission can be requested
        m_usbReceiver = null;
        m_usbDevice = null;
        m_usbHidDevice = null;

        m_deviceTransportUsbHid = new DeviceTransportUsbHid(null);
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
        return m_deviceTransportUsbHid.attemptUsbHidConnection(this);
    }

    public void usbAccessPermissionGranted() {
        attemptConnection();
    }

    public void usbAccessPermissionDenied() {
        m_state = ProviderState_e.PROVIDER_DEVICE_CONNECTION_FAILED;
    }

    @Override
    public void onUsbHidDeviceConnected(UsbHidDevice device) {
        appendToLog("Device connected");
    }

    @Override
    public void onUsbHidDeviceConnectFailed(UsbHidDevice device) {
        appendToLog("Device connect failed");
        m_usbHidDevice.close();
        m_usbHidDevice = null;
        m_usbDevice = null;
        m_connectionSucceeded = false;
        m_state = ProviderState_e.PROVIDER_INITIAL;
    }

    public void switchSuite(int position) {
        String suiteName = m_presetSuiteRegistry.nameAt(position);
        HashMap<Integer,FenderJsonPresetRegistry.Record> suitePresetRecords = m_presetSuiteRegistry.recordsAt(position);
        m_mainActivity.suiteSelected(suiteName, suitePresetRecords);
    }
}

