# Paste into `00_ADMIN\AI_SYNC_STATUS.md`

This was written from a Claude Code **cloud** session, which can't reach the
NanuYT folder on the PC. Paste the block below into the shared status file.
Also add this line to `09_WORKFLOWS\agents\README.md`:

> Blender character pilot: see `<pilot folder>\AGENTS.md`. Agent rules, run
> commands, checks and design decisions for the Ramu 2.5D rig. Codex reads it
> automatically; Claude via CLAUDE.md.

---

## Blender pilot: Ramu 2.5D cutout rig (Claude), 2026-10-03

**Read `AGENTS.md` in the pilot folder first.** It holds the full briefing:
rules, commands, checks, design decisions and open items.

**Decision (user):** a 2.5D cutout rig built from the approved Ramu art.
**Blender does body motion only.** ComfyUI does the voice, audio and
supporting work. Renders are silent; lip-sync is either ComfyUI on the
rendered video (untested on the 2D face; test early) or Blender's drawn mouth
shapes from a WAV. The user is
also weighing 3D (ChatGPT suggested it for smoother motion); the open item is
a one-day 3D likeness test (see AGENTS.md §6).

**Where the work is:** GitHub `joyarnold16/joy.arnold16`, branch
`claude/blender-ramu-pilot-02ff4e`, folder `blender_ramu_pilot/`. Copy it
into an isolated pilot folder in NanuYT. Nothing in NanuYT was touched: no
ComfyUI workflows, models, voices, production settings, EP07 or Shorts.

**Done (Blender 5.2.2, placeholder mannequin only, "NOT RAMU"):**
- **Pipeline:**
  - rig builder, rigid and soft (bending) limbs
  - appearance sign-off sheet
  - 10 s / 24 fps test from a shot spec: walk → stop → turn → Hindi line →
    bowl pickup
  - 10 automated QA checks
  - render benchmark (time, RAM, VRAM)
  - PowerShell runner
- **Bowl pickup revised:**
  - the hand comes down onto the near rim from above and lifts
  - the other hand then supports the base, so he holds it with two hands
  - a new `hand_vs_table` check: the old palm-under grip went 48.8 px
    through the tabletop (FAIL); the new one is 0 px
- **Smoothness work.** A before/after video was made:
  - soft rig (limbs bend instead of hinging)
  - springs for overlap and follow-through
  - anticipation and the body leading the reach
  - weight shift, quick-start/slow-land reaches
  - 2-frame cross-dissolve on turns, motion blur

  All 10 checks PASS on both versions. Feet slide 0 px and the hand–bowl gap
  is ≤0.001 px.
- **Render:** cloud CPU only (Cycles 1080p ~3.5 s/frame, 522 MB RAM); not
  representative of the PC GPU.

**Not done / blocked:**
1. **Ramu's art isn't cut into layers yet**, so there is no Ramu appearance
   preview. See `ART_PREP.md` and `rig/ramu_rig.template.json` (side + front
   required, 3/4 recommended; rigid or soft limbs).
2. **Audio and lip-sync are ComfyUI's job** (user decision). One open
   question: does ComfyUI video lip-sync work on the flat 2D face? Test it on
   one speaking shot.
3. **No PC GPU numbers yet:** run `.\run_pilot.ps1 selftest` with ComfyUI
   idle.
4. **Known flaw:** the turn's cross-dissolve shows an "x-ray" look for 2
   frames (parts fade one by one). The fix is to composite each view whole.

**For Codex (Studio integration and job runner). Wrap the scripts; don't
rebuild them:**
- **Jobs:** one Blender command per step:
  `blender -b --factory-startup --python-exit-code 1 [blend] -P <script> -- <args>`.
  Non-zero exit = failed.
- **Outputs:**
  - `*.plan.json`
  - `qa.json`, with a `summary` of PASS/WARN/FAIL per check
  - `render_report.json`
  - the MP4
- **Approval gate:** the Studio must let the user sign off
  `appearance_preview.png` and write `"appearance_approved"` into the
  manifest; then rebuild.
- **GPU queue:** preview and render are heavy GPU jobs; build, animate and QA
  are CPU and take seconds.
