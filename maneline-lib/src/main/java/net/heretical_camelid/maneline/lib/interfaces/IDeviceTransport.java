package net.heretical_camelid.maneline.lib.interfaces;

/**
 * This interface abstracts the different implementations
 * required to support USB HID transport on Android and
 * Linux desktop environments.
 */
public interface IDeviceTransport {
    int read(byte[] packetBuffer);
    int write(byte[] commandBytes);
    String getLastErrorMessage();
}
