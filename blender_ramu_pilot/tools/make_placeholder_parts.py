"""Generate PLACEHOLDER cutout parts for testing the pipeline.

This draws a grey mannequin, deliberately abstract so nobody mistakes it for
Ramu. It exists so the rig/animation/QA/render chain can be tested before the
approved Ramu art is cut into layers. Output layout is exactly what the real
art must follow (see ART_PREP.md): one PNG per part, per view, plus a
manifest with joint pivots.

Run with Blender's Python (it ships numpy) or any Python with numpy:
    blender -b --factory-startup -P tools/make_placeholder_parts.py -- --out placeholder
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "scripts"))
from pilot_common import save_json, script_args, write_png  # noqa: E402

W, H = 1200, 2100          # canvas px
OX, OY = 600, 2000         # ground point between the feet
PPU = 1000.0               # canvas px per Blender unit
VIEWS = {"side_R": 90.0, "three_quarter": 45.0, "front": 0.0}

OUTLINE = (40, 44, 52)
NEAR, MID, FAR = (178, 188, 201), (160, 170, 184), (122, 132, 147)
HEAD = (198, 205, 214)
DARK, WHITE, TONGUE, BROW = (70, 30, 40), (246, 246, 246), (205, 95, 105), (60, 62, 72)

# Character space: x = character's left, y = forward, z = up (units).
J = {
    "hip_c": (0, 0, 0.90), "hip.L": (0.09, 0, 0.88), "knee.L": (0.09, 0.025, 0.48),
    "ankle.L": (0.09, 0, 0.085), "toe.L": (0.09, 0.16, 0.035), "heel.L": (0.09, -0.05, 0.03),
    "neck": (0, 0, 1.45), "shoulder.L": (0.17, 0, 1.36), "elbow.L": (0.20, -0.02, 1.09),
    "wrist.L": (0.21, 0.0, 0.85), "palm.L": (0.215, 0.012, 0.79), "finger.L": (0.22, 0.03, 0.725),
    "head_c": (0, 0.01, 1.64), "head_top": (0, 0.01, 1.83),
}
for k in list(J):
    if k.endswith(".L"):
        x, y, z = J[k]
        J[k[:-2] + ".R"] = (-x, y, z)
HEAD_R = (0.15, 0.15, 0.185)


def proj(p, yaw):
    """Character-space point -> (canvas x, canvas y, depth toward camera)."""
    t = math.radians(yaw)
    x, y, z = p
    sx = x * math.cos(t) + y * math.sin(t)
    depth = y * math.cos(t) - x * math.sin(t)
    return OX + sx * PPU, OY - z * PPU, depth


def on_head(x, z, bump=0.0):
    cx, cy, cz = J["head_c"]
    rx, ry, rz = HEAD_R
    q = 1 - (x / rx) ** 2 - ((z - cz) / rz) ** 2
    return (x, cy + (ry + bump) * math.sqrt(max(q, 0.0)), z)


class Layer:
    def __init__(self):
        self.rgb = np.zeros((H, W, 3), np.float32)   # premultiplied
        self.a = np.zeros((H, W), np.float32)

    def draw(self, sdf, bbox, fill, outline=OUTLINE, ow=5.0):
        x0, y0, x1, y1 = [int(v) for v in bbox]
        x0, y0, x1, y1 = max(x0 - 8, 0), max(y0 - 8, 0), min(x1 + 8, W), min(y1 + 8, H)
        if x1 <= x0 or y1 <= y0:
            return
        X, Y = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        d = sdf(X, Y)
        cov = np.clip(0.5 - d, 0, 1)
        inner = np.clip(0.5 - (d + ow), 0, 1) if outline else cov
        col = (np.array(outline or fill, np.float32)[None, None] * (1 - inner[..., None])
               + np.array(fill, np.float32)[None, None] * inner[..., None]) / 255.0
        a_dst = self.a[y0:y1, x0:x1]
        self.rgb[y0:y1, x0:x1] = col * cov[..., None] + self.rgb[y0:y1, x0:x1] * (1 - cov[..., None])
        self.a[y0:y1, x0:x1] = cov + a_dst * (1 - cov)

    def save_cropped(self, path, pad=6):
        ys, xs = np.nonzero(self.a > 0.002)
        if len(xs) == 0:
            return None
        x0, x1 = max(xs.min() - pad, 0), min(xs.max() + pad + 1, W)
        y0, y1 = max(ys.min() - pad, 0), min(ys.max() + pad + 1, H)
        a = self.a[y0:y1, x0:x1]
        rgb = np.where(a[..., None] > 0, self.rgb[y0:y1, x0:x1] / np.maximum(a[..., None], 1e-6), 0)
        out = np.concatenate([rgb, a[..., None]], axis=2)
        write_png(path, (np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8))
        return [int(x0), int(y0)]


def sdf_capsule(a, b, r1, r2):
    ax, ay = a
    bx, by = b

    def f(X, Y):
        px, py = X - ax, Y - ay
        vx, vy = bx - ax, by - ay
        h = np.clip((px * vx + py * vy) / max(vx * vx + vy * vy, 1e-9), 0, 1)
        return np.hypot(px - vx * h, py - vy * h) - (r1 + (r2 - r1) * h)
    bb = (min(ax, bx) - max(r1, r2), min(ay, by) - max(r1, r2), max(ax, bx) + max(r1, r2), max(ay, by) + max(r1, r2))
    return f, bb


def sdf_ellipse(c, rx, ry):
    cx, cy = c
    rx, ry = max(rx, 1.0), max(ry, 1.0)

    def f(X, Y):
        px, py = X - cx, Y - cy
        k0 = np.hypot(px / rx, py / ry)
        k1 = np.maximum(np.hypot(px / rx ** 2, py / ry ** 2), 1e-9)
        return k0 * (k0 - 1.0) / k1
    return f, (cx - rx, cy - ry, cx + rx, cy + ry)


def sdf_box(c, hw, hh, rad=0.0):
    cx, cy = c

    def f(X, Y):
        qx, qy = np.abs(X - cx) - hw + rad, np.abs(Y - cy) - hh + rad
        return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - rad
    return f, (cx - hw, cy - hh, cx + hw, cy + hh)


def sdf_polyline(pts, r):
    caps = [sdf_capsule(pts[i], pts[i + 1], r, r) for i in range(len(pts) - 1)]

    def f(X, Y):
        return np.minimum.reduce([c[0](X, Y) for c in caps])
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return f, (min(xs) - r, min(ys) - r, max(xs) + r, max(ys) + r)


def sdf_union(*shapes):
    def f(X, Y):
        return np.minimum.reduce([s[0](X, Y) for s in shapes])
    bbs = [s[1] for s in shapes]
    return f, (min(b[0] for b in bbs), min(b[1] for b in bbs), max(b[2] for b in bbs), max(b[3] for b in bbs))


def px(u):
    return u * PPU


def build_view(view, yaw, out_dir):
    P = {k: proj(v, yaw) for k, v in J.items()}
    t = math.radians(yaw)

    def side_of(name_l):
        d = P[name_l][2]
        return "near" if d > 0.01 else "far" if d < -0.01 else "mid"

    parts, bones = [], {}

    def xy(k):
        return [round(P[k][0], 1), round(P[k][1], 1)]

    def at(k, dz):
        x, y, z = J[k]
        p = proj((x, y, z + dz), yaw)
        return [round(p[0], 1), round(p[1], 1)]

    bones["hips"] = {"parent": None, "head": xy("hip_c"), "tail": at("hip_c", 0.12)}
    bones["torso"] = {"parent": "hips", "head": at("hip_c", 0.04), "tail": xy("neck")}
    bones["head"] = {"parent": "torso", "head": xy("neck"), "tail": xy("head_top")}
    for s in ("L", "R"):
        bones[f"upper_arm.{s}"] = {"parent": "torso", "head": xy(f"shoulder.{s}"), "tail": xy(f"elbow.{s}")}
        bones[f"forearm.{s}"] = {"parent": f"upper_arm.{s}", "head": xy(f"elbow.{s}"), "tail": xy(f"wrist.{s}")}
        bones[f"hand.{s}"] = {"parent": f"forearm.{s}", "head": xy(f"wrist.{s}"), "tail": xy(f"palm.{s}")}
        bones[f"thigh.{s}"] = {"parent": "hips", "head": xy(f"hip.{s}"), "tail": xy(f"knee.{s}")}
        bones[f"shin.{s}"] = {"parent": f"thigh.{s}", "head": xy(f"knee.{s}"), "tail": xy(f"ankle.{s}")}
        bones[f"foot.{s}"] = {"parent": f"shin.{s}", "head": xy(f"ankle.{s}"), "tail": xy(f"toe.{s}"),
                              "heel": xy(f"heel.{s}")}

    def add(name, bone, z, shapes, fill, group=None, variant=None):
        layer = Layer()
        for shp in shapes:
            if isinstance(shp, tuple) and len(shp) == 3:
                (f, bb), fcol, oc = shp
                layer.draw(f, bb, fcol, oc)
            else:
                layer.draw(shp[0], shp[1], fill)
        rel = f"parts/{view}/{name}.png"
        off = layer.save_cropped(os.path.join(out_dir, rel))
        if off is None:
            return
        part = {"name": name, "file": rel, "bone": bone, "z": z, "offset": off}
        if group:
            part["group"], part["variant"] = group, variant
        parts.append(part)

    zbase = {"far": (1, 4), "mid": (50, 10), "near": (55, 40)}   # (arm base, leg base)
    for s in ("L", "R"):
        side = side_of(f"shoulder.{s}")
        col = {"near": NEAR, "mid": MID, "far": FAR}[side]
        arm_z, leg_z = zbase[side]
        sh, el, wr, pa, fi = (P[f"{k}.{s}"][:2] for k in ("shoulder", "elbow", "wrist", "palm", "finger"))
        add(f"upper_arm.{s}", f"upper_arm.{s}", arm_z, [sdf_capsule(sh, el, px(0.048), px(0.04))], col)
        add(f"forearm.{s}", f"forearm.{s}", arm_z + 1, [sdf_capsule(el, wr, px(0.04), px(0.033))], col)
        add(f"hand_open.{s}", f"hand.{s}", arm_z + 2,
            [sdf_union(sdf_ellipse(pa, px(0.034), px(0.04)), sdf_capsule(pa, fi, px(0.024), px(0.018)))],
            col, f"hand.{s}", "open")
        add(f"hand_grip.{s}", f"hand.{s}", arm_z + 2,
            [sdf_ellipse(pa, px(0.042), px(0.032))], col, f"hand.{s}", "grip")
        hp, kn, an, to, he = (P[f"{k}.{s}"][:2] for k in ("hip", "knee", "ankle", "toe", "heel"))
        add(f"shin.{s}", f"shin.{s}", leg_z, [sdf_capsule(kn, an, px(0.055), px(0.042))], col)
        add(f"thigh.{s}", f"thigh.{s}", leg_z + 1, [sdf_capsule(hp, kn, px(0.07), px(0.056))], col)
        foot = sdf_union(sdf_capsule(an, he, px(0.042), px(0.034)), sdf_capsule(he, to, px(0.034), px(0.03)),
                         sdf_ellipse(to, px(0.035 + 0.02 * math.cos(t)), px(0.032)))
        add(f"foot.{s}", f"foot.{s}", leg_z + 2, [foot], col)

    def width(wx, wy):
        return math.sqrt((wx * math.cos(t)) ** 2 + (wy * math.sin(t)) ** 2)

    hc = P["hip_c"]
    add("pelvis", "hips", 20, [sdf_ellipse(hc[:2], px(width(0.15, 0.11)), px(0.085))], MID)
    top = proj((0, 0, 1.33), yaw)[:2]
    bot = proj((0, 0, 0.96), yaw)[:2]
    neck_a, neck_b = proj((0, 0, 1.40), yaw)[:2], proj((0, 0.0, 1.52), yaw)[:2]
    add("torso", "torso", 21, [sdf_union(sdf_capsule(bot, top, px(width(0.145, 0.10)), px(width(0.16, 0.105))),
                                         sdf_capsule(neck_a, neck_b, px(0.045), px(0.045)))], MID)

    hcx, hcy, hdepth = P["head_c"]
    add("head", "head", 30, [sdf_ellipse((hcx, hcy), px(width(HEAD_R[0], HEAD_R[1])), px(HEAD_R[2]))], HEAD)

    def feature(x, z, bump=0.0):
        p = proj(on_head(x, z, bump), yaw)
        rel = (p[2] - hdepth) / HEAD_R[1]
        return p[0], p[1], rel

    nx, ny, nrel = feature(0, 1.615, 0.02)
    if nrel > -0.05:
        add("nose", "head", 31, [sdf_ellipse((nx, ny), px(0.022 * max(0.5, nrel)), px(0.026))], HEAD)

    # Eyes / brows: both eyes in one image per variant.
    eyes = [feature(sx * 0.06, 1.665) for sx in (1, -1)]
    eyes = [e for e in eyes if e[2] > 0.15]
    look = 0.010 * math.sin(t)
    for variant in ("open", "half", "closed"):
        shapes = []
        for ex, ey, rel in eyes:
            s = max(0.4, rel)
            if variant == "open":
                shapes.append((sdf_ellipse((ex, ey), px(0.028 * s), px(0.036)), WHITE, OUTLINE))
                shapes.append((sdf_ellipse((ex + px(look), ey + px(0.004)), px(0.014 * s), px(0.019)), (30, 30, 35), None))
            elif variant == "half":
                shapes.append((sdf_ellipse((ex, ey + px(0.014)), px(0.028 * s), px(0.017)), WHITE, OUTLINE))
                shapes.append((sdf_ellipse((ex + px(look), ey + px(0.016)), px(0.013 * s), px(0.011)), (30, 30, 35), None))
                shapes.append((sdf_polyline([(ex - px(0.03 * s), ey - px(0.002)), (ex + px(0.03 * s), ey - px(0.002))],
                                            px(0.006)), OUTLINE, None))
            else:
                shapes.append((sdf_polyline([(ex - px(0.029 * s), ey), (ex, ey + px(0.010)), (ex + px(0.029 * s), ey)],
                                            px(0.006)), OUTLINE, None))
        if shapes:
            add(f"eyes_{variant}", "head", 32, shapes, WHITE, "eyes", variant)
    brows = [feature(sx * 0.06, 1.718) for sx in (1, -1)]
    brows = [b for b in brows if b[2] > 0.15]
    for variant, lift, arch in (("neutral", 0.0, 0.004), ("raised", 0.022, 0.012)):
        shapes = []
        for bx, by, rel in brows:
            s = max(0.4, rel)
            by -= px(lift)
            shapes.append((sdf_polyline([(bx - px(0.034 * s), by + px(0.004)), (bx, by - px(arch)),
                                         (bx + px(0.034 * s), by + px(0.004))], px(0.008)), BROW, None))
        if shapes:
            add(f"brows_{variant}", "head", 33, shapes, BROW, "brows", variant)

    mx, my, mrel = feature(0, 1.555)
    if mrel > -0.05:
        s = max(0.4, mrel)
        mx -= px(0.012 * math.sin(t))   # sit just inside the profile edge

        def E(dx, dy, rx, ry, fill, oc=OUTLINE):
            return (sdf_ellipse((mx + px(dx * s), my + px(dy)), px(rx * s), px(ry)), fill, oc)

        def L(pts, r):
            return (sdf_polyline([(mx + px(a * s), my + px(b)) for a, b in pts], px(r)), OUTLINE, None)
        shapes = {
            "X": [L([(-0.022, 0), (0.022, 0)], 0.005)],
            "A": [L([(-0.028, 0.002), (0, -0.002), (0.028, 0.002)], 0.007)],
            "B": [E(0, 0, 0.028, 0.010, DARK), E(0, -0.005, 0.022, 0.004, WHITE, None)],
            "C": [E(0, 0, 0.030, 0.020, DARK), E(0, -0.013, 0.022, 0.005, WHITE, None)],
            "D": [E(0, 0.004, 0.033, 0.034, DARK), E(0, 0.024, 0.020, 0.010, TONGUE, None)],
            "E": [E(0, 0, 0.024, 0.022, DARK)],
            "F": [E(0, 0, 0.014, 0.015, DARK)],
            "G": [E(0, 0, 0.028, 0.012, DARK), E(0, -0.004, 0.022, 0.006, WHITE, None)],
            "H": [E(0, 0, 0.030, 0.024, DARK), E(0, -0.010, 0.012, 0.007, TONGUE, None)],
            "smile": [L([(-0.035, -0.012), (-0.018, 0.004), (0, 0.008), (0.018, 0.004), (0.035, -0.012)], 0.006)],
        }
        for variant, shp in shapes.items():
            add(f"mouth_{variant}", "head", 34, shp, DARK, "mouth", variant)

    defaults = {"eyes": "open", "brows": "neutral", "mouth": "X", "hand.L": "open", "hand.R": "open"}
    bend = ({"leg.L": 1, "leg.R": 1, "arm.L": -1, "arm.R": -1} if yaw > 1 else
            {"leg.L": 1, "leg.R": -1, "arm.L": 1, "arm.R": -1})
    return {"canvas": [W, H], "ground_origin": [OX, OY], "bones": bones, "parts": parts,
            "defaults": defaults, "ik_bend": bend}


def build_props(out_dir):
    props = {}
    # Bowl: pivot = underside centre, where the palm supports it.
    layer_w, layer_h = 320, 220
    global W, H
    W0, H0 = W, H
    W, H = layer_w, layer_h
    lay = Layer()
    body = sdf_ellipse((160, 80), 122, 100)
    lay.draw(lambda X, Y: np.maximum(body[0](X, Y), 80 - Y), (38, 80, 282, 182), (150, 95, 52))
    lay.draw(*sdf_ellipse((160, 80), 126, 24), (190, 132, 72))
    lay.draw(*sdf_ellipse((160, 80), 108, 15), (244, 232, 200), None)
    off = lay.save_cropped(os.path.join(out_dir, "parts/props/bowl.png"))
    props["bowl"] = {"file": "parts/props/bowl.png", "offset": off, "pivot": [160, 180], "z": 51.5}
    # Side table the bowl rests on (set dressing; real version is background art).
    W, H = 520, 960
    lay = Layer()
    lay.draw(*sdf_box((260, 60), 230, 22, 8), (122, 86, 58))
    for lx in (70, 450):
        lay.draw(*sdf_box((lx, 500), 16, 440, 6), (104, 72, 48))
    lay.draw(*sdf_box((260, 640), 200, 10, 4), (104, 72, 48))
    off = lay.save_cropped(os.path.join(out_dir, "parts/props/table.png"))
    props["table"] = {"file": "parts/props/table.png", "offset": off, "pivot": [260, 38], "z": 15}
    W, H = W0, H0
    return props


def main():
    args = script_args()
    out = os.path.abspath(args[args.index("--out") + 1] if "--out" in args else "placeholder")
    manifest = {
        "character": "placeholder_mannequin",
        "placeholder": True,
        "note": "PLACEHOLDER parts for pipeline testing only. Not Ramu.",
        "appearance_approved": None,
        "px_per_unit": PPU,
        "views": {v: build_view(v, yaw, out) for v, yaw in VIEWS.items()},
        "props": build_props(out),
    }
    save_json(os.path.join(out, "rig_manifest.json"), manifest)
    n = sum(len(v["parts"]) for v in manifest["views"].values())
    print(f"placeholder: {n} parts in {len(VIEWS)} views -> {out}")


if __name__ == "__main__":
    main()
