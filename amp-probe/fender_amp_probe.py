#!/usr/bin/python3
# (not /usr/bin/env python3: Windows' py launcher would then search PATH for python3 and find
# the Microsoft Store placeholder, which only says that Python was not found)
"""Fender amp check, Python version (Windows, macOS, Linux).

Does what the web page (index.html) does and also collects what a browser can't see:
Bluetooth advertisement and manufacturer data, MTU, GATT descriptors, and the amp's
USB audio and Bluetooth audio entries as the operating system lists them (Fender
entries only). Writes a Markdown report that you check before sending it.

  python -m pip install bleak          (Windows: py -m pip install bleak)
  python fender_amp_probe.py           (Windows: py fender_amp_probe.py)
  Linux, where pip refuses system-wide installs: python3 -m venv amp-venv,
  amp-venv/bin/pip install bleak, amp-venv/bin/python fender_amp_probe.py
  python fender_amp_probe.py --out DIR (write the report into DIR)

Nothing is sent anywhere. Only the amp is written to, and only with requests that read
or, if you agree, the reversible control test. See README.md.
"""
import asyncio
import json
import os
import platform
import re
import struct
import subprocess
import sys
import time
import urllib.parse
import warnings
import webbrowser
from datetime import date, datetime
from pathlib import Path

PROBE_VERSION = '1.21'
CONFIG = {
    'email': 'joachim.banzhaf@gmail.com',  # address for sending by email; not offered while empty
    'issues': 'https://github.com/tim-littlefair/maneline/issues/new',
}


UUID = {
    'request': '820a7e34-4e0a-4f90-8520-04ebce35a3a1',
    'response': '1017adcc-dcbc-4387-a59f-2546b2ea5bb0',
    'mp': '90559580-b707-11ee-acb1-7b7e30f1af54',
    'ltx': '90559580-b707-11ee-acb1-7b7e30f12dcd',
    'gt': '82107b48-5a47-4067-bcc1-319844c8f1e8',
    'gtReversed': 'e8f1c844-9831-c1bc-6740-475a487b1082',
}
FAMILY_BY_SERVICE = {
    UUID['mp']: 'mp',
    UUID['ltx']: 'ltx',
    UUID['gt']: 'gt',
    UUID['gtReversed']: 'gt',
}
FAMILIES = {
    'gt': {
        'label': 'Mustang GT / GTX / Rumble ("gt" messages)',
        'envelope': 'gt.FenderMessage',
        'framed': False,
    },
    'ltx': {
        'label': 'Mustang LTX ("ltx" messages)',
        'envelope': 'ltx.MustangLtxToneMessage',
        'framed': True,
    },
    'mp': {
        'label': 'Mustang Micro Plus ("mp" messages)',
        'envelope': 'mp.MustangLtxAppMessage',
        'framed': True,
    },
}
STD = '-0000-1000-8000-00805f9b34fb'
DEVICE_INFO = {
    '00002a29' + STD: 'manufacturer',
    '00002a24' + STD: 'model number',
    '00002a27' + STD: 'hardware revision',
    '00002a26' + STD: 'firmware revision',
    '00002a28' + STD: 'software revision',
    '00002a50' + STD: 'PnP id',
}
NOT_READ = {'00002a25' + STD, '00002a23' + STD}  # serial number, system id
NAME_PATTERN = re.compile(r'mustang|rumble|tone ?master|fender|gtx|ltx', re.I)
FRAME_PAYLOAD, FRAME_GAP, HEARTBEAT = 117, 20, 250

SPEED = 1.0  # tests make the waits shorter


async def sleep(ms):
    await asyncio.sleep(ms * SPEED / 1000)


# ---- protobuf ------------------------------------------------------------------------
def varint(n):
    if n < 0:
        raise ValueError('negative varint')
    out = bytearray()
    while n >= 128:
        out.append((n & 127) | 128)
        n >>= 7
    out.append(n)
    return bytes(out)


def read_varint(b, o):
    v = shift = 0
    for _ in range(10):
        if o >= len(b):
            raise ValueError('truncated varint')
        x = b[o]
        o += 1
        v |= (x & 127) << shift
        shift += 7
        if x < 128:
            return v, o
    raise ValueError('varint too long')


class Field:
    __slots__ = ('no', 'wire', 'value', 'offset')

    def __init__(self, no, wire, value, offset):
        self.no, self.wire, self.value, self.offset = no, wire, value, offset


def parse_fields(b, base=0):
    out, o = [], 0
    while o < len(b):
        tag, o = read_varint(b, o)
        no, wire = tag >> 3, tag & 7
        if no == 0:
            raise ValueError('field number 0')
        if wire == 0:
            v, o = read_varint(b, o)
            out.append(Field(no, 0, v, None))
        elif wire == 2:
            n, o = read_varint(b, o)
            if o + n > len(b):
                raise ValueError('truncated field')
            out.append(Field(no, 2, bytes(b[o : o + n]), base + o))
            o += n
        elif wire in (1, 5):
            n = 4 if wire == 5 else 8
            if o + n > len(b):
                raise ValueError('truncated field')
            out.append(Field(no, wire, bytes(b[o : o + n]), base + o))
            o += n
        else:
            raise ValueError(f'unsupported wire type {wire}')
    return out


def parses(b):
    try:
        return len(parse_fields(b)) > 0
    except ValueError:
        return False


def rnd(x):
    return float(f'{x:.7g}') if x == x and abs(x) != float('inf') else x


def lz4_block(src, limit=1 << 21):
    out, i = bytearray(), 0
    while i < len(src):
        token = src[i]
        i += 1
        lit = token >> 4
        if lit == 15:
            while True:
                b = src[i]
                i += 1
                lit += b
                if b != 255:
                    break
        out += src[i : i + lit]
        i += lit
        if i >= len(src):
            break
        off = src[i] | (src[i + 1] << 8)
        i += 2
        if not off or off > len(out):
            raise ValueError('bad LZ4 offset')
        ml = token & 15
        if ml == 15:
            while True:
                b = src[i]
                i += 1
                ml += b
                if b != 255:
                    break
        ml += 4
        start = len(out) - off
        for k in range(ml):
            out.append(out[start + k])
        if len(out) > limit:
            raise ValueError('LZ4 output too large')
    return bytes(out)


def resolve(name, family):
    if not name:
        return None
    if name in MSG or name in ENUM:
        return name
    alt = family + '.' + name
    return alt if alt in MSG or alt in ENUM else None


def field_spec(type_, key):
    spec = MSG.get(type_)
    if spec is None:
        raise ValueError('unknown message type ' + type_)
    idx = 0 if isinstance(key, int) else 1
    return next((f for f in spec if f[idx] == key), None)


def has_field(type_, name):
    return type_ in MSG and field_spec(type_, name) is not None


def enum_number(type_, v):
    if isinstance(v, int) and not isinstance(v, bool):
        return v
    for k, n in ENUM.get(type_, {}).items():
        if n == v:
            return int(k)
    raise ValueError(f'{type_} has no value {v}')


def encode_field(no, t, tn, v, family):
    if t == 11:
        body = encode_msg(tn, v, family)
        return varint(no << 3 | 2) + varint(len(body)) + body
    if t in (9, 12):
        b = v.encode() if t == 9 else bytes(v)
        return varint(no << 3 | 2) + varint(len(b)) + b
    if t == 2:
        return varint(no << 3 | 5) + struct.pack('<f', v)
    if t == 1:
        return varint(no << 3 | 1) + struct.pack('<d', v)
    if t == 8:
        return varint(no << 3) + varint(1 if v else 0)
    if t == 14:
        return varint(no << 3) + varint(enum_number(tn, v))
    if t in (17, 18):
        return varint(no << 3) + varint(-2 * v - 1 if v < 0 else 2 * v)
    if t in (7, 15):
        return varint(no << 3 | 5) + struct.pack('<I', v & 0xFFFFFFFF)
    return varint(no << 3) + varint(v)


def is_default(t, tn, v):
    if t == 11:
        return False
    if t == 14:
        return enum_number(tn, v) == 0
    return v in (0, False, '', None, b'')


def encode_msg(type_, obj, family=None):
    family = family or type_.split('.')[0]
    out = b''
    for key, val in (obj or {}).items():
        f = field_spec(type_, key)
        if f is None:
            raise ValueError(f'{type_} has no field {key}')
        no, _, t, tn, rep, oneof = f
        rt = resolve(tn, family)
        for v in val if rep else [val]:
            if not rep and oneof < 0 and is_default(t, rt, v):
                continue
            out += encode_field(no, t, rt, v, family)
    return out


# ---- privacy -------------------------------------------------------------------------
IDS = 'preset and cloud ids (replaced by id-1, id-2, ...)'
PRIVATE = {
    'gt.SystemStatus.address': ('remove', "the amp's Bluetooth address"),
    'gt.NetworkStatus.connectedSSID': ('remove', 'the Wi-Fi network name'),
    'gt.NetworkStatus.connectedIP': ('remove', 'the IP address'),
    'gt.SSIDEntry.ssid': ('remove', 'names of nearby Wi-Fi networks'),
    'gt.WifiConnectToSSIDMessage.ssidToConnect': ('remove', 'the Wi-Fi network name'),
    'gt.WifiConnectToSSIDMessage.password': ('remove', 'a Wi-Fi password'),
    'gt.ConfirmPasskey.passkey': ('remove', 'a pairing passkey'),
    'gt.PresetInfoMessage.author': ('author', 'preset authors (other than Fender)'),
    'gt.PresetInfoMessage.preset_id': ('pseudo', IDS),
    'gt.PresetInfoMessage.source_id': ('pseudo', IDS),
    'gt.CurrentLoadedPresetIdStatus.currentLoadedPresetId': ('pseudo', IDS),
    'gt.LoadPreset.presetId': ('pseudo', IDS),
    'gt.SetlistStatus.Ids': ('remove', 'the preset ids in setlists'),
}
NETS = [
    (
        re.compile(r'\b[0-9A-Fa-f]{2}([:-])[0-9A-Fa-f]{2}(?:\1[0-9A-Fa-f]{2}){4}\b'),
        'a hardware address (MAC)',
    ),
    (
        re.compile(r'(?<![0-9A-Fa-f])[0-9A-Fa-f]{2}(?:_[0-9A-Fa-f]{2}){5}(?![0-9A-Fa-f])'),
        'a hardware address (MAC)',
    ),  # BlueZ paths: dev_AA_BB_...
    # Windows device ids: BTHENUM\DEV_123456789ABC, BTHLE\DEV_...
    (re.compile(r'(?i)\bDEV_[0-9a-f]{12}\b'), 'a hardware address (MAC)'),
    (re.compile(r'[\w.+-]+@[\w-]+(?:\.[\w-]+)+'), 'an email address'),
    (
        re.compile(r'\b(?:25[0-5]|2[0-4]\d|1?\d?\d)(?:\.(?:25[0-5]|2[0-4]\d|1?\d?\d)){3}\b'),
        'an IP address',
    ),
]
JSON_REMOVE = re.compile(
    r'^(author|authorName|user|userName|username|email|owner|account|createdBy|created_by)$', re.I
)
JSON_PSEUDO = re.compile(
    r'^(preset_id|presetId|source_id|sourceId|cloud_id|cloudId|uuid|user_id|userId)$', re.I
)
FENDER = re.compile(r'^fender\b', re.I)


class Privacy:
    def __init__(self):
        self.reasons, self.ids = [], {}

    def reason(self, text):
        if text not in self.reasons:
            self.reasons.append(text)

    def pseudo(self, s):
        return self.ids.setdefault(s, f'id-{len(self.ids) + 1}')

    def scrub(self, s, key=''):
        t = s
        for regex, label in NETS:
            if label == 'an IP address' and re.search('version', key or '', re.I):
                continue
            r = regex.sub('[removed]', t)
            if r != t:
                self.reason(label)
                t = r
        return t

    def json(self, value):
        changed = False

        def walk(v, key):
            nonlocal changed
            if isinstance(v, list):
                return [walk(x, key) for x in v]
            if isinstance(v, dict):
                o = {}
                for k, x in v.items():
                    if isinstance(x, str) and x and JSON_REMOVE.match(k) and not FENDER.match(x):
                        o[k] = '[removed]'
                        self.reason('preset authors (other than Fender)')
                        changed = True
                    elif isinstance(x, str) and x and JSON_PSEUDO.match(k):
                        o[k] = self.pseudo(x)
                        self.reason(IDS)
                        changed = True
                    else:
                        o[k] = walk(x, k)
                return o
            if isinstance(v, str):
                t = self.scrub(v, key)
                changed = changed or t != v
                return t
            return v

        return walk(value, ''), changed


# ---- decoder -------------------------------------------------------------------------
class Hidden(dict):
    """A dict whose 'hex' stays out of the report text."""

    def __init__(self, data, **kw):
        super().__init__(**kw)
        self.hex = data.hex()


class Ctx:
    def __init__(self, family, privacy, unknown):
        self.family, self.privacy, self.unknown, self.masks = family, privacy, unknown, []

    def mask(self, f):
        if f.offset is not None:
            self.masks.append((f.offset, f.offset + len(f.value)))


def decode_string(ctx, key, f):
    s = f.value.decode('utf-8', 'replace')
    rule = PRIVATE.get(key)
    if rule and s:
        kind, label = rule
        if kind == 'author' and FENDER.match(s):
            return s
        ctx.privacy.reason(label)
        ctx.mask(f)
        return ctx.privacy.pseudo(s) if kind == 'pseudo' else '[removed]'
    if re.match(r'^\s*[{\[]', s):
        try:
            v, changed = ctx.privacy.json(json.loads(s))
            if changed:
                ctx.mask(f)
            return {'json': v}
        except ValueError:
            pass
    t = ctx.privacy.scrub(s, key)
    if t != s:
        ctx.mask(f)
    return t


def decode_bytes(ctx, key, f):
    if re.search(r'(PacketDataMTU|FileTransferData)\.data$', key):
        ctx.mask(f)
        ctx.privacy.reason('raw pieces of transferred presets (shown joined and filtered instead)')
        return Hidden(f.value, bytes=len(f.value))
    if key.endswith('compressedPresetJson'):
        try:
            v, changed = ctx.privacy.json(json.loads(lz4_block(f.value).decode()))
            if changed:
                ctx.mask(f)
            return {'lz4Json': v, 'compressedBytes': len(f.value)}
        except (ValueError, IndexError) as e:
            return {'hex': f.value.hex(), 'error': f'not LZ4 JSON: {e}'}
    return {'hex': f.value.hex()}


WANT = {1: 1, 2: 5, 6: 1, 7: 5, 15: 5, 16: 1, 9: 2, 11: 2, 12: 2}


def convert(ctx, f, t, tn, key):
    if f.wire != WANT.get(t, 0):
        return generic(ctx, f, key)
    if t == 1:
        return rnd(struct.unpack('<d', f.value)[0])
    if t == 2:
        return rnd(struct.unpack('<f', f.value)[0])
    if t in (3, 5):
        return f.value - (1 << 64) if f.value >= 1 << 63 else f.value
    if t == 8:
        return bool(f.value)
    if t == 9:
        return decode_string(ctx, key, f)
    if t == 11:
        return decode_msg(ctx, tn, f.value, f.offset) if tn else generic(ctx, f, key)
    if t == 12:
        return decode_bytes(ctx, key, f)
    if t == 14:
        return ENUM.get(tn, {}).get(str(f.value), f.value)
    if t in (17, 18):
        return -(f.value + 1) // 2 if f.value & 1 else f.value // 2
    if t == 7:
        return struct.unpack('<I', f.value)[0]
    if t == 15:
        return struct.unpack('<i', f.value)[0]
    return f.value


