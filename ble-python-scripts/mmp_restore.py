#!/usr/bin/env python3
"""Put presets back from a backup (mmp_backup.py) into the Micro Plus, through the
running amp agent (mmp_web.py), and check each slot by reading it back.

  mmp_restore.py BACKUP_DIR --slots 99            # one slot
  mmp_restore.py BACKUP_DIR --slots 1-100         # everything
  mmp_restore.py BACKUP_DIR --slots 1-100 --dry-run   # only compare, write nothing

Writes savePresetAsRequest (18) per slot: the backup's stored preset under its name.
A slot that already holds exactly the backup's preset is not written (no flash wear).
Standard library only.
"""
import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit

from mmp_backup import slot_list


def call(agent, path, body=None):
    parts = urlsplit(agent)
    req = urllib.request.Request(agent.rstrip('/') + path, data=None if body is None else json.dumps(body).encode(),
                                 method='GET' if body is None else 'POST', headers={
                                     'Content-Type': 'application/json',
                                     'Origin': '{}://{}'.format(parts.scheme, parts.netloc)})
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        try:
            message = json.loads(error.read()).get('error')
        except ValueError:
            message = None
        raise RuntimeError(message or 'agent error {}'.format(error.code)) from None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('backup', help='directory written by mmp_backup.py')
    parser.add_argument('--slots', required=True, help='e.g. 99 or 72-100')
    parser.add_argument('--agent', default='http://localhost:8081', help='the amp agent (mmp_web.py)')
    parser.add_argument('--dry-run', action='store_true', help='only compare the slots with the backup')
    args = parser.parse_args()
    backup = Path(args.backup)
    manifest = json.load(open(backup / 'manifest.json', encoding='utf-8'))
    entries = {p['slot']: p for p in manifest['presets']}
    failed = 0
    for slot in slot_list(args.slots):
        entry = entries.get(slot)
        if entry is None:
            print('{:3d}  not in the backup'.format(slot)); failed += 1; continue
        wanted = json.load(open(backup / entry['file'], encoding='utf-8'))
        try:
            now = call(args.agent, '/api/micro-plus/preset?slot={}'.format(slot))
            if now == wanted:
                print('{:3d}  {:18s} same as the backup'.format(slot, entry['name'])); continue
            if args.dry_run:
                print('{:3d}  {:18s} DIFFERS from the backup'.format(slot, entry['name'])); failed += 1; continue
            stored = call(args.agent, '/api/micro-plus/save', {'slot': slot, 'preset': wanted, 'name': entry['name']})
            ok = stored['preset'] == wanted
            print('{:3d}  {:18s} {}'.format(slot, entry['name'], 'restored' if ok else 'WRITTEN, BUT READS BACK DIFFERENTLY'))
            failed += not ok
        except RuntimeError as error:
            print('{:3d}  {:18s} FAILED: {}'.format(slot, entry['name'], error)); failed += 1
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
