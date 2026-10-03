"""Generate the 10-second test from a shot spec.

    blender -b --factory-startup out/ramu_cutout_rig.blend -P scripts/animate_test.py -- \
        --shot shots/test_10s.json --out out/ramu_test_10s.blend

Walk -> stop -> turn -> speak -> lift bowl, all baked as per-frame FK keys
on the cutout rig. Legs and the bowl arm are solved with exact planar
two-bone IK, so a planted foot (or the hand on the bowl) stays where it was
put; qa_checks.py measures that from Blender's evaluated pose rather than
trusting this script.

Refuses to animate a non-placeholder rig whose manifest has no
"appearance_approved" entry (the appearance gate), unless --allow-unapproved.
"""
import json
import math
import os
import sys

import bpy
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lipsync  # noqa: E402
from pilot_common import (MOUTH_SHAPES, angle_of, ease_in_out, hermite, lerp, load_json,  # noqa: E402
                          resolve, save_json, script_args, smoothstep, two_bone_ik, wrap_angle)

LR = ("L", "R")
D2R = math.pi / 180


def arg(name, default=None):
    a = script_args()
    return a[a.index(name) + 1] if name in a else default


def flag(name):
    return name in script_args()


def rot(v, a):
    c, s = math.cos(a), math.sin(a)
    return (v[0] * c - v[1] * s, v[0] * s + v[1] * c)


def add(a, b):
    return (a[0] + b[0], a[1] + b[1])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1])


# ------------------------------------------------------------------ rig model

class ViewRig:
    """2D model of one view's armature: rest data plus an FK/IK solver."""

    def __init__(self, rig):
        self.obj = rig
        self.name = rig["view"]
        self.defaults = json.loads(rig["defaults"])
        bend_cfg = json.loads(rig["ik_bend"])
        bones = rig.data.bones
        self.rest = {}
        for b in bones:
            self.rest[b.name] = {"head": (b.head_local.x, b.head_local.z), "tail": (b.tail_local.x, b.tail_local.z),
                                 "parent": b.parent.name if b.parent else None,
                                 "m3inv": b.matrix_local.to_3x3().inverted()}
        self.order = []
        seen = set()
        while len(self.order) < len(self.rest):
            for n, r in self.rest.items():
                if n not in seen and (r["parent"] is None or r["parent"] in seen):
                    self.order.append(n)
                    seen.add(n)
        for chain in (("thigh", "shin", "foot"), ("upper_arm", "forearm", "hand")):
            for s in LR:
                a, b_, c = (f"{x}.{s}" for x in chain)
                if self.rest[b_]["parent"] != a or self.rest[c]["parent"] != b_:
                    raise ValueError(f"{self.name}: chain {a}->{b_}->{c} must be parented in order")
        self.chain = {}
        for kind, (a, b_, c) in (("leg", ("thigh", "shin", "foot")), ("arm", ("upper_arm", "forearm", "hand"))):
            for s in LR:
                ra, rb, rc = (self.rest[f"{x}.{s}"]["head"] for x in (a, b_, c))
                v1, v2 = sub(rb, ra), sub(rc, rb)
                l1, l2 = math.hypot(*v1), math.hypot(*v2)
                d_rest = math.hypot(*sub(rc, ra))
                bend_rest = wrap_angle(angle_of(v1) - angle_of(sub(rc, ra)))
                if abs(bend_rest) > 0.5 * D2R:
                    bend = 1 if bend_rest > 0 else -1
                else:
                    bend = bend_cfg.get(f"{kind}.{s}", 1)
                self.chain[f"{kind}.{s}"] = {"bones": (f"{a}.{s}", f"{b_}.{s}", f"{c}.{s}"), "l1": l1, "l2": l2,
                                             "r1": angle_of(v1), "r2": angle_of(v2), "bend": bend,
                                             "reach": max(0.9995 * (l1 + l2), d_rest)}
        self.heel = {}
        for s in LR:
            fb = bones[f"foot.{s}"]
            self.heel[s] = tuple(fb["heel"]) if "heel" in fb else self.rest[f"foot.{s}"]["head"]
        self.parts = [o for o in bpy.data.collections[f"VIEW_{self.name}"].objects if o.type == "MESH"]
        self.groups = {}
        for o in self.parts:
            if o.get("group"):
                self.groups.setdefault(o["group"], {}).setdefault(o["variant"], []).append(o)

    def ankle_rest(self, s):
        return self.rest[f"foot.{s}"]["head"]

    def toe_rest(self, s):
        return self.rest[f"foot.{s}"]["tail"]

    def palm_vec(self, s):
        r = self.rest[f"hand.{s}"]
        return sub(r["tail"], r["head"])

    def _fk(self, deltas, hips_off):
        tot, pos = {}, {}
        for n in self.order:
            r = self.rest[n]
            p = r["parent"]
            if p is None:
                tot[n], pos[n] = deltas.get(n, 0.0), r["head"]
            else:
                tot[n] = tot[p] + deltas.get(n, 0.0)
                pos[n] = add(pos[p], rot(sub(r["head"], self.rest[p]["head"]), tot[p]))
            if n == "hips":
                pos[n] = add(pos[n], hips_off)
        return tot, pos

    def solve(self, pose):
        """pose: hips_off, rot {bone: delta}, legs/arms {side: ('ik', target, total_delta)}.

        Leg IK target = ankle, total_delta = foot rotation from rest.
        Arm IK target = palm (hand bone tail), total_delta = hand rotation from rest.
        Returns ({bone: local delta}, hips_off, clamped_chains).
        """
        hips_off = pose.get("hips_off", (0.0, 0.0))
        deltas = dict(pose.get("rot", {}))
        tot, pos = self._fk(deltas, hips_off)
        clamped = []
        for kind, key in (("leg", "legs"), ("arm", "arms")):
            for s, spec in pose.get(key, {}).items():
                if spec is None:
                    continue
                _, target, end_delta = spec
                ch = self.chain[f"{kind}.{s}"]
                a, b_, c = ch["bones"]
                if kind == "arm":
                    target = sub(target, rot(self.palm_vec(s), end_delta))
                a1, a2, _, was_clamped = two_bone_ik(pos[a], target, ch["l1"], ch["l2"], ch["bend"], ch["reach"])
                if was_clamped:
                    clamped.append(f"{kind}.{s}")
                t1, t2 = a1 - ch["r1"], a2 - ch["r2"]
                parent_tot = tot[self.rest[a]["parent"]]
                deltas[a] = wrap_angle(t1 - parent_tot)
                deltas[b_] = wrap_angle(t2 - t1)
                deltas[c] = wrap_angle(end_delta - t2)
        return deltas, hips_off, clamped

    def apply(self, deltas, hips_off, frame):
        for pb in self.obj.pose.bones:
            pb.rotation_euler = (0.0, 0.0, deltas.get(pb.name, 0.0))
            pb.keyframe_insert("rotation_euler", index=2, frame=frame)
            if pb.name == "hips":
                pb.location = self.rest["hips"]["m3inv"] @ Vector((hips_off[0], 0.0, hips_off[1]))
                pb.keyframe_insert("location", frame=frame)


