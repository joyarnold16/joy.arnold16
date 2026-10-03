"""Render the animated test and record real render time and memory.

    blender -b out/ramu_test_10s.blend -P scripts/render_bench.py -- \
        --out-dir out/render [--engine eevee|cycles] [--percent 100] [--frames 0:239] \
        [--cycles-device GPU|CPU] [--require-idle-gpu] [--video ffmpeg|blender|none]

Writes PNG frames, an MP4 with the line audio placed at its start frame, and
render_report.json with per-frame times, peak process RAM and (NVIDIA only)
VRAM. VRAM from nvidia-smi is system-wide, so the report keeps the baseline
taken before rendering next to the peak.

House rule: one heavy GPU job at a time. Other processes holding >1 GB of
VRAM (e.g. ComfyUI with models loaded) are listed in the report; with
--require-idle-gpu the render refuses to start instead.
"""
import json
import os
import platform
import shutil
import statistics
import subprocess
import sys
import time

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pilot_common import GpuMemoryMonitor, load_json, other_gpu_processes, peak_rss_mb, save_json, script_args  # noqa: E402


def arg(name, default=None):
    a = script_args()
    return a[a.index(name) + 1] if name in a else default


def set_engine(scene, name, samples_eevee, samples_cycles, device):
    if name == "cycles":
        scene.render.engine = "CYCLES"
        scene.cycles.samples = samples_cycles
        scene.cycles.use_denoising = False
        scene.cycles.transparent_max_bounces = 64
        scene.cycles.max_bounces = 0
        if device == "GPU":
            prefs = bpy.context.preferences.addons["cycles"].preferences
            for kind in ("OPTIX", "CUDA", "HIP", "ONEAPI", "METAL"):
                try:
                    prefs.compute_device_type = kind
                    prefs.get_devices()
                    if any(d.type == kind for d in prefs.devices):
                        for d in prefs.devices:
                            d.use = d.type == kind
                        scene.cycles.device = "GPU"
                        return f"CYCLES/{kind}"
                except TypeError:
                    continue
        scene.cycles.device = "CPU"
        return "CYCLES/CPU"
    for ident in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT"):
        try:
            scene.render.engine = ident
            break
        except TypeError:
            continue
    scene.eevee.taa_render_samples = samples_eevee
    return scene.render.engine


def gpu_renderer():
    try:
        import gpu
        return f"{gpu.platform.renderer_get()} ({gpu.platform.backend_type_get()})"
    except Exception as e:  # no GPU context (e.g. Cycles CPU)
        return f"unavailable: {e}"


def mux_ffmpeg(frames_dir, start, fps, audio, audio_frame, out):
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(fps), "-start_number", str(start),
           "-i", os.path.join(frames_dir, "%04d.png")]
    if audio:
        delay_ms = int(round((audio_frame - start) / fps * 1000))
        cmd += ["-i", audio, "-filter_complex", f"[1:a]adelay={delay_ms}:all=1[a]", "-map", "0:v", "-map", "[a]",
                "-c:a", "aac", "-b:a", "192k"]
    cmd += ["-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18", "-movflags", "+faststart", out]
    subprocess.run(cmd, check=True)


def mux_blender(frames_dir, start, end, fps, res, audio, audio_frame, out):
    """Fallback when ffmpeg is not on PATH: Blender's own sequencer encodes."""
    sc = bpy.data.scenes.new("mux")
    sc.render.fps, sc.render.fps_base = fps, 1.0
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.render.resolution_percentage = 100
    sc.frame_start, sc.frame_end = start, end
    se = sc.sequence_editor_create()
    strips = se.strips if hasattr(se, "strips") else se.sequences
    files = sorted(f for f in os.listdir(frames_dir) if f.endswith(".png"))
    img = strips.new_image("frames", os.path.join(frames_dir, files[0]), 1, start)
    for f in files[1:]:
        img.elements.append(f)
    if audio:
        strips.new_sound("line", audio, 2, audio_frame)
    s = sc.render.image_settings
    if hasattr(s, "media_type"):
        s.media_type = "VIDEO"
    s.file_format = "FFMPEG"
    sc.render.ffmpeg.format = "MPEG4"
    sc.render.ffmpeg.codec = "H264"
    sc.render.ffmpeg.audio_codec = "AAC" if audio else "NONE"
    sc.render.ffmpeg.constant_rate_factor = "HIGH"
    sc.render.use_sequencer = True
    sc.render.filepath = out
    _render_scene(sc)
    produced = [p for p in os.listdir(os.path.dirname(out)) if p.startswith(os.path.basename(out))]
    if produced and produced[0] != os.path.basename(out):
        os.replace(os.path.join(os.path.dirname(out), produced[0]), out)


def _render_scene(sc):
    win = bpy.context.window
    if win:
        with bpy.context.temp_override(window=win, scene=sc):
            bpy.ops.render.render(animation=True, scene=sc.name)
    else:
        bpy.ops.render.render(animation=True, scene=sc.name)


