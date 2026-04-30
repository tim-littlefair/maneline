package net.heretical_camelid.maneline.desktop_app;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;

import org.freedesktop.dbus.errors.NoReply;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.UInt16;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public class BluezHoGPConnection extends AbstractPropertiesChangedHandler {
    final BluetoothDevice m_device;
    final BluetoothGattService m_service;
    final BluetoothGattCharacteristic m_sendChr;
    final BluetoothGattCharacteristic m_notifyChr;

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
            System.exit(-106);
        }
    }

    public void startNotify() {
        try {
            m_notifyChr.startNotify();
        } catch (DBusException e) {
            System.err.println("Failed to start notifying: " + e.getMessage());
            System.exit(-107);
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

    public byte[] receive(UInt16 timeout) throws DBusException, NoReply {
        Map<String,Object> readOptions = new HashMap<>();
        readOptions.put("offset",new UInt16(0));
        //readOptions.put("mtu",new UInt16(128));
        readOptions.put("timeout",timeout);
        return m_notifyChr.readValue(readOptions);
    }
}
