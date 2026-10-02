"""Offline isometric renderer for the Schematicannon block model.

Renders models/block/schematicannon.json with a simple painter's-algorithm isometric
projection so the proportions can be eyeballed without launching the game. This is a
*preview aid*, not a Minecraft renderer: it applies element rotations, sorts faces back
to front, and shades each face by its world-space normal. Textures are sampled at the
face centre colour (no UV mapping), which is enough to confirm silhouette and palette.

Usage:  python tools/model_preview.py [out.png]
"""
import json
import math
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "schematicannon")
MODEL = os.path.join(ASSETS, "models", "block", "schematicannon.json")

OUT_W, OUT_H = 640, 640
SS = 2  # supersample factor


# ------------------------------------------------------------------ png io ----

def read_png(path):
    d = open(path, "rb").read()
    pos = 8
    idat = b""
    w = h = nch = None
    while pos < len(d):
        ln = struct.unpack(">I", d[pos:pos + 4])[0]
        typ = d[pos + 4:pos + 8]
        data = d[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h, _bd, ct = struct.unpack(">IIBB", data[:10])
            nch = 3 if ct == 2 else 4
        elif typ == b"IDAT":
            idat += data
        pos += 12 + ln
    raw = zlib.decompress(idat)
    stride = w * nch
    out = []
    prev = bytearray(stride)
    p = 0
    for _y in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        for i in range(stride):
            a = line[i - nch] if i >= nch else 0
            b = prev[i]
            c = prev[i - nch] if i >= nch else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + ((a + b) >> 1)) & 255
            elif f == 4:
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out.append(bytes(line))
        prev = line
    return w, h, nch, out


def avg_color(path):
    """Average RGB of a texture, used as a flat stand-in for that face's material."""
    w, h, nch, rows = read_png(path)
    r = g = b = 0
    n = 0
    for y in range(h):
        row = rows[y]
        for x in range(w):
            i = x * nch
            r += row[i]
            g += row[i + 1]
            b += row[i + 2]
            n += 1
    return (r // n, g // n, b // n)


def write_png(path, w, h, rgb):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw += rgb[y * w * 3:(y + 1) * w * 3]

    def chunk(tag, body):
        return (struct.pack(">I", len(body)) + tag + body
                + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    open(path, "wb").write(png)


# -------------------------------------------------------------- geometry ------

AXES = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}


def rot(p, origin, axis, ang):
    """Rotate point p about an axis-aligned line through origin, as Minecraft does."""
    x, y, z = p
    ox, oy, oz = origin
    x, y, z = x - ox, y - oy, z - oz
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + ox, y + oy, z + oz)


def rot_dir(v, axis, ang):
    """Rotate a direction vector (no translation)."""
    return rot(v, (0, 0, 0), axis, ang)


