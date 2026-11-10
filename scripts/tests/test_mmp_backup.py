"""Preset backup: request encoding and reply parsing (no amp needed)."""
import asyncio
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mmp_backup import BackupController, preset_data_request, slot_list
from mmp_ble import KEEPALIVE, packet, select_preset, tone_request, varint
from mmp_lz4 import compress


def reply(kind, content):
    """An amp status message: MustangLtxAppMessage.ampStatus (2) wrapping one status."""
    status = varint((kind << 3) | 2) + varint(len(content)) + content
    body = b'\x08\x02' + status                     # responseType IS_LAST_ACK, then the status
    message = b'\x12' + varint(len(body)) + body
    frames, rest = [], message
    while len(rest) > 200:                          # long replies come in several frames
        frames.append(bytes((0x33 if not frames else 0x34, 0, 200)) + rest[:200])
        rest = rest[200:]
    frames.append(bytes((0x35, 0, len(rest))) + rest)
    return frames


class BackupTests(unittest.TestCase):
    def test_requests_match_the_amp(self):
        # sent to the amp on 2026-11-10: slot 21 answered with presetDataStatus index 20
        self.assertEqual(preset_data_request(21).hex(), '3500060a0462020814')
        self.assertEqual(preset_data_request(1).hex(), '3500040a026200')
        # known requests, built the same way, give maneline's captured bytes:
        # heartbeat (24, empty), switch to preset 72 (loadPresetRequest 8), current preset (4)
        self.assertEqual(tone_request(24, b''), KEEPALIVE)
        self.assertEqual(tone_request(8, b'\x08\x47'), select_preset(72))
        self.assertEqual(tone_request(4, b'\x08\x01'), packet(bytes.fromhex('0a0422020801')))

    def test_preset_data_reply_in_frames(self):
        async def run():
            controller = BackupController(None, None)
            doc = {'nodeType': 'preset', 'info': {'displayName': 'Echo Dome'}, 'pad': 'x' * 900}
            raw = compress(json.dumps(doc).encode())
            for frame in reply(10, b'\x08\x14' + b'\x12' + varint(len(raw)) + raw):
                controller.notify(None, frame)
            self.assertEqual(controller.presets[20], raw)
            for frame in reply(10, b'\x12' + varint(len(raw)) + raw):      # index 0 is omitted
                controller.notify(None, frame)
            self.assertIn(0, controller.presets)
            for frame in reply(17, b'\x08\x02'):
                controller.notify(None, frame)
            self.assertEqual(controller.error, 'INVALID_PARAM')
        asyncio.run(run())

    def test_slot_list(self):
        self.assertEqual(slot_list('1-3,72'), [1, 2, 3, 72])
        for bad in ('0', '101', '5-3'):
            with self.assertRaises(Exception):
                slot_list(bad)


if __name__ == '__main__':
    unittest.main()
