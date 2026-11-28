"""Tests for fender_amp_probe.py against simulated amps (same cases as test_core.js).

Run: python3 -m unittest discover -s amp-probe/tests
"""
import asyncio
import json
import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import fender_amp_probe as P  # noqa: E402

P.SPEED = 0.002
PRIVATE_STRINGS = ['AA:BB:CC:11:22:33', 'MyHomeWifi', 'NeighbourNet', '192.168.1.50', 'jane@example.com', 'Jane',
                   '0f8b1c1e-1111-4222-8333-444455556666']


def lz4_literal(b):
    n, out = len(b), bytearray([(15 if len(b) >= 15 else len(b)) << 4])
    if n >= 15:
        n -= 15
        while n >= 255:
            out.append(255)
            n -= 255
        out.append(n)
    return bytes(out) + b


class FakeGt:
    def __init__(self, edited=False):
        self.slot, self.edited, self.sink, self.writes = 3, edited, None, []
        self.params = {'gain': 0.4, 'volume': -6.0}
        self.presets = [{'slotNumber': n, 'displayName': f'Preset {n}', 'product_id': 'mustang-gt-100',
                         'preset_id': '0f8b1c1e-1111-4222-8333-444455556666' if n == 2 else f'id{n}',
                         'author': 'jane@example.com' if n == 2 else 'Fender', 'is_factory_default': n != 2,
                         'isEmpty': n == 5} for n in range(1, 6)]

    def emit(self, name, body):
        data = P.gt_msg(name, body)
        asyncio.get_running_loop().call_soon(self.sink, data)

    async def write(self, data):
        self.writes.append(data.hex())
        m = P.decode_message('gt', data, P.Privacy(), {})
        v = m['value'] or {}
        name = m['name']
        if name == 'toneSync':
            self.emit('packetBegin', {'packetType': 'TONE_SYNC'})
            self.emit('systemStatus', {'address': 'AA:BB:CC:11:22:33', 'firmwareVersion': '3.0.43', 'wifiState': 'On',
                                       'btName': 'Mustang GT', 'productId': 'mustang-gt-100'})
            for p in self.presets:
                self.emit('presetInfoMessage', p)
            self.emit('packetEnd', {'packetType': 'TONE_SYNC'})
        elif name == 'syncCurrentPresetToTone':
            self.emit('packetBegin', {'packetType': 'SYNC_CURRRENT_PRESET_TONE'})
            self.emit('presetEditedStatus', {'presetEdited': self.edited})
            self.emit('presetInfoMessage', self.presets[self.slot - 1])
            self.emit('nodeStatus', {'nodeStatus': [{'nodeId': 'DUBS_LinearGain_1', 'fenderId': 'DUBS_LinearGain'},
                                                    {'nodeId': 'DUBS_Twin65_1', 'fenderId': 'DUBS_Twin65', 'bypass': True}]})
            for k, x in self.params.items():
                self.emit('dspUnitParameterStatus', {'nodeId': 'DUBS_LinearGain_1', 'parameterId': k, 'floatParameter': x})
            self.emit('packetEnd', {'packetType': 'SYNC_CURRRENT_PRESET_TONE'})
        elif name == 'loadPresetIfValid':
            self.slot, self.edited = v['slot'], False
            self.emit('presetInfoMessage', self.presets[self.slot - 1])
            self.emit('packetEnd', {'packetType': 'LOAD_PRESET_IF_VALID'})
        elif name == 'setDspUnitParameter':
            self.params[v['parameterId']] = v['floatParameter']
            self.edited = True
            self.emit('presetEditedStatus', {'presetEdited': True})
        elif name == 'getDspUnitParameter':
            self.emit('dspUnitParameterStatus', {'nodeId': v['nodeId'], 'parameterId': v['parameterId'],
                                                 'floatParameter': self.params[v['parameterId']]})
        elif name == 'retrievePreset':
            js = json.dumps({'info': {'displayName': 'Preset 3', 'author': 'Jane Doe', 'preset_id': 'id3'}}).encode()
            self.emit('packetStartMTU', {'numBytes': len(js), 'id': 7})
            self.emit('packetDataMTU', {'seqNumber': 0, 'id': 7, 'data': js[:40]})
            self.emit('packetDataMTU', {'seqNumber': 1, 'id': 7, 'data': js[40:]})
            self.emit('packetStopMTU', {'id': 7})
        elif name == 'setlistManagerSync':
            self.emit('setlistStatus', {'slot': 1, 'displayName': 'FAVORITES'})
        self.emit('networkStatus', {'connectionStatus': 'CONNECTED', 'connectedSSID': 'MyHomeWifi',
                                    'connectedIP': '192.168.1.50',
                                    'availableNetworks': [{'ssid': 'NeighbourNet', 'signalStrength': 3}]})


