# Icon

`source-hero.webp` is the artwork as it was supplied: a worker, a lit charge
and a blast cross over the coloured grid.

## The one thing that had to be fixed

The supplied file had **rounded corners baked into it**, with solid black in
the four corners — 13% of the frame. Both Play and Android apply their own
mask on top, so shipping it as-is would have shown a dark rim inside the
mask, rounded twice.

Measured corner radius was ~36% of the width, so the largest centred square
that clears it is 79% of the frame: `990x990+132+132`. Everything in this
folder and in `app/src/main/res` is cut from that crop.

    convert source-hero.webp -crop 990x990+132+132 +repage hero-clean.png
    convert hero-clean.png -resize 512x512! -alpha remove -alpha off -strip \
      store-icon-512.png

That crop costs 21% of the art, so a redrawn full-square version is worth
having. If one arrives, redo the two outputs from it without the crop step
and check the corners are real artwork, not black.

## What goes where

- `store-icon-512.png` → Play Console, Main store listing, App icon.
  512x512, no alpha. Play will round it itself.
- `app/src/main/res/drawable/ic_launcher_foreground.png` → the same image,
  full bleed, as the adaptive icon's foreground layer.
- `ic_launcher_monochrome.xml` → the themed-icon layer. A scene cannot be
  flattened to one colour, so it draws only the charge.

## The rule that decides whether an icon works

Android crops the launcher icon to a circle, and only the inner 72 of 108dp
is guaranteed to survive — a radius of 0.333 of the width from the centre.
Anything that matters outside that can be cut.

Earlier candidates were measured against this and scaled to fit. This one is
a full-bleed scene, so it is used at full size on purpose: the worker's face,
the charge and the blast all fall inside the safe circle, and what the mask
removes is only scenery at the edges. That was checked by rendering it under
circle and squircle masks before shipping it.
