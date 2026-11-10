#!/usr/bin/env python3
"""Back up the Micro Plus presets as JSON. Read-only.

For every slot it sends presetDataRequest (12), which returns the preset exactly as
stored in that slot (presetDataStatus, 10), without selecting it: the amp stays on
its current preset, and nothing is written. Message numbers: see
docs/MUSTANG_MICRO_PLUS_PROTOCOL.md.

The stored form is what a restore writes back. (The current-preset reply, request 4,
is the loaded state instead: numbers widened to float, and a few fields differ.)

Output: a directory with one pretty-printed JSON file per slot and manifest.json,
which also keeps each preset exactly as the amp sent it (LZ4 block, base64), for a
byte-exact restore.
"""
import argparse
import asyncio
import base64
import contextlib
import hashlib
import json
import re
import sys
import time
from pathlib import Path

from mmp_ble import (Controller, KEEPALIVE, KEEPALIVE_INTERVAL, NAME, RESPONSE, SCAN_FILTER,
                     fields, tone_request, varint)
from mmp_bluez import make_client
from mmp_lz4 import decompress

PRESET_DATA_REQUEST = 12        # MustangLtxToneRequest.presetDataRequest {presetIndex = 1}
PRESET_DATA_STATUS = 10         # MustangLtxAmpStatus.presetDataStatus {presetIndex = 1, compressedPresetJson = 2}
UNSUPPORTED_STATUS = 17         # MustangLtxAmpStatus.unsupportedMessageStatus {status = 1}
FIRMWARE_STATUS = 6             # MustangLtxAmpStatus.firmwareVersionStatus {version = 1}
ERRORS = {0: 'UNSUPPORTED', 1: 'FAILED', 2: 'INVALID_PARAM', 3: 'INVALID_NODE_ID',
          4: 'PARAM_OUT_OF_BOUNDS', 5: 'FACTORY_RESTORE_IN_PROGRESS'}
READ_TIMEOUT = 8
# default output, independent of the working directory (_work is not tracked by git)
BACKUP_DIR = Path(__file__).resolve().parents[1] / '_work' / 'backups'


def preset_data_request(slot):
    index = slot - 1                    # 0-based, like loadPresetRequest
    body = b'\x08' + varint(index) if index else b''    # proto3 omits the default 0
    return tone_request(PRESET_DATA_REQUEST, body)


class BackupController(Controller):
    """Controller that also collects presetDataStatus, error and firmware replies."""

    def __init__(self, client, log):
        super().__init__(client, log)
        self.presets = {}           # 0-based index -> raw LZ4 block
        self.error = None
        self.firmware = None
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
                if number != 2 or not isinstance(body, bytes):
                    continue
                for kind, value in fields(body):
                    if not isinstance(value, bytes):
                        continue
                    content = dict(fields(value))
                    if kind == PRESET_DATA_STATUS and isinstance(content.get(2), bytes):
                        self.presets[content.get(1, 0)] = content[2]
                        self.arrived.set()
                    elif kind == UNSUPPORTED_STATUS:
                        self.error = ERRORS.get(content.get(1, 0), content.get(1))
                        self.arrived.set()
                    elif kind == FIRMWARE_STATUS and isinstance(content.get(1), bytes):
                        self.firmware = content[1].decode('utf-8', 'replace')
        except ValueError:
            pass                    # message types the simple decoder doesn't know

    async def read_slot(self, slot):
        """The preset stored in slot as (raw LZ4 block, JSON document)."""
        index = slot - 1
        self.presets.pop(index, None)
        self.error = None
        self.arrived.clear()
        await self.send(preset_data_request(slot))
        async with asyncio.timeout(READ_TIMEOUT):
            while index not in self.presets:
                if self.failure:
                    raise self.failure
                if self.error:
                    raise RuntimeError('slot {}: the amp answered {}'.format(slot, self.error))
                self.arrived.clear()
                await self.arrived.wait()
        raw = self.presets[index]
        return raw, json.loads(decompress(raw))


