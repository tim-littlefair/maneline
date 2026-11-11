"""Linux: control the Micro Plus over its LE bearer only, so Bluetooth audio keeps playing.

The Micro Plus is a dual-mode device: audio arrives over classic Bluetooth (BR/EDR,
A2DP) while presets are controlled over a GATT service on Bluetooth LE. Bleak's
BlueZ backend models a whole device: it calls Device1.Connect and Device1.Disconnect,
and a background task sends Device1.Disconnect when the event loop shuts down. Each
of those also drops the A2DP link, and while audio is connected Bleak considers the
device connected already and never brings up LE.

BlueZ with `Experimental = true` exports org.bluez.Bearer.LE1, whose Connect and
Disconnect touch the LE link alone. LeBearerClient uses that plus the plain BlueZ
GATT objects, and offers the small part of the BleakClient API used by Controller.

Linux therefore needs no Bleak at all: the amp has to be paired anyway, so find_device
asks BlueZ for the paired device instead of scanning. Bleak is imported on other
platforms only (macOS).
"""
import asyncio
import sys
import xml.etree.ElementTree as ElementTree

BLUEZ = 'org.bluez'
DEVICE = 'org.bluez.Device1'
LE_BEARER = 'org.bluez.Bearer.LE1'
CHARACTERISTIC = 'org.bluez.GattCharacteristic1'
MEDIA_TRANSPORT = 'org.bluez.MediaTransport1'
DEVICE_NAME = '00002a00-0000-1000-8000-00805f9b34fb'
PROPERTIES = 'org.freedesktop.DBus.Properties'
OBJECT_MANAGER = 'org.freedesktop.DBus.ObjectManager'
INTROSPECTABLE = 'org.freedesktop.DBus.Introspectable'
# Replies that mean the characteristic is not ready yet, right after the LE link came up.
NOT_READY = ('org.bluez.Error.Failed', 'org.bluez.Error.NotConnected',
             'org.bluez.Error.InProgress', 'org.freedesktop.DBus.Error.UnknownObject',
             'org.freedesktop.DBus.Error.UnknownMethod')


LINUX = sys.platform.startswith('linux')
NOT_PAIRED = ('Micro Plus not found among the paired Bluetooth devices. Pair it first, '
              'see docs/MUSTANG_MICRO_PLUS_LINUX.md.')
NO_LE_BEARER = ('BlueZ offers no org.bluez.Bearer.LE1 for the Micro Plus. Control needs '
                'BlueZ 5.84 or later with "Experimental = true" in /etc/bluetooth/main.conf, '
                'see docs/MUSTANG_MICRO_PLUS_LINUX.md.')
NO_ANSWER = ('The Micro Plus does not answer. Turn it on and close Fender Tone on other '
             'devices. If it is already on, switch it off and on again: its Bluetooth can '
             'stop answering while the amp itself keeps working.')


class Device:
    """A device BlueZ knows, with what the callers use of a Bleak scan result."""

    def __init__(self, path, address, name):
        self.address = address
        self.name = name
        self.details = {'path': path}


def device_path(device):
    """The BlueZ object path of a device, or None (other platforms, fakes)."""
    details = getattr(device, 'details', None)
    return details.get('path') if isinstance(details, dict) else None