def generic(ctx, f, key):
    if f.wire == 0:
        return f.value
    if f.wire == 5:
        return {
            'float': rnd(struct.unpack('<f', f.value)[0]),
            'uint32': struct.unpack('<I', f.value)[0],
        }
    if f.wire == 1:
        return {'double': rnd(struct.unpack('<d', f.value)[0])}
    try:
        inner = parse_fields(f.value, f.offset)
        if inner and all(x.no < 4096 for x in inner):
            o = {}
            for x in inner:
                k, v = f'#{x.no}', generic(ctx, x, f'{key}.#{x.no}')
                o[k] = (o[k] if isinstance(o[k], list) else [o[k]]) + [v] if k in o else v
            return o
    except ValueError:
        pass
    try:
        s = f.value.decode('utf-8')
        if all(c.isprintable() or c in '\t\r\n' for c in s):
            return decode_string(ctx, key, f)
    except UnicodeDecodeError:
        pass
    return {'hex': f.value.hex()}


def decode_msg(ctx, type_, b, base=0):
    out = {}
    for f in parse_fields(b, base or 0):
        spec = field_spec(type_, f.no)
        if spec is None:
            ctx.unknown.setdefault(type_, set()).add(f.no)
            k, v = f'#{f.no}', generic(ctx, f, f'{type_}.#{f.no}')
            out[k] = (out[k] if isinstance(out[k], list) else [out[k]]) + [v] if k in out else v
            continue
        _, name, t, tn, rep = spec[:5]
        v = convert(ctx, f, t, resolve(tn, ctx.family), f'{type_}.{name}')
        if rep:
            out.setdefault(name, []).append(v)
        else:
            out[name] = v
    return out


def decode_message(family, b, privacy, unknown):
    ctx = Ctx(family, privacy, unknown)
    try:
        v = decode_msg(ctx, FAMILIES[family]['envelope'], b)
        response_type = None
        if family == 'gt':
            keys = list(v)
            name, value = '+'.join(keys) or '(empty)', v[keys[0]] if keys else None
        elif 'ampStatus' in v:
            st = v['ampStatus']
            response_type = st.get('responseType')
            keys = [k for k in st if k != 'responseType']
            name = keys[0] if keys else 'ampStatus'
            value = st[keys[0]] if keys else st
        elif 'toneRequest' in v:
            k = next(iter(v['toneRequest']))
            name, value = 'request:' + k, v['toneRequest'][k]
        else:
            k = next(iter(v), None)
            name, value = k or '(empty)', v.get(k)
        return {'name': name, 'value': value, 'responseType': response_type, 'masks': ctx.masks}
    except ValueError as e:
        return {
            'name': '(undecodable)',
            'value': {'hex': bytes(b).hex()},
            'masks': [],
            'error': str(e),
        }


def to_frames(payload):
    chunks = [payload[i : i + FRAME_PAYLOAD] for i in range(0, len(payload), FRAME_PAYLOAD)] or [
        b''
    ]
    return [
        bytes((0x35 if n == len(chunks) - 1 else (0x33 if n == 0 else 0x34), 0, len(c))) + c
        for n, c in enumerate(chunks)
    ]


# ---- session -------------------------------------------------------------------------
class Entry:
    def __init__(self, t, direction, raw, label=None):
        self.t, self.dir, self.raw, self.label, self.mask, self.note = (
            t,
            direction,
            bytes(raw),
            label,
            [],
            None,
        )


class Message:
    def __init__(self, t, raw, pieces):
        self.t, self.raw, self.pieces = t, raw, pieces
        self.name = self.value = self.response_type = self.error = None


class Session:
    def __init__(self, family, write):
        self.family, self.write = family, write
        self.t0 = time.monotonic()
        self.log, self.messages, self.waiters, self.events = [], [], [], []
        self.privacy, self.unknown = Privacy(), {}
        self.assembly, self.unframed, self.dropped = None, 0, 0
        self.twice, self.last_rx = 0, None  # notifications that arrived twice in a row
        self.lock = asyncio.Lock()
        self.heartbeat_task = None

    def now(self):
        return round((time.monotonic() - self.t0) * 1000)

    def event(self, text):
        self.events.append((self.now(), text))

    def framed(self):
        return FAMILIES.get(self.family, {}).get('framed', False)

    def on_data(self, data):
        entry = Entry(self.now(), 'rx', data)
        self.log.append(entry)
        # the same bytes again within 30 ms: a second program on this computer receives
        # the amp's replies too (BlueZ then delivers each one twice); counted for the report
        if self.last_rx and self.last_rx.raw == entry.raw and entry.t - self.last_rx.t < 30:
            self.twice += 1
        self.last_rx = entry
        if self.family not in FAMILIES:
            return
        if self.framed():
            return self.feed_frame(entry)
        piece = (entry, 0, 0, len(entry.raw))
        if self.assembly:
            prev, self.assembly = self.assembly, None
            joined = prev[0] + entry.raw
            pieces = prev[1] + [(entry, len(prev[0]), 0, len(entry.raw))]
            if parses(joined):
                return self.complete(joined, pieces)
            if not parses(entry.raw) and len(joined) < 65536:
                self.assembly = (joined, pieces)
                return
        if parses(entry.raw):
            return self.complete(entry.raw, [piece])
        self.assembly = (entry.raw, [piece])

    def feed_frame(self, entry):
        d = entry.raw
        if not (len(d) >= 3 and d[0] in (0x33, 0x34, 0x35) and d[1] == 0 and d[2] == len(d) - 3):
            if parses(d):
                self.unframed += 1
                return self.complete(d, [(entry, 0, 0, len(d))])
            self.dropped += 1
            entry.note = 'not a valid frame'
            self.assembly = None
            return
        if d[0] == 0x33 or (d[0] == 0x35 and not self.assembly):
            self.assembly = (b'', [])
        elif not self.assembly:
            self.dropped += 1
            entry.note = 'continuation without start'
            return
        data, pieces = self.assembly
        pieces = pieces + [(entry, len(data), 3, len(d) - 3)]
        data = data + d[3:]
        self.assembly = (data, pieces)
        if d[0] == 0x35:
            self.assembly = None
            self.complete(data, pieces)

    def complete(self, raw, pieces):
        m = Message(pieces[-1][0].t, raw, pieces)
        self.decode_into(m)
        self.messages.append(m)
        for w in list(self.waiters):
            pred, fut = w
            if not fut.done() and pred(m):
                fut.set_result(m)
                self.waiters.remove(w)

    def decode_into(self, m):
        d = decode_message(self.family, m.raw, self.privacy, self.unknown)
        m.name, m.value, m.response_type, m.error = (
            d['name'],
            d['value'],
            d.get('responseType'),
            d.get('error'),
        )
        for p in m.pieces:
            p[0].mask = []
        for s, e in d['masks']:
            for entry, start, raw_start, length in m.pieces:
                a, b = max(s, start), min(e, start + length)
                if a < b:
                    entry.mask.append((raw_start + a - start, raw_start + b - start))

    def redecode(self, family):
        self.family, self.unknown = family, {}
        for m in self.messages:
            self.decode_into(m)

    async def send(self, payload, label=None):
        frames = to_frames(payload) if self.framed() else [payload]
        async with self.lock:
            for i, frame in enumerate(frames):
                self.log.append(Entry(self.now(), 'tx', frame, label))
                await self.write(frame)
                if i < len(frames) - 1:
                    await sleep(FRAME_GAP)

    def since(self, mark):
        return self.messages[mark:]

    async def wait_for(self, pred, ms, mark=0):
        for m in self.messages[mark:]:
            if pred(m):
                return m
        fut = asyncio.get_running_loop().create_future()
        w = (pred, fut)
        self.waiters.append(w)
        try:
            return await asyncio.wait_for(fut, ms * SPEED / 1000)
        except asyncio.TimeoutError:
            return None
        finally:
            if w in self.waiters:
                self.waiters.remove(w)

    def start_heartbeat(self, payload):
        self.stop_heartbeat()

        async def beat():
            while True:
                await sleep(HEARTBEAT)
                try:
                    await self.send(payload, 'heartbeat')
                except Exception:  # the connection may be gone; the report shows that
                    pass

        self.heartbeat_task = asyncio.ensure_future(beat())

    def stop_heartbeat(self):
        if self.heartbeat_task:
            self.heartbeat_task.cancel()
        self.heartbeat_task = None


class Results:
    def __init__(self, on_change=None):
        self.checks, self.data, self.on_change = [], {}, on_change or (lambda c: None)

    def check(self, id_, title, status, detail=''):
        c = {'id': id_, 'title': title, 'status': status, 'detail': detail}
        for i, x in enumerate(self.checks):
            if x['id'] == id_:
                self.checks[i] = c
                break
        else:
            self.checks.append(c)
        self.on_change(c)
        return c


def summarize(msgs):
    counts = {}
    for m in msgs:
        counts[m.name] = counts.get(m.name, 0) + 1
    return ', '.join(f'{k} ×{n}' if n > 1 else k for k, n in counts.items()) or 'nothing'


def by(msgs, name):
    return [m for m in msgs if m.name == name]


def last_of(msgs, name):
    found = by(msgs, name)
    return found[-1] if found else None


def near(a, b):
    return abs(a - b) < 0.011 + abs(b) * 1e-4


def val(m, key, default=None):
    v = m.value if m else None
    return v.get(key, default) if isinstance(v, dict) else default


# ---- GT family -----------------------------------------------------------------------
def gt_msg(name, body=None):
    return encode_msg('gt.FenderMessage', {name: body or {}}, 'gt')


def query_body(type_):
    return {k: True for k in ('request', 'sync', 'dummyField') if has_field(type_, k)}


def gt_type(name):
    return resolve(field_spec('gt.FenderMessage', name)[3], 'gt')


GT_EXTRA_READS = [
    ('btStatusRequest', 'Bluetooth state (913)'),
    ('globalEQSync', 'global EQ (126)'),
    ('globalFXAssignSync', 'global effect assignment (1038)'),
    ('globalFXAssignSyncMgt7', 'global effect assignment, other models (1050)'),
    ('bpmStatusRequest', 'tempo (445)'),
    ('globalBpmModeStatusRequest', 'global tempo mode (444)'),
    ('setlistManagerSync', 'setlists (805)'),
]


def packet_end(kind):
    return lambda m: m.name == 'packetEnd' and val(m, 'packetType') == kind


async def gt_current(s):
    mark = len(s.messages)
    await s.send(gt_msg('syncCurrentPresetToTone'), 'syncCurrentPresetToTone')
    end = await s.wait_for(packet_end('SYNC_CURRRENT_PRESET_TONE'), 8000, mark)
    got = s.since(mark)
    info, edited = last_of(got, 'presetInfoMessage'), last_of(got, 'presetEditedStatus')
    nodes = [n for m in by(got, 'nodeStatus') for n in (val(m, 'nodeStatus') or [])]
    return {
        'end': end,
        'got': got,
        'slot': val(info, 'slotNumber', 0) if info else None,
        'name': val(info, 'displayName'),
        'edited': bool(val(edited, 'presetEdited')) if edited else None,
        'nodes': nodes,
        'params': [m.value for m in by(got, 'dspUnitParameterStatus')],
    }


def yes_no(v):
    return '?' if v is None else ('yes' if v else 'no')


async def gt_read(s, r):
    mark = len(s.messages)
    await sleep(2500)
    r.check('listen', 'What the amp sends by itself (2.5 s)', 'info', summarize(s.since(mark)))

    mark = len(s.messages)
    await s.send(gt_msg('toneSync'), 'toneSync')
    end = await s.wait_for(packet_end('TONE_SYNC'), 20000, mark)
    got = s.since(mark)
    sys_ = last_of(got, 'systemStatus')
    presets = [m.value for m in by(got, 'presetInfoMessage')]
    r.data['system'] = sys_.value if sys_ else None
    r.data['presets'] = presets
    r.check(
        'toneSync',
        'Session start (901): firmware, model, preset list',
        'ok' if end and sys_ else ('partial' if got else 'noanswer'),
        (
            f"firmware {val(sys_, 'firmwareVersion', '?')}, product {val(sys_, 'productId', '?')}, "
            f"{len(presets)} presets. "
            if sys_
            else ''
        )
        + f'Replies: {summarize(got)}',
    )

    cur = await gt_current(s)
    r.data['current'] = cur
    r.check(
        'current',
        'Current preset (920): signal chain and knob values',
        'ok' if cur['end'] else ('partial' if cur['got'] else 'noanswer'),
        f"slot {cur['slot'] if cur['slot'] is not None else '?'} \"{cur['name'] or '?'}\", "
        f"{len(cur['nodes'])} blocks, {len(cur['params'])} values, unsaved changes: {yes_no(cur['edited'])}. "
        f"Replies: {summarize(cur['got'])}",
    )

    for name, title in GT_EXTRA_READS:
        if not has_field('gt.FenderMessage', name):
            continue
        mark = len(s.messages)
        await s.send(gt_msg(name, query_body(gt_type(name))), name)
        await sleep(1500)
        got = [m for m in s.since(mark) if m.name != 'networkStatus']
        r.check('x-' + name, 'Ask for ' + title, 'ok' if got else 'noanswer', summarize(got))

    mark = len(s.messages)
    await s.send(gt_msg('loopbackTest', {'data': 'maneline-probe'}), 'loopbackTest')
    await sleep(1500)
    got = [m for m in s.since(mark) if m.name != 'networkStatus']
    r.check('x-loopback', 'Loopback test (503)', 'ok' if got else 'noanswer', summarize(got))

    p = next((x for x in cur['params'] if isinstance(x.get('floatParameter'), float)), None)
    if p:
        mark = len(s.messages)
        await s.send(
            gt_msg('getDspUnitParameter', {'nodeId': p['nodeId'], 'parameterId': p['parameterId']}),
            'getDspUnitParameter',
        )
        a = await s.wait_for(
            lambda m: m.name == 'dspUnitParameterStatus'
            and val(m, 'parameterId') == p['parameterId'],
            2500,
            mark,
        )
        r.check(
            'x-getparam',
            f"Read one knob (106): {p['nodeId']} {p['parameterId']}",
            'ok' if a else 'noanswer',
            (
                f"value {val(a, 'floatParameter')} (920 said {p['floatParameter']})"
                if a
                else summarize(s.since(mark))
            ),
        )

    if cur['slot'] is not None and cur['edited'] is False:
        mark = len(s.messages)
        await s.send(gt_msg('retrievePreset', {'slot': cur['slot']}), 'retrievePreset')
        await s.wait_for(lambda m: m.name in ('packetStopMTU', 'presetJSONMessage'), 6000, mark)
        await sleep(500)
        got = [m for m in s.since(mark) if m.name != 'networkStatus']
        r.data['transfer'] = assemble_transfer(got)
        r.check(
            'x-retrieve',
            f"Preset data of slot {cur['slot']} (424)",
            'ok' if got else 'noanswer',
            summarize(got) + (f"; {r.data['transfer']['summary']}" if r.data['transfer'] else ''),
        )
    else:
        r.check(
            'x-retrieve',
            'Preset data (424)',
            'skipped',
            'current preset has unsaved changes' if cur['edited'] else 'current preset unknown',
        )


def assemble_transfer(msgs):
    start, data, js = (
        last_of(msgs, 'packetStartMTU'),
        by(msgs, 'packetDataMTU'),
        last_of(msgs, 'presetJSONMessage'),
    )
    if js:
        return {'summary': 'preset as JSON (904)', 'json': val(js, 'data')}
    if not start and not data:
        return None
    parts = sorted((m.value for m in data), key=lambda p: p.get('seqNumber', 0))
    b = b''.join(bytes.fromhex(getattr(p.get('data'), 'hex', '') or '') for p in parts)
    out = {
        'summary': f"{len(parts)} chunks, {len(b)} bytes (start said {val(start, 'numBytes', '?')})"
    }

    def try_json(x):
        return Privacy().json(json.loads(x.decode()))[0]

    try:
        out['json'] = try_json(b)
        out['summary'] += ', plain JSON'
    except (ValueError, UnicodeDecodeError):
        try:
            out['json'] = try_json(lz4_block(b))
            out['summary'] += ', LZ4-compressed JSON'
        except (ValueError, UnicodeDecodeError, IndexError):
            out['summary'] += ', format unknown'
            out['head'] = b[:16].hex()
    return out


