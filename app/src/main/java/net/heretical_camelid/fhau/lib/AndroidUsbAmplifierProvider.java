package net.heretical_camelid.fhau.lib;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import com.benlypan.usbhid.OnUsbHidDeviceListener;
import com.benlypan.usbhid.UsbHidDevice;

public class AndroidUsbAmplifierProvider implements IAmplifierProvider {
    UsbHidDevice m_device;
    UsbDevice m_usbDevice;

    @Override
    public boolean connect(
            Context context,
            OnUsbHidDeviceListener usbHidDeviceListener,
            StringBuilder sb
    ) {
        final boolean[] retval = {false};
        // Upstream UsbHid project searched for this VID/PID
        // UsbHidDevice device = UsbHidDevice.factory(this, 0x0680, 0x0180);
        // This fork searches for any device associated with the Fender VID
        m_device = UsbHidDevice.factory(context, 0x0, 0x0);
        if (m_device == null) {
            sb.append("No device found\n");
            return retval[0];
        }
        m_usbDevice = m_device.getUsbDevice();
        sb.append(String.format(
                "Device found: id=%d sn=%s vid=%04x pid=%04x pname=%s",
                m_device.getDeviceId(),
                m_device.getSerialNumber(),
                m_usbDevice.getVendorId(),
                m_usbDevice.getProductId(),
                m_usbDevice.getProductName()
        ));
        m_device.open(context, usbHidDeviceListener);
        return retval[0];
    }

    @Override
    public byte[] sendCommandAndReceiveResponse(String commandHexString, StringBuilder sb) {
        m_device.write(ByteArrayTranslator.hexToBytes(commandHexString));
        byte[] responseBytes = m_device.read(64);
        sb.append("Sent " + commandHexString + "\n");
        sb.append("Received " + ByteArrayTranslator.bytesToHex(responseBytes) + "\n");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            sb.append("sleep interrupted\n");
        }
        return responseBytes;
    }

    @Override
    public PresetInfo getPresets(PresetInfo requestedPresets) {
        assert requestedPresets == null;
        PresetInfo retval = new PresetInfo();
        PresetInfo.PresetRecord pr1 = new PresetInfo.PresetRecord("FENDER CLEAN",1);
        PresetInfo.PresetRecord pr2 = new PresetInfo.PresetRecord("SILKY SOLO",2);
        PresetInfo.PresetRecord pr3 = new PresetInfo.PresetRecord("CHICAGO BLUES",3);
        retval.add(pr1);
        retval.add(pr2);
        retval.add(pr3);
        return retval;
    }
}