# ------------------------------------------------------------------ the shot

class Shot:
    def __init__(self, spec, rigs, cues, speech_frames, env):
        self.spec, self.rigs = spec, rigs
        self.fs = spec.get("facing", 1)
        w = spec["walk"]
        self.w = w
        self.f0, self.N, self.T = w["start_frame"], w["steps"], w["frames_per_step"]
        self.Sw, self.Rr, self.S = w["swing_frames"], w["heel_roll_frames"], w["step_length"]
        self.lead = w.get("lead_foot", "R")
        self.trail = "L" if self.lead == "R" else "R"
        self.land = [self.f0 + i * self.T for i in range(1, self.N + 1)]
        self.steps = {"L": [], "R": []}
        for i, t in enumerate(self.land, start=1):
            foot = self.lead if i % 2 else self.trail
            dest = i * self.S if i < self.N else (self.N - 1) * self.S
            self.steps[foot].append({"land": t, "lift": t - self.Sw, "heel_off": t - self.Sw - self.Rr, "dest": dest})
        self.travel = (self.N - 1) * self.S * self.fs
        knots = [(self.f0, 0.0, 0.0)]
        for i, t in enumerate(self.land[:-1], start=1):
            knots.append((t, (i - 0.5) * self.S, self.S / self.T))
        knots.append((self.land[-1], (self.N - 1) * self.S, 0.0))
        self.knots = knots
        self.views = [(spec["frame_start"], spec["start_view"])]
        for item in spec["turn"]["sequence"]:
            if item["view"] in rigs:
                self.views.append((item["frame"], item["view"]))
            else:
                print(f"WARN: view '{item['view']}' not in manifest; turn skips it")
        self.cues, self.speech_frames, self.env = cues, speech_frames, env
        bowl = bpy.data.objects.get("prop:bowl")
        self.contacts = json.loads(bowl.get("contacts", "{}")) if bowl else {}
        self.contacts.setdefault("pivot", [0.0, 0.0])
        self.clamps = 0
        self.foot_contacts = {}

    # -- body travel and timing envelopes
    def master_x(self, f):
        k = self.knots
        if f <= k[0][0]:
            return 0.0
        if f >= k[-1][0]:
            return self.travel
        for (t0, p0, v0), (t1, p1, v1) in zip(k, k[1:]):
            if t0 <= f <= t1:
                h = t1 - t0
                return self.fs * hermite(p0, p1, v0 * h, v1 * h, (f - t0) / h)

    def walk_env(self, f, tail=4):
        up = smoothstep((f - self.f0) / self.T)
        down = 1 - smoothstep((f - self.land[-2]) / (self.land[-1] + tail - self.land[-2]))
        return up * down

    def view_at(self, f):
        v = self.views[0][1]
        for frame, name in self.views:
            if f >= frame:
                v = name
        return v

    # -- feet (side view walk)
    def foot_state(self, rig, s, f):
        """(ankle armature-space-in-world, foot delta, contact) for the walking view."""
        A0, T0 = rig.ankle_rest(s), rig.toe_rest(s)
        roll = self.w["heel_roll_deg"] * D2R * -self.fs
        toe_up = self.w["toe_up_deg"] * D2R * self.fs
        planted = 0.0
        for st in self.steps[s]:
            if f >= st["land"]:
                planted = st["dest"]
                continue
            if st["heel_off"] <= f < st["lift"]:
                u = smoothstep((f - st["heel_off"]) / self.Rr)
                toe = (T0[0] + planted * self.fs, T0[1])
                return add(toe, rot(sub(A0, T0), roll * u)), roll * u, "toe"
            if st["lift"] <= f < st["land"]:
                u = (f - st["lift"]) / self.Sw
                toe = (T0[0] + planted * self.fs, T0[1])
                start = add(toe, rot(sub(A0, T0), roll))
                end = (A0[0] + st["dest"] * self.fs, A0[1])
                x = lerp(start[0], end[0], ease_in_out(u))
                # sin^2 lift: zero vertical speed at toe-off and heel strike (no hard landing).
                z = lerp(start[1], end[1], smoothstep(u)) + self.w["lift_height"] * math.sin(math.pi * u) ** 2
                if u < 0.8:
                    ang = lerp(roll, toe_up, smoothstep(u / 0.8))
                else:
                    ang = lerp(toe_up, 0.0, smoothstep((u - 0.8) / 0.2))
                return (x, z), ang, None
            break
        return (A0[0] + planted * self.fs, A0[1]), 0.0, "flat"

    # -- per-frame pose
    def pose(self, f):
        sp, w = self.spec, self.w
        view = self.view_at(f)
        rig = self.rigs[view]
        mx = self.master_x(f)
        env = self.walk_env(f)
        T = self.T
        bob = 0.5 + 0.5 * math.cos(2 * math.pi * (f - self.f0 - 1) / T)
        dz = -(w["crouch"] + w["bob"] * bob) * env
        a, b = sp["turn"]["dip_frames"]
        if a <= f <= b:
            dz -= sp["turn"]["dip"] * math.sin(math.pi * (f - a) / (b - a)) ** 2

        rots, legs, arms = {}, {}, {}
        lean = w["lean_deg"] * D2R
        t_stop = self.land[-1]
        if f <= t_stop:
            lean_amt = lean * smoothstep((f - self.f0 + 4) / self.T)
        else:
            tau = f - t_stop
            lean_amt = lean * (1 - smoothstep(tau / 12)) + 0.8 * lean * (math.sin(math.pi * min(tau, 12) / 12) ** 2)
        side_view = view == sp["start_view"]
        if side_view:
            rots["torso"] = -self.fs * lean_amt
            rots["head"] = self.fs * lean_amt * 0.5
            arm_env = smoothstep((f - self.f0) / T) * (1 - smoothstep((f - self.land[-2]) / (t_stop + 8 - self.land[-2])))
            swing = -math.cos(math.pi * (f - self.land[0]) / T) * w["arm_swing_deg"] * D2R * arm_env
            soft = 3 * D2R
            for s, sgn in ((self.lead, 1), (self.trail, -1)):
                sw = swing * sgn
                rots[f"upper_arm.{s}"] = self.fs * sw
                # Elbow bends more on the forward swing. Smooth max(0, sw): a hard max
                # put a velocity kink in the hand at every zero crossing (QA caught it).
                fwd = 0.5 * (sw + math.sqrt(sw * sw + soft * soft) - soft)
                rots[f"forearm.{s}"] = self.fs * (5 * D2R * arm_env + 0.6 * fwd)
        else:
            rots["torso"] = 0.3 * D2R * math.sin(2 * math.pi * f / 72)
            rots["head"] = self.head_accent(f)

        # Feet: walking view follows the step schedule, other views stay planted.
        for s in LR:
            if side_view:
                ankle_w, fdelta, contact = self.foot_state(rig, s, f)
            else:
                ankle_w, fdelta, contact = rig.ankle_rest(s), 0.0, "flat"
            # Walk positions are world-relative-to-shot-start; the rig rides on the master.
            ankle = (ankle_w[0] - mx, ankle_w[1]) if side_view else ankle_w
            legs[s] = ("ik", ankle, fdelta)
            self.foot_contacts.setdefault(s, {})[f] = contact
            if contact:
                ch = rig.chain[f"leg.{s}"]
                hj = rig.rest[ch["bones"][0]]["head"]
                dx = ankle[0] - hj[0]
                lmax = 0.995 * (ch["l1"] + ch["l2"])
                if abs(dx) < lmax:
                    cap = ankle[1] + math.sqrt(lmax * lmax - dx * dx) - hj[1]
                    if dz > cap and side_view:
                        dz = cap
                        self.clamps += 1

        # Bowl: grip hand reaches its contact on the bowl and lifts; support hand comes in under it.
        bw = sp["bowl"]
        gr, su = bw["grip"], bw.get("support")
        r0 = bw["reach"][0]
        l0, l1 = bw["lift"]
        if not side_view and f >= r0:
            lean_r = bw["reach_lean_deg"] * D2R
            sgn = 1 if bw["rest"][0] > 0 else -1
            if f <= bw["grip_frame"]:
                lean_now = lean_r * ease_in_out((f - r0) / max(1, bw["grip_frame"] - r0))
            else:
                lean_now = lean_r * (1 - ease_in_out((f - l0) / max(1, l1 - l0)))
            rots["torso"] = rots.get("torso", 0.0) - sgn * lean_now
            arms[gr["hand"]] = ("ik",) + self.grip_target(rig, f, rots, (0.0, dz))
            if su and f >= su["reach"][0]:
                arms[su["hand"]] = ("ik",) + self.support_target(rig, f, rots, (0.0, dz))
        return {"view": view, "mx": mx, "pose": {"hips_off": (0.0, dz), "rot": rots, "legs": legs, "arms": arms}}

    def bowl_pos(self, f):
        """Where the bowl's pivot (base centre) is meant to be, relative to the stop position."""
        bw = self.spec["bowl"]
        R, H = tuple(bw["rest"]), tuple(bw["hold"])
        l0, l1 = bw["lift"]
        if f <= l0:
            return R
        u = ease_in_out((f - l0) / max(1, l1 - l0))
        # Settle bump after arrival; sin^2 keeps velocity continuous at both ends.
        x = (f - (l1 - 4)) / 12.0
        over = bw["overshoot"] * math.sin(math.pi * x) ** 2 if 0 <= x <= 1 else 0.0
        return (lerp(R[0], H[0], u), lerp(R[1], H[1], u) + over)

    def contact(self, name):
        if name not in self.contacts:
            raise ValueError(f"bowl has no contact point '{name}' (has: {sorted(self.contacts)})")
        return tuple(self.contacts[name])

    def _reach(self, rig, s, f, rots, hips_off, f0, f1, target, angle_deg, approach):
        """Hand grip point from its FK-rest position onto `target(f)`.

        The path is a quadratic Bezier whose control point sits at `approach`
        (dx, dz) from the target, so the hand arrives from that side: from
        above onto a rim, from below under a base. Eased, so IK takes over
        from the rest pose without a pop and lands without a bump.
        """
        total = wrap_angle(angle_deg * D2R - angle_of(rig.palm_vec(s)))
        if f >= f1:
            return target(f), total
        tot, pos = rig._fk(rots, hips_off)
        hb = f"hand.{s}"
        p0 = add(pos[hb], rot(rig.palm_vec(s), tot[hb]))
        u = ease_in_out((f - f0) / max(1, f1 - f0))
        t = target(f)
        c = add(t, approach)
        p = tuple((1 - u) ** 2 * a + 2 * u * (1 - u) * b + u * u * e for a, b, e in zip(p0, c, t))
        return p, lerp(tot[hb], total, u)

    def grip_target(self, rig, f, rots, hips_off):
        bw = self.spec["bowl"]
        gr = bw["grip"]
        c = self.contact(gr["contact"])
        return self._reach(rig, gr["hand"], f, rots, hips_off, bw["reach"][0], bw["grip_frame"],
                           lambda fr: add(self.bowl_pos(fr), c), gr["hand_angle_deg"],
                           tuple(gr.get("approach", (0.0, 0.15))))

    def support_target(self, rig, f, rots, hips_off):
        su = self.spec["bowl"]["support"]
        c = self.contact(su["contact"])
        return self._reach(rig, su["hand"], f, rots, hips_off, su["reach"][0], su["reach"][1],
                           lambda fr: add(self.bowl_pos(fr), c), su["hand_angle_deg"],
                           tuple(su.get("approach", (0.0, -0.12))))

    def head_accent(self, f):
        acc = self.spec["line"].get("head_accent_deg", 0) * D2R
        out = 0.0
        for i, pf in enumerate(self.env.get("peaks", [])):
            out += acc * (1 if i % 2 == 0 else -1) * math.exp(-((f - pf) / 4.0) ** 2)
        return out

    # -- sprite groups
    def groups_at(self, f, view):
        rig = self.rigs[view]
        ex = self.spec["expressions"]
        g = dict(rig.defaults)
        for b in ex.get("blinks", []):
            if f in (b, b + 3):
                g["eyes"] = "half"
            elif b < f < b + 3:
                g["eyes"] = "closed"
        for a, b in ex.get("brows_raised", []):
            if a <= f < b:
                g["brows"] = "raised"
        if f in self.speech_frames:
            g["mouth"] = self.speech_frames[f]
        elif f >= ex.get("smile_from", 10 ** 9):
            g["mouth"] = "smile"
        bw = self.spec["bowl"]
        if f >= bw["grip_frame"] - 1:
            g[f"hand.{bw['grip']['hand']}"] = bw["grip"].get("variant", "grip")
        su = bw.get("support")
        if su and f >= su["reach"][1] - 2:
            g[f"hand.{su['hand']}"] = su.get("variant", "support")
        for grp, variant in list(g.items()):
            if grp in rig.groups and variant not in rig.groups[grp]:
                g[grp] = rig.defaults.get(grp)
        return g


