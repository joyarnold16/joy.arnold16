"""Build the reusable 2.5D cutout rig from a parts manifest.

    blender -b --factory-startup -P scripts/build_rig.py -- \
        --manifest rig/ramu_rig.json --out out/ramu_cutout_rig.blend

One armature per view (side_R, three_quarter, front), all parented to a
single `ramu_master` empty that sits on the ground between the feet. Every
part is a flat plane bone-parented to its bone, so a pose is just in-plane
bone rotations. Sprite groups (eyes, brows, mouth, hand.L, hand.R) are
separate planes whose visibility is switched per frame.

Full-canvas layer exports (no "offset" in the manifest) are auto-cropped to
their alpha bounds into a cache folder, which keeps texture memory down.
"""
import json
import os
import sys

import bpy
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pilot_common import REQUIRED_BONES, MOUTH_SHAPES, load_json, resolve, script_args  # noqa: E402

DEPTH_STEP = 0.002     # Blender units between draw-order layers (toward camera = -Y)


def arg(name, default=None):
    a = script_args()
    return a[a.index(name) + 1] if name in a else default


class Canvas:
    def __init__(self, view, ppu):
        self.gx, self.gy = view["ground_origin"]
        self.ppu = ppu

    def w(self, p):
        """Canvas px (y down) -> armature-space (x, z) in units."""
        return ((p[0] - self.gx) / self.ppu, (self.gy - p[1]) / self.ppu)


def load_image(path):
    img = bpy.data.images.load(path, check_existing=True)
    img.alpha_mode = "STRAIGHT"
    img.colorspace_settings.name = "sRGB"
    return img


def crop_to_alpha(path, cache_dir, pad=4):
    """Crop a full-canvas layer to its alpha bounds. Returns (path, offset)."""
    import numpy as np
    img = load_image(path)
    w, h = img.size
    buf = np.empty(w * h * 4, np.float32)
    img.pixels.foreach_get(buf)
    px = buf.reshape(h, w, 4)                        # Blender rows are bottom-up
    ys, xs = np.nonzero(px[..., 3] > 0.002)
    if len(xs) == 0:
        raise ValueError(f"{path}: layer is fully transparent")
    x0, x1 = max(xs.min() - pad, 0), min(xs.max() + pad + 1, w)
    y0, y1 = max(ys.min() - pad, 0), min(ys.max() + pad + 1, h)
    sub = np.ascontiguousarray(px[y0:y1, x0:x1])
    os.makedirs(cache_dir, exist_ok=True)
    out = os.path.join(cache_dir, os.path.basename(path))
    new = bpy.data.images.new(os.path.basename(path) + "_crop", x1 - x0, y1 - y0, alpha=True)
    new.pixels.foreach_set(sub.ravel())
    new.filepath_raw = out
    new.file_format = "PNG"
    new.save()
    bpy.data.images.remove(new)
    bpy.data.images.remove(img)
    # Offset is the crop's top-left in canvas px (y down).
    return out, [int(x0), int(h - y1)]


def flat_material(name, img):
    mat = bpy.data.materials.new(name)
    if bpy.app.version < (5, 0, 0):
        mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = img
    tex.interpolation = "Linear"
    tex.extension = "CLIP"
    emit = nt.nodes.new("ShaderNodeEmission")
    clear = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix = nt.nodes.new("ShaderNodeMixShader")
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(tex.outputs["Color"], emit.inputs["Color"])
    nt.links.new(tex.outputs["Alpha"], mix.inputs["Fac"])
    nt.links.new(clear.outputs[0], mix.inputs[1])
    nt.links.new(emit.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs["Surface"])
    for attr, val in (("surface_render_method", "DITHERED"), ("use_backface_culling", False)):
        if hasattr(mat, attr):
            setattr(mat, attr, val)
    return mat


def image_plane(name, img, corners_xz, depth, origin=(0.0, 0.0)):
    """Plane in the XZ picture plane. corners_xz = (x0, z0, x1, z1) in units."""
    x0, z0, x1, z1 = corners_xz
    ox, oz = origin
    verts = [(x0 - ox, depth, z0 - oz), (x1 - ox, depth, z0 - oz), (x1 - ox, depth, z1 - oz), (x0 - ox, depth, z1 - oz)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], [(0, 1, 2, 3)])
    uv = me.uv_layers.new(name="UVMap")
    for loop, co in zip(uv.data, [(0, 0), (1, 0), (1, 1), (0, 1)]):
        loop.uv = co
    me.materials.append(flat_material(name, img))
    obj = bpy.data.objects.new(name, me)
    return obj