def main():
    scene = bpy.context.scene
    plan = load_json(scene["pilot_plan"])
    shot = load_json(plan["shot"])
    out_dir = os.path.abspath(arg("--out-dir", os.path.join(os.path.dirname(bpy.data.filepath), "render")))
    frames_dir = os.path.join(out_dir, "frames")
    os.makedirs(frames_dir, exist_ok=True)
    for f in os.listdir(frames_dir):
        if f.endswith(".png"):
            os.remove(os.path.join(frames_dir, f))

    busy = other_gpu_processes()
    if busy and "--require-idle-gpu" in script_args():
        sys.exit(f"STOP: GPU busy (one heavy GPU job at a time): {busy}")

    rs = shot["render"]
    engine = set_engine(scene, arg("--engine", rs["engine"]), rs.get("eevee_samples", 16),
                        rs.get("cycles_samples", 16), arg("--cycles-device", "GPU"))
    pct = int(arg("--percent", 100))
    scene.render.resolution_percentage = pct
    if arg("--frames"):
        a, b = (int(x) for x in arg("--frames").split(":"))
        scene.frame_start, scene.frame_end = a, b
    s = scene.render.image_settings
    if hasattr(s, "media_type"):
        s.media_type = "IMAGE"
    s.file_format = "PNG"
    s.color_mode = "RGB"
    s.color_depth = "8"
    scene.render.filepath = os.path.join(frames_dir, "")
    scene.render.use_file_extension = True

    times, starts, rss = {}, {}, []

    def pre(sc, *_):
        starts[sc.frame_current] = time.perf_counter()

    def post(sc, *_):
        f = sc.frame_current
        times[f] = time.perf_counter() - starts.get(f, time.perf_counter())
        rss.append(peak_rss_mb())
    bpy.app.handlers.render_pre.append(pre)
    bpy.app.handlers.render_post.append(post)

    gpu_mon = GpuMemoryMonitor()
    gpu_mon.start()
    rss_before = peak_rss_mb()
    t0 = time.perf_counter()
    bpy.ops.render.render(animation=True)
    wall = time.perf_counter() - t0
    gpu_mon.stop()
    renderer = gpu_renderer() if "EEVEE" in engine else engine

    ft = [times[f] for f in sorted(times)]
    steady = ft[1:] if len(ft) > 1 else ft
    res = [int(shot["resolution"][0] * pct / 100), int(shot["resolution"][1] * pct / 100)]
    video = None
    mode = arg("--video", "ffmpeg" if shutil.which("ffmpeg") else "blender")
    audio = plan.get("speech", {}).get("audio")
    t_mux = time.perf_counter()
    if mode != "none":
        video = os.path.join(out_dir, f"{shot['name']}_{res[1]}p.mp4")
        if mode == "ffmpeg":
            mux_ffmpeg(frames_dir, scene.frame_start, shot["fps"], audio, shot["line"]["start_frame"], video)
        else:
            mux_blender(frames_dir, scene.frame_start, scene.frame_end, shot["fps"], res, audio,
                        shot["line"]["start_frame"], video)
    build = json.loads(scene.get("pilot_build_report", "{}"))
    warnings = []
    vram = gpu_mon.report()
    if vram.get("available") and (vram.get("baseline_used_mb") or 0) > 2048:
        warnings.append(f"{vram['baseline_used_mb']:.0f} MB VRAM was already in use before the render "
                        "(ComfyUI with models loaded?). Timing may be affected; close other GPU jobs and re-run.")
    if busy:
        warnings.append(f"other GPU processes at start: {busy}")
    report = {
        "measured_on": {"os": platform.platform(), "cpu_count": os.cpu_count(), "blender": bpy.app.version_string,
                        "gpu_renderer": renderer},
        "placeholder": plan["placeholder"],
        "engine": engine, "samples": scene.eevee.taa_render_samples if "EEVEE" in engine else scene.cycles.samples,
        "resolution": res, "fps": shot["fps"], "frames": [scene.frame_start, scene.frame_end], "frame_count": len(ft),
        "time_s": {"total_wall": round(wall, 2), "first_frame": round(ft[0], 2) if ft else None,
                   "mean_after_first": round(statistics.mean(steady), 3) if steady else None,
                   "median_after_first": round(statistics.median(steady), 3) if steady else None,
                   "p95_after_first": round(sorted(steady)[int(0.95 * (len(steady) - 1))], 3) if steady else None,
                   "max": round(max(ft), 3) if ft else None,
                   "realtime_factor": round(wall / (len(ft) / shot["fps"]), 2) if ft else None,
                   "video_mux": round(time.perf_counter() - t_mux, 2)},
        "memory": {"blender_peak_rss_mb": round(max([r for r in rss + [rss_before] if r] or [0]), 1),
                   "rss_before_render_mb": round(rss_before or 0, 1),
                   "texture_mb_estimate": build.get("texture_mb"), "vram": vram,
                   "other_gpu_processes_at_start": busy},
        "warnings": warnings,
        "per_frame_s": {str(f): round(times[f], 3) for f in sorted(times)},
        "outputs": {"frames": frames_dir, "video": video},
    }
    save_json(os.path.join(out_dir, "render_report.json"), report)
    print("RENDER_REPORT " + json.dumps({k: report[k] for k in ("engine", "resolution", "time_s", "memory")}))


if __name__ == "__main__":
    main()