def slot_list(text):
    slots = set()
    for part in text.split(','):
        first, _, last = part.partition('-')
        first, last = int(first), int(last or first)
        if not 1 <= first <= last <= 100:
            raise argparse.ArgumentTypeError('slots must be within 1-100, e.g. 1-71,80')
        slots.update(range(first, last + 1))
    return sorted(slots)


def file_name(slot, name):
    safe = re.sub(r'[^A-Za-z0-9._-]+', '_', name.strip()).strip('_') or 'unnamed'
    return '{:03d}-{}.json'.format(slot, safe)


async def heartbeat(controller):
    while True:
        await asyncio.sleep(KEEPALIVE_INTERVAL)
        await controller.send(KEEPALIVE)


async def backup(args):
    from bleak import BleakScanner
    device = await BleakScanner.find_device_by_filter(
        lambda d, advert: (d.name == NAME or advert.local_name == NAME)
        and (args.device is None or d.address == args.device), **SCAN_FILTER, timeout=10)
    if device is None:
        sys.exit('Micro Plus not found. Turn it on, close Fender Tone and stop other programs '
                 'that control it (e.g. the browser server or ChordsPlay\'s amp agent).')
    out = Path(args.out) if args.out else BACKUP_DIR / ('micro-plus-' + time.strftime('%Y%m%d-%H%M%S'))
    out.mkdir(parents=True, exist_ok=False)
    log = open(args.log, 'a', encoding='utf-8') if args.log else None
    client = await make_client(device)
    async with client:
        controller = BackupController(client, log)
        await client.start_notify(RESPONSE, controller.notify)
        beat = asyncio.create_task(heartbeat(controller))
        try:
            await controller.initialize()
            names = list(controller.names)
            print('Connected: firmware {}, {} presets, current {}'.format(
                controller.firmware, len(names), controller.slot), flush=True)
            manifest = {'device': device.address, 'created': time.strftime('%Y-%m-%dT%H:%M:%S%z'),
                        'firmware': controller.firmware, 'current_slot': controller.slot,
                        'source': 'presetDataRequest (stored form)', 'names': names, 'presets': []}
            try:
                for slot in args.slots:
                    expected = names[slot - 1].strip() if slot <= len(names) else ''
                    raw, doc = await controller.read_slot(slot)
                    got = doc.get('info', {}).get('displayName', '').strip()
                    if got != expected:
                        print('  slot {}: name in JSON "{}" differs from "{}"'.format(
                            slot, got, expected), flush=True)
                    name = file_name(slot, expected or got)
                    (out / name).write_text(json.dumps(doc, indent=1, ensure_ascii=False) + '\n',
                                            encoding='utf-8')
                    manifest['presets'].append({
                        'slot': slot, 'name': expected, 'json_name': got, 'file': name,
                        'sha256': hashlib.sha256(raw).hexdigest(),
                        'lz4_block_base64': base64.b64encode(raw).decode()})
                    print('  {:3d} {}'.format(slot, expected), flush=True)
            finally:
                (out / 'manifest.json').write_text(json.dumps(manifest, indent=1, ensure_ascii=False)
                                                   + '\n', encoding='utf-8')
        finally:
            beat.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await beat
    if log:
        log.close()
    print('{} presets saved in {} (amp still on preset {})'.format(
        len(manifest['presets']), out, controller.slot))


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--slots', type=slot_list, default=list(range(1, 101)),
                        help='slots to back up, e.g. 1-71,80 (default: all 100)')
    parser.add_argument('--out', help='output directory (default: _work/backups/micro-plus-<time>, '
                                      'not tracked by git)')
    parser.add_argument('--device', help='Bluetooth address of the amp')
    parser.add_argument('--log', help='append the BLE traffic to this JSONL file')
    args = parser.parse_args()
    try:
        asyncio.run(backup(args))
    except KeyboardInterrupt:
        pass


if __name__ == '__main__':
    main()
