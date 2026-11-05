"""Minimal Preferences DataStore protobuf seed/check for a disposable rooted Android emulator.
The only encoded value types are bool (1) and string (5); other values are skipped during reads.
"""
from pathlib import Path
import sys

def varint(n):
    out = bytearray()
    while n > 127:
        out.append((n & 127) | 128); n >>= 7
    out.append(n)
    return bytes(out)

def field_bytes(number, value):
    return varint((number << 3) | 2) + varint(len(value)) + value

def encode_item(key, kind, value):
    if kind == 'string':
        encoded_value = field_bytes(5, value.encode())
    elif kind == 'bool':
        encoded_value = varint(1 << 3) + varint(bool(value))
    else:
        raise ValueError(kind)
    return field_bytes(1, field_bytes(1, key.encode()) + field_bytes(2, encoded_value))

def read_varint(data, pos):
    n = shift = 0
    while True:
        if pos >= len(data) or shift > 70: raise ValueError('bad protobuf varint')
        b = data[pos]; pos += 1
        n |= (b & 127) << shift
        if b < 128: return n, pos
        shift += 7

def fields(data):
    pos = 0
    while pos < len(data):
        key, pos = read_varint(data, pos)
        number, wire = key >> 3, key & 7
        if not number: raise ValueError('invalid protobuf field')
        if wire == 0:
            value, pos = read_varint(data, pos)
        elif wire == 2:
            length, pos = read_varint(data, pos)
            end = pos + length
            if end > len(data): raise ValueError('truncated protobuf field')
            value = data[pos:end]; pos = end
        elif wire in (1, 5):
            length = 8 if wire == 1 else 4
            value = data[pos:pos+length]; pos += length
        else:
            raise ValueError(f'unsupported protobuf wire type: {wire}')
        yield number, wire, value

def read_preferences(path):
    result = {}
    for num, wire, entry in fields(Path(path).read_bytes()):
        if num != 1 or wire != 2: continue
        entry_fields = list(fields(entry))
        key = next(v.decode() for n, w, v in entry_fields if n == 1 and w == 2)
        value = next(v for n, w, v in entry_fields if n == 2 and w == 2)
        vs = list(fields(value))
        if not vs: continue
        number, wire, value = vs[0]
        if number == 1 and wire == 0: result[key] = bool(value)
        elif number == 5 and wire == 2: result[key] = value.decode()
    return result

seed = [
    ('setupCompleted', 'bool', True),
    ('backgroundVideoQuality', 'string', 'P2160'),
    ('hidePlayerStatusBar', 'bool', True),
    ('appTheme', 'string', 'LIQUID_GLASS'),
    ('pureBlack', 'bool', False),
    ('coverTransition', 'string', 'FADE'),
]
expected = {
    'setupCompleted': True,
    'videoQuality': 'Q2160',
    'playerStatusBar': False,
    'appTheme': 'LIQUID_GLASS',
    'pureBlack': False,
    'coverTransition': 'FADE',
}

if sys.argv[1] == 'seed':
    Path(sys.argv[2]).write_bytes(b''.join(encode_item(*x) for x in seed))
    values = read_preferences(sys.argv[2])
    for key, _, value in seed:
        assert values[key] == value, (key, values)
    print('Seeded legacy settings:', sorted(values))
elif sys.argv[1] == 'check':
    values = read_preferences(sys.argv[2])
    for key, want in expected.items():
        if values.get(key, object()) != want:
            raise SystemExit(f'{key}: expected {want!r}, found {values.get(key)!r}')
    for key in ('backgroundVideoQuality', 'hidePlayerStatusBar'):
        if key in values: raise SystemExit(f'Legacy key {key} was not removed')
    print('Migrated and preserved settings:', expected)
else:
    raise SystemExit('Usage: seed_and_check_preferences.py seed|check FILE')
