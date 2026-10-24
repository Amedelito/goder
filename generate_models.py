#!/usr/bin/env python3
"""
Generador de modelos de bloque (JSON) para el resource pack de YTProps.
Minecraft Java 1.21.1 (pack_format 34).

Convenciones
- Cada prop se diseña en "píxeles locales": origen en la esquina mínima de la huella
  (X 0..W*16, Z 0..D*16) y Y=0 en el suelo. 16 px = 1 bloque.
- Al exportar se transforma a coordenadas de modelo:  x-W*8+8, y-16, z-D*8+8
  => el centro de la huella cae en el centro del modelo y el suelo en y=-16.
  Un item_display colocado en (centro_x, suelo+1.5, centro_z) lo deja exactamente
  sobre el suelo. Rango válido de Minecraft: -16..32 (3 bloques por eje).
- Solo se usan texturas vanilla (no hay que distribuir texturas propias).
"""
import json, math, os, random

OUT = os.path.join(os.path.dirname(__file__), "..", "pack", "assets", "ytcustom", "models", "prop")
os.makedirs(OUT, exist_ok=True)

FACES = ["north", "south", "east", "west", "up", "down"]


def region(start, length):
    if length >= 16:
        return 0.0, 16.0
    s = start % 16
    if s + length > 16:
        s = 16 - length
    return round(s, 3), round(s + length, 3)


class Model:
    def __init__(self, w, d, textures):
        self.w, self.d = w, d
        self.W, self.D = w * 16, d * 16
        self.textures = dict(textures)
        self.elements = []
        self.name = ""

    # ---- transform -------------------------------------------------
    def tr(self, p):
        x, y, z = p
        return [round(x - self.W / 2 + 8, 4), round(y - 16, 4), round(z - self.D / 2 + 8, 4)]

    def _uv(self, face, a, b, rot):
        (x1, y1, z1), (x2, y2, z2) = a, b
        if face in ("north", "south"):
            du, dv, su, sv = x2 - x1, y2 - y1, x1, y1
        elif face in ("east", "west"):
            du, dv, su, sv = z2 - z1, y2 - y1, z1, y1
        else:
            du, dv, su, sv = x2 - x1, z2 - z1, x1, z1
        if rot in (90, 270):
            du, dv = dv, du
        u = region(su, du)
        v = region(sv, dv)
        return [u[0], v[0], u[1], v[1]]

    # ---- primitives ------------------------------------------------
    def box(self, a, b, tex, rot=None, face_rot=None):
        """a,b = esquinas locales. tex = clave o dict cara->clave.
        rot = (eje, angulo, origen_local). face_rot = dict cara->0/90/180/270."""
        lo = [min(a[i], b[i]) for i in range(3)]
        hi = [max(a[i], b[i]) for i in range(3)]
        faces = {}
        for f in FACES:
            t = tex.get(f) if isinstance(tex, dict) else tex
            if t is None:
                continue
            fr = (face_rot or {}).get(f, 0)
            faces[f] = {"uv": self._uv(f, lo, hi, fr), "texture": "#" + t}
            if fr:
                faces[f]["rotation"] = fr
        el = {"from": self.tr(lo), "to": self.tr(hi), "faces": faces}
        if rot:
            axis, ang, org = rot
            el["rotation"] = {"angle": ang, "axis": axis, "origin": self.tr(org)}
        self.elements.append(el)

    def log(self, center, axis, length, t, tex_side, tex_end, y1, rot=None, jitter=0.0):
        """Tronco horizontal (axis 'x' o 'z') o vertical ('y')."""
        cx, cz = center
        j = random.uniform(-jitter, jitter) if jitter else 0.0
        half = length / 2
        if axis == "x":
            a = (cx - half + j, y1, cz - t / 2)
            b = (cx + half + j, y1 + t, cz + t / 2)
            tex = {"north": tex_side, "south": tex_side, "up": tex_side, "down": tex_side,
                   "east": tex_end, "west": tex_end}
            fr = {"north": 90, "south": 90, "up": 90, "down": 90}
        elif axis == "z":
            a = (cx - t / 2, y1, cz - half + j)
            b = (cx + t / 2, y1 + t, cz + half + j)
            tex = {"east": tex_side, "west": tex_side, "up": tex_side, "down": tex_side,
                   "north": tex_end, "south": tex_end}
            fr = {"east": 90, "west": 90}
        else:  # vertical
            a = (cx - t / 2, y1, cz - t / 2)
            b = (cx + t / 2, y1 + length, cz + t / 2)
            tex = {"north": tex_side, "south": tex_side, "east": tex_side, "west": tex_side,
                   "up": tex_end, "down": tex_end}
            fr = {}
        self.box(a, b, tex, rot=rot, face_rot=fr)

    def plane(self, cx, cz, y1, y2, halfw, tex, along="x", yaw=0.0):
        """Plano doble cara (llamas). along='x' => plano en eje X (caras north/south)."""
        if along == "x":
            lo = (cx - halfw, y1, cz)
            hi = (cx + halfw, y2, cz)
            faces = {"north": {"uv": [0, 0, 16, 16], "texture": "#" + tex},
                     "south": {"uv": [0, 0, 16, 16], "texture": "#" + tex}}
        else:
            lo = (cx, y1, cz - halfw)
            hi = (cx, y2, cz + halfw)
            faces = {"east": {"uv": [0, 0, 16, 16], "texture": "#" + tex},
                     "west": {"uv": [0, 0, 16, 16], "texture": "#" + tex}}
        el = {"from": self.tr(lo), "to": self.tr(hi), "faces": faces}
        if yaw:
            el["rotation"] = {"angle": yaw, "axis": "y", "origin": self.tr((cx, y1, cz))}
        self.elements.append(el)

    # ---- export ----------------------------------------------------
    def export(self, name, height_px, gui_extra=None):
        for el in self.elements:
            for k in ("from", "to"):
                for v in el[k]:
                    assert -16 <= v <= 32, f"{name}: coordenada fuera de rango {el[k]}"
        biggest = max(self.W, self.D, height_px)
        scale = round(0.625 * 16 / biggest, 4)
        cy = -16 + height_px / 2
        ty = round((8 - cy) * scale, 3)
        tex = dict(self.textures)
        tex["particle"] = tex.get("particle", list(tex.values())[0])
        data = {
            "credit": "YTProps - generado por generate_models.py",
            "textures": tex,
            "elements": self.elements,
            "display": {
                "gui": {"rotation": [30, 225, 0], "translation": [0, ty, 0], "scale": [scale] * 3},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [scale] * 3},
                "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [scale] * 3},
                "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [scale] * 3},
                "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [scale * 1.2] * 3},
            },
        }
        with open(os.path.join(OUT, name + ".json"), "w") as f:
            json.dump(data, f, indent=1)
        return len(self.elements)


