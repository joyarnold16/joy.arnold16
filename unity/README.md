# Blastgrid, Unity port

Unity **6.3 LTS (6000.3.26f1)**, Android module required.

## What is here

    Assets/Scripts/Sim/     the game's rules, as plain C# with no UnityEngine
    Assets/Tests/EditMode/  NUnit tests for the above

The simulation deliberately has **no UnityEngine dependency** (its asmdef sets
`noEngineReferences`). Rendering and input will sit in a separate layer that
reads the sim. Two reasons: the rules can be tested in the Editor in under a
second without entering play mode, and the port can be checked against the web
build rather than merely compiled.

## Checked against the web build

`LevelGen` is a port of `buildLevel()` from `app/src/main/assets/blastgrid.html`,
kept deliberately close to it - including the order of random draws. Reordering
a single `rnd()` call would silently desynchronise the two, and a daily run is
supposed to lay down the same sector in both.

Its logic was verified by mirroring it and comparing the generated grid, brick
palette, exit and drone list against the shipped JS across 24 combinations of
date and sector. All matched bit for bit.

One difference worth knowing about: JS `Array.sort` is stable and
`List<T>.Sort` is not, so where two bricks sit the same distance from the
spawn an unstable sort could choose a different exit. `LevelGen` tie-breaks on
insertion order to match.

## Opening it

This folder is already a Unity project - `ProjectSettings/ProjectVersion.txt`
pins it to 6000.3.26f1, and Unity fills in the rest (Library, packages,
default settings) the first time it opens. There is nothing to create and
nothing to copy.

1. Unity Hub -> **Add** -> **Add project from disk** -> pick this `unity`
   folder.
2. Open it. First open takes a few minutes while Unity imports.
3. **Window -> General -> Test Runner -> EditMode -> Run All.**

Everything green means the port compiles and behaves like the web build, and
the rendering layer can be built on top. Anything red is a defect in the port,
not in the game.

If Test Runner is missing from the Window menu, the Test Framework package did
not come in by default: **Window -> Package Manager -> Unity Registry ->
Test Framework -> Install**.