def part_geometry(part, canvas, manifest_path, cache_dir):
    path = resolve(manifest_path, part["file"])
    if not os.path.exists(path):
        raise FileNotFoundError(f"part '{part['name']}': missing file {path}")
    offset = part.get("offset")
    if offset is None:
        path, offset = crop_to_alpha(path, cache_dir)
    img = load_image(path)
    w, h = img.size
    (ax, az), (bx, bz) = canvas.w((offset[0], offset[1] + h)), canvas.w((offset[0] + w, offset[1]))
    return img, (ax, az, bx, bz)


def build_view(view_name, view, ppu, manifest_path, cache_dir, master, coll):
    canvas = Canvas(view, ppu)
    missing = [b for b in REQUIRED_BONES if b not in view["bones"]]
    if missing:
        raise ValueError(f"view '{view_name}' is missing required bones: {missing}")

    arm = bpy.data.armatures.new(f"RIG_{view_name}")
    rig = bpy.data.objects.new(f"RIG_{view_name}", arm)
    coll.objects.link(rig)
    rig.parent = master
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode="EDIT")
    root = arm.edit_bones.new("root")
    root.head, root.tail = Vector((0, 0, 0)), Vector((0, 0, 0.15))
    root.align_roll(Vector((0, -1, 0)))
    root.use_deform = False
    made = {"root": root}
    pending = dict(view["bones"])
    while pending:
        progressed = False
        for name, b in list(pending.items()):
            parent = b.get("parent") or "root"
            if parent not in made:
                continue
            eb = arm.edit_bones.new(name)
            hx, hz = canvas.w(b["head"])
            tx, tz = canvas.w(b["tail"])
            if abs(hx - tx) + abs(hz - tz) < 1e-5:
                raise ValueError(f"view '{view_name}' bone '{name}' has zero length")
            eb.head, eb.tail = Vector((hx, 0, hz)), Vector((tx, 0, tz))
            eb.align_roll(Vector((0, -1, 0)))       # local Z toward camera: +rotZ = CCW on screen
            eb.parent = made[parent]
            eb.use_connect = False
            eb.use_deform = False
            made[name] = eb
            del pending[name]
            progressed = True
        if not progressed:
            raise ValueError(f"view '{view_name}': unresolved bone parents {sorted(pending)}")
    bpy.ops.object.mode_set(mode="OBJECT")

    for name, b in view["bones"].items():
        if "heel" in b:
            arm.bones[name]["heel"] = list(canvas.w(b["heel"]))
    for pb in rig.pose.bones:
        pb.rotation_mode = "XYZ"
        pb.lock_rotation = (True, True, False)
    rig["view"] = view_name
    rig["defaults"] = json.dumps(view.get("defaults", {}))
    rig["ik_bend"] = json.dumps(view.get("ik_bend", {}))
    rig.show_in_front = True

    tex_bytes = 0
    groups = {}
    for part in view["parts"]:
        bone_name = part["bone"]
        if bone_name not in arm.bones:
            raise ValueError(f"view '{view_name}' part '{part['name']}' uses unknown bone '{bone_name}'")
        img, corners = part_geometry(part, canvas, manifest_path, cache_dir)
        tex_bytes += img.size[0] * img.size[1] * 4
        obj = image_plane(f"{view_name}:{part['name']}", img, corners, -part["z"] * DEPTH_STEP)
        coll.objects.link(obj)
        bone = arm.bones[bone_name]
        obj.parent = rig
        obj.parent_type = "BONE"
        obj.parent_bone = bone_name
        obj.matrix_parent_inverse = (rig.matrix_world @ bone.matrix_local
                                     @ Matrix.Translation((0, bone.length, 0))).inverted()
        obj["view"] = view_name
        if "group" in part:
            obj["group"], obj["variant"] = part["group"], part["variant"]
            groups.setdefault(part["group"], []).append(part["variant"])
    defaults = view.get("defaults", {})
    for g, variants in groups.items():
        if defaults.get(g) not in variants:
            raise ValueError(f"view '{view_name}' group '{g}' default {defaults.get(g)!r} not in {variants}")
    for obj in coll.objects:
        if obj.get("group"):
            visible = obj["variant"] == defaults[obj["group"]]
            obj.hide_render = obj.hide_viewport = not visible
    return rig, groups, tex_bytes