class FakeFramed:
    def __init__(self, family, dirty=False, silent=False):
        self.family, self.index, self.dirty, self.silent, self.sink, self.writes = family, 4, dirty, silent, None, []
        self.names = ['EMPTY' if i == 7 else f'Name {i + 1}' for i in range(10)]
        self.amp = {'gain': 0.5, 'volume': -5.0}

    def preset(self):
        return {'info': {'displayName': self.names[self.index], 'author': 'Jane Doe',
                         'preset_id': '0f8b1c1e-1111-4222-8333-444455556666'},
                'audioGraph': {'nodes': [{'nodeId': 'amp', 'FenderId': 'ACD_Twin65GT', 'dspUnitParameters': dict(self.amp)}]}}

    def status(self, name, body):
        env = P.FAMILIES[self.family]['envelope']
        data = P.encode_msg(env, {'ampStatus': {'responseType': 'IS_LAST_ACK', name: body}}, self.family)
        for f in P.to_frames(data):
            asyncio.get_running_loop().call_soon(self.sink, f)

    async def write(self, data):
        self.writes.append(data.hex())
        if self.silent:
            return
        m = P.decode_message(self.family, data[3:], P.Privacy(), {})
        if not m['name'].startswith('request:'):
            return
        name, v = m['name'][8:], m['value'] or {}
        lz = {'compressedPresetJson': lz4_literal(json.dumps(self.preset()).encode())}
        if name == 'firmwareVersionRequest':
            self.status('firmwareVersionStatus', {'version': '1.0.29'})
        elif name == 'presetNamesRequest':
            self.status('presetNamesStatus', {'presetName': self.names})
        elif name in ('currentPresetRequest', 'loadPresetRequest'):
            if name == 'loadPresetRequest':
                self.index, self.dirty = v.get('newPresetIndex', 0), False
            self.status('currentPresetStatus', {'presetIndex': self.index, 'isDirty': self.dirty, 'dummyField': True})
            self.status('currentPresetDataStatus', lz)
        elif name == 'presetDataRequest':
            self.status('presetDataStatus', {'presetIndex': v.get('presetIndex', 0), **lz})
        elif name == 'parameterRequest':
            self.status('parameterStatus', {'nodeId': v['nodeId'], 'parameterId': v['parameterId'],
                                            'floatParameter': self.amp[v['parameterId']]})
        elif name == 'setParameterRequest':
            self.amp[v['parameterId']] = v['floatParameter']
            self.dirty = True


def attach(fake, family):
    s = P.Session(family, fake.write)
    fake.sink = s.on_data
    return s


def report(s, r, family):
    return P.build_report({'results': r, 'session': s, 'family': family, 'serviceUuid': 'x', 'date': '2026-11-27',
                           'device': {'name': 'Test amp'}, 'answers': {}})


class Vectors(unittest.TestCase):
    def test_gt_bytes_from_issue_148(self):
        self.assertEqual(P.gt_msg('toneSync').hex(), 'aa3800')
        self.assertEqual(P.gt_msg('loadPresetIfValid', {'slot': 198}).hex(), 'ba190308c601')
        self.assertEqual(P.gt_msg('setDspUnitParameter', {'nodeId': 'DUBS_LinearGain_1', 'parameterId': 'gain',
                                                          'floatParameter': 0.4}).hex(),
                         'b2061e0a11' + b'DUBS_LinearGain_1'.hex() + '1204' + b'gain'.hex() + '1dcdcccc3e')

    def test_micro_plus_frames_from_maneline(self):
        def frame(b):
            return P.to_frames(b)[0].hex()
        self.assertEqual(frame(P.modal('mp', 'SYNC_BEGIN')), '3500021a00')
        self.assertEqual(frame(P.modal('mp', 'SYNC_END')), '3500041a020801')
        self.assertEqual(frame(P.tone_msg('mp', 'firmwareVersionRequest')), '3500040a023a00')
        self.assertEqual(frame(P.tone_msg('mp', 'currentPresetRequest', {'requestPresetData': True})), '3500060a0422020801')
        self.assertEqual(frame(P.tone_msg('mp', 'heartbeat')), '3500050a03c20100')
        self.assertEqual(frame(P.tone_msg('mp', 'loadPresetRequest', {'newPresetIndex': 71})), '3500060a0442020847')

    def test_windows_device_ids_lose_the_address(self):
        p = P.Privacy()
        self.assertEqual(p.scrub(r'BTHENUM\DEV_123456789ABC | USB\VID_1ED8&PID_003A&MI_00'),
                         r'BTHENUM\[removed] | USB\VID_1ED8&PID_003A&MI_00')

    def test_privacy(self):
        p = P.Privacy()
        self.assertEqual(p.scrub('jane@example.com 10.0.0.7 AA:BB:CC:11:22:33'), '[removed] [removed] [removed]')
        self.assertEqual(p.scrub('1.0.29.4', 'firmwareVersion'), '1.0.29.4')

    def test_lz4(self):
        self.assertEqual(P.lz4_block(bytes([0x35, 0x61, 0x62, 0x63, 0x03, 0x00])), b'abcabcabcabc')