def pick_target(items, current):
    ok = [x for x in items if x['slot'] != current and not x['empty']]
    above = [x for x in ok if x['slot'] > current]
    return (above or ok or [{'slot': None}])[0]['slot']


def pick_param(params):
    floats = [
        p
        for p in params
        if isinstance(p['value'], (int, float)) and not isinstance(p['value'], bool)
    ]
    for cond in (
        lambda p: p['parameterId'] == 'gain' and 0 <= p['value'] <= 1,
        lambda p: p['parameterId'] == 'gain',
        lambda p: p['parameterId'] == 'volume',
        lambda p: True,
    ):
        found = [p for p in floats if cond(p)]
        if found:
            return found[0]
    return None


def nudge(v):
    if 0 <= v <= 1:
        return rnd(v + 0.1 if v <= 0.85 else v - 0.1)
    return rnd(v + (1 if v <= 9 else -1))


async def gt_control(s, r):
    cur = r.data.get('current')
    if not cur or cur['slot'] is None:
        return r.check('c-switch', 'Switch preset and back', 'skipped', 'current preset unknown')
    if cur['edited'] is not False:
        return r.check(
            'c-switch',
            'Switch preset and back',
            'skipped',
            'the current preset may have unsaved changes; not touched',
        )
    items = [
        {'slot': p.get('slotNumber', 0), 'empty': bool(p.get('isEmpty'))}
        for p in r.data.get('presets') or []
    ]
    target = pick_target(items, cur['slot'])
    if target is None:
        return r.check('c-switch', 'Switch preset and back', 'skipped', 'no other preset found')

    def loaded(slot):
        return lambda m: (
            (m.name == 'presetInfoMessage' and val(m, 'slotNumber', 0) == slot)
            or (m.name == 'packetEnd' and val(m, 'packetType') == 'LOAD_PRESET_IF_VALID')
        )

    async def load(slot):
        mark = len(s.messages)
        await s.send(gt_msg('loadPresetIfValid', {'slot': slot}), 'loadPresetIfValid')
        hit = await s.wait_for(loaded(slot), 5000, mark)
        await sleep(400)
        return hit, s.since(mark)

    a, there = await load(target)
    await sleep(2000)
    b, back = await load(cur['slot'])
    check = await gt_current(s)
    r.check(
        'c-switch',
        f"Switch to slot {target} and back to {cur['slot']} (407)",
        'ok' if check['slot'] == cur['slot'] and a and b else ('partial' if a or b else 'failed'),
        f"there: {summarize(there)}; back: {summarize(back)}; now on slot {check['slot']}",
    )
    if check['slot'] != cur['slot']:
        return r.check(
            'c-knob', 'Turn a knob and back', 'skipped', 'not back on the original preset'
        )

    p = pick_param(
        [
            {
                'nodeId': x.get('nodeId'),
                'parameterId': x.get('parameterId'),
                'value': x.get('floatParameter'),
            }
            for x in check['params']
        ]
    )
    if not p:
        return r.check('c-knob', 'Turn a knob and back', 'skipped', 'no knob value found')
    v2 = nudge(p['value'])

    async def read():
        mark = len(s.messages)
        await s.send(
            gt_msg('getDspUnitParameter', {'nodeId': p['nodeId'], 'parameterId': p['parameterId']}),
            'getDspUnitParameter',
        )
        m = await s.wait_for(
            lambda x: x.name == 'dspUnitParameterStatus'
            and val(x, 'parameterId') == p['parameterId']
            and val(x, 'nodeId') == p['nodeId'],
            2500,
            mark,
        )
        return val(m, 'floatParameter') if m else None

    async def set_(v):
        mark = len(s.messages)
        await s.send(
            gt_msg(
                'setDspUnitParameter',
                {'nodeId': p['nodeId'], 'parameterId': p['parameterId'], 'floatParameter': v},
            ),
            'setDspUnitParameter',
        )
        await sleep(600)
        return s.since(mark)

    r1 = await set_(v2)
    after = await read()
    await sleep(2000)
    r2 = await set_(p['value'])
    restored = await read()
    await load(cur['slot'])  # drops the edit flag
    fin = await gt_current(s)
    ok_set = after is not None and near(after, v2)
    ok_back = restored is not None and near(restored, p['value'])
    r.check(
        'c-knob',
        f"Set {p['nodeId']} {p['parameterId']} {p['value']} → {v2} and back (102)",
        (
            'ok'
            if ok_set and ok_back and fin['edited'] is False
            else ('partial' if ok_set or ok_back else 'failed')
        ),
        f'read back {after} then {restored}; replies to set: {summarize(r1)} / {summarize(r2)}; '
        f"after reloading the preset: unsaved changes {yes_no(fin['edited'])}",
    )


# ---- Micro Plus / LTX ----------------------------------------------------------------
PROVEN_EMPTY = {
    'firmwareVersionRequest',
    'presetNamesRequest',
    'usbAudioGainInfoRequest',
    'eqRequest',
    'heartbeat',
}
MP_READS = [
    'currentPresetRequest',
    'usbAudioGainInfoRequest',
    'eqRequest',
    'productIdentificationRequest',
    'connectionStatusRequest',
    'modifiableParameterRequest',
    'tapTempoRequest',
    'attenuatorRequest',
    'muteDetectEnableRequest',
    'inputPadRequest',
]
LTX_READS = [
    'currentPresetRequest',
    'productIdRequest',
    'usbAndLineOutGainRequest',
    'outputPowerRequest',
    'presetModeRequest',
    'ftswBrightnessRequest',
    'ftswVersionRequest',
    'connectionStatusRequest',
]


def request_type(family):
    return FAMILIES[family]['envelope'].rsplit('.', 1)[0] + '.MustangLtxToneRequest'


def tone_msg(family, name, body=None):
    return encode_msg(FAMILIES[family]['envelope'], {'toneRequest': {name: body or {}}}, family)


def request_body(family, name, extra=None):
    type_ = resolve(field_spec(request_type(family), name)[3], family)
    body = {} if name in PROVEN_EMPTY else query_body(type_)
    body.update(extra or {})
    return body


def modal(family, context):
    return encode_msg(FAMILIES[family]['envelope'], {'modalStatus': {'context': context}}, family)


def status_numbers(m):
    try:
        st = next((f for f in parse_fields(m.raw) if f.no == 2 and f.wire == 2), None)
        return [f.no for f in parse_fields(st.value)] if st else []
    except ValueError:
        return []


async def framed_read(s, r, service_family):
    mark = len(s.messages)
    await s.send(modal('mp', 'SYNC_BEGIN'), 'modal SYNC_BEGIN')
    await s.send(tone_msg('mp', 'firmwareVersionRequest'), 'firmwareVersionRequest')
    await s.send(tone_msg('mp', 'presetNamesRequest'), 'presetNamesRequest')
    names = await s.wait_for(lambda m: bool({11, 12} & set(status_numbers(m))), 6000, mark)
    family = service_family
    if names:
        family = 'mp' if 12 in status_numbers(names) else 'ltx'
    if family != s.family:
        s.redecode(family)
    r.data['family'] = family
    r.check(
        'family',
        'Which message numbers the amp uses',
        ('ok' if family == service_family else 'warn') if names else 'noanswer',
        (
            (
                f'"{family}" numbering (Bluetooth service says "{service_family}")'
                + (f'; {s.unframed} replies came without frame header' if s.unframed else '')
            )
            if names
            else f'no answer to the preset name request. Replies: {summarize(s.since(mark))}'
        ),
    )
    if not names:
        return
    s.start_heartbeat(tone_msg(family, 'heartbeat'))
    s.event(f'heartbeat every {HEARTBEAT} ms started')
    for name in MP_READS if family == 'mp' else LTX_READS:
        if not has_field(request_type(family), name):
            continue
        extra = {'requestPresetData': True} if name == 'currentPresetRequest' else {}
        await s.send(tone_msg(family, name, request_body(family, name, extra)), name)
        await sleep(150)
    await s.send(modal(family, 'SYNC_END'), 'modal SYNC_END')
    await sleep(2500)
    got = s.since(mark)
    fw, nm = last_of(got, 'firmwareVersionStatus'), last_of(got, 'presetNamesStatus')
    cur, data = last_of(got, 'currentPresetStatus'), last_of(got, 'currentPresetDataStatus')
    r.data['firmware'] = val(fw, 'version')
    pn = val(nm, 'presetName') or []
    r.data['names'] = pn if isinstance(pn, list) else [pn]
    r.data['current'] = (
        {
            'index': val(cur, 'presetIndex', 0),
            'dirty': bool(val(cur, 'isDirty')),
            'auditioning': bool(val(cur, 'isAuditioning')),
        }
        if cur
        else None
    )
    r.data['presetJson'] = (
        (val(data, 'compressedPresetJson') or {}).get('lz4Json') if data else None
    )
    r.check(
        'handshake',
        'Firmware, preset names and current preset',
        'ok' if fw and nm and cur else 'partial',
        f"firmware {r.data['firmware'] or '?'}, {len(r.data['names'])} names, current "
        f"{r.data['current']['index'] + 1 if cur else '?'}"
        f"{' (unsaved changes)' if cur and r.data['current']['dirty'] else ''}. Replies: {summarize(got)}",
    )
    if cur:
        mark = len(s.messages)
        index = r.data['current']['index']
        await s.send(
            tone_msg(
                family,
                'presetDataRequest',
                request_body(family, 'presetDataRequest', {'presetIndex': index}),
            ),
            'presetDataRequest',
        )
        st = await s.wait_for(lambda m: m.name == 'presetDataStatus', 4000, mark)
        packed = val(st, 'compressedPresetJson') or {}
        r.data['storedJson'] = packed.get('lz4Json')
        r.check(
            'stored',
            f'Stored form of preset {index + 1} (12)',
            'ok' if st else 'noanswer',
            (
                f"{packed.get('compressedBytes', '?')} compressed bytes"
                if st
                else summarize(s.since(mark))
            ),
        )
    mark = len(s.messages)
    await sleep(3000)
    r.check('listen', 'Messages during 3 s with heartbeat only', 'info', summarize(s.since(mark)))


def json_params(preset):
    nodes = ((preset or {}).get('audioGraph') or {}).get('nodes') or []
    return [
        {'nodeId': n.get('nodeId'), 'parameterId': k, 'value': v}
        for n in nodes
        for k, v in (n.get('dspUnitParameters') or {}).items()
    ]


async def framed_control(s, r):
    family, cur = r.data.get('family'), r.data.get('current')
    if not family or not cur:
        return r.check('c-switch', 'Switch preset and back', 'skipped', 'current preset unknown')
    if cur['dirty'] or cur['auditioning']:
        return r.check(
            'c-switch',
            'Switch preset and back',
            'skipped',
            'the current preset has unsaved changes; not touched',
        )
    items = [
        {'slot': i, 'empty': bool(re.match(r'^\s*empty\s*$', n, re.I))}
        for i, n in enumerate(r.data['names'])
    ]
    target = pick_target(items or [{'slot': cur['index'] + 1, 'empty': False}], cur['index'])

    async def load(index):
        mark = len(s.messages)
        await s.send(
            tone_msg(
                family,
                'loadPresetRequest',
                request_body(family, 'loadPresetRequest', {'newPresetIndex': index}),
            ),
            'loadPresetRequest',
        )
        m = await s.wait_for(
            lambda x: x.name == 'currentPresetStatus' and val(x, 'presetIndex', 0) == index,
            4000,
            mark,
        )
        await sleep(300)
        return m, s.since(mark)

    a, there = await load(target)
    await sleep(2000)
    b, back = await load(cur['index'])
    r.check(
        'c-switch',
        f"Switch to {target + 1} and back to {cur['index'] + 1} (load preset)",
        'ok' if a and b else ('partial' if a or b else 'failed'),
        f'there: {summarize(there)}; back: {summarize(back)}',
    )
    if not b:
        return r.check(
            'c-knob', 'Turn a knob and back', 'skipped', 'not back on the original preset'
        )
    params = json_params(r.data.get('presetJson'))
    p = pick_param([x for x in params if x['nodeId'] == 'amp']) or pick_param(params)
    if not p:
        return r.check(
            'c-knob', 'Turn a knob and back', 'skipped', 'no knob value found in the preset'
        )

    async def read():
        mark = len(s.messages)
        await s.send(
            tone_msg(
                family, 'parameterRequest', {'nodeId': p['nodeId'], 'parameterId': p['parameterId']}
            ),
            'parameterRequest',
        )
        m = await s.wait_for(
            lambda x: x.name == 'parameterStatus' and val(x, 'parameterId') == p['parameterId'],
            2500,
            mark,
        )
        return val(m, 'floatParameter') if m else None

    async def set_(v):
        mark = len(s.messages)
        await s.send(
            tone_msg(
                family,
                'setParameterRequest',
                {'nodeId': p['nodeId'], 'parameterId': p['parameterId'], 'floatParameter': v},
            ),
            'setParameterRequest',
        )
        await sleep(600)
        return s.since(mark)

    before = await read()
    v2 = nudge(p['value'])
    r1 = await set_(v2)
    after = await read()
    await sleep(2000)
    r2 = await set_(p['value'])
    restored = await read()
    c, _ = await load(cur['index'])  # drops the unsaved-change flag
    dirty = bool(val(c, 'isDirty')) if c else None
    ok_set = after is not None and near(after, v2)
    ok_back = restored is not None and near(restored, p['value'])
    r.check(
        'c-knob',
        f"Set {p['nodeId']} {p['parameterId']} {p['value']} → {v2} and back (set parameter)",
        (
            'ok'
            if ok_set and ok_back and dirty is False
            else ('partial' if ok_set or ok_back else 'failed')
        ),
        f'read before {before}, after {after}, restored {restored}; replies to set: {summarize(r1)} / '
        f'{summarize(r2)}; after reload: unsaved changes {yes_no(dirty)}',
    )


# ---- report --------------------------------------------------------------------------
STATUS_TEXT = {
    'ok': 'OK',
    'partial': 'partly',
    'noanswer': 'no answer',
    'failed': 'failed',
    'skipped': 'skipped',
    'info': 'info',
    'warn': 'check',
}


def cell(v):
    return str('' if v is None else v).replace('|', '\\|').replace('\n', ' ')


def one_line(v, n=400):
    t = json.dumps(
        v, ensure_ascii=False, default=lambda o: sorted(o) if isinstance(o, set) else str(o)
    )
    return t[:n] + ' …' if len(t) > n else t


def raw_line(e):
    masked = ''.join(
        '..' if any(s <= i < t for s, t in e.mask) else f'{b:02x}' for i, b in enumerate(e.raw)
    )
    return (
        f"{'+' + format(e.t / 1000, '.3f'):>9}s {e.dir} {masked}"
        + (f'  {e.label}' if e.label else '')
        + (f'  ({e.note})' if e.note else '')
    )


def traffic_log(log, max_lines=6000):
    lines, seen, beats, skipped = [], {}, 0, 0
    for e in log:
        if e.label == 'heartbeat':
            beats += 1
            if beats > 3:
                continue
        key = e.dir + e.raw.hex()
        seen[key] = seen.get(key, 0) + 1
        if e.dir == 'rx' and seen[key] > 3:
            skipped += 1
            continue
        if len(lines) < max_lines:
            lines.append(raw_line(e))
    if beats > 3:
        lines.append(f'({beats - 3} more heartbeats not shown)')
    if skipped:
        lines.append(f'({skipped} repeated identical notifications not shown)')
    if len(lines) >= max_lines:
        lines.append(f'(log cut at {max_lines} lines)')
    return '\n'.join(lines)


