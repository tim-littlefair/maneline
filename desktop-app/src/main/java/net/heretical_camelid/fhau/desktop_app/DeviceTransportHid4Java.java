package net.heretical_camelid.fhau.desktop_app;

import net.heretical_camelid.fhau.lib.interfaces.IDeviceTransport;
import org.hid4java.HidDevice;


class DeviceTransportHid4Java implements IDeviceTransport {
    final HidDevice m_hidDevice;

    DeviceTransportHid4Java(HidDevice hidDevice) {
        m_hidDevice = hidDevice;
    }

    @Override
    public int read(byte[] packetBuffer) {
        return m_hidDevice.read(packetBuffer, packetBuffer.length);
    }

    @Override
    public int write(byte[] commandBytes) {
        return m_hidDevice.write(commandBytes, commandBytes.length,(byte) 0,true);
    }

    @Override
    public String getLastErrorMessage() {
        return m_hidDevice.getLastErrorMessage();
    }
}