# ------------------------------------------------------------------ scene setup

def fcurves_of(id_data):
    """F-curves of an ID's action (slotted actions in 4.4+/5.x, legacy before)."""
    ad = id_data.animation_data
    if not ad or not ad.action:
        return []
    if hasattr(ad, "action_slot"):
        from bpy_extras import anim_utils
        bag = anim_utils.action_get_channelbag_for_slot(ad.action, ad.action_slot)
        return list(bag.fcurves) if bag else []
    return list(ad.action.fcurves)


def make_constant(id_data, prefixes):
    """Python keyframe_insert ignores the new-key interpolation preference in
    5.2, so step keys (visibility, constraint switches) are set explicitly."""
    for fc in fcurves_of(id_data):
        if fc.data_path.startswith(prefixes):
            for k in fc.keyframe_points:
                k.interpolation = "CONSTANT"


def set_engine(scene, name, rspec):
    if name == "cycles":
        scene.render.engine = "CYCLES"
        scene.cycles.samples = rspec.get("cycles_samples", 16)
        scene.cycles.use_denoising = False
        scene.cycles.transparent_max_bounces = 64
        scene.cycles.max_bounces = 0
        return "CYCLES"
    if name == "workbench":
        scene.render.engine = "BLENDER_WORKBENCH"
        return "BLENDER_WORKBENCH"
    for ident in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT"):
        try:
            scene.render.engine = ident
            break
        except TypeError:
            continue
    scene.eevee.taa_render_samples = rspec.get("eevee_samples", 16)
    return scene.render.engine


