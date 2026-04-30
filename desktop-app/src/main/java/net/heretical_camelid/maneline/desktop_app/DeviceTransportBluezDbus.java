// The initial version of this file was based on:
// https://github.com/masato-ka/bluez-dbus-sample/blob/master/src/main/java/ka/masato/bluz_sample/App.java
package net.heretical_camelid.maneline.desktop_app;

// import net.heretical_camelid.maneline.lib.interfaces.IDeviceTransport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.errors.NoReply;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.DiscoveryFilter;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.MethodCall;
import org.freedesktop.dbus.types.UInt16;

class ReceiverHeartbeat
    extends Thread
    implements Runnable, DBusSigHandler<Properties.PropertiesChanged>
{
    static final UInt16 HEARTBEAT_PERIOD_MS = new UInt16(500);
    static ReceiverHeartbeat s_instance = null;
    static boolean s_shouldStop = false;
    static boolean s_hasStopped = false;

    static BluezHoGPConnection s_connection = null;
    private ReceiverHeartbeat() { }
    static ReceiverHeartbeat startUp(BluezHoGPConnection theConnection) {
        assert s_instance == null;
        assert s_connection == null;
        assert s_shouldStop == false;
        s_connection = theConnection;
        s_instance = new ReceiverHeartbeat();
        s_instance.start();
        return s_instance;
    }
    static void requestStop() {
        assert s_instance != null;
        s_shouldStop = true;
        int numSleeps = 0;
        int maxSleeps = 100;
        while(true) {
            if(s_hasStopped) {
                break;
            } else if(numSleeps==maxSleeps) {
                System.err.println("Deadlock!");
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                System.out.println("Heartbeat shutdown sleep interrupted: " + e);
            }
            ++numSleeps;
        }
    }

    public static void requestInterrupt() {
        if (s_instance == null) {
            // Don't interrupt until heartbeat has started
        } else if (s_instance.threadId() == Thread.currentThread().threadId()) {
            // Heartbeat thread should not interrupt itself
        } else {
            s_instance.interrupt();
        }
    }

    @Override
    public void run() {
        while(s_shouldStop==false) {
            try {
                Long nextHeartbeatTime = System.currentTimeMillis() + Long.valueOf(HEARTBEAT_PERIOD_MS.toString());
                try {
                    while(System.currentTimeMillis()<nextHeartbeatTime) {
                        byte[] chunk = s_connection.receive(HEARTBEAT_PERIOD_MS);
                        System.out.println("Chunk: " + HexFormat.of().formatHex(chunk));
                        if(chunk.length==0) {
                            break;
                        }
                        Thread.sleep(nextHeartbeatTime-System.currentTimeMillis());
                    }
                } catch (NoReply e) {
                    System.err.println("Receive timed out");
                } catch (DBusExecutionException e) {
                    System.err.println("DBusExecutionException: " + e);
                } catch (DBusException e) {
                    System.err.println("DBusException: " + e);
                    System.exit(-108);
                }
                try {
                    s_connection.send("3500050a03c20100","command");
                    System.out.println("#");
                } catch (DBusException e) {
                    System.err.println("Hearbeat send failed: " + e);
                }
            } catch (InterruptedException e) {
                // Do nothing
            }
        }
        s_hasStopped = true;
    }
    @Override
    public void handle(Properties.PropertiesChanged _signal) {
        if(_signal.getInterfaceName().startsWith("org.bluez")) {
            System.out.println("?:" + _signal);
        }
    }
}

public class DeviceTransportBluezDbus {
    // implements IDeviceTransport
    public final static String FENDERTONE_SERVICE_UUID = "90559580-b707-11ee-acb1-7b7e30f1af54";
    public final static String FENDERTONE_HOGP_SEND_UUID = "820a7e34-4e0a-4f90-8520-04ebce35a3a1";
    public final static String FENDERTONE_HOGP_NTFY_UUID = "1017adcc-dcbc-4387-a59f-2546b2ea5bb0";