def build_report(st):
    L, r, s = [], st['results'], st.get('session')
    d = r.data
    # Texts that come from the system rather than from decoded messages (error messages,
    # device names) go through the same filter; an error can name the amp's address.
    meta = Privacy()

    def clean(text):
        text = str(text)
        return meta.scrub(text, 'version' if re.search(r'firmware|version', text, re.I) else '')

    fam = st.get('family')
    L += [
        '# Fender amp check report',
        '',
        f"- Tool: amp-probe {PROBE_VERSION} ({st.get('tool', 'python script')})",
        f"- Date: {st['date']}",
        f"- Computer: {st.get('system') or 'unknown'}",
        f"- Device: \"{clean((st.get('device') or {}).get('name') or '?')}\"",
        (
            f"- Family: {fam} ({FAMILIES[fam]['label']}), from Bluetooth service {st.get('serviceUuid')}"
            if fam
            else '- Family: not a known Fender service'
        ),
    ]
    if d.get('family') and d['family'] != fam:
        L.append(f"- Message numbering seen: {d['family']}")
    L += ['', '## Results', '', '| Check | Result | Details |', '| --- | --- | --- |']
    L += [
        f"| {cell(c['title'])} | {STATUS_TEXT.get(c['status'], c['status'])} | {cell(clean(c['detail']))} |"
        for c in r.checks
    ] or ['| (nothing was checked) | | |']
    L += ['', '## From the tester', '']
    L += [f'- {k}: {v or "(no answer)"}' for k, v in (st.get('answers') or {}).items()]
    if st.get('notes'):
        L += ['', '> ' + st['notes'].replace('\n', '\n> ')]
    if st.get('advert'):
        L += ['', '## Bluetooth advertisement', '']
        L += [f'- {k}: {v}' for k, v in st['advert'].items()]
    if st.get('gatt'):
        L += [
            '',
            '## Bluetooth services',
            '',
            '| Service | Characteristic | Properties |',
            '| --- | --- | --- |',
        ]
        for sv in st['gatt']:
            if not sv['chars']:
                L.append(f"| {sv['uuid']} | | {cell(clean(sv.get('error', '')))} |")
            for c in sv['chars']:
                desc = (
                    f"; descriptors {', '.join(c['descriptors'])}" if c.get('descriptors') else ''
                )
                L.append(f"| {sv['uuid']} | {c['uuid']} | {', '.join(c['props'])}{desc} |")
    if st.get('deviceInfo'):
        L += ['', '## Device information service', ''] + [
            f'- {k}: {v}' for k, v in st['deviceInfo'].items()
        ]
    if st.get('extra'):
        L += ['', st['extra']]
    if d.get('system'):
        L += ['', '## Amp', ''] + [
            f'- {k}: {one_line(v) if isinstance(v, (dict, list)) else v}'
            for k, v in d['system'].items()
        ]
    if d.get('firmware'):
        L += ['', '## Amp', '', f"- firmware: {d['firmware']}"]
    if d.get('presets'):
        L += [
            '',
            f"## Presets ({len(d['presets'])})",
            '',
            '| Slot | Name | Factory | Empty | Made for | Author |',
            '| --- | --- | --- | --- | --- | --- |',
        ]
        for p in d['presets']:
            L.append(
                f"| {p.get('slotNumber', 0)} | {cell(p.get('displayName'))} | "
                f"{'yes' if p.get('is_factory_default') else ''} | {'yes' if p.get('isEmpty') else ''} | "
                f"{cell(p.get('product_id'))} | {cell(p.get('author'))} |"
            )
    if d.get('names'):
        L += ['', f"## Presets ({len(d['names'])})", ''] + [
            f'{i + 1}. {n}' for i, n in enumerate(d['names'])
        ]
    c = d.get('current')
    if c:
        L += ['', '## Current preset', '']
        if 'nodes' in c:
            L += [
                f"Slot {c['slot'] if c['slot'] is not None else '?'} \"{c['name'] or '?'}\", "
                f"unsaved changes: {yes_no(c['edited'])}",
                '',
                '| Block | Model | Bypassed |',
                '| --- | --- | --- |',
            ]
            L += [
                f"| {cell(n.get('nodeId'))} | {cell(n.get('fenderId'))} | {'yes' if n.get('bypass') else ''} |"
                for n in c['nodes']
            ]
            L += ['', '| Block | Knob | Value |', '| --- | --- | --- |']
            for p in c['params']:
                k = next(
                    (
                        k
                        for k in (
                            'floatParameter',
                            'stringParameter',
                            'sint32Parameter',
                            'boolParameter',
                        )
                        if k in p
                    ),
                    None,
                )
                L.append(
                    f"| {cell(p.get('nodeId'))} | {cell(p.get('parameterId'))} | "
                    f"{cell(p[k] if k else '(default)')} |"
                )
        else:
            L.append(
                f"Preset {c['index'] + 1}, unsaved changes: {yes_no(c['dirty'])}, "
                f"auditioning: {yes_no(c['auditioning'])}"
            )
    for key, title in (
        ('presetJson', 'Current preset as loaded (JSON)'),
        ('storedJson', 'Current preset as stored (JSON)'),
    ):
        if d.get(key):
            L += [
                '',
                f'### {title}',
                '',
                '```json',
                json.dumps(d[key], indent=1, ensure_ascii=False)[:20000],
                '```',
            ]
    if d.get('transfer'):
        t = d['transfer']
        L += ['', '### Preset transfer (500-502 / 904)', '', f"- {t['summary']}"]
        if t.get('head'):
            L.append(f"- first bytes: {t['head']}")
        if t.get('json'):
            L += ['', '```json', json.dumps(t['json'], indent=1, ensure_ascii=False)[:20000], '```']
    if s:
        skip = {
            'presetInfoMessage',
            'dspUnitParameterStatus',
            'nodeStatus',
            'presetNamesStatus',
            'currentPresetDataStatus',
            'presetDataStatus',
        }
        groups = {}
        for m in s.messages:
            if m.name not in skip:
                groups.setdefault(m.name, []).append(m)
        L += [
            '',
            '## Other message types received',
            '',
            'Preset names and preset data are in their own sections above.',
            '',
            '| Message | Count | First value | Last value |',
            '| --- | --- | --- | --- |',
        ]
        for name, g in groups.items():
            L.append(
                f'| {cell(name)} | {len(g)} | {cell(one_line(g[0].value, 300))} | '
                f"{cell(one_line(g[-1].value, 300)) if len(g) > 1 else ''} |"
            )
        unk = [f"{t}: {', '.join(map(str, sorted(v)))}" for t, v in s.unknown.items()]
        bad = [m for m in s.messages if m.error]
        if unk or bad or s.dropped or s.twice:
            L += ['', '## Not understood', ''] + [
                f"- fields not in the app's definitions: {u}" for u in unk
            ]
            first_tx = min((e.t for e in s.log if e.dir == 'tx'), default=None)
            for m in bad[:20]:
                # before the amp could answer anything: the end of a reply to an earlier connection
                early = first_tx is not None and m.t <= first_tx + 50
                L.append(
                    f'- could not decode ({m.error}): {m.raw.hex()[:200]}'
                    + (
                        ' (arrived before the first answer could: left over from before this connection, harmless)'
                        if early
                        else ''
                    )
                )
            if s.dropped:
                L.append(f'- {s.dropped} notifications were not valid frames')
            if s.twice:
                L.append(
                    f'- {s.twice} notifications arrived twice in a row: probably another program '
                    'on this computer received the amp\'s replies too'
                )
        if s.events:
            L += ['', '## Events', ''] + [
                f'- +{t / 1000:.3f} s: {clean(text)}' for t, text in s.events
            ]
        L += [
            '',
            '## Traffic (hex; ".." marks bytes removed for privacy)',
            '',
            '```',
            traffic_log(s.log),
            '```',
        ]
    reasons = list(
        dict.fromkeys(
            (s.privacy.reasons if s else [])
            + list(st.get('removed') or [])
            + [x + ' (in an error message or device name)' for x in meta.reasons]
        )
    )
    L += [
        '',
        '## Removed for privacy',
        '',
        '\n'.join(f'- {x}' for x in reasons) or '- nothing needed removing',
    ]
    return '\n'.join(L) + '\n'


# ---- what the operating system knows (Fender entries only) ---------------------------
DROP_KEYS = re.compile(
    r'serial|address|location|uuid|guid|vendor_id_raw|_spusb_location|ssid', re.I
)


def _filter_tree(node, privacy, keep=False):
    """Keep dict entries that mention Fender (or that are inside one); drop identifying keys."""
    if isinstance(node, dict):
        text = (
            ' '.join(str(v) for v in node.values() if isinstance(v, str))
            + ' '
            + ' '.join(map(str, node))
        )
        mine = keep or bool(re.search(r'fender|mustang|rumble|1ed8', text, re.I))
        out = {}
        for k, v in node.items():
            if DROP_KEYS.search(k):
                continue
            sub = _filter_tree(v, privacy, mine)
            if sub not in (None, {}, []):
                out[k] = sub
        return out if (mine or out) else None
    if isinstance(node, list):
        return [
            x for x in (_filter_tree(v, privacy, keep) for v in node) if x not in (None, {}, [])
        ]
    return privacy.scrub(str(node)) if keep else None


def _run(cmd, timeout=40):
    try:
        return subprocess.run(
            cmd, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=timeout
        ).stdout
    except (OSError, subprocess.SubprocessError) as e:
        return f'(could not run {cmd[0]}: {e})'


# Linux: BlueZ is the Bluetooth stack, for bleak as for Chrome. Problems seen with the
# Micro Plus (docs/MUSTANG_MICRO_PLUS_LINUX.md): the amps are dual-mode, audio on classic
# Bluetooth (BR/EDR), control on LE. While the classic link is up, or when BlueZ prefers
# it for this device (PreferredBearer bredr, or last-used after audio), a connect brings
# up classic only, which has no control service. And BlueZ writes a characteristic the amp
# never answers, which stalls the connection until it is dropped after 30 s.
LINUX_HINT = (
    'On Linux: (1) the amp\'s classic (audio) link was probably used instead of its LE control '
    'link: disconnect the amp, set its PreferredBearer to le (this program offers to) and try '
    'again; (2) if connecting hung for about 30 seconds ("Unlikely Error"), BlueZ waits for an '
    'answer the amp never sends: step 3 of the BlueZ setup in maneline\'s '
    'docs/MUSTANG_MICRO_PLUS_LINUX.md.'
)
BT_INFO_KEEP = re.compile(
    r'^\s*((BREDR\.|LE\.)?(Paired|Bonded|Connected)|Name|Class|Icon|UUID|Modalias|'
    r'PreferredBearer|ServicesResolved|AddressType):',
    re.M,
)


def is_linux():
    return sys.platform.startswith('linux')


def bluez_version():
    for cmd in (['bluetoothd', '--version'], ['bluetoothctl', '--version']):
        m = re.search(r'\d+\.\d+', _run(cmd, timeout=5))
        if m:
            return m.group(0)
    return 'unknown'


def bluez_info(address):
    """bluetoothctl's view of one device, or '' if unknown or bluetoothctl is missing."""
    out = _run(['bluetoothctl', 'info', address], timeout=10)
    return out if re.search(r'^Device ', out, re.M) else ''


def bluez_connected(address):
    return bool(re.search(r'^\s*(BREDR\.)?Connected: yes', bluez_info(address), re.M))


def bluez_path(device):
    """The device's D-Bus object path: from bleak where it says, else on adapter hci0."""
    details = getattr(device, 'details', None)
    if isinstance(details, dict) and details.get('path'):
        return details['path']
    return '/org/bluez/hci0/dev_' + device.address.replace(':', '_').upper()


def preferred_bearer(path):
    """BlueZ's PreferredBearer for the device (le, bredr, last-used, ...), None if unknown."""
    out = _run(
        [
            'busctl',
            '--system',
            'get-property',
            'org.bluez',
            path,
            'org.bluez.Device1',
            'PreferredBearer',
        ],
        timeout=10,
    )
    m = re.match(r's "([\w-]+)"', out.strip())
    return m.group(1) if m else None


def set_preferred_bearer(path, value):
    _run(
        [
            'busctl',
            '--system',
            'set-property',
            'org.bluez',
            path,
            'org.bluez.Device1',
            'PreferredBearer',
            's',
            value,
        ],
        timeout=10,
    )
    return preferred_bearer(path) == value


async def linux_prepare(device, r):
    """Free the amp's LE link for this check. Returns what to undo afterwards."""
    info, path = bluez_info(device.address), bluez_path(device)
    classic = bool(re.search(r'^\s*BREDR\.Paired: yes', info, re.M)) or (
        bool(re.search(r'^\s*Paired: yes', info, re.M)) and 'Audio Sink' in info
    )
    connected = bluez_connected(device.address)
    bearer = preferred_bearer(path) if classic else None
    change = bool(bearer) and bearer != 'le'
    undo = {'path': path, 'bearer': None, 'reconnect': False}
    if not (connected or change):
        return undo
    print('\nThis computer also knows the amp as a Bluetooth audio device. Its control service is')
    print('only on the amp\'s Bluetooth LE link; a connect would use the audio link instead.')
    print('For this check the program can:')
    if connected:
        print('  - disconnect the amp (audio to it stops; you can reconnect it at the end)')
    if change:
        print(f'  - let Bluetooth prefer LE for the amp (PreferredBearer {bearer} -> le);')
        print(f'    it is set back to {bearer} at the end')
    if not yes('Do that now?', 'please do: otherwise the check can\'t reach the amp'):
        return undo
    if connected:
        _run(['bluetoothctl', 'disconnect', device.address], timeout=15)
        undo['reconnect'] = True
    if change:
        if set_preferred_bearer(path, 'le'):
            undo['bearer'] = bearer
            # BlueZ keeps the preference permanently; say how to undo it if we stop early
            print('BlueZ keeps this setting. Should this program stop early, put it back with:')
            print(
                f'  busctl --system set-property org.bluez {path} org.bluez.Device1 '
                f'PreferredBearer s {bearer}'
            )
            r.check(
                'linux-bearer',
                'Linux: LE preferred for this check',
                'info',
                f'PreferredBearer {bearer} -> le, set back afterwards',
            )
        else:
            print('Could not change it (your user may not be allowed to); as root:')
            print(
                f'  busctl --system set-property org.bluez {path} org.bluez.Device1 '
                'PreferredBearer s le'
            )
    await asyncio.sleep(2)
    return undo


async def linux_restore(device, undo):
    if undo.get('bearer'):
        ok = set_preferred_bearer(undo['path'], undo['bearer'])
        print(
            f"PreferredBearer {'set back to ' + undo['bearer'] if ok else 'could not be set back'}."
        )
    if undo.get('reconnect'):
        if await yes_async(
            'Reconnect the amp\'s Bluetooth audio now?',
            'yes if you play audio from here to the amp',
        ):
            _run(['bluetoothctl', 'connect', device.address], timeout=30)


def linux_bluetooth(privacy):
    """The paired or known Fender devices as BlueZ lists them, address and alias left out."""
    rows = []
    for line in _run(['bluetoothctl', 'devices'], timeout=10).splitlines():
        m = re.match(r'Device (\S+) (.*)', line.strip())
        if not m or not NAME_PATTERN.search(m.group(2)):
            continue
        keep = [x.strip() for x in bluez_info(m.group(1)).splitlines() if BT_INFO_KEEP.match(x)]
        rows.append(
            f'- Bluetooth (BlueZ {bluez_version()}): '
            + privacy.scrub(m.group(2))
            + '\n```\n'
            + privacy.scrub('\n'.join(keep))
            + '\n```'
        )
    return rows


