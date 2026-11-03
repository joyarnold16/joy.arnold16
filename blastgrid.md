# Blastgrid — working notes

Shared context for anyone (human or assistant) working on this repository.
Read this before changing anything; most of it is here because it cost
somebody time to find out.

---

## There are two codebases

**`app/`** — the live game. One HTML file inside an Android WebView wrapper.
This is what is on Google Play right now.

    app/src/main/assets/blastgrid.html          the entire game, ~3000 lines
    app/src/main/java/com/joy/blastgrid/MainActivity.java   WebView host, ads, consent
    app/build.gradle                            versionCode / versionName live here

**`unity/`** — the Unity 6.3 port, in progress. Unity **6000.3.26f1**, Android
module installed.

    unity/Assets/Scripts/Sim/      game rules, plain C#, NO UnityEngine
    unity/Assets/Tests/EditMode/   NUnit tests for the above
    unity/README.md                how to open it

The HTML game is the **reference implementation**. Where the two disagree, the
HTML one is right, because it is the one that has been played.

---

## How work gets verified here

Every change to `app/` has been run before being handed over: the game is
served locally and driven with Playwright, or the shipped function is pulled
out of the HTML and unit-tested in Node. That discipline has caught real
defects more than once, including one the author was sure was fine.

There is **no C# compiler and no Unity in the authoring environment**, so
`unity/` code cannot be compiled before handover. The way round it:

- Keep the simulation free of `UnityEngine` so its logic can be mirrored and
  diffed against the JS it was ported from.
- Ship NUnit tests with it so the person at the editor can run them.
- Treat a red test as "the port is wrong", not "the game is wrong".

`LevelGen` was checked this way: mirrored, then diffed against the shipped JS
across 24 date/sector combinations. All matched bit for bit.

---

## Traps that have already bitten

**`--` inside an XML comment** breaks aapt2 and fails the whole build. A
comment mentioning a CSS custom property by name (`--void`) did exactly that.

**AGP 8 does not generate `BuildConfig`** unless `buildFeatures { buildConfig
true }` is set. `MainActivity` reads `BuildConfig.DEBUG`, so the flag is
required.

**JS `Array.sort` is stable, C# `List<T>.Sort` is not.** The exit is chosen
from bricks sorted by distance; without a tie-break on insertion order the two
builds pick different exits and the daily run desynchronises. `LevelGen`
tie-breaks deliberately — do not "simplify" it.

**Draw order in the level generator is load-bearing.** A daily run must lay
down the same grid in both builds. Reordering, adding or removing a single
`rnd()` call in `buildLevel` / `LevelGen.Build` breaks that silently. If you
change generation, change it in both and re-run the diff.

**The spawn pocket must stay walkable.** `(1,1) (2,1) (1,2) (3,1) (1,3)` are
never filled. A test asserts this over 300 generated sectors.

**Density has a history.** It was 0.30, cut to 0.16 after a report of jank,
and raised to 0.28 once the jank turned out to be an FX watchdog with a
different cause. 0.16 left sector 1 three-quarters bare floor. Do not cut it
again without looking at what the arena actually contains.

---

## Things that are deliberate, not accidental

**The shove is the game's identity.** Walking into a live charge makes it
slide; a kill made that way scores double and is called out as `SHOVE +n`.
It is the only thing here a plain Bomberman clone does not do. Do not
flatten it into an ordinary kill.

**Portrait lock.** `android:screenOrientation="portrait"`. This excludes some
landscape-only tablets and Android Auto — that trade was made knowingly. The
art direction being explored is landscape, so this is an open question, not a
bug.

**Ads only fire on a natural break.** An interstitial every third retry, never
on the first run, never blocking the retry. The rewarded revive is offered
once per run and only when an ad is genuinely loaded.

**Release builds never carry the debug revive bypass.** `BuildConfig.DEBUG`
guards it; the compiler strips the branch. Verified by grepping the AAB's dex
for the debug log string.

---

## Shipping

CI builds on push to `main`:

- `.github/workflows/build.yml` — debug APK, runs automatically
- `.github/workflows/release.yml` — signed AAB, manual dispatch only

Never assume a build passed. Check `conclusion` on the run, then open the
artifact and confirm what is inside it.

Play Console notes that have caught people out:

- `versionCode` must be **higher** than what is live. Live is tracked in
  `app/build.gradle`.
- Uploading a new bundle is **Upload**, not "Add from library" — the library
  only lists bundles Play already has.
- Saving a release is not submitting it. **Publishing overview → Submit.**
- The missing-deobfuscation-file warning is expected (`minifyEnabled false`)
  and does not block anything.
- Play does not always email about an approved update. Check the store page's
  "Updated on" date and the Production track summary instead.

---

## Working alongside someone else

The repository is the only shared state. If work is not committed, the other
side cannot see it and will duplicate or overwrite it.

- Commit early, even when unfinished.
- `git fetch` before writing. Pushes have already been rejected here because
  someone else's commit landed first.
- One file, one owner at a time. Split by directory, not by task.
- Say in the commit message what was verified and how.

---

## Current state

Live on Play: **versionCode 8 / 1.7**.
In the repo and not yet shipped: density and patrol changes, the exit survey
mark, the shove reward, the debug revive path — all built and tested as debug
APKs, bumped to **versionCode 10 / 1.9**.

The Unity port has the level generator and its tests, and nothing else yet.
