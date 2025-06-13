package org.github.hypfvieh.sandbox.bluez;

import java.util.HashMap;
import java.util.Map;

import org.bluez.Adapter1;
import org.bluez.Device1;
import org.bluez.GattCharacteristic1;
import org.bluez.GattManager1;
import org.bluez.GattProfile1;
import org.bluez.exceptions.BluezFailedException;
import org.bluez.exceptions.BluezNotAuthorizedException;
import org.bluez.exceptions.BluezNotReadyException;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnection.DBusBusType;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.handlers.AbstractInterfacesAddedHandler;
import org.freedesktop.dbus.handlers.AbstractInterfacesRemovedHandler;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.interfaces.Properties.PropertiesChanged;
import org.freedesktop.dbus.types.Variant;

public class SimpleBluez implements DBusInterface, ObjectManager {

    private GattProfile1Impl     profile;
    private DBusConnection       connection;

    private Map<String, Device1> btDevices = new HashMap<>();

    public SimpleBluez() throws DBusException {
        // open connection to bluez on SYSTEM Bus
        connection = DBusConnection.getConnection(DBusBusType.SYSTEM);
        // create profile to export
        profile = new GattProfile1Impl("/com/github/hypfvieh/bluez/Paulmannprofile");
    }

    public void register() throws DBusException {

        connection.exportObject(getObjectPath(), this);

        addPropertiesChangedListener();

        addInterfacesAddedListener();

        addInterfacesRemovedListener();

        // get the GattManager to register new profile
        GattManager1 gattmanager = connection.getRemoteObject("org.bluez", "/org/bluez/hci0", GattManager1.class);

        System.out.println("Registering: " + this.getObjectPath());

        // register profile
        gattmanager.RegisterApplication(new DBusPath(this.getObjectPath()), new HashMap<>());

    }

    private void addInterfacesRemovedListener() throws DBusException {
        connection.addSigHandler(InterfacesRemoved.class,
            new AbstractInterfacesRemovedHandler() {
                @Override
                public void handle(InterfacesRemoved _s) {
                    if (_s != null) {
                        if (_s.getInterfaces().contains(Device1.class.getName())) {
                            System.out.println("Bluetooth device removed: " + _s.getSignalSource());
                            btDevices.remove(_s.getPath());
                        }

                        System.out.println("InterfaceRemoved ----> " + _s.getInterfaces());
                    }

                }

            });
    }

    private void addInterfacesAddedListener() throws DBusException {
        connection.addSigHandler(InterfacesAdded.class,
            new AbstractInterfacesAddedHandler() {

                @Override
                public void handle(InterfacesAdded _s) {
                    if (_s != null) {
                        Map<String, Map<String, Variant<?>>> interfaces = _s.getInterfaces();
                        interfaces.entrySet().stream().filter(e -> e.getKey().equals(Device1.class.getName()))
                            .forEach(e -> {
                                Variant<?> address = e.getValue().get("Address");
                                if (address != null && address.getValue() != null) {
                                    System.out.println("Bluetooth device added: " + address.getValue());
                                    String p = _s.getSignalSource().getPath();
                                    try {
                                        Device1 device1 =
                                            connection.getRemoteObject("org.bluez", p, Device1.class);
                                        btDevices.put(p, device1);
                                    } catch (DBusException _ex) {
                                        // TODO Auto-generated catch block
                                        _ex.printStackTrace();
                                    }
                                }
                            });

                        interfaces.entrySet().stream()
                            .filter(e -> e.getKey().equals(GattCharacteristic1.class.getName())).forEach(e -> {
                                System.out.println("New characteristics: " + e.getValue());
                            });
                        // System.out.println("InterfaceAdded ----> " + _s.getInterfaces());
                    }

                }

            });
    }

