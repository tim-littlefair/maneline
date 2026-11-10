"""Audition: request encoding, overrides and confirmation parsing (no amp needed)."""
import asyncio
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mmp_audition import EXIT_AUDITION, AuditionController, apply_overrides, audition_message
from mmp_ble import fields, frames, varint
from mmp_lz4 import decompress

PRESET = {'nodeType': 'preset', 'info': {'displayName': '80S Rock'},
          'audioGraph': {'nodes': [{'nodeId': 'stomp', 'FenderId': 'DUBS_Unknown'},
                                   {'nodeId': 'amp', 'FenderId': 'ACD_Jcm800GT',
                                    'dspUnitParameters': {'volume': -5.0, 'gain': 1.0}}]}}


class AuditionTests(unittest.TestCase):
    def test_overrides_copy(self):
        doc = apply_overrides(PRESET, name='Limelight solo', volume=-8)
        self.assertEqual(doc['info']['displayName'], 'Limelight solo')
        self.assertEqual(doc['audioGraph']['nodes'][1]['dspUnitParameters']['volume'], -8.0)
        self.assertEqual(PRESET['audioGraph']['nodes'][1]['dspUnitParameters']['volume'], -5.0)
        with self.assertRaises(ValueError):
            apply_overrides({'audioGraph': {'nodes': []}}, volume=-3)

    def test_message_layout(self):
        message = audition_message(PRESET)
        request = dict(fields(message))[1]                  # toneRequest
        audition = dict(fields(request))[1]                 # auditionPresetRequest
        raw = dict(fields(audition))[1]                     # compressedPresetJson
        self.assertEqual(json.loads(decompress(raw)), PRESET)
        self.assertGreater(len(frames(message)), 0)

    def test_exit_audition_is_not_empty(self):
        # an empty exitAuditionRequest was ignored by the amp; dummyField = true works
        self.assertEqual(EXIT_AUDITION.hex(), '3500060a0432020801')

    def test_confirmation(self):
        async def run():
            controller = AuditionController(None, None)
            status = b'\x08\x63\x10\x01\x18\x01\x20\x01'    # currentPresetStatus, index 99
            body = b'\x08\x02' + b'\x2a' + varint(len(status)) + status
            controller.notify(None, bytes((0x35, 0, len(body) + 2)) + b'\x12' + bytes((len(body),)) + body)
            self.assertEqual((await controller.wait_status(5, 0.1)).get(1, 0) + 1, 100)
        asyncio.run(run())


if __name__ == '__main__':
    unittest.main()
