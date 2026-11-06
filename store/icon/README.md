# Icon

`source-hero.webp` is the artwork as supplied: the worker, a lit charge and
the blast cross over the coloured grid.

## What goes where

    store-icon-512.png                     Play Console -> Main store listing -> App icon
    ../../app/src/main/res/drawable/ic_launcher_foreground.png   adaptive icon, foreground layer
    ../../app/src/main/res/drawable/ic_launcher_monochrome.xml   themed-icon layer

Both images are the same square at 512x512, full bleed, no alpha:

    convert source-hero.webp -resize 512x512! -alpha remove -alpha off -strip store-icon-512.png

## The rule that decides whether an icon works

Android crops the launcher icon to a circle, and only the inner 72 of 108dp
is guaranteed to survive: a radius of **0.333 of the width** from the centre.
Anything that matters outside that can be cut, on some launchers.

This art is used full bleed on purpose. Measured, the worker's head, the
charge and the blast all land near r=0.20, well inside the safe circle, and
what a mask removes is scenery - blocks, rubble, smoke - at the edges. It was
rendered under circle and squircle masks, and at 96px and 48px, before being
committed.

## What went wrong with the first version, so it is not repeated

The first file supplied had **rounded corners baked into it**, with solid
black behind them - 13% of the frame, and ~36% of the width in corner radius.
Play and Android apply their own mask on top, so that would have shipped a
dark rim inside the mask, rounded twice. It had to be cropped to
`990x990+132+132`, losing 21% of the art.

The replacement was asked for as a plain full square and checks clean: the
diagonal test that found the mask returns 0px, and all four corners are real
artwork. **Any future icon must pass that same check before it is used.**

    # corners must be artwork, not black
    for p in 0,0 12,12 1241,0 0,1241 1241,1241; do
      convert source-hero.webp -format "%[pixel:p{$p}]" info:; echo
    done
