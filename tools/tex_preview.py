"""Contact sheet for the mod's 16x16 textures.

Grid order matches the block model faces so the sheets are readable at a glance:

    +---------+---------+---------+---------+
    | 帆布顶  | 帆布侧  | 炮 顶   | 炮 底   |
    +---------+---------+---------+---------+
    | 炮 左右 | 炮 前后 | 材料清单| 蓝图与笔|
    +---------+---------+---------+---------+

Tiles that cannot be found are drawn as a red "MISSING" marker instead of silently
disappearing, so a renamed file is obvious.
"""
import os
import struct
import sys
import zlib

SCALE = 10
GAP = 6
COLS = 4

# (folder, file stem, human label)
LAYOUT = [
    ('block', 'schematic_table_top', 'table/top'),
    ('block', 'schematic_table_side', 'table/side'),
    ('block', 'schematicannon_top', 'cannon/top'),
    ('block', 'schematicannon_bottom', 'cannon/bottom'),
    ('block', 'schematicannon_left_right', 'cannon/side'),
    ('block', 'schematicannon_front_behind', 'cannon/front'),
    ('item', 'material_list', 'item/list'),
    ('item', 'schematic_and_quill', 'item/quill'),
]


def read_png(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', path
    pos, idat = 8, b''
    w = h = depth = ctype = None
    while pos < len(data):
        length, ctag = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        if ctag == b'IHDR':
            w, h, depth, ctype = struct.unpack('>IIBB', body[:10])
        elif ctag == b'IDAT':
            idat += body
        pos += 12 + length
    bpp = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * bpp
    out = bytearray()
    prev = bytearray(stride)
    p = 0
    for _ in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out += line
        prev = line
    if ctype == 6:
        px = out
    elif ctype == 2:
        px = bytearray()
        for i in range(w * h):
            px += out[i * 3:i * 3 + 3] + b'\xff'
    else:
        px = bytearray()
        for i in range(w * h):
            v = out[i * bpp]
            px += bytes((v, v, v, 255))
    return w, h, 4, bytes(px)


def blank():
    return bytes([0] * (16 * 16 * 4))


def write_png(path, w, h, rgba):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw += rgba[y * w * 4:(y + 1) * w * 4]

    def chunk(tag, body):
        return (struct.pack('>I', len(body)) + tag + body
                + struct.pack('>I', zlib.crc32(tag + body) & 0xFFFFFFFF))

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')
    open(path, 'wb').write(png)


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else 'schematicannon/src/main/resources/assets/schematicannon/textures'
    out_path = sys.argv[2] if len(sys.argv) > 2 else 'textures_sheet.png'
    tiles = []
    for sub, stem, label in LAYOUT:
        path = os.path.join(root, sub, stem + '.png')
        if os.path.isfile(path):
            w, h, ch, px = read_png(path)
            print('%-28s %dx%d' % (sub + '/' + stem, w, h))
        else:
            w = h = 16
            px = blank()
            print('%-28s MISSING' % (sub + '/' + stem))
            label += ' (missing)'
        tiles.append((w, h, px, label))

    cell = 16 * SCALE
    rows = (len(tiles) + COLS - 1) // COLS
    sheet_w = COLS * (cell + GAP) + GAP
    sheet_h = rows * (cell + GAP) + GAP
    sheet = bytearray([0x2B, 0x2B, 0x33, 0xFF] * (sheet_w * sheet_h))
    for idx, (w, h, px, label) in enumerate(tiles):
        cx = GAP + (idx % COLS) * (cell + GAP)
        cy = GAP + (idx // COLS) * (cell + GAP)
        for y in range(h * SCALE):
            for x in range(w * SCALE):
                sx, sy = x // SCALE, y // SCALE
                o = (sy * w + sx) * 4
                r, g, b = px[o], px[o + 1], px[o + 2]
                a = px[o + 3]
                if a < 24:  # checkerboard behind transparent pixels
                    v = 0x55 if ((sx + sy) & 1) == 0 else 0x77
                    r = g = b = v
                    a = 255
                d = ((cy + y) * sheet_w + (cx + x)) * 4
                sheet[d] = r
                sheet[d + 1] = g
                sheet[d + 2] = b
                sheet[d + 3] = a
    write_png(out_path, sheet_w, sheet_h, sheet)
    print('wrote', out_path, '%dx%d' % (sheet_w, sheet_h), '(%d tiles)' % len(tiles))


if __name__ == '__main__':
    main()
