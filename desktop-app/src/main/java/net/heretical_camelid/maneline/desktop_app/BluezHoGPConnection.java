package net.heretical_camelid.maneline.desktop_app;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;

import org.bluez.datatypes.TwoTuple;
import org.freedesktop.dbus.FileDescriptor;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.errors.NoReply;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.Variant;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public class BluezHoGPConnection extends AbstractPropertiesChangedHandler {
    final BluetoothDevice m_device;
    final BluetoothGattService m_service;
    final BluetoothGattCharacteristic m_sendChr;
    final BluetoothGattCharacteristic m_notifyChr;

    FileDescriptor m_notifyFD = null;
    private UInt16 m_mtu = null;

    public BluezHoGPConnection(
        BluetoothDevice device, String service_uuid, String send_uuid, String notify_uuid
    ) {
        m_device = device;
        m_service = m_device.getGattServiceByUuid(service_uuid);
        m_sendChr = m_service.getGattCharacteristicByUuid(send_uuid);
        m_notifyChr = m_service.getGattCharacteristicByUuid(notify_uuid);
        System.out.println(m_sendChr.toString() + String.join(",",m_sendChr.getFlags()));
        System.out.println(m_notifyChr.toString() + String.join(",",m_notifyChr.getFlags()));
    }

    public void registerForNotifications(DeviceManager deviceManager) {
        try {
            deviceManager.registerPropertyHandler(this);
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
            System.out.println("acquireNotify");
            Map<String,Variant<?>> acquireOptions = new HashMap<>();
            Tuple tuple = m_notifyChr.getRawGattCharacteristic().AcquireNotify(acquireOptions);
            System.out.println("Tuple: " + tuple.toString());
            TwoTuple<FileDescriptor, UInt16> fd_mtu = (TwoTuple<FileDescriptor, UInt16>) tuple;
            assert fd_mtu != null;
            m_notifyFD = fd_mtu.getFirstValue();
            m_mtu = fd_mtu.getSecondValue();

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

    public byte[] receive(UInt16 timeout) throws DBusException, NoReply, InterruptedException {
        if(m_notifyFD == null) {
            Map<String, Object> readOptions = new HashMap<>();
            readOptions.put("offset", new UInt16(0));
            //readOptions.put("mtu",new UInt16(128));
            readOptions.put("timeout", timeout);
            return m_notifyChr.readValue(readOptions);
        } else {
            Thread.sleep(250);
            System.out.println("read-after-acquire TBD");
            return new byte[] { };
        }
    }
}