class Flows(unittest.IsolatedAsyncioTestCase):
    def assert_private_removed(self, text):
        for p in PRIVATE_STRINGS:
            self.assertNotIn(p, text)
            self.assertNotIn(p.encode().hex(), text)

    async def test_gt(self):
        fake = FakeGt()
        s, r = attach(fake, 'gt'), P.Results()
        await P.gt_read(s, r)
        st = {c['id']: c for c in r.checks}
        for k in ('toneSync', 'current', 'x-getparam', 'x-retrieve'):
            self.assertEqual(st[k]['status'], 'ok', st[k])
        self.assertEqual(r.data['system']['address'], '[removed]')
        await P.gt_control(s, r)
        st = {c['id']: c for c in r.checks}
        self.assertEqual(st['c-switch']['status'], 'ok', st['c-switch'])
        self.assertEqual(st['c-knob']['status'], 'ok', st['c-knob'])
        self.assertEqual((fake.slot, fake.params['gain'], fake.edited), (3, 0.4, False))
        text = report(s, r, 'gt')
        self.assertIn('mustang-gt-100', text)
        self.assert_private_removed(text)

    async def test_gt_edited_preset_untouched(self):
        fake = FakeGt(edited=True)
        s, r = attach(fake, 'gt'), P.Results()
        await P.gt_read(s, r)
        await P.gt_control(s, r)
        self.assertEqual({c['id']: c['status'] for c in r.checks}['c-switch'], 'skipped')
        self.assertFalse([w for w in fake.writes if w.startswith(('ba19', 'b206'))])

    async def test_framed(self):
        for family in ('mp', 'ltx'):
            with self.subTest(family=family):
                fake = FakeFramed(family)
                s, r = attach(fake, family), P.Results()
                await P.framed_read(s, r, family)
                await P.framed_control(s, r)
                s.stop_heartbeat()
                st = {c['id']: c for c in r.checks}
                for k in ('family', 'handshake', 'stored', 'c-switch', 'c-knob'):
                    self.assertEqual(st[k]['status'], 'ok', st[k])
                self.assertEqual((fake.index, fake.amp['gain'], fake.dirty), (4, 0.5, False))
                self.assertIn(P.to_frames(P.tone_msg(family, 'heartbeat'))[0].hex(), fake.writes)
                text = report(s, r, family)
                self.assertIn('Name 10', text)
                self.assert_private_removed(text)

    async def test_ltx_behind_micro_plus_service(self):
        fake = FakeFramed('ltx')
        s, r = attach(fake, 'mp'), P.Results()
        await P.framed_read(s, r, 'mp')
        s.stop_heartbeat()
        self.assertEqual(r.data['family'], 'ltx')
        self.assertEqual(fake.writes[:3], ['3500021a00', '3500040a023a00', '3500040a027200'])

    async def test_silent_amp(self):
        fake = FakeFramed('ltx', silent=True)
        s, r = attach(fake, 'ltx'), P.Results()
        await P.framed_read(s, r, 'ltx')
        self.assertEqual({c['id']: c['status'] for c in r.checks}['family'], 'noanswer')
        self.assertEqual(len(fake.writes), 3)
        self.assertIsNone(s.heartbeat_task)

    async def test_doubled_notifications_are_counted_in_the_report(self):
        fake = FakeFramed('mp')
        s = attach(fake, 'mp')
        single = fake.sink
        fake.sink = lambda data: (single(data), single(data))   # a second subscriber: each reply twice
        r = P.Results()
        await P.framed_read(s, r, 'mp')
        self.assertGreater(s.twice, 0)
        self.assertIn('notifications arrived twice in a row', report(s, r, 'mp'))

    async def test_silent_amp_skips_the_control_test_without_asking(self):
        fake = FakeFramed('ltx', silent=True)
        s, r, st = attach(fake, 'ltx'), P.Results(), {}
        saved = P.ask_async

        async def ask(prompt, default=''):
            raise AssertionError('asked: ' + prompt)

        P.ask_async = ask
        try:
            await P.read_and_control(s, r, st, 'ltx')
        finally:
            P.ask_async = saved
        self.assertEqual({c['id']: c['status'] for c in r.checks}['control'], 'skipped')
        self.assertEqual(len(fake.writes), 3)   # nothing beyond the requests both families share


