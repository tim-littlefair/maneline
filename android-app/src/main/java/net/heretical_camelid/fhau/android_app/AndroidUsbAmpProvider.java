package net.heretical_camelid.fhau.android_app;

import android.hardware.usb.UsbDevice;

import com.benlypan.usbhid.UsbHidDevice;

import net.heretical_camelid.fhau.lib.ByteArrayTranslator;
import net.heretical_camelid.fhau.lib.IAmpProvider;
import net.heretical_camelid.fhau.lib.ILoggingAgent;
import net.heretical_camelid.fhau.lib.PresetInfo;
import net.heretical_camelid.fhau.lib.PresetRecord;

public class AndroidUsbAmpProvider implements IAmpProvider {
    final ILoggingAgent m_loggingAgent;
    UsbHidDevice m_device;
    UsbDevice m_usbDevice;
    MainActivity m_mainActivity;

    AndroidUsbAmpProvider(
        ILoggingAgent loggingAgent,
        MainActivity mainActivity
    ) {
        m_loggingAgent = loggingAgent;
        m_mainActivity = mainActivity;
    }
    @Override
    public boolean connect() {
        final boolean[] retval = {false};
        // Upstream UsbHid project searched for this VID/PID
        // UsbHidDevice device = UsbHidDevice.factory(this, 0x0680, 0x0180);
        // This fork searches for any device associated with the Fender VID
        m_device = UsbHidDevice.factory(m_mainActivity, 0x0, 0x0);
        if (m_device == null) {
            m_loggingAgent.appendToLog(0,"No device found\n");
            return retval[0];
        }
        m_usbDevice = m_device.getUsbDevice();
        m_loggingAgent.appendToLog(0,String.format(
                "Device found: id=%d sn=%s vid=%04x pid=%04x pname=%s",
                m_device.getDeviceId(),
                m_device.getSerialNumber(),
                m_usbDevice.getVendorId(),
                m_usbDevice.getProductId(),
                m_usbDevice.getProductName()
        ));
        m_device.open(m_mainActivity, m_mainActivity);

        return retval[0];
    }

    @Override
    public void sendCommand(String commandHexString) {
        m_device.write(ByteArrayTranslator.hexToBytes(commandHexString));
        byte[] responseBytes = m_device.read(64);
        m_loggingAgent.appendToLog(0,"Sent " + commandHexString + "\n");
        m_loggingAgent.appendToLog(0,"Received " + ByteArrayTranslator.bytesToHex(responseBytes) + "\n");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            m_loggingAgent.appendToLog(0,"sleep interrupted\n");
        }
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
