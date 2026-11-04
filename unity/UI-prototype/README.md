# UI prototype

`hud.html` is the in-game HUD, built to match the art direction the owner
chose (industrial demolition site, red/steel plates, cyan accents, chunky
bevelled buttons). `hud.png` is it rendered.

It is HTML on purpose. The authoring environment has no Unity, so a design
written there cannot be looked at before handover - but HTML can be rendered
and screenshotted, so the look can be agreed on before anyone spends editor
time on it.

It is also close to where it is going: Unity's UI Toolkit uses UXML and USS,
which are XML and CSS in all but name. The plates, the bevels, the glow, the
layout all port across fairly directly.

The grid background is a stand-in for the 3D scene, so the UI can be judged
over something rather than over black. It is not part of the design.

## Open question

The reference art the design follows is **landscape**; the shipped game is
**portrait-locked**. This prototype is landscape. If the game stays portrait
the layout needs rethinking - the three top plates will not fit side by side.