    private void addPropertiesChangedListener() throws DBusException {
        connection.addSigHandler(PropertiesChanged.class,
            new AbstractPropertiesChangedHandler() {

                @Override
                public void handle(PropertiesChanged _s) {
                    if (_s != null) {

                        if (!_s.getPath().contains("/org/bluez")
                            && !_s.getPath().contains(getClass().getPackage().getName())) { // filter all events
                            // not belonging to
                            // bluez
                            return;
                        }

                        // if (_s.get)
                        System.err.println("PropertiesChanged:----> " + _s.getPropertiesChanged());
                        if (!_s.getPropertiesRemoved().isEmpty())
                            System.err.println("PropertiesRemoved:----> " + _s.getPropertiesRemoved());
                    }
                }

            });
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public String getObjectPath() {
        return "/" + getClass().getName().replace(".", "/");
    }

    @Override
    public Map<DBusPath, Map<String, Map<String, Variant<?>>>> GetManagedObjects() {
        System.out.println("GetManagedObjects Called");
        Map<DBusPath, Map<String, Map<String, Variant<?>>>> outerMap = new HashMap<>();

        outerMap.put(new DBusPath(profile.getObjectPath()), profile.getProperties());

        return outerMap;
    }

    protected void scan(int _i) {
        System.out.println("Scanning for " + _i + " seconds");
        Adapter1 adapter = null;
        try {
            adapter = connection.getRemoteObject("org.bluez", "/org/bluez/hci0", Adapter1.class);
            adapter.StartDiscovery();
            Thread.sleep(_i * 1000);

        } catch (DBusException | InterruptedException _ex) {
            // TODO Auto-generated catch block
            _ex.printStackTrace();
        } finally {
            if (adapter != null) {
                try {
                    adapter.StopDiscovery();
                } catch (BluezNotReadyException | BluezFailedException | BluezNotAuthorizedException _ex) {
                    // TODO Auto-generated catch block
                    _ex.printStackTrace();
                }
            }
        }
        System.out.println("Scanning for finished");
    }

    /*
     * =================================================================
     *
     * STATIC STUFF
     *
     * =================================================================
     */

    public static void main(String[] args) {
        Thread thread = new Thread("MyThread") {
            private boolean running = true;

            @Override
            public void run() {
                System.out.println("Init");
                SimpleBluez simpleBluez = null;
                try {
                    simpleBluez = new SimpleBluez();
                    System.out.println("Registering");
                    simpleBluez.register();
                    System.out.println("Waiting");
                    while (running) {
                        try {
                            Thread.sleep(500L);
                        } catch (InterruptedException _ex) {
                            running = false;
                        }
                    }
                } catch (Exception _ex) {
                    // TODO Auto-generated catch block
                    _ex.printStackTrace();
                } finally {
                    running = false;
                    System.out.println("Terminating");
                    if (simpleBluez != null) {
                        simpleBluez.connection.disconnect();
                    }
                }

            }

        };

        thread.start();
    }

    static class ObjectManagerHandler implements ObjectManager {

        @Override
        public boolean isRemote() {
            return false;
        }

        @Override
        public String getObjectPath() {
            return "/";
        }

        @Override
        public Map<DBusPath, Map<String, Map<String, Variant<?>>>> GetManagedObjects() {
            System.err.println(this.getClass() + " Getmanagedobjects called");
            return null;
        }

    }

    static class GattProfile1Impl implements GattProfile1, Properties {
        private boolean                              released;
        private String                               path;

        private Map<String, Map<String, Variant<?>>> properties = new HashMap<>();

        public GattProfile1Impl(String _path) {
            released = false;
            path = _path;

            Map<String, Variant<?>> map = new HashMap<>();
            map.put("UUIDs", new Variant<>(new String[] {
                "0000ffb0-0000-1000-8000-00805f9b34fb"
            }));

            properties.put(GattProfile1.class.getName(), map);
        }

        @Override
        public boolean isRemote() {
            return false;
        }

        public Map<String, Map<String, Variant<?>>> getProperties() {
            return properties;
        }

        @Override
        public String getObjectPath() {
            return path;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void Release() {
            released = true;
        }

        public boolean isReleased() {
            System.out.println("released called");
            return released;
        }

        @Override
        public <A> A Get(String _interface_name, String _property_name) {
            System.out.println("Get called");
            // Variant<?> variant = properties.get(_interface_name).get(_property_name);
            return null; //
        }

        @Override
        public <A> void Set(String _interface_name, String _property_name, A _value) {
            System.out.println("Set called");

        }

        @Override
        public Map<String, Variant<?>> GetAll(String _interface_name) {
            System.out.println("queried for: " + _interface_name);
            return properties.get(_interface_name);
        }

    }

}