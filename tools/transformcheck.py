"""Standalone check of the offset math in SchematicTransform.

Mirrors the Java implementation so the four rotations and the mirror can be verified against
hand-computed expected values without launching the game. Coordinates here are the same
(x, z) relative offsets the Java class works with; y is never touched.

Vanilla orientation (verified from bytecode):
    NORTH = -Z, SOUTH = +Z, EAST = +X, WEST = -X
so a clockwise turn seen from above sends NORTH -> EAST, i.e. (x, z) -> (-z, x).
"""


def apply(x, z, rot, mirrored):
    """SchematicTransform.applyToOffset."""
    mx = -x if mirrored else x
    if rot == 1:
        return -z, mx
    if rot == 2:
        return -mx, -z
    if rot == 3:
        return z, -mx
    return mx, z


def compass(rot, mirrored):
    """SchematicTransform.compass, in terms of the four horizontal unit offsets."""
    # NORTH is (0, -1), SOUTH (0, +1), EAST (+1, 0), WEST (-1, 0)
    table = {0: (0, -1), 1: (1, 0), 2: (0, 1), 3: (-1, 0)}
    # rotate(NORTH)
    north = {0: (0, -1), 1: (1, 0), 2: (0, 1), 3: (-1, 0)}[rot]
    if mirrored and north[1] != 0:  # LEFT_RIGHT flips the Z axis
        north = (north[0], -north[1])
    return {(1, 0): "+X", (0, 1): "+Z", (-1, 0): "-X", (0, -1): "-Z"}[north]


failures = 0


def check(label, got, want):
    global failures
    ok = got == want
    if not ok:
        failures += 1
    print(f"{'ok  ' if ok else 'FAIL'} {label}: got {got}, want {want}")


print("--- offsets: a single block at (1, 0) walking around the compass ---")
# rot0: east stays east. rot1 (cw): east -> south. rot2: east -> west. rot3 (ccw): east -> north.
check("rot0 (1,0)", apply(1, 0, 0, False), (1, 0))
check("rot1 (1,0)", apply(1, 0, 1, False), (0, 1))
check("rot2 (1,0)", apply(1, 0, 2, False), (-1, 0))
check("rot3 (1,0)", apply(1, 0, 3, False), (0, -1))

print("\n--- offsets: a single block at (0, 1) ---")
check("rot0 (0,1)", apply(0, 1, 0, False), (0, 1))
check("rot1 (0,1)", apply(0, 1, 1, False), (-1, 0))
check("rot2 (0,1)", apply(0, 1, 2, False), (0, -1))
check("rot3 (0,1)", apply(0, 1, 3, False), (1, 0))

print("\n--- mirror before rotation ---")
check("mirror only (1,0)", apply(1, 0, 0, True), (-1, 0))
check("mirror only (0,1)", apply(0, 1, 0, True), (0, 1))
check("mirror+rot1 (1,0)", apply(1, 0, 1, True), (0, -1))
check("rot1+mirror (1,0)", apply(-1, 0, 1, False), (0, -1))

print("\n--- four rotations return to the start ---")
for x, z in ((3, 1), (-2, 5), (7, -4)):
    cur = (x, z)
    for _ in range(4):
        cur = apply(cur[0], cur[1], 1, False)
    check(f"( {x},{z}) x4 cw", cur, (x, z))

print("\n--- rotation is a bijection and preserves distance from the origin ---")
seen = set()
for x in range(-3, 4):
    for z in range(-3, 4):
        seen.add(apply(x, z, 1, False))
check("bijection over 7x7 (rot1)", len(seen), 49)
for x, z in ((3, 4), (-5, 2)):
    got = apply(x, z, 3, False)
    check(f"|({x},{z})| preserved", abs(got[0]) + abs(got[1]), abs(x) + abs(z))

print("\n--- size swap ---")
def size_after(sx, sz, rot):
    return (sz, sx) if rot in (1, 3) else (sx, sz)

check("3x5 rot0", size_after(3, 5, 0), (3, 5))
check("3x5 rot1", size_after(3, 5, 1), (5, 3))
check("3x5 rot2", size_after(3, 5, 2), (3, 5))
check("3x5 rot3", size_after(3, 5, 3), (5, 3))

# The transformed bounding box must match the extremes of the transformed block offsets.
print("\n--- bounding box consistency (the check that would catch a bad formula) ---")
for sx, sz, rot in ((3, 5, 1), (3, 5, 3), (4, 4, 2), (2, 7, 0)):
    xs, zs = [], []
    for x in range(sx):
        for z in range(sz):
            ox, oz = apply(x, z, rot, False)
            xs.append(ox)
            zs.append(oz)
    got = (max(xs) - min(xs) + 1, max(zs) - min(zs) + 1)
    check(f"{sx}x{sz} rot{rot} bbox", got, size_after(sx, sz, rot))

print("\n--- compass ---")
check("rot0", compass(0, False), "-Z")
check("rot1 cw", compass(1, False), "+X")
check("rot2", compass(2, False), "+Z")
check("rot3 ccw", compass(3, False), "-X")
check("rot0 mirrored", compass(0, True), "+Z")
check("rot1 mirrored", compass(1, True), "+X")

print("\n--- encode / decode round trip ---")
for rot in range(4):
    for mir in (False, True):
        enc = (rot & 0x3) | (0x4 if mir else 0)
        check(f"decode({enc})", (enc & 0x3, bool(enc & 0x4)), (rot, mir))

print()
print("FAILURES:", failures)
raise SystemExit(1 if failures else 0)
