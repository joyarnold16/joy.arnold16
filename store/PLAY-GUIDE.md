# Blastgrid 2.0 — Play Console, step by step

Live right now: **versionCode 8 / 1.7**, published 12 Oct 2026.
Going up: **versionCode 11 / 2.0**.

Do all three parts, then submit **once** at the end. One review instead of
three.

Keep these to hand:
- `app-release.aab`
- `blastgrid-store-screenshots.zip` (unzip it first)
- `aso.md` (the text to paste)

---

## PART 1 — The listing text

**Grow users → Store presence → Main store listing**

### 1.1 App name

Clear the field and paste:

    Blastgrid: Bomb Maze Arcade

27 of 30 characters.

If you want to keep the Nanu brand instead, use `Nanu Blastgrid: Bomb Maze`
(25/30). Pick one — do not try to fit both ideas in.

### 1.2 Short description

Clear it and paste:

    Bomb the maze, shove live charges, dodge drones. Fast offline arcade.

69 of 80.

### 1.3 Full description

Clear it and paste the whole FULL DESCRIPTION block from `aso.md` — from
"Blast your way through a collapsing demolition site." down to the last line.

Keep the bullet symbols. Play renders them.

### 1.4 Save

**Save** at the bottom. This saves a draft; nothing is public yet.

---

## PART 2 — The screenshots

Same page, scroll down to **Graphics**.

Unzip `blastgrid-store-screenshots.zip` first. Fifteen files:

    phone-01 … phone-05          Phone
    tablet7-01 … tablet7-05      7-inch tablet
    tablet10-01 … tablet10-05    10-inch tablet

For each of the three sections:

1. **Delete the old screenshots first.** Play keeps whatever is already there
   and simply adds yours after it, so the old art stays live next to the new
   if you skip this.
2. Upload that section's five files.
3. Check the order. `-01` must be first — it is the blast, and the first two
   are what people actually see in search results. `-02` is the shove, which
   is the only thing in this game another bomb game does not have.

Leave the icon and feature graphic alone — those are already the new ones.

**Save** again.

---

## PART 3 — The build

**Test and release → Production → Create new release**

### 3.1 Upload

Press **Upload** and choose `app-release.aab`.

Do **not** use "Add from library" — that list only contains bundles Play
already has, so your new one will not be in it.

### 3.2 The warning you will see

> This App Bundle contains native code, and you've not uploaded a
> deobfuscation file.

**Expected. Ignore it.** The app is built with `minifyEnabled false`, so
there is no mapping file to give. It does not block anything.

### 3.3 Release name

Play fills in `11 (2.0)` by itself. Leave it.

### 3.4 Release notes — "What's new"

Paste this:

    New look. The whole site has been rebuilt - blocks that stand up off
    the floor, real colour, and blasts that throw a shockwave ring.

    Tougher. Two hits to a crew now, not three.

    Fairer. Two revives a run instead of one.

    Also: more to blow up in every sector, a third drone on patrol, a
    marker on the brick hiding the lift, and a CHARGE button you can
    actually read.

### 3.5 Save

**Save**, then **Review release**, then **Start rollout to Production**.

If it offers a staged rollout percentage, set it to **100%**. With five
active users, a partial rollout tells you nothing.

---

## PART 4 — Actually submit it

**This is the step that gets missed.** Saving a release is not publishing it.

**Publishing overview** (left sidebar, near the top).

You will see a list under **Changes ready to send for review**. Expect
**several rows, not two** — the app name, short description, full
description, and each screenshot set each count separately, plus the release.
That is normal.

Press **Send change for review**.

---

## AFTER

- Review usually takes a few hours to a couple of days. It has been same-day
  for this app before.
- **Play does not reliably email you about an approved update.** Do not wait
  for a mail. Check the store page's "Updated on" date, or the Production
  track summary in Console.
- Once it is live, the store page can take a few more hours to show the new
  screenshots and name — caching, not an error.

### Then leave it alone for two to three weeks

The numbers that matter are on the dashboard: **device acquisitions**, **first
opens**, **monthly active devices**. They are 1 / 1 / 5 today.

Do not change the listing again during that window. If you change it while
you are measuring it, you will not know what did what.

---

## One more thing, on a deadline

The dashboard is showing:

> Declare your trader status by February 1, 2027

Not urgent today, but it is not optional — without it Play can restrict
distribution in the EU. It is a short form under **Settings → Trader status**.
Worth doing on a quiet day rather than at the deadline.
