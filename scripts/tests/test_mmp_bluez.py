"""LE-bearer client for Linux: keeps Bluetooth audio up, without a real BlueZ."""
import asyncio
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import mmp_bluez  # noqa: E402
from mmp_ble import REQUEST, RESPONSE  # noqa: E402

try:
    from dbus_fast import Message, MessageType, Variant
except ImportError:          # macOS: Bleak uses CoreBluetooth and dbus-fast is absent
    Message = None

DEV = '/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF'
RESPONSE_PATH = DEV + '/service0015/char0016'
NAME_PATH = DEV + '/service0007/char0008'
REQUEST_PATH = DEV + '/service0015/char001a'


def reply(body=(), signature='', error=None):
    if error:
        return types.SimpleNamespace(message_type=MessageType.ERROR, error_name=error,
                                     body=['test failure'])
    return types.SimpleNamespace(message_type=MessageType.METHOD_RETURN, body=list(body))


class FakeBus:
    """Records method calls; answers from a per-member queue of replies."""

    def __init__(self, answers=None):
        self.calls = []
        self.answers = answers or {}
        self.handler = None
        self.closed = False

    async def connect(self):
        return self

    def add_message_handler(self, handler):
        self.handler = handler

    async def call(self, message):
        self.calls.append((message.path, message.interface, message.member))
        queue = self.answers.get(message.member)
        return queue.pop(0) if queue else reply()

    def disconnect(self):
        self.closed = True


def managed_objects():
    def characteristic(uuid, flags):
        return {mmp_bluez.CHARACTERISTIC: {'UUID': Variant('s', uuid),
                                            'Flags': Variant('as', flags)}}
    return reply([{NAME_PATH: characteristic(mmp_bluez.DEVICE_NAME, ['read']),
                   RESPONSE_PATH: characteristic(RESPONSE, ['read', 'notify']),
                   REQUEST_PATH: characteristic(REQUEST, ['write-without-response', 'write']),
                   DEV: {mmp_bluez.DEVICE: {}}}])


def signal(path, interface, member, signature, body):
    return Message(path=path, interface=interface, member=member, signature=signature,
                   body=body, message_type=MessageType.SIGNAL)


NO_BLEAK = {'bleak': None}         # importing bleak raises ImportError


class ClientSelectionTests(unittest.IsolatedAsyncioTestCase):
    async def test_other_platforms_use_bleak(self):
        made = []

        class FakeBleakClient:
            def __init__(self, device, **options):
                made.append((device, options))

        class FakeScanner:
            @staticmethod
            async def find_device_by_filter(match, **options):
                made.append(options)
                return device if match(device, types.SimpleNamespace(local_name=None)) else None

        device = types.SimpleNamespace(address='test-device', name='Amp')
        fake_bleak = types.SimpleNamespace(BleakClient=FakeBleakClient, BleakScanner=FakeScanner)
        with patch.dict(sys.modules, {'bleak': fake_bleak}), patch('mmp_bluez.LINUX', False):
            self.assertIs(await mmp_bluez.find_device('Amp', service_uuids=['x']), device)
            self.assertIsNone(await mmp_bluez.find_device('Amp', 'another-device'))
            client = await mmp_bluez.make_client(device, disconnected_callback=print)
            self.assertEqual(mmp_bluez.not_found('caller text'), 'caller text')
        self.assertIsInstance(client, FakeBleakClient)
        self.assertEqual(made, [{'timeout': 10, 'service_uuids': ['x']}, {'timeout': 10},
                                (device, {'disconnected_callback': print})])

    async def test_linux_without_le_bearer_explains_instead_of_using_bleak(self):
        async def unavailable(path):
            return False

        for device in (types.SimpleNamespace(address='test-device'),
                       mmp_bluez.Device(DEV, 'AA:BB:CC:DD:EE:FF', 'Amp')):
            with patch.dict(sys.modules, NO_BLEAK), patch('mmp_bluez.LINUX', True), \
                 patch('mmp_bluez.le_bearer_available', unavailable), \
                 self.assertRaisesRegex(RuntimeError, 'Experimental = true'):
                await mmp_bluez.make_client(device)

    async def test_linux_with_le_bearer_needs_no_bleak(self):
        async def available(path):
            return path == DEV

        with patch.dict(sys.modules, NO_BLEAK), patch('mmp_bluez.LINUX', True), \
             patch('mmp_bluez.le_bearer_available', available):
            client = await mmp_bluez.make_client(mmp_bluez.Device(DEV, 'AA:BB:CC:DD:EE:FF', 'Amp'),
                                                 disconnected_callback=print)
            self.assertEqual(mmp_bluez.not_found('caller text'), mmp_bluez.NOT_PAIRED)
        self.assertIsInstance(client, mmp_bluez.LeBearerClient)
        self.assertEqual((client.path, client.disconnected_callback, client.timeout),
                         (DEV, print, 15))

    def test_device_path_comes_from_bluez_details(self):
        self.assertEqual(mmp_bluez.device_path(types.SimpleNamespace(details={'path': DEV})), DEV)
        self.assertIsNone(mmp_bluez.device_path(types.SimpleNamespace(details=object())))


