#!/usr/bin/env python3
"""Measure the Micro Plus output level over USB: check level changes, match presets.

The amp's USB recording carries what the amp outputs, but only while its plug is
in an instrument (unplugged it sends exact zeros). Two ways to get a signal:

* noise (works now): the guitar stays connected with its volume at zero, and the
  auditioned preset has its noise gate off. The amp's own input noise then passes
  every stage, so its level follows the preset. This is exact for changes within
  one preset: an amp volume 7 dB lower measured 6.8 dB lower, repeatably to 0.1 dB.
  Across different presets it is an approximation: tiny noise doesn't drive
  distortion or compressors the way a guitar does, so high-gain and compressed
  presets measure louder than they sound when played.
* signal (needs a cable, not yet tested): a guitar-like test tone from the computer's
  headphone output into a 6.35 mm socket the amp is plugged into (--signal-sink).

Everything happens by audition on a reserved slot (default 100): nothing is
written to the amp, and the amp returns to its original preset at the end.

  mmp_level.py delta PRESET.json [--offsets 0,3,6,-3,-6]      # does +3 dB give +3 dB?
  mmp_level.py survey BACKUP_DIR [--slots 1-71] [--out FILE]  # level of every preset

Analysis needs numpy.
"""
import argparse
import asyncio
import contextlib
import json
import os
import signal
import statistics
import subprocess
import sys
import tempfile
import time
import wave
from pathlib import Path

import numpy as np

RATE = 48000
SOURCE_HINT = 'Fender_Mustang_Micro_Plus'
# A constant line at 48000/18 Hz (and harmonics) is in every recording, with or
# without input; probably USB or display interference. It is notched out so that it
# cannot mask level changes of quiet presets.
INTERFERENCE_HZ = RATE / 18
NOTCH_HZ = 25
BAND = (40.0, 12000.0)
SILENCE_DB = -200.0             # an all-zero recording measures far below this
# signal method: the test tone must be clearly above what the amp sends without it,
# or it does not reach the guitar input (cable missing or in the wrong socket)
SIGNAL_MARGIN_DB = 15.0
SIGNAL_VOLUME = '40%'           # output volume while the test tone plays (reproducible level)


# ---------------------------------------------------------------- analysis

