# Paste into `00_ADMIN\AI_SYNC_STATUS.md`

This was written from a Claude Code **cloud** session, which can't reach the
NanuYT folder on the PC. It hasn't been added to the shared file yet. Paste
the block below, or have a local session do it.

---

## Blender pilot: Ramu 2.5D cutout rig (Claude), 2026-10-03

**Decision (user):** a 2.5D cutout rig built from the approved Ramu art, not
a 3D model. ComfyUI stays responsible for the supporting images and audio.

**Where the work is:** GitHub `joyarnold16/joy.arnold16`, branch
`claude/blender-ramu-pilot-02ff4e`, folder `blender_ramu_pilot/`. Copy that
folder into an isolated pilot folder in NanuYT. Nothing in NanuYT was
touched: no ComfyUI workflows, models, voices, production settings, EP07 or
Shorts were changed.

**What exists (tested in Blender 5.2.2 LTS, Linux, placeholder parts only):**
- `scripts/build_rig.py`: manifest + PNG layers → rig `.blend`. One
  armature per view (`side_R`, `three_quarter`, `front`) on `ramu_master`.
  Sprite groups for eyes, brows, mouth and hands. Validates the manifest and
  lists everything still missing.
- `scripts/preview_appearance.py`: the sign-off sheet (views, face sets,
  joint stress poses).
- `scripts/animate_test.py`: the 10 s / 24 fps test from
  `shots/test_10s.json`: walk 6 steps → stop → turn (via 3/4) → Hindi line →
  lift bowl. Exact planar IK keeps feet and the bowl grip fixed. Refuses an
  unapproved real rig (exit 1).
- `scripts/qa_checks.py`: measured checks → `qa_report.json` / `.md`.
- `scripts/render_bench.py`: frames + MP4 + `render_report.json` (time, RAM,
  VRAM). `--require-idle-gpu` refuses to render while another job holds the
  GPU.
- `run_pilot.ps1 selftest|preview|test`: the stage runner. Not yet run on
  Windows.

**Placeholder test results (cloud CPU; not Ramu, and not PC numbers):**
- **QA, measured:**
  - feet: sliding 0.00 px, ground penetration 0.00 px
  - bowl: hand–bowl gap 0.00 px at the grip and after it, tilt 0.00°
  - sprites: 0 frames with a wrong mouth/eye/brow/hand variant
  - face: 4 blinks
  - mouth vs loudness: r = 0.62 with the mouth 1 frame early
  - motion: 0 pops; turn swaps shift the feet 0 px and the head ≤1.4 px
- **Render:** Cycles CPU 1080p 3.45 s/frame (13.8 min for 10 s), 522 MB peak
  RAM. EEVEE on software OpenGL 15.8 s/frame. Not representative of the PC
  GPU.
- **Issue spotted by eye:** the palm-under-bowl grip would pass through the
  tabletop. Pick a rim or two-hand grip when the real art is drawn.

**Not done / blocked:**
1. No Ramu art is cut yet, so there is no Ramu appearance preview. The
   checklist is `ART_PREP.md`, the manifest to fill in is
   `rig/ramu_rig.template.json`, and the art needs side + front views, with
   3/4 recommended. FLUX.2 Klein may draw missing views; every view needs
   checking against the bible before approval.
2. The Hindi line hasn't been generated in Ramu's voice. Proposed:
   अरे वाह, आज तो खीर बनी है! (`audio/README.md`). It needs the existing
   ComfyUI audio workflow, then Rhubarb Lip Sync (`-r phonetic`). Rhubarb is
   a free tool that isn't on the PC yet and needs the user's OK.
3. No real GPU render time or memory yet: run `.\run_pilot.ps1 selftest` on
   the PC (one GPU job; close or idle ComfyUI first).

**For Codex (Studio integration and job runner). Please don't rebuild these
scripts; wrap them:**
- **Jobs:** each stage is one Blender command:
  `blender -b --factory-startup --python-exit-code 1 [file.blend] -P <script> -- <args>`.
  Non-zero exit = failed step (manifest problems are printed as a list,
  unapproved rig, GPU busy).
- **Inputs:** `rig/ramu_rig.json` (manifest), `shots/*.json` (shot spec), line
  WAV + Rhubarb JSON.
- **Outputs:**
  - `*.plan.json`: what was animated
  - `qa_report.json`: a per-check verdict in `summary` (PASS/WARN/FAIL)
  - `render/render_report.json`
  - `render/*.mp4`
- **Approval gate:** the Studio needs a way to record
  `"appearance_approved": {"by","date"}` in the manifest after the user signs
  off on `appearance_preview.png`. Then rebuild the rig.
- **GPU queue:** treat `preview` (rendering) and `render_bench` as heavy GPU
  jobs; build, animate and QA are CPU-only and take seconds.

**Next (Claude):** once the cut art and the line exist, run `preview`, fix
pivots and overlaps until the sheet is approved, then run `test`, review the
QA, adjust the shot timing, and report the real render numbers here.