    public static void main( String[] args ) throws InterruptedException, IOException {
        MethodCall.setDefaultTimeout(8000);
        DeviceManager deviceManager = null;
        try {
            deviceManager = DeviceManager.createInstance(false);
        } catch (DBusException e) {
            System.err.println("Failed to create device manager: " + e.getMessage());
            System.exit(-101);
        }
        List<BluetoothAdapter> result = deviceManager.getAdapters();
        BluetoothAdapter bluetoothAdaptor = result.get(0);
        try {
            Map<DiscoveryFilter, Object> mmpFilter = new HashMap<DiscoveryFilter, Object>();
            // mmpFilter.put(DiscoveryFilter.Transport, DiscoveryTransport.LE);
            mmpFilter.put(DiscoveryFilter.UUIDs, new String[] { FENDERTONE_SERVICE_UUID });
            deviceManager.setScanFilter(mmpFilter);
        } catch (DBusException e) {
            System.err.println("Failed to set Bluetooth filter: " + e.getMessage());
            System.exit(-103);
        }
        List<BluetoothDevice> devices = deviceManager.scanForBluetoothDevices(5000);
        BluetoothDevice mmpDevice = null;
        List<String> rejectedDeviceNames = new ArrayList<String>();
        if(devices.size()==1) {
            System.out.println("Only 1 device matches");
            mmpDevice = devices.get(0);
        } else if(devices.size()==0) {
            System.err.println("No devices found");
            mmpDevice = null;
            System.exit(-103);
        } else {
            for(BluetoothDevice btDevice: devices) {
                String candidateDeviceName = btDevice.getName();
                if (candidateDeviceName.equals("Mustang Micro Plus")) {
                    mmpDevice = btDevice;
                    break;
                } else {
                    rejectedDeviceNames.add(candidateDeviceName);
                }
            }
            if(rejectedDeviceNames.size() == devices.size() ) {
                System.err.println(
                    "Failed to find MMP device, candidates were: " +
                        String.join(", ", rejectedDeviceNames)
                );
                mmpDevice = null;
                System.exit(-104);
            }
        }
        assert mmpDevice != null;
        for(int i = 0; i<3; ++i) {
            mmpDevice.refreshGattServices();
            if(mmpDevice.isServicesResolved()) {
                System.out.println("All services resolved");
                System.out.println(mmpDevice.getGattServices());
                break;
            } else {
                System.out.println("Still resolving services");
            }
        }
        try {
            mmpDevice.connect();
        } catch (DBusExecutionException e) {
            System.out.println("FailedConnection " + e.getMessage());
            e.printStackTrace();
            System.exit(-105);
        }
        DeviceTransportBluezDbus theTransport = new DeviceTransportBluezDbus(mmpDevice);
        try {
            theTransport.acquireNotify();
            //theTransport.startNotify();
            //theTransport.registerForNotifications(deviceManager);
            theTransport.send("35000201a0", "command");
            theTransport.send("3500050a03c20100", "command");
            theTransport.send("3500040a023a00", "command");
            System.out.println("Initial writes done");
            theTransport.send("3500040a027200", "command");
            ReceiverHeartbeat.startUp(theTransport.m_connection);
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


    final private BluezHoGPConnection m_connection;
    public DeviceTransportBluezDbus(
        BluetoothDevice device
    ) {
        m_connection = new BluezHoGPConnection(
            device,
            FENDERTONE_SERVICE_UUID,
            FENDERTONE_HOGP_SEND_UUID,
            FENDERTONE_HOGP_NTFY_UUID
        );
    }

    public void registerForNotifications(DeviceManager deviceManager) {
        m_connection.registerForNotifications(deviceManager);
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

    public byte[] receive(UInt16 timeout) throws DBusException, NoReply, InterruptedException {
        return m_connection.receive(timeout);
    }
}

