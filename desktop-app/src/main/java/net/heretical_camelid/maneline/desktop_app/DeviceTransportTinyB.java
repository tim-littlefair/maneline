package net.heretical_camelid.maneline.desktop_app;

import net.heretical_camelid.maneline.lib.interfaces.IDeviceTransport;

import tinyb.*;

public class DeviceTransportTinyB implements IDeviceTransport {
    @Override
    public int read(byte[] packetBuffer) {
        return 0;
    }

    @Override
    public int write(byte[] commandBytes) {
        return 0;
    }

    @Override
    public String getLastErrorMessage() {
        return "";
    }

    public static void main(String args[]) {
        System.out.println("HW");
        System.exit(0);
    }
}
