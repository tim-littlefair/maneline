package net.heretical_camelid.fhau.android_app;

import com.benlypan.usbhid.UsbHidDevice;

import net.heretical_camelid.fhau.lib.DeviceTransportInterface;

class DeviceTransportUsbHid implements DeviceTransportInterface {
    final UsbHidDevice m_usbHidDevice;
    final static int TIMEOUT_MS = 500;

    DeviceTransportUsbHid(UsbHidDevice hidDevice) {
        m_usbHidDevice = hidDevice;
    }

    @Override
    public int read(byte[] packetBuffer) {
        byte[] bytesRead = m_usbHidDevice.read(packetBuffer.length,TIMEOUT_MS);
        if(bytesRead==null) {
            return 0;
        } else {
            System.arraycopy(bytesRead, 0, packetBuffer, 0, bytesRead.length);
            return bytesRead.length;
        }
    }

    @Override
    public int write(byte[] commandBytes) {
        m_usbHidDevice.write(commandBytes);
        return commandBytes.length;
    }
    @Override
    public String getLastErrorMessage() {
        return "not available";
    }
}
