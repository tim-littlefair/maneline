#!/usr/bin/env python3
"""Build the compact message schema that both probes embed.

Input: the output directory of scripts/extract_fender_protos.py (its *.desc.txt files).
Output: replaces the text between the SCHEMA-BEGIN and SCHEMA-END markers in
index.html and fender_amp_probe.py with one JSON object, wrapped into short lines. Only
the parts the probes need are kept (see SENT below):

  {"messages": {"gt.FenderMessage": [[number, name, type, typeName, repeated, oneof], ...], ...},
   "enums":    {"gt.PacketType": {"0": "LOAD_PRESET", ...}, ...}}

type is the protobuf FieldDescriptorProto type number (2 float, 8 bool, 9 string,
11 message, 12 bytes, 14 enum, 17 sint32, ...); oneof is the field's oneof index, -1 if
none. Names are package-qualified with the "fendermessage." prefix dropped. Needs the
protobuf package.

  python -I scripts/extract_fender_protos.py libmustangdevice.so out/
  python -I amp-probe/build_schema.py out/
"""
import glob
import json
import os
import re
import sys

from google.protobuf import descriptor_pb2, text_format

FAMILIES = ('gt', 'ltx', 'mp')

# Only what the probes need is embedded (interoperability: no more than necessary):
# everything the amp can send back (to decode replies and to find private fields), and of
# the requests only those the probes send. Keep these lists in step with index.html and
# fender_amp_probe.py; tests/test_core.js checks that every request the code sends is here.
SENT = {
    'gt': {
        'toneSync',
        'syncCurrentPresetToTone',
        'loadPresetIfValid',
        'setDspUnitParameter',
        'getDspUnitParameter',
        'retrievePreset',
        'loopbackTest',
        'btStatusRequest',
        'globalEQSync',
        'globalFXAssignSync',
        'globalFXAssignSyncMgt7',
        'bpmStatusRequest',
        'globalBpmModeStatusRequest',
        'setlistManagerSync',
    },
    'mp': {
        'firmwareVersionRequest',
        'presetNamesRequest',
        'heartbeat',
        'loadPresetRequest',
        'parameterRequest',
        'presetDataRequest',
        'setParameterRequest',
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
    },
    'ltx': {
        'firmwareVersionRequest',
        'presetNamesRequest',
        'heartbeat',
        'loadPresetRequest',
        'parameterRequest',
        'presetDataRequest',
        'setParameterRequest',
        'currentPresetRequest',
        'productIdRequest',
        'usbAndLineOutGainRequest',
        'outputPowerRequest',
        'presetModeRequest',
        'ftswBrightnessRequest',
        'ftswVersionRequest',
        'connectionStatusRequest',
    },
}
# GT has one flat envelope for both directions; these are the kinds the amp sends back.
# Left out: requests the probe doesn't send, and the amp's own screen (views, keyboard,
# pedal and setlist menus). An amp that sends one anyway shows up by number with raw bytes.
GT_REPLY = re.compile(r'(Status|Response|Begin|End|MTU|Complete|Failed|Message|JSON|Count|Data)$')
ENVELOPES = {
    'gt': 'gt.FenderMessage',
    'ltx': 'ltx.MustangLtxToneMessage',
    'mp': 'mp.MustangLtxAppMessage',
}
REQUESTS = {'ltx': 'ltx.MustangLtxToneRequest', 'mp': 'mp.MustangLtxToneRequest'}
HERE = os.path.dirname(os.path.abspath(__file__))
WIDTH = 96
TARGETS = {  # file: (start marker, end marker, how the JSON text is written there)
    os.path.join(HERE, 'index.html'): ('/*SCHEMA-BEGIN*/', '/*SCHEMA-END*/', lambda t: t),
    os.path.join(HERE, 'fender_amp_probe.py'): (
        '# SCHEMA-BEGIN\n',
        '\n# SCHEMA-END',
        lambda t: "SCHEMA = json.loads(\n    r'''\n" + t + "\n'''\n)",
    ),
}


def short(name):
    return re.sub(r'^\.?(fendermessage\.)?', '', name)


