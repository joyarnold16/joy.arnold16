"""Mouth cues for the cutout rig.

Preferred source: Rhubarb Lip Sync run in phonetic mode, which does not
depend on English word recognition and is the only Rhubarb mode that makes
sense for Hindi:

    rhubarb -r phonetic -f json --extendedShapes GHX -o line.rhubarb.json line.wav

Fallback (no Rhubarb installed): an amplitude envelope mapped to open/closed
shapes. That is lip-flap, not phoneme-accurate, and the QA report says so.

Standalone use (no Blender needed):
    python lipsync.py --wav line.wav --fps 24 --out line.amplitude.json
"""
import argparse
import math
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pilot_common import MOUTH_FALLBACK, load_json, save_json  # noqa: E402


def read_wav_mono(path):
    """Return (samples as list-like float array in -1..1, sample_rate).

    Handles PCM 8/16/24/32-bit and IEEE float 32/64, which the stdlib
    `wave` module does not (TTS tools often write float WAV).
    """
    import numpy as np
    with open(path, "rb") as f:
        data = f.read()
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError(f"{path}: not a RIFF/WAVE file")
    pos, fmt, frames = 12, None, None
    while pos + 8 <= len(data):
        cid, size = data[pos:pos + 4], struct.unpack("<I", data[pos + 4:pos + 8])[0]
        body = data[pos + 8:pos + 8 + size]
        if cid == b"fmt ":
            tag, ch, sr, _, _, bits = struct.unpack("<HHIIHH", body[:16])
            if tag == 0xFFFE and len(body) >= 26:  # WAVE_FORMAT_EXTENSIBLE
                tag = struct.unpack("<H", body[24:26])[0]
            fmt = (tag, ch, sr, bits)
        elif cid == b"data":
            frames = body
        pos += 8 + size + (size & 1)
    if fmt is None or frames is None:
        raise ValueError(f"{path}: missing fmt or data chunk")
    tag, ch, sr, bits = fmt
    if tag == 3:
        arr = np.frombuffer(frames, dtype="<f4" if bits == 32 else "<f8").astype(np.float64)
    elif tag == 1:
        if bits == 8:
            arr = (np.frombuffer(frames, np.uint8).astype(np.float64) - 128) / 128
        elif bits == 16:
            arr = np.frombuffer(frames, "<i2").astype(np.float64) / 32768
        elif bits == 24:
            b = np.frombuffer(frames, np.uint8)
            b = b[:len(b) // 3 * 3].reshape(-1, 3).astype(np.int32)
            v = b[:, 0] | (b[:, 1] << 8) | (b[:, 2] << 16)
            v = np.where(v & 0x800000, v - 0x1000000, v)
            arr = v.astype(np.float64) / 8388608
        elif bits == 32:
            arr = np.frombuffer(frames, "<i4").astype(np.float64) / 2147483648
        else:
            raise ValueError(f"{path}: unsupported PCM bit depth {bits}")
    else:
        raise ValueError(f"{path}: unsupported WAV format tag {tag}")
    arr = arr[:len(arr) // ch * ch].reshape(-1, ch).mean(axis=1)
    return arr, sr


def rms_envelope(samples, sr, fps):
    """RMS per animation frame, normalised so the loudest frame is 1."""
    import numpy as np
    hop = sr / fps
    n = int(math.ceil(len(samples) / hop))
    env = np.zeros(n)
    for i in range(n):
        seg = samples[int(i * hop):int((i + 1) * hop)]
        env[i] = math.sqrt(float((seg ** 2).mean())) if len(seg) else 0.0
    peak = env.max() if len(env) else 0
    return env / peak if peak > 0 else env


def amplitude_cues(samples, sr, fps):
    """Lip-flap cues in Rhubarb's JSON layout, from loudness alone."""
    env = rms_envelope(samples, sr, fps)
    cues = []
    for i, e in enumerate(env):
        if e < 0.06:
            shape = "X"
        elif e < 0.2:
            shape = "B"
        elif e < 0.45:
            shape = "C"
        else:
            shape = "D"
        # A short dip between two loud frames reads as a closure (m/b/p).
        if 0 < i < len(env) - 1 and e < 0.25 and env[i - 1] > 0.4 and env[i + 1] > 0.4:
            shape = "A"
        t0, t1 = i / fps, (i + 1) / fps
        if cues and cues[-1]["value"] == shape:
            cues[-1]["end"] = round(t1, 3)
        else:
            cues.append({"start": round(t0, 3), "end": round(t1, 3), "value": shape})
    return {"metadata": {"duration": round(len(samples) / sr, 3), "source": "amplitude-fallback"},
            "mouthCues": cues}


def load_cues(path):
    data = load_json(path)
    if "mouthCues" not in data:
        raise ValueError(f"{path}: expected Rhubarb JSON with a 'mouthCues' list")
    return data


def frame_shapes(cues, fps, start_frame, available, min_hold=2):
    """Map cue times to per-frame mouth variants.

    Returns {frame: shape}. Shapes missing from `available` fall back via
    MOUTH_FALLBACK. Runs shorter than `min_hold` frames are merged into the
    previous shape, since one-frame mouths flicker at 24 fps.
    """
    if not available:
        return {}

    def pick(shape):
        seen = set()
        while shape not in available and shape not in seen:
            seen.add(shape)
            shape = MOUTH_FALLBACK.get(shape, "X")
        return shape if shape in available else sorted(available)[0]

    runs = []
    for c in cues["mouthCues"]:
        f0 = start_frame + int(round(c["start"] * fps))
        f1 = start_frame + int(round(c["end"] * fps))
        if f1 <= f0:
            continue
        shape = pick(c["value"])
        if runs and runs[-1][2] == shape:
            runs[-1][1] = f1
        else:
            runs.append([f0, f1, shape])
    merged = []
    for r in runs:
        if merged and (r[1] - r[0] < min_hold):
            merged[-1][1] = r[1]
        elif merged and merged[-1][2] == r[2]:
            merged[-1][1] = r[1]
        else:
            merged.append(r)
    out = {}
    for f0, f1, shape in merged:
        for f in range(f0, f1):
            out[f] = shape
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--wav", required=True)
    ap.add_argument("--fps", type=float, default=24)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    samples, sr = read_wav_mono(a.wav)
    save_json(a.out, amplitude_cues(samples, sr, a.fps))
    print(f"wrote {a.out}")


if __name__ == "__main__":
    main()
