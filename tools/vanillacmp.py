"""Compare SchematicTransform's hand-rolled offset math against the algorithm read directly out of
the decompiled 26.1 vanilla source (StructureTemplate.transform + getZeroPositionWithTransform).

Vanilla (26.1, minecraft-patched-26.1.2.112-sources.jar):

    transform(pos, mirror, rotation, pivot):
        x, z = pos.x, pos.z
        LEFT_RIGHT  -> z = -z
        FRONT_BACK  -> x = -x
        COUNTERCLOCKWISE_90 -> (pivotX - pivotZ + z, pivotX + pivotZ - x)
        CLOCKWISE_90        -> (pivotX + pivotZ - z, pivotZ - pivotX + x)
        CLOCKWISE_180       -> (pivotX + pivotX - x, pivotZ + pivotZ - z)
        NONE                -> (x, z)

    getZeroPositionWithTransform(zeroPos, mirror, rotation, sizeX, sizeZ):
        sizeX -= 1; sizeZ -= 1
        fb = sizeX if mirror == FRONT_BACK else 0
        lr = sizeZ if mirror == LEFT_RIGHT else 0
        COUNTERCLOCKWISE_90 -> zeroPos + (lr, 0, sizeX - fb)
        CLOCKWISE_90        -> zeroPos + (sizeZ - lr, 0, fb)
        CLOCKWISE_180       -> zeroPos + (sizeX - fb, 0, sizeZ - lr)
        NONE                -> zeroPos + (fb, 0, lr)

The cannon's contract is:  worldPos = origin + localOffset,  so only the *relative* offset matters
and the origin corner has to stay put. Vanilla's composition gives

    localOffset(x, z) = cornerShift + transform((x, 0, z), mirror, rotation, ZERO)

with cornerShift the getZeroPositionWithTransform result, and THAT is what this script checks.
"""

ROT = {"NONE": 0, "CW90": 1, "CW180": 2, "CCW90": 3}
MIR = {"NONE": 0, "LR": 1}  # LR == LEFT_RIGHT, negates Z


def vanilla_transform(x, z, mirror, rot, px=0, pz=0):
    if mirror == "LR":
        z = -z
    elif mirror == "FB":
        x = -x
    if rot == "CCW90":
        return (px - pz + z, px + pz - x)
    if rot == "CW90":
        return (px + pz - z, pz - px + x)
    if rot == "CW180":
        return (px + px - x, pz + pz - z)
    return (x, z)


def vanilla_corner_shift(mirror, rot, size_x, size_z):
    size_x -= 1
    size_z -= 1
    fb = size_x if mirror == "FB" else 0
    lr = size_z if mirror == "LR" else 0
    if rot == "CCW90":
        return (lr, size_x - fb)
    if rot == "CW90":
        return (size_z - lr, fb)
    if rot == "CW180":
        return (size_x - fb, size_z - lr)
    return (fb, lr)


def vanilla_offset(x, z, mirror, rot, size_x, size_z):
    cx, cz = vanilla_corner_shift(mirror, rot, size_x, size_z)
    tx, tz = vanilla_transform(x, z, mirror, rot)
    return (tx + cx, tz + cz)


def current_offset(x, z, mirror, rot_index):
    """The shipped SchematicTransform.applyToOffset."""
    mx = -x if mirror else x
    if rot_index == 1:
        return (-z, mx)
    if rot_index == 2:
        return (-mx, -z)
    if rot_index == 3:
        return (z, -mx)
    return (mx, z)


def size_after(size_x, size_z, rot):
    return (size_z, size_x) if rot in ("CW90", "CCW90") else (size_x, size_z)


ROTS = ["NONE", "CW90", "CW180", "CCW90"]
failures = 0


def report(label, got, want):
    global failures
    ok = got == want
    if not ok:
        failures += 1
    print(f"{'ok  ' if ok else 'FAIL'} {label}: got {got}, want {want}")


# --- 1. what does the CURRENT code produce vs vanilla, ignoring the anchor? -----------------
print("=== current applyToOffset vs vanilla offset (no anchor correction) ===")
diffs = 0
for sx, sz in ((3, 5), (4, 4), (2, 7), (1, 1), (5, 2)):
    for rot_name in ROTS:
        for mir_name, mir_bool in (("NONE", False), ("LR", True)):
            bad = []
            for x in range(sx):
                for z in range(sz):
                    cur = current_offset(x, z, mir_bool, ROT[rot_name])
                    # vanilla offset WITHOUT the corner shift: pure transform about ZERO
                    van_raw = vanilla_transform(x, z, mir_name, rot_name)
                    if cur != van_raw:
                        bad.append(((x, z), cur, van_raw))
            if bad:
                diffs += 1
                print(f"  DIFFER {sx}x{sz} rot={rot_name} mirror={mir_name}: {len(bad)}/{sx*sz} cells")
                for cell, cur, van in bad[:3]:
                    print(f"      local{cell}: current={cur} vanilla-transform={van}")

print(f"  ({diffs} configuration(s) differ)\n")

# --- 2. does the current code leave the bounding box anchored at the origin? ----------------
# The cannon computes origin() from the TRANSFORMED size, so the local offsets it feeds to
# place() are expected to run over [0, transformedSize) - i.e. already corner-shifted.
print("=== current applyToOffset already equals vanilla OFFSET (with corner shift)? ===")
match_all = 0
for sx, sz in ((3, 5), (4, 4), (2, 7), (1, 1), (5, 2)):
    for rot_name in ROTS:
        for mir_name, mir_bool in (("NONE", False), ("LR", True)):
            ok = True
            for x in range(sx):
                for z in range(sz):
                    cur = current_offset(x, z, mir_bool, ROT[rot_name])
                    van = vanilla_offset(x, z, mir_name, rot_name, sx, sz)
                    if cur != van:
                        ok = False
                        break
            if ok:
                match_all += 1
print(f"  {match_all} matched")

# --- 3. the invariant that actually matters: bbox of offsets == transformed size ------------
print("\n=== invariant: bbox(local offsets) == transformed size, and it starts at 0 ===")
for sx, sz in ((3, 5), (4, 4), (2, 7), (5, 2)):
    for rot_name in ROTS:
        for mir_name, mir_bool in (("NONE", False), ("LR", True)):
            xs, zs = [], []
            for x in range(sx):
                for z in range(sz):
                    ox, oz = vanilla_offset(x, z, mir_name, rot_name, sx, sz)
                    xs.append(ox)
                    zs.append(oz)
            got = (min(xs), min(zs), max(xs) - min(xs) + 1, max(zs) - min(zs) + 1)
            w, d = size_after(sx, sz, rot_name)
            want = (0, 0, w, d)
            report(f"{sx}x{sz} rot={rot_name} mirror={mir_name}", got, want)

# --- 4. old vs new, does the anchor move? ---------------------------------------------------
print("\n=== translation shift: new_offset - old_offset must be constant (a pure translation) ===")
for sx, sz in ((3, 5), (4, 4), (2, 7)):
    for rot_name in ROTS:
        for mir_name, mir_bool in (("NONE", False), ("LR", True)):
            deltas = set()
            for x in range(sx):
                for z in range(sz):
                    cur = current_offset(x, z, mir_bool, ROT[rot_name])
                    van = vanilla_offset(x, z, mir_name, rot_name, sx, sz)
                    deltas.add((van[0] - cur[0], van[1] - cur[1]))
            report(
                f"{sx}x{sz} rot={rot_name} mirror={mir_name} constant shift",
                len(deltas) == 1,
                True,
            )

print()
print("FAILURES:", failures)
raise SystemExit(1 if failures else 0)