def collect(directory):
    messages, enums = {}, {}
    for path in sorted(glob.glob(os.path.join(directory, '*.desc.txt'))):
        fd = text_format.Parse(open(path).read(), descriptor_pb2.FileDescriptorProto())
        package = short(fd.package) if fd.package else fd.name.split('/')[0]
        if package not in FAMILIES:
            continue

        def add_enum(prefix, e):
            enums[prefix + e.name] = {str(v.number): v.name for v in e.value}

        def add_message(prefix, m):
            full = prefix + m.name
            messages[full] = [
                [
                    f.number,
                    f.name,
                    f.type,
                    short(f.type_name),
                    f.label == 3,
                    f.oneof_index if f.HasField('oneof_index') else -1,
                ]
                for f in sorted(m.field, key=lambda f: f.number)
            ]
            for e in m.enum_type:
                add_enum(full + '.', e)
            for n in m.nested_type:
                add_message(full + '.', n)

        for e in fd.enum_type:
            add_enum(package + '.', e)
        for m in fd.message_type:
            add_message(package + '.', m)
    return {'messages': messages, 'enums': enums}


def pack(items, indent):
    """items joined by commas, as many per line as fit in WIDTH."""
    lines, line = [], ''
    for i, item in enumerate(items):
        item += ',' if i < len(items) - 1 else ''
        if line and len(indent) + len(line) + 1 + len(item) > WIDTH:
            lines.append(indent + line)
            line = item
        else:
            line = f'{line} {item}' if line else item
    if line:
        lines.append(indent + line)
    return lines


def layout(schema):
    """Valid JSON (and a valid JavaScript literal), one message or enum per block."""
    compact = lambda v: json.dumps(v, separators=(',', ':'))
    out = []
    for key, closing in (('enums', '},'), ('messages', '}')):
        out.append(f'"{key}": {{')
        names = sorted(schema[key])
        for i, name in enumerate(names):
            value = schema[key][name]
            items = (
                [compact(f) for f in value]
                if key == 'messages'
                else [f'{compact(k)}:{compact(v)}' for k, v in value.items()]
            )
            brackets = '[]' if key == 'messages' else '{}'
            end = brackets[1] + (',' if i < len(names) - 1 else '')
            out.append(f' {compact(name)}: {brackets[0]}')
            out += pack(items, '  ') + [' ' + end]
        out.append(closing)
    return '{\n' + '\n'.join(out) + '\n}'


def prune(schema):
    """Keep the envelopes' needed fields and only the types and enums they reach."""
    messages, enums = schema['messages'], schema['enums']
    gt = ENVELOPES['gt']
    messages[gt] = [
        f
        for f in messages[gt]
        if f[1] in SENT['gt'] or (GT_REPLY.search(f[1]) and not f[1].endswith('CmdMessage'))
    ]
    for family, request in REQUESTS.items():
        messages[request] = [f for f in messages[request] if f[1] in SENT[family]]
    keep_m, keep_e, todo = set(), set(), list(ENVELOPES.values())
    while todo:
        name = todo.pop()
        if name in keep_m or name not in messages:
            continue
        keep_m.add(name)
        family = name.split('.')[0]
        for f in messages[name]:
            ref = f[3]
            pool = messages if f[2] == 11 else enums if f[2] == 14 else None  # 11 message, 14 enum
            if pool is None:
                continue
            ref = ref if ref in pool else f'{family}.{ref}'  # names may come without the family
            (todo.append(ref) if pool is messages else keep_e.add(ref))
    return {
        'messages': {k: v for k, v in messages.items() if k in keep_m},
        'enums': {k: v for k, v in enums.items() if k in keep_e},
    }


def main():
    full = collect(sys.argv[1])
    print(f"extracted: {len(full['messages'])} messages, {len(full['enums'])} enums")
    schema = prune(full)
    text = layout(schema)
    assert json.loads(text) == schema and "'''" not in text and '\\' not in text
    print(f"{len(schema['messages'])} messages, {len(schema['enums'])} enums, {len(text)} bytes")
    for path, (begin, end, form) in TARGETS.items():
        source = open(path, encoding='utf-8').read()
        a, b = source.index(begin) + len(begin), source.index(end)
        open(path, 'w', encoding='utf-8').write(source[:a] + form(text) + source[b:])
        print('updated', os.path.relpath(path))


if __name__ == '__main__':
    main()