async def paired_devices(name, address=None, service=None):
    """The paired devices BlueZ knows that offer that GATT service, without scanning.

    The service identifies the amp whatever it is called. The name only counts for a
    device whose services BlueZ does not list (yet). A paired device is listed whether
    it is switched on or not; connecting tells.
    """
    try:
        from dbus_fast import BusType, Message, MessageType
        from dbus_fast.aio import MessageBus
    except ImportError:
        raise RuntimeError('The Python package dbus-fast is missing.') from None
    try:
        bus = await MessageBus(bus_type=BusType.SYSTEM).connect()
    except OSError as error:
        raise RuntimeError(f'The D-Bus system bus is not reachable ({error}).') from None
    try:
        reply = await bus.call(Message(destination=BLUEZ, path='/', interface=OBJECT_MANAGER,
                                       member='GetManagedObjects'))
    finally:
        bus.disconnect()
    if reply.message_type == MessageType.ERROR:
        raise RuntimeError(f'BlueZ does not answer ({reply.error_name}). '
                           'Is the bluetooth service running?')

    def value(properties, key):
        variant = properties.get(key)
        return variant.value if variant is not None else None

    found = []
    for path, interfaces in sorted(reply.body[0].items()):
        properties = interfaces.get(DEVICE)
        if not properties or not value(properties, 'Paired'):
            continue
        device = Device(path, value(properties, 'Address') or '',
                        value(properties, 'Name') or value(properties, 'Alias'))
        services = [uuid.lower() for uuid in value(properties, 'UUIDs') or ()]
        offered = service is not None and service.lower() in services
        if (offered or device.name == name) and (address is None
                                                 or device.address.upper() == address.upper()):
            found.append(device)
    return found


async def find_devices(name, address=None, timeout=10, service=None, **scan_filter):
    """Every Micro Plus in reach: on Linux the paired ones, elsewhere a Bleak scan."""
    if LINUX:
        return await paired_devices(name, address, service)
    from bleak import BleakScanner
    devices = await BleakScanner.discover(timeout=timeout, return_adv=True, **scan_filter)
    return [device for device, advert in devices.values()
            if (advert.local_name == name or device.name == name)
            and (address is None or device.address == address)]


async def find_device(name, address=None, timeout=10, service=None, **scan_filter):
    """One Micro Plus or None: on Linux the paired one, elsewhere the first one scanned."""
    if LINUX:
        return next(iter(await paired_devices(name, address, service)), None)
    from bleak import BleakScanner
    return await BleakScanner.find_device_by_filter(
        lambda device, advert: (device.name == name or advert.local_name == name)
        and (address is None or device.address == address), timeout=timeout, **scan_filter)


def not_found(message):
    """Why find_device returned None: on Linux the amp is not paired, else the caller's text."""
    return NOT_PAIRED if LINUX else message


async def le_bearer_available(path):
    """True if BlueZ offers Bearer.LE1.Connect for this device (needs Experimental = true)."""
    try:
        from dbus_fast import BusType, Message, MessageType
        from dbus_fast.aio import MessageBus
    except ImportError:
        return False
    bus = await MessageBus(bus_type=BusType.SYSTEM).connect()
    try:
        reply = await bus.call(Message(destination=BLUEZ, path=path, interface=INTROSPECTABLE,
                                       member='Introspect'))
        if reply.message_type == MessageType.ERROR:
            return False
        for interface in ElementTree.fromstring(reply.body[0]).iter('interface'):
            if interface.get('name') == LE_BEARER:
                return any(m.get('name') == 'Connect' for m in interface.iter('method'))
        return False
    finally:
        bus.disconnect()


async def make_client(device, timeout=None, disconnected_callback=None):
    """An unconnected client: LeBearerClient on Linux, BleakClient elsewhere."""
    if LINUX:
        path = device_path(device)
        if not (path and await le_bearer_available(path)):
            raise RuntimeError(NO_LE_BEARER)
        return LeBearerClient(path, disconnected_callback=disconnected_callback,
                              timeout=timeout or 15)
    from bleak import BleakClient
    options = {'timeout': timeout, 'disconnected_callback': disconnected_callback}
    return BleakClient(device, **{k: v for k, v in options.items() if v is not None})


class AmpBusy(RuntimeError):
    """Another program on this computer already receives the amp's replies."""


class Characteristic:
    def __init__(self, uuid, path, flags):
        self.uuid = uuid
        self.path = path
        self.properties = list(flags)


class Services:
    def __init__(self, characteristics):
        self.characteristics = characteristics

    def get_characteristic(self, uuid):
        return self.characteristics.get(uuid)