def flat_mat(name, rgb):
    mat = bpy.data.materials.new(name)
    if bpy.app.version < (5, 0, 0):
        mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    e = nt.nodes.new("ShaderNodeEmission")
    e.inputs["Color"].default_value = (*srgb_to_linear(rgb), 1.0)
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(e.outputs[0], o.inputs["Surface"])
    return mat


def srgb_to_linear(rgb):
    return tuple(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in rgb)


def quad(name, x0, z0, x1, z1, y, mat):
    me = bpy.data.meshes.new(name)
    me.from_pydata([(x0, y, z0), (x1, y, z0), (x1, y, z1), (x0, y, z1)], [], [(0, 1, 2, 3)])
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def build_set(spec, travel):
    r = spec["render"]
    lo, hi = min(0.0, travel) - 4, max(0.0, travel) + 4
    quad("set:backdrop", lo, -3, hi, 6, 0.6, flat_mat("backdrop", r["backdrop_color"]))
    quad("set:ground", lo, -3, hi, 0.0, 0.5, flat_mat("ground", r["ground_color"]))
    if r.get("ground_ticks"):
        tick = flat_mat("tick", [c * 0.7 for c in r["ground_color"]])
        x = math.floor(lo * 4) / 4
        while x <= hi:
            quad(f"set:tick{x:+.2f}", x - 0.004, -0.05, x + 0.004, 0.0, 0.45, tick)
            x += 0.25


