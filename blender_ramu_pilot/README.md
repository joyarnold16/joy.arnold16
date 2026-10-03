# Ramu 2.5D cutout pilot (Blender 5.2)

**Status:** the pipeline is built and tested end to end in Blender 5.2.2 LTS,
but only with placeholder parts (a grey mannequin). Ramu's approved art has
not been cut into layers yet, so **there is no Ramu appearance preview yet**,
and nothing here should be presented as Ramu. See [ART_PREP.md](ART_PREP.md)
for the art that is needed.

## Why a 2.5D cutout rig

The episodes so far are built from approved 2D art. A cutout rig moves that
same art: every body part is a flat layer on a bone. So the approved face and
costume stay exactly as approved, which a 3D rebuild can't promise without
manual sculpting.

What this approach costs:

- **Fixed views.** The character exists only in the views that are drawn:
  side, 3/4 and front. Turning between them swaps drawings; it isn't a smooth
  rotation.
- **Art prep.** Someone has to cut each view into parts, with the hidden areas
  under each joint painted in.

## Files

| Path | What it does |
|---|---|
| `scripts/build_rig.py` | Manifest + PNG layers → `.blend` rig: one armature per view on a shared `ramu_master` empty, parts bone-parented, sprite groups for eyes/brows/mouth/hands. Validates the manifest and lists every missing field or file. |
| `scripts/preview_appearance.py` | The sign-off sheet: every view at rest, every mouth/eye/brow variant, and joint stress poses. |
| `scripts/animate_test.py` | `shots/test_10s.json` → the 10 s test (walk, stop, turn, line, bowl lift), baked as FK keys using exact planar IK. Refuses to animate an unapproved real rig. |
| `scripts/lipsync.py` | Reads Rhubarb mouth cues (phonetic mode, needed for Hindi). Without cues it falls back to a loudness envelope. Reads PCM and float WAV. |
| `scripts/qa_checks.py` | Measures the result from Blender's evaluated scene: foot sliding, ground penetration, hand/bowl contact, bowl tilt, one-visible-variant-per-group, blinks, mouth timing against audio, pops, turn alignment. |
| `scripts/render_bench.py` | Renders frames + MP4 (audio placed at the line's frame); records per-frame time, peak RAM and VRAM; warns if another GPU job is holding VRAM. |
| `tools/make_placeholder_parts.py` | Generates the labelled placeholder mannequin used to test the chain. |
| `rig/ramu_rig.template.json` | The real manifest to fill in once the art is cut (bones, pivots, 91 layers in 3 views). |
| `shots/test_10s.json` | The shot as data: timings, step length, turn frames, line, bowl positions, camera. |
| `run_pilot.ps1` / `run_pilot.sh` | Stages: `selftest`, `preview`, `test`. |
| `AGENTS.md` (+ `CLAUDE.md`) | Briefing for any agent working here: rules, commands, checks, design decisions, open items. Codex reads it automatically. |

## Running it (Windows)

From this folder, in PowerShell:

```powershell
.\run_pilot.ps1 selftest                         # placeholder through the whole chain; real GPU numbers
.\run_pilot.ps1 preview -Manifest rig\ramu_rig.json   # real Ramu → out\ramu\appearance_preview.png, then STOP
# after sign-off: set "appearance_approved": {"by": "...", "date": "..."} in rig\ramu_rig.json
.\run_pilot.ps1 preview -Manifest rig\ramu_rig.json   # rebuild with the approval recorded
.\run_pilot.ps1 test -RequireIdleGpu             # 10 s test → QA → render (one GPU job)
```

The default Blender path is
`C:\Program Files\Blender Foundation\Blender 5.2\blender.exe`; use `-Blender`
to change it. Each step runs Blender with `--python-exit-code 1`, so any
failure stops the run with a non-zero exit code.

## The appearance gate

`animate_test.py` stops unless the manifest records
`"appearance_approved": {"by": ..., "date": ...}`. The placeholder manifest is
exempt because it is marked `"placeholder": true`, and its frames are stamped
*PLACEHOLDER PARTS - PIPELINE TEST - NOT RAMU*.

## What QA can and can't tell you

- **Measured from the evaluated scene, not from intent:**
  - feet: sliding, ground penetration, leg-reach clamps
  - bowl: hand–bowl gap, bowl movement before the grip, bowl tilt while held
  - sprites: exactly one variant visible per group on every frame, no
    other-view parts visible
  - face: blink lengths
  - mouth: timing against the line's loudness
  - motion: pops (velocity kinks or jumps), feet and head shift across each
    view swap
- **Mouth timing is checked, mouth correctness is not.** The loudness
  correlation catches a mouth that is late, early or flapping in silence. It
  can't tell whether the shapes fit the Hindi phonemes; only watching it can.
- **Appeal and acting are never measured.** QA catches mechanical faults; a
  person still has to watch the render.

## Cloud test results (placeholder parts; not Ramu)

Run on Blender 5.2.2 LTS (Linux, 4 vCPU, no GPU). The line was a robotic
espeak-ng Hindi recording of the proposed sentence, *not Ramu's voice*. Mouth
cues came from Rhubarb 1.14 in phonetic mode.

| Check (measured from Blender's evaluated scene) | Result |
|---|---|
| Foot sliding while planted (810 contact frame-pairs) | 0.00 px max |
| Feet below the ground line | 0.00 px |
| Leg/arm reach clamps | none |
| Hand-to-bowl gap at grip / max after grip (left hand on rim) | 0.00 px / 0.001 px |
| Support hand under the base (right hand) | 0.001 px max gap |
| Hand below the tabletop while at the bowl | 0 px (the old palm-under grip: 48.8 px, FAIL) |
| Bowl movement before grip; bowl tilt while carried | 0.00 px; 0.00° |
| Frames with ≠1 mouth/eye/brow/hand variant visible | 0 of 240 |
| Blinks | 4, each 4 frames (half–closed–closed–half) |
| Mouth openness vs. audio loudness | r = 0.48 at lag 0, 0.62 with the mouth 1 frame early; 1 open-mouth frame in silence |
| Pops (velocity kinks or jumps), outside the 2 swap frames | 0 |
| Turn swaps (side→3/4 @96, 3/4→front @100): feet / head-top shift | 0.0 / 1.4 px and 0.0 / 0.3 px |
| MP4 | 1920×1080, 240 frames, 10.000 s, line audio starting at 4.667 s (frame 112) |

The 0.00 px results are real, not a broken measurement. The IK is exact, and
the evaluated toe was checked by hand: it stays at x = 0.520 for 12 frames
while the hips travel 0.36 units over it. Along the way the checks caught two
real bugs, both fixed:

- the bowl followed the hand before the grip
- a velocity kink in the elbow on every arm swing

**Render time and memory: cloud CPU only, so this says nothing about the PC.**

| Engine | Time | Peak RAM |
|---|---|---|
| Cycles, CPU, 1080p, 16 samples | 3.45 s/frame (p95 3.64 s); 13.8 min for 240 frames | 522 MB |
| EEVEE through software OpenGL (llvmpipe), single frame | 15.8 s | 1.9 GB |

- Texture memory is 13 MB, but that is for placeholder parts; real art at
  ~2k per view will be larger.
- The two engines' frames differ by a mean of 0.2/255, with differences only
  on anti-aliased edges, so the CPU render shows what EEVEE will draw.
- VRAM wasn't measured (no GPU here).
- Real numbers come from `.\run_pilot.ps1 selftest` on the PC.

**Bowl pickup, revised after review.** The first version gripped the bowl
palm-up from underneath while it sat on the table. That is physically
impossible, and the old QA passed it because it only checked that the hand
reached its target point. Now:

- **Contact points.** The bowl has named contacts (`rim_left`, `rim_right`,
  `base`).
- **Grip.** The left hand comes down onto the near rim from above, with
  fingers hooked over it, and lifts.
- **Support.** The right hand then comes up under the base, so he holds it
  with both hands.
- **Staging.** The table is a realistic 0.78 units high, at near-full arm's
  reach, so the arm comes down almost straight instead of folding into a
  "chicken wing".
- **New check, `hand_vs_table`.** It finds the lowest opaque pixel of the
  visible hand drawings while the hand is at the bowl, and fails if it is
  below the tabletop. On the old grip it measures 48.8 px through the table
  (FAIL); on the new one, 0 px.
- **Support hand contact** is checked too: 0.001 px gap.

The checks run in a flat picture, so a hand hanging *beside* the table can't
be told apart from one *behind* it. That is why the table check only covers
the frames when the hand is at the bowl.

## Smoothness: before / after

The same shot, generated twice from the same code: `--no-polish` with rigid
pieces, against the defaults with the soft rig.

| | Before | After |
|---|---|---|
| Limbs and waist | rigid pieces hinge at joints | one drawing per limb, bends smoothly (soft rig) |
| Head, arms, hands | move exactly in step with the body | lag, then settle (springs driven by real body acceleration) |
| Starting off and reaching | everything starts together | lean back before stepping; body leans and head looks before the arm reaches; weight shifts first |
| Speed curves | one symmetric ease everywhere | reaches start quickly and land gently; lifts start slowly |
| Turn | hard swap between drawings | 2-frame cross-dissolve, plus a blink |
| Fast moves | sharp | motion blur (needs about 32 Cycles samples) |

Both versions pass all 10 QA checks. Feet slide 0 px, the hand–bowl gap is
≤0.001 px, and there are 0 pops.

The first "after" version started reaches 2–3× more abruptly than the
baseline, measured from hand acceleration. That was tuned down before
rendering. One flaw is still there: during the 2 cross-dissolve frames, each
part fades on its own, so the incoming figure shows its body through its arms.
Fixing it means compositing each view as a whole before fading.

## Not done / needs a person

1. **Cut Ramu's art into layers** for side, front and (recommended) 3/4
   views, and fill in the manifest pivots. This is the largest piece of work;
   see ART_PREP.md.
2. **Generate the line**, अरे वाह, आज तो खीर बनी है!, in Ramu's approved voice
   using the existing ComfyUI audio workflow. Then run Rhubarb
   (`audio/README.md`). Installing Rhubarb on the PC is a new free tool and
   needs your OK.
3. **Run `selftest` on the PC** to get the real EEVEE/GPU render time and
   memory. The cloud numbers above are from software OpenGL / CPU and say
   nothing about the PC.
4. **Watch every render.** The walk, turn and bowl lift are procedural and
   mechanically clean. Timing and acting will still want an animator's pass
   in the Graph Editor once the real art is in.

## Known limitations

- **Turns** between drawn views pop slightly by nature. The test hides it with
  a dip and a blink on the swap. The 3/4 view is what makes it acceptable.
- **Walking left** would need either mirrored art (asymmetric costume details
  jump sides) or a `side_L` view set. The test only walks right.
- **Long garments** that cross the hip or knee may need extra flap layers,
  depending on Ramu's costume.
- **Elbow direction.** In a flat picture each arm bends to one fixed side per
  shot. If a pose wants the elbow on the other side, change the staging
  (where the prop is), because flipping the bend mid-shot pops.
- **Animator controls.** The bones are FK only; the IK is solved in Python
  and baked. Hand-animating later in Blender would benefit from IK controls,
  which haven't been added.
- **Windows.** Every script ran on Linux with Blender 5.2.2. `run_pilot.ps1`
  and the Windows memory probe have not run on Windows yet.