def system_devices(privacy):
    """USB, audio and Bluetooth entries of Fender devices, as the OS lists them."""
    out = []
    if sys.platform == 'darwin':
        try:
            tree = json.loads(
                _run(
                    [
                        'system_profiler',
                        'SPUSBDataType',
                        'SPAudioDataType',
                        'SPBluetoothDataType',
                        '-json',
                    ]
                )
            )
            out.append(
                '```json\n' + json.dumps(_filter_tree(tree, privacy) or {}, indent=1) + '\n```'
            )
        except ValueError:
            out.append('(system_profiler gave no data)')
    elif sys.platform.startswith('win'):
        ps = (
            "[Console]::OutputEncoding = [Text.Encoding]::UTF8; "
            "Get-PnpDevice -PresentOnly | Where-Object { $_.InstanceId -match 'VID_1ED8' -or "
            "$_.FriendlyName -match 'Fender|Mustang|Rumble' } | Select-Object Class,FriendlyName,InstanceId,Status | "
            "ConvertTo-Json"
        )
        raw = _run(['powershell', '-NoProfile', '-Command', ps])
        try:
            items = json.loads(raw or '[]')
            items = items if isinstance(items, list) else [items]
            rows = ['| Class | Name | Id | Status |', '| --- | --- | --- | --- |']
            for it in items:
                iid = '\\'.join(
                    str(it.get('InstanceId', '')).split('\\')[:2]
                )  # without the serial part
                rows.append(
                    f"| {cell(it.get('Class'))} | {cell(privacy.scrub(str(it.get('FriendlyName'))))} | "
                    f"{cell(privacy.scrub(iid))} | {cell(it.get('Status'))} |"
                )
            out.append('\n'.join(rows))
        except ValueError:
            out.append('(PowerShell gave no data)')
    else:
        rows = []
        for dev in sorted(Path('/sys/bus/usb/devices').glob('*')):
            try:
                if (dev / 'idVendor').read_text().strip() != '1ed8':
                    continue
                classes = sorted({p.read_text().strip() for p in dev.glob('*:*/bInterfaceClass')})
                product = (
                    (dev / 'product').read_text().strip() if (dev / 'product').exists() else '?'
                )
                rows.append(
                    f"- USB 1ed8:{(dev / 'idProduct').read_text().strip()} \"{product}\", "
                    f"interface classes {', '.join(classes)} (01 = audio, 03 = HID)"
                )
            except OSError:
                pass
        for card in sorted(Path('/proc/asound').glob('card*')):
            ident = (card / 'id').read_text().strip() if (card / 'id').exists() else ''
            streams = [p.read_text() for p in card.glob('stream*')]
            if re.search(r'fender|mustang|rumble', ident + ' '.join(streams), re.I):
                rows.append(
                    f'- ALSA card {ident}:\n```\n'
                    + privacy.scrub('\n'.join(streams))[:4000]
                    + '\n```'
                )
        rows += linux_bluetooth(privacy)
        out.append('\n'.join(rows) or '(no Fender USB, audio or Bluetooth device found)')
    return '## What the computer knows about the amp (USB, audio, Bluetooth)\n\n' + '\n'.join(out)


# ---- console program -----------------------------------------------------------------
def ask(prompt, default=''):
    try:
        answer = input(prompt).strip()
    except EOFError:
        answer = ''
    return answer or default


def yes(prompt, hint=''):
    """A yes/no question; Enter means no. hint says what we would like, e.g. 'please do'."""
    return ask(f"{prompt}{f' ({hint})' if hint else ''} [y/N] ").lower().startswith('y')


async def yes_async(prompt, hint=''):
    """yes() while connected."""
    answer = await ask_async(f"{prompt}{f' ({hint})' if hint else ''} [y/N] ")
    return answer.lower().startswith('y')


async def ask_async(prompt, default=''):
    """ask() while connected: the event loop keeps running, so heartbeats and replies go on."""
    return await asyncio.get_running_loop().run_in_executor(None, ask, prompt, default)


def advert_info(adv):
    info = {}
    if adv is None:
        return info
    if adv.local_name:
        info['name'] = adv.local_name
    info['RSSI'] = adv.rssi
    if adv.tx_power is not None:
        info['TX power'] = adv.tx_power
    if adv.service_uuids:
        info['services'] = ', '.join(adv.service_uuids)
    for company, data in (adv.manufacturer_data or {}).items():
        text = ''.join(chr(b) if 32 <= b < 127 else '.' for b in data)
        info[f'manufacturer data 0x{company:04x}'] = f'{data.hex()} ("{text}")'
    for uuid, data in (adv.service_data or {}).items():
        info[f'service data {uuid}'] = data.hex()
    return info


async def main_async(out_dir=None):
    try:
        from bleak import BleakClient, BleakScanner
    except ImportError:
        print(
            'This needs the "bleak" package. Install it with:\n'
            '  Windows:  py -m pip install bleak\n  macOS:    python3 -m pip install bleak\n'
            '  Linux:    python3 -m venv amp-venv && amp-venv/bin/pip install bleak\n'
            '            then run: amp-venv/bin/python fender_amp_probe.py\n'
            '            (or install your distribution\'s python3-bleak package)'
        )
        return 1
    print(f'Fender amp check {PROBE_VERSION}\n')
    print(
        'Before you start: switch the amp on, close the Fender Tone app on your phone or tablet\n'
        '(or switch their Bluetooth off), and keep the amp close to this computer.\n'
        'Nothing is sent anywhere. At the end you get a report file to read before you send it.\n'
    )
    st = {
        'results': Results(
            lambda c: print(
                f"  [{STATUS_TEXT.get(c['status'], c['status']):>9}] {c['title']}: "
                f"{c['detail'][:150]}"
            )
        ),
        'date': date.today().isoformat(),
        'tool': 'python script',
        'system': f'{platform.system()} {platform.release()}, Python {platform.python_version()}'
        + (f', BlueZ {bluez_version()}' if is_linux() else ''),
    }
    r = st['results']
    print('Looking for amps (10 seconds)…')
    found = {}

    def seen(device, adv):
        services = [u.lower() for u in (adv.service_uuids or [])]
        name = adv.local_name or device.name or ''
        if NAME_PATTERN.search(name) or any(u in FAMILY_BY_SERVICE for u in services) or show_all:
            found[device.address] = (device, adv)

    show_all = False
    async with BleakScanner(detection_callback=seen):
        await asyncio.sleep(10)
    if not found and yes(
        'No amp found. Show all Bluetooth devices?', 'please do if the amp is on and close by'
    ):
        show_all = True
        async with BleakScanner(detection_callback=seen):
            await asyncio.sleep(10)
    choices = list(found.values())
    for i, (dev, adv) in enumerate(choices, 1):
        print(f'  {i}. {adv.local_name or dev.name or "(no name)"}  (signal {adv.rssi})')
    device = adv = None
    if choices:
        n = ask(f'Which one is your amp? [1-{len(choices)}, Enter = 1] ', '1')
        if n.isdigit() and 1 <= int(n) <= len(choices):
            device, adv = choices[int(n) - 1]
    if device is None:
        r.check('connect', 'Find the amp', 'failed', 'no amp found or chosen')
    else:
        st['device'] = {'name': adv.local_name or device.name or '(no name)'}
        st['advert'] = advert_info(adv)
        undo = await linux_prepare(device, r) if is_linux() else {}
        try:
            await probe(BleakClient, device, st, r)
        except Exception as e:  # the report should still be written
            r.check(
                'connect',
                'Connect and read',
                'failed',
                f'{type(e).__name__}: {e}' + (' ' + LINUX_HINT if is_linux() else ''),
            )
            if is_linux():
                print('\n' + LINUX_HINT)
        finally:
            if undo:
                await linux_restore(device, undo)
    privacy = Privacy()
    print('\nCollecting the amp\'s USB and audio entries from this computer…')
    st['extra'] = system_devices(privacy)
    print('\nA few questions. Please answer what you can; Enter skips one.')
    st['answers'] = {
        'Amp model (as printed on the amp)': ask(
            '  Amp model, as printed on the amp (please fill in): '
        ),
        'Amp firmware (if known)': ask('  Amp firmware version, if you know it (Enter = skip): '),
        'USB cable to this computer': ask(
            '  Is the amp connected to this computer with a USB cable? (yes/no, Enter = skip): '
        ),
        'Bluetooth audio playing': ask(
            '  Is music playing to the amp over Bluetooth right now? (yes/no, Enter = skip): '
        ),
        **st.get('observations', {}),
    }
    st['notes'] = privacy.scrub(
        ask('  Anything else, for example something odd that happened (one line, Enter = skip): ')
    )
    st['removed'] = [x + ' (in your notes or the computer\'s device list)' for x in privacy.reasons]
    text = build_report(st)
    folder = Path(out_dir) if out_dir else Path.home() / 'Desktop'
    folder = folder if folder.is_dir() else Path.home()
    name = re.sub(r'[^\w-]+', '-', (st.get('device') or {}).get('name') or 'amp')
    # date and time, so that reports of several runs don't overwrite each other
    path = folder / f"fender-amp-report-{name}-{st['date']}-{datetime.now():%H%M%S}.md"
    path.write_text(text, encoding='utf-8')
    print(f'\nThe report is saved as:\n  {path}\nPlease open it and read it before you send it.')
    if yes('Open it now?', 'please do: read what you would send'):
        open_file(path)
    subject = f"Fender amp check: {st['answers'].get('Amp model (as printed on the amp)') or st.get('device', {}).get('name', 'amp')}"
    summary = '\n'.join(
        f"- {c['title']}: {STATUS_TEXT.get(c['status'], c['status'])}" for c in r.checks
    )
    # A mail or issue link can't carry the whole report (mail programs cut long links off),
    # so it goes to the clipboard and the text that opens says where to paste it.
    if CONFIG['email'] and yes(
        'Open your email program to send it?', 'please do, or use GitHub below'
    ):
        copied = copy_to_clipboard(text, path)
        paste = (
            '>>> PASTE THE REPORT HERE: it is already copied (Ctrl+V, on a Mac Cmd+V). <<<\n'
            f'(Or attach the file {path.name} instead.)'
            if copied
            else f'>>> PLEASE ATTACH THE FILE {path.name} (from {path.parent}) <<<'
        )
        body = (
            f'Hello,\n\nhere is the report from the Fender amp check (amp-probe {PROBE_VERSION}).'
            f'\n\n{paste}\n\nSummary:\n{summary}\n'
        )
        open_link(
            f"mailto:{CONFIG['email']}?subject={urllib.parse.quote(subject)}&body={urllib.parse.quote(body)}"
        )
        print(
            '\n*** The report is copied. In the email, paste it where it says so (Ctrl+V, on a Mac Cmd+V). ***'
            if copied
            else f'\n*** Please attach the report file to the email:\n    {path} ***'
        )
        print(
            f"If no email program opened: write to {CONFIG['email']}, subject \"{subject}\", and "
            + ('paste the report (it is copied).' if copied else 'attach the report file.')
        )
        # Gmail's own "new message" address works without any mail program or link handler
        if yes(
            'Open Gmail in the browser instead?', 'yes if no email program opened and you use Gmail'
        ):
            webbrowser.open(
                'https://mail.google.com/mail/?view=cm&fs=1'
                f"&to={urllib.parse.quote(CONFIG['email'])}&su={urllib.parse.quote(subject)}"
                f'&body={urllib.parse.quote(body)}'
            )
    if yes(
        'Open a GitHub issue page to send it there?',
        'needs a GitHub account (free); please do if you did not send it by email',
    ):
        copied = copy_to_clipboard(text, path)
        paste = (
            '**Paste the report here: it is already copied (Ctrl+V, on a Mac Cmd+V).** If GitHub '
            f'says it is too long, drag the file {path.name} into this box instead.'
            if copied
            else f'**Please drag the file {path.name} into this box.**'
        )
        body = (
            f'Report from the Fender amp check (amp-probe {PROBE_VERSION}, python).\n\n'
            f'{paste}\n\nSummary:\n{summary}\n'
        )
        webbrowser.open(
            f"{CONFIG['issues']}?title={urllib.parse.quote(subject)}&body={urllib.parse.quote(body)}"
        )
        print(
            '\n*** The report is copied. On GitHub, paste it into the text box (Ctrl+V, on a Mac Cmd+V). ***'
            if copied
            else f'\n*** Please drag the report file into the GitHub text box:\n    {path} ***'
        )
    return 0


def open_link(url):
    """Open a link with the desktop's default program for it.

    On Linux, webbrowser often starts a browser directly, which can't handle mailto:
    links; xdg-open asks the desktop, which knows the default mail program.
    """
    if is_linux():
        try:
            if subprocess.run(['xdg-open', url], timeout=30).returncode == 0:
                return
        except (OSError, subprocess.SubprocessError):
            pass
    webbrowser.open(url)


