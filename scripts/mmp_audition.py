#!/usr/bin/env python3
"""Audition a preset on the Micro Plus: play it without saving it.

auditionPresetRequest (1) loads a complete preset (Fender's preset JSON, LZ4
compressed) into the amp's working memory. Nothing is written to the preset slots,
so auditioning costs no flash writes and survives no power cycle.

  mmp_audition.py _work/backups/<backup>/019-80S_Rock.json --volume -8 --slot 100

* --slot selects a slot first. A reserved empty slot (e.g. 100) keeps the
  auditioned name out of your real presets: the amp shows the auditioned name in
  place of the selected slot's name until a real slot is selected or the amp is
  switched off.
* At the end the amp selects the preset it started on again, which ends the
  audition (unless --keep).

Message numbers and the frame rules: docs/MUSTANG_MICRO_PLUS_PROTOCOL.md.
"""
import argparse
import asyncio
import contextlib
import json
import sys
import time

from mmp_ble import (Controller, KEEPALIVE, KEEPALIVE_INTERVAL, NAME, RESPONSE, SCAN_FILTER,
                     fields, tone_message, tone_request, varint)
from mmp_bluez import make_client
from mmp_lz4 import compress, decompress

AUDITION_REQUEST = 1            # MustangLtxToneRequest.auditionPresetRequest {compressedPresetJson = 1}
CURRENT_PRESET_REQUEST = 4      # .currentPresetRequest {dummyField = 1}
CURRENT_PRESET_DATA_STATUS = 4  # MustangLtxAmpStatus.currentPresetDataStatus {compressedPresetJson = 1}
CURRENT_PRESET_STATUS = 5       # .currentPresetStatus {presetIndex = 1, ...}
UNSUPPORTED_STATUS = 17
# exitAuditionRequest {dummyField = true}: an empty message is ignored by the amp.
EXIT_AUDITION = tone_request(6, b'\x08\x01')
CONFIRM_TIMEOUT = 3


def apply_overrides(doc, name=None, volume=None):
    """A copy of the preset JSON with a new display name and/or amp volume (dB)."""
    doc = json.loads(json.dumps(doc))
    if name is not None:
        doc.setdefault('info', {})['displayName'] = name
    if volume is not None:
        amps = [n for n in doc['audioGraph']['nodes'] if n.get('nodeId') == 'amp']
        if not amps:
            raise ValueError('preset has no amp node')
        amps[0].setdefault('dspUnitParameters', {})['volume'] = float(volume)
    return doc


def audition_message(doc):
    """The unframed auditionPresetRequest for a preset JSON document."""
    raw = compress(json.dumps(doc, separators=(',', ':'), ensure_ascii=False).encode('utf-8'))
    return tone_message(AUDITION_REQUEST, b'\x0a' + varint(len(raw)) + raw)


def summary(doc):
    amp = next((n for n in doc.get('audioGraph', {}).get('nodes', []) if n.get('nodeId') == 'amp'), {})
    volume = amp.get('dspUnitParameters', {}).get('volume')
    return '"{}" {} {}'.format(doc.get('info', {}).get('displayName'),
                               amp.get('FenderId', '?').replace('ACD_', ''),
                               '{:.1f} dB'.format(volume) if volume is not None else '')