class ReportFilter(unittest.TestCase):
    def test_error_messages_and_device_name(self):
        r = P.Results()
        r.check('connect', 'Connect and read', 'failed',
                'BleakError: org.bluez.Error.Failed /org/bluez/hci0/dev_AA_BB_CC_11_22_33 (AA:BB:CC:11:22:33)')
        r.check('fw', 'Session start', 'ok', 'firmware 1.0.29.4')
        text = P.build_report({'results': r, 'date': '2026-11-27', 'device': {'name': 'Mustang GT at 10.0.0.7'}})
        for bad in ('AA_BB_CC', 'AA:BB:CC', '10.0.0.7'):
            self.assertNotIn(bad, text)
        self.assertIn('firmware 1.0.29.4', text)
        self.assertIn('a hardware address (MAC) (in an error message or device name)', text)


class LinuxBearer(unittest.IsolatedAsyncioTestCase):
    async def test_prefers_le_for_the_check_and_sets_it_back(self):
        addr, path = 'AA:BB:CC:11:22:33', '/org/bluez/hci0/dev_AA_BB_CC_11_22_33'
        bearer, calls = ['bredr'], []

        def run(cmd, timeout=40):
            calls.append(cmd)
            if cmd[:2] == ['bluetoothctl', 'info']:
                return ('Device AA:BB:CC:11:22:33 (public)\n\tPaired: yes\n\tConnected: yes\n'
                        '\tBREDR.Paired: yes\n\tBREDR.Connected: yes\n\tLE.Connected: no\n')
            if cmd[:2] == ['busctl', '--system'] and cmd[2] == 'get-property':
                return f's "{bearer[0]}"\n'
            if cmd[:2] == ['busctl', '--system'] and cmd[2] == 'set-property':
                bearer[0] = cmd[-1]
            return ''

        class Device:
            address, details = addr, {'path': path}

        saved = P._run, P.yes, P.ask_async
        P._run, P.yes = run, lambda prompt, hint='': True

        async def no(prompt, default=''):
            return 'n'
        P.ask_async = no
        try:
            r = P.Results()
            undo = await P.linux_prepare(Device(), r)
            self.assertEqual(bearer[0], 'le')
            self.assertIn(['bluetoothctl', 'disconnect', addr], calls)
            self.assertEqual({c['id']: c['status'] for c in r.checks}['linux-bearer'], 'info')
            self.assertNotIn('AA', ' '.join(c['detail'] for c in r.checks))
            await P.linux_restore(Device(), undo)
            self.assertEqual(bearer[0], 'bredr')
            self.assertNotIn(['bluetoothctl', 'connect', addr], calls)
        finally:
            P._run, P.yes, P.ask_async = saved


class GattReady(unittest.IsolatedAsyncioTestCase):
    async def test_waits_for_a_read_then_retries_the_subscription(self):
        class Char:
            def __init__(self, uuid, props):
                self.uuid, self.properties = uuid, props

        name = Char('00002a00-0000-1000-8000-00805f9b34fb', ['read'])
        resp = Char(P.UUID['response'], ['notify'])

        class Client:
            services = [type('S', (), {'characteristics': [resp, name]})()]
            reads, subs = 0, 0

            async def read_gatt_char(self, c):
                assert c is name
                Client.reads += 1
                if Client.reads < 3:
                    raise RuntimeError('org.bluez.Error.Failed')
                return b'Mustang'

            async def start_notify(self, c, cb):
                Client.subs += 1
                if Client.subs < 2:
                    raise RuntimeError('org.bluez.Error.NotConnected')

        client = Client()
        ms, tries = await P.wait_for_gatt(client)
        self.assertEqual(tries, 3)
        await P.start_notify_when_ready(client, resp, lambda *a: None)
        self.assertEqual(Client.subs, 2)


