"""Generate the 4 new 16x16 sprites used by the Schematicannon 3D model.

Palette is lifted verbatim from the existing hand-refined cannon sprites so the
new parts sit next to them without clashing:

    outline ink      #120C05
    wood hi/mid/lo   #A07E4E / #83623D / #61482B   (+ table wood #755E3C / #55442A)
    metal hi/2/mid   #525152 / #4B4B4B / #444242
    metal dark/ink   #3D3C3F / #2A282A
    stone hi/mid/lo  #9E9E9E / #7A7A7A / #676767
    brass            #A9A93F-ish -> reuse table_top brass #4A5A8A is blue; use #8C7A3C

Writes straight into the mod's texture folder. No third-party deps: the PNG is
hand-encoded with zlib + struct, same technique as tools/tex_preview.py.
"""
import os
import struct
import zlib

OUT = os.path.join('src', 'main', 'resources', 'assets', 'schematicannon',
                   'textures', 'block')

# ----------------------------------------------------------------- palette ----
INK      = (0x12, 0x0C, 0x05)
WOOD_HI  = (0xA8, 0x82, 0x4E)
WOOD_MID = (0x78, 0x59, 0x35)
WOOD_LO  = (0x50, 0x3B, 0x21)
WOOD_DP  = (0x35, 0x27, 0x15)
WOOD_HI2 = (0xB6, 0x90, 0x5C)
WOOD_MID2 = (0x87, 0x66, 0x3D)
WOOD_LO2 = (0x5B, 0x43, 0x27)

WOOD_TOP = [
    list('HHHHHHHHHHHHHHHH'),
    list('mmmmmmmmmmmmmmmm'),
    list('mmmmmmmmmmmmmmmm'),
    list('mmmmmmmmmmmmmmmm'),
    list('llllllllllllllll'),  # seam
    list('HHHHHHHHHHHHHHHH'),
    list('mmmmmmmmmmmmmmmm'),
    list('mmmmmmmmmmmmmmmm'),
    list('mmmmmmmmmmmmmmmm'),
    list('llllllllllllllll'),  # seam
    list('HHHHHHHHHHHHHHHH'),
    list('mmmmmmmmmmmmmmmm'),
    list('mmmmmmmmmmmmmmmm'),
    list('mmmmmmmmmmmmmmmm'),
    list('llllllllllllllll'),  # seam
    list('llllllllllllllll'),  # shadow edge, kept at the seam tone so the tile does not
                              # end in a hard black band once the icon border is gone
]
WOOD_SIDE = [
    list('mmmmmmmmmmmmmmmm'),
    list('mmmmmmmmmmmmmmmm'),
    list('llllllllllllllll'),
    list('mmmmmmmmmmmmmmmm'),
    list('mmmmmmmmmmmmmmmm'),
    list('mmmmmmmmmmmmmmmm'),
    list('hhhhhhhhhhhhhhhh'),  # brighter plank band
    list('hhhhhhhhhhhhhhhh'),
    list('llllllllllllllll'),
    list('mmmmmmmmmmmmmmmm'),
    list('mmmmmmmmmmmmmmmm'),
    list('mmmmmmmmmmmmmmmm'),
    list('llllllllllllllll'),
    list('mmmmmmmmmmmmmmmm'),
    list('llllllllllllllll'),
    list('llllllllllllllll'),
]

METAL_HI  = (0x5C, 0x5B, 0x5E)
METAL_MID = (0x42, 0x41, 0x44)
METAL_LO  = (0x2E, 0x2D, 0x30)
METAL_DP  = (0x1A, 0x19, 0x1C)
METAL_EDGE = (0x6A, 0x69, 0x6C)

