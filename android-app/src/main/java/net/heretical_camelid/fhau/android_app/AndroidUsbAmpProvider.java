package net.heretical_camelid.fhau.android_app;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import com.benlypan.usbhid.OnUsbHidDeviceListener;
import com.benlypan.usbhid.UsbHidDevice;

import net.heretical_camelid.fhau.lib.*;

import java.util.HashMap;

import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static net.heretical_camelid.fhau.android_app.MainActivity.appendToLog;

public class AndroidUsbAmpProvider implements OnUsbHidDeviceListener, IAmpProvider {
    final static String ACTION_USB_PERMISSION = "net.heretical_camelid.fhau.android_app.USB_PERMISSION";

    ProviderState_e m_state;
    MainActivity m_mainActivity;
    UsbManager m_usbManager;
    UsbBroadcastReceiver m_usbReceiver;
    PendingIntent m_permissionIntent;
    PresetRegistryBase m_presetRegistry;
    AbstractMessageProtocolBase m_protocol;
    String m_firmwareVersion;
    boolean m_permissionRequested = false;
    boolean m_connectionSucceeded=false;


    UsbHidDevice m_usbHidDevice;
    UsbDevice m_usbDevice;

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

    @Override
    public ProviderState_e getState() {
        return null;
    }

    @Override
    public void sendCommand(String commandHexString) {

    }

    @Override
    public String getFirmwareVersion() {
        return m_firmwareVersion;
    }

    @Override
    public PresetInfo getPresetInfo(PresetInfo requestedPresets) {
        PresetInfo retval = new PresetInfo();
        if(m_presetRegistry==null) {
            m_presetRegistry = new FenderJsonPresetRegistry(null);

            m_presetRegistry.acceptVisitor(new PresetRegistryBase.Visitor(){
                @Override
                public void visitBeforeRecords(PresetRegistryBase registry) { }

                @Override
                public void visitRecord(int slotIndex, Object record) {
                    FenderJsonPresetRegistry.Record fjpr = (FenderJsonPresetRegistry.Record) record;
                    PresetRecord pr = new PresetRecord(fjpr.displayName(), slotIndex);
                    pr.m_state = PresetRecord.PresetState.ACCEPTED;
                    retval.add(pr);
                }

                @Override
                public void visitAfterRecords(PresetRegistryBase registry) {
                    appendToLog("Preset count: " + retval.m_presetRecords.size());
                }
            });
        }
        return retval;
    }

    public PresetRegistryBase getPresetRegistry() {
        return m_presetRegistry;
    }

    @Override
    public void switchPreset(int slotIndex) {
        m_protocol.switchPreset(slotIndex);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public ProviderState_e attemptConnection() {
        if(m_usbDevice==null)
        {
            UsbDevice usbDevice = null;
            HashMap<String, UsbDevice> usbDeviceMap = m_usbManager.getDeviceList();
            if (usbDeviceMap == null) {
                appendToLog("device map not received");
                m_state = ProviderState_e.PROVIDER_NO_APPLICABLE_DEVICE;
                return m_state;
            } else if (usbDeviceMap.size() == 0) {
                appendToLog("device map empty");
                m_state = ProviderState_e.PROVIDER_NO_APPLICABLE_DEVICE;
                return m_state;
            } else {
                for (String deviceName : usbDeviceMap.keySet()) {
                    usbDevice = usbDeviceMap.get(deviceName);
                    if (usbDevice.getVendorId() != 0x1ed8) {
                        appendToLog(String.format(
                            "non-FMIC device found with vid=%04x pid=%04x",
                            usbDevice.getVendorId(), usbDevice.getProductId()
                        ));
                        usbDevice = null;
                        continue;
                    } else {
                        appendToLog(String.format(
                            "FMIC device found with vid=%04x pid=%04x",
                            usbDevice.getVendorId(), usbDevice.getProductId()
                        ));
                        appendToLog(
                            "FMIC product name: " + usbDevice.getProductName()
                        );
                        m_usbDevice = usbDevice;
                        break;
                    }
                }
            }
            assert m_usbDevice!=null;
            if(m_usbHidDevice==null) {
                if (!m_usbManager.hasPermission(m_usbDevice)) {
                    registerForPermissionIntent();
                    m_usbManager.requestPermission(m_usbDevice, m_permissionIntent);
                    m_state = ProviderState_e.PROVIDER_CONNECTING_TO_DEVICE;
                    return m_state;
                }

                try {
                    m_state = ProviderState_e.PROVIDER_CONNECTING_TO_DEVICE;

                    // If we are still in the function at this point,
                    // we must have permission, so we try to connect
                    UsbDeviceConnection cxn = m_usbManager.openDevice(m_usbDevice);
                    if (cxn != null) {
                        UsbHidDevice m_usbHidDevice = UsbHidDevice.factory(
                            m_mainActivity,
                            m_usbDevice.getVendorId(), m_usbDevice.getProductId()
                        );
                        if (m_usbHidDevice == null) {
                            m_usbHidDevice.open(m_mainActivity,this);
                            appendToLog("No USB HID device found");
                            m_state = ProviderState_e.PROVIDER_DEVICE_CONNECTION_FAILED;
                            return m_state;
                        }
                        m_usbHidDevice.open(m_mainActivity, null);
                        m_protocol.setDeviceTransport(new DeviceTransportUsbHid(m_usbHidDevice));
                        m_connectionSucceeded = true;
                        m_state = ProviderState_e.PROVIDER_DEVICE_CONNECTION_SUCCEEDED;
                    } else {
                        m_state = ProviderState_e.PROVIDER_DEVICE_CONNECTION_FAILED;
                    }
                } catch (Exception e) {
                    appendToLog("Exception caught - see logcat for details");
                    System.err.println(e.toString());
                    e.printStackTrace(System.err);
                    m_state = ProviderState_e.PROVIDER_DEVICE_CONNECTION_FAILED;
                }
            }
        }
        return m_state;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    void registerForPermissionIntent() {
        if (m_usbReceiver == null) {
            m_usbReceiver = new UsbBroadcastReceiver();
            m_usbReceiver.setProvider(this);
            m_permissionIntent = PendingIntent.getBroadcast(
                m_mainActivity, 0,
                new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
            );
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            filter.addAction(UsbManager.EXTRA_PERMISSION_GRANTED);
            appendToLog("Registering for permission intent");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                m_mainActivity.registerReceiver(m_usbReceiver, filter, RECEIVER_NOT_EXPORTED);
            } else {
                m_mainActivity.registerReceiver(m_usbReceiver, filter);
            }
            appendToLog("Registered for permission intent");
        } else {
            appendToLog("Already registered for permission intent");
        }
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
}

