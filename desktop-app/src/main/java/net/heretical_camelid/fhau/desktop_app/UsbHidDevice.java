package net.heretical_camelid.fhau.desktop_app;

import net.heretical_camelid.fhau.lib.ProtocolDeviceInterface;
import org.hid4java.HidDevice;

class UsbHidDevice implements ProtocolDeviceInterface {
    final HidDevice m_hidDevice;

    UsbHidDevice(HidDevice hidDevice) {
        m_hidDevice = hidDevice;
    }

    @Override
    public int read(byte[] packetBuffer, int i) {
        return m_hidDevice.read(packetBuffer, i);
    }

    @Override
    public String getLastErrorMessage() {
        return m_hidDevice.getLastErrorMessage();
    }

    @Override
    public int write(byte[] commandBytes, int i, byte b, boolean b1) {
        return m_hidDevice.write(commandBytes, i, b, b1);
    }
}