def played_level_db(x, rate=RATE, frame_s=0.1):
    '''Level of the loud part of a played take: mean power of the loudest half of the
    100 ms frames, so pauses between strums don't lower the result.'''
    x = np.asarray(x, dtype=float)
    n = int(rate * frame_s)
    frames = len(x) // n
    if frames < 4:
        raise ValueError('recording too short')
    powers = []
    for i in range(frames):
        chunk = x[i * n:(i + 1) * n]
        powers.append(10 ** (level_db(chunk, rate) / 10) if np.any(chunk) else 0.0)
    loud = sorted(powers)[frames // 2:]
    mean = sum(loud) / len(loud)
    return 10 * np.log10(mean) if mean > 0 else SILENCE_DB - 40


def decode_s24(raw, channels=2):
    """24-bit little-endian PCM to a mono float array in [-1, 1)."""
    b = np.frombuffer(raw, np.uint8)
    b = b[:len(b) - len(b) % (3 * channels)].reshape(-1, 3)
    x = (b[:, 0].astype(np.int32) | b[:, 1].astype(np.int32) << 8
         | b[:, 2].astype(np.int32) << 16) << 8 >> 8
    return (x / float(1 << 23)).reshape(-1, channels).mean(axis=1)


def level_db(x, rate=RATE):
    """Band-limited RMS level in dBFS, without the interference line."""
    x = np.asarray(x, dtype=float)
    if len(x) < rate // 20:
        raise ValueError('recording too short')
    x = x - x.mean()
    # Hann window: the interference line falls between FFT bins; without a window its
    # leakage spreads far beyond the notch (a -70 dB line still counted as -95 dB).
    window = np.hanning(len(x))
    power = np.abs(np.fft.rfft(x * window)) ** 2
    f = np.fft.rfftfreq(len(x), 1 / rate)
    keep = (f >= BAND[0]) & (f <= BAND[1])
    for k in range(1, int(BAND[1] / INTERFERENCE_HZ) + 1):
        keep &= np.abs(f - k * INTERFERENCE_HZ) > NOTCH_HZ
    # Parseval for a windowed one-sided spectrum: mean square = 2 * sum|X|^2 / (N * sum w^2)
    mean_square = 2 * power[keep].sum() / (len(x) * np.sum(window ** 2))
    return 10 * np.log10(mean_square) if mean_square > 0 else SILENCE_DB - 40


# ---------------------------------------------------------------- recording

def usb_device(kind):
    """The PipeWire name of the amp's USB 'sources' (recording) or 'sinks'."""
    out = subprocess.run(['pactl', 'list', 'short', kind], capture_output=True, text=True,
                         check=True).stdout
    names = [line.split('\t')[1] for line in out.splitlines()
             if SOURCE_HINT in line and '.monitor' not in line]
    if not names:
        raise RuntimeError('Micro Plus USB {} not found: connect the amp by USB'.format(kind))
    return names[0]


def signal_sink(name):
    '''The PipeWire sink for the test tone; "auto" picks the headphone output.'''
    if name != 'auto':
        return name
    out = subprocess.run(['pactl', 'list', 'short', 'sinks'], capture_output=True, text=True,
                         check=True).stdout
    sinks = [line.split('\t')[1] for line in out.splitlines()]
    phones = [s for s in sinks if 'headphone' in s.lower()]
    if len(phones) != 1:
        raise RuntimeError('cannot pick the headphone output automatically; give --signal-sink '
                           'one of: ' + ', '.join(sinks))
    return phones[0]


def check_signal(tone_db, silent_db, margin=SIGNAL_MARGIN_DB, played=False):
    '''Raise unless the test tone (or the playing) clearly reaches the amp.'''
    if tone_db - silent_db < margin:
        if played:
            raise RuntimeError(
                'no playing arrived: {:.1f} dBFS during the take, {:.1f} dBFS with muted strings '
                '(needs {:.0f} dB more). Turn the guitar volume up and play the riff when '
                'prompted.'.format(tone_db, silent_db, margin))
        raise RuntimeError(
            'the test tone does not reach the guitar input: {:.1f} dBFS with it, {:.1f} dBFS '
            'without (needs {:.0f} dB more). Check the cable from the headphone output to the '
            'amp\'s plug, and the output volume.'.format(tone_db, silent_db, margin))


@contextlib.contextmanager
def sink_volume(sink, volume):
    '''Set the test-tone output to a fixed volume, restore it afterwards.'''
    before = subprocess.run(['pactl', 'get-sink-volume', sink], capture_output=True,
                            text=True).stdout.split('/')
    previous = before[1].strip() if len(before) > 1 else None
    subprocess.run(['pactl', 'set-sink-volume', sink, volume], check=True)
    try:
        yield
    finally:
        if previous:
            subprocess.run(['pactl', 'set-sink-volume', sink, previous])


def record(seconds, source=None, play=None, play_sink=None):
    """Record the amp's USB output; optionally play a WAV file meanwhile."""
    source = source or usb_device('sources')
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, 'rec.wav')
        rec = subprocess.Popen(['pw-record', '--target', source, '--rate', str(RATE),
                                '--channels', '2', '--format', 's24', path],
                               stderr=subprocess.DEVNULL)
        try:
            time.sleep(0.3)
            if play:
                subprocess.run(['pw-play', '--target', play_sink, play], check=True)
            else:
                time.sleep(seconds)
        finally:
            rec.send_signal(signal.SIGINT)
            rec.wait(timeout=5)
        with wave.open(path, 'rb') as w:
            x = decode_s24(w.readframes(w.getnframes()), w.getnchannels())
    skip = int(0.2 * RATE)                     # the start can carry the previous tone
    return x[skip:]


