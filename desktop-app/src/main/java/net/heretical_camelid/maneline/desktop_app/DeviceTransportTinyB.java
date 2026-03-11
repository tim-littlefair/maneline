package net.heretical_camelid.maneline.desktop_app;

import net.heretical_camelid.maneline.lib.interfaces.IDeviceTransport;

import java.util.List;

// /*
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.AdapterDiscoveryListener;
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.DeviceDiscoveryListener;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.ManagerListener;
import org.sputnikdev.bluetooth.manager.impl.BluetoothManagerBuilder;
// import org.sputnikdev.bluetooth.manager.transport.bluegiga.BluegigaFactory;
import org.sputnikdev.bluetooth.manager.transport.tinyb.TinyBFactory;
import org.sputnikdev.bluetooth.manager.BluetoothManager;
// */

import tinyb.*;

public class DeviceTransportTinyB
    implements IDeviceTransport, ManagerListener, GovernorListener, DeviceDiscoveryListener, AdapterDiscoveryListener {
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

        DeviceTransportTinyB theTransport = instance;
        mgr.addManagerListener(theTransport);
        mgr.addAdapterDiscoveryListener(theTransport);
        mgr.addDeviceDiscoveryListener(theTransport);
        DeviceGovernor dg = mgr.getDeviceGovernor(new URL("/XX:XX:XX:XX:XX:XX/84:17:15:2B:4E:7E"));
        System.out.println(dg.getResolvedServices());
        mgr.start(true);
        String targetDeviceAddress = "";
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        mgr.dispose();
        System.exit(0);
    }


    @Override
    public void ready(BluetoothGovernor governor, boolean ready) {
        System.err.println(governor.getURL());
    }

    @Override
    public void ready(boolean isReady) {

    }

    @Override
    public void discovered(DiscoveredDevice discoveredDevice) {
        System.err.println(discoveredDevice.getName());
    }

    @Override
    public void discovered(DiscoveredAdapter adapter) {
        System.err.println(adapter.getName());
    }
}
