package net.heretical_camelid.fhau.android_app;

import android.app.PendingIntent;
import android.content.Context;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import com.benlypan.usbhid.UsbHidDevice;

import net.heretical_camelid.fhau.lib.*;

import java.util.HashMap;

import static androidx.core.content.ContextCompat.registerReceiver;

public class AndroidUsbAmpProvider implements IAmpProvider {
    final ILoggingAgent m_loggingAgent;
    UsbHidDevice m_device;
    UsbDevice m_usbDevice;
    MainActivity m_mainActivity;
    UsbManager m_usbManager;
    PresetRegistryBase m_presetRegistry;
    AbstractMessageProtocolBase m_protocol;
    String m_firmwareVersion;

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

    public boolean connect(int vendorId, int productId) {
        m_device = UsbHidDevice.factory(m_mainActivity, vendorId, productId);
        if (m_device == null) {
            m_loggingAgent.appendToLog(0,"No device found\n");
            return false;
        }
        m_usbDevice = m_device.getUsbDevice();
        assert m_usbDevice != null;
        m_device.open(m_mainActivity, m_mainActivity);
        UsbDeviceConnection deviceConnection = m_usbManager.openDevice(m_usbDevice);
        assert deviceConnection!=null;
        m_protocol.setDeviceTransport(new DeviceTransportUsbHid(m_device));
        String[] firmwareVersionHolder = new String[] { null };
        int startupStatus = m_protocol.doStartup(firmwareVersionHolder);
        m_firmwareVersion = firmwareVersionHolder[0];
        m_loggingAgent.appendToLog(0,"Firmware Version: " + m_firmwareVersion);
        return startupStatus==AbstractMessageProtocolBase.STATUS_OK;
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
        assert requestedPresets == null;
        PresetInfo retval = new PresetInfo();
        PresetRecord pr1 = new PresetRecord("FENDER CLEAN",1);
        PresetRecord pr2 = new PresetRecord("SILKY SOLO",2);
        PresetRecord pr3 = new PresetRecord("CHICAGO BLUES",3);
        retval.add(pr1);
        retval.add(pr2);
        retval.add(pr3);
        return retval;
    }
}