FACES = {
    "north": ([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], (0, 0, -1)),
    "south": ([(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)], (0, 0, 1)),
    "west":  ([(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)], (-1, 0, 0)),
    "east":  ([(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)], (1, 0, 0)),
    "down":  ([(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], (0, -1, 0)),
    "up":    ([(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)], (0, 1, 0)),
}


def build_quads(model, colors):
    """Yield (depth, [(px,py) x4], rgb) for every visible face."""
    quads = []
    for el in model["elements"]:
        fr, to = el["from"], el["to"]
        r = el.get("rotation")
        for fname, (corners, normal) in FACES.items():
            spec = el["faces"].get(fname)
            if spec is None:
                continue
            key = spec["texture"]
            if key not in colors:
                continue
            pts = []
            for cx, cy, cz in corners:
                v = (fr[0] + (to[0] - fr[0]) * cx,
                     fr[1] + (to[1] - fr[1]) * cy,
                     fr[2] + (to[2] - fr[2]) * cz)
                if r:
                    v = rot(v, r["origin"], r["axis"], r["angle"])
                pts.append(v)
            nrm = rot_dir(normal, r["axis"], r["angle"]) if r else normal
            centre = tuple(sum(p[i] for p in pts) / 4.0 for i in range(3))
            quads.append((pts, nrm, centre, colors[key]))
    return quads


def project(p, scale, ox, oy):
    """Isometric view from the (+X, +Y, -Z) octant - the same corner the reference art is
    drawn from, so +X runs right-down, +Z runs left-down and +Y is up."""
    x, y, z = p
    sx = (x - z) * math.cos(math.radians(30)) * scale + ox
    sy = ((x + z) * math.sin(math.radians(30)) - y) * scale + oy
    return (sx, sy)


def shade(rgb, n):
    """Fake directional light so faces are distinguishable."""
    lx, ly, lz = -0.45, 0.8, -0.4
    d = n[0] * lx + n[1] * ly + n[2] * lz
    f = 0.62 + 0.38 * max(0.0, d)
    return tuple(min(255, int(c * f)) for c in rgb)


def fill_quad(buf, w, h, pts, rgb):
    ys = [p[1] for p in pts]
    y0, y1 = max(0, int(min(ys))), min(h - 1, int(max(ys)))
    for y in range(y0, y1 + 1):
        xs = []
        for i in range(4):
            x1, y1_ = pts[i]
            x2, y2_ = pts[(i + 1) % 4]
            if (y1_ <= y < y2_) or (y2_ <= y < y1_):
                t = (y - y1_) / (y2_ - y1_)
                xs.append(x1 + t * (x2 - x1))
        if len(xs) < 2:
            continue
        xa, xb = max(0, int(min(xs))), min(w - 1, int(max(xs)))
        for x in range(xa, xb + 1):
            i = (y * w + x) * 3
            buf[i:i + 3] = bytes(rgb)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "model_preview.png")
    model = json.load(open(MODEL, encoding="utf-8"))

    colors = {}
    for key, ref in model["textures"].items():
        if not ref.startswith("schematicannon:"):
            continue
        rel = ref.split(":", 1)[1]
        path = os.path.join(ASSETS, "textures", *rel.split("/")) + ".png"
        if os.path.isfile(path):
            colors["#" + key] = avg_color(path)

    quads = build_quads(model, colors)

    W, H = OUT_W * SS, OUT_H * SS
    buf = bytearray(b"\x24\x26\x2b" * (W * H))

    # Fit the 16-unit cube: the widest span on screen is (x+z) which peaks at 32.
    scale = 15.0 * SS
    ox, oy = W / 2.0, H * 0.30

    # Painter's algorithm. Depth must be measured along the VIEW direction, not a
    # hand-picked combination, or faces end up drawn in the wrong order and you
    # misjudge the model (a barrel can look "buried" when it is really in front).
    #
    # The isometric camera of project() looks from the (+1, +1, -1) octant: it sees +X, +Y
    # and -Z. A point is FARTHER when it is smaller along that view vector, so sort so the
    # smallest projection is painted first and the largest last (on top).
    VX, VY, VZ = 1.0, 1.0, -1.0

    def depth(q):
        c = q[2]
        return c[0] * VX + c[1] * VY + c[2] * VZ

    quads.sort(key=depth)

    for pts, nrm, _c, rgb in quads:
        scr = [project(p, scale, ox, oy) for p in pts]
        fill_quad(buf, W, H, scr, shade(rgb, nrm))

    # downsample
    final = bytearray(W // SS * H // SS * 3)
    for y in range(H // SS):
        for x in range(W // SS):
            r = g = b = 0
            for dy in range(SS):
                for dx in range(SS):
                    i = ((y * SS + dy) * W + (x * SS + dx)) * 3
                    r += buf[i]
                    g += buf[i + 1]
                    b += buf[i + 2]
            n = SS * SS
            j = (y * W // SS + x) * 3
            final[j:j + 3] = bytes((r // n, g // n, b // n))

    write_png(out, W // SS, H // SS, final)
    print(f"wrote {out}  ({W//SS}x{H//SS})  quads={len(quads)}  textures={len(colors)}")


if __name__ == "__main__":
    main()
