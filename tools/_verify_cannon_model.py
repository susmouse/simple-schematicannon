"""Verify the Schematicannon block/item models:

  1. JSON parses (both files)
  2. Every element's ROTATED aabb stays inside [0..16] on x/y/z
  3. Every #texture reference resolves (defined in `textures` + PNG exists on disk)
     and every generated PNG is exactly 16x16
  4. Every face object declares a `texture`

Read-only. Exits non-zero on any failure.
"""
import json
import math
import os
import struct
import sys

ROOT = os.path.join('src', 'main', 'resources', 'assets', 'schematicannon')
BLOCK = os.path.join(ROOT, 'models', 'block', 'schematicannon.json')
ITEM = os.path.join(ROOT, 'models', 'item', 'schematicannon.json')
TEXDIR = os.path.join(ROOT, 'textures')

GENERATED = [
    'schematicannon_wood',
    'schematicannon_wood_side',
    'schematicannon_metal',
    'schematicannon_metal_shaft',
    'schematicannon_stone',
]

FAILS = []


def fail(msg):
    FAILS.append(msg)
    print('  !! ' + msg)


def rotate_point(p, origin, axis, angle_deg):
    x, y, z = p
    ox, oy, oz = origin
    a = math.radians(angle_deg)
    c, s = math.cos(a), math.sin(a)
    dx, dy, dz = x - ox, y - oy, z - oz
    if axis == 'x':
        return (ox + dx, oy + dy * c - dz * s, oz + dy * s + dz * c)
    if axis == 'y':
        return (ox + dx * c + dz * s, oy + dy, oz - dx * s + dz * c)
    return (ox + dx * c - dy * s, oy + dx * s + dy * c, oz + dz)


def element_aabb(el):
    f, t = el['from'], el['to']
    pts = [(f[0] if i & 1 else t[0], f[1] if i & 2 else t[1], f[2] if i & 4 else t[2])
           for i in range(8)]
    rot = el.get('rotation')
    if rot:
        pts = [rotate_point(p, rot['origin'], rot['axis'], rot['angle']) for p in pts]
    return ([min(p[i] for p in pts) for i in range(3)],
            [max(p[i] for p in pts) for i in range(3)])


