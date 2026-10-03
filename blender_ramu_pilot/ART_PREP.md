# Cutting Ramu into rig layers

The rig can only look like Ramu if the layers are cut from approved Ramu art.
This is the largest piece of manual work in the pilot, and it has not been
done yet. The placeholder mannequin in `tools/` follows exactly this layout;
use it as a reference for what a finished set looks like.

## 1. Model sheet first (one drawing per view)

Make one clean, full-body drawing per view. Use the same scale and the same
ground line in every view, and follow the approved design in
`02_CHARACTERS_V2\RAMU` and `01_BIBLES\characters\Ramu_Character_Bible.md`:
face, clothing, proportions and accessories.

| View | Needed for | Status if missing |
|---|---|---|
| `side_R`: profile facing screen-right | the walk | required |
| `front` | the line and the bowl lift | required |
| `three_quarter` | the in-between drawing of the turn | optional; without it the turn swaps two drawings and pops harder |

- **Where the drawings can come from.** First use any existing approved
  references that already show these views. FLUX.2 Klein can draw missing
  views from the approved references, but check every AI-drawn view against
  the bible. AI turnarounds often drift on accessories, clothing patterns,
  hairline, and finger count.
- **Pose to cut from.** Neutral standing pose. Keep the arms about 10° away
  from the body so they don't overlap the torso, with hands open and relaxed.
  In the side view, give the elbow and knee a very slight bend. That tells the
  IK which way each joint bends.
- **Rendering style.** Flat or cel-shaded colour cuts cleanly. Baked
  painterly lighting turns with the limbs (a forearm shadow stays put when the
  arm lifts). Small angles get away with it; big ones don't.

## 2. Layers per view

Each layer is a PNG named exactly as below, in `rig/parts/<view>/`.

| Layer | Bone | Notes |
|---|---|---|
| `torso` | torso | include the neck; must be complete under the arms |
| `pelvis` | hips | hips/waist; must be complete under the thigh tops |
| `head` | head | head without eyes/brows/mouth (those are their own layers); hair, ears and nose can stay here |
| `upper_arm.L/R`, `forearm.L/R` | same name | round cap past each joint |
| `hand_open.L/R`, `hand_grip.L/R`, `hand_support.L/R` | hand.L/R | grip = fingers hooked over a rim; support = flat palm under a base |
| `thigh.L/R`, `shin.L/R`, `foot.L/R` | same name | |
| `eyes_open`, `eyes_half`, `eyes_closed` | head | both eyes on one layer, aligned on the head |
| `brows_neutral`, `brows_raised` | head | |
| `mouth_A` … `mouth_H`, `mouth_X`, `mouth_smile` | head | full set for `front` and `three_quarter`; `side_R` needs only `X` and `smile` |
| accessories (`acc_<name>`) | the bone they ride on | e.g. cap → head, bag strap → torso |

L/R are Ramu's own left and right. In `side_R` (facing right) his right side
is nearest the camera, so draw the R limbs over the body and the L limbs
behind it. The `z` values in the manifest set this draw order.

**Mouth shapes** follow Rhubarb Lip Sync's set:

| Shape | Mouth |
|---|---|
| A | closed, lips pressed (M, B, P) |
| B | slightly open, teeth (most consonants, "ee") |
| C | open ("e", "a" in *bat*) |
| D | wide open ("aa" as in *aaj*) |
| E | slightly rounded ("o") |
| F | puckered ("oo", "w") |
| G | upper teeth on lower lip (F, V) |
| H | tongue up (L) |
| X | relaxed rest |

If G or H are missing, the pipeline uses B or C in their place.

**Hands.** Each hand needs three drawings: open, grip and support. The grip
drawing has the fingers hooked over the bowl's rim. The support drawing is a
flat palm, drawn pointing the same way as the open hand. The rig turns it
level when it goes under the bowl.

**Props.** The bowl is its own PNG in `rig/parts/props/`.

- `pivot`: the centre of the bowl's underside, where it rests on the table.
- `contacts`: where each hand's grip point goes, in bowl-canvas pixels:
  - `rim_left` and `rim_right`: just outside each rim, slightly below the lip
  - `base`: just under the bottom

The shot picks the contacts. In the test, the left hand grips `rim_left` and
the right hand supports at `base`. The table is optional set dressing with
its `pivot` at the centre of the tabletop; it can be replaced by background
art at the same height.

## 3. Joint rules (these make or break a cutout rig)

1. **Overlap at every joint.** Each child part extends past its joint with a
   round end centred on the pivot, so rotating it never opens a gap.
2. **Fill what the overlap hides.** Paint the parent part complete underneath:
   torso behind the arm, pelvis behind the thigh tops, upper arm under the
   forearm's cap. ComfyUI with FLUX fill can do this inpainting, but check it
   at every joint.
3. **Clothing that crosses a joint** (kurta hem, dhoti, shawl ends) is the
   hardest case. Either split the hem into front and back flaps on the
   thighs, or keep it on the pelvis and accept less visible leg swing. Decide
   per garment once the art exists.
4. **Prove it with the preview.** The "joint stress" tiles in
   `preview_appearance.py` bend elbows to 60–70° and knees to 30–50°. Any gap
   or hard edge there means that part needs more overlap.

## 4. Export

- Export PNG, RGBA, straight alpha, sRGB. Make every layer full canvas size.
  Krita has *Export Layers*; Photoshop has *Export Layers to Files*. The
  builder crops each layer to its visible pixels, so no `offset` is needed.
- **Canvas size.** About 2000–2400 px tall per view, same size for every
  view. Set `px_per_unit` so the character comes out about 1.8 units tall:
  1000 px per unit for a character drawn 1800 px tall.

## 5. Pivots (fill in `rig/ramu_rig.template.json`)

Coordinates are canvas pixels, x to the right, y downward. A layer of small
dots in the art file, read off with the cursor, is the quickest way to get
them.

| Bone | head → tail |
|---|---|
| hips | pelvis centre → a point straight above it |
| torso | base of spine → base of neck |
| head | neck → top of head |
| upper_arm | shoulder → elbow |
| forearm | elbow → wrist |
| hand | wrist → palm centre (the grip point) |
| thigh | hip joint → knee |
| shin | knee → ankle |
| foot | ankle → ball of the toe, plus a `heel` point |

`ground_origin` is the point midway between the feet, on the sole line.

After filling these in, `build_rig.py` lists anything still missing. The
preview then shows whether each pivot is right: a wrong pivot makes a limb
swing around the wrong point in the stress tiles.

## 6. Effort (rough)

Each view takes a 2D artist about half a day to a day to cut, fill the hidden
areas and draw the face sets. That makes 2–3 days for three views. ComfyUI
(SAM to segment, FLUX fill for hidden areas) can speed up the cutting, but
someone still has to clean up every joint by hand.