def camera_track(spec, travel):
    keys = spec["camera"]
    cam_data = bpy.data.cameras.new("cam")
    cam_data.type = "ORTHO"
    cam_data.clip_start, cam_data.clip_end = 0.1, 30
    cam = bpy.data.objects.new("cam", cam_data)
    bpy.context.scene.collection.objects.link(cam)
    bpy.context.scene.camera = cam
    cam.rotation_euler = (math.pi / 2, 0, 0)

    def val(k):
        base = 0.0 if k["anchor"] == "start" else travel
        return base + k["dx"], k["z"], k["ortho"]
    for f in range(spec["frame_start"], spec["frame_end"] + 1):
        prev = max([k for k in keys if k["frame"] <= f], key=lambda k: k["frame"])
        nxt = [k for k in keys if k["frame"] > f]
        if nxt:
            nxt = min(nxt, key=lambda k: k["frame"])
            u = ease_in_out((f - prev["frame"]) / (nxt["frame"] - prev["frame"]))
            x, z, o = (lerp(a, b, u) for a, b in zip(val(prev), val(nxt)))
        else:
            x, z, o = val(prev)
        cam.location = (x, -10.0, z)
        cam_data.ortho_scale = o
        cam.keyframe_insert("location", frame=f)
        cam_data.keyframe_insert("ortho_scale", frame=f)
    return cam