def png_size(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG: ' + path
    pos = 8
    while pos < len(data):
        length, tag = struct.unpack('>I4s', data[pos:pos + 8])
        if tag == b'IHDR':
            w, h = struct.unpack('>II', data[pos + 8:pos + 16])
            return w, h
        pos += 12 + length
    raise AssertionError('no IHDR in ' + path)


# ---------------------------------------------------------------- 1. parse ----
print('=' * 74)
print('CHECK 1 — JSON parses')
print('=' * 74)
model = item = None
for label, path in (('block', BLOCK), ('item', ITEM)):
    raw = open(path, 'rb').read()
    if raw[:3] == b'\xef\xbb\xbf':
        fail('%s: file has a UTF-8 BOM' % path)
    try:
        obj = json.loads(raw.decode('utf-8'))
        print('  OK  %-5s %s  (%d bytes, valid UTF-8 JSON)' % (label, path, len(raw)))
    except Exception as exc:
        fail('%s: %s' % (path, exc))
        continue
    if label == 'block':
        model = obj
    else:
        item = obj

if model is None or item is None:
    print('\nABORT: model did not parse')
    sys.exit(1)

print('  item model parent = %s' % item.get('parent'))

# ------------------------------------------------------------ 2. bbox [0..16] --
print()
print('=' * 74)
print('CHECK 2 — rotated AABB of every element inside [0..16]')
print('=' * 74)
gmn = [1e9] * 3
gmx = [-1e9] * 3
for i, el in enumerate(model['elements']):
    for key in ('from', 'to'):
        for v in el[key]:
            if v < 0 or v > 16:
                fail('element %d (%s): raw %s=%s outside [0..16]' % (i, el.get('name'), key, el[key]))
    mn, mx = element_aabb(el)
    for k in range(3):
        gmn[k] = min(gmn[k], mn[k])
        gmx[k] = max(gmx[k], mx[k])
    bad = [a for a, lo, hi in zip('xyz', mn, mx)
           if lo < -1e-6 or hi > 16 + 1e-6]
    tag = 'OUT OF RANGE on %s' % ','.join(bad) if bad else 'ok'
    rot = ''
    if el.get('rotation'):
        r = el['rotation']
        if r['angle'] % 22.5:
            fail('element %d (%s): angle %s not a multiple of 22.5' % (i, el.get('name'), r['angle']))
        if r['axis'] not in ('x', 'y', 'z'):
            fail('element %d (%s): bad axis %s' % (i, el.get('name'), r['axis']))
        rot = '  rot=%s %s@%s' % (r['axis'], r['angle'], r['origin'])
    print('  [%2d] %-20s x[%7.4f,%7.4f] y[%7.4f,%7.4f] z[%7.4f,%7.4f]  %s%s'
          % (i, el.get('name', '?'), mn[0], mx[0], mn[1], mx[1], mn[2], mx[2], tag, rot))
    if bad:
        fail('element %d (%s): rotated aabb escapes [0..16]' % (i, el.get('name')))

print('  ' + '-' * 70)
print('  MODEL BBOX   min=(%.4f, %.4f, %.4f)   max=(%.4f, %.4f, %.4f)'
      % (gmn[0], gmn[1], gmn[2], gmx[0], gmx[1], gmx[2]))
print('  MODEL SIZE   (%.4f, %.4f, %.4f)  — fits in 16^3 cube: %s'
      % (gmx[0] - gmn[0], gmx[1] - gmn[1], gmx[2] - gmn[2],
         all(gmx[k] - gmn[k] <= 16 + 1e-6 for k in range(3))))
print('  ELEMENT COUNT %d' % len(model['elements']))

# --------------------------------------------------- 3 + 4. textures / faces ---
print()
print('=' * 74)
print('CHECK 3/4 — texture refs resolve, PNGs exist and are 16x16, all faces textured')
print('=' * 74)
declared = model.get('textures', {})
print('  declared:')
for k, v in declared.items():
    print('    #%-10s -> %s' % (k, v))

referenced = set()
for i, el in enumerate(model['elements']):
    faces = el.get('faces', {})
    if not faces:
        fail('element %d (%s): has no faces at all' % (i, el.get('name')))
    for fname, fobj in faces.items():
        if fname not in ('down', 'up', 'north', 'south', 'east', 'west'):
            fail('element %d (%s): bogus face key %r' % (i, el.get('name'), fname))
        if 'texture' not in fobj:
            fail('element %d (%s) face %s: NO TEXTURE' % (i, el.get('name'), fname))
            continue
        tex = fobj['texture']
        if not isinstance(tex, str) or not tex:
            fail('element %d (%s) face %s: bad texture %r' % (i, el.get('name'), fname, tex))
            continue
        referenced.add(tex)
        if tex.startswith('#'):
            if tex[1:] not in declared:
                fail('element %d (%s) face %s: %s undefined in textures block'
                     % (i, el.get('name'), fname, tex))

print('  referenced (resolved through the textures block):')
resolved_files = set()
for tex in sorted(referenced):
    if tex.startswith('#'):
        target = declared.get(tex[1:])
    else:
        target = tex
    if target is None:
        continue
    if ':' not in target:
        fail('%s resolves to %r which is not a namespaced id' % (tex, target))
        continue
    ns, path = target.split(':', 1)
    if not path.startswith('block/'):
        fail('%s -> %s : not under block/' % (tex, target))
        continue
    png = os.path.join(TEXDIR, 'block', path[len('block/'):] + '.png')
    exists = os.path.isfile(png)
    print('    %-8s %-52s %s' % (tex, target, 'FOUND' if exists else 'MISSING'))
    if not exists:
        fail('texture file missing for %s -> %s' % (tex, target))
    else:
        resolved_files.add(png)

# every declared texture must be used and exist
for k, v in declared.items():
    if k == 'particle':
        continue
    if ('#' + k) not in referenced:
        fail('declared texture #%s is never used by any face' % k)

print('  generated PNGs (must all be 16x16):')
for stem in GENERATED:
    p = os.path.join(TEXDIR, 'block', stem + '.png')
    if not os.path.isfile(p):
        fail('generated texture missing: %s' % p)
        continue
    w, h = png_size(p)
    print('    %-34s %dx%d  %s' % (os.path.relpath(p, TEXDIR), w, h,
                                   'OK' if (w, h) == (16, 16) else 'WRONG SIZE'))
    if (w, h) != (16, 16):
        fail('%s is %dx%d, expected 16x16' % (p, w, h))

# pre-existing textures that were reused must also be 16x16
print('  reused pre-existing PNGs:')
for p in sorted(resolved_files):
    if os.path.basename(p)[:-4] in GENERATED:
        continue
    w, h = png_size(p)
    print('    %-34s %dx%d  %s' % (os.path.relpath(p, TEXDIR), w, h,
                                   'OK' if (w, h) == (16, 16) else 'WRONG SIZE'))
    if (w, h) != (16, 16):
        fail('%s is %dx%d, expected 16x16' % (p, w, h))

print()
print('=' * 74)
if FAILS:
    print('RESULT: FAILED — %d problem(s)' % len(FAILS))
    for f in FAILS:
        print('  - ' + f)
    sys.exit(1)
print('RESULT: ALL CHECKS PASSED')
print('=' * 74)
