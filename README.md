# Nanu Blastgrid

A grid demolition game. One self-contained HTML file wrapped in a WebView.
No analytics, no external assets. The only network traffic is Google's ad SDK
requesting interstitials between runs (see "Ads" below) - the game itself still
makes no calls of its own.

## Layout

    app/src/main/assets/blastgrid.html   the entire game
    app/src/main/java/.../MainActivity.java  the WebView host

To update the game, replace the HTML file. Nothing else needs to change.

## Building from a phone

Push to `main`. GitHub Actions builds a debug APK and attaches it to a
timestamped release. No wrapper jar is committed, so the repo stays text-only
and is safe to manage from the GitHub mobile app.

## WebView settings that actually matter

These are the ones that fail silently rather than crashing:

| Setting | Consequence if missing |
|---|---|
| `setDomStorageEnabled(true)` | Every high score vanishes on relaunch |
| `VIBRATE` permission | Haptics button appears and does nothing |
| `setMediaPlaybackRequiresUserGesture(false)` | Web Audio never starts |
| `setTextZoom(100)` | A large system font scale breaks the HUD layout |
| `hardwareAccelerated="true"` | Canvas framerate collapses |
| `pauseTimers()` in `onPause` | Game keeps running behind the launcher |

Assets are served through `WebViewAssetLoader` on a virtual `https://blastgrid.local`
origin rather than `file://`, because a `file://` page gets an opaque origin in some
WebView builds and `localStorage` then behaves inconsistently.

## Release build

    gradle :app:bundleRelease

Produces an AAB for Play. You will need a signing config and Play App Signing.
`minifyEnabled` is deliberately `false`.

## Furthest-progress checkpoint

`snapshotRun` records the deepest sector ever reached, together with the kit
held at the time, under the `run` key in the same localStorage blob. It is
written at every sector gate and whenever the page is hidden, and it is never
discarded - not on death, not on starting over - so the overlay can always
offer "Continue - Sector N" rather than making anyone replay ground they have
already cleared.

Two deliberate limits:

- It restores the **start** of that sector, not the exact position. Persisting
  a half-destroyed level would mean serialising five typed arrays of grid
  state, and the sector gate is both simpler and fairer than dropping the
  player into rubble.
- It only moves **forward**. A short run after a deep one must not overwrite
  the deep one, so `snapshotRun` ignores any level at or below the stored one.

Nothing is offered until one sector has actually been cleared; sector 1 is
where a run starts anyway, so there is nothing to continue from before that.

## Telling the player what to do next

A sector needs both things: every drone dead *and* the exit found. Nothing
used to announce the first one, so clearing the patrol just made the level go
quiet and left people wandering it.

Three signals now, deliberately overlapping:

- **A one-shot line** across the top the moment the last drone dies - "find
  the exit" if it is still buried, "reach the exit" if it already turned up.
  `G.allDown` latches so it fires once per sector.
- **The drones tile in the HUD** re-purposes itself once the patrol is down:
  amber "find exit" while it is buried, green "exit open" once it is not. The
  banner fades; this does not, so there is always somewhere to read the
  objective.
- **The exit itself** pulses a halo past its own tile rather than the thin
  inner stroke it had, which read as decoration rather than as the way out.

## Back button

Back once pauses the run via `window.blastgrid.pause()`. Back twice within two
seconds quits.

## Ads

GMA Next-Gen SDK (`ads-mobile-sdk`), not the legacy `play-services-ads` -
Google put that one in maintenance mode in mid-2026. `MainActivity` preloads
one interstitial ad unit; `blastgrid.html`'s retry button asks for it via
`window.AdBridge.onRunEnd()` every third retry (never on the very first
"Begin shift" tap, and never blocking the retry if no ad is ready).

`@string/admob_app_id`, `INTERSTITIAL_AD_UNIT_ID` and `REWARDED_AD_UNIT_ID`
hold this app's real AdMob IDs as of v1.2.

GDPR/UK consent runs through Google's UMP SDK before any ad is requested
(`gatherConsentThenInitAds`). UMP decides per-region whether a form is needed,
so players outside the EEA/UK never see a prompt. If consent is declined,
`canRequestAds()` stays false, the preloaders never start, and the game runs
ad-free - `isReviveAvailable()` reports false so the revive prompt is never
offered.
