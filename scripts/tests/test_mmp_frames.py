"""Sending long messages: frame splitting, and no frame of another task in between."""
import asyncio
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mmp_ble import FRAME_GAP, FRAME_PAYLOAD, KEEPALIVE, Controller, Frames, frames


class FakeClient:
    """Each write takes a moment, so other tasks get a chance in between."""

    class _Characteristic:
        properties = ['write-without-response']

    class _Services:
        def get_characteristic(self, _uuid):
            return FakeClient._Characteristic()

    def __init__(self):
        self.services = self._Services()
        self.written = []

    async def write_gatt_char(self, _characteristic, data, response=False):
        await asyncio.sleep(0.005)
        self.written.append(bytes(data))


class FrameTests(unittest.TestCase):
    def test_split_and_reassemble(self):
        message = bytes(range(256)) * 5                 # 1280 bytes, like a preset audition
        parts = frames(message)
        self.assertEqual([p[0] for p in parts], [0x33] + [0x34] * 9 + [0x35])
        self.assertTrue(all(len(p) <= FRAME_PAYLOAD + 3 for p in parts))
        incoming = Frames()                             # the amp's reassembly rules, as maneline reads them
        results = [incoming.feed(p) for p in parts]
        self.assertEqual(results[-1], message)
        self.assertEqual(frames(b'\x0a\x02\x62\x00'), [bytes.fromhex('3500040a026200')])

    def test_heartbeat_waits_for_a_long_message(self):
        async def run():
            client = FakeClient()
            controller = Controller(client, None)

            async def heartbeat():
                while True:
                    await controller.send(KEEPALIVE)
                    await asyncio.sleep(0.003)
            beat = asyncio.create_task(heartbeat())
            await asyncio.sleep(0.01)
            message = bytes(1166)
            controller.frame_times = []
            write = client.write_gatt_char

            async def timed_write(characteristic, data, response=False):
                if data[0] in (0x33, 0x34) or (data[0] == 0x35 and data != KEEPALIVE):
                    controller.frame_times.append(asyncio.get_running_loop().time())
                await write(characteristic, data, response)
            client.write_gatt_char = timed_write
            await controller.send_message(message)
            await asyncio.sleep(0.02)
            beat.cancel()
            sent = client.written
            first = next(i for i, f in enumerate(sent) if f[0] == 0x33)
            block = sent[first:first + len(frames(message))]
            self.assertEqual(block, frames(message))    # contiguous, no heartbeat inside
            self.assertGreater(sent.count(KEEPALIVE), 2)  # and the heartbeat did keep running
            times = controller.frame_times
            self.assertTrue(all(b - a >= FRAME_GAP * 0.9 for a, b in zip(times, times[1:])),
                            'frames of one message must be paced')
        asyncio.run(run())


if __name__ == '__main__':
    unittest.main()
