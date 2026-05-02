package net.heretical_camelid.maneline.desktop_app;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattDescriptor;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;

import org.bluez.datatypes.TwoTuple;
import org.freedesktop.dbus.FileDescriptor;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.errors.NoReply;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.transport.junixsocket.JUnixSocketSocketProvider;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.Variant;
import org.newsclub.net.unix.FileDescriptorCast;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public class BluezHoGPConnection extends AbstractPropertiesChangedHandler {
    public final static String CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    final BluetoothDevice m_device;
    final BluetoothGattService m_service;
    BluetoothGattCharacteristic m_sendChr = null;
    BluetoothGattCharacteristic m_notifyChr = null;

    final JUnixSocketSocketProvider m_socketProvider;

    FileDescriptor m_notifyDebusFD = null;
    private UInt16 m_mtu = null;
    private DataInputStream m_notifyInputStream;

    public BluezHoGPConnection(BluetoothDevice device, String service_uuid) {
        m_device = device;
        m_service = m_device.getGattServiceByUuid(service_uuid);
        m_socketProvider = new JUnixSocketSocketProvider();
        for(BluetoothGattCharacteristic chr: m_service.getGattCharacteristics()) {
            if(chr.getFlags().contains("notify")) {
                m_notifyChr = chr;
                /*
                for(BluetoothGattDescriptor dsc: m_notifyChr.getGattDescriptors()) {
                    System.out.println(dsc.getUuid());
                }
                 */
            } else if(chr.getFlags().contains("write")) {
                m_sendChr = chr;
            }
        }
        assert m_notifyChr != null;
        assert m_sendChr != null;
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

    public byte[] receive(UInt16 timeout) throws DBusException, NoReply, InterruptedException {
        if(m_notifyDebusFD == null) {
            Map<String, Object> readOptions = new HashMap<>();
            readOptions.put("offset", new UInt16(0));
            //readOptions.put("mtu",new UInt16(128));
            readOptions.put("timeout", timeout);
            return m_notifyChr.readValue(readOptions);
        } else {
            byte[] buffer = new byte[m_mtu.intValue()-120];
            try {
                m_notifyInputStream.read(buffer);
                System.out.println("received " + HexFormat.of().formatHex(buffer));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return buffer;
        }
    }
}
