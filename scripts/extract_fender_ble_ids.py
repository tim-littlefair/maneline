#!/usr/bin/env python3
"""List the Bluetooth LE UUIDs (and USB ids next to them) in Fender's device library.

libmustangdevice.so from the Fender Tone app keeps its service and characteristic
UUIDs as 16-byte values in Windows GUID byte order (first three groups little-endian),
some also in plain big-endian order; they are not stored as text. This finds the
known request characteristic and prints every UUID-shaped value near it.
Static analysis only: the library is read as bytes, never loaded or executed.

  unzip -j config.arm64_v8a.apk lib/arm64-v8a/libmustangdevice.so   # from the app's XAPK
  python -I extract_fender_ble_ids.py libmustangdevice.so

See docs/FENDER_BLE_PROTOCOLS.md.
"""
import struct
import sys
import uuid

REQUEST = uuid.UUID('820a7e34-4e0a-4f90-8520-04ebce35a3a1')
KNOWN = {
    '90559580-b707-11ee-acb1-7b7e30f1af54': 'service: Mustang Micro Plus ("mp" messages)',
    '90559580-b707-11ee-acb1-7b7e30f12dcd': 'service: probably Mustang LTX ("ltx" messages)',
    '82107b48-5a47-4067-bcc1-319844c8f1e8': 'service: Mustang GT / GTX / Rumble ("gt" messages)',
    'e8f1c844-9831-c1bc-6740-475a487b1082': 'the GT service byte-reversed, as the amp advertises it',
    '820a7e34-4e0a-4f90-8520-04ebce35a3a1': 'characteristic: requests (app -> amp)',
    '1017adcc-dcbc-4387-a59f-2546b2ea5bb0': 'characteristic: responses (amp -> app, notify)',
}
WINDOW = 0x1000


def plausible(value):
    """An RFC 4122 UUID: variant bits 10, version 1-5."""
    return value.variant == uuid.RFC_4122 and value.version in (1, 2, 3, 4, 5)


def main():
    data = open(sys.argv[1], 'rb').read()
    anchor = data.find(REQUEST.bytes_le)
    if anchor < 0:
        sys.exit('The request characteristic was not found; the layout has changed.')
    start, end = max(0, anchor - WINDOW) & ~15, min(len(data), anchor + WINDOW)
    seen = set()
    for offset in range(start, end - 15):
        chunk = data[offset:offset + 16]
        for order, value in (('guid', uuid.UUID(bytes_le=chunk)), ('big-endian', uuid.UUID(bytes=chunk))):
            text = str(value)
            if text in KNOWN and (text, order) not in seen:
                seen.add((text, order))
                print(f'{offset:#08x}  {order:10}  {text}  {KNOWN[text]}')
    # Unknown UUID-shaped values are printed too, but only at 16-byte alignment, where the
    # known ones sit; anything else in this window is float tables and code constants.
    for offset in range(start, end - 15, 16):
        value = uuid.UUID(bytes_le=data[offset:offset + 16])
        if str(value) not in KNOWN and plausible(value) and data[offset + 8] & 0xc0 == 0x80:
            print(f'{offset:#08x}  guid?       {value}  (unknown, check by hand)')
    # USB vendor/product pairs (vendor 0x1ed8 = Fender Musical Instruments) near the UUIDs
    for offset in range(start, end - 3, 2):
        vid, pid = struct.unpack_from('<HH', data, offset)
        if vid == 0x1ed8:
            print(f'{offset:#08x}  usb         {vid:04x}:{pid:04x}')


if __name__ == '__main__':
    main()
