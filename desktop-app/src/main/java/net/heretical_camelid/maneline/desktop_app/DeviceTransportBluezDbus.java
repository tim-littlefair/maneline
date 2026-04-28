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

import org.bluez.exceptions.BluezFailedException;
import org.bluez.exceptions.BluezInProgressException;
import org.bluez.exceptions.BluezInvalidValueLengthException;
import org.bluez.exceptions.BluezNotAuthorizedException;
import org.bluez.exceptions.BluezNotConnectedException;
import org.bluez.exceptions.BluezNotPermittedException;
import org.bluez.exceptions.BluezNotSupportedException;
import org.freedesktop.dbus.DBusMap;
import org.freedesktop.dbus.connections.base.DBusBoundPropertyHandler;
import org.freedesktop.dbus.errors.NoReply;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import com.github.hypfvieh.bluetooth.DiscoveryFilter;
import com.github.hypfvieh.bluetooth.DiscoveryTransport;

import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.handlers.AbstractSignalHandlerBase;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.messages.MethodCall;
import org.freedesktop.dbus.types.UInt16;

class ReceiverHeartbeat
    extends Thread
    implements Runnable, DBusSigHandler<Properties.PropertiesChanged>
{
    static final UInt16 HEARTBEAT_PERIOD_MS = new UInt16(5000);
    static ReceiverHeartbeat s_instance = null;
    static DeviceTransportBluezDbus s_transport = null;
    static boolean s_shouldStop = false;
    static boolean s_hasStopped = false;

    private ReceiverHeartbeat() { }
    static ReceiverHeartbeat startUp(
            DeviceManager deviceManager,
            DeviceTransportBluezDbus theTransport,
            BluetoothGattCharacteristic notifyChr
    ) {
        assert s_instance == null;
        assert s_shouldStop == false;
        s_instance = new ReceiverHeartbeat();
        s_transport = theTransport;
        s_transport.registerForNotifications(deviceManager, theTransport, notifyChr);
        s_instance.run();
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
            ++numSleeps;
        }
    }

    @Override
    public void run() {
        while(s_shouldStop==false) {
            Long nextHeartbeatTime = System.currentTimeMillis() + Long.valueOf(HEARTBEAT_PERIOD_MS.toString());
            try {
                while(System.currentTimeMillis()<nextHeartbeatTime) {
                    byte[] chunk = s_transport.receive(HEARTBEAT_PERIOD_MS);
                    System.out.println("Chunk: " + HexFormat.of().formatHex(chunk));
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
                s_transport.send("3500050a03c20100","request");
            } catch (DBusException e) {
                System.err.println("Receive timed out");
                System.exit(-108);
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
public class DeviceTransportBluezDbus
    extends AbstractPropertiesChangedHandler
    // implements IDeviceTransport
{
    public final static String FENDERTONE_SERVICE_UUID = "90559580-b707-11ee-acb1-7b7e30f1af54";
    public final static String FENDERTONE_HOGP_SEND_UUID = "820a7e34-4e0a-4f90-8520-04ebce35a3a1";
    public final static String FENDERTONE_HOGP_NTFY_UUID = "1017adcc-dcbc-4387-a59f-2546b2ea5bb0";

    final BluetoothDevice m_device;
    final BluetoothGattCharacteristic m_sendChr;
    final BluetoothGattCharacteristic m_notifyChr;



    public static void main( String[] args ) throws InterruptedException, IOException {

        MethodCall.setDefaultTimeout(30000);
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
        try {
            mmpDevice.connect();
        } catch (DBusExecutionException e) {
            System.out.println("FailedConnection " + e.getMessage());
            e.printStackTrace();
            System.exit(-105);
        }

        mmpDevice.refreshGattServices();
        List<BluetoothGattService> services = mmpDevice.getGattServices();
        for (BluetoothGattService service : services) {
            System.out.println("Service: " + service.getUuid());
            List<BluetoothGattCharacteristic> characteristics = service.getGattCharacteristics();
            characteristics.stream().map(e -> e.getUuid()).forEach(System.out::println);
        }
        BluetoothGattService mmpFendertoneService = mmpDevice.getGattServiceByUuid(FENDERTONE_SERVICE_UUID);
        BluetoothGattCharacteristic sendChr = mmpFendertoneService.getGattCharacteristicByUuid(FENDERTONE_HOGP_SEND_UUID);
        BluetoothGattCharacteristic notifyChr = mmpFendertoneService.getGattCharacteristicByUuid(FENDERTONE_HOGP_NTFY_UUID);
        System.out.println(sendChr.toString() + String.join(",",sendChr.getFlags()));
        System.out.println(notifyChr.toString() + String.join(",",notifyChr.getFlags()));

        DeviceTransportBluezDbus theTransport = new DeviceTransportBluezDbus(
            mmpDevice, sendChr, notifyChr
        );
        ReceiverHeartbeat.startUp(deviceManager, theTransport, notifyChr);
        try {
            theTransport.send("35000201a00", "request");
            theTransport.send("3500050a03c20100", "request");
            theTransport.send("3500040a023a00", "request");
            theTransport.send("3500040a027200", "command");
            System.out.println("Writes done");
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

    static void registerForNotifications(
        DeviceManager deviceManager,
        DeviceTransportBluezDbus theTransport,
        BluetoothGattCharacteristic notifyChr
    ) {
        try {
            deviceManager.registerPropertyHandler(theTransport);
        } catch (DBusException e) {
            System.err.println("Failed to register signal handler: " + e.getMessage());
            System.exit(-106);
        }
        try {
            notifyChr.startNotify();
        } catch (DBusException e) {
            System.err.println("Failed to start notifying: " + e.getMessage());
            System.exit(-107);
        }
    }

    public DeviceTransportBluezDbus(
        BluetoothDevice device,
        BluetoothGattCharacteristic sendChr,
        BluetoothGattCharacteristic notifyChr
    ) {
        m_device = device;
        m_sendChr = sendChr;
        m_notifyChr = notifyChr;
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

    public byte[] receive(UInt16 timeout) throws DBusException, NoReply {
        Map<String,Object> readOptions = new HashMap<>();
        readOptions.put("offset",new UInt16(0));
        //readOptions.put("mtu",new UInt16(128));
        //readOptions.put("timeout",timeout);
        return m_notifyChr.readValue(readOptions);
    }
}

