#!/usr/bin/env python3
"""Extract the protobuf message definitions embedded in Fender's device library.

The Fender Tone app ships libmustangdevice.so with serialized FileDescriptorProto
blobs for each amp family; the Micro Plus uses the "mp" family (package
fendermessage.mp, envelope mp/MustangLtxAppMessage.proto). This cuts the blobs out
of the binary and writes them as readable .proto-like text, one file per proto.
Static analysis only: the library is read as bytes, never loaded or executed.

  unzip -j config.arm64_v8a.apk lib/arm64-v8a/libmustangdevice.so   # from the app's XAPK
  python -I extract_fender_protos.py libmustangdevice.so out/ mp/

Needs the protobuf package (pip install protobuf). See docs/MUSTANG_MICRO_PLUS_PROTOCOL.md.
"""
import os, re, sys
from google.protobuf import descriptor_pb2

def varint(buf, i):
    v = s = 0
    while True:
        b = buf[i]; i += 1
        v |= (b & 0x7f) << s; s += 7
        if b < 0x80: return v, i

def blob_end(buf, start):
    '''Walk protobuf fields from start while they look like FileDescriptorProto fields (1..14).'''
    i = start
    while i < len(buf):
        try:
            tag, j = varint(buf, i)
        except IndexError:
            break
        field, wire = tag >> 3, tag & 7
        if not 1 <= field <= 14 or wire not in (0, 2):
            break
        if wire == 0:
            _, j = varint(buf, j)
        else:
            n, j = varint(buf, j)
            if j + n > len(buf): break
            j += n
        i = j
    return i

data = open(sys.argv[1], 'rb').read()
out, prefix = sys.argv[2], (sys.argv[3] if len(sys.argv) > 3 else '')
os.makedirs(out, exist_ok=True)
found = {}
for m in re.finditer(rb'\x0a([\x05-\x7f])([A-Za-z0-9_/]+\.proto)', data):
    name = m.group(2).decode()
    if len(name) != m.group(1)[0] or not name.startswith(prefix) or name in found:
        continue
    end = blob_end(data, m.start())
    fd = descriptor_pb2.FileDescriptorProto()
    try:
        fd.ParseFromString(data[m.start():end])
    except Exception:
        continue
    if fd.name == name and (fd.message_type or fd.enum_type):
        found[name] = fd
        open(os.path.join(out, name.replace('/', '__') + '.desc.txt'), 'w').write(str(fd))
print(len(found), 'descriptors:', ', '.join(sorted(found)[:8]), '...')

TYPES = {1: 'double', 2: 'float', 3: 'int64', 4: 'uint64', 5: 'int32', 8: 'bool', 9: 'string',
         12: 'bytes', 13: 'uint32', 17: 'sint32', 18: 'sint64'}
def show(fd):
    lines = ['// ' + fd.name + '  package ' + fd.package]
    for e in fd.enum_type:
        lines.append('enum {} {{ {} }}'.format(e.name, ', '.join('{}={}'.format(v.name, v.number) for v in e.value)))
    for msg in fd.message_type:
        lines.append('message ' + msg.name + ' {')
        oneofs = [o.name for o in msg.oneof_decl]
        for f in sorted(msg.field, key=lambda f: f.number):
            t = f.type_name.split('.')[-1] if f.type_name else TYPES.get(f.type, f.type)
            oo = ' [oneof {}]'.format(oneofs[f.oneof_index]) if f.HasField('oneof_index') else ''
            lines.append('  {} {} = {};{}'.format(t, f.name, f.number, oo))
        for nested in msg.enum_type:
            lines.append('  enum {} {{ {} }}'.format(nested.name, ', '.join('{}={}'.format(v.name, v.number) for v in nested.value)))
        lines.append('}')
    return '\n'.join(lines)
for name in sorted(found):
    open(os.path.join(out, name.replace('/', '__')), 'w').write(show(found[name]) + '\n')