def copy_to_clipboard(text, path):
    """Put the report on the clipboard with the system's own tool; False if there is none."""
    env = dict(os.environ, AMP_REPORT=str(path), LANG=os.environ.get('LANG') or 'en_US.UTF-8')
    if sys.platform.startswith('win'):
        # reads the saved UTF-8 file itself: no console code page in the way
        cmds = [
            [
                'powershell',
                '-NoProfile',
                '-Command',
                'Get-Content -Raw -Encoding UTF8 -LiteralPath $env:AMP_REPORT | Set-Clipboard',
            ]
        ]
    elif sys.platform == 'darwin':
        cmds = [['pbcopy']]
    else:
        cmds = [
            ['wl-copy'],
            ['xclip', '-selection', 'clipboard'],
            ['xsel', '--clipboard', '--input'],
        ]
    for cmd in cmds:
        try:
            # no pipes kept: wl-copy and xclip stay in the background to serve the clipboard
            done = subprocess.run(
                cmd,
                input=text.encode('utf-8'),
                env=env,
                timeout=15,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            if done.returncode == 0:
                return True
        except (OSError, subprocess.SubprocessError):
            continue
    return False


def open_file(path):
    try:
        if sys.platform.startswith('win'):
            os.startfile(str(path))  # noqa: S606 (opens the report in the default program)
        elif sys.platform == 'darwin':
            subprocess.run(['open', str(path)], check=False)
        else:
            subprocess.run(['xdg-open', str(path)], check=False)
    except OSError:
        print('Could not open it; please open the file yourself.')


async def mtu_of(client):
    """The link's MTU.

    On BlueZ bleak only knows it after acquiring a characteristic (else it warns and
    guesses), so read BlueZ's own MTU property of a characteristic instead: that touches
    nothing. Elsewhere bleak knows it.
    """
    if is_linux():
        for sv in client.services:
            for c in sv.characteristics:
                obj = getattr(c, 'obj', None)
                if isinstance(obj, (tuple, list)) and obj:
                    out = _run(
                        [
                            'busctl',
                            '--system',
                            'get-property',
                            'org.bluez',
                            obj[0],
                            'org.bluez.GattCharacteristic1',
                            'MTU',
                        ],
                        timeout=10,
                    )
                    m = re.match(r'q (\d+)', out.strip())
                    return int(m.group(1)) if m else '?'
        return '?'
    with warnings.catch_warnings():
        warnings.simplefilter('ignore')
        return client.mtu_size


def linux_links(address):
    """Which of the amp's Bluetooth links BlueZ has up: {'LE': 'yes', 'BREDR': 'no'}."""
    info = bluez_info(address)
    return {k: v for k, v in re.findall(r'^\s*(LE|BREDR)\.Connected: (\w+)', info, re.M)}


async def wait_for_gatt(client, seconds=10):
    """Wait until BlueZ's GATT client works over the new LE link (as maneline's mmp_bluez).

    Connect returns once the radio link exists; the GATT client is ready about a second
    later. Until then BlueZ still exports the previous session's characteristics, and
    ServicesResolved can already be true, so a subscription made now can be lost without
    an error: the amp then seems not to answer. A read has to cross the link, so retry one
    (the GAP Device Name, else any readable characteristic) until it succeeds.
    Returns (milliseconds, tries), or None if there was nothing to read or it timed out.
    """
    chars = [c for sv in client.services for c in sv.characteristics]
    probe_char = next((c for c in chars if c.uuid.lower().startswith('00002a00-')), None) or next(
        (c for c in chars if 'read' in c.properties), None
    )
    if probe_char is None:
        return None
    t0, tries = time.monotonic(), 0
    while True:
        tries += 1
        try:
            await client.read_gatt_char(probe_char)
            return round((time.monotonic() - t0) * 1000), tries
        except Exception:  # not ready yet (Failed, NotConnected, InProgress, ...)
            if time.monotonic() - t0 > seconds:
                return None
            await asyncio.sleep(0.2)


def linux_notifying(char):
    """True if some program already receives this characteristic's notifications."""
    obj = getattr(char, 'obj', None)
    path = obj[0] if isinstance(obj, (tuple, list)) and obj else None
    if not path:
        return False
    out = _run(
        [
            'busctl',
            '--system',
            'get-property',
            'org.bluez',
            path,
            'org.bluez.GattCharacteristic1',
            'Notifying',
        ],
        timeout=10,
    )
    return out.strip() == 'b true'


async def start_notify_when_ready(client, char, callback, seconds=5):
    """start_notify, retried while BlueZ still reports the link as not ready."""
    t0 = time.monotonic()
    while True:
        try:
            return await client.start_notify(char, callback)
        except Exception:
            if time.monotonic() - t0 > seconds:
                raise
            await asyncio.sleep(0.2)


async def probe(BleakClient, device, st, r):
    print(f"\nConnecting to {st['device']['name']}…")
    t0 = time.monotonic()
    async with BleakClient(device, timeout=20) as client:
        ms, mtu = round((time.monotonic() - t0) * 1000), await mtu_of(client)
        r.check(
            'connect',
            'Connect over Bluetooth',
            'ok',
            f"\"{st['device']['name']}\" in {ms} ms, MTU {mtu}",
        )
        if is_linux():
            # the control service is only on LE; with the audio link up, bleak may never open LE
            links = linux_links(device.address)
            if links:
                le_up = links.get('LE') == 'yes'
                r.check(
                    'linux-link',
                    'Linux: Bluetooth links up',
                    'ok' if le_up else 'warn',
                    f"LE {links.get('LE', '?')}, classic {links.get('BREDR', '?')}"
                    + (
                        ''
                        if le_up
                        else '; the control link (LE) is not up, so the amp will not answer'
                    ),
                )
                if not le_up:
                    print('\n' + LINUX_HINT)
        if is_linux():
            ready = await wait_for_gatt(client)
            r.check(
                'linux-gatt',
                'Linux: Bluetooth data link ready',
                'ok' if ready else 'warn',
                (
                    f'a read went through after {ready[0]} ms ({ready[1]} tries)'
                    if ready
                    else 'no read went through within 10 s; replies may get lost'
                ),
            )
        st['gatt'], family, req, resp, info = [], None, None, None, {}
        for sv in client.services:
            entry = {'uuid': sv.uuid.lower(), 'chars': []}
            st['gatt'].append(entry)
            if not family and entry['uuid'] in FAMILY_BY_SERVICE:
                family, st['serviceUuid'] = FAMILY_BY_SERVICE[entry['uuid']], entry['uuid']
            for c in sv.characteristics:
                cu = c.uuid.lower()
                entry['chars'].append(
                    {
                        'uuid': cu,
                        'props': list(c.properties),
                        'descriptors': [
                            d.uuid.lower().replace(STD, '').replace('0000', '0x', 1)
                            for d in c.descriptors
                        ],
                    }
                )
                if entry['uuid'] in FAMILY_BY_SERVICE and cu == UUID['request']:
                    req = c
                if entry['uuid'] in FAMILY_BY_SERVICE and cu == UUID['response']:
                    resp = c
                if cu in DEVICE_INFO and cu not in NOT_READ and 'read' in c.properties:
                    try:
                        v = bytes(await client.read_gatt_char(c))
                        info[DEVICE_INFO[cu]] = (
                            v.hex()
                            if DEVICE_INFO[cu] == 'PnP id'
                            else Privacy().scrub(v.decode('utf-8', 'replace').rstrip('\0'))
                        )
                    except Exception as e:  # not readable on this amp
                        info[DEVICE_INFO[cu]] = f'(not readable: {e})'
        st['deviceInfo'], st['family'] = info, family
        r.check(
            'services',
            'Bluetooth services',
            'ok' if family else 'warn',
            f"{len(st['gatt'])} service{'' if len(st['gatt']) == 1 else 's'}; "
            + (
                f"Fender control service found: {FAMILIES[family]['label']}"
                if family
                else 'no known Fender control service'
            )
            + (
                ''
                if not family or (req and resp)
                else '; but the request/response characteristics are missing'
            ),
        )
        if not (family and req and resp):
            return
        with_response = 'write' in req.properties

        async def write(data):
            await client.write_gatt_char(req, data, response=with_response)

        s = Session(family, write)
        st['session'] = s
        s.event(
            f"writes {'with' if with_response else 'without'} response, MTU {await mtu_of(client)}"
        )
        if is_linux() and linux_notifying(resp):
            # BlueZ would then deliver every reply twice, which garbles long replies (maneline's
            # mmp_bluez refuses to start for the same reason)
            r.check(
                'linux-notify',
                'Linux: another program receives the amp\'s replies',
                'warn',
                'already subscribed before this program: every reply arrives twice and long '
                'replies get mixed up',
            )
            print(
                '\nAnother program on this computer already receives the amp\'s replies. Usually that is'
                '\nthe browser after the amp check page: it can stay connected even when the tab shows'
                '\n"Find my amp" again. Close the browser completely (all windows). Otherwise: an earlier'
                '\nrun of this program that is still open, or maneline\'s agent. Every reply would then'
                '\narrive twice and long replies would get mixed up.'
            )
            if not await yes_async(
                'Continue anyway?', 'better not: close the other program, then run this again'
            ):
                return
        await start_notify_when_ready(client, resp, lambda _c, data: s.on_data(bytes(data)))
        try:
            await read_and_control(s, r, st, family)
        finally:
            s.stop_heartbeat()
        s.event('the script closed the connection')


async def read_and_control(s, r, st, family):
    print('Reading from the amp (this only reads)…')
    if family == 'gt':
        await gt_read(s, r)
    else:
        await framed_read(s, r, family)
    if not s.messages:
        # nothing came back, so a control test could neither be confirmed nor undone safely
        r.check(
            'control',
            'Control test',
            'skipped',
            'the amp did not answer the reading, so the control test was not run',
        )
        print('\nThe amp did not answer, so the control test is skipped.')
        if is_linux():
            print(LINUX_HINT)
        st['observations'] = {'Control test': 'skipped: the amp did not answer'}
        return
    print(
        '\nShort control test: the amp switches to another preset for two seconds and back,\n'
        'then one knob (such as gain) is turned a little and back. Nothing is saved.\n'
        'If the current sound has unsaved changes, the test is skipped by itself.'
    )
    if await yes_async('Run the control test?', 'please do: nothing is saved on the amp'):
        if family == 'gt':
            await gt_control(s, r)
        else:
            await framed_control(s, r)
        print(
            '  With a guitar plugged in you can hear the change, even if the amp\'s display stays dark.\n'
            '  If you can\'t tell, answer "not sure": the program read the values back anyway.'
        )
        st['observations'] = {
            'Amp switched preset and back': await ask_async(
                '  Did the amp switch to another preset and back? (yes/no/not sure, Enter = skip): '
            ),
            'Sound or knob changed and came back': await ask_async(
                '  Did the sound or a knob change briefly and come back? (yes/no/not sure, Enter = skip): '
            ),
        }
    else:
        st['observations'] = {'Control test': 'skipped by the tester'}


def main():
    import argparse

    parser = argparse.ArgumentParser(
        description='Fender amp check: read what a Fender amp reports over '
        'Bluetooth and write a report to check and send.'
    )
    parser.add_argument('--out', help='folder for the report (default: Desktop, else home folder)')
    args = parser.parse_args()
    try:
        return asyncio.run(main_async(args.out))
    except KeyboardInterrupt:
        return 130


# ---- message definitions ---------------------------------------------------------------
# The parts this program needs, extracted from the Fender Tone app and written here by
# amp-probe/build_schema.py, at the end so that the program comes first. Per message: [number, name, type, type name,
# repeated, oneof index]; types as in protobuf's FieldDescriptorProto.
# SCHEMA-BEGIN
SCHEMA = json.loads(
    r'''
{
"enums": {
 "gt.BackupRestoreCompleteStatus.Status": {
  "0":"SUCCESS", "1":"FAIL", "2":"DIRTY_CURR_PRESET", "3":"COMPRESS_FAIL",
  "4":"DECOMPRESS_FAIL", "5":"INCORRECT_CRC", "6":"ABORT"
 },
 "gt.BtState": {
  "0":"BT_OFF", "1":"BLE_ON", "2":"BLUETOOTH_STREAMING_ON", "3":"BLE_AND_BLUETOOTH_STREAMING_ON"
 },
 "gt.FileTransferBegin.FileType": {
  "0":"JSON", "1":"BINARY"
 },
 "gt.FootswitchCommStatus.ProductID": {
  "12":"FTSW_ID_ULT1", "8":"FTSW_ID_ULT4", "9":"FTSW_ID_SIMPLE", "11":"FTSW_ID_SWR_2BTN",
  "10":"FTSW_ID_SM1500"
 },
 "gt.LooperState": {
  "0":"STOPPED_CLEAN", "1":"STOPPED_DIRTY", "2":"RECORDING", "3":"OVERDUBBING", "4":"PLAYING",
  "5":"NUM_LOOPER_STATES"
 },
 "gt.PacketStatus": {
  "0":"SUCCESS", "1":"UNKNOWN_ERROR", "2":"INVALID_ID"
 },
 "gt.PacketType": {
  "0":"LOAD_PRESET", "1":"NODE_REPLACE", "2":"NODE_ADD", "3":"NODE_INSERT_PRE", "4":"NODE_MOVE",
  "5":"NODE_DELETE", "6":"SYNC", "7":"FACTORY_RESTORE", "8":"RESTORE_ALL_FACTORY",
  "9":"PRESET_DISPLAY", "10":"FIND_NEXT_VALID_PRESET", "11":"FIND_PREVIOUS_VALID_PRESET",
  "12":"FIND_NEXT_EMPTY_SLOT", "13":"FIND_PREVIOUS_EMPTY_SLOT", "14":"LOAD_PRESET_IF_VALID",
  "15":"SAVE_CURRENT_PRESET", "16":"DELETE_PRESET", "17":"RENAME_PRESET",
  "18":"NODE_INSERT_POST", "19":"TONE_SYNC", "20":"SAVE_TO", "21":"BACKUP", "22":"RESTORE",
  "23":"SYNC_CURRRENT_PRESET_TONE"
 },
 "gt.SSIDEntry.Protocol": {
  "0":"NONE", "1":"WEP", "2":"WPA_WPA2_PSK", "3":"EAP_802_1X"
 },
 "gt.WifiConnectToSSIDMessage.Protocol": {
  "0":"NONE", "1":"WEP", "2":"WPA_WPA2_PSK", "3":"EAP_802_1X"
 },
 "gt.WifiConnectToSSIDResponse.ResponseCode": {
  "0":"CONNECTED", "1":"NOT_FOUND", "2":"INVALID_PASSWORD_OR_SSID", "3":"INVALID_PROTOCOL"
 },
 "ltx.ErrorType": {
  "0":"UNSUPPORTED", "1":"FAILED", "2":"INVALID_PARAM", "3":"INVALID_NODE_ID",
  "4":"PARAM_OUT_OF_BOUNDS", "5":"FACTORY_RESTORE_IN_PROGRESS"
 },
 "ltx.ModalContext": {
  "0":"SYNC_BEGIN", "1":"SYNC_END", "2":"BACKUP_BEGIN", "3":"BACKUP_END", "4":"RESTORE_BEGIN",
  "5":"RESTORE_END", "6":"TUNER_ENABLE", "7":"TUNER_DISABLE", "8":"FACTORY_RESTORE_BEGIN",
  "9":"FACTORY_RESTORE_END", "10":"TONE_BUSY_BEGIN", "11":"TONE_BUSY_END"
 },
 "ltx.ModalState": {
  "0":"OK", "1":"FAIL"
 },
 "ltx.ProtobufOutputPower": {
  "0":"One", "1":"Two", "2":"Three", "3":"Four"
 },
 "ltx.ProtobufPresetMode": {
  "0":"Sequential", "1":"Bank"
 },
 "ltx.ProtobufProductId": {
  "0":"LTX100", "1":"LTX50"
 },
 "ltx.ResponseType": {
  "0":"UNSOLICITED", "1":"NOT_LAST_ACK", "2":"IS_LAST_ACK"
 },
 "mp.EqPreset": {
  "0":"FLAT", "1":"BRIGHT_CUT_1", "2":"BRIGHT_CUT_2", "3":"LOW_CUT_1", "4":"LOW_CUT_2",
  "5":"BRIGHT_BOOST_1", "6":"BRIGHT_BOOST_2", "7":"BASS_BOOST_1", "8":"BASS_BOOST_2"
 },
 "mp.ErrorType": {
  "0":"UNSUPPORTED", "1":"FAILED", "2":"INVALID_PARAM", "3":"INVALID_NODE_ID",
  "4":"PARAM_OUT_OF_BOUNDS", "5":"FACTORY_RESTORE_IN_PROGRESS"
 },
 "mp.ModalContext": {
  "0":"SYNC_BEGIN", "1":"SYNC_END", "2":"BACKUP_BEGIN", "3":"BACKUP_END", "4":"RESTORE_BEGIN",
  "5":"RESTORE_END", "6":"TUNER_ENABLE", "7":"TUNER_DISABLE", "8":"FACTORY_RESTORE_BEGIN",
  "9":"FACTORY_RESTORE_END", "10":"TONE_BUSY_BEGIN", "11":"TONE_BUSY_END"
 },
 "mp.ModalState": {
  "0":"OK", "1":"FAIL"
 },
 "mp.ResponseType": {
  "0":"UNSOLICITED", "1":"NOT_LAST_ACK", "2":"IS_LAST_ACK"
 }
},
"messages": {
 "gt.BLEStatus": [
  [1,"status",8,"",false,-1]
 ],
 "gt.BackupRestoreCompleteStatus": [
  [1,"success",14,"gt.BackupRestoreCompleteStatus.Status",false,-1]
 ],
 "gt.BackupRestoreEnableStatus": [
  [1,"enable",8,"",false,-1]
 ],
 "gt.BpmStatus": [
  [1,"value",2,"",false,-1]
 ],
 "gt.BpmStatusRequest": [
  [1,"request",8,"",false,-1]
 ],
 "gt.BtStatus": [
  [1,"state",14,"gt.BtState",false,-1]
 ],
 "gt.BtStatusRequest": [
  [1,"request",8,"",false,-1]
 ],
 "gt.BtStreamingStatus": [
  [1,"status",8,"",false,-1]
 ],
 "gt.CloudPresetStatus": [
  [1,"cloudPreset",8,"",false,-1]
 ],
 "gt.ConsoleMessage": [
  [1,"command",9,"",false,-1], [2,"argument",9,"",false,-1]
 ],
 "gt.CurrentLoadedPresetIdStatus": [
  [1,"currentLoadedPresetId",9,"",false,-1]
 ],
 "gt.DspUnitParameterStatus": [
  [1,"nodeId",9,"",false,-1], [2,"parameterId",9,"",false,-1],
  [3,"floatParameter",2,"",false,0], [4,"stringParameter",9,"",false,0],
  [5,"sint32Parameter",17,"",false,0], [6,"boolParameter",8,"",false,0]
 ],
 "gt.EffectsLoopReturnStatus": [
  [1,"leftIsConnected",8,"",false,-1], [2,"rightIsConnected",8,"",false,-1]
 ],
 "gt.FenderMessage": [
  [102,"setDspUnitParameter",11,"gt.SetDspUnitParameter",false,0],
  [106,"getDspUnitParameter",11,"gt.GetDspUnitParameter",false,0],
  [124,"looperStatus",11,"gt.LooperStatus",false,0],
  [126,"globalEQSync",11,"gt.GlobalEQSync",false,0],
  [201,"nodeStatus",11,"gt.NodeStatus",false,0],
  [203,"dspUnitParameterStatus",11,"gt.DspUnitParameterStatus",false,0],
  [204,"currentLoadedPresetIdStatus",11,"gt.CurrentLoadedPresetIdStatus",false,0],
  [205,"tunerNoteStatus",11,"gt.TunerNoteStatus",false,0],
  [206,"tunerEnableStatus",11,"gt.TunerEnableStatus",false,0],
  [207,"masterVolumeStatus",11,"gt.MasterVolumeStatus",false,0],
  [208,"presetStatus",11,"gt.PresetStatus",false,0],
  [209,"productIdentificationStatus",11,"gt.ProductIdentificationStatus",false,0],
  [210,"headphonesConnectedStatus",11,"gt.HeadphonesConnectedStatus",false,0],
  [211,"effectsLoopReturnStatus",11,"gt.EffectsLoopReturnStatus",false,0],
  [212,"processorPercentageAvailableStatus",11,"gt.ProcessorPercentageAvailableStatus",false,0],
  [213,"masterVolumeNotDBStatus",11,"gt.MasterVolumeNotDBStatus",false,0],
  [214,"presetEditedStatus",11,"gt.PresetEditedStatus",false,0],
  [215,"tunerNoteViewStatus",11,"gt.TunerNoteViewStatus",false,0],
  [300,"rawJSON",11,"gt.RawJSON",false,0],
  [301,"consoleMessage",11,"gt.ConsoleMessage",false,0],
  [302,"timestampMessage",11,"gt.TimestampMessage",false,0],
  [303,"packetBegin",11,"gt.PacketBegin",false,0], [304,"packetEnd",11,"gt.PacketEnd",false,0],
  [305,"presetCount",11,"gt.PresetCount",false,0],
  [306,"networkStatus",11,"gt.NetworkStatus",false,0],
  [307,"wifiConnectToSSIDMessage",11,"gt.WifiConnectToSSIDMessage",false,0],
  [308,"wifiConnectToSSIDResponse",11,"gt.WifiConnectToSSIDResponse",false,0],
  [311,"wifiOnOffCmdResponse",11,"gt.WifiOnOffCmdResponse",false,0],
  [312,"wifiScanForNetworksMessage",11,"gt.WifiScanForNetworksMessage",false,0],
  [314,"scanCommandFailed",11,"gt.ScanCommandFailed",false,0],
  [407,"loadPresetIfValid",11,"gt.LoadPresetIfValid",false,0],
  [418,"movePresetComplete",11,"gt.MovePresetComplete",false,0],
  [424,"retrievePreset",11,"gt.RetrievePreset",false,0],
  [427,"fileTransferBegin",11,"gt.FileTransferBegin",false,0],
  [428,"fileTransferData",11,"gt.FileTransferData",false,0],
  [429,"fileTransferEnd",11,"gt.FileTransferEnd",false,0],
  [430,"backupRestoreEnableStatus",11,"gt.BackupRestoreEnableStatus",false,0],
  [431,"backupRestoreCompleteStatus",11,"gt.BackupRestoreCompleteStatus",false,0],
  [435,"restartAmpMessage",11,"gt.RestartAmpMessage",false,0],
  [438,"bpmStatus",11,"gt.BpmStatus",false,0],
  [441,"globalBpmModeStatus",11,"gt.GlobalBpmModeStatus",false,0],
  [444,"globalBpmModeStatusRequest",11,"gt.GlobalBpmModeStatusRequest",false,0],
  [445,"bpmStatusRequest",11,"gt.BpmStatusRequest",false,0],
  [500,"packetStartMTU",11,"gt.PacketStartMTU",false,0],
  [501,"packetDataMTU",11,"gt.PacketDataMTU",false,0],
  [502,"packetStopMTU",11,"gt.PacketStopMTU",false,0],
  [503,"loopbackTest",11,"gt.LoopbackTest",false,0],
  [602,"footswitchCommStatus",11,"gt.FootswitchCommStatus",false,0],
  [803,"setlistStatus",11,"gt.SetlistStatus",false,0],
  [805,"setlistManagerSync",11,"gt.SetlistManagerSync",false,0],
  [807,"setlistViewAddMenuItemOneBeforeEnd",11,"gt.SetlistViewAddMenuItemOneBeforeEnd",false,0],
  [821,"setlistManagerReorderPresetComplete",11,"gt.SetlistManagerReorderPresetComplete",false,0],
  [825,"setlistSyncEnd",11,"gt.SetlistSyncEnd",false,0],
  [850,"qAStatus",11,"gt.QAStatus",false,0],
  [854,"qAViewAddMenuItemOneBeforeEnd",11,"gt.QAViewAddMenuItemOneBeforeEnd",false,0],
  [901,"toneSync",11,"gt.ToneSync",false,0], [902,"systemStatus",11,"gt.SystemStatus",false,0],
  [903,"presetInfoMessage",11,"gt.PresetInfoMessage",false,0],
  [904,"presetJSONMessage",11,"gt.PresetJSONMessage",false,0],
  [908,"usbGainStatus",11,"gt.UsbGainStatus",false,0],
  [912,"btStatus",11,"gt.BtStatus",false,0],
  [913,"btStatusRequest",11,"gt.BtStatusRequest",false,0],
  [916,"setDownloadServerComplete",11,"gt.SetDownloadServerComplete",false,0],
  [917,"btStreamingStatus",11,"gt.BtStreamingStatus",false,0],
  [918,"bLEStatus",11,"gt.BLEStatus",false,0],
  [920,"syncCurrentPresetToTone",11,"gt.SyncCurrentPresetToTone",false,0],
  [1003,"cloudPresetStatus",11,"gt.CloudPresetStatus",false,0],
  [1013,"saveViewGetCmdInfoStatus",11,"gt.SaveViewGetCmdInfoStatus",false,0],
  [1023,"keyboardSaveResponse",11,"gt.KeyboardSaveResponse",false,0],
  [1038,"globalFXAssignSync",11,"gt.GlobalFXAssignSync",false,0],
  [1040,"globalFXAssignStatus",11,"gt.GlobalFXAssignStatus",false,0],
  [1050,"globalFXAssignSyncMgt7",11,"gt.GlobalFXAssignSyncMgt7",false,0]
 ],
 "gt.FileTransferBegin": [
  [1,"id",5,"",false,-1], [2,"numPackets",5,"",false,-1], [3,"numBytes",5,"",false,-1],
  [4,"fileType",14,"gt.FileTransferBegin.FileType",false,-1]
 ],
 "gt.FileTransferData": [
  [1,"seqNumber",5,"",false,-1], [2,"id",5,"",false,-1], [3,"data",12,"",false,-1]
 ],
 "gt.FileTransferEnd": [
  [1,"id",5,"",false,-1], [2,"crcCheck",5,"",false,-1]
 ],
 "gt.FootswitchCommStatus": [
  [1,"ftswProductIdMsg",14,"gt.FootswitchCommStatus.ProductID",false,-1]
 ],
 "gt.GetDspUnitParameter": [
  [1,"nodeId",9,"",false,-1], [2,"parameterId",9,"",false,-1]
 ],
 "gt.GlobalBpmModeStatus": [
  [1,"globalBpmOn",8,"",false,-1]
 ],
 "gt.GlobalBpmModeStatusRequest": [
  [1,"request",8,"",false,-1]
 ],
 "gt.GlobalEQSync": [
  [1,"sync",8,"",false,-1]
 ],
 "gt.GlobalFXAssignStatus": [
  [1,"assign",9,"",false,-1]
 ],
 "gt.GlobalFXAssignSync": [
  [1,"sync",8,"",false,-1]
 ],
 "gt.GlobalFXAssignSyncMgt7": [
  [1,"sync",8,"",false,-1]
 ],
 "gt.HeadphonesConnectedStatus": [
  [1,"isConnected",8,"",false,-1]
 ],
 "gt.KeyboardSaveResponse": [
  [1,"text",9,"",false,-1]
 ],
 "gt.LoadPresetIfValid": [
  [1,"slot",5,"",false,-1], [2,"sendToTone",8,"",false,-1], [3,"setlistIndex",5,"",false,-1]
 ],
 "gt.LoopbackTest": [
  [1,"data",9,"",false,-1]
 ],
 "gt.LooperStatus": [
  [1,"state",14,"gt.LooperState",false,-1], [2,"trackCount",5,"",false,-1]
 ],
 "gt.MasterVolumeNotDBStatus": [
  [1,"value",2,"",false,-1]
 ],
 "gt.MasterVolumeStatus": [
  [1,"valueDB",2,"",false,-1]
 ],
 "gt.MovePresetComplete": [
  [1,"isComplete",8,"",false,-1]
 ],
 "gt.NetworkStatus": [
  [1,"connectionStatus",9,"",false,-1], [2,"connectedSSID",9,"",false,-1],
  [3,"connectedIP",9,"",false,-1], [4,"availableNetworks",11,"gt.SSIDEntry",true,-1]
 ],
 "gt.NodeEntry": [
  [1,"nodeId",9,"",false,-1], [2,"fenderId",9,"",false,-1], [3,"bypass",8,"",false,-1]
 ],
 "gt.NodeStatus": [
  [1,"nodeStatus",11,"gt.NodeEntry",true,-1]
 ],
 "gt.PacketBegin": [
  [1,"packetType",14,"gt.PacketType",false,-1]
 ],
 "gt.PacketDataMTU": [
  [1,"seqNumber",5,"",false,-1], [2,"id",5,"",false,-1], [3,"data",12,"",false,-1]
 ],
 "gt.PacketEnd": [
  [1,"packetType",14,"gt.PacketType",false,-1], [2,"packetStatus",14,"gt.PacketStatus",false,-1]
 ],
 "gt.PacketStartMTU": [
  [1,"numBytes",5,"",false,-1], [2,"id",5,"",false,-1]
 ],
 "gt.PacketStopMTU": [
  [1,"id",5,"",false,-1], [2,"crcCheck",5,"",false,-1]
 ],
 "gt.PresetCount": [
  [1,"count",17,"",false,-1]
 ],
 "gt.PresetEditedStatus": [
  [1,"presetEdited",8,"",false,-1]
 ],
 "gt.PresetInfoMessage": [
  [1,"slotNumber",5,"",false,-1], [2,"displayName",9,"",false,-1],
  [3,"preset_id",9,"",false,-1], [4,"author",9,"",false,-1], [5,"timestamp",3,"",false,-1],
  [6,"created_at",3,"",false,-1], [7,"product_id",9,"",false,-1],
  [8,"is_factory_default",8,"",false,-1], [9,"isEmpty",8,"",false,-1],
  [10,"source_id",9,"",false,-1]
 ],
 "gt.PresetJSONMessage": [
  [1,"data",9,"",false,-1], [2,"slotIndex",5,"",false,-1]
 ],
 "gt.PresetStatus": [
  [1,"slot",13,"",false,-1], [2,"displayName",9,"",false,-1], [3,"isEmpty",8,"",false,-1]
 ],
 "gt.ProcessorPercentageAvailableStatus": [
  [1,"percent",2,"",false,-1]
 ],
 "gt.ProductIdentificationStatus": [
  [1,"id",9,"",false,-1]
 ],
 "gt.QAStatus": [
  [1,"qa1",13,"",false,-1], [2,"qa2",13,"",false,-1], [3,"qa3",13,"",false,-1]
 ],
 "gt.QAViewAddMenuItemOneBeforeEnd": [
  [1,"displayText",9,"",false,-1]
 ],
 "gt.RawJSON": [
  [1,"data",9,"",false,-1]
 ],
 "gt.RestartAmpMessage": [
  [1,"bRestart",8,"",false,-1]
 ],
 "gt.RetrievePreset": [
  [1,"slot",5,"",false,-1]
 ],
 "gt.SSIDEntry": [
  [1,"ssid",9,"",false,-1], [2,"protocol",14,"gt.SSIDEntry.Protocol",false,-1],
  [3,"inSupplicant",8,"",false,-1], [4,"signalStrength",5,"",false,-1]
 ],
 "gt.SaveViewGetCmdInfoStatus": [
  [1,"cmdInfo",9,"",false,-1]
 ],
 "gt.ScanCommandFailed": [
  [1,"hackSoItGens",8,"",false,-1]
 ],
 "gt.SetDownloadServerComplete": [
  [1,"complete",8,"",false,-1]
 ],
 "gt.SetDspUnitParameter": [
  [1,"nodeId",9,"",false,-1], [2,"parameterId",9,"",false,-1],
  [3,"floatParameter",2,"",false,0], [4,"stringParameter",9,"",false,0],
  [5,"sint32Parameter",17,"",false,0], [6,"boolParameter",8,"",false,0]
 ],
 "gt.SetlistManagerReorderPresetComplete": [
  [1,"isComplete",8,"",false,-1]
 ],
 "gt.SetlistManagerSync": [
 ],
 "gt.SetlistStatus": [
  [1,"slot",13,"",false,-1], [2,"displayName",9,"",false,-1], [3,"setlist",13,"",true,-1],
  [4,"Ids",9,"",true,-1]
 ],
 "gt.SetlistSyncEnd": [
  [1,"successful",8,"",false,-1]
 ],
 "gt.SetlistViewAddMenuItemOneBeforeEnd": [
  [1,"displayText",9,"",false,-1]
 ],
 "gt.SyncCurrentPresetToTone": [
  [1,"value",8,"",false,-1]
 ],
 "gt.SystemStatus": [
  [1,"address",9,"",false,-1], [2,"firmwareVersion",9,"",false,-1],
  [3,"wifiState",9,"",false,-1], [4,"btName",9,"",false,-1], [5,"productId",9,"",false,-1]
 ],
 "gt.TimestampMessage": [
  [1,"seconds",5,"",false,-1], [2,"nanoseconds",5,"",false,-1]
 ],
 "gt.ToneSync": [
  [1,"hackSoItGens",8,"",false,-1]
 ],
 "gt.TunerEnableStatus": [
  [1,"enable",8,"",false,-1]
 ],
 "gt.TunerNoteStatus": [
  [1,"midiNoteNumberIndex",17,"",false,-1], [2,"midiNoteNumberCents",2,"",false,-1]
 ],
 "gt.TunerNoteViewStatus": [
  [1,"midiNoteNumberIndex",17,"",false,-1], [2,"midiNoteNumberCents",2,"",false,-1]
 ],
 "gt.UsbGainStatus": [
  [1,"valueDB",2,"",false,-1]
 ],
 "gt.WifiConnectToSSIDMessage": [
  [1,"ssidToConnect",9,"",false,-1],
  [2,"protocol",14,"gt.WifiConnectToSSIDMessage.Protocol",false,-1],
  [3,"password",9,"",false,-1]
 ],
 "gt.WifiConnectToSSIDResponse": [
  [1,"responseCode",14,"gt.WifiConnectToSSIDResponse.ResponseCode",false,-1]
 ],
 "gt.WifiOnOffCmdResponse": [
  [1,"powerOn",8,"",false,-1]
 ],
 "gt.WifiScanForNetworksMessage": [
  [1,"hackSoItGens",8,"",false,-1]
 ],
 "ltx.ClearPresetStatus": [
  [1,"presetIndex",13,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "ltx.ConnectionStatus": [
  [1,"isConnected",8,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "ltx.ConnectionStatusRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "ltx.CurrentPresetDataStatus": [
  [1,"compressedPresetJson",12,"",false,-1]
 ],
 "ltx.CurrentPresetRequest": [
  [1,"requestPresetData",8,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "ltx.CurrentPresetStatus": [
  [1,"presetIndex",13,"",false,-1], [2,"isDirty",8,"",false,-1],
  [3,"isAuditioning",8,"",false,-1], [4,"dummyField",8,"",false,-1]
 ],
 "ltx.FirmwareVersionRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "ltx.FirmwareVersionStatus": [
  [1,"version",9,"",false,-1]
 ],
 "ltx.FtswBrightnessRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "ltx.FtswBrightnessStatus": [
  [1,"ftswBrightness",13,"",false,-1]
 ],
 "ltx.FtswUpdateStatus": [
  [1,"dummyField",8,"",false,-1], [2,"inProgress",8,"",false,-1]
 ],
 "ltx.FtswVersionRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "ltx.FtswVersionStatus": [
  [1,"version",9,"",false,-1]
 ],
 "ltx.Heartbeat": [
  [1,"dummyField",8,"",false,-1]
 ],
 "ltx.LoadPresetRequest": [
  [1,"newPresetIndex",13,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "ltx.LoopbackTest": [
  [1,"data",9,"",false,-1]
 ],
 "ltx.ModalStatusMessage": [
  [1,"context",14,"ltx.ModalContext",false,-1], [2,"state",14,"ltx.ModalState",false,-1],
  [3,"dummyField",8,"",false,-1]
 ],
 "ltx.MoveNodeStatus": [
  [1,"nodeIdToMove",9,"",false,-1], [2,"nodeIdInsertLocation",9,"",false,-1]
 ],
 "ltx.MovePresetStatus": [
  [1,"success",8,"",false,-1]
 ],
 "ltx.MustangLtxAmpStatus": [
  [1,"responseType",14,"ltx.ResponseType",false,-1],
  [2,"clearPresetStatus",11,"ltx.ClearPresetStatus",false,0],
  [3,"connectionStatus",11,"ltx.ConnectionStatus",false,0],
  [4,"currentPresetDataStatus",11,"ltx.CurrentPresetDataStatus",false,0],
  [5,"currentPresetStatus",11,"ltx.CurrentPresetStatus",false,0],
  [6,"firmwareVersionStatus",11,"ltx.FirmwareVersionStatus",false,0],
  [7,"moveNodeStatus",11,"ltx.MoveNodeStatus",false,0],
  [8,"parameterStatus",11,"ltx.ParameterStatus",false,0],
  [9,"presetDataStatus",11,"ltx.PresetDataStatus",false,0],
  [10,"presetNameStatus",11,"ltx.PresetNameStatus",false,0],
  [11,"presetNamesStatus",11,"ltx.PresetNamesStatus",false,0],
  [12,"presetSavedStatus",11,"ltx.PresetSavedStatus",false,0],
  [13,"replaceNodeStatus",11,"ltx.ReplaceNodeStatus",false,0],
  [14,"unsupportedMessageStatus",11,"ltx.UnsupportedMessageStatus",false,0],
  [15,"usbAndLineOutGainStatus",11,"ltx.UsbAndLineOutGainStatus",false,0],
  [16,"tunerStatus",11,"ltx.TunerStatus",false,0],
  [17,"productIdStatus",11,"ltx.ProductIdStatus",false,0],
  [18,"outputPowerStatus",11,"ltx.OutputPowerStatus",false,0],
  [19,"presetModeStatus",11,"ltx.PresetModeStatus",false,0],
  [21,"ftswBrightnessStatus",11,"ltx.FtswBrightnessStatus",false,0],
  [22,"ftswVersionStatus",11,"ltx.FtswVersionStatus",false,0],
  [23,"movePresetStatus",11,"ltx.MovePresetStatus",false,0],
  [24,"ftswUpdateStatus",11,"ltx.FtswUpdateStatus",false,0]
 ],
 "ltx.MustangLtxLoopback": [
  [1,"responseType",14,"ltx.ResponseType",false,-1],
  [2,"loopbackTest",11,"ltx.LoopbackTest",false,-1]
 ],
 "ltx.MustangLtxToneMessage": [
  [1,"toneRequest",11,"ltx.MustangLtxToneRequest",false,0],
  [2,"ampStatus",11,"ltx.MustangLtxAmpStatus",false,0],
  [3,"modalStatus",11,"ltx.ModalStatusMessage",false,0],
  [4,"loopback",11,"ltx.MustangLtxLoopback",false,0]
 ],
 "ltx.MustangLtxToneRequest": [
  [3,"connectionStatusRequest",11,"ltx.ConnectionStatusRequest",false,0],
  [4,"currentPresetRequest",11,"ltx.CurrentPresetRequest",false,0],
  [7,"firmwareVersionRequest",11,"ltx.FirmwareVersionRequest",false,0],
  [8,"heartbeat",11,"ltx.Heartbeat",false,0],
  [9,"loadPresetRequest",11,"ltx.LoadPresetRequest",false,0],
  [11,"parameterRequest",11,"ltx.ParameterRequest",false,0],
  [12,"presetDataRequest",11,"ltx.PresetDataRequest",false,0],
  [14,"presetNamesRequest",11,"ltx.PresetNamesRequest",false,0],
  [18,"setParameterRequest",11,"ltx.SetParameterRequest",false,0],
  [21,"usbAndLineOutGainRequest",11,"ltx.UsbAndLineOutGainRequest",false,0],
  [23,"productIdRequest",11,"ltx.ProductIdRequest",false,0],
  [24,"outputPowerRequest",11,"ltx.OutputPowerRequest",false,0],
  [26,"presetModeRequest",11,"ltx.PresetModeRequest",false,0],
  [29,"ftswBrightnessRequest",11,"ltx.FtswBrightnessRequest",false,0],
  [30,"ftswVersionRequest",11,"ltx.FtswVersionRequest",false,0]
 ],
 "ltx.OutputPowerRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "ltx.OutputPowerStatus": [
  [1,"outputPower",14,"ProtobufOutputPower",false,-1]
 ],
 "ltx.ParameterRequest": [
  [1,"nodeId",9,"",false,-1], [2,"parameterId",9,"",false,-1]
 ],
 "ltx.ParameterStatus": [
  [1,"nodeId",9,"",false,-1], [2,"parameterId",9,"",false,-1],
  [3,"floatParameter",2,"",false,0], [4,"stringParameter",9,"",false,0],
  [5,"sint32Parameter",17,"",false,0], [6,"boolParameter",8,"",false,0]
 ],
 "ltx.PresetDataRequest": [
  [1,"presetIndex",13,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "ltx.PresetDataStatus": [
  [1,"presetIndex",13,"",false,-1], [2,"compressedPresetJson",12,"",false,-1]
 ],
 "ltx.PresetModeRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "ltx.PresetModeStatus": [
  [1,"presetMode",14,"ProtobufPresetMode",false,-1]
 ],
 "ltx.PresetNameStatus": [
  [1,"presetIndex",13,"",false,-1], [2,"presetName",9,"",false,-1]
 ],
 "ltx.PresetNamesRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "ltx.PresetNamesStatus": [
  [1,"presetName",9,"",true,-1]
 ],
 "ltx.PresetSavedStatus": [
  [1,"presetIndex",13,"",false,-1], [2,"presetName",9,"",false,-1]
 ],
 "ltx.ProductIdRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "ltx.ProductIdStatus": [
  [1,"productId",14,"ProtobufProductId",false,-1]
 ],
 "ltx.ReplaceNodeStatus": [
  [1,"nodeIdToReplace",9,"",false,-1], [2,"fenderIdToReplaceWith",9,"",false,-1]
 ],
 "ltx.SetParameterRequest": [
  [1,"nodeId",9,"",false,-1], [2,"parameterId",9,"",false,-1],
  [3,"floatParameter",2,"",false,0], [4,"stringParameter",9,"",false,0],
  [5,"sint32Parameter",17,"",false,0], [6,"boolParameter",8,"",false,0]
 ],
 "ltx.TunerStatus": [
  [1,"dummyField",8,"",false,-1], [2,"active",8,"",false,-1]
 ],
 "ltx.UnsupportedMessageStatus": [
  [1,"status",14,"ltx.ErrorType",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "ltx.UsbAndLineOutGainRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "ltx.UsbAndLineOutGainStatus": [
  [1,"valueDB",2,"",false,-1]
 ],
 "mp.AttenuatorRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "mp.AttenuatorStatus": [
  [1,"attenuatorIsOn",8,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "mp.ClearPresetStatus": [
  [1,"presetIndex",13,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "mp.ConnectionStatus": [
  [1,"isConnected",8,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "mp.ConnectionStatusRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "mp.CurrentPresetDataStatus": [
  [1,"compressedPresetJson",12,"",false,-1]
 ],
 "mp.CurrentPresetRequest": [
  [1,"requestPresetData",8,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "mp.CurrentPresetStatus": [
  [1,"presetIndex",13,"",false,-1], [2,"isDirty",8,"",false,-1],
  [3,"isAuditioning",8,"",false,-1], [4,"dummyField",8,"",false,-1]
 ],
 "mp.EqRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "mp.EqStatus": [
  [1,"dummyField",8,"",false,-1], [2,"eqPreset",14,"EqPreset",false,-1]
 ],
 "mp.FirmwareVersionRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "mp.FirmwareVersionStatus": [
  [1,"version",9,"",false,-1]
 ],
 "mp.Heartbeat": [
  [1,"dummyField",8,"",false,-1]
 ],
 "mp.InputPadRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "mp.InputPadStatus": [
  [1,"isOn",8,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "mp.LoadPresetRequest": [
  [1,"newPresetIndex",13,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "mp.LoopbackTest": [
  [1,"data",9,"",false,-1]
 ],
 "mp.ModalStatusMessage": [
  [1,"context",14,"mp.ModalContext",false,-1], [2,"state",14,"mp.ModalState",false,-1],
  [3,"dummyField",8,"",false,-1]
 ],
 "mp.ModifiableParameterRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "mp.ModifiableParameterStatus": [
  [1,"nodeId",9,"",false,-1], [2,"parameterId",9,"",false,-1]
 ],
 "mp.MoveNodeStatus": [
  [1,"nodeIdToMove",9,"",false,-1], [2,"nodeIdInsertLocation",9,"",false,-1]
 ],
 "mp.MovePresetStatus": [
  [1,"success",8,"",false,-1]
 ],
 "mp.MustangLtxAmpStatus": [
  [1,"responseType",14,"mp.ResponseType",false,-1],
  [2,"clearPresetStatus",11,"mp.ClearPresetStatus",false,0],
  [3,"connectionStatus",11,"mp.ConnectionStatus",false,0],
  [4,"currentPresetDataStatus",11,"mp.CurrentPresetDataStatus",false,0],
  [5,"currentPresetStatus",11,"mp.CurrentPresetStatus",false,0],
  [6,"firmwareVersionStatus",11,"mp.FirmwareVersionStatus",false,0],
  [7,"modifiableParameterStatus",11,"mp.ModifiableParameterStatus",false,0],
  [8,"moveNodeStatus",11,"mp.MoveNodeStatus",false,0],
  [9,"parameterStatus",11,"mp.ParameterStatus",false,0],
  [10,"presetDataStatus",11,"mp.PresetDataStatus",false,0],
  [11,"presetNameStatus",11,"mp.PresetNameStatus",false,0],
  [12,"presetNamesStatus",11,"mp.PresetNamesStatus",false,0],
  [13,"presetSavedStatus",11,"mp.PresetSavedStatus",false,0],
  [14,"productIdentificationStatus",11,"mp.ProductIdentificationStatus",false,0],
  [15,"replaceNodeStatus",11,"mp.ReplaceNodeStatus",false,0],
  [17,"unsupportedMessageStatus",11,"mp.UnsupportedMessageStatus",false,0],
  [18,"usbAudioGainInfoStatus",11,"mp.UsbAudioGainInfoStatus",false,0],
  [19,"eqStatus",11,"mp.EqStatus",false,0],
  [20,"tapTempoStatus",11,"mp.TapTempoStatus",false,0],
  [21,"tunerStatus",11,"mp.TunerStatus",false,0],
  [22,"attenuatorStatus",11,"mp.AttenuatorStatus",false,0],
  [23,"movePresetStatus",11,"mp.MovePresetStatus",false,0],
  [24,"muteDetectEnableStatus",11,"mp.MuteDetectEnableStatus",false,0],
  [25,"inputPadStatus",11,"mp.InputPadStatus",false,0]
 ],
 "mp.MustangLtxAppMessage": [
  [1,"toneRequest",11,"mp.MustangLtxToneRequest",false,0],
  [2,"ampStatus",11,"mp.MustangLtxAmpStatus",false,0],
  [3,"modalStatus",11,"mp.ModalStatusMessage",false,0],
  [4,"loopback",11,"mp.MustangLtxLoopback",false,0]
 ],
 "mp.MustangLtxLoopback": [
  [1,"responseType",14,"mp.ResponseType",false,-1],
  [2,"loopbackTest",11,"mp.LoopbackTest",false,-1]
 ],
 "mp.MustangLtxToneRequest": [
  [3,"connectionStatusRequest",11,"mp.ConnectionStatusRequest",false,0],
  [4,"currentPresetRequest",11,"mp.CurrentPresetRequest",false,0],
  [7,"firmwareVersionRequest",11,"mp.FirmwareVersionRequest",false,0],
  [8,"loadPresetRequest",11,"mp.LoadPresetRequest",false,0],
  [9,"modifiableParameterRequest",11,"mp.ModifiableParameterRequest",false,0],
  [11,"parameterRequest",11,"mp.ParameterRequest",false,0],
  [12,"presetDataRequest",11,"mp.PresetDataRequest",false,0],
  [14,"presetNamesRequest",11,"mp.PresetNamesRequest",false,0],
  [15,"productIdentificationRequest",11,"mp.ProductIdentificationRequest",false,0],
  [20,"setParameterRequest",11,"mp.SetParameterRequest",false,0],
  [22,"usbAudioGainInfoRequest",11,"mp.UsbAudioGainInfoRequest",false,0],
  [24,"heartbeat",11,"mp.Heartbeat",false,0], [25,"eqRequest",11,"mp.EqRequest",false,0],
  [28,"tapTempoRequest",11,"mp.TapTempoRequest",false,0],
  [31,"attenuatorRequest",11,"mp.AttenuatorRequest",false,0],
  [33,"muteDetectEnableRequest",11,"mp.MuteDetectEnableRequest",false,0],
  [35,"inputPadRequest",11,"mp.InputPadRequest",false,0]
 ],
 "mp.MuteDetectEnableRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "mp.MuteDetectEnableStatus": [
  [1,"enable",8,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "mp.ParameterRequest": [
  [1,"nodeId",9,"",false,-1], [2,"parameterId",9,"",false,-1]
 ],
 "mp.ParameterStatus": [
  [1,"nodeId",9,"",false,-1], [2,"parameterId",9,"",false,-1],
  [3,"floatParameter",2,"",false,0], [4,"stringParameter",9,"",false,0],
  [5,"sint32Parameter",17,"",false,0], [6,"boolParameter",8,"",false,0]
 ],
 "mp.PresetDataRequest": [
  [1,"presetIndex",13,"",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "mp.PresetDataStatus": [
  [1,"presetIndex",13,"",false,-1], [2,"compressedPresetJson",12,"",false,-1]
 ],
 "mp.PresetNameStatus": [
  [1,"presetIndex",13,"",false,-1], [2,"presetName",9,"",false,-1]
 ],
 "mp.PresetNamesRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "mp.PresetNamesStatus": [
  [1,"presetName",9,"",true,-1]
 ],
 "mp.PresetSavedStatus": [
  [1,"presetIndex",13,"",false,-1], [2,"presetName",9,"",false,-1]
 ],
 "mp.ProductIdentificationRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "mp.ProductIdentificationStatus": [
  [1,"id",9,"",false,-1]
 ],
 "mp.ReplaceNodeStatus": [
  [1,"nodeIdToReplace",9,"",false,-1], [2,"fenderIdToReplaceWith",9,"",false,-1]
 ],
 "mp.SetParameterRequest": [
  [1,"nodeId",9,"",false,-1], [2,"parameterId",9,"",false,-1],
  [3,"floatParameter",2,"",false,0], [4,"stringParameter",9,"",false,0],
  [5,"sint32Parameter",17,"",false,0], [6,"boolParameter",8,"",false,0]
 ],
 "mp.TapTempoRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "mp.TapTempoStatus": [
  [1,"dummyField",8,"",false,-1], [2,"bpm",13,"",false,-1]
 ],
 "mp.TunerStatus": [
  [1,"dummyField",8,"",false,-1], [2,"active",8,"",false,-1]
 ],
 "mp.UnsupportedMessageStatus": [
  [1,"status",14,"mp.ErrorType",false,-1], [2,"dummyField",8,"",false,-1]
 ],
 "mp.UsbAudioGainInfoRequest": [
  [1,"dummyField",8,"",false,-1]
 ],
 "mp.UsbAudioGainInfoStatus": [
  [1,"valueDB",2,"",false,-1], [2,"dummyField",8,"",false,-1]
 ]
}
}
'''
)
# SCHEMA-END
MSG, ENUM = SCHEMA.get('messages', {}), SCHEMA.get('enums', {})


if __name__ == '__main__':
    sys.exit(main())
