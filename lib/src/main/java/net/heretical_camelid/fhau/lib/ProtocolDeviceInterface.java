package net.heretical_camelid.fhau.lib;

public interface ProtocolDeviceInterface {

    int read(byte[] packetBuffer, int i);

    String getLastErrorMessage();

    int write(byte[] commandBytes, int i, byte b, boolean b1);
}
