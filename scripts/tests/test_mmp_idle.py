"""Idle disconnect to save the amp's battery, without hardware."""
import asyncio
import contextlib
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mmp_session import MicroPlusSession  # noqa: E402


class IdleDisconnectTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.clients = []
        self.acknowledge = asyncio.Event()
        self.acknowledge.set()
        self.streaming = False
        self.close_delay = 0
        test = self

        async def find_device(*args, **kwargs):
            return types.SimpleNamespace(address='test-device')

        async def make_client(device, disconnected_callback):
            return FakeClient(device, disconnected_callback)

        class FakeClient:
            def __init__(self, device, disconnected_callback):
                self.is_connected = True
                test.clients.append(self)

            async def __aenter__(self):
                return self

            async def __aexit__(self, *args):
                await asyncio.sleep(test.close_delay)     # closing the LE link takes time
                self.is_connected = False

            async def start_notify(self, *args):
                pass

            async def audio_streaming(self):
                return test.streaming

        class FakeController:
            failure = None
            names = [f'Preset name {n}' for n in range(1, 101)]

            def __init__(self, *args):
                self.slot = 71

            async def initialize(self):
                pass

            def notify(self, *args):
                pass

            def status(self):
                return {'preset': self.slot, 'name': self.names[self.slot - 1]}

            async def send(self, data):
                pass

            async def switch(self, slot):
                if not test.clients[-1].is_connected:
                    raise RuntimeError('switch sent over a closed connection')
                await test.acknowledge.wait()
                self.slot = slot

        self.patches = [patch('mmp_session.find_device', find_device),
                        patch('mmp_session.make_client', make_client),
                        patch('mmp_session.Controller', FakeController),
                        patch('mmp_session.KEEPALIVE_INTERVAL', .005)]
        for p in self.patches:
            p.start()
        self.background = None

    async def asyncTearDown(self):
        if self.background:
            self.background.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self.background
        for p in reversed(self.patches):
            p.stop()

    async def start(self, idle_disconnect):
        self.session = MicroPlusSession(idle_disconnect=idle_disconnect)
        self.session.next_audio_check = 0
        self.background = asyncio.create_task(self.session.run())
        await asyncio.wait_for(self.session.ready.wait(), 1)

    async def until(self, predicate, timeout=1):
        async with asyncio.timeout(timeout):
            while not predicate():
                await asyncio.sleep(0.01)

    async def test_idle_without_audio_sleeps_and_switch_reconnects(self):
        await self.start(idle_disconnect=0.05)
        await self.until(lambda: self.session.asleep)
        self.assertFalse(self.clients[0].is_connected)
        state = self.session.snapshot()
        self.assertFalse(state['connected'])
        self.assertTrue(state['asleep'])
        self.assertEqual((state['preset'], state['name']), (71, 'Preset name 71'))
        self.assertEqual(len(state['presets']), 100)

        state = await self.session.switch(72)
        self.assertTrue(state['connected'])
        self.assertFalse(state['asleep'])
        self.assertEqual(state['preset'], 72)
        self.assertEqual(self.session.connections, 2)

    async def test_switch_while_closing_waits_for_a_new_connection(self):
        self.close_delay = 0.3
        await self.start(idle_disconnect=0.05)
        await self.until(lambda: self.session.asleep)
        self.assertTrue(self.clients[0].is_connected)          # still closing
        self.assertFalse(self.session.snapshot()['connected'])
        state = await self.session.switch(72)
        self.assertEqual(state['preset'], 72)
        self.assertEqual(len(self.clients), 2)
        self.assertFalse(self.clients[0].is_connected)

    async def test_streaming_audio_keeps_the_connection(self):
        self.streaming = True
        await self.start(idle_disconnect=0.05)
        await asyncio.sleep(0.3)
        self.assertFalse(self.session.asleep)
        self.assertEqual(len(self.clients), 1)
        self.assertTrue(self.session.snapshot()['connected'])

    async def test_never_sleeps_while_a_switch_waits_for_confirmation(self):
        await self.start(idle_disconnect=0.05)
        self.acknowledge.clear()
        switch = asyncio.create_task(self.session.switch(72))
        await asyncio.sleep(0.3)
        self.assertFalse(self.session.asleep)
        self.acknowledge.set()
        self.assertEqual((await switch)['preset'], 72)

    async def test_off_by_default(self):
        await self.start(idle_disconnect=None)
        await asyncio.sleep(0.3)
        self.assertFalse(self.session.asleep)
        self.assertEqual(len(self.clients), 1)
        self.assertIsNone(self.session.snapshot()['idle_disconnect'])


if __name__ == '__main__':
    unittest.main()
