package net.heretical_camelid.maneline.desktop_app;

import net.heretical_camelid.maneline.lib.interfaces.IDeviceTransport;
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
        try {
            assert m_hidDevice!=null: "m_hidDevice==null";
            String lastErrorMessage = m_hidDevice.getLastErrorMessage();
            assert lastErrorMessage !=null: "m_hidDevice.getLastErrorMessage()==null";
            return lastErrorMessage;
        }
        catch(Exception e) {
            e.printStackTrace();
            System.out.print(e);
            return "Last error message not available";
        }
    }
}