class LeBearerClient:
    """BleakClient subset bound to the LE bearer; never calls Device1.Disconnect."""

    def __init__(self, path, disconnected_callback=None, timeout=15):
        self.path = path
        self.timeout = timeout
        self.disconnected_callback = disconnected_callback
        self.services = Services({})
        self._bus = None
        self._connected = False
        self._notify = {}          # characteristic path -> (Characteristic, callback)

    @property
    def is_connected(self):
        return self._connected

    async def __aenter__(self):
        await self.connect()
        return self

    async def __aexit__(self, *_exc):
        await self.disconnect()

    async def _call(self, path, interface, member, signature='', body=(), destination=BLUEZ):
        from dbus_fast import Message
        return await self._bus.call(Message(destination=destination, path=path,
                                            interface=interface, member=member,
                                            signature=signature, body=list(body)))

    @staticmethod
    def _error(reply):
        from dbus_fast import MessageType
        if reply.message_type != MessageType.ERROR:
            return None
        return reply.error_name, (reply.body[0] if reply.body else '')

    async def connect(self):
        from dbus_fast import BusType
        from dbus_fast.aio import MessageBus
        # A new D-Bus connection per session, as Bleak does: BlueZ ties notification
        # subscriptions to the connection that made them.
        self._bus = await MessageBus(bus_type=BusType.SYSTEM).connect()
        self._bus.add_message_handler(self._on_message)
        for rule in (f"type='signal',sender='{BLUEZ}',interface='{PROPERTIES}',"
                     f"member='PropertiesChanged',path_namespace='{self.path}'",
                     f"type='signal',sender='{BLUEZ}',interface='{LE_BEARER}',"
                     f"member='Disconnected',path='{self.path}'",
                     "type='signal',sender='org.freedesktop.DBus',interface='org.freedesktop.DBus',"
                     f"member='NameOwnerChanged',arg0='{BLUEZ}'"):
            await self._call('/org/freedesktop/DBus', 'org.freedesktop.DBus', 'AddMatch', 's',
                             [rule], destination='org.freedesktop.DBus')
        try:
            async with asyncio.timeout(self.timeout):
                error = self._error(await self._call(self.path, LE_BEARER, 'Connect'))
                if error and error[0] != 'org.bluez.Error.AlreadyConnected':
                    raise RuntimeError(f'LE connection failed: {error[0]} {error[1]}'.strip())
                self._connected = True
                await self._discover()
                await self._wait_for_gatt()
        except TimeoutError:
            # A paired amp is known to BlueZ while it is switched off, so this is where
            # an absent amp shows (a scan used to tell before connecting).
            await self.disconnect()
            raise RuntimeError(NO_ANSWER) from None
        except BaseException:
            await self.disconnect()
            raise

    async def _discover(self):
        """Wait until BlueZ exports the GATT characteristics below the device."""
        prefix = self.path + '/'
        while True:
            reply = await self._call('/', OBJECT_MANAGER, 'GetManagedObjects')
            error = self._error(reply)
            if error:
                raise RuntimeError(f'Reading BlueZ objects failed: {error[0]}')
            found = {}
            for path, interfaces in reply.body[0].items():
                properties = interfaces.get(CHARACTERISTIC)
                if path.startswith(prefix) and properties:
                    uuid = properties['UUID'].value
                    found[uuid] = Characteristic(uuid, path, properties['Flags'].value)
            if found:
                self.services = Services(found)
                return
            await asyncio.sleep(0.1)

    async def _wait_for_gatt(self):
        """Wait until BlueZ's GATT client works over the new LE link.

        Connect returns as soon as the radio link exists; the GATT client is ready about a
        second later. Until then BlueZ still exports the characteristics of the previous
        session, so their presence proves nothing, and Device1.ServicesResolved can already
        be true from the audio connection's service browse. A read has to cross the LE link,
        so retry one until it succeeds: the GAP Device Name, which every LE device has.
        """
        characteristics = self.services.characteristics.values()
        probe = (self.services.get_characteristic(DEVICE_NAME)
                 or next((c for c in characteristics if 'read' in c.properties), None))
        if probe is not None:
            await self._until_ready(probe.path, 'ReadValue', 'a{sv}', [{}])

    async def _until_ready(self, path, member, signature='', body=()):
        """Call a characteristic method, retrying while the LE GATT client initialises."""
        while True:
            if not self._connected:
                raise RuntimeError('Micro Plus disconnected')
            error = self._error(await self._call(path, CHARACTERISTIC, member, signature, body))
            if not error:
                return
            if error[0] not in NOT_READY:
                raise RuntimeError(f'{member} failed: {error[0]} {error[1]}'.strip())
            await asyncio.sleep(0.2)

    async def start_notify(self, uuid, callback):
        characteristic = self.services.get_characteristic(uuid)
        if characteristic is None:
            raise RuntimeError(f'Characteristic {uuid} was not found')
        if await self._notifying(characteristic.path):
            # Someone else is subscribed. BlueZ would deliver every notification to both
            # programs twice, corrupting multi-frame messages, and closing the LE link
            # later would cut the other program off. Leave without touching the link.
            self._connected = False
            await self.disconnect()
            raise AmpBusy('Another program on this computer is already controlling the '
                          'Micro Plus, for example the Maneline web server. Stop it first: '
                          'two programs at once corrupt each other\'s messages.')
        self._notify[characteristic.path] = (characteristic, callback)
        async with asyncio.timeout(self.timeout):
            await self._until_ready(characteristic.path, 'StartNotify')

    async def _notifying(self, path):
        reply = await self._call(path, PROPERTIES, 'Get', 'ss', [CHARACTERISTIC, 'Notifying'])
        return not self._error(reply) and bool(reply.body) and bool(reply.body[0].value)

    async def write_gatt_char(self, characteristic, data, response=False):
        from dbus_fast import Variant
        if isinstance(characteristic, str):
            characteristic = self.services.get_characteristic(characteristic)
        options = {'type': Variant('s', 'request' if response else 'command')}
        error = self._error(await self._call(characteristic.path, CHARACTERISTIC, 'WriteValue',
                                             'aya{sv}', [bytes(data), options]))
        if error:
            raise RuntimeError(f'Write failed: {error[0]} {error[1]}'.strip())

    async def audio_streaming(self):
        """True while Bluetooth audio is streaming to this device.

        Lets a caller keep the LE link only while it costs little: next to an active
        A2DP stream the LE link's wake-ups hardly matter, while on an idle device they
        are a large share of what the amp's radio does.
        """
        if self._bus is None:
            return False
        reply = await self._call('/', OBJECT_MANAGER, 'GetManagedObjects')
        if self._error(reply):
            return False
        prefix = self.path + '/'
        for path, interfaces in reply.body[0].items():
            transport = interfaces.get(MEDIA_TRANSPORT)
            if path.startswith(prefix) and transport and \
                    transport['State'].value in ('pending', 'active'):
                return True
        return False

    async def disconnect(self):
        bus = self._bus
        if bus is None:
            return
        try:
            if self._connected:
                for path in list(self._notify):
                    await self._call(path, CHARACTERISTIC, 'StopNotify')
                # Only the LE link: Device1.Disconnect would end the audio stream too.
                await self._call(self.path, LE_BEARER, 'Disconnect')
        finally:
            self._connected = False
            self._notify.clear()
            self._bus = None
            bus.disconnect()

    def _lost(self):
        if self._connected:
            self._connected = False
            if self.disconnected_callback:
                self.disconnected_callback(self)

    def _on_message(self, message):
        """Dispatch notifications and notice when the LE link (not the device) goes away."""
        member, path, body = message.member, message.path, message.body
        if member == 'PropertiesChanged' and body:
            interface, changed = body[0], body[1]
            if interface == CHARACTERISTIC and 'Value' in changed and path in self._notify:
                characteristic, callback = self._notify[path]
                callback(characteristic, bytearray(changed['Value'].value))
                return None
            if (path == self.path and interface in (LE_BEARER, DEVICE)
                    and 'Connected' in changed and not changed['Connected'].value):
                self._lost()
        elif member == 'Disconnected' and message.interface == LE_BEARER and path == self.path:
            self._lost()
        elif member == 'NameOwnerChanged' and body and body[0] == BLUEZ and not body[2]:
            self._lost()      # bluetoothd stopped
        return None