def test_tone_wav(path, seconds=3.0, level_db=-20.0):
    """A guitar-like test signal: decaying plucked A2/E3/A3 notes (signal method)."""
    t = np.arange(int(RATE * seconds)) / RATE
    x = np.zeros_like(t)
    for start, freq in ((0.0, 110.0), (0.5, 164.8), (1.0, 220.0), (1.5, 110.0), (2.0, 164.8)):
        tt = t - start
        on = tt >= 0
        note = sum(np.sin(2 * np.pi * freq * k * tt) / k ** 1.5 for k in range(1, 6))
        x[on] += note[on] * np.exp(-tt[on] * 2.5)
    x *= 10 ** (level_db / 20) / (np.sqrt(np.mean(x ** 2)) + 1e-12)
    data = (np.clip(x, -1, 1) * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data.tobytes())


# ---------------------------------------------------------------- presets

def amp_params(doc):
    amps = [n for n in doc.get('audioGraph', {}).get('nodes', []) if n.get('nodeId') == 'amp']
    if not amps:
        raise ValueError('preset has no amp node')
    return amps[0].setdefault('dspUnitParameters', {})


def measurement_copy(doc, offset_db=0.0, gate_off=True, name=None):
    """A copy for measuring: amp volume shifted by offset_db, noise gate off."""
    doc = json.loads(json.dumps(doc))
    params = amp_params(doc)
    params['volume'] = float(params.get('volume', 0.0)) + offset_db
    if gate_off:
        params['gatePreset'] = 'off'
    if name is not None:
        doc.setdefault('info', {})['displayName'] = name
    return doc


def match(levels, target=None):
    """Volume corrections that bring every measured level to the target (default:
    median). levels: {key: (amp_volume_db, measured_db)}."""
    measured = [m for _, m in levels.values()]
    target = statistics.median(measured) if target is None else target
    return target, {key: {'amp_volume_db': volume, 'measured_db': round(m, 2),
                          'correction_db': round(target - m, 2),
                          'matched_volume_db': round(volume + target - m, 2)}
                    for key, (volume, m) in levels.items()}


# ---------------------------------------------------------------- amp session

class Meter:
    """Audition presets on a reserved slot and measure each one."""

    def __init__(self, controller, slot=100, seconds=3.0, settle=0.7, signal_sink=None, played=False):
        self.controller = controller
        self.played = played            # the player plays a riff for every preset
        self.slot = slot
        self.seconds = seconds
        self.settle = settle
        self.signal_sink = signal_sink
        self.source = usb_device('sources')
        self.tone = None
        self.checked = False

    def prepare(self, doc, offset_db=0.0, name=None):
        '''The measurement copy: noise method with the gate off; signal method as the
        preset is played (a real signal opens its gate).'''
        return measurement_copy(doc, offset_db, gate_off=not (self.signal_sink or self.played), name=name)

    async def measure(self, doc):
        if self.played:
            return await self._measure_played(doc)
        if self.signal_sink and not self.checked:
            # once per run: with the tone the amp must be far above its level without it
            silent = await self._measure(doc, use_signal=False)
            tone = await self._measure(doc)
            check_signal(tone, silent)
            self.checked = True
            return tone
        return await self._measure(doc)

    async def _measure_played(self, doc):
        '''Played method: the player plays the same riff for every preset.'''
        if not self.checked:
            print('Mute the strings and keep still for 3 s (noise floor)...', flush=True)
            await asyncio.sleep(1.0)
            self.floor = level_db(await asyncio.to_thread(record, 3.0, self.source))
        confirmed = await self.controller.audition(doc)
        if confirmed != self.slot:
            raise RuntimeError('audition confirmed on slot {}, expected {}'.format(confirmed, self.slot))
        print('\a  "{}": play your riff now ({:.0f} s)'.format(
            doc.get('info', {}).get('displayName'), self.seconds), flush=True)
        await asyncio.sleep(0.5)
        level = played_level_db(await asyncio.to_thread(record, self.seconds, self.source))
        if not self.checked:
            check_signal(level, self.floor, played=True)
            self.checked = True
        return level

    async def _measure(self, doc, use_signal=True):
        confirmed = await self.controller.audition(doc)
        if confirmed != self.slot:
            raise RuntimeError('audition confirmed on slot {}, expected {}'.format(confirmed, self.slot))
        await asyncio.sleep(self.settle)
        if self.signal_sink and use_signal:
            if self.tone is None:
                self.tone = os.path.join(tempfile.mkdtemp(), 'tone.wav')
                test_tone_wav(self.tone, self.seconds)
            x = await asyncio.to_thread(record, self.seconds, self.source, self.tone, self.signal_sink)
        else:
            x = await asyncio.to_thread(record, self.seconds, self.source)
        level = level_db(x)
        if level < SILENCE_DB:
            raise RuntimeError('the amp sends silence over USB: plug it into an instrument '
                               '(with the instrument volume at zero for the noise method)')
        return level


