"""LZ4 block format used for Micro Plus preset JSON."""
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mmp_lz4 import compress, decompress


class Lz4Tests(unittest.TestCase):
    def test_start_of_a_captured_preset_reply(self):
        # The first bytes of the amp's current-preset reply (Driven Deluxe): 21 literals,
        # then a 5-byte match 20 bytes back, then literals up to the end of the sample.
        block = bytes.fromhex('f1067b226e6f646554797065223a22707265736574222c1400'
                              '404964223a')
        self.assertEqual(decompress(block), b'{"nodeType":"preset","nodeId":')

    def test_round_trip(self):
        preset = {'nodeType': 'preset', 'info': {'displayName': 'Echo Dome'},
                  'audioGraph': {'nodes': [{'nodeId': n, 'nodeType': 'dspUnit',
                                            'dspUnitParameters': {'bypass': False, 'level': 0.5}}
                                           for n in ('stomp', 'mod', 'amp', 'delay', 'reverb')]}}
        for data in (b'', b'x', b'abcd' * 3, b'a' * 300, json.dumps(preset).encode(), bytes(range(256)) * 3):
            block = compress(data)
            self.assertEqual(decompress(block), data)
        self.assertLess(len(compress(json.dumps(preset).encode())), len(json.dumps(preset)))

    def test_long_lengths(self):
        data = b'0123456789abcdef' * 40 + b'z' * 1000 + bytes(range(256))
        self.assertEqual(decompress(compress(data)), data)

    def test_malformed(self):
        for bad in (bytes.fromhex('f1'), bytes.fromhex('10'), bytes.fromhex('1041ff00'),
                    bytes.fromhex('104100')):
            with self.assertRaises(ValueError, msg=bad.hex()):
                decompress(bad)


if __name__ == '__main__':
    unittest.main()
