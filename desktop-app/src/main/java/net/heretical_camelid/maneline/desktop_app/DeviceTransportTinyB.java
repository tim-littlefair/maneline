package net.heretical_camelid.maneline.desktop_app;

import net.heretical_camelid.maneline.lib.interfaces.IDeviceTransport;

import java.util.List;

// /*
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.AdapterDiscoveryListener;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.DeviceDiscoveryListener;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.impl.BluetoothManagerBuilder;
// import org.sputnikdev.bluetooth.manager.transport.bluegiga.BluegigaFactory;
import org.sputnikdev.bluetooth.manager.transport.tinyb.TinyBFactory;
import org.sputnikdev.bluetooth.manager.BluetoothManager;
// */

import tinyb.*;

public class DeviceTransportTinyB implements IDeviceTransport {
    @Override
    public int read(byte[] packetBuffer) {
        return 0;
    }

    @Override
    public int write(byte[] commandBytes) {
        return 0;
    }

    @Override
    public String getLastErrorMessage() {
        return "";
    }

    static boolean running = true;

    static void printDevice(BluetoothDevice device) {
        System.out.print("Address = " + device.getAddress());
        System.out.print(" Name = " + device.getName());
        System.out.print(" Connected = " + device.getConnected());
        System.out.println();
    }
    /*
    static BluetoothDevice getDevice(String address) throws InterruptedException {
        boolean nativeLoadStatus = TinyBFactory.loadNativeLibraries();
        System.out.println("nls="+nativeLoadStatus);
        BluetoothManager mgr =  new BluetoothManagerBuilder()
            .withTinyBTransport(true)
            .build()
        ;
        BluetoothDevice sensor = null;
        for (int i = 0; (i < 15) && running; ++i) {
            List<BluetoothDevice> list = mgr.getDevices();
            if (list == null)
                return null;

            for (BluetoothDevice device : list) {
                printDevice(device);
                /*
                 * Here we check if the address matches.
                 * /
                if (device.getAddress().equals(address))
                    sensor = device;
            }

            if (sensor != null) {
                return sensor;
            }
            Thread.sleep(4000);
        }
        return null;
    }
    */

    static {
        try {
            System.loadLibrary("tinyb");
            System.loadLibrary("javatinyb");
        } catch (UnsatisfiedLinkError var1) {
            System.err.println("Native code library failed to load.\n" + var1);
        }
    }

    public static void main(String args[]) {
        boolean nativeLoadStatus = TinyBFactory.loadNativeLibraries();
        System.out.println("nls="+nativeLoadStatus);
        BluetoothManager mgr =  new BluetoothManagerBuilder()
            .withTinyBTransport(true)
            .build()
            ;
        mgr.registerFactory(new TinyBFactory());
        DeviceTransportTinyB instance = new DeviceTransportTinyB();
        System.out.println("TinyBFactory registered");

        mgr.getCharacteristicGovernor(
            new URL("/XX:XX:XX:XX:XX:XX/F7:EC:62:B9:CF:1F/"
                + "0000180f-0000-1000-8000-00805f9b34fb/00002a19-0000-1000-8000-00805f9b34fb"
            ),
            true
        )
            .whenReady(CharacteristicGovernor::read)
            .thenAccept(data -> {
                System.out.println("Battery level: " + data[0]);
            })
        ;

        /*
        BluetoothManager manager = BluetoothManager.getBluetoothManager();
        manager.addDeviceDiscoveryListener(instance);
        manager.addAdapterDiscoveryListener(instance);
        //String var0 = tinyb.BluetoothManager.getNativeAPIVersion();
        String var1 = tinyb.BluetoothManager.class.getPackage().getSpecificationVersion();
        System.out.println("sv="+var1);
        manager.start(true);
        String targetDeviceAddress = "84:17:15:2B:4E:7E";
        BluetoothManager manager = BluetoothManager.getBluetoothManager();;
        boolean discoveryStarted = manager.startDiscovery();
        try {
            BluetoothDevice sensor = getDevice(targetDeviceAddress);
            manager.stopDiscovery();
            if (sensor == null) {
                System.err.println("No sensor found with the provided address.");
                System.exit(-1);
            }
        } catch (BluetoothException e) {
            System.err.println("Discovery could not be stopped.");
        } catch (InterruptedException e) {
            System.err.println("Discovery was interrupted.");
        }
         */
        System.exit(0);
    }


}
