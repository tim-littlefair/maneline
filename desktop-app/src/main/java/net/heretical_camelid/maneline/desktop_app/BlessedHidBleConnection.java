package net.heretical_camelid.maneline.desktop_app;

import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothCentralManagerCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.ScanResult;
import com.welie.blessed.bluez.BluezAdapter;
import com.welie.blessed.bluez.BluezDevice;


import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public class BlessedHidBleConnection {
    /*
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

    public static BlessedHidBleConnection build(String service_uuid, int defaultTimeoutMsec) {
        m_deviceManager = null;
        BluetoothDevice mmpDevice = null;
        MethodCall.setDefaultTimeout(defaultTimeoutMsec);
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
        return new BlessedHidBleConnection(mmpDevice, service_uuid);
    }

    public BlessedHidBleConnection(BluetoothDevice device, String service_uuid) {
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
             * /
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
     */


    private static final String MMP_PERIPHERAL_NAME = "Mustang Micro Plus";
    private static final UUID BLP_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb");
    private static final UUID HTS_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb");
    private static final UUID BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb");
    private static final UUID TEMPERATURE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb");
    private static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static void main(String[] args) {
        _BCM_Callback bcmCallback =  new _BCM_Callback();
        BluetoothCentralManager bcm = new BluetoothCentralManager(bcmCallback);
        bcm.scanForPeripheralsWithNames(new String[] { "Mustang Micro Plus" });
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // BluezDevice bluezDevice = new BluezDevice();
        // BluetoothPeripheral peripheral = new BluetoothPeripheral(bcm, bluezDevice, MMP_PERIPHERAL_NAME, DUMMY_MAC_ADDRESS_BLP, null, peripheralCallback, callbackHandler);

        // BluetoothCentralManager bcm = new BluetoothCentralManager();
    }
}

class _BCM_Callback extends BluetoothCentralManagerCallback {
    @Override
    public void onDiscoveredPeripheral(@org.jetbrains.annotations.NotNull BluetoothPeripheral peripheral, @org.jetbrains.annotations.NotNull ScanResult scanResult) {
        super.onDiscoveredPeripheral(peripheral, scanResult);
    }
}