class AuditionController(Controller):
    """Controller that also collects the replies that confirm an audition."""

    def __init__(self, client, log):
        super().__init__(client, log)
        self.statuses = {}
        self.arrived = asyncio.Event()
        feed = self.frames.feed

        def tapped_feed(data):
            message = feed(data)
            if message is not None:
                self.on_message(message)
            return message
        self.frames.feed = tapped_feed

    def on_message(self, message):
        if not message.startswith(b'\x12'):
            return
        try:
            for number, body in fields(message):
                if number == 2 and isinstance(body, bytes):
                    for kind, value in fields(body):
                        if kind != 1 and isinstance(value, bytes):
                            self.statuses[kind] = dict(fields(value))
                            self.arrived.set()
        except ValueError:
            pass

    async def wait_status(self, kind, timeout):
        async with asyncio.timeout(timeout):
            while kind not in self.statuses:
                if UNSUPPORTED_STATUS in self.statuses:
                    raise RuntimeError('the amp rejected the request (error {})'.format(
                        self.statuses[UNSUPPORTED_STATUS].get(1, 0)))
                self.arrived.clear()
                await self.arrived.wait()
        return self.statuses[kind]

    async def loaded(self):
        """The preset the amp has loaded now (loaded form)."""
        self.statuses.pop(CURRENT_PRESET_DATA_STATUS, None)
        await self.send(tone_request(CURRENT_PRESET_REQUEST, b'\x08\x01'))
        status = await self.wait_status(CURRENT_PRESET_DATA_STATUS, CONFIRM_TIMEOUT)
        return json.loads(decompress(status[1]))

    async def audition(self, doc, attempts=3):
        """Send the preset; return the slot the amp reports it on (1-based).

        Now and then the amp does not confirm an audition (about one in 25 in a survey
        of all factory presets, independent of the preset); the message was probably
        lost on the radio. Auditioning is idempotent, so it is simply sent again.
        """
        message = audition_message(doc)
        for attempt in range(1, attempts + 1):
            self.statuses.clear()
            await self.send_message(message)
            try:
                status = await self.wait_status(CURRENT_PRESET_STATUS, CONFIRM_TIMEOUT)
                return status.get(1, 0) + 1
            except TimeoutError:
                if attempt == attempts:
                    raise RuntimeError('the amp did not confirm the audition '
                                       '({} attempts)'.format(attempts)) from None
                await asyncio.sleep(0.5)


async def heartbeat(controller):
    while True:
        await asyncio.sleep(KEEPALIVE_INTERVAL)
        await controller.send(KEEPALIVE)


async def run(args):
    doc = apply_overrides(json.load(open(args.preset, encoding='utf-8')), args.name, args.volume)
    from bleak import BleakScanner
    device = await BleakScanner.find_device_by_filter(
        lambda d, advert: (d.name == NAME or advert.local_name == NAME)
        and (args.device is None or d.address == args.device), **SCAN_FILTER, timeout=10)
    if device is None:
        sys.exit('Micro Plus not found. Turn it on and stop other programs that control it.')
    log = open(args.log, 'a', encoding='utf-8') if args.log else None
    client = await make_client(device)
    async with client:
        controller = AuditionController(client, log)
        await client.start_notify(RESPONSE, controller.notify)
        beat = asyncio.create_task(heartbeat(controller))
        try:
            await controller.initialize()
            original = controller.slot
            print('Amp on preset {} "{}"'.format(original, controller.names[original - 1]), flush=True)
            if args.slot:
                await controller.switch(args.slot)
            try:
                slot = await controller.audition(doc)
                print('Auditioning on slot {}: {}'.format(slot, summary(await controller.loaded())),
                      flush=True)
                if args.hold:
                    await asyncio.sleep(args.hold)
            finally:
                if not args.keep:
                    await controller.switch(original)
                    print('Back on preset {}: {}'.format(original, summary(await controller.loaded())),
                          flush=True)
        finally:
            beat.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await beat
    if log:
        log.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('preset', help='preset JSON file, e.g. from mmp_backup.py')
    parser.add_argument('--slot', type=int, choices=range(1, 101), metavar='1-100',
                        help='select this slot first (e.g. a reserved empty one)')
    parser.add_argument('--name', help='display name for the auditioned preset')
    parser.add_argument('--volume', type=float, help='amp volume in dB')
    parser.add_argument('--hold', type=float, default=10, help='seconds to keep it (default 10)')
    parser.add_argument('--keep', action='store_true',
                        help='leave the audition running instead of returning to the original preset')
    parser.add_argument('--device', help='Bluetooth address of the amp')
    parser.add_argument('--log', help='append the BLE traffic to this JSONL file')
    args = parser.parse_args()
    try:
        asyncio.run(run(args))
    except KeyboardInterrupt:
        pass


if __name__ == '__main__':
    main()
