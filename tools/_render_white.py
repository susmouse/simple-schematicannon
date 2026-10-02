"""Render the schematicannon block model on a white background, isometric.

Same projection/sorting as model_preview.py but with a white backdrop and a tighter
framing, so the result can be eyeballed next to the reference art.

Usage:  python tools/_render_white.py [out.png]
"""
import importlib.util
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
spec = importlib.util.spec_from_file_location("mp", os.path.join(HERE, "model_preview.py"))
mp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mp)

MODEL = os.path.join(ROOT, "src", "main", "resources", "assets", "schematicannon",
                     "models", "block", "schematicannon.json")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "schematicannon")


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "_render_white.png")
    model = json.load(open(MODEL, encoding="utf-8"))

    colors = {}
    for key, ref in model["textures"].items():
        if not ref.startswith("schematicannon:"):
            continue
        rel = ref.split(":", 1)[1]
        path = os.path.join(ASSETS, "textures", *rel.split("/")) + ".png"
        if os.path.isfile(path):
            colors["#" + key] = mp.avg_color(path)
        else:
            print("MISSING texture:", path)

    quads = mp.build_quads(model, colors)
    W = H = 560
    buf = bytearray(b"\xff\xff\xff" * (W * H))
    scale, ox, oy = 17.0, W / 2.0, H * 0.22
    VX, VY, VZ = 1.0, 1.0, -1.0
    quads.sort(key=lambda t: t[2][0] * VX + t[2][1] * VY + t[2][2] * VZ)
    for pts, nrm, _c, rgb in quads:
        scr = [mp.project(p, scale, ox, oy) for p in pts]
        mp.fill_quad(buf, W, H, scr, mp.shade(rgb, nrm))
    mp.write_png(out, W, H, buf)
    print(f"wrote {out}  quads={len(quads)}  textures={len(colors)}")


if __name__ == "__main__":
    main()