async def with_amp(args, work):
    """Connect, select the reserved slot, run work(meter), return to the original preset."""
    from bleak import BleakScanner
    from mmp_audition import AuditionController, heartbeat
    from mmp_ble import NAME, RESPONSE, SCAN_FILTER
    from mmp_bluez import make_client
    device = await BleakScanner.find_device_by_filter(
        lambda d, advert: (d.name == NAME or advert.local_name == NAME)
        and (args.device is None or d.address == args.device), **SCAN_FILTER, timeout=10)
    if device is None:
        sys.exit('Micro Plus not found. Turn it on and stop other programs that control it.')
    client = await make_client(device)
    async with client:
        controller = AuditionController(client, None)
        await client.start_notify(RESPONSE, controller.notify)
        beat = asyncio.create_task(heartbeat(controller))
        try:
            await controller.initialize()
            original = controller.slot
            await controller.switch(args.slot)
            try:
                sink = signal_sink(args.signal_sink) if args.signal_sink else None
                meter = Meter(controller, args.slot, args.seconds, args.settle, sink, args.played)
                if sink:
                    with sink_volume(sink, args.signal_volume):
                        return await work(meter)
                return await work(meter)
            finally:
                await controller.switch(original)
                print('Back on preset {} "{}"'.format(original, controller.names[original - 1]),
                      flush=True)
        finally:
            beat.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await beat


def run_delta(args):
    preset = json.load(open(args.preset, encoding='utf-8'))
    offsets = [float(o) for o in args.offsets.split(',')]
    base_volume = amp_params(preset).get('volume', 0.0)

    async def work(meter):
        rows = []
        reference = await meter.measure(meter.prepare(preset, 0.0, name='CP level ref'))
        for offset in offsets:
            levels = []
            for _ in range(args.repeat):
                levels.append(await meter.measure(meter.prepare(
                    preset, offset, name='CP level {:+g}'.format(offset))))
            rows.append((offset, statistics.mean(levels), max(levels) - min(levels)))
        drift = await meter.measure(meter.prepare(preset, 0.0, name='CP level ref')) - reference
        return reference, rows, drift

    reference, rows, drift = asyncio.run(with_amp(args, work))
    print('{} (amp volume {:.1f} dB), reference {:.1f} dBFS, {}'.format(
        preset.get('info', {}).get('displayName'), base_volume, reference,
        'signal method' if args.signal_sink else 'noise method, gate off'))
    ok = True
    for offset, level, spread in rows:
        delta = level - reference
        passed = abs(delta - offset) <= args.tolerance
        ok &= passed
        print('  volume {:+5.1f} dB -> {:7.1f} dBFS  measured {:+5.1f} dB  expected {:+5.1f}  {}'
              '{}'.format(offset, level, delta, offset, 'ok' if passed else 'FAIL',
                          '  (spread {:.1f} dB)'.format(spread) if args.repeat > 1 else ''))
    print('  reference drift {:+.2f} dB'.format(drift))
    if abs(drift) > args.tolerance:
        ok = False
        print('  FAIL: the reference moved during the test')
    return 0 if ok else 1


