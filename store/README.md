# Store assets

Everything here regenerates the Play listing's images and video from the
**real game**, by driving `app/src/main/assets/blastgrid.html` in a headless
browser. Nothing is mocked up, so the screenshots cannot drift from what the
app actually looks like — rerun these after any art change.

## Running them

Serve the game first; every script expects it on port 8731.

    cd app/src/main/assets && python3 -m http.server 8731

Then, from this directory:

    node capture.js phone      # raw gameplay frames -> raw/
    node make.js    phone      # captions them into store frames -> out/
    node capture.js tablet7    &&  node make.js tablet7
    node capture.js tablet10   &&  node make.js tablet10
    node record.js             # gameplay video -> vid/*.webm
    node feature.js            # 1024x500 feature graphic -> out-feature/
    node icons.js              # four icon concepts -> icons/
    node refsheet.js           # art reference sheet -> icons/icon-reference.png

Output sizes: phone and 7-inch 1080x1920, 10-inch 1440x2560.

**All three are exactly 9:16, and that is not optional.** Play's uploader
accepts 16:9 or 9:16 and nothing between: an earlier pass used 1200x1920 for
the 7-inch set, which is 5:8, and would have been refused at upload. The
10-inch set also has a higher floor than the others — each side must be at
least 1080px, which 1440x2560 clears.

Converting the recording (ffmpeg):

    ffmpeg -ss 4.6 -i vid/*.webm -vf "scale=1080:1920:flags=lanczos" \
      -r 30 -c:v libx264 -preset slow -crf 19 -pix_fmt yuv420p -an \
      -movflags +faststart vid/blastgrid-gameplay.mp4

The feature graphic must be **exactly** 1024x500, so `feature.js` renders at
deviceScaleFactor 1 rather than the 2 the screenshots use. It puts a real
gameplay frame behind the type instead of a drawing of one, and fades it out
to the left so the words have clean ground. Play crops it on some surfaces,
so nothing that matters sits near an edge.

`raw/`, `out/`, `out-feature/` and `vid/` are deliberately not committed — they are large and
regenerate in under a minute.

## Things that cost time to find out

**`recordVideo.size` only ever scales DOWN.** Asking for 1080x1920 from a
540x960 viewport does not upscale; it pastes the page into the corner of a
grey frame. Record at the viewport size and let ffmpeg do the upscale.

**The capture scripts stub `window.AdBridge`.** Without it the page thinks no
rewarded ad exists, which is correct outside the Android shell but blocks any
frame that needs the revive screen.

**The hint overlay arrives a beat after "Begin shift"**, not with it. Check
for it in a short loop or the first captures come out with it on top.

**The shove needs contact, not a tap.** Place a charge, step off it, then
hold the opposite direction for longer than `KICK_HOLD` (0.22s) so the player
leans into it and it slides.

**Captions sit over the HUD on purpose.** It is the least interesting strip
to someone browsing the store, so covering it keeps the whole board visible.

## The icon

Four concepts were drawn in code (`icons.js`) and none of them were liked, so
the icon is going out to an image generator instead. `ICON-PROMPT.md` carries
three prompts, the fixes for the usual failure modes, and a reject checklist;
`refsheet.js` renders the reference sheet that goes with them, showing the
real palette, the charge and worker sprites, and the adaptive-icon safe zone.

The sheet is committed rather than gitignored like the other output, because
it is handed to someone outside this repo and regenerating it needs the game
server running.

Two things that decide whether an icon works, and neither is about taste:
it has to survive being shrunk to a thumbnail, and nothing that matters can
sit near the edge, because Android crops the launcher icon to a circle.

Changing the icon is not only a store change. The launcher icon inside the
app (`app/src/main/res/drawable/ic_launcher_foreground.png`) has to change
with it, or the store shows one thing and the phone another - which means a
rebuilt AAB.

## The copy

`aso.md` holds the listing text — app name, short description, full
description — with the character counts and the reasoning for each, plus
where each piece goes in Play Console. Read the note at the end of it before
promising anything in a video edit.
