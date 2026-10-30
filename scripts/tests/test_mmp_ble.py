"""Protocol fixtures from the checked-in Micro Plus Bluetooth captures."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mmp_ble import Controller, Frames, fields, packet, select_preset


class ProtocolTests(unittest.TestCase):
    def test_captured_switch_to_displayed_preset_72(self):
        self.assertEqual(select_preset(72).hex(), '3500060a0442020847')

    def test_preset_range(self):
        for slot in (0, -1, 101):
            with self.assertRaises(ValueError):
                select_preset(slot)

    def test_captured_selected_preset_report(self):
        controller = Controller(None, None)
        controller.notify(None, bytes.fromhex('35000a120808012a0408472001'))
        self.assertEqual(controller.status(), {'preset': 72, 'name': None})

    def test_first_preset_omits_protobuf_default(self):
        controller = Controller(None, None)
        controller.notify(None, bytes.fromhex('350008120608012a022001'))
        self.assertEqual(controller.slot, 1)

    def test_fragmented_names_response(self):
        controller = Controller(None, None)
        body = b'\x08\x02\x62\x0a\x0a\x03One\x0a\x03Two'
        frame = b'\x12' + bytes([len(body)]) + body
        controller.notify(None, bytes([0x33, 0, 5]) + frame[:5])
        controller.notify(None, bytes([0x35, 0, len(frame) - 5]) + frame[5:])
        self.assertEqual(controller.names, ['One', 'Two'])
        self.assertIsNone(controller.failure)

    def test_malformed_packet_clears_partial_message(self):
        frames = Frames()
        frames.feed(bytes.fromhex('3300021206'))
        with self.assertRaises(ValueError):
            frames.feed(bytes.fromhex('350002ff'))
        self.assertEqual(frames.buffer, bytearray())

    def test_truncated_protobuf_fails(self):
        for data in (b'\x12\xff', b'\x12\x02\x01', b'\x00'):
            with self.assertRaises(ValueError):
                fields(data)


class SwitchTests(unittest.IsolatedAsyncioTestCase):
    async def test_switch_requires_amp_acknowledgement(self):
        import asyncio
        class ConnectedClient:
            is_connected = True

        controller = Controller(ConnectedClient(), None)
        sent = []

        async def send(data):
            sent.append(data)

        controller.send = send
        controller.slot = 71
        switch = asyncio.create_task(controller.switch(72))
        await asyncio.sleep(0)
        self.assertEqual(sent, [select_preset(72)])
        self.assertFalse(switch.done())
        controller.notify(None, bytes.fromhex('35000a120808012a0408472001'))
        self.assertEqual(await switch, {'preset': 72, 'name': None})

    async def test_wait_for_missing_response_times_out(self):
        class ConnectedClient:
            is_connected = True

        controller = Controller(ConnectedClient(), None)
        with self.assertRaises(TimeoutError):
            await controller.wait_for(lambda: False, timeout=0.01)


if __name__ == '__main__':
    unittest.main()
