package net.heretical_camelid.fhau.lib;

/**
 * This interface defines the behaviour an implemention of
 * FMICMessageProtocol requires in order to be able to send
 * and receive messages and retrieve error state.
 * Note that it is modelled on the methods provided
 * by hid4java's HidDevice class which are used by
 * n.h_c.f.desktop_app.DeviceTransportHid4Java, but
 * allows dependency on the interface to be localized
 * to n.h_c.f.lib so that an alternate implementation
 * can be used in n.h_c.f.android_app where hid4java
 * is not available.
 */
public interface DeviceTransportInterface {
    int read(byte[] packetBuffer);
    int write(byte[] commandBytes);
    String getLastErrorMessage();
}
