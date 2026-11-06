# Blastgrid — Play Store listing copy

Paste these into Play Console → Grow users → Store presence → Main store listing.

---

## APP NAME  (limit 30)

**Blastgrid: Bomb Maze Arcade**                                    `27/30`

Why this and not "Nanu Blastgrid":

Play weights the app name more heavily than any other text when it decides
what to show for a search. The current name contains no word anybody types.
"Nanu" is a brand nobody has heard of yet, and "Blastgrid" is an invented
word with no search volume — so the listing can only be found by someone who
already knows its exact name.

This version keeps the real name first and spends the rest on the three words
people actually search: **bomb**, **maze**, **arcade**.

### If you want to keep the Nanu brand
Two options, pick one:
- `Nanu Blastgrid: Bomb Maze`  (26/30) — keeps the brand, loses "arcade"
- Keep **Blastgrid: Bomb Maze Arcade** as the store name and leave the
  developer name as Nanu. The brand still shows under the title on every
  listing and on your developer page.

The launcher icon label (what shows under the icon on the phone) is separate
and stays short: **Blastgrid**.

---

## SHORT DESCRIPTION  (limit 80)

**Bomb the maze, shove live charges, dodge drones. Fast offline arcade.**  `69/80`

This line shows directly under the title in search results and is indexed.
It carries: bomb, maze, dodge, offline, arcade.

---

## FULL DESCRIPTION  (limit 4000)

Blast your way through a collapsing demolition site.

Drop a charge, get clear, and watch the blast tear a cross through the grid.
Break the blocks, scrap the patrol drones, and find the lift before the clock
runs out. Then do it again, one sector deeper.

**THE SHOVE**
Walk into a charge you have already placed and it slides. Shove one into a
drone and the kill scores double. It is the shot nothing else in this genre
gives you, and it turns a careful bomb maze game into something you can play
aggressively.

**WHAT YOU ARE UP AGAINST**
• Patrol drones that hunt, drift and phase through walls
• Wooden pallets that catch fire and spread it
• Smoke that chokes you if you stay in it
• Concrete columns that come down if you hit them hard enough
• A clock, and a site that gets meaner every sector

**EVERY RUN IS DIFFERENT**
• Pick a perk between sectors — longer blasts, more charges, remote
  detonation, a reinforced vest, steel toes that launch a shoved charge
• Your furthest sector is kept, so a bad run never costs you everything
• A daily run on a grid that is the same for every player, with a day streak

**BUILT TO BE PLAYED ANYWHERE**
• Plays offline — the game needs no connection (ads and the optional revive do)
• One hand, four directions and a charge button
• No account, no sign-up
• Small download, runs on modest phones

Free to play, with an optional ad to get back on your feet after a bad run.

If you grew up on bomb maze arcade games — dropping bombs, blowing up blocks,
cornering enemies in a grid — this is that, with a demolition crew, a patrol
of drones, and a kick that actually pays.

---

## KEYWORDS THIS COPY NOW COVERS

bomb, bomb game, bomb maze, blast, blast game, maze, arcade, offline,
offline game, offline arcade, drones, demolition, retro arcade, grid,
explosion, single player, no wifi

Play does not have a keyword field — it reads the name, the short
description and the full description. All three now carry these.

---

## A note on the word "offline"

The app serves AdMob, so "offline" looks like a contradiction. It is not, and
this was checked in `MainActivity.java` rather than assumed:

- the game is served from inside the APK by `WebViewAssetLoader`, so no asset
  is ever fetched over the network;
- `gatherConsentThenInitAds()` is asynchronous and `web.loadUrl(GAME_URL)`
  does not wait for it, so consent never gates the game starting;
- the network-failure branch is explicit - ads stay off for that launch and
  play continues.

So with no connection the game runs in full; the ads and the rewarded revive
are what go missing. The bullet says exactly that rather than a bare "no
connection needed", because the revive genuinely is unavailable offline.

Keep the word. "offline games" is one of the highest-volume searches this
listing can realistically compete for, and the claim is true.

## WHAT THIS WILL AND WILL NOT DO

It makes the listing findable at all. Right now it is not: no word in it
matches anything a person types.

It will not put you near the top for "bomb game". That category has apps with
millions of installs, and Play ranks on install volume and ratings as well as
relevance. Expect the long tail — the more specific searches — not the head.

Realistic: this moves you from roughly one install a month to somewhere in
the handful-per-month range. Getting beyond that needs people seeing the game
somewhere other than Play.

---

# PLAY CONSOLE — WHERE EACH PIECE GOES

**Store listing text**
Grow users → Store presence → Main store listing
- App name → the title above
- Short description → the 69-character line
- Full description → the long block

**Screenshots**
Same page, scroll down to Graphics.
- Phone → the 5 files named `phone-01` … `phone-05`
- 7-inch tablet → `tablet7-01` … `tablet7-05`
- 10-inch tablet → `tablet10-01` … `tablet10-05`
Delete the old ones first; Play keeps them otherwise.

**The new build**
Test and release → Production → Create new release → upload `app-release.aab`
(**Upload**, not "Add from library" — the library only lists bundles Play
already has.) This is versionCode 11 / 2.0, which is higher than the live 8,
so Play will accept it.

**Then actually submit**
Saving a release is not submitting it. Go to **Publishing overview → Submit**,
or nothing ships. Expect several rows in "Changes in review" — each asset type
counts separately.

The missing-deobfuscation-file warning is expected and does not block.

---

# THE VIDEO

Two clips, both 1080x1920, no sound, straight from the real build:

- `blastgrid-gameplay.mp4` — 20s, several charges placed and set off
- `blastgrid-shove-hook.mp4` — 9s, built around the shove

These are raw footage, not finished posts. For Gemini, the useful prompt is
roughly:

> Here is 9 seconds of gameplay from a mobile arcade game. Cut it into a
> vertical short. The hook is the move at the centre: the player pushes a
> live bomb along the floor into a patrol drone. Add on-screen text that
> lands on that moment — something like "most bomb games don't let you do
> this" — then end on the app name, Blastgrid.

What matters in the edit: the shove has to be visible in the first two or
three seconds. It is the only thing here that another game in this genre
does not have, so it is the whole reason someone stops scrolling.

A note on honesty, worth keeping: do not let the edit imply anything the
game does not do. A clip that oversells gets installs that uninstall, and
Play reads that as a bad signal.
