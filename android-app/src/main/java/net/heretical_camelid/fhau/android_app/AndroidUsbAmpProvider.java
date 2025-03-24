package net.heretical_camelid.fhau.android_app;

import android.content.Context;
import android.hardware.usb.UsbDevice;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import com.benlypan.usbhid.UsbHidDevice;

import net.heretical_camelid.fhau.lib.*;

public class AndroidUsbAmpProvider implements IAmpProvider {
    final ILoggingAgent m_loggingAgent;
    UsbHidDevice m_device;
    UsbDevice m_usbDevice;
    MainActivity m_mainActivity;
    UsbManager m_usbManager;
    PresetRegistryBase m_presetRegistry;
    AbstractMessageProtocolBase m_protocol;
    String m_firmwareVersion;
    PresetInfo m_presetInfo;

    AndroidUsbAmpProvider(
        ILoggingAgent loggingAgent,
        MainActivity mainActivity
    ) {
        m_loggingAgent = loggingAgent;
        m_mainActivity = mainActivity;
        m_presetRegistry = new FenderJsonPresetRegistry(null);
        m_protocol = new LTSeriesProtocol(m_presetRegistry);
        m_usbManager = (UsbManager) m_mainActivity.getSystemService(Context.USB_SERVICE);

    }

    public boolean getFirmwareVersionAndPresets() {
        m_protocol.setDeviceTransport(new DeviceTransportUsbHid(m_device));
        String[] firmwareVersionHolder = new String[] { null };
        int startupStatus = m_protocol.doStartup(firmwareVersionHolder);
        m_firmwareVersion = firmwareVersionHolder[0];
        m_loggingAgent.appendToLog(0,"Firmware Version: " + m_firmwareVersion);
        int presetNamesStatus = m_protocol.getPresetNamesList();
        m_loggingAgent.appendToLog(0,String.format(
            "Amp contains %d unique presets", m_presetRegistry.uniquePresetCount()
        ));
        return startupStatus== AbstractMessageProtocolBase.STATUS_OK;
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
                    m_loggingAgent.appendToLog(0,
                        "Preset count: " + retval.m_presetRecords.size()
                    );
                }
            });
        }
        return retval;
    }

    public PresetRegistryBase getPresetRegistry() {
        return m_presetRegistry;
    }

    public boolean attemptConnection(UsbDevice device) {
        m_device = UsbHidDevice.factory(
            m_mainActivity, device.getVendorId(), device.getProductId()
        );
        if (m_device == null) {
            m_loggingAgent.appendToLog(0,
                "No USB HID device found"
            );
            return false;
        }
        m_usbDevice = m_device.getUsbDevice();
        if (m_usbDevice == null) {
            m_loggingAgent.appendToLog(0,
                "m_device.getUsbDevice() returned null"
            );
            return false;
        }
        m_device.open(m_mainActivity, null);
        UsbDeviceConnection deviceConnection = m_usbManager.openDevice(m_usbDevice);
        if(deviceConnection==null) {
            m_loggingAgent.appendToLog(0,
                "m_usbManager.openDevice(m_usbDevice) returned null"
            );
            return false;
        }
        getFirmwareVersionAndPresets();
        return true;
    }
}
