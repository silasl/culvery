#!/usr/bin/env bash
# The 4c Appendix B numbers for the Culvery release installed on one emulator or device (4c design section 4.5): the APK's
# size, five discarded warm-up cold starts (a fresh install starts slowly for its first few launches), then five measured
# cold starts (am start -W TotalTime, "Fully drawn" time from reportFullyDrawn, skipped frames, and the Google Play
# services crashes logcat showed during the run), then gfxinfo's p50/p90 and Dalvik and native PSS after the last start.
# TotalTime ends at the first real draw, which is Home with its cards; "Fully drawn" is the same moment reported by the app.
# A run with Play services crashes is noisy: repeat it. It only stops and starts Culvery. Takes about two minutes.
# Usage: bash tools/measure-release.sh [serial]   (default emulator-5554; install the signed release on it first)
set -Eeuo pipefail

SERIAL=${1:-emulator-5554}
PKG=uk.co.siland.culvery
APK=app/build/outputs/apk/release/app-release.apk
SETTLE_SECONDS=10
ADB=${ADB:-adb}
command -v "$ADB" >/dev/null || ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb"
dev() { "$ADB" -s "$SERIAL" "$@"; }
trap 'echo "measure-release.sh stopped at line $LINENO. Check that $SERIAL is running (adb devices) and the release build is installed on it." >&2' ERR

echo "APK size: $(wc -c < "$APK") bytes"
echo "Warm-up: 5 discarded cold starts."
for warm in 1 2 3 4 5; do
  dev shell am force-stop "$PKG"
  dev shell am start -W -n "$PKG/.MainActivity" >/dev/null
  sleep "$SETTLE_SECONDS"
done
for run in 1 2 3 4 5; do
  dev shell am force-stop "$PKG"
  dev logcat -c || true
  total=$(dev shell am start -W -n "$PKG/.MainActivity" | tr -d '\r' | sed -n 's/^TotalTime: //p')
  sleep "$SETTLE_SECONDS"
  pid=$(dev shell pidof "$PKG" | tr -d '\r' | awk '{ print $1 }' || true)
  [ -n "$pid" ] || { echo "Culvery isn't running after the start (run $run)."; exit 1; }
  skipped=$(dev logcat -d --pid="$pid" -s Choreographer:I | tr -d '\r' | sed -n 's/.*Skipped \([0-9]*\) frames.*/\1/p' | awk '{ s += $1 } END { print s + 0 }')
  # ActivityTaskManager prints "+1s234ms", "+1s" or "+850ms".
  drawn=$(dev logcat -d | tr -d '\r' | { grep "Fully drawn $PKG/" || true; } | tail -n 1 | sed -n 's/.*: +//p' |
    awk '{ s = 0; if (match($0, /[0-9]+s/)) s = substr($0, RSTART, RLENGTH - 1) * 1000; if (match($0, /[0-9]+ms/)) s += substr($0, RSTART, RLENGTH - 2); print s }')
  gms=$(dev logcat -d | tr -d '\r' | { grep -c "Process: com.google.android.gms" || true; })
  echo "Run $run: TotalTime $total ms, fully drawn ${drawn:-n/a} ms, skipped frames $skipped, Play services crashes $gms"
done
echo "gfxinfo (last cold start):"
dev shell dumpsys gfxinfo "$PKG" | tr -d '\r' | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile" || echo "  (gfxinfo gave no frame data)"
echo "PSS in KB (last cold start):"
dev shell dumpsys meminfo "$PKG" | tr -d '\r' | grep -E "^ *(Dalvik Heap|Native Heap) " || echo "  (meminfo gave no heap data)"
