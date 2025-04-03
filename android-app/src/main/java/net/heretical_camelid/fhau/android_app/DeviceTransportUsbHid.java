package net.heretical_camelid.fhau.android_app;

import com.benlypan.usbhid.UsbHidDevice;

import net.heretical_camelid.fhau.lib.interfaces.IDeviceTransport;

class DeviceTransportUsbHid implements IDeviceTransport {
    final UsbHidDevice m_usbHidDevice;
    final static int TIMEOUT_MS = 500;

    DeviceTransportUsbHid(UsbHidDevice hidDevice) {
        m_usbHidDevice = hidDevice;
    }

    @Override
    public int read(byte[] packetBuffer) {
        final int NUM_ATTEMPTS=3;
        final int MSEC_BETWEEN_ATTEMPTS=100;
        for(int i=0; i<NUM_ATTEMPTS; ++i) {
            try {
                byte[] bytesRead = m_usbHidDevice.read(packetBuffer.length,TIMEOUT_MS);
                if(bytesRead==null) {
                    return 0;
                } else {
                    System.arraycopy(bytesRead, 0, packetBuffer, 0, bytesRead.length);
                    return bytesRead.length;
                }
            }
            catch(IllegalArgumentException e) {
                try {
                    Thread.sleep(MSEC_BETWEEN_ATTEMPTS);
                } catch (InterruptedException ex) {
                    // Do nothing
                }
            }
        }
        return 0;
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
