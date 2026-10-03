"""Appearance preview: the sign-off sheet before any animation work.

    blender -b out/ramu_cutout_rig.blend -P scripts/preview_appearance.py -- \
        --out out/appearance_preview.png [--engine eevee|cycles]

Renders one contact sheet:
  row 1  every view at rest (full body), with the bowl prop
  row 2  face close-ups: each mouth shape, eyes open/half/closed, brows
  row 3  joint stress poses per view (elbows, knees, shoulders, neck bent)

Row 3 is the one to look at hardest: cut-out art shows gaps or bad overlaps
when a joint rotates, and that is the usual failure of a cutout rig.
"""
import json
import math
import os
import sys
import tempfile

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pilot_common import MOUTH_SHAPES, script_args, write_png  # noqa: E402

D2R = math.pi / 180
STRESS = {"upper_arm.L": 35, "forearm.L": 70, "upper_arm.R": -30, "forearm.R": -60, "head": 8,
          "thigh.L": 25, "shin.L": -50, "thigh.R": -15, "shin.R": -30, "torso": -4}


def arg(name, default=None):
    a = script_args()
    return a[a.index(name) + 1] if name in a else default


def emission_mat(name, rgb):
    mat = bpy.data.materials.new(name)
    if bpy.app.version < (5, 0, 0):
        mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    e = nt.nodes.new("ShaderNodeEmission")
    e.inputs["Color"].default_value = (*rgb, 1)
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(e.outputs[0], o.inputs["Surface"])
    return mat


