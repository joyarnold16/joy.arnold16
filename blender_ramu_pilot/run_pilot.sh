#!/usr/bin/env bash
# Linux/macOS twin of run_pilot.ps1 (this is what was used to test the
# pipeline in the cloud session). Usage: ./run_pilot.sh selftest|preview|test
set -euo pipefail
cd "$(dirname "$0")"
STAGE=${1:?usage: run_pilot.sh selftest|preview|test}
BLENDER=${BLENDER:-blender}
MANIFEST=${MANIFEST:-rig/ramu_rig.json}
AUDIO=${AUDIO:-audio/ramu_line.wav}
CUES=${CUES:-audio/ramu_line.rhubarb.json}
ENGINE=${ENGINE:-eevee}
CYCLES_DEVICE=${CYCLES_DEVICE:-GPU}

bl() { echo ">> blender $*"; "$BLENDER" -b --factory-startup --python-exit-code 1 "$@"; }

speech_args() {
  [ -f "$AUDIO" ] || { echo "WARN: no line audio at $AUDIO" >&2; return; }
  printf -- "--audio %s " "$AUDIO"
  [ -f "$CUES" ] && printf -- "--cues %s " "$CUES" || echo "WARN: no Rhubarb cues, amplitude fallback" >&2
}

chain() {  # $1 = out dir, $2 = rig blend
  local o=$1 rig=$2
  # shellcheck disable=SC2046
  bl "$rig" -P scripts/animate_test.py -- --shot shots/test_10s.json --out "$o/ramu_test_10s.blend" $(speech_args)
  bl "$o/ramu_test_10s.blend" -P scripts/qa_checks.py -- --out "$o/qa_report.json"
  bl "$o/ramu_test_10s.blend" -P scripts/render_bench.py -- --out-dir "$o/render" --engine "$ENGINE" \
     --cycles-device "$CYCLES_DEVICE" ${REQUIRE_IDLE_GPU:+--require-idle-gpu}
}

case "$STAGE" in
  selftest)
    o=out/selftest
    bl -P tools/make_placeholder_parts.py -- --out "$o/placeholder"
    bl -P scripts/build_rig.py -- --manifest "$o/placeholder/rig_manifest_soft.json" --out "$o/rig.blend"
    bl "$o/rig.blend" -P scripts/preview_appearance.py -- --out "$o/appearance_preview.png" --engine "$ENGINE"
    chain "$o" "$o/rig.blend" ;;
  preview)
    o=out/ramu
    bl -P scripts/build_rig.py -- --manifest "$MANIFEST" --out "$o/ramu_cutout_rig.blend"
    bl "$o/ramu_cutout_rig.blend" -P scripts/preview_appearance.py -- --out "$o/appearance_preview.png" --engine "$ENGINE"
    echo "STOP: review $o/appearance_preview.png and record appearance_approved in $MANIFEST" ;;
  test)
    chain out/ramu out/ramu/ramu_cutout_rig.blend ;;
  *) echo "unknown stage $STAGE" >&2; exit 2 ;;
esac
