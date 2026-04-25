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
import org.freedesktop.dbus.connections.base.DBusBoundPropertyHandler;
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
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.messages.MethodCall;
import org.freedesktop.dbus.types.UInt16;

class DBusSignalHandler extends AbstractSignalHandlerBase<DBusSignal> {
    @Override
    public void handle(org.freedesktop.dbus.messages.DBusSignal _signal) {
        System.out.println("?:" + _signal);
    }

    @Override
    public Class<org.freedesktop.dbus.messages.DBusSignal> getImplementationClass() {
        if(1==1) {
            return org.freedesktop.dbus.messages.DBusSignal.class;
        } else {
            return org.freedesktop.dbus.messages.DBusSignal.class;
        }
        // return DBusSignal.class;
    }
}

class GattPropertyChangeHandler extends AbstractPropertiesChangedHandler {
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
        deviceManager.findBtDevicesByIntrospection(bluetoothAdaptor);

        List<BluetoothDevice> devices = deviceManager.getDevices();
        BluetoothDevice mmpDevice;
        List<String> rejectedDeviceNames = new ArrayList<String>();
        if(devices.size()==1) {
            mmpDevice = devices.get(0);
        } else if(devices.size()==0) {
            mmpDevice = null;
        } else {
            mmpDevice = null;
            for(BluetoothDevice btDevice: devices) {
                String candidateDeviceName = btDevice.getName();
                if (candidateDeviceName.equals("Mustang Micro Plus")) {
                    mmpDevice = btDevice;
                    break;
                } else {
                    rejectedDeviceNames.add(candidateDeviceName);
                }
            }
        }
        if(mmpDevice == null) {
            System.err.println(
                "Failed to find MMP device, candidates were: " +
                String.join(", ", rejectedDeviceNames)
            );
            System.exit(-103);
        }
        try {
            mmpDevice.connect();
        } catch (DBusExecutionException e) {
            System.out.println("FailedConnection " + e.getMessage());
            e.printStackTrace();
            System.exit(-104);
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
        DeviceTransportBluezDbus theTransport = new DeviceTransportBluezDbus(sendChr, notifyChr);
        try {
            deviceManager.registerPropertyHandler(theTransport);
        } catch (DBusException e) {
            System.err.println("Failed to register signal handler: " + e.getMessage());
            System.exit(-102);
        }

        try {
            notifyChr.startNotify();
        } catch (DBusException e) {
            System.err.println("Failed to start notifying: " + e.getMessage());
            System.exit(-105);
        }
        try {
            theTransport.send("3500050a03c20100", "request");
            theTransport.send("3500040a023a00", "request");
            theTransport.send("3500040a027200", "command");
            System.out.println("Writes done");
            Thread.sleep(10000);
        } catch (DBusException e3) {
            System.err.println("Failed initial sends: " + e3.getMessage());
            System.exit(-106);
        }
    }

    public DeviceTransportBluezDbus(
        BluetoothGattCharacteristic sendChr, BluetoothGattCharacteristic notifyChr
    ) {
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
        writeOptions.put( "type", writeType);
        m_sendChr.writeValue(HexFormat.of().parseHex(bytesAsHex),writeOptions);
    }
}