@unittest.skipIf(Message is None, 'dbus-fast is only used on Linux')
class PairedDeviceTests(unittest.IsolatedAsyncioTestCase):
    """Linux finds the amp among BlueZ's paired devices: no scan, no Bleak."""
    OTHER = '/org/bluez/hci0/dev_11_22_33_44_55_66'
    SEEN = '/org/bluez/hci0/dev_77_88_99_AA_BB_CC'
    RENAMED = '/org/bluez/hci0/dev_DD_EE_FF_00_11_22'

    def objects(self):
        def device(address, paired, **names):
            properties = {'Address': Variant('s', address), 'Paired': Variant('b', paired)}
            properties.update({key: Variant('s', name) for key, name in names.items()})
            return {mmp_bluez.DEVICE: properties}
        return reply([{
            '/org/bluez/hci0': {'org.bluez.Adapter1': {}},
            self.OTHER: device('11:22:33:44:55:66', True, Name='Headphones', Alias='Headphones'),
            self.SEEN: device('77:88:99:AA:BB:CC', False, Name='Amp', Alias='Amp'),   # scanned once
            DEV: device('AA:BB:CC:DD:EE:FF', True, Name='Amp', Alias='Practice amp'),
            self.RENAMED: device('DD:EE:FF:00:11:22', True, Alias='Amp'),             # no Name
            RESPONSE_PATH: {mmp_bluez.CHARACTERISTIC: {'UUID': Variant('s', RESPONSE)}}}])

    async def find(self, function, *args, answer=None, **options):
        bus = FakeBus({'GetManagedObjects': [answer or self.objects()]})
        with patch('dbus_fast.aio.MessageBus', lambda **_: bus), \
             patch.dict(sys.modules, NO_BLEAK), patch('mmp_bluez.LINUX', True):
            result = await function(*args, **options)
        self.assertEqual(bus.calls, [('/', mmp_bluez.OBJECT_MANAGER, 'GetManagedObjects')])
        self.assertTrue(bus.closed)
        return result

    async def test_only_paired_devices_of_that_name(self):
        devices = await self.find(mmp_bluez.find_devices, 'Amp', timeout=3, service_uuids=['x'])
        self.assertEqual([(d.address, d.name, mmp_bluez.device_path(d)) for d in devices],
                         [('AA:BB:CC:DD:EE:FF', 'Amp', DEV),
                          ('DD:EE:FF:00:11:22', 'Amp', self.RENAMED)])

    async def test_one_device_by_address_in_any_case(self):
        device = await self.find(mmp_bluez.find_device, 'Amp', 'dd:ee:ff:00:11:22')
        self.assertEqual(mmp_bluez.device_path(device), self.RENAMED)
        self.assertEqual(mmp_bluez.device_path(await self.find(mmp_bluez.find_device, 'Amp')), DEV)

    async def test_none_when_not_paired(self):
        self.assertIsNone(await self.find(mmp_bluez.find_device, 'Amp', '77:88:99:AA:BB:CC'))
        self.assertIsNone(await self.find(mmp_bluez.find_device, 'Mustang'))
        self.assertEqual(await self.find(mmp_bluez.find_devices, 'Mustang'), [])

    async def test_no_system_bus_is_an_error(self):
        def no_bus(**_):
            raise FileNotFoundError(2, 'No such file or directory')

        with patch('dbus_fast.aio.MessageBus', no_bus), patch('mmp_bluez.LINUX', True), \
             self.assertRaisesRegex(RuntimeError, 'system bus'):
            await mmp_bluez.find_device('Amp')

    async def test_bluez_not_running_is_an_error(self):
        error = reply(error='org.freedesktop.DBus.Error.ServiceUnknown')
        with self.assertRaisesRegex(RuntimeError, 'bluetooth service'):
            await self.find(mmp_bluez.find_device, 'Amp', answer=error)


