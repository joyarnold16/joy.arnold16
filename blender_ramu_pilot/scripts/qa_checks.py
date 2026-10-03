"""Measure the animated test from Blender's evaluated scene.

    blender -b out/ramu_test_10s.blend -P scripts/qa_checks.py -- --out out/qa_report.json

Nothing here trusts animate_test.py's intent: every number comes from
evaluated bone matrices, constraint results and sprite visibility after
frame_set(). Distances are reported in output pixels at the frame's camera
zoom. Thresholds are heuristics for catching pops and slides; a human still
has to watch the render for appeal and acting.
"""
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pilot_common import MOUTH_OPENNESS, load_json, save_json, script_args  # noqa: E402

LR = ("L", "R")


def arg(name, default=None):
    a = script_args()
    return a[a.index(name) + 1] if name in a else default


def verdict(value, warn, fail=None, higher_is_bad=True):
    if value is None:
        return "N/A"
    bad = (lambda v, t: v > t) if higher_is_bad else (lambda v, t: v < t)
    if fail is not None and bad(value, fail):
        return "FAIL"
    return "WARN" if bad(value, warn) else "PASS"


def pearson(a, b):
    n = len(a)
    if n < 3:
        return None
    ma, mb = sum(a) / n, sum(b) / n
    va = sum((x - ma) ** 2 for x in a)
    vb = sum((y - mb) ** 2 for y in b)
    if va == 0 or vb == 0:
        return None
    return sum((x - ma) * (y - mb) for x, y in zip(a, b)) / math.sqrt(va * vb)


def opaque_points(obj, step=2):
    """Object-local positions (homogeneous, Nx4) of a part's opaque pixels."""
    nodes = obj.active_material.node_tree.nodes
    img = next(n.image for n in nodes if n.type == "TEX_IMAGE")
    w, h = img.size
    buf = np.empty(w * h * 4, np.float32)
    img.pixels.foreach_get(buf)
    a = buf.reshape(h, w, 4)[::step, ::step, 3]
    ys, xs = np.nonzero(a > 0.5)
    v = [obj.data.vertices[i].co for i in range(4)]   # (x0,z0) (x1,z0) (x1,z1) (x0,z1); rows bottom-up
    u = (xs * step + 0.5) / w
    t = (ys * step + 0.5) / h
    pts = np.zeros((len(xs), 4))
    for k in range(3):
        pts[:, k] = v[0][k] + u * (v[1][k] - v[0][k]) + t * (v[3][k] - v[0][k])
    pts[:, 3] = 1.0
    return pts


