#!/usr/bin/env bash
# The 4c Appendix B numbers for the Culvery release installed on one emulator or device (4c design §4.5): the APK's
# size, five cold starts (am start -W TotalTime and skipped frames each), then gfxinfo's p50/p90 and Dalvik and native
# PSS after the last start. It only stops and starts Culvery. Takes about a minute.
# Usage: bash tools/measure-release.sh [serial]   (default emulator-5554)
set -euo pipefail

SERIAL=${1:-emulator-5554}
PKG=uk.co.siland.culvery
APK=app/build/outputs/apk/release/app-release.apk
SETTLE_SECONDS=10
ADB=${ADB:-adb}
command -v "$ADB" >/dev/null || ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb"
dev() { "$ADB" -s "$SERIAL" "$@"; }

echo "APK size: $(wc -c < "$APK") bytes"
for run in 1 2 3 4 5; do
  dev shell am force-stop "$PKG"
  dev logcat -c
  total=$(dev shell am start -W -n "$PKG/.MainActivity" | tr -d '\r' | sed -n 's/^TotalTime: //p')
  sleep "$SETTLE_SECONDS"
  skipped=$(dev logcat -d -s Choreographer:I | tr -d '\r' | sed -n 's/.*Skipped \([0-9]*\) frames.*/\1/p' | awk '{ s += $1 } END { print s + 0 }')
  echo "Run $run: TotalTime $total ms, skipped frames $skipped"
done
echo "gfxinfo (last cold start):"
dev shell dumpsys gfxinfo "$PKG" | tr -d '\r' | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile"
echo "PSS in KB (last cold start):"
dev shell dumpsys meminfo "$PKG" | tr -d '\r' | grep -E "^ *(Dalvik Heap|Native Heap) "
