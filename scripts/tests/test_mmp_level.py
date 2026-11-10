"""Level measurement: analysis and matching (no amp needed)."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import numpy as np
from mmp_level import INTERFERENCE_HZ, RATE, decode_s24, level_db, match, measurement_copy

PRESET = {'info': {'displayName': '80S Rock'},
          'audioGraph': {'nodes': [{'nodeId': 'amp', 'dspUnitParameters': {'volume': -5.0, 'gatePreset': 'mid'}}]}}


def noise(db, seconds=2.0, seed=1):
    rng = np.random.default_rng(seed)
    x = rng.standard_normal(int(RATE * seconds))
    # band-limit roughly to the analysis band so the expected level is known
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    spec[(f < 40) | (f > 12000)] = 0
    x = np.fft.irfft(spec, len(x))
    return x / np.sqrt(np.mean(x ** 2)) * 10 ** (db / 20)


class LevelTests(unittest.TestCase):
    def test_level_of_known_noise(self):
        for db in (-30, -60, -90):
            self.assertAlmostEqual(level_db(noise(db)), db, delta=0.2)

    def test_level_difference_is_exact(self):
        self.assertAlmostEqual(level_db(noise(-80.4)) - level_db(noise(-87.4)), 7.0, delta=0.05)

    def test_interference_line_is_ignored(self):
        t = np.arange(int(RATE * 2)) / RATE
        line = 10 ** (-70 / 20) * np.sqrt(2) * np.sin(2 * np.pi * INTERFERENCE_HZ * t)
        self.assertAlmostEqual(level_db(noise(-90) + line), -90, delta=0.3)

    def test_silence(self):
        self.assertLess(level_db(np.zeros(RATE)), -200)

    def test_decode_s24(self):
        samples = [0, 1 << 22, -(1 << 22), (1 << 23) - 1]
        raw = b''.join(int(s).to_bytes(3, 'little', signed=True) * 2 for s in samples)
        np.testing.assert_allclose(decode_s24(raw), [0, 0.5, -0.5, 1 - 2 ** -23])

    def test_measurement_copy(self):
        doc = measurement_copy(PRESET, 3.0, name='CP')
        params = doc['audioGraph']['nodes'][0]['dspUnitParameters']
        self.assertEqual((params['volume'], params['gatePreset'], doc['info']['displayName']), (-2.0, 'off', 'CP'))
        self.assertEqual(PRESET['audioGraph']['nodes'][0]['dspUnitParameters']['volume'], -5.0)

    def test_match_to_median(self):
        target, result = match({1: (-5.0, -80.0), 2: (-12.0, -90.0), 3: (-20.0, -85.0)})
        self.assertEqual(target, -85.0)
        self.assertEqual(result[1]['correction_db'], -5.0)
        self.assertEqual(result[2]['matched_volume_db'], -7.0)
        self.assertEqual(result[3]['correction_db'], 0.0)


if __name__ == '__main__':
    unittest.main()