def main():
    scene = bpy.context.scene
    plan = load_json(scene["pilot_plan"])
    out = os.path.abspath(arg("--out", os.path.splitext(scene["pilot_plan"])[0].replace(".plan", "") + ".qa.json"))
    rigs = {o["view"]: o for o in bpy.data.objects if o.type == "ARMATURE" and o.get("view")}
    parts = {v: [o for o in bpy.data.collections[f"VIEW_{v}"].objects if o.type == "MESH"] for v in rigs}
    bowl = bpy.data.objects.get("prop:bowl")
    cam = scene.camera
    res_x = scene.render.resolution_x
    f0, f1 = plan["frames"]

    def heel_local(rig, s):
        b = rig.data.bones[f"foot.{s}"]
        hx, hz = b["heel"] if "heel" in b else (b.head_local.x, b.head_local.z)
        return b.matrix_local.inverted() @ Vector((hx, 0.0, hz))

    heel_l = {v: {s: heel_local(r, s) for s in LR} for v, r in rigs.items()}
    contacts = {k: Vector((c[0], 0.0, c[1])) for k, c in plan["bowl"].get("contacts", {}).items()}
    table = plan["bowl"].get("table")
    hand_px = {o.name: opaque_points(o) for objs in parts.values() for o in objs
               if str(o.get("group", "")).startswith("hand.")} if table else {}
    # Contact phase only: from just before the grip until the bowl has left the table. Earlier in
    # the reach a hand hanging beside the table overlaps it in 2D without touching it (no depth).
    watch = range(plan["bowl"]["grip_frame"] - 4, plan["bowl"]["lift"][0] + 5)
    rest_z = {v: {s: {"heel": r.data.bones[f"foot.{s}"].get("heel", [0, r.data.bones[f"foot.{s}"].head_local.z])[1],
                      "toe": r.data.bones[f"foot.{s}"].tail_local.z} for s in LR} for v, r in rigs.items()}

    samples = {}
    for f in range(f0, f1 + 1):
        scene.frame_set(f)
        dg = bpy.context.evaluated_depsgraph_get()
        view = plan["views"][str(f)]
        rig = rigs[view].evaluated_get(dg)
        mw = rig.matrix_world
        pb = rig.pose.bones

        def wpt(bone, local=None, tail=False):
            m = pb[bone].matrix
            if tail:
                p = m @ Vector((0, pb[bone].length, 0))
            elif local is not None:
                p = m @ local
            else:
                p = m.translation
            w = mw @ p
            return (w.x, w.z)
        s = {"view": view, "ppu": res_x / cam.data.ortho_scale}
        for side in LR:
            s[f"toe.{side}"] = wpt(f"foot.{side}", tail=True)
            s[f"heel.{side}"] = wpt(f"foot.{side}", local=heel_l[view][side])
            s[f"palm.{side}"] = wpt(f"hand.{side}", tail=True)
            s[f"ankle.{side}"] = wpt(f"foot.{side}")
        s["hips"] = wpt("hips")
        s["head"] = wpt("head", tail=True)
        if bowl:
            bm = bowl.evaluated_get(dg).matrix_world
            s["bowl"] = (bm.translation.x, bm.translation.z)
            s["bowl_tilt"] = math.degrees(bm.to_euler().y)
            for k, c in contacts.items():
                w = bm @ c
                s[f"bowl.{k}"] = (w.x, w.z)
        if table and f in watch:
            # Lowest opaque pixel of each visible hand drawing that is over the tabletop.
            low = None
            for o in parts[view]:
                pts = hand_px.get(o.name)
                if pts is None or o.hide_render:
                    continue
                m = np.array(o.evaluated_get(dg).matrix_world)
                w = pts @ m.T
                over = (w[:, 0] >= table["x_range"][0]) & (w[:, 0] <= table["x_range"][1])
                if over.any():
                    z = float(w[over, 2].min())
                    if low is None or z < low[0]:
                        low = (z, o.name)
            s["hand_low_over_table"] = low
        vis = {}
        stray = 0
        fading_in = plan.get("crossfade", {}).get(str(f))
        for v, objs in parts.items():
            for o in objs:
                if o.hide_render:
                    continue
                if v != view:
                    if v != fading_in:      # the next view may show while it fades in for a turn
                        stray += 1
                elif o.get("group"):
                    vis.setdefault(o["group"], []).append(o["variant"])
        s["groups"], s["stray_visible"] = vis, stray
        samples[f] = s

    report = {"blend": bpy.data.filepath, "placeholder": plan["placeholder"], "frames": [f0, f1], "checks": {}}
    C = report["checks"]

    # ---- foot sliding / penetration
    slides, worst, pen = [], {"px": 0.0, "frame": None, "foot": None}, 0.0
    for side in LR:
        for f in range(f0 + 1, f1 + 1):
            a, b = samples[f - 1], samples[f]
            ca, cb = plan["contacts"][side].get(str(f - 1)), plan["contacts"][side].get(str(f))
            if a["view"] != b["view"] or not ca or not cb:
                continue
            pts = ["toe"] + (["heel"] if ca == "flat" and cb == "flat" else [])
            for p in pts:
                pa, pb_ = a[f"{p}.{side}"], b[f"{p}.{side}"]
                d = math.hypot(pb_[0] - pa[0], pb_[1] - pa[1]) * b["ppu"]
                slides.append(d)
                if d > worst["px"]:
                    worst = {"px": round(d, 3), "frame": f, "foot": side, "point": p}
        for f, smp in samples.items():
            for p in ("toe", "heel"):
                dz = (smp[f"{p}.{side}"][1] - rest_z[smp["view"]][side][p]) * smp["ppu"]
                pen = min(pen, dz)
    C["foot_sliding"] = {"max_px_per_frame": round(max(slides, default=0.0), 3),
                         "mean_px_per_frame": round(sum(slides) / max(1, len(slides)), 4),
                         "contact_frame_pairs": len(slides), "worst": worst,
                         "verdict": verdict(max(slides, default=0.0), 0.5, 2.0)}
    C["ground_penetration"] = {"lowest_px_below_rest": round(-pen, 3), "verdict": verdict(-pen, 1.0, 3.0)}
    C["leg_reach_clamps"] = {"frames": plan.get("leg_reach_clamps", []), "ik_clamps": plan.get("ik_clamps", []),
                             "verdict": "PASS" if not plan.get("leg_reach_clamps") and not plan.get("ik_clamps") else "WARN"}

    # ---- hand / bowl
    if bowl:
        b = plan["bowl"]
        g = b["grip_frame"]
        gr = b["grip"]

        def gap(f, hand, contact):
            p, c = samples[f][f"palm.{hand}"], samples[f][f"bowl.{contact}"]
            return math.hypot(p[0] - c[0], p[1] - c[1]) * samples[f]["ppu"]
        grip_gaps = {f: gap(f, gr["hand"], gr["contact"]) for f in range(g, f1 + 1)}
        pre = [math.hypot(samples[f]["bowl"][0] - samples[f0]["bowl"][0], samples[f]["bowl"][1] - samples[f0]["bowl"][1])
               * samples[f]["ppu"] for f in range(f0, g)]
        tilt = [abs(samples[f]["bowl_tilt"] - samples[g]["bowl_tilt"]) for f in range(g, f1 + 1)]
        res = {"grip": f"{gr['hand']} hand on {gr['contact']}", "gap_at_grip_px": round(grip_gaps[g], 3),
               "max_gap_after_grip_px": round(max(grip_gaps.values()), 3),
               "bowl_moved_before_grip_px": round(max(pre, default=0.0), 3), "max_bowl_tilt_deg": round(max(tilt), 3)}
        ok = grip_gaps[g] <= 2 and max(grip_gaps.values()) <= 1 + grip_gaps[g] and max(pre, default=0) <= 0.5 \
            and max(tilt) <= 1.0
        su = b.get("support")
        if su:
            arrive = su["reach"][1]
            sg = {f: gap(f, su["hand"], su["contact"]) for f in range(arrive, f1 + 1)}
            res.update({"support": f"{su['hand']} hand on {su['contact']} from frame {arrive}",
                        "support_max_gap_px": round(max(sg.values()), 3)})
            ok = ok and max(sg.values()) <= 1.0
        res["verdict"] = "PASS" if ok else "FAIL"
        C["hand_bowl_contact"] = res
    if table:
        worst = (0.0, None, None)
        for f in watch:
            low = samples[f].get("hand_low_over_table")
            if low:
                below = (table["top_z"] - low[0]) * samples[f]["ppu"]
                if below > worst[0]:
                    worst = (below, f, low[1])
        C["hand_vs_table"] = {"max_px_below_tabletop": round(worst[0], 2), "frame": worst[1], "part": worst[2],
                              "frames_checked": [watch.start, watch.stop - 1],
                              "note": "Lowest opaque pixel of the visible hand drawings over the table while the hand "
                                      "is at the bowl (grip-4 to lift start+4). Below the tabletop = hand through the table.",
                              "verdict": verdict(worst[0], 1.0, 3.0)}

    # ---- sprite groups / expressions
    viol = []
    for f, smp in samples.items():
        for grp, variants in smp["groups"].items():
            if len(variants) != 1:
                viol.append([f, grp, variants])
        expected = plan["groups"].get(str(f), {})
        for grp in expected:
            if grp not in smp["groups"] and any(o.get("group") == grp for o in parts[smp["view"]]):
                viol.append([f, grp, []])
        if smp["stray_visible"]:
            viol.append([f, "other-view parts visible", smp["stray_visible"]])
    blinks, run = [], None
    for f in range(f0, f1 + 1):
        eyes = samples[f]["groups"].get("eyes", ["open"])[0]
        if eyes != "open" and run is None:
            run = f
        if eyes == "open" and run is not None:
            blinks.append([run, f - run])
            run = None
    brows = [f for f in range(f0, f1 + 1) if samples[f]["groups"].get("brows") == ["raised"]]
    C["sprite_groups"] = {"violations": viol[:20], "violation_count": len(viol),
                          "verdict": "PASS" if not viol else "FAIL"}
    C["facial_expressions"] = {"blinks": [{"frame": b, "frames_closed_or_half": n} for b, n in blinks],
                               "brows_raised_frames": [brows[0], brows[-1]] if brows else None,
                               "verdict": "PASS" if blinks and all(2 <= n <= 6 for _, n in blinks) else "WARN"}

    # ---- mouth timing
    sp = plan.get("speech", {})
    if sp.get("audio") and plan.get("rms"):
        s0, s1 = sp["start_frame"], sp["end_frame"]
        frames = list(range(s0, min(s1, f1 + 1)))
        opening = []
        for f in frames:
            m = samples[f]["groups"].get("mouth", ["X"])
            opening.append(MOUTH_OPENNESS.get(m[0], 0.5) if len(m) == 1 else 0.5)
        rms = plan["rms"]

        def env_at(f):
            i = f - s0
            return rms[i] if 0 <= i < len(rms) else 0.0
        lags = {}
        for lag in range(-3, 4):
            lags[lag] = pearson(opening, [env_at(f + lag) for f in frames])
        best = max((l for l in lags if lags[l] is not None), key=lambda l: lags[l], default=None)
        silent_open = sum(1 for f, o in zip(frames, opening) if env_at(f) < 0.06 and o >= 0.6)
        loud_closed = sum(1 for f, o in zip(frames, opening) if env_at(f) > 0.5 and o == 0.0)
        holds, prev, n = [], None, 0
        for f in frames:
            m = samples[f]["groups"].get("mouth", ["?"])[0]
            if m == prev:
                n += 1
            else:
                if prev is not None:
                    holds.append(n)
                prev, n = m, 1
        holds.append(n)
        strip_ok = None
        se = scene.sequence_editor
        if se:
            strips = se.strips if hasattr(se, "strips") else se.sequences
            snd = [s for s in strips if s.type == "SOUND"]
            strip_ok = bool(snd) and int(snd[0].frame_start) == s0
        corr0 = lags.get(0)
        C["mouth_timing"] = {
            "cue_source": sp.get("cues_source"), "speech_frames": [s0, s1],
            "corr_openness_vs_loudness_lag0": None if corr0 is None else round(corr0, 3),
            "best_lag_frames": best, "best_lag_corr": None if best is None else round(lags[best], 3),
            "open_mouth_in_silence_frames": silent_open, "closed_mouth_when_loud_frames": loud_closed,
            "one_frame_shapes": sum(1 for h in holds if h == 1), "shape_changes": len(holds),
            "audio_strip_at_speech_start": strip_ok,
            "note": "Loudness correlation only checks timing, not phoneme accuracy; amplitude-fallback cues "
                    "will score well here and still look like lip-flap. Positive lag = mouth shape arrives "
                    "that many frames before the sound (a 1-2 frame lead is normal practice).",
            "verdict": ("PASS" if (corr0 or 0) >= 0.4 and best is not None and -1 <= best <= 2 and silent_open <= 2
                        and strip_ok is not False else "WARN"),
        }
    else:
        C["mouth_timing"] = {"verdict": "N/A", "note": "no line audio in plan"}

    # ---- smoothness: pops are isolated spikes, not large smooth motion
    # A velocity kink shows as acceleration at one frame with quiet
    # neighbours; a teleport shows as one oversized step. Big smooth moves
    # (an arm swing, a reach) have large but slowly varying acceleration.
    swaps = set(plan["swap_frames"])
    tr = plan["transitions"]
    marks = {k: v for k, v in tr.items() if isinstance(v, int)}
    for i, f in enumerate(tr.get("turn", [])):
        marks[f"turn_{i}"] = f
    chans = ["hips", "head", "palm.L", "palm.R", "toe.L", "toe.R"]
    accel, vel = {}, {}
    for f in range(f0 + 1, f1 + 1):
        if f in swaps:
            continue
        a, b = samples[f - 1], samples[f]
        for ch in chans:
            vel.setdefault(ch, {})[f] = math.hypot(b[ch][0] - a[ch][0], b[ch][1] - a[ch][1]) * b["ppu"]
    for f in range(f0 + 1, f1):
        if {f, f + 1} & swaps:
            continue
        a, b, c = samples[f - 1], samples[f], samples[f + 1]
        for ch in chans:
            ax = (c[ch][0] - 2 * b[ch][0] + a[ch][0]) * b["ppu"]
            az = (c[ch][1] - 2 * b[ch][1] + a[ch][1]) * b["ppu"]
            accel.setdefault(ch, {})[f] = math.hypot(ax, az)
    pops = []
    for ch in chans:
        A, V = accel.get(ch, {}), vel.get(ch, {})
        for f, v in A.items():
            nb = [A[g] for g in (f - 1, f + 1) if g in A]
            if nb and v > 2.0 and v > 2.5 * (sum(nb) / len(nb) + 0.5):
                pops.append({"frame": f, "channel": ch, "kind": "velocity kink", "accel_px": round(v, 2)})
        for f, v in V.items():
            nb = [V[g] for g in (f - 1, f + 1) if g in V]
            if nb and v > 3.0 and v > 3.0 * max(nb):
                pops.append({"frame": f, "channel": ch, "kind": "jump", "step_px": round(v, 2)})
    windows = {}
    for name, mf in marks.items():
        w = {}
        for ch in chans[:4]:
            vals = [(accel[ch][f], f) for f in range(mf - 3, mf + 4) if f in accel.get(ch, {})]
            if vals:
                v, fr = max(vals)
                w[ch] = {"max_px_per_frame2": round(v, 3), "frame": fr}
        windows[name] = w
    overall = {ch: round(max(accel.get(ch, {0: 0}).values()), 3) for ch in chans}
    C["transition_smoothness"] = {"pops": pops[:20], "pop_count": len(pops),
                                  "max_accel_px_per_frame2": overall, "per_transition": windows,
                                  "excluded_swap_frames": sorted(swaps),
                                  "verdict": "PASS" if not pops else "WARN"}

    # ---- view swaps (turn)
    sw = []
    for f in sorted(swaps):
        a, b = samples[f - 1], samples[f]
        ga = ((a["ankle.L"][0] + a["ankle.R"][0]) / 2)
        gb = ((b["ankle.L"][0] + b["ankle.R"][0]) / 2)
        sw.append({"frame": f, "from": a["view"], "to": b["view"],
                   "feet_centre_shift_px": round(abs(gb - ga) * b["ppu"], 2),
                   "head_top_shift_px": round(abs(b["head"][1] - a["head"][1]) * b["ppu"], 2)})
    worst_feet = max((s["feet_centre_shift_px"] for s in sw), default=0.0)
    worst_head = max((s["head_top_shift_px"] for s in sw), default=0.0)
    C["turn_alignment"] = {"swaps": sw, "verdict": "PASS" if worst_feet <= 6 and worst_head <= 12 else "WARN",
                           "note": "A drawn-view swap always pops a little; these numbers catch a character "
                                   "that jumps sideways or changes height between views."}

    report["summary"] = {k: v.get("verdict") for k, v in C.items()}
    save_json(out, report)
    md = [f"# QA report - {os.path.basename(bpy.data.filepath)}", ""]
    if plan["placeholder"]:
        md += ["**Placeholder parts - this measures the pipeline, not Ramu's look.**", ""]
    md += ["| Check | Verdict | Key numbers |", "|---|---|---|"]
    for k, v in C.items():
        nums = {kk: vv for kk, vv in v.items() if kk not in ("verdict", "note", "per_transition", "swaps",
                                                             "violations", "blinks", "worst", "pops")}
        if k == "hand_vs_table":
            nums = {kk: v[kk] for kk in ("max_px_below_tabletop", "frame", "part")}
        if k == "turn_alignment":
            nums = {f"swap@{x['frame']}": [x["feet_centre_shift_px"], x["head_top_shift_px"]] for x in v["swaps"]}
        md.append(f"| {k} | {v.get('verdict')} | {json.dumps(nums, ensure_ascii=False)[:220]} |")
    with open(os.path.splitext(out)[0] + ".md", "w", encoding="utf-8") as fh:
        fh.write("\n".join(md) + "\n")
    print("QA_SUMMARY " + json.dumps(report["summary"]))


if __name__ == "__main__":
    main()