class Clipboard(unittest.TestCase):
    def test_tries_the_system_tools_in_turn(self):
        tried = []

        def run(cmd, **kw):
            tried.append(cmd[0])
            self.assertEqual(kw['input'], 'report ü'.encode('utf-8'))
            if cmd[0] == 'wl-copy':
                raise FileNotFoundError(cmd[0])
            return subprocess.CompletedProcess(cmd, 0)

        saved, platform = P.subprocess.run, P.sys.platform
        P.subprocess.run, P.sys.platform = run, 'linux'
        try:
            self.assertTrue(P.copy_to_clipboard('report ü', '/tmp/r.md'))
        finally:
            P.subprocess.run, P.sys.platform = saved, platform
        self.assertEqual(tried, ['wl-copy', 'xclip'])


class OpenLink(unittest.TestCase):
    def test_linux_asks_the_desktop_and_falls_back_to_webbrowser(self):
        calls = []
        saved = P.subprocess.run, P.webbrowser.open, P.sys.platform
        P.sys.platform = 'linux'
        P.webbrowser.open = lambda url: calls.append(('webbrowser', url))
        try:
            P.subprocess.run = lambda cmd, **kw: calls.append(tuple(cmd)) or subprocess.CompletedProcess(cmd, 0)
            P.open_link('mailto:a@b?subject=x')
            self.assertEqual(calls, [('xdg-open', 'mailto:a@b?subject=x')])
            calls.clear()
            P.subprocess.run = lambda cmd, **kw: (_ for _ in ()).throw(FileNotFoundError('xdg-open'))
            P.open_link('mailto:a@b')
            self.assertEqual(calls, [('webbrowser', 'mailto:a@b')])
        finally:
            P.subprocess.run, P.webbrowser.open, P.sys.platform = saved


class SystemFilter(unittest.TestCase):
    def test_only_fender_entries_without_identifiers(self):
        tree = {'SPUSBDataType': [{'_name': 'USB Bus', '_items': [
            {'_name': 'Fender Mustang GT', 'vendor_id': '0x1ed8', 'serial_num': 'ABC123', 'location_id': '0x14'},
            {'_name': "Jane's Phone", 'vendor_id': '0x05ac'}]}],
            'SPBluetoothDataType': [{'device_connected': [{'Mustang GT': {'device_address': 'AA:BB:CC:11:22:33',
                                                                          'device_minorType': 'Speaker'}}]}]}
        text = json.dumps(P._filter_tree(tree, P.Privacy()))
        self.assertIn('Fender Mustang GT', text)
        self.assertIn('Speaker', text)
        for bad in ('ABC123', 'Jane', 'AA:BB:CC', '0x14'):
            self.assertNotIn(bad, text)

    def test_linux_bluetooth_entry(self):
        out = {('bluetoothctl', 'devices'): 'Device AA:BB:CC:11:22:33 Mustang GT\nDevice 11:22:33:44:55:66 Jane\'s Phone\n',
               ('bluetoothctl', 'info', 'AA:BB:CC:11:22:33'):
                   'Device AA:BB:CC:11:22:33 (public)\n\tName: Mustang GT\n\tAlias: Jane\'s amp\n\tClass: 0x00240414\n'
                   '\tPaired: yes\n\tConnected: yes\n\tUUID: Audio Sink (0000110b-0000-1000-8000-00805f9b34fb)\n',
               ('bluetoothd', '--version'): '5.87\n'}
        run = P._run
        P._run = lambda cmd, timeout=40: out.get(tuple(cmd), '')
        try:
            text = '\n'.join(P.linux_bluetooth(P.Privacy()))
            connected = P.bluez_connected('AA:BB:CC:11:22:33')
        finally:
            P._run = run
        self.assertTrue(connected)
        for good in ('Mustang GT', 'BlueZ 5.87', 'Class: 0x00240414', 'Audio Sink', 'Connected: yes'):
            self.assertIn(good, text)
        for bad in ('AA:BB:CC', 'Jane', 'Alias'):
            self.assertNotIn(bad, text)


if __name__ == '__main__':
    unittest.main()
