"""HTTP contracts and persistent Bluetooth ownership, without hardware."""
import asyncio
import contextlib
import json
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mmp_session import MicroPlusSession
from mmp_web import dispatch, HTTPError


class HTTPTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.session = MicroPlusSession()
        self.headers = {'host': 'localhost:8080', 'origin': 'http://localhost:8080',
                        'content-type': 'application/json'}

    async def test_status_never_returns_old_presets_when_disconnected(self):
        code, _, body = await dispatch('GET', '/api/micro-plus', self.headers, b'', self.session)
        self.assertEqual(code, 200)
        state = json.loads(body)
        self.assertFalse(state['connected'])
        self.assertIsNone(state['preset'])
        self.assertEqual(state['presets'], [])

    async def test_invalid_slots_rejected_before_bluetooth(self):
        for slot in (0, 101, -1, 1.5, True, '72', None):
            with self.subTest(slot=slot), self.assertRaises(HTTPError) as caught:
                await dispatch('POST', '/api/micro-plus', self.headers,
                               json.dumps({'preset': slot}).encode(), self.session)
            self.assertEqual(caught.exception.status, 400)

    async def test_foreign_origin_rejected(self):
        self.headers['origin'] = 'https://example.com'
        with self.assertRaises(HTTPError) as caught:
            await dispatch('POST', '/api/micro-plus', self.headers, b'{"preset":72}', self.session)
        self.assertEqual(caught.exception.status, 403)

    async def test_foreign_host_rejected_even_with_matching_origin(self):
        headers = {**self.headers, 'host': 'example.com', 'origin': 'http://example.com'}
        with self.assertRaises(HTTPError) as caught:
            await dispatch('GET', '/api/micro-plus', headers, b'', self.session, {'localhost'})
        self.assertEqual(caught.exception.status, 403)

    async def test_disconnected_switch_is_an_error(self):
        with self.assertRaises(HTTPError) as caught:
            await dispatch('POST', '/api/micro-plus', self.headers, b'{"preset":72}', self.session)
        self.assertEqual(caught.exception.status, 503)

    async def test_static_paths_cannot_escape_web_directory(self):
        for path in ('/../../scripts/mmp_web.py', '/api/missing'):
            with self.subTest(path=path), self.assertRaises(HTTPError) as caught:
                await dispatch('GET', path, self.headers, b'', self.session)
            self.assertEqual(caught.exception.status, 404)

    async def test_root_serves_micro_plus_controls(self):
        code, content_type, body = await dispatch('GET', '/', self.headers, b'', self.session)
        self.assertEqual((code, content_type), (200, 'text/html'))
        self.assertIn(b'Mustang Micro Plus', body)
        self.assertIn(b'micro-plus.js', body)


class PersistentTests(unittest.IsolatedAsyncioTestCase):
    async def test_keepalive_continues_while_switch_waits_and_disconnect_clears_state(self):
        ready = asyncio.Event()
        three_beats = asyncio.Event()
        acknowledgement = asyncio.Event()
        closed = asyncio.Event()
        clients = []
        beats = []

        class FakeScanner:
            @staticmethod
            async def find_device_by_filter(*args, **kwargs):
                return types.SimpleNamespace(address='test-device')

        class FakeClient:
            def __init__(self, device, disconnected_callback):
                self.is_connected = True
                self.callback = disconnected_callback
                clients.append(self)

            async def __aenter__(self):
                return self

            async def __aexit__(self, *args):
                self.is_connected = False
                closed.set()

            async def start_notify(self, *args):
                pass

        class FakeController:
            failure = None
            names = ['Test preset'] * 100
            slot = 71

            def __init__(self, *args):
                pass

            async def initialize(self):
                ready.set()

            def notify(self, *args):
                pass

            def status(self):
                return {'preset': self.slot, 'name': 'Test preset'}

            async def send(self, data):
                beats.append(data)
                if len(beats) == 3:
                    three_beats.set()

            async def switch(self, slot):
                await acknowledgement.wait()
                self.slot = slot

        fake_bleak = types.SimpleNamespace(BleakClient=FakeClient, BleakScanner=FakeScanner)
        session = MicroPlusSession()
        with patch.dict(sys.modules, {'bleak': fake_bleak}), \
             patch('mmp_session.Controller', FakeController), \
             patch('mmp_session.KEEPALIVE_INTERVAL', .005):
            background = asyncio.create_task(session.run())
            switch = None
            try:
                await asyncio.wait_for(ready.wait(), .5)
                switch = asyncio.create_task(session.switch(72))
                await asyncio.wait_for(three_beats.wait(), .5)
                self.assertFalse(switch.done())
                self.assertEqual(session.connections, 1)
                acknowledgement.set()
                self.assertEqual((await switch)['preset'], 72)
                clients[0].is_connected = False
                clients[0].callback(clients[0])
                await asyncio.wait_for(closed.wait(), .5)
                self.assertIsNone(session.snapshot()['preset'])
                self.assertEqual(session.snapshot()['presets'], [])
            finally:
                if switch and not switch.done():
                    switch.cancel()
                    with contextlib.suppress(asyncio.CancelledError):
                        await switch
                background.cancel()
                with contextlib.suppress(asyncio.CancelledError):
                    await background


if __name__ == '__main__':
    unittest.main()
