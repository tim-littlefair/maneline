"""Level measurement: analysis and matching (no amp needed)."""
import asyncio
import json
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import numpy as np
import mmp_level
from mmp_level import INTERFERENCE_HZ, RATE, Meter, check_signal, combine_takes, decode_s24, level_db, match, measurement_copy, played_level_db

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


class PlayedLevel(unittest.TestCase):
    def test_pauses_do_not_lower_the_level(self):
        loud = noise(-30, seconds=2.0)
        with_pauses = np.concatenate([loud[:RATE], np.zeros(RATE // 2), loud[RATE:], np.zeros(RATE // 2)])
        self.assertAlmostEqual(played_level_db(loud), -30, delta=0.5)
        self.assertAlmostEqual(played_level_db(with_pauses), -30, delta=0.5)

    def test_louder_take_measures_louder(self):
        self.assertAlmostEqual(played_level_db(noise(-27, seed=2)) - played_level_db(noise(-30, seed=3)), 3.0, delta=0.3)

    def test_played_method_keeps_the_gate(self):
        meter = Meter.__new__(Meter)
        meter.signal_sink, meter.played = None, True
        self.assertEqual(meter.prepare(PRESET)['audioGraph']['nodes'][0]['dspUnitParameters']['gatePreset'], 'mid')


class Takes(unittest.TestCase):
    def test_power_mean_and_spread(self):
        mean, spread = combine_takes([-30.0, -30.0])
        self.assertAlmostEqual(mean, -30.0)
        self.assertEqual(spread, 0.0)
        mean, spread = combine_takes([-30.0, -33.0])
        self.assertAlmostEqual(mean, -31.25, delta=0.01)   # power mean, not -31.5
        self.assertEqual(spread, 3.0)


class SignalChecks(unittest.TestCase):
    def test_tone_must_arrive(self):
        check_signal(-30.0, -80.0)                      # fine: 50 dB above
        with self.assertRaises(RuntimeError):
            check_signal(-80.4, -80.6)                  # no tone at the input (the dry run on 2026-11-10)
        with self.assertRaisesRegex(RuntimeError, 'no playing arrived'):
            check_signal(-104.7, -118.7, played=True)   # nobody played (dry run of the played method)

    def test_signal_method_keeps_the_gate(self):
        meter = Meter.__new__(Meter)
        meter.signal_sink, meter.played = 'phones', False
        self.assertEqual(meter.prepare(PRESET)['audioGraph']['nodes'][0]['dspUnitParameters']['gatePreset'], 'mid')
        meter.signal_sink = None
        self.assertEqual(meter.prepare(PRESET)['audioGraph']['nodes'][0]['dspUnitParameters']['gatePreset'], 'off')


class Stop(Exception):
    pass


class FakeMeter:
    '''Played method without an amp: each preset has a true level; the player adds
    `strength` dB; after `stop_after` measurements the player stops (Ctrl+C).'''

    signal_sink, played = None, True

    def __init__(self, true_db, strength=0.0, stop_after=None):
        self.true_db, self.strength, self.stop_after = true_db, strength, stop_after
        self.measured = []
        self.spreads = {}
        self.checked = False

    def prepare(self, doc, offset_db=0.0, name=None):
        return measurement_copy(doc, offset_db, gate_off=False, name=name)

    async def measure(self, doc):
        if self.stop_after is not None and len(self.measured) >= self.stop_after:
            raise Stop()
        self.checked = True
        name = doc['info']['displayName']
        self.measured.append(name)
        return self.true_db['80S Rock' if name == 'CP reference' else name] + self.strength


class Sessions(unittest.TestCase):
    '''A survey stopped and resumed later is on the scale of its first session.'''

    TRUE = {'80S Rock': -20.0, 'Jazz Clean': -18.0, 'Twin': -22.0, 'Deluxe': -23.5, 'Metal': -15.0}

    def setUp(self):
        self.entries = [{'slot': i, 'name': name} for i, name in enumerate(self.TRUE, 1)]
        self.progress = mmp_level.new_progress('played')

    def load(self, entry):
        doc = json.loads(json.dumps(PRESET))
        doc['info']['displayName'] = entry['name']
        return doc

    def run_session(self, meter, overlap=2):
        try:
            asyncio.run(mmp_level.survey(meter, self.entries, self.load, self.progress, lambda: None,
                                         overlap, use_reference=True, reference_every=2))
        except Stop:
            pass

    def test_stop_and_go_on_with_stronger_playing(self):
        # session 1: reference, then two presets, then the player stops
        first = FakeMeter(self.TRUE, stop_after=3)
        self.run_session(first)
        self.assertEqual(first.measured, ['CP reference', '80S Rock', 'Jazz Clean'])
        self.assertEqual(self.progress['order'], [1, 2])
        # session 2 plays 3 dB stronger; the last two of session 1 come first again
        second = FakeMeter(self.TRUE, strength=3.0)
        self.run_session(second)
        self.assertEqual(second.measured[:2], ['80S Rock', 'Jazz Clean'])
        self.assertEqual(self.progress['sessions'][1]['offset_db'], 3.0)
        self.assertEqual(self.progress['order'], [1, 2, 3, 4, 5])
        report = mmp_level.survey_report(self.progress, self.entries, {}, 'backup')
        corrections = {p['name']: p['correction_db'] for p in report['presets']}
        # true median -20: the stronger playing of session 2 doesn't show in the corrections
        self.assertEqual(corrections, {'80S Rock': 0.0, 'Jazz Clean': -2.0, 'Twin': 2.0, 'Deluxe': 3.5,
                                       'Metal': -5.0})
        self.assertEqual(report['reference']['drift_db'], 0.0)
        # reference at the start, after 2 (in session 2), after 4 and at the end
        self.assertEqual([r['after_presets'] for r in report['reference']['levels']], [0, 2, 4, 5])

    def test_overlap_one(self):
        self.run_session(FakeMeter(self.TRUE, stop_after=3))
        second = FakeMeter(self.TRUE, strength=-1.5)
        self.run_session(second, overlap=1)
        self.assertEqual(second.measured[0], 'Jazz Clean')
        self.assertEqual(self.progress['sessions'][1]['offset_db'], -1.5)

    def test_progress_file_round_trip(self):
        self.run_session(FakeMeter(self.TRUE, stop_after=3))
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'survey-played.json'
            mmp_level.save_progress(path, self.progress)
            self.assertEqual(mmp_level.load_progress(path, 'played'), self.progress)
            with self.assertRaises(SystemExit):
                mmp_level.load_progress(path, 'signal')


if __name__ == '__main__':
    unittest.main()
