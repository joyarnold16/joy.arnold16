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

Output sizes: phone 1080x1920, tablet7 1200x1920, tablet10 1600x2560 — all
inside Play's accepted range.

Converting the recording (ffmpeg):

    ffmpeg -ss 4.6 -i vid/*.webm -vf "scale=1080:1920:flags=lanczos" \
      -r 30 -c:v libx264 -preset slow -crf 19 -pix_fmt yuv420p -an \
      -movflags +faststart vid/blastgrid-gameplay.mp4

`raw/`, `out/` and `vid/` are deliberately not committed — they are large and
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

## The copy

`aso.md` holds the listing text — app name, short description, full
description — with the character counts and the reasoning for each, plus
where each piece goes in Play Console. Read the note at the end of it before
promising anything in a video edit.
