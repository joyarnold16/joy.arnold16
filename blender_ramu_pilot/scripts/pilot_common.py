"""Shared helpers for the Ramu 2.5D cutout pilot.

Everything here is plain Python (no bpy import at module level) so the
lip-sync and PNG helpers also run outside Blender.
"""
import json
import math
import os
import struct
import subprocess
import sys
import threading
import time
import zlib

PILOT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Bones the animation script relies on. A view may add extra bones
# (accessories, scarf ends), but these names must exist in every view.
REQUIRED_BONES = [
    "hips", "torso", "head",
    "upper_arm.L", "forearm.L", "hand.L",
    "upper_arm.R", "forearm.R", "hand.R",
    "thigh.L", "shin.L", "foot.L",
    "thigh.R", "shin.R", "foot.R",
]

# Rhubarb Lip Sync mouth shapes. X = idle/rest.
MOUTH_SHAPES = ["A", "B", "C", "D", "E", "F", "G", "H", "X"]
# Used when a view does not provide a shape (same substitutions Rhubarb
# applies when extended shapes are disabled).
MOUTH_FALLBACK = {"G": "B", "H": "C", "X": "A"}
# 0 = closed, 1 = widest. Used by the mouth-timing check.
MOUTH_OPENNESS = {"X": 0.0, "A": 0.0, "B": 0.3, "G": 0.3, "F": 0.3,
                  "E": 0.5, "C": 0.6, "H": 0.6, "D": 1.0, "smile": 0.1}


def script_args():
    """Arguments after `--` on the Blender command line."""
    argv = sys.argv
    return argv[argv.index("--") + 1:] if "--" in argv else argv[1:]


def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_json(path, data):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def resolve(base_file, rel):
    """Resolve `rel` against the directory holding `base_file`."""
    if rel is None or os.path.isabs(rel):
        return rel
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(base_file)), rel))


# ---------------------------------------------------------------- 2D maths

def angle_of(v):
    return math.atan2(v[1], v[0])


def wrap_angle(a):
    return (a + math.pi) % (2 * math.pi) - math.pi


def two_bone_ik(root, target, l1, l2, bend_sign, max_reach=None):
    """Planar two-bone IK.

    Returns (angle_1, angle_2, reached_point, clamped). Angles are absolute,
    measured CCW from +X in the picture plane. `bend_sign` picks which side
    the middle joint bends to (+1 = CCW from the root->target line).
    `max_reach` lets a rest pose drawn fully straight stay straight.
    """
    dx, dz = target[0] - root[0], target[1] - root[1]
    d = math.hypot(dx, dz)
    lo, hi = abs(l1 - l2) + 1e-6, max_reach or (l1 + l2) * 0.9995
    clamped = d > hi + 1e-9 or d < lo
    d_c = min(max(d, lo), hi)
    phi = math.atan2(dz, dx)
    cos_a = (l1 * l1 + d_c * d_c - l2 * l2) / (2 * l1 * d_c)
    alpha = math.acos(max(-1.0, min(1.0, cos_a)))
    a1 = phi + bend_sign * alpha
    mid = (root[0] + l1 * math.cos(a1), root[1] + l1 * math.sin(a1))
    end = (root[0] + d_c * math.cos(phi), root[1] + d_c * math.sin(phi))
    a2 = math.atan2(end[1] - mid[1], end[0] - mid[0])
    return a1, a2, end, clamped


def smoothstep(u):
    u = max(0.0, min(1.0, u))
    return u * u * (3 - 2 * u)


def ease_in_out(u):
    u = max(0.0, min(1.0, u))
    return 0.5 - 0.5 * math.cos(math.pi * u)


def hermite(p0, p1, m0, m1, u):
    u2, u3 = u * u, u * u * u
    return ((2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * m0
            + (-2 * u3 + 3 * u2) * p1 + (u3 - u2) * m1)


def lerp(a, b, u):
    return a + (b - a) * u


# ---------------------------------------------------------------- PNG I/O

def write_png(path, rgba):
    """Write an (h, w, 4) uint8 numpy array as PNG without Pillow."""
    import numpy as np
    h, w, _ = rgba.shape
    raw = np.concatenate([np.zeros((h, 1), np.uint8), rgba.reshape(h, w * 4)], axis=1).tobytes()

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 6)))
        f.write(chunk(b"IEND", b""))


# ---------------------------------------------------------------- memory

