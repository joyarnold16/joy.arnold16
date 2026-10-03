# AGENTS.md: Ramu Blender pilot

These are the instructions for any AI agent working in this folder: Codex
(ChatGPT) or Claude. Codex reads this file automatically; `CLAUDE.md` imports
it for Claude. It is self-contained; read it fully before acting. Then log
what you did in `00_ADMIN\AI_SYNC_STATUS.md`.

## 1. What this is and where it stands

- **Goal:** animate the approved 2D character Ramu in Blender for the next
  NanuYT episode. ComfyUI stays responsible for supporting images and audio.
- **Decision (user):** a **2.5D cutout rig**. Ramu's approved drawings are
  cut into layers and moved by bones, so he stays exactly on-model.
- **Status:** the full pipeline is built and tested in Blender 5.2.2 LTS,
  but **only with a grey placeholder mannequin**, stamped
  "PLACEHOLDER … NOT RAMU". The real Ramu art has **not** been cut into
  layers, so no Ramu preview exists yet.
- **Division of labour:**
  - **Claude:** character, rig, animation, QA.
  - **Codex:** Studio integration and the job runner (wrap these scripts;
    don't rewrite them).

## 2. Rules (do not break)

1. Never present placeholder output as Ramu.
2. **Appearance gate:** do not animate a real (non-placeholder) rig until
   the user has approved `appearance_preview.png` and the manifest records
   `"appearance_approved": {"by": ..., "date": ...}`. The script enforces
   this (exit 1); do not bypass it with `--allow-unapproved` unless the user
   says so.
3. **One heavy GPU job at a time.** Rendering (`preview_appearance.py`,
   `render_bench.py`) is heavy. Building, animating and QA are CPU-only and
   take seconds. Use `--require-idle-gpu` in automated runs.
4. **Don't touch:**
   - ComfyUI workflows, models or voices, or production settings
   - EP07 and its Shorts, which are uploaded Private and must not be
     changed or reuploaded
5. Ask the user before using paid tools or doing large downloads. Rhubarb
   (free, ~90 MB) and ComfyUI 3D models (several GB) need their OK.
6. Don't build the whole cast or start another episode.

## 3. How to run

PowerShell, from this folder. Blender is at
`C:\Program Files\Blender Foundation\Blender 5.2\blender.exe`.

```powershell
.\run_pilot.ps1 selftest                              # placeholder, whole chain: proves the PC setup, gives real GPU numbers
.\run_pilot.ps1 preview -Manifest rig\ramu_rig.json   # real Ramu → out\ramu\appearance_preview.png, then STOP for sign-off
.\run_pilot.ps1 test -RequireIdleGpu                  # after approval: 10 s test → QA → render
```

Each step is `blender -b --factory-startup --python-exit-code 1 [file.blend] -P <script> -- <args>`.
A non-zero exit means the step failed. The reason is printed: a list of
manifest problems, unapproved appearance, or GPU busy.

| Script | In → out |
|---|---|
| `tools/make_placeholder_parts.py` | → placeholder layers plus `rig_manifest.json` (rigid) and `rig_manifest_soft.json` (soft) |
| `scripts/build_rig.py --manifest M --out rig.blend` | manifest + PNG layers → rig. Lists every missing field or file |
| `scripts/preview_appearance.py --out sheet.png` | rig → sign-off sheet (views, face sets, joint stress poses) |
| `scripts/animate_test.py --shot shots/test_10s.json --out test.blend [--audio W --cues C] [--no-polish]` | rig → animated shot + `test.plan.json` |
| `scripts/qa_checks.py --out qa.json` | animated shot → `qa.json` + `qa.md`; `summary` gives PASS/WARN/FAIL per check |
| `scripts/render_bench.py --out-dir D [--engine eevee\|cycles] [--percent N] [--require-idle-gpu]` | frames + MP4 + `render_report.json` (time, RAM, VRAM) |

Inputs:
- the line audio, generated in ComfyUI in Ramu's voice
- Rhubarb cues: `rhubarb -r phonetic -f json --extendedShapes GHX`. Use
  phonetic mode for Hindi; the default mode is English-only.

## 4. How to check your work

- **Automated checks:** every QA check must be PASS before you show a
  render. They cover:
  - foot sliding and ground penetration
  - hand-to-bowl contact, the support hand, and bowl tilt
  - `hand_vs_table`: no hand through the tabletop while it's at the bowl
  - sprite groups: exactly one mouth/eye/brow/hand drawing visible
  - blinks
  - mouth timing against the audio loudness
  - pops: isolated velocity kinks or jumps
  - turn alignment
- **Then look at frames.** QA does not judge appeal, acting or whether a
  pose is physically believable. The first bowl grip passed every check and
  was still impossible: the hand was under a bowl that was sitting on the
  table. That is why `hand_vs_table` exists.
- **Report real numbers.** Render time and memory only mean something on the
  PC's GPU. Cloud or CPU numbers must be labelled as such.

## 5. Design decisions (and why)

- **Puppet structure.** One armature per drawn view (`side_R`,
  `three_quarter`, `front`) on a shared `ramu_master`. Turns swap views.
- **Exact planar IK, baked as FK keys.** Planted feet and hands on props
  don't slide (measured 0 px). It's solved in Python, not with Blender IK
  constraints.
- **Soft rig (optional per part).** A part with `"bones": [a, b]` is a
  subdivided mesh skinned to two bones with a smooth blend zone, so limbs and
  waist bend like a rubber hose instead of hinging. The art for these is an
  unbroken limb drawing. `"bone": x` keeps a rigid piece.
- **Polish (shot spec `polish`, switched off with `--no-polish`):**
  - Springs on the head, arms and hands give overlap and follow-through,
    pushed by the real body acceleration. Arms in an IK hold are excluded,
    so contacts stay exact.
  - Anticipation before the first step.
  - The body leans and the head looks before the arm reaches.
  - Weight shifts toward the bowl, with the hips lowered just enough to keep
    the feet planted.
  - Reaches start quickly and land gently; lifts start slowly. Quick starts
    were tuned down after QA showed hand accelerations 2–3× the baseline.
  - A 2-frame cross-dissolve on each view swap.
  - Motion blur, which needs about 32 Cycles samples to look clean.
- **Bowl pickup.**
  - The bowl has named contact points (`rim_left`, `rim_right`, `base`).
  - The grip hand comes down onto the near rim from above (`approach`),
    then lifts.
  - The other hand comes up under the base, so he holds it with two hands.
  - The table is 0.78 units high, at near-full arm's reach. Closer, the
    elbow flares to shoulder height ("chicken wing").
- **Elbow direction.** In 2D each arm bends to one fixed side per shot.
  Flipping it mid-shot pops, so fix awkward elbows by moving the prop, not
  the bend.
- **Colour.** Colour management is set to Standard, so the art's colours
  come out exactly as drawn.

## 6. Known limitations / open items

- **Cross-dissolve "x-ray".** Each part fades separately, so for 2 frames
  the incoming figure shows its body through its arms. The fix is to
  composite each view as a whole (separate view layers) before fading.
- **Turns are drawing swaps.** Smooth rotation and new camera angles need 3D.
- **Animator controls.** IK is baked, with no animator IK controls yet.
  Hand polish happens in the Graph Editor.
- **Windows.** `run_pilot.ps1` and the Windows memory probe have not run on
  Windows yet.
- **3D question (open).** ChatGPT suggested 3D for smoother motion.
  - The assessment: 3D wins on turns, camera angles and in-depth hand/object
    work (cooking, carts). It costs a 3D Ramu that matches the approved art:
    model, rig and a talking face.
  - ComfyUI image-to-3D (Hunyuan3D/TRELLIS) is free to run but gives a
    static mesh: no skeleton, a fused face, poor deformation.
  - Agreed next step: a one-day **likeness test**. Generate a 3D Ramu, judge
    likeness from front, side and 3/4, and only then rig. That needs the
    user's OK for the download and a licence check for commercial YouTube
    use.
  - Most of this pipeline carries over to 3D (shot spec, QA, lip-sync,
    render benchmark).

## 7. Tools

- **Blender** is the tool. It is fully scriptable headless, so agents can
  run and verify everything.
- **Avoid GUI-only 2D tools** (Moho, Cartoon Animator, Spine, Live2D). They
  are hard or impossible for agents to drive.
- **Extra CLI tools:** Rhubarb (lip-sync) and ffmpeg (video; Blender's own
  encoder is the fallback).
