"""Throwaway helper: compute per-element rotated AABBs for candidate model data
before the model file is written. Pure arithmetic, no assets touched."""
import json
import math


def rotate_point(p, origin, axis, angle_deg):
    x, y, z = p
    ox, oy, oz = origin
    a = math.radians(angle_deg)
    c, s = math.cos(a), math.sin(a)
    dx, dy, dz = x - ox, y - oy, z - oz
    if axis == 'x':
        ny = dy * c - dz * s
        nz = dy * s + dz * c
        return (ox + dx, oy + ny, oz + nz)
    if axis == 'y':
        nx = dx * c + dz * s
        nz = -dx * s + dz * c
        return (ox + nx, oy + dy, oz + nz)
    nx = dx * c - dy * s
    ny = dx * s + dy * c
    return (ox + nx, oy + ny, oz + dz)


def element_aabb(el):
    f, t = el['from'], el['to']
    corners = [(f[0] if i & 1 else t[0],
                f[1] if i & 2 else t[1],
                f[2] if i & 4 else t[2]) for i in range(8)]
    rot = el.get('rotation')
    if rot:
        corners = [rotate_point(c, rot['origin'], rot['axis'], rot['angle'])
                   for c in corners]
    mn = [min(c[i] for c in corners) for i in range(3)]
    mx = [max(c[i] for c in corners) for i in range(3)]
    return mn, mx


def report(path):
    data = json.load(open(path, encoding='utf-8'))
    if 'elements' not in data:
        data = {'elements': data['probe']}
        print('(probe file: bare element list)')
    print('%s : %d elements' % (path, len(data['elements'])))
    gmn = [1e9] * 3
    gmx = [-1e9] * 3
    worst = []
    ok = True
    for i, el in enumerate(data['elements']):
        mn, mx = element_aabb(el)
        for k in range(3):
            gmn[k] = min(gmn[k], mn[k])
            gmx[k] = max(gmx[k], mx[k])
        bad = (mn[0] < -1e-6 or mn[1] < -1e-6 or mn[2] < -1e-6
               or mx[0] > 16 + 1e-6 or mx[1] > 16 + 1e-6 or mx[2] > 16 + 1e-6)
        rot = ' rot=%s%s@%s' % (el['rotation']['axis'], el['rotation']['angle'],
                                el['rotation']['origin']) if el.get('rotation') else ''
        flag = '   <<< OUT OF RANGE' if bad else ''
        print('  [%2d] from=%s to=%s%s\n       aabb min=(%.4f, %.4f, %.4f) max=(%.4f, %.4f, %.4f)%s'
              % (i, el['from'], el['to'], rot, mn[0], mn[1], mn[2], mx[0], mx[1], mx[2], flag))
        if bad:
            ok = False
            worst.append((i, mn, mx))
    print('  MODEL BBOX min=(%.4f, %.4f, %.4f) max=(%.4f, %.4f, %.4f)'
          % (gmn[0], gmn[1], gmn[2], gmx[0], gmx[1], gmx[2]))
    print('  SIZE = (%.4f, %.4f, %.4f)' % (gmx[0] - gmn[0], gmx[1] - gmn[1], gmx[2] - gmn[2]))
    print('  RESULT:', 'OK inside [0..16]' if ok else 'FAIL %d element(s) out of range' % len(worst))
    return ok


if __name__ == '__main__':
    import sys
    allok = True
    for p in sys.argv[1:]:
        allok &= report(p)
    sys.exit(0 if allok else 1)
