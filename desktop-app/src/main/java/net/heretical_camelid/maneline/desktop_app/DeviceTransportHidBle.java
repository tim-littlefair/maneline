// The initial version of this file was based on:
// https://github.com/masato-ka/bluez-dbus-sample/blob/master/src/main/java/ka/masato/bluz_sample/App.java
package net.heretical_camelid.maneline.desktop_app;

// import net.heretical_camelid.maneline.lib.interfaces.IDeviceTransport;

import java.io.IOException;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.UInt16;

public class DeviceTransportHidBle {
    // implements IDeviceTransport
    public final static String FENDERTONE_SERVICE_UUID = "90559580-b707-11ee-acb1-7b7e30f1af54";

    final private BluezHidBleConnection m_connection;
    public DeviceTransportHidBle() {
        m_connection = BluezHidBleConnection.build(FENDERTONE_SERVICE_UUID);
        m_connection.doConnect();
    }

    public void registerForNotifications() {
        m_connection.registerForNotifications();
    }

    public void startHeartbeat(String heartbeatMessageHex) {
        m_connection.startHeartbeat(heartbeatMessageHex);
    }

    public void acquireNotify() {
        m_connection.acquireNotify();
    }
    public void startNotify() {
        m_connection.startNotify();
    }
    public void send(String bytesAsHex, String writeType) throws DBusException {
        ReceiverHeartbeat.requestInterrupt();
        m_connection.send(bytesAsHex, writeType);
    }

    public byte[] receive(UInt16 timeout) {
        return m_connection.receive(timeout);
    }

    public static void main( String[] args ) throws InterruptedException, IOException {

        DeviceTransportHidBle theTransport = new DeviceTransportHidBle();
        try {
            //theTransport.acquireNotify();
            //theTransport.startNotify();
            theTransport.registerForNotifications();
            theTransport.send("35000201a0", "command");
            theTransport.send("3500050a03c20100", "command");
            theTransport.send("3500040a023a00", "command");
            System.out.println("Initial writes done");
            theTransport.send("3500040a027200", "command");
            theTransport.startHeartbeat("3500050a03c20100");
            for(int i=0; i<20; ++i) {
                System.out.println(".");
                Thread.sleep(1000);
            }
            ReceiverHeartbeat.requestStop();
        } catch (DBusException e3) {
            System.err.println("Failed initial sends: " + e3.getMessage());
            System.exit(-108);
        }
    }
}