def add_sound(scene, path, frame):
    se = scene.sequence_editor_create()
    coll = se.strips if hasattr(se, "strips") else se.sequences
    coll.new_sound("line", path, 1, frame)


def load_speech(spec, shot_path, rigs):
    line = spec.get("line") or {}
    # Command-line paths are relative to the working directory; spec paths to the shot file.
    audio = os.path.abspath(arg("--audio")) if arg("--audio") else resolve(shot_path, line.get("audio"))
    cues_path = os.path.abspath(arg("--cues")) if arg("--cues") else resolve(shot_path, line.get("cues"))
    info = {"audio": audio if audio and os.path.exists(audio) else None, "cues_source": None,
            "start_frame": line.get("start_frame"), "duration_s": None}
    if info["audio"] is None:
        print("WARN: no line audio found; mouth stays at rest")
        return {}, info, {"peaks": []}
    samples, sr = lipsync.read_wav_mono(audio)
    info["duration_s"] = round(len(samples) / sr, 3)
    if cues_path and os.path.exists(cues_path):
        cues = lipsync.load_cues(cues_path)
        info["cues_source"] = cues.get("metadata", {}).get("source", "rhubarb")
        info["cues_file"] = cues_path
    else:
        cues = lipsync.amplitude_cues(samples, sr, spec["fps"])
        info["cues_source"] = "amplitude-fallback"
        out = os.path.splitext(audio)[0] + ".amplitude.json"
        save_json(out, cues)
        info["cues_file"] = out
    talk_view = [v for f, v in sorted(spec_views(spec)) if f <= line["start_frame"] and v in rigs][-1]
    available = set(rigs[talk_view].groups.get("mouth", {}))
    frames = lipsync.frame_shapes(cues, spec["fps"], line["start_frame"], available)
    end = line["start_frame"] + int(math.ceil(info["duration_s"] * spec["fps"]))
    info["end_frame"] = end
    info["talk_view"] = talk_view
    info["missing_shapes"] = [s for s in MOUTH_SHAPES if s not in available]
    env = lipsync.rms_envelope(samples, sr, spec["fps"])
    peaks = []
    for i in range(1, len(env) - 1):
        if env[i] > 0.6 and env[i] >= env[i - 1] and env[i] >= env[i + 1]:
            if not peaks or i - peaks[-1] >= 8:
                peaks.append(i)
    peaks = sorted(sorted(peaks, key=lambda i: -env[i])[:3])
    return frames, info, {"peaks": [line["start_frame"] + p for p in peaks], "rms": [float(e) for e in env]}


