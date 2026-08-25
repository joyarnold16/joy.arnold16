# Yoga Guide

Condition-based therapeutic yoga for Android. 72 poses, 17 conditions, 40
sessions — fully offline, no account, no analytics, no internet permission.

The thing that makes it different from a generic pose app is the **safety
filter**. You tell it what you are working around — pregnancy, high blood
pressure, a slipped disc, glaucoma — and every session is rewritten before you
see it. Contraindicated poses are swapped for a gentler substitute or dropped,
and the app tells you what it changed and why.

## Layout

    app/src/main/assets/
      index.html                 the shell
      css/app.css                light/dark tokens, all components
      js/figure.js               SVG line-art renderer
      js/figures.js              64 pose illustrations, as joint coordinates
      js/poses.js                72 poses with contraindications
      js/conditions.js           17 conditions and their sessions
      js/safety.js               the contraindication filter
      js/store.js                localStorage: profile, history, streaks
      js/native.js               bridge to Android, with web fallbacks
      js/audio.js                synthesised chimes
      js/breath.js               the breath pacer
      js/player.js               the session player
      js/ui.js, js/screens.js    router and screens
      js/app.js                  bootstrap

    app/src/main/java/com/joy/yogaguide/
      MainActivity.java          WebView host, TTS, back handling
      NativeBridge.java          window.Android
      Reminders.java             notification channel, scheduling
      ReminderWorker.java        posts the daily reminder, re-arms itself

    tools/
      validate-content.js        data integrity check (no dependencies)
      smoke.js                   end-to-end test in Chromium
      figure-sheet.js            renders every illustration to one PNG

## Illustrations

There are no image files. Every pose is drawn by `js/figure.js` from a handful
of joint coordinates in a shared 120×100 space with the floor at y=92. That
keeps the whole illustration set to a few hundred KB of text instead of tens of
megabytes of PNG, makes 72 poses automatically consistent with each other, and
keeps the repository text-only — which is what makes it manageable from a phone.

Coordinates are impossible to proof-read, so:

    node tools/figure-sheet.js       # -> tools/shots/figures.png

renders all 64 figures to one labelled contact sheet.

## The safety model

`js/poses.js` tags every pose with `avoid`, a list of health flags from
`YG.FLAGS`. `js/safety.js` resolves a pose against a profile by walking the
`alt` chain until it finds something safe, or dropping the step if nothing is.

Two rules the code depends on, both enforced by `tools/validate-content.js`:

- A substitute may share *some* of a pose's contraindications (the chain keeps
  walking) but never *all* of them — a substitute that shares every restriction
  can never actually be reached.
- Every `avoid` flag needs matching `cautions` prose. A pose that silently
  disappears teaches the user nothing.

Sessions in `js/conditions.js` are written as the ideal sequence for someone
with no restrictions. The filter is what makes them safe, not self-censorship at
authoring time.

## Testing

    node tools/validate-content.js   # data integrity, no dependencies
    node tools/smoke.js              # end-to-end in Chromium
    node tools/smoke.js --shots      # ...and write screenshots

The smoke test completes onboarding as a pregnant user with high blood pressure
and then asserts that no contraindicated pose survives in any of the 40
sessions, and no advanced pose reaches a beginner.

The whole app runs in a desktop browser — every native call in `js/native.js`
has a web fallback — so `python3 -m http.server` inside `assets/` is a working
development loop.

## Building from a phone

Push to `main`. GitHub Actions validates the content, runs the smoke test,
builds a debug APK and attaches it to a timestamped release. No wrapper jar is
committed, so the repo stays text-only and is safe to manage from the GitHub
mobile app.

## WebView settings that actually matter

The ones that fail silently rather than crashing:

| Setting | Consequence if missing |
|---|---|
| `setDomStorageEnabled(true)` | Health profile, streak and history vanish on relaunch |
| `setMediaPlaybackRequiresUserGesture(false)` | Chimes stay muted until a tap |
| `setTextZoom(100)` | A large system font scale breaks the player layout |
| `hardwareAccelerated="true"` | The breath orb animation stutters |
| `pauseTimers()` in `onPause` | Session clock keeps running behind the launcher |
| `FLAG_KEEP_SCREEN_ON` | Screen dims during a 90-second hold |

Assets are served through `WebViewAssetLoader` on a virtual
`https://yogaguide.local` origin rather than `file://`, because a `file://` page
gets an opaque origin in some WebView builds and `localStorage` then behaves
inconsistently.

Speech uses the platform `TextToSpeech` engine rather than the WebView's
`SpeechSynthesis`, which reports voices and then never fires an utterance on
several OEM builds.

## Release build

    gradle :app:bundleRelease

Produces an AAB for Play. Needs `app/release.keystore` plus a
`keystore.properties` (see `keystore.properties.example`), or the four
`RELEASE_*` secrets and the manual **Release AAB** workflow.

Before submitting: content rating questionnaire, a data-safety declaration of
"no data collected" (accurate — there is no INTERNET permission), the privacy
policy in `docs/`, and, for a new personal developer account, the 12-tester /
14-day closed testing period.

## Not medical advice

The app says so on first launch and on every condition screen. It does not
diagnose, treat or cure anything. The contraindications encoded here are the
conventional ones taught with each posture and are deliberately cautious: the
cost of dropping a safe pose is a slightly shorter session, and the cost of
keeping an unsafe one is an injury.