# ---------------------------------------------------------------------------
# Paletas
# ---------------------------------------------------------------------------
def palette(soul):
    return {
        "stone": "minecraft:block/blackstone" if soul else "minecraft:block/cobblestone",
        "stone2": "minecraft:block/deepslate_bricks" if soul else "minecraft:block/stone",
        "ash": "minecraft:block/soul_soil" if soul else "minecraft:block/coal_block",
        "ember": "minecraft:block/magma",
        "logside": "minecraft:block/dark_oak_log",
        "logend": "minecraft:block/dark_oak_log_top",
        "fire": "minecraft:block/soul_campfire_fire" if soul else "minecraft:block/campfire_fire",
        "metal": "minecraft:block/iron_block",
        "lamp": "minecraft:block/sea_lantern" if soul else "minecraft:block/glowstone",
        "bone": "minecraft:block/bone_block_side",
        "bonetop": "minecraft:block/bone_block_top",
        "dark": "minecraft:block/coal_block",
        "plank": "minecraft:block/dark_oak_planks",
        "caul_side": "minecraft:block/cauldron_side",
        "caul_top": "minecraft:block/cauldron_top",
        "caul_bot": "minecraft:block/cauldron_bottom",
    }


def snap(a):
    a = (a % 90)
    if a > 45:
        a -= 90
    return round(a / 22.5) * 22.5


# ---------------------------------------------------------------------------
# Piezas reutilizables
# ---------------------------------------------------------------------------
def stone_ring(m, cx, cz, r, sw, count, tall=(4, 6)):
    for i in range(count):
        th = 2 * math.pi * i / count + 0.15
        x, z = cx + r * math.cos(th), cz + r * math.sin(th)
        h = random.uniform(*tall)
        w = sw * random.uniform(0.9, 1.15)
        ang = snap(-math.degrees(th))
        tex = "stone" if i % 3 else "stone2"
        m.box((x - w / 2, 0, z - w / 2), (x + w / 2, h, z + w / 2), tex,
              rot=("y", ang, (x, 0, z)) if ang else None)


