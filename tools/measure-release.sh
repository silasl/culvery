#!/usr/bin/env bash
# The 4c Appendix B numbers for the Culvery release installed on one emulator or device (4c design section 4.5): the APK's
# size, five cold starts (am start -W TotalTime and skipped frames each), then gfxinfo's p50/p90 and Dalvik and native
# PSS after the last start. It only stops and starts Culvery. Takes about a minute.
# Usage: bash tools/measure-release.sh [serial]   (default emulator-5554)
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
for run in 1 2 3 4 5; do
  dev shell am force-stop "$PKG"
  dev logcat -c || true
  total=$(dev shell am start -W -n "$PKG/.MainActivity" | tr -d '\r' | sed -n 's/^TotalTime: //p')
  sleep "$SETTLE_SECONDS"
  pid=$(dev shell pidof "$PKG" | tr -d '\r' | awk '{ print $1 }' || true)
  [ -n "$pid" ] || { echo "Culvery isn't running after the start (run $run)."; exit 1; }
  skipped=$(dev logcat -d --pid="$pid" -s Choreographer:I | tr -d '\r' | sed -n 's/.*Skipped \([0-9]*\) frames.*/\1/p' | awk '{ s += $1 } END { print s + 0 }')
  echo "Run $run: TotalTime $total ms, skipped frames $skipped"
done
echo "gfxinfo (last cold start):"
dev shell dumpsys gfxinfo "$PKG" | tr -d '\r' | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile"
echo "PSS in KB (last cold start):"
dev shell dumpsys meminfo "$PKG" | tr -d '\r' | grep -E "^ *(Dalvik Heap|Native Heap) "