def peak_rss_mb():
    """Peak resident memory of this process in MB (Windows and Linux)."""
    if os.name == "nt":
        import ctypes
        from ctypes import wintypes

        class PMC(ctypes.Structure):
            _fields_ = [("cb", wintypes.DWORD), ("PageFaultCount", wintypes.DWORD),
                        ("PeakWorkingSetSize", ctypes.c_size_t), ("WorkingSetSize", ctypes.c_size_t),
                        ("QuotaPeakPagedPoolUsage", ctypes.c_size_t), ("QuotaPagedPoolUsage", ctypes.c_size_t),
                        ("QuotaPeakNonPagedPoolUsage", ctypes.c_size_t), ("QuotaNonPagedPoolUsage", ctypes.c_size_t),
                        ("PagefileUsage", ctypes.c_size_t), ("PeakPagefileUsage", ctypes.c_size_t)]
        # Explicit signatures: without them ctypes passes the process pseudo-handle
        # as a 32-bit int, which can fail silently on 64-bit Windows and read 0 MB.
        k32, psapi = ctypes.WinDLL("kernel32"), ctypes.WinDLL("psapi")
        k32.GetCurrentProcess.restype = wintypes.HANDLE
        psapi.GetProcessMemoryInfo.argtypes = [wintypes.HANDLE, ctypes.POINTER(PMC), wintypes.DWORD]
        psapi.GetProcessMemoryInfo.restype = wintypes.BOOL
        pmc = PMC()
        pmc.cb = ctypes.sizeof(PMC)
        if not psapi.GetProcessMemoryInfo(k32.GetCurrentProcess(), ctypes.byref(pmc), pmc.cb):
            return None
        return pmc.PeakWorkingSetSize / 2 ** 20
    import resource
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024.0


def nvidia_smi(query, kind="gpu"):
    """Run an nvidia-smi query; returns list of row lists or None."""
    flag = "--query-gpu" if kind == "gpu" else "--query-compute-apps"
    try:
        out = subprocess.run(["nvidia-smi", f"{flag}={query}", "--format=csv,noheader,nounits"],
                             capture_output=True, text=True, timeout=10)
    except (OSError, subprocess.TimeoutExpired):
        return None
    if out.returncode != 0:
        return None
    return [[c.strip() for c in line.split(",")] for line in out.stdout.strip().splitlines() if line.strip()]


class GpuMemoryMonitor:
    """Polls nvidia-smi for used VRAM. Values are system-wide, so the
    baseline taken before rendering is reported alongside the peak."""

    def __init__(self, interval=0.5):
        self.interval = interval
        self.available = nvidia_smi("name,memory.total,memory.used") is not None
        self.baseline_mb = None
        self.peak_mb = None
        self.gpu_name = None
        self._stop = threading.Event()
        self._thread = None

    def _used(self):
        rows = nvidia_smi("name,memory.total,memory.used")
        if not rows:
            return None
        self.gpu_name = rows[0][0]
        return float(rows[0][2])

    def start(self):
        if not self.available:
            return
        self.baseline_mb = self._used()
        self.peak_mb = self.baseline_mb

        def run():
            while not self._stop.wait(self.interval):
                used = self._used()
                if used is not None:
                    self.peak_mb = max(self.peak_mb or 0, used)
        self._thread = threading.Thread(target=run, daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=5)

    def report(self):
        if not self.available:
            return {"available": False, "note": "nvidia-smi not found; VRAM not measured"}
        return {"available": True, "gpu": self.gpu_name, "baseline_used_mb": self.baseline_mb,
                "peak_used_mb": self.peak_mb,
                "delta_mb": None if self.peak_mb is None else round(self.peak_mb - self.baseline_mb, 1),
                "note": "system-wide VRAM; delta is the increase during this render"}


def other_gpu_processes(min_mb=1024):
    """Compute processes other than this one holding at least `min_mb` VRAM.

    On Windows (WDDM drivers) nvidia-smi often reports per-process memory as
    "[N/A]"; those processes are listed with used_mb None rather than dropped,
    because ComfyUI is exactly the process this guard is meant to see.
    """
    rows = nvidia_smi("pid,process_name,used_memory", kind="apps")
    if not rows:
        return []
    me = os.getpid()
    busy = []
    for row in rows:
        if len(row) < 3:
            continue
        pid, name, used = row[0], row[1], row[2]
        try:
            if int(pid) == me:
                continue
        except ValueError:
            continue
        try:
            mb = float(used)
        except ValueError:
            busy.append({"pid": int(pid), "process": name, "used_mb": None})
            continue
        if mb >= min_mb:
            busy.append({"pid": int(pid), "process": name, "used_mb": mb})
    return busy


class Stopwatch:
    def __init__(self):
        self.t0 = time.perf_counter()

    def elapsed(self):
        return time.perf_counter() - self.t0