def run_survey(args):
    from mmp_backup import slot_list
    backup = Path(args.backup)
    manifest = json.load(open(backup / 'manifest.json', encoding='utf-8'))
    wanted = set(slot_list(args.slots))
    entries = [p for p in manifest['presets'] if p['slot'] in wanted and p['name'] != 'Empty']

    async def work(meter):
        levels, failed = {}, {}
        for entry in entries:
            doc = json.load(open(backup / entry['file'], encoding='utf-8'))
            volume = amp_params(doc).get('volume', 0.0)
            try:
                level = await meter.measure(meter.prepare(doc))
            except RuntimeError as error:
                if not meter.checked and (meter.signal_sink or meter.played):
                    raise           # no tone / no playing arrives: no point measuring on
                failed[entry['slot']] = str(error)
                print('  {:3d} {:18s} FAILED: {}'.format(entry['slot'], entry['name'], error), flush=True)
                continue
            levels[entry['slot']] = (volume, level)
            print('  {:3d} {:18s} amp volume {:6.1f} dB -> {:7.1f} dBFS'.format(
                entry['slot'], entry['name'], volume, level), flush=True)
        return levels, failed

    levels, failed = asyncio.run(with_amp(args, work))
    if not levels:
        print('no preset could be measured; nothing written')
        return 1
    target, result = match(levels)
    report = {'method': 'played' if args.played else ('signal' if args.signal_sink else 'noise'),
              'note': {'noise': 'noise method: exact within a preset, approximate across presets '
                                '(high-gain and compressed presets measure louder than they sound)',
                       'signal': 'signal method: test tone at the guitar input',
                       'played': 'played method: the same riff played for every preset (about +-1 dB)'}[
                  'played' if args.played else ('signal' if args.signal_sink else 'noise')],
              'measured_at': time.strftime('%Y-%m-%dT%H:%M:%S%z'), 'firmware': manifest.get('firmware'),
              'backup': str(backup), 'target_db': round(target, 2),
              'presets': [dict(slot=slot, name=next(e['name'] for e in entries if e['slot'] == slot),
                               **values) for slot, values in sorted(result.items())],
              'failed': [{'slot': slot, 'error': error} for slot, error in sorted(failed.items())]}
    out = Path(args.out or backup / 'levels-{}.json'.format(report['method']))
    out.write_text(json.dumps(report, indent=1) + '\n', encoding='utf-8')
    print('target {:.1f} dBFS (median); corrections written to {}{}'.format(
        target, out, '; {} failed'.format(len(failed)) if failed else ''))
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--slot', type=int, default=100, help='reserved slot to audition on')
    parser.add_argument('--seconds', type=float, default=3.0, help='recording length')
    parser.add_argument('--settle', type=float, default=0.7, help='wait after each audition')
    parser.add_argument('--signal-sink', help='PipeWire sink feeding the guitar input (signal method); '
                                              '"auto" picks the headphone output')
    parser.add_argument('--played', action='store_true',
                        help='played method: you play the same riff for every preset (no cable needed)')
    parser.add_argument('--signal-volume', default=SIGNAL_VOLUME,
                        help='output volume while the test tone plays (default %(default)s)')
    parser.add_argument('--device', help='Bluetooth address of the amp')
    sub = parser.add_subparsers(dest='command', required=True)
    delta = sub.add_parser('delta', help='check that volume offsets give the same level change')
    delta.add_argument('preset', help='preset JSON, e.g. from mmp_backup.py')
    delta.add_argument('--offsets', default='3,6,-3,-6', help='dB, comma separated')
    delta.add_argument('--repeat', type=int, default=1, help='measurements per offset')
    delta.add_argument('--tolerance', type=float, default=1.0, help='allowed error in dB')
    survey = sub.add_parser('survey', help='measure every preset of a backup and match levels')
    survey.add_argument('backup', help='directory written by mmp_backup.py')
    survey.add_argument('--slots', default='1-100', help='e.g. 1-71')
    survey.add_argument('--out', help='result file (default: <backup>/levels-<method>.json)')
    args = parser.parse_args()
    if args.played and args.signal_sink:
        parser.error('choose either --played or --signal-sink')
    if args.played and args.seconds < 4:
        args.seconds = 4.0
    sys.exit(run_delta(args) if args.command == 'delta' else run_survey(args))


if __name__ == '__main__':
    main()