@unittest.skipIf(Message is None, 'dbus-fast is only used on Linux')
class LeBearerTests(unittest.IsolatedAsyncioTestCase):
    async def connected(self, answers=None):
        bus = FakeBus(answers)
        client = mmp_bluez.LeBearerClient(DEV, timeout=2)
        with patch('dbus_fast.aio.MessageBus', lambda **_: bus):
            await client.connect()
        return client, bus

    async def test_connect_brings_up_only_the_le_bearer(self):
        error = reply(error='org.bluez.Error.AlreadyConnected')
        client, bus = await self.connected({'Connect': [error],
                                            'GetManagedObjects': [managed_objects()]})
        self.assertTrue(client.is_connected)
        self.assertEqual(bus.calls.count((DEV, mmp_bluez.LE_BEARER, 'Connect')), 1)
        self.assertNotIn(mmp_bluez.DEVICE, [interface for _, interface, _ in bus.calls])
        self.assertEqual(client.services.get_characteristic(REQUEST).properties,
                         ['write-without-response', 'write'])

    async def test_connect_waits_until_a_read_crosses_the_le_link(self):
        not_ready = reply(error='org.bluez.Error.Failed')
        client, bus = await self.connected({'GetManagedObjects': [managed_objects()],
                                            'ReadValue': [not_ready, not_ready]})
        reads = [call for call in bus.calls if call[2] == 'ReadValue']
        self.assertEqual(reads, [(NAME_PATH, mmp_bluez.CHARACTERISTIC, 'ReadValue')] * 3)
        members = [member for *_, member in bus.calls]
        self.assertNotIn('StartNotify', members)
        self.assertNotIn('WriteValue', members)
        self.assertTrue(client.is_connected)

    async def test_failed_le_connect_raises(self):
        bus = FakeBus({'Connect': [reply(error='org.bluez.Error.Failed')]})
        client = mmp_bluez.LeBearerClient(DEV, timeout=2)
        with patch('dbus_fast.aio.MessageBus', lambda **_: bus), \
             self.assertRaises(RuntimeError):
            await client.connect()
        self.assertFalse(client.is_connected)
        self.assertTrue(bus.closed)

    async def test_amp_that_is_off_does_not_answer(self):
        class SilentBus(FakeBus):
            async def call(self, message):
                if message.member == 'Connect':
                    await asyncio.sleep(60)
                return await super().call(message)

        bus = SilentBus()
        client = mmp_bluez.LeBearerClient(DEV, timeout=0.05)
        with patch('dbus_fast.aio.MessageBus', lambda **_: bus), \
             self.assertRaisesRegex(RuntimeError, 'Turn it on'):
            await client.connect()
        self.assertFalse(client.is_connected)
        self.assertTrue(bus.closed)

    async def test_disconnect_closes_le_and_never_the_device(self):
        client, bus = await self.connected({'GetManagedObjects': [managed_objects()]})
        await client.start_notify(RESPONSE, lambda *_: None)
        bus.calls.clear()
        await client.disconnect()
        self.assertEqual(bus.calls, [(RESPONSE_PATH, mmp_bluez.CHARACTERISTIC, 'StopNotify'),
                                     (DEV, mmp_bluez.LE_BEARER, 'Disconnect')])
        self.assertTrue(bus.closed)
        self.assertFalse(client.is_connected)

    async def test_start_notify_retries_until_gatt_client_is_ready(self):
        not_ready = reply(error='org.bluez.Error.Failed')
        client, bus = await self.connected({'GetManagedObjects': [managed_objects()],
                                            'StartNotify': [not_ready]})
        await client.start_notify(RESPONSE, lambda *_: None)
        self.assertEqual(bus.calls.count((RESPONSE_PATH, mmp_bluez.CHARACTERISTIC,
                                          'StartNotify')), 2)

    async def test_write_without_response_uses_command_type(self):
        client, bus = await self.connected({'GetManagedObjects': [managed_objects()]})
        sent = []
        original = bus.call

        async def call(message):
            if message.member == 'WriteValue':
                sent.append(message.body)
            return await original(message)

        bus.call = call
        await client.write_gatt_char(client.services.get_characteristic(REQUEST), b'\x35\x00',
                                     response=False)
        self.assertEqual(sent[0][0], b'\x35\x00')
        self.assertEqual(sent[0][1]['type'].value, 'command')

    async def test_notifications_reach_the_callback(self):
        client, bus = await self.connected({'GetManagedObjects': [managed_objects()]})
        received = []
        await client.start_notify(RESPONSE, lambda char, data: received.append((char.uuid, data)))
        bus.handler(signal(RESPONSE_PATH, mmp_bluez.PROPERTIES, 'PropertiesChanged', 'sa{sv}as',
                           [mmp_bluez.CHARACTERISTIC, {'Value': Variant('ay', b'\x35\x00')}, []]))
        self.assertEqual(received, [(RESPONSE, bytearray(b'\x35\x00'))])

    async def test_audio_link_loss_is_not_control_loss(self):
        lost = []
        client, bus = await self.connected({'GetManagedObjects': [managed_objects()]})
        client.disconnected_callback = lost.append
        bus.handler(signal(DEV, mmp_bluez.PROPERTIES, 'PropertiesChanged', 'sa{sv}as',
                           ['org.bluez.Bearer.BREDR1', {'Connected': Variant('b', False)}, []]))
        self.assertEqual(lost, [])
        self.assertTrue(client.is_connected)

    async def test_le_link_loss_reported_once(self):
        lost = []
        client, bus = await self.connected({'GetManagedObjects': [managed_objects()]})
        client.disconnected_callback = lost.append
        bus.handler(signal(DEV, mmp_bluez.PROPERTIES, 'PropertiesChanged', 'sa{sv}as',
                           [mmp_bluez.LE_BEARER, {'Connected': Variant('b', False)}, []]))
        bus.handler(signal(DEV, mmp_bluez.LE_BEARER, 'Disconnected', 'ss', ['x', 'y']))
        self.assertEqual(lost, [client])
        self.assertFalse(client.is_connected)

    async def test_bluetoothd_exit_is_control_loss(self):
        lost = []
        client, bus = await self.connected({'GetManagedObjects': [managed_objects()]})
        client.disconnected_callback = lost.append
        bus.handler(signal('/org/freedesktop/DBus', 'org.freedesktop.DBus', 'NameOwnerChanged',
                           'sss', ['org.bluez', ':1.5', '']))
        self.assertEqual(lost, [client])

    async def test_audio_streaming_reads_this_devices_transport(self):
        def objects(state, device=DEV):
            return reply([{device + '/sep1/fd0': {mmp_bluez.MEDIA_TRANSPORT:
                                                  {'State': Variant('s', state)}}}])

        other = '/org/bluez/hci0/dev_11_22_33_44_55_66'
        client, bus = await self.connected({'GetManagedObjects': [
            managed_objects(), objects('active'), objects('idle'), objects('active', other)]})
        self.assertTrue(await client.audio_streaming())
        self.assertFalse(await client.audio_streaming())
        self.assertFalse(await client.audio_streaming())    # another device's audio


    async def test_refuses_when_another_program_is_subscribed(self):
        client, bus = await self.connected({'GetManagedObjects': [managed_objects()],
                                            'Get': [reply([Variant('b', True)])]})
        with self.assertRaises(mmp_bluez.AmpBusy):
            await client.start_notify(RESPONSE, lambda *_: None)
        members = [(interface, member) for _, interface, member in bus.calls]
        self.assertNotIn((mmp_bluez.CHARACTERISTIC, 'StartNotify'), members)
        self.assertNotIn((mmp_bluez.LE_BEARER, 'Disconnect'), members)    # not our link to close
        self.assertFalse(client.is_connected)
        self.assertTrue(bus.closed)
        await client.disconnect()                                          # still harmless
        self.assertNotIn((mmp_bluez.LE_BEARER, 'Disconnect'),
                         [(i, m) for _, i, m in bus.calls])

if __name__ == '__main__':
    unittest.main()