def spec_views(spec):
    return [(spec["frame_start"], spec["start_view"])] + [(i["frame"], i["view"]) for i in spec["turn"]["sequence"]]


def main():
    shot_path = os.path.abspath(arg("--shot"))
    out = os.path.abspath(arg("--out", "out/ramu_test_10s.blend"))
    spec = load_json(shot_path)
    scene = bpy.context.scene
    placeholder = bool(scene.get("pilot_placeholder"))
    approved = json.loads(scene.get("pilot_approved", "null"))
    if not placeholder and not approved and not flag("--allow-unapproved"):
        sys.exit("STOP: appearance not approved. Render preview_appearance.py, get sign-off, then set "
                 "\"appearance_approved\": {\"by\": ..., \"date\": ...} in the rig manifest and rebuild.")

    rigs = {o["view"]: ViewRig(o) for o in bpy.data.objects if o.type == "ARMATURE" and o.get("view")}
    if spec["start_view"] not in rigs:
        sys.exit(f"start view '{spec['start_view']}' not in rig")
    speech, speech_info, env = load_speech(spec, shot_path, rigs)
    shot = Shot(spec, rigs, None, speech, env)
    if speech_info.get("end_frame") and speech_info["end_frame"] > spec["bowl"]["reach"][0]:
        print(f"WARN: line ends at frame {speech_info['end_frame']}, after the reach starts "
              f"({spec['bowl']['reach'][0]}); they will overlap")

    scene.render.fps, scene.render.fps_base = spec["fps"], 1.0
    scene.frame_start, scene.frame_end = spec["frame_start"], spec["frame_end"]
    scene.render.resolution_x, scene.render.resolution_y = spec["resolution"]
    scene.render.resolution_percentage = 100
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.render.use_sequencer = False
    engine = set_engine(scene, arg("--engine", spec["render"]["engine"]), spec["render"])

    master = bpy.data.objects["ramu_master"]
    frames = range(spec["frame_start"], spec["frame_end"] + 1)
    view_timeline, clamp_frames, arm_clamps = {}, [], []
    for f in frames:
        st = shot.pose(f)
        view_timeline[f] = st["view"]
        master.location = (st["mx"], 0.0, 0.0)
        master.keyframe_insert("location", index=0, frame=f)
        before = shot.clamps
        for name, rig in rigs.items():
            if name == st["view"]:
                deltas, hips_off, clamped = rig.solve(st["pose"])
                if clamped:
                    arm_clamps.append([f, clamped])
            else:
                deltas, hips_off = {}, (0.0, 0.0)
            rig.apply(deltas, hips_off, f)
        if shot.clamps != before:
            clamp_frames.append(f)

    # Sprite visibility (step keys at changes only).
    group_track = {}
    for name, rig in rigs.items():
        last = {}
        for f in frames:
            active = view_timeline[f] == name
            g = shot.groups_at(f, name)
            if active:
                group_track[f] = g
            for o in rig.parts:
                vis = active and (not o.get("group") or g.get(o["group"]) == o["variant"])
                if last.get(o.name) != vis:
                    o.hide_render = o.hide_viewport = not vis
                    o.keyframe_insert("hide_render", frame=f)
                    o.keyframe_insert("hide_viewport", frame=f)
                    last[o.name] = vis
        for o in rig.parts:
            make_constant(o, ("hide_",))

    # Bowl and table.
    bw = spec["bowl"]
    final_x = shot.travel
    table = bpy.data.objects.get("prop:table")
    if table:
        table.location = (final_x + bw["table"][0], 0.0, bw["table"][1])
    bowl = bpy.data.objects.get("prop:bowl")
    grip_view = view_timeline[bw["grip_frame"]]
    if bowl:
        bowl.location = (final_x + bw["rest"][0], 0.0, bw["rest"][1])
        con = bowl.constraints.new("CHILD_OF")
        con.target = rigs[grip_view].obj
        con.subtarget = f"hand.{bw['grip']['hand']}"
        scene.frame_set(bw["grip_frame"])
        dg = bpy.context.evaluated_depsgraph_get()
        r_eval = rigs[grip_view].obj.evaluated_get(dg)
        con.inverse_matrix = (r_eval.matrix_world @ r_eval.pose.bones[con.subtarget].matrix).inverted()
        con.influence = 0.0
        con.keyframe_insert("influence", frame=spec["frame_start"])
        con.influence = 1.0
        con.keyframe_insert("influence", frame=bw["grip_frame"])
        make_constant(bowl, ("constraints",))

    table_info = None
    if table:
        scene.frame_set(spec["frame_start"])
        corners = [table.matrix_world @ v.co for v in table.data.vertices]
        table_info = {"top_z": table.location.z, "x_range": [min(c.x for c in corners), max(c.x for c in corners)]}

    build_set(spec, final_x)
    camera_track(spec, final_x)
    if speech_info.get("audio"):
        add_sound(scene, speech_info["audio"], spec["line"]["start_frame"])
    r = scene.render
    r.use_stamp = bool(spec.get("stamp_frame_numbers") or placeholder)
    for attr in ("use_stamp_date", "use_stamp_time", "use_stamp_render_time", "use_stamp_camera",
                 "use_stamp_lens", "use_stamp_scene", "use_stamp_filename", "use_stamp_marker",
                 "use_stamp_frame_range", "use_stamp_memory", "use_stamp_hostname", "use_stamp_sequencer_strip"):
        if hasattr(r, attr):
            setattr(r, attr, False)
    r.use_stamp_frame = True
    r.use_stamp_note = placeholder
    r.stamp_note_text = "PLACEHOLDER PARTS - PIPELINE TEST - NOT RAMU" if placeholder else ""
    r.stamp_font_size = 28

    plan = {
        "shot": shot_path, "fps": spec["fps"], "frames": [spec["frame_start"], spec["frame_end"]],
        "resolution": spec["resolution"], "engine": engine, "placeholder": placeholder,
        "views": {str(f): v for f, v in view_timeline.items()},
        "swap_frames": [f for f in frames if f > spec["frame_start"] and view_timeline[f] != view_timeline[f - 1]],
        "contacts": {s: {str(f): c for f, c in d.items()} for s, d in shot.foot_contacts.items()},
        "walk": {"start": shot.f0, "landings": shot.land, "travel": final_x},
        "bowl": {"grip": bw["grip"], "support": bw.get("support"), "grip_frame": bw["grip_frame"],
                 "reach": bw["reach"], "lift": bw["lift"], "view": grip_view,
                 "contacts": shot.contacts, "table": table_info},
        "speech": speech_info, "mouth": {str(f): s for f, s in speech.items()},
        "rms": env.get("rms", []), "head_accent_frames": env.get("peaks", []),
        "groups": {str(f): g for f, g in group_track.items()},
        "transitions": {"walk_start": shot.f0, "walk_stop": shot.land[-1],
                        "turn": [f for f, _ in shot.views[1:]], "speech_start": spec["line"]["start_frame"],
                        "speech_end": speech_info.get("end_frame"), "reach_start": bw["reach"][0],
                        "grip": bw["grip_frame"], "lift_end": bw["lift"][1],
                        **({"support_start": bw["support"]["reach"][0], "support_contact": bw["support"]["reach"][1]}
                           if bw.get("support") else {})},
        "leg_reach_clamps": clamp_frames, "ik_clamps": arm_clamps,
    }
    plan_path = os.path.splitext(out)[0] + ".plan.json"
    save_json(plan_path, plan)
    scene["pilot_plan"] = plan_path
    scene.frame_set(spec["frame_start"])
    bpy.ops.wm.save_as_mainfile(filepath=out)
    print(f"ANIMATE ok: {out} engine={engine} clamps={len(clamp_frames)} ik_clamps={len(arm_clamps)} "
          f"speech={speech_info.get('cues_source')}")


if __name__ == "__main__":
    main()