def main():
    out = os.path.abspath(arg("--out", "out/appearance_preview.png"))
    scene = bpy.context.scene
    engine = arg("--engine", "eevee")
    if engine == "cycles":
        scene.render.engine = "CYCLES"
        scene.cycles.device = "CPU"
        scene.cycles.samples = 16
        scene.cycles.transparent_max_bounces = 64
        scene.cycles.use_denoising = False
    else:
        scene.render.engine = "BLENDER_EEVEE"
        scene.eevee.taa_render_samples = 16
    scene.view_settings.view_transform = "Standard"
    scene.render.film_transparent = False
    if scene.world is None:
        scene.world = bpy.data.worlds.new("w")
    if bpy.app.version < (5, 0, 0):
        scene.world.use_nodes = True
    bg = scene.world.node_tree.nodes.get("Background") if scene.world.node_tree else None
    if bg:
        bg.inputs["Color"].default_value = (0.92, 0.92, 0.9, 1)
    else:
        scene.world.color = (0.92, 0.92, 0.9)
    scene.render.image_settings.file_format = "PNG"

    rigs = {o["view"]: o for o in bpy.data.objects if o.type == "ARMATURE" and o.get("view")}
    parts = {v: [o for o in bpy.data.collections[f"VIEW_{v}"].objects if o.type == "MESH"] for v in rigs}
    props = {o["prop"]: o for o in bpy.data.objects if o.get("prop")}

    cam_data = bpy.data.cameras.new("preview_cam")
    cam_data.type = "ORTHO"
    cam = bpy.data.objects.new("preview_cam", cam_data)
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.rotation_euler = (math.pi / 2, 0, 0)

    txt_data = bpy.data.curves.new("caption", "FONT")
    txt_data.size = 0.06
    txt_data.align_x = "CENTER"
    txt = bpy.data.objects.new("caption", txt_data)
    txt.rotation_euler = (math.pi / 2, 0, 0)
    txt.data.materials.append(emission_mat("caption", (0.05, 0.05, 0.06)))
    scene.collection.objects.link(txt)

    def show(view, groups=None, pose=None):
        for v, objs in parts.items():
            defaults = json.loads(rigs[v]["defaults"])
            want = dict(defaults, **(groups or {}))
            for o in objs:
                vis = v == view and (not o.get("group") or want.get(o["group"]) == o["variant"])
                o.hide_render = not vis
        for v, rig in rigs.items():
            for pb in rig.pose.bones:
                pb.rotation_euler = (0, 0, (pose or {}).get(pb.name, 0) * D2R if v == view else 0)
                pb.location = (0, 0, 0)

    tmp = tempfile.mkdtemp(prefix="ramu_preview_")
    tiles = []

    def shoot(name, caption, cx, cz, ortho, w, h, row):
        scene.render.resolution_x, scene.render.resolution_y = w, h
        cam.location = (cx, -10, cz)
        cam_data.ortho_scale = ortho
        txt_data.body = caption
        txt_data.size = ortho * (0.045 if w > 400 else 0.075)
        txt.location = (cx, -5, cz - ortho * (h / max(w, h)) / 2 + ortho * 0.03)
        path = os.path.join(tmp, f"{name}.png")
        scene.render.filepath = path
        bpy.ops.render.render(write_still=True)
        tiles.append((row, path))

    for p in props.values():
        p.hide_render = True
    head_z = {}
    for v, rig in rigs.items():
        hb = rig.data.bones["head"]
        head_z[v] = ((hb.head_local.x + hb.tail_local.x) / 2, (hb.head_local.z + hb.tail_local.z) / 2 - 0.02)

    bowl = props.get("bowl")
    bowl_view = "front" if "front" in rigs else list(rigs)[-1]
    for v in rigs:
        show(v)
        if bowl and v == bowl_view:
            bowl.hide_render = False
            bowl.location = (0.55, 0, 0.35)
        shoot(f"rest_{v}", f"{v} - rest", 0.05, 0.95, 2.2, 520, 760, 0)
        if bowl:
            bowl.hide_render = True

    face_view = "front" if "front" in rigs else list(rigs)[-1]
    groups = {}
    for o in parts[face_view]:
        if o.get("group"):
            groups.setdefault(o["group"], set()).add(o["variant"])
    hx, hz = head_z[face_view]
    for m in [s for s in MOUTH_SHAPES + ["smile"] if s in groups.get("mouth", ())]:
        show(face_view, {"mouth": m})
        shoot(f"mouth_{m}", f"mouth {m}", hx, hz, 0.5, 300, 300, 1)
    for e in ("half", "closed"):
        if e in groups.get("eyes", ()):
            show(face_view, {"eyes": e})
            shoot(f"eyes_{e}", f"eyes {e}", hx, hz, 0.5, 300, 300, 1)
    if "raised" in groups.get("brows", ()):
        show(face_view, {"brows": "raised", "mouth": "smile" if "smile" in groups.get("mouth", ()) else "X"})
        shoot("brows_raised", "brows raised", hx, hz, 0.5, 300, 300, 1)

    for v in rigs:
        show(v, {"hand.L": "grip"}, STRESS)
        shoot(f"stress_{v}", f"{v} - joint stress", 0.05, 0.95, 2.2, 520, 760, 2)

    # Compose: rows top to bottom, white gutters.
    rows = {}
    for row, path in tiles:
        im = bpy.data.images.load(path)
        w, h = im.size
        buf = np.empty(w * h * 4, np.float32)
        im.pixels.foreach_get(buf)
        rows.setdefault(row, []).append(np.flipud(buf.reshape(h, w, 4)))
        bpy.data.images.remove(im)
    gap = 12
    row_imgs = []
    for r in sorted(rows):
        ims = rows[r]
        hmax = max(i.shape[0] for i in ims)
        wsum = sum(i.shape[1] for i in ims) + gap * (len(ims) + 1)
        canvas = np.ones((hmax + 2 * gap, wsum, 4), np.float32)
        x = gap
        for i in ims:
            canvas[gap:gap + i.shape[0], x:x + i.shape[1]] = i
            x += i.shape[1] + gap
        row_imgs.append(canvas)
    width = max(r.shape[1] for r in row_imgs)
    sheet = np.ones((sum(r.shape[0] for r in row_imgs), width, 4), np.float32)
    y = 0
    for r in row_imgs:
        sheet[y:y + r.shape[0], :r.shape[1]] = r
        y += r.shape[0]
    sheet[..., 3] = 1
    write_png(out, (np.clip(sheet, 0, 1) * 255 + 0.5).astype(np.uint8))
    build = json.loads(scene.get("pilot_build_report", "{}"))
    info = {"sheet": out, "placeholder": bool(scene.get("pilot_placeholder")), "views": list(rigs),
            "face_view": face_view, "mouth_shapes": sorted(groups.get("mouth", ())),
            "missing": {v: d.get("missing_mouth_shapes") for v, d in build.get("views", {}).items()},
            "stress_pose_deg": STRESS,
            "next": "Get sign-off, then add \"appearance_approved\": {\"by\": ..., \"date\": ...} to the manifest."}
    with open(os.path.splitext(out)[0] + ".json", "w", encoding="utf-8") as f:
        json.dump(info, f, indent=2)
    print("PREVIEW " + out)


if __name__ == "__main__":
    main()
