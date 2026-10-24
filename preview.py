import json, math, os, glob
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Polygon

D = os.path.join(os.path.dirname(__file__), "..", "pack", "assets", "ytcustom", "models", "prop")
COL = {
    "stone": (0.45, 0.45, 0.47), "stone2": (0.55, 0.55, 0.56), "ash": (0.12, 0.12, 0.13),
    "ember": (0.95, 0.35, 0.1), "logside": (0.28, 0.18, 0.10), "logend": (0.45, 0.32, 0.18),
    "fire": (1.0, 0.65, 0.1), "metal": (0.7, 0.7, 0.75), "lamp": (1.0, 0.85, 0.4),
    "bone": (0.9, 0.88, 0.8), "bonetop": (0.95, 0.93, 0.85), "dark": (0.1, 0.1, 0.1),
    "caul_side": (0.25, 0.25, 0.28), "caul_top": (0.3, 0.3, 0.32), "caul_bot": (0.2, 0.2, 0.2),
}
FACE = {  # cara -> (vertices indices del cubo, normal)
    "north": ([0, 1, 5, 4], (0, 0, -1)), "south": ([2, 3, 7, 6], (0, 0, 1)),
    "west": ([0, 2, 6, 4], (-1, 0, 0)), "east": ([1, 3, 7, 5], (1, 0, 0)),
    "down": ([0, 1, 3, 2], (0, -1, 0)), "up": ([4, 5, 7, 6], (0, 1, 0)),
}


def rot(p, ax, ang, org):
    a = math.radians(ang)
    x, y, z = [p[i] - org[i] for i in range(3)]
    c, s = math.cos(a), math.sin(a)
    if ax == "y":
        x, z = x * c + z * s, -x * s + z * c
    elif ax == "x":
        y, z = y * c - z * s, y * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + org[0], y + org[1], z + org[2])


def project(p, az=35):
    x, y, z = p
    a = math.radians(az)
    xr = x * math.cos(a) - z * math.sin(a)
    zr = x * math.sin(a) + z * math.cos(a)
    el = math.radians(28)
    sx = xr
    sy = y * math.cos(el) + zr * math.sin(el)
    depth = zr * math.cos(el) - y * math.sin(el)
    return sx, sy, depth


def render(ax, path, az=35):
    d = json.load(open(path))
    polys = []
    for el in d["elements"]:
        f, t = el["from"], el["to"]
        v = [(x, y, z) for y in (f[1], t[1]) for x in (f[0], t[0]) for z in (f[2], t[2])]
        # orden: bit0 z, bit1 x, bit2 y  -> remap a índices usados por FACE (x bit0,z bit1,y bit2)
        corners = []
        for i in range(8):
            xx = t[0] if i & 1 else f[0]
            zz = t[2] if i & 2 else f[2]
            yy = t[1] if i & 4 else f[1]
            corners.append((xx, yy, zz))
        r = el.get("rotation")
        if r:
            corners = [rot(c, r["axis"], r["angle"], r["origin"]) for c in corners]
        for fn, fd in el["faces"].items():
            idx = FACE[fn][0]
            pts = [corners[i] for i in idx]
            key = fd["texture"][1:]
            col = np.array(COL.get(key, (0.6, 0.3, 0.6)))
            shade = {"up": 1.0, "north": 0.8, "south": 0.8, "east": 0.65, "west": 0.65, "down": 0.5}[fn]
            pr = [project(p, az) for p in pts]
            depth = sum(q[2] for q in pr) / 4
            polys.append((depth, [(q[0], q[1]) for q in pr], tuple(np.clip(col * shade, 0, 1)), key == "fire"))
    polys.sort(key=lambda p: -p[0])
    for dep, pts, col, isfire in polys:
        ax.add_patch(Polygon(pts, closed=True, facecolor=col, edgecolor=(0, 0, 0, 0.25), linewidth=0.3,
                             alpha=0.75 if isfire else 1))
    ax.set_aspect("equal")
    ax.set_xlim(-34, 34)
    ax.set_ylim(-22, 38)
    ax.axis("off")


files = sorted(glob.glob(os.path.join(D, "*.json")))
cols = 4
rows = math.ceil(len(files) / cols)
fig, axes = plt.subplots(rows, cols, figsize=(cols * 3.4, rows * 3.4), facecolor="#14110f")
for a in axes.flat:
    a.axis("off")
for a, p in zip(axes.flat, files):
    a.set_facecolor("#14110f")
    render(a, p)
    a.set_title(os.path.basename(p)[:-5], color="#e8d8b8", fontsize=9)
plt.tight_layout()
out = os.path.join(os.path.dirname(__file__), "..", "preview_props.png")
plt.savefig(out, dpi=110, facecolor="#14110f")
print("ok", out)
