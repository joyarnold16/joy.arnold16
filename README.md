# Nanu Blastgrid

A grid demolition game. One self-contained HTML file wrapped in a WebView.
No network calls, no analytics, no external assets.

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

## Back button

Back once pauses the run via `window.blastgrid.pause()`. Back twice within two
seconds quits.
