"""Audition: request encoding, overrides and confirmation parsing (no amp needed)."""
import asyncio
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mmp_audition import PRESET_SAVED_STATUS, EXIT_AUDITION, AuditionController, apply_overrides, audition_message
from mmp_ble import fields, frames, varint
from mmp_backup import PRESET_DATA_STATUS
from mmp_lz4 import compress, decompress

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

    def test_unanswered_stored_preset_read_is_sent_again(self):
        async def run():
            controller = AuditionController(None, None)
            raw = compress(json.dumps(PRESET).encode())
            sent = []

            async def send(data):           # the first request goes unanswered
                sent.append(data)
                if len(sent) > 1:
                    controller.statuses[PRESET_DATA_STATUS] = {1: 20, 2: raw}
                    controller.arrived.set()
            controller.send = send
            with patch('mmp_audition.CONFIRM_TIMEOUT', 0.03):
                self.assertEqual(await controller.stored_preset(21), PRESET)
                self.assertEqual(len(sent), 2)
                sent.clear()
                controller.send = lambda data: asyncio.sleep(0, sent.append(data))
                with self.assertRaisesRegex(RuntimeError, 'did not send preset 21 \\(3 attempts\\)'):
                    await controller.stored_preset(21)
                self.assertEqual(len(sent), 3)
        asyncio.run(run())

    def test_unconfirmed_save_is_sent_again(self):
        async def run():
            controller = AuditionController(None, None)
            sent = []

            async def send_message(message):    # the first save is not confirmed
                sent.append(message)
                if len(sent) > 1:
                    controller.statuses[PRESET_SAVED_STATUS] = {1: 98}
                    controller.arrived.set()

            async def stored_preset(slot):
                return PRESET
            controller.send_message = send_message
            controller.stored_preset = stored_preset
            with patch('mmp_audition.SAVE_TIMEOUT', 0.03):
                self.assertEqual(await controller.save(99, PRESET, '80S Rock'), PRESET)
                self.assertEqual(len(sent), 2)
                self.assertEqual(sent[0], sent[1])
                sent.clear()
                controller.send_message = lambda message: asyncio.sleep(0, sent.append(message))
                with self.assertRaisesRegex(RuntimeError, 'did not confirm the save \\(3 attempts\\)'):
                    await controller.save(99, PRESET, '80S Rock')
                self.assertEqual(len(sent), 3)
        asyncio.run(run())


if __name__ == '__main__':
    unittest.main()


class SaveMessage(unittest.TestCase):
    '''savePresetAsRequest (18): index, compressed JSON, no load, name.'''

    def decode(self, slot, name):
        from mmp_audition import save_message, SAVE_AS_REQUEST
        from mmp_ble import fields
        from mmp_lz4 import decompress
        doc = {'info': {'displayName': 'x'}, 'audioGraph': {'nodes': [{'nodeId': 'amp'}]}}
        envelope = dict(fields(save_message(slot, doc, name)))
        request = dict(fields(envelope[1]))
        self.assertEqual(list(request), [SAVE_AS_REQUEST])
        body = dict(fields(request[SAVE_AS_REQUEST]))
        self.assertEqual(json.loads(decompress(body[2])), doc)
        self.assertNotIn(3, body)                       # isLoadPreset false: not selected
        return body

    def test_fields(self):
        body = self.decode(99, 'Twin -3')
        self.assertEqual((body[1], body[4]), (98, b'Twin -3'))

    def test_slot_one_has_no_index_field(self):
        self.assertNotIn(1, self.decode(1, 'A'))        # proto3 leaves out the default 0
