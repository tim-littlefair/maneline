"""LZ4 block format, as used by the Micro Plus for preset JSON.

The amp sends a preset as an LZ4 *block* (no frame header, no size prefix): the
reply to the current-preset request starts with the token f1 06 and the literal
text {"nodeType":"preset",. Pure Python, so no extra dependency; presets are
about 1 KB compressed and 5 KB as text.

compress() is a small greedy compressor whose output any LZ4 decoder accepts.
It is not tuned for ratio; the amp only needs valid blocks.
"""

MIN_MATCH = 4
LAST_LITERALS = 5           # the format requires the last 5 bytes to be literals
MATCH_LIMIT = 12            # no match may start within the last 12 bytes
MAX_OFFSET = 65535


def _read_length(src, i, length):
    if length == 15:
        while True:
            if i >= len(src):
                raise ValueError('Truncated LZ4 length')
            byte = src[i]
            i += 1
            length += byte
            if byte != 255:
                break
    return length, i


def decompress(src, max_size=1 << 20):
    """Decompress one LZ4 block. Raises ValueError on malformed input."""
    out = bytearray()
    i = 0
    while i < len(src):
        token = src[i]
        i += 1
        literals, i = _read_length(src, i, token >> 4)
        if i + literals > len(src):
            raise ValueError('Truncated LZ4 literals')
        out += src[i:i + literals]
        i += literals
        if i == len(src):
            break                           # the last sequence has no match
        if i + 2 > len(src):
            raise ValueError('Truncated LZ4 offset')
        offset = src[i] | src[i + 1] << 8
        i += 2
        if offset == 0 or offset > len(out):
            raise ValueError('Invalid LZ4 match offset')
        length, i = _read_length(src, i, token & 15)
        length += MIN_MATCH
        if len(out) + length > max_size:
            raise ValueError('LZ4 output exceeds {} bytes'.format(max_size))
        start = len(out) - offset
        for k in range(length):            # byte by byte: matches may overlap
            out.append(out[start + k])
    return bytes(out)


def _write_length(out, length):
    length -= 15
    while length >= 255:
        out.append(255)
        length -= 255
    out.append(length)


def _sequence(out, literals, match_length=None, offset=None):
    lit = len(literals)
    token = min(lit, 15) << 4
    if match_length is not None:
        token |= min(match_length - MIN_MATCH, 15)
    out.append(token)
    if lit >= 15:
        _write_length(out, lit)
    out += literals
    if match_length is not None:
        out += bytes((offset & 255, offset >> 8))
        if match_length - MIN_MATCH >= 15:
            _write_length(out, match_length - MIN_MATCH)


def compress(data):
    """Compress bytes into one LZ4 block."""
    data = bytes(data)
    out = bytearray()
    table = {}
    anchor = 0
    i = 0
    end = len(data) - MATCH_LIMIT
    while i < end:
        key = data[i:i + MIN_MATCH]
        candidate = table.get(key)
        table[key] = i
        if candidate is not None and i - candidate <= MAX_OFFSET:
            length = MIN_MATCH
            limit = len(data) - LAST_LITERALS
            while i + length < limit and data[candidate + length] == data[i + length]:
                length += 1
            _sequence(out, data[anchor:i], length, i - candidate)
            i += length
            anchor = i
        else:
            i += 1
    _sequence(out, data[anchor:])
    return bytes(out)
