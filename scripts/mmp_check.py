#!/usr/bin/env python3
"""Check the Micro Plus after a firmware update (or any time): does everything ChordsPlay
uses still work? Runs against the running amp agent (mmp_web.py), compares with a
backup (mmp_backup.py), standard library only.

  mmp_check.py BACKUP_DIR              # connection, firmware, names, 3 presets, switch, audition
  mmp_check.py BACKUP_DIR --all        # all 100 presets compared with the backup (about a minute)
  mmp_check.py BACKUP_DIR --write      # also write a copy into slot 99, read it back, restore it

The amp switches briefly (to another slot and back, and an audition on slot 100), so
don't run it while playing. Nothing is written without --write; with --write only slot
99, which is put back from the backup at the end. Exit code 0 when everything passed.
"""
import argparse
import json
import sys
import time
from pathlib import Path

from mmp_restore import call

AUDITION_SLOT = 100
WRITE_SLOT = 99
QUICK_SLOTS = (1, 19, 41)


class Check(object):
    def __init__(self, agent):
        self.agent = agent
        self.failed = 0

    def report(self, ok, what, detail=''):
        print('{}  {}{}'.format('PASS' if ok else ('INFO' if ok is None else 'FAIL'), what,
                                ': ' + detail if detail else ''), flush=True)
        if ok is False:
            self.failed += 1
        return ok

    def state(self):
        return call(self.agent, '/api/micro-plus')


def wait_connected(check, timeout=40):
    '''The agent may be asleep (idle disconnect): a switch to the current slot wakes it.'''
    state = check.state()
    if not state.get('connected') and state.get('preset'):
        try:
            call(check.agent, '/api/micro-plus', {'preset': state['preset']})
        except RuntimeError:
            pass
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        state = check.state()
        if state.get('connected') and state.get('presets'):
            return state
        time.sleep(1)
    return state


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('backup', help='directory written by mmp_backup.py (the state to compare with)')
    parser.add_argument('--agent', default='http://localhost:8081')
    parser.add_argument('--all', action='store_true', help='compare all 100 presets, not only 3')
    parser.add_argument('--write', action='store_true', help='also test writing (slot 99, restored at the end)')
    args = parser.parse_args()
    backup = Path(args.backup)
    manifest = json.load(open(backup / 'manifest.json', encoding='utf-8'))
    entries = {p['slot']: p for p in manifest['presets']}
    stored = {slot: json.load(open(backup / e['file'], encoding='utf-8')) for slot, e in entries.items()}
    check = Check(args.agent)

    # 1. connection
    try:
        state = wait_connected(check)
    except RuntimeError as e:
        check.report(False, 'amp agent', str(e))
        return 1
    if not check.report(bool(state.get('connected')), 'Mustang connected',
                        state.get('error') or state.get('phase') or ''):
        return 1
    original = state['preset']

    # 2. firmware
    before = manifest.get('firmware') or manifest.get('firmware_reply_6')
    now = state.get('firmware')
    if now is None:
        check.report(None, 'firmware', 'the agent does not report it (update maneline)')
    else:
        check.report(None if now != before else True, 'firmware',
                     now if now == before else '{} (backup: {}) - CHANGED, check the rest carefully'.format(now, before))

    # 3. names (the amp's list; json_name is the preset's own, which slot 100 may differ in)
    names = [p['name'].strip() for p in state['presets']]
    want = [(entries[s].get('json_name') or entries[s]['name']).strip() for s in range(1, 101)]
    differ = [s for s in range(1, 101) if s <= len(names) and names[s - 1] != want[s - 1] and s != AUDITION_SLOT]
    check.report(len(names) == 100 and not differ, '100 preset names as in the backup',
                 '{} names'.format(len(names)) if len(names) != 100 else
                 ', '.join('{} "{}" (backup "{}")'.format(s, names[s - 1], want[s - 1]) for s in differ[:5]))

    # 4. presets read without switching (presetDataRequest), compared with the backup
    slots = range(1, 101) if args.all else QUICK_SLOTS
    differ = []
    for slot in slots:
        try:
            if call(args.agent, '/api/micro-plus/preset?slot={}'.format(slot)) != stored[slot]:
                differ.append(slot)
        except RuntimeError as e:
            differ.append('{} ({})'.format(slot, e))
    check.report(not differ, '{} presets read, same as the backup'.format(len(slots)),
                 'differ: ' + ', '.join(map(str, differ[:10])) if differ else '')

    # 5. switch and back
    other = 41 if original != 41 else 19
    try:
        ok = call(args.agent, '/api/micro-plus', {'preset': other}).get('preset') == other
        ok = ok and call(args.agent, '/api/micro-plus', {'preset': original}).get('preset') == original
        check.report(ok, 'switch to slot {} and back to {}'.format(other, original))
    except RuntimeError as e:
        check.report(False, 'switch', str(e))

    # 6. audition on the reserved slot, then back
    try:
        doc = json.loads(json.dumps(stored[19]))
        doc['info']['displayName'] = 'Check audition'
        s = call(args.agent, '/api/micro-plus/audition', {'preset': doc, 'slot': AUDITION_SLOT})
        ok = (s.get('audition') or {}).get('name') == 'Check audition'
        call(args.agent, '/api/micro-plus', {'preset': original})
        check.report(ok, 'audition on slot {} (tones, levels, knobs)'.format(AUDITION_SLOT))
    except RuntimeError as e:
        check.report(False, 'audition', str(e))

    # 7. writing (savePresetAsRequest), only on request; slot 99 is put back from the backup
    if args.write:
        doc = json.loads(json.dumps(stored[19]))
        doc['info']['displayName'] = 'Check write'
        try:
            back = call(args.agent, '/api/micro-plus/save', {'slot': WRITE_SLOT, 'preset': doc, 'name': 'Check write'})
            check.report(back.get('preset') == doc, 'write slot {} and read it back'.format(WRITE_SLOT))
        except RuntimeError as e:
            check.report(False, 'write slot {}'.format(WRITE_SLOT), str(e))
        try:
            entry = entries[WRITE_SLOT]
            back = call(args.agent, '/api/micro-plus/save', {'slot': WRITE_SLOT, 'preset': stored[WRITE_SLOT],
                                                             'name': entry.get('json_name') or entry['name']})
            check.report(back.get('preset') == stored[WRITE_SLOT], 'slot {} put back from the backup'.format(WRITE_SLOT))
        except RuntimeError as e:
            check.report(False, 'restore slot {}'.format(WRITE_SLOT),
                         '{} - run mmp_restore.py {} --slots {}'.format(e, backup, WRITE_SLOT))

    print('{} failed'.format(check.failed) if check.failed else 'all checks passed')
    return 1 if check.failed else 0


if __name__ == '__main__':
    sys.exit(main())