def build_prop(name, prop, ppu, manifest_path, cache_dir, coll):
    path = resolve(manifest_path, prop["file"])
    offset = prop.get("offset")
    if offset is None:
        path, offset = crop_to_alpha(path, cache_dir)
    img = load_image(path)
    w, h = img.size
    pvx, pvy = prop["pivot"]
    # Prop space: units, origin at pivot, z up.
    x0, x1 = (offset[0] - pvx) / ppu, (offset[0] + w - pvx) / ppu
    z1, z0 = (pvy - offset[1]) / ppu, (pvy - offset[1] - h) / ppu
    obj = image_plane(f"prop:{name}", img, (x0, z0, x1, z1), -prop.get("z", 50) * DEPTH_STEP)
    coll.objects.link(obj)
    obj["prop"] = name
    # Named contact points (where a hand's grip point goes), in prop space units.
    obj["contacts"] = json.dumps({k: [(cx - pvx) / ppu, (pvy - cy) / ppu]
                                  for k, (cx, cy) in prop.get("contacts", {}).items()})
    return obj, w * h * 4


def validate(m, manifest_path):
    """Every unfilled field and missing file at once, instead of one crash at a time."""
    problems = []
    if not m.get("px_per_unit"):
        problems.append("px_per_unit is not set (canvas pixels per Blender unit; ~1000 for a 2k-tall canvas)")
    for v, view in m.get("views", {}).items():
        for key in ("canvas", "ground_origin"):
            if view.get(key) is None:
                problems.append(f"{v}.{key} is null")
        for b in REQUIRED_BONES:
            bone = view.get("bones", {}).get(b)
            if bone is None:
                problems.append(f"{v}: bone {b} missing")
                continue
            for key in ("head", "tail"):
                if bone.get(key) is None:
                    problems.append(f"{v}: bone {b}.{key} is null")
            if b.startswith("foot.") and bone.get("heel") is None:
                problems.append(f"{v}: bone {b}.heel is null (heel contact point, needed for foot planting)")
        for p in view.get("parts", []):
            path = resolve(manifest_path, p["file"])
            if not os.path.exists(path):
                problems.append(f"{v}: part {p['name']} file not found: {p['file']}")
    for name, prop in m.get("props", {}).items():
        if prop.get("pivot") is None:
            problems.append(f"prop {name}: pivot is null")
        for cname, c in prop.get("contacts", {}).items():
            if c is None:
                problems.append(f"prop {name}: contact point '{cname}' is null")
        if not os.path.exists(resolve(manifest_path, prop["file"])):
            problems.append(f"prop {name}: file not found: {prop['file']}")
    return problems


def main():
    manifest_path = os.path.abspath(arg("--manifest"))
    out = os.path.abspath(arg("--out", "out/ramu_cutout_rig.blend"))
    cache_dir = os.path.abspath(arg("--crop-cache", os.path.join(os.path.dirname(out), "_cropped")))
    m = load_json(manifest_path)
    problems = validate(m, manifest_path)
    if problems:
        print(f"MANIFEST NOT READY: {len(problems)} problem(s)")
        for p in problems[:200]:
            print("  - " + p)
        sys.exit(1)
    ppu = float(m["px_per_unit"])

    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o)
    scene = bpy.context.scene
    master = bpy.data.objects.new("ramu_master", None)
    master.empty_display_type = "ARROWS"
    scene.collection.objects.link(master)

    report = {"views": {}, "props": [], "texture_mb": 0.0}
    tex_total = 0
    for view_name, view in m["views"].items():
        coll = bpy.data.collections.new(f"VIEW_{view_name}")
        scene.collection.children.link(coll)
        rig, groups, tex = build_view(view_name, view, ppu, manifest_path, cache_dir, master, coll)
        tex_total += tex
        mouth = set(groups.get("mouth", []))
        report["views"][view_name] = {
            "parts": len(view["parts"]),
            "groups": {g: sorted(v) for g, v in groups.items()},
            "missing_mouth_shapes": [s for s in MOUTH_SHAPES if s not in mouth],
        }
    props_coll = bpy.data.collections.new("PROPS")
    scene.collection.children.link(props_coll)
    for name, prop in m.get("props", {}).items():
        _, tex = build_prop(name, prop, ppu, manifest_path, cache_dir, props_coll)
        tex_total += tex
        report["props"].append(name)

    first = next(iter(m["views"]))
    for view_name in m["views"]:
        if view_name != first:
            for obj in bpy.data.collections[f"VIEW_{view_name}"].objects:
                if obj.type == "MESH":
                    obj.hide_render = obj.hide_viewport = True

    report["texture_mb"] = round(tex_total / 2 ** 20, 1)
    scene["pilot_manifest"] = manifest_path
    scene["pilot_placeholder"] = bool(m.get("placeholder"))
    scene["pilot_approved"] = json.dumps(m.get("appearance_approved"))
    scene["pilot_build_report"] = json.dumps(report)
    scene.view_settings.view_transform = "Standard"   # keep the art's colours exactly
    scene.view_settings.look = "None"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=out)
    print("BUILD_REPORT " + json.dumps(report))


if __name__ == "__main__":
    main()
