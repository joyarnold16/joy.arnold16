# Line audio for the test

`shots/test_10s.json` expects:

- `ramu_line.wav`: the short Hindi line in Ramu's approved voice, generated
  through the existing ComfyUI audio workflow. `06_AUDIO\VOICES\ramu.wav` is
  the voice reference, not the line. Proposed line, about 2 s, which fits
  frames 112–164 before the reach at 172:

  > अरे वाह, आज तो खीर बनी है!  ("Oh wow, there's kheer today!")

  It was chosen to exercise most mouth shapes: closed lips in *बनी*, wide open
  in *आज*, rounded in *वाह*, spread in *खीर*. Change it freely. A line longer
  than about 2.5 s makes the generator warn that speech overlaps the reach;
  if so, move `bowl.reach`, `grip_frame` and `lift` later in the shot.
- `ramu_line.rhubarb.json`: mouth cues from Rhubarb Lip Sync
  (free, offline: https://github.com/DanielSWolf/rhubarb-lip-sync):

      rhubarb -r phonetic -f json --extendedShapes GHX -o ramu_line.rhubarb.json ramu_line.wav

  Use `-r phonetic`. The default recogniser is English-only and gives poor
  shapes for Hindi. Without a cue file the pipeline falls back to a loudness
  envelope, which is plain lip-flap.

Trim leading and trailing silence from the WAV. The audio is placed at
`line.start_frame`, so leading silence delays the mouth by the same amount.
These audio files are not committed.