def ash_bed(m, cx, cz, rho, embers=6):
    a = rho * 0.7071
    m.box((cx - a, 0, cz - a), (cx + a, 1.5, cz + a), "ash")
    m.box((cx - a, 0, cz - a), (cx + a, 1.52, cz + a), "ash", rot=("y", 45, (cx, 0, cz)))
    for _ in range(embers):
        ang = random.uniform(0, 2 * math.pi)
        rr = random.uniform(0, rho * 0.55)
        ex, ez = cx + rr * math.cos(ang), cz + rr * math.sin(ang)
        s = random.uniform(2, 3.5)
        m.box((ex - s / 2, 1.5, ez - s / 2), (ex + s / 2, 1.75, ez + s / 2), "ember")


def log_stack(m, cx, cz, t, L0, layers, y0=1.5, jitter=0.8):
    for i in range(layers):
        L = max(L0 - i * t * 1.0, t * 2)
        cnt = max(2, int(L // (t * 1.9)))
        spacing = t * 1.9
        axis = "x" if i % 2 == 0 else "z"
        y1 = y0 + i * t
        for j in range(cnt):
            off = (j - (cnt - 1) / 2) * spacing
            c = (cx, cz + off) if axis == "x" else (cx + off, cz)
            m.log(c, axis, L, t, "logside", "logend", y1, jitter=jitter)


def leaning_logs(m, cx, cz, rb, length, t, y0=1.5):
    # 4 troncos inclinados hacia el centro (rotación 45 grados)
    # +x lean: eje z, ángulo -45 ; -x lean: +45 ; +z lean: eje x, +45 ; -z lean: -45
    specs = [
        ((cx - rb, cz), "z", -45), ((cx + rb, cz), "z", 45),
        ((cx, cz - rb), "x", 45), ((cx, cz + rb), "x", -45),
    ]
    for (px, pz), axis, ang in specs:
        m.box((px - t / 2, y0, pz - t / 2), (px + t / 2, y0 + length, pz + t / 2),
              {"north": "logside", "south": "logside", "east": "logside", "west": "logside",
               "up": "logend", "down": "logend"},
              rot=(axis, ang, (px, y0, pz)))


def flames(m, cx, cz, y1, height, width, small=True):
    hw = width / 2
    m.plane(cx, cz, y1, y1 + height, hw, "fire", "x")
    m.plane(cx, cz, y1, y1 + height, hw, "fire", "z")
    m.plane(cx, cz, y1 + 1, y1 + height * 0.92, hw * 0.85, "fire", "x", 45)
    m.plane(cx, cz, y1 + 1, y1 + height * 0.92, hw * 0.85, "fire", "z", 45)
    if small:
        d = width * 0.28
        for sx, sz, al in ((d, d, "x"), (-d, d, "z"), (d, -d, "z"), (-d, -d, "x")):
            m.plane(cx + sx, cz + sz, y1, y1 + height * 0.5, hw * 0.4, "fire", al, 22.5 if sx * sz > 0 else -22.5)


# ---------------------------------------------------------------------------
# Props
# ---------------------------------------------------------------------------
def bonfire(n, soul):
    random.seed(100 + n + (50 if soul else 0))
    S = n * 16
    c = S / 2
    m = Model(n, n, palette(soul))
    sw = {3: 7.0, 2: 6.0, 1: 5.0}[n]
    r = c - (sw * 1.15 / 2 + 0.2)
    count = max(6, int(2 * math.pi * r / (sw + 0.8)))
    stone_ring(m, c, c, r, sw, count, tall=(4, 6.5) if n > 1 else (3.5, 5))
    ash_bed(m, c, c, r - 2, embers=n * 2)
    t = {3: 4.0, 2: 3.2, 1: 2.6}[n]
    layers = {3: 5, 2: 4, 1: 3}[n]
    log_stack(m, c, c, t, S * 0.6, layers)
    if n >= 2:
        leaning_logs(m, c, c, r - 4, (r - 4) * 1.35, t * 0.8)
    height = 20 + n * 8
    flames(m, c, c, 3, height, 14 + n * 6)
    return m, 3 + height


def brasero(soul):
    random.seed(5 + (1 if soul else 0))
    m = Model(1, 1, palette(soul))
    c = 8
    for sx, sz in ((4, 4), (12, 4), (4, 12), (12, 12)):
        m.box((sx - 1, 0, sz - 1), (sx + 1, 11, sz + 1), "metal")
    m.box((3, 0, 3), (13, 1, 13), "metal")          # base cruzada
    m.box((c - 4, 9, c - 4), (c + 4, 11, c + 4), "metal")
    m.box((c - 6, 11, c - 6), (c + 6, 13, c + 6), "metal")
    m.box((c - 7.5, 13, c - 7.5), (c + 7.5, 15, c + 7.5), "metal")
    # aro superior (4 paredes)
    m.box((0.5, 15, 0.5), (15.5, 17.5, 1.5), "metal")
    m.box((0.5, 15, 14.5), (15.5, 17.5, 15.5), "metal")
    m.box((0.5, 15, 1.5), (1.5, 17.5, 14.5), "metal")
    m.box((14.5, 15, 1.5), (15.5, 17.5, 14.5), "metal")
    m.box((2, 14.9, 2), (14, 16, 14), "dark")        # carbón
    m.box((4, 16, 4), (8, 16.25, 8), "ember")
    m.box((9, 16, 8), (12, 16.25, 11.5), "ember")
    flames(m, c, c, 16, 22, 14, small=False)
    return m, 38


def asador(soul):
    random.seed(9 + (1 if soul else 0))
    m = Model(3, 1, palette(soul))
    cx, cz = 24, 8
    for px in (4, 44):                                  # postes
        m.box((px - 1.5, 0, cz - 1.5), (px + 1.5, 34, cz + 1.5),
              {"north": "logside", "south": "logside", "east": "logside", "west": "logside",
               "up": "logend", "down": "logend"})
        m.box((px - 3, 0, cz - 3), (px + 3, 1.5, cz + 3), "stone2")
    m.log((cx, cz), "x", 46, 2.6, "logside", "logend", 30)  # espetón
    # fogata pequeña
    stone_ring(m, cx, cz, 6.2, 4.5, 8, tall=(3, 4.5))
    ash_bed(m, cx, cz, 5, embers=2)
    t = 2.2
    log_stack(m, cx, cz, t, 11, 2, jitter=0.4)
    flames(m, cx, cz, 3, 15, 12, small=False)
    # cadena + caldero
    m.box((cx - 0.75, 25, cz - 0.75), (cx + 0.75, 30, cz + 0.75), "metal")
    m.box((cx - 5, 17, cz - 5), (cx + 5, 25, cz + 5),
          {"north": "caul_side", "south": "caul_side", "east": "caul_side", "west": "caul_side",
           "up": "caul_top", "down": "caul_bot"})
    return m, 34


def pila_lena():
    random.seed(21)
    m = Model(2, 1, palette(False))
    rows = [(5, 4, 6, 1), (4, 7, 12, 2), (3, 10, 18, 3)]
    # (cantidad, primer centro x, y1, fila)
    t = 6
    y = 0
    for cnt, first, _, _ in rows:
        for j in range(cnt):
            m.log((first + j * t, 8), "z", 15, t, "logside", "logend", y, jitter=0.8)
        y += t
    for cnt, first, y1 in ((2, 13, 18),):
        for j in range(cnt):
            m.log((first + j * t, 8), "z", 14, t, "logside", "logend", y1, jitter=0.8)
    return m, 24


def tocon():
    m = Model(1, 1, palette(False))
    side = {"north": "logside", "south": "logside", "east": "logside", "west": "logside",
            "up": "logend", "down": "logend"}
    m.box((2, 0, 2), (14, 11, 14), side)
    m.box((2, 0, 2), (14, 11.02, 14), side, rot=("y", 45, (8, 0, 8)))
    for rx, rz, ra in ((1, 8, 0), (15, 8, 0), (8, 1, 0), (8, 15, 0)):
        m.box((rx - 1.5, 0, rz - 1.5), (rx + 1.5, 2.5, rz + 1.5), side)
    return m, 11


def pila_huesos():
    random.seed(33)
    m = Model(2, 2, palette(False))
    m.box((2, 0, 2), (30, 0.5, 30), {"up": "ash"}, rot=("y", 22.5, (16, 0, 16)))

    def bone(x, z, y, length, ang):
        org = (x, y, z)
        a = ang
        rot = ("y", a, org)
        m.box((x - length / 2, y, z - 1), (x + length / 2, y + 2, z + 1), {"north": "bone", "south": "bone", "up": "bone", "down": "bone", "east": "bonetop", "west": "bonetop"}, rot=rot)
        for sgn in (-1, 1):
            ex = x + sgn * (length / 2)
            for sz in (-1.2, 1.2):
                m.box((ex - 1.5, max(y - 0.25, 0), z + sz - 1.2), (ex + 1.5, y + 2.25, z + sz + 1.2), "bonetop", rot=rot)

    spots = [(9, 9, 0, 16, 22.5), (20, 8, 0, 14, -22.5), (23, 19, 0, 17, 45), (10, 22, 0, 15, 0),
             (16, 16, 2.3, 16, -45), (14, 13, 4.6, 13, 22.5), (19, 23, 2.3, 12, 0)]
    for x, z, y, L, a in spots:
        bone(x, z, y, L, a)
    # cráneo
    sx, sz = 16, 16.5
    sk = {"north": "bonetop", "south": "bonetop", "east": "bonetop", "west": "bonetop", "up": "bonetop", "down": "bonetop"}
    m.box((sx - 4, 6.5, sz - 4), (sx + 4, 14.5, sz + 4), sk, rot=("y", 22.5, (sx, 6.5, sz)))
    m.box((sx - 2.5, 5, sz - 3), (sx + 2.5, 6.5, sz + 3), sk, rot=("y", 22.5, (sx, 6.5, sz)))
    m.box((sx - 3.1, 10.5, sz + 3.9), (sx - 0.9, 12.7, sz + 4.1), "dark", rot=("y", 22.5, (sx, 6.5, sz)))
    m.box((sx + 0.9, 10.5, sz + 3.9), (sx + 3.1, 12.7, sz + 4.1), "dark", rot=("y", 22.5, (sx, 6.5, sz)))
    return m, 15


def farol_poste(soul):
    m = Model(1, 1, palette(soul))
    c = 8
    side = {"north": "logside", "south": "logside", "east": "logside", "west": "logside",
            "up": "logend", "down": "logend"}
    m.box((c - 4, 0, c - 4), (c + 4, 3, c + 4), "stone2")
    m.box((c - 5, 0, c - 5), (c + 5, 1.5, c + 5), "stone")
    m.box((c - 1.5, 3, c - 1.5), (c + 1.5, 44, c + 1.5), side)
    lx = c + 5
    m.box((c - 1.5, 41, c - 1.5), (c + 6, 44, c + 1.5), side)            # brazo
    m.box((lx - 0.5, 34, c - 0.5), (lx + 0.5, 41, c + 0.5), "metal")      # cadena
    m.box((lx - 3, 26.5, c - 3), (lx + 3, 27.5, c + 3), "metal")         # tapa inferior
    m.box((lx - 3, 33, c - 3), (lx + 3, 34, c + 3), "metal")             # tapa superior
    m.box((lx - 2.5, 27.5, c - 2.5), (lx + 2.5, 33, c + 2.5), "lamp")    # luz
    for ax in (-2.5, 2.1):
        for az in (-2.5, 2.1):
            m.box((lx + ax, 27.5, c + az), (lx + ax + 0.4, 33, c + az + 0.4), "metal")
    m.box((c - 2.5, 16, c - 2.5), (c + 2.5, 17, c + 2.5), "metal")       # anilla decorativa
    return m, 44


def banco():
    random.seed(41)
    m = Model(2, 1, palette(False))
    side = {"north": "logside", "south": "logside", "east": "logside", "west": "logside",
            "up": "logend", "down": "logend"}
    for px in (5, 27):
        m.box((px - 3, 0, 4), (px + 3, 6, 12), side)
    m.log((16, 8), "x", 30, 6, "logside", "logend", 6)
    return m, 12


def main():
    jobs = {}
    jobs["fogata_grande"] = bonfire(3, False)
    jobs["fogata_mediana"] = bonfire(2, False)
    jobs["fogata_pequena"] = bonfire(1, False)
    jobs["brasero"] = brasero(False)
    jobs["asador"] = asador(False)
    jobs["pila_lena"] = pila_lena()
    jobs["tocon"] = tocon()
    jobs["pila_huesos"] = pila_huesos()
    jobs["farol_poste"] = farol_poste(False)
    jobs["banco"] = banco()
    jobs["fogata_grande_alma"] = bonfire(3, True)
    jobs["fogata_mediana_alma"] = bonfire(2, True)
    jobs["fogata_pequena_alma"] = bonfire(1, True)
    jobs["brasero_alma"] = brasero(True)
    jobs["asador_alma"] = asador(True)
    jobs["farol_poste_alma"] = farol_poste(True)
    meta = {}
    for name, (m, h) in jobs.items():
        n = m.export(name, h)
        meta[name] = {"elements": n, "w": m.w, "d": m.d, "height_px": h}
        print(f"{name:22s} {m.w}x{m.d}  h={h:5.1f}px  elementos={n}")
    with open(os.path.join(os.path.dirname(__file__), "meta.json"), "w") as f:
        json.dump(meta, f, indent=1)


if __name__ == "__main__":
    main()