METAL = [
    list('hhhhhhhhhhhhhhhh'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('llllllllllllllll'),
    list('llllllllllllllll'),
]

STONE_HI  = (0xA8, 0xA8, 0xA8)
STONE_MID = (0x7A, 0x7A, 0x7A)
STONE_LO  = (0x5B, 0x5B, 0x5B)
STONE_DP  = (0x45, 0x45, 0x45)

STONE = [
    list('hhhhhhhhhhhhhhhh'),
    list('hmmmmmmmmmmmmmmm'),
    list('hmmmmmmmmmmmmmmm'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmllmmmmmmmll'),
    list('hmmmmllmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmll'),
    list('hmmmmmmmmmmmmmmm'),
    list('hmmmmmmmmmmmmmmm'),
    list('llllllllllllllll'),
]

BRASS_HI  = (0xC8, 0xA9, 0x4E)
BRASS_MID = (0x9A, 0x7E, 0x33)
BRASS_LO  = (0x6E, 0x59, 0x22)
BRASS_DP  = (0x3A, 0x2F, 0x12)


def build_shaft_side():
    """The dark squarish camera panel seen on the barrel's top face: a lit frame
    with a big recessed dark window."""
    g = [list('llllllllllllllll') for _ in range(16)]
    for y in range(2, 14):
        for x in range(2, 14):
            window = 4 <= x <= 11 and 4 <= y <= 11
            g[y][x] = 'k' if window else ('b' if (x < 3 or x > 12 or y < 3 or y > 12) else 'b')
    # brass frame tone on the border ring, dark window inside, 1px ink edge
    for y in range(2, 14):
        for x in range(2, 14):
            if 4 <= x <= 11 and 4 <= y <= 11:
                g[y][x] = 'k'
            else:
                g[y][x] = 'c' if (x + y) % 5 else 'b'
    for x in range(1, 15):
        g[1][x] = 'd'
        g[14][x] = 'd'
    for y in range(1, 15):
        g[y][1] = 'd'
        g[y][14] = 'd'
    return g


SHAFT_TOP = [
    list('llllllllllllllll'),
    list('lddddddddddddddl'),
    list('ldbbbbbbbbbbbddl'),
    list('ldbkkkkkkkkkkbdl'),
    list('ldbkkkkkkkkkkbdl'),
    list('ldbkkkkkkkkkkbdl'),
    list('ldbkkkkkkkkkkbdl'),
    list('ldbkkkkkkkkkkbdl'),
    list('ldbkkkkkkkkkkbdl'),
    list('ldbkkkkkkkkkkbdl'),
    list('ldbkkkkkkkkkkbdl'),
    list('ldbkkkkkkkkkkbdl'),
    list('ldbbbbbbbbbbbddl'),
    list('lddddddddddddddl'),
    list('llllllllllllllll'),
    list('llllllllllllllll'),
]

SYMBOLS = {
    'o': INK,
    'H': WOOD_HI,  'h': WOOD_HI,  'm': WOOD_MID, 'l': WOOD_LO,  'd': WOOD_DP,
    'J': WOOD_HI2, 'K': WOOD_MID2, 'L': WOOD_LO2,
    'H2': METAL_HI, 'h2': METAL_HI, 'm2': METAL_MID, 'l2': METAL_LO,
    'd2': METAL_DP, 'e2': METAL_EDGE,
    's': STONE_HI, 'S': STONE_MID, 'z': STONE_LO, 'Z': STONE_DP,
    'c': BRASS_HI, 'b': BRASS_MID, 'n': BRASS_LO, 'B': BRASS_DP,
    'k': (0x0A, 0x0A, 0x0C),
}
# one-letter aliases actually used by the grids above
ALIAS = {
    'h': METAL_HI, 'm': METAL_MID, 'l': METAL_LO, 'd': METAL_DP,
}


def resolve(ch, mode):
    if ch == 'o':
        return INK
    if mode == 'wood':
        return {'H': WOOD_HI, 'h': WOOD_HI2, 'm': WOOD_MID, 'l': WOOD_LO, 'd': WOOD_DP}[ch]
    if mode == 'metal':
        return {'h': METAL_HI, 'm': METAL_MID, 'l': METAL_LO, 'd': METAL_DP}[ch]
    if mode == 'stone':
        return {'h': STONE_HI, 'm': STONE_MID, 'l': STONE_LO, 'd': STONE_DP}[ch]
    if mode == 'shaft':
        return {'l': METAL_LO, 'd': METAL_DP, 'b': BRASS_MID, 'c': BRASS_HI, 'k': (0x0A, 0x0A, 0x0C)}[ch]
    raise KeyError(ch)


def outline(grid, mode):
    """Resolve the sprite to RGB.

    Note: this used to wrap every sprite in a 1px ink border, copying the one-item-icon
    convention. That is wrong for a *block* texture. An item sprite is viewed once, so a
    single dark frame reads as a neat icon edge. A block face is tiled by the world and by
    ambient occlusion, so the same border turns into a dark grid of seams across the whole
    machine - and on the grey metal and stone parts a dark *brown* border (#120C05) also
    clashes outright.

    Each grid below already carries its own edge shading (the 'h' first column / 'l' last
    column), which is the right way for a block: the light comes from the material, not from
    a drawn frame. So this function is now a plain resolve with no forced border.
    """
    out = []
    for y in range(16):
        row = []
        for x in range(16):
            row.append(resolve(grid[y][x], mode))
        out.append(row)
    return out


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


def flatten(rows):
    px = bytearray()
    for row in rows:
        for r, g, b in row:
            px += bytes((r, g, b, 0xFF))
    return bytes(px)


JOBS = [
    ('schematicannon_wood',       WOOD_TOP,   'wood'),
    ('schematicannon_wood_side',  WOOD_SIDE,  'wood'),
    ('schematicannon_metal',      METAL,      'metal'),
    ('schematicannon_metal_shaft', SHAFT_TOP, 'shaft'),
    ('schematicannon_stone',      STONE,      'stone'),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, grid, mode in JOBS:
        rows = outline(grid, mode)
        path = os.path.join(OUT, name + '.png')
        write_png(path, 16, 16, flatten(rows))
        print('wrote %-34s 16x16' % os.path.join('textures/block', name + '.png'))


if __name__ == '__main__':
    main()
