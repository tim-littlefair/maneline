package net.heretical_camelid.maneline.desktop_app;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.DiscoveryFilter;
import com.github.hypfvieh.bluetooth.DiscoveryTransport;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;

import org.bluez.datatypes.TwoTuple;
import org.freedesktop.dbus.FileDescriptor;
import org.freedesktop.dbus.errors.NoReply;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.MethodCall;
import org.freedesktop.dbus.transport.junixsocket.JUnixSocketSocketProvider;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.Variant;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

class ReceiverHeartbeat
    extends Thread
    implements Runnable, DBusSigHandler<Properties.PropertiesChanged>
{
    static final int HEARTBEAT_PERIOD_MS = 500;
    static ReceiverHeartbeat s_instance = null;
    static boolean s_shouldStop = false;
    static boolean s_hasStopped = false;

    static String s_heartbeatMessageHex = null;

    static BluezHidBleConnection s_connection = null;
    private ReceiverHeartbeat() { }
    static ReceiverHeartbeat startUp(BluezHidBleConnection theConnection, String heartbeatMessageHex) {
        assert s_instance == null;
        assert s_connection == null;
        assert s_shouldStop == false;
        s_connection = theConnection;
        s_instance = new ReceiverHeartbeat();
        s_heartbeatMessageHex = heartbeatMessageHex;
        s_instance.start();
        return s_instance;
    }
    static void requestStop() {
        if(s_instance==null) {
            // heartbeat was not started
            return;
        }
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

    // This function uses Thread.getId() which is deprecated in preference
    // to Thread.getThreadId() which is not because the latter is not available
    // at JavaVersion.VERSION_11 which is (presently) our preferred language baseline
    @SuppressWarnings("deprecation")
    public static void requestInterrupt() {
        if (s_instance == null) {
            // Don't interrupt until heartbeat has started
        } else if (s_instance.getId() == Thread.currentThread().getId()) {
            // Heartbeat thread should not interrupt itself
        } else {
            s_instance.interrupt();
        }
    }

    @Override
    public void run() {
        while(s_shouldStop==false) {
            try {
                Long nextHeartbeatTime = System.currentTimeMillis() + HEARTBEAT_PERIOD_MS;
                try {
                    s_connection.send(s_heartbeatMessageHex,"command");
                    System.out.println("#");
                } catch (DBusException e) {
                    System.err.println("Hearbeat send failed: " + e);
                }
                Thread.sleep(System.currentTimeMillis()+nextHeartbeatTime);
                try {
                    /*
                    while(System.currentTimeMillis()<nextHeartbeatTime) {
                        byte[] chunk = s_connection.receive(HEARTBEAT_PERIOD_MS);
                        if(chunk==null) {
                            break;
                        } else if( chunk.length==0) {
                            break;
                        }
                        System.out.println("Chunk: " + HexFormat.of().formatHex(chunk));
                        Thread.sleep(nextHeartbeatTime-System.currentTimeMillis());
                    }
                     */
                } catch (NoReply e) {
                    System.err.println("Receive timed out");
                } catch (DBusExecutionException e) {
                    System.err.println("DBusExecutionException: " + e);
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

public class BluezHidBleConnection extends AbstractPropertiesChangedHandler {
    public final static String CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb";
    private static DeviceManager m_deviceManager;

    final BluetoothDevice m_device;
    final BluetoothGattService m_service;
    BluetoothGattCharacteristic m_sendChr = null;
    BluetoothGattCharacteristic m_notifyChr = null;

    final JUnixSocketSocketProvider m_socketProvider;

    FileDescriptor m_notifyDebusFD = null;
    private UInt16 m_mtu = null;
    private DataInputStream m_notifyInputStream;

    public static BluezHidBleConnection build(String service_uuid, int bluezTimeoutMs) {
        m_deviceManager = null;
        BluetoothDevice mmpDevice = null;
        MethodCall.setDefaultTimeout(bluezTimeoutMs);
        try {
            m_deviceManager = DeviceManager.createInstance(false);
        } catch (DBusException e) {
            System.err.println("Failed to create device manager: " + e.getMessage());
            System.exit(-101);
        }
        List<BluetoothAdapter> result = m_deviceManager.getAdapters();
        try {
            Map<DiscoveryFilter, Object> mmpFilter = new HashMap<DiscoveryFilter, Object>();
            mmpFilter.put(DiscoveryFilter.Transport, DiscoveryTransport.LE);
            mmpFilter.put(DiscoveryFilter.UUIDs, new String[] { service_uuid });
            m_deviceManager.setScanFilter(mmpFilter);
        } catch (DBusException e) {
            System.err.println("Failed to set Bluetooth filter: " + e.getMessage());
            System.exit(-103);
        }
        List<BluetoothDevice> devices = m_deviceManager.scanForBluetoothDevices(5000);
        List<String> rejectedDeviceNames = new ArrayList<String>();
        if(devices.size()==1) {
            System.out.println("Only 1 device matches");
            mmpDevice = devices.get(0);
        } else if(devices.isEmpty()) {
            System.err.println("No devices found");
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
        System.out.println(mmpDevice);
        return new BluezHidBleConnection(mmpDevice, service_uuid);
    }

    public BluezHidBleConnection(BluetoothDevice device, String service_uuid) {
        m_device = device;
        m_service = m_device.getGattServiceByUuid(service_uuid);
        m_socketProvider = new JUnixSocketSocketProvider();
        for(BluetoothGattCharacteristic chr: m_service.getGattCharacteristics()) {
            if(chr.getFlags().contains("notify")) {
                m_notifyChr = chr;
            } else if(chr.getFlags().contains("write")) {
                m_sendChr = chr;
            }
        }
        assert m_notifyChr != null;
        assert m_sendChr != null;
    }

    public void doConnect() {
        try {
            if(m_device.isConnected()==false) {
                m_device.connect();
            }
            if(m_device.isPaired()==false) {
                boolean pairingOutcome = m_device.pair();
                System.out.println(String.format(
                    "Pairing required, outcome: " + pairingOutcome
                ));
            } else {
                System.out.println("Already paired");
            }
        } catch (DBusExecutionException e) {
            System.out.println("FailedConnection " + e.getMessage());
            e.printStackTrace();
            System.exit(-105);
        }
        for(int i = 0; i<12; ++i) {
            assert m_device.isConnected(): String.format("disconnected");
            m_device.refreshGattServices();
            if(m_device.isServicesResolved()) {
                break;
            } else {
                System.out.println("Still resolving services");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        assert m_device.isConnected(): String.format("disconnected");
        System.out.println(m_device.getGattServices());
    }

    public void startHeartbeat(String heartbeatMessageHex) {
        ReceiverHeartbeat.startUp(this, heartbeatMessageHex);
    }
    public void registerForNotifications() {
        try {
            m_deviceManager.registerPropertyHandler(this);
        } catch (DBusException e) {
            System.err.println("Failed to register signal handler: " + e.getMessage());
            System.exit(-111);
        }
    }

    public void startNotify() {
        try {
            m_notifyChr.startNotify();
        } catch (DBusException e) {
            System.err.println("Failed to start notifying: " + e.getMessage());
            System.exit(-112);
        } catch (NoReply e) {
            System.err.println("Failed to start notifying: " + e.getMessage());
            System.exit(-113);
        }
    }

    public void acquireNotify() {
        try {
            /*
            BluetoothGattDescriptor m_notifyCccd = m_notifyChr.getGattDescriptorByUuid(CCCD_UUID);
            System.out.println(m_notifyCccd.getFlags());
            Map<String,Object> cccdWriteOptions = new HashMap<>();
            m_notifyCccd.writeValue(new byte[] { 0x01, 0x00 }, cccdWriteOptions);
            System.out.println("acquireNotify");
             */
            Map<String,Variant<?>> acquireOptions = new HashMap<>();
            TwoTuple<FileDescriptor, UInt16> fd_mtu = m_notifyChr.getRawGattCharacteristic().AcquireNotify(acquireOptions);
            assert fd_mtu != null;
            FileDescriptor dbusFD = fd_mtu.getFirstValue();
            m_mtu = fd_mtu.getSecondValue();
            java.io.FileDescriptor jiFD = dbusFD.toJavaFileDescriptor(m_socketProvider);
            m_notifyInputStream = new DataInputStream(new FileInputStream(jiFD));
        } catch (DBusException e) {
            System.err.println("Failed to acquire notify fd: " + e.getMessage());
            System.exit(-113);
        }
    }

    @Override
    public void handle(Properties.PropertiesChanged _signal) {
        if(_signal.getInterfaceName().startsWith("org.bluez")) {
            System.out.println("?:" + _signal);
        }
    }

    public void send(String bytesAsHex, String writeType) throws DBusException {
        Map<String,Object> writeOptions = new HashMap<>();
        writeOptions.put("offset",new UInt16(0));
        //writeOptions.put("mtu",new UInt16(128));
        writeOptions.put( "type", writeType);
        m_sendChr.writeValue(HexFormat.of().parseHex(bytesAsHex),writeOptions);
    }

    public byte[] receive(int timeout) {
        if(m_notifyDebusFD == null) {
            Map<String, Object> readOptions = new HashMap<>();
            readOptions.put("offset", new UInt16(0));
            //readOptions.put("mtu",new UInt16(128));
            readOptions.put("timeout", new UInt16(timeout));
            try {
                return m_notifyChr.readValue(readOptions);
            }
            catch(DBusException|NoReply e) {
                System.err.println("Receive failed: " + e.getMessage());
                return null;
            }
        } else {
            byte[] buffer = new byte[m_mtu.intValue()-120];
            try {
                m_notifyInputStream.read(buffer);
                System.out.println("received " + HexFormat.of().formatHex(buffer));
                return buffer;
            } catch (IOException e) {
                System.err.println("Receive failed: " + e.getMessage());
                return null;
            }
        }
    }
}
