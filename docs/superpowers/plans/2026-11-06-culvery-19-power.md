# Culvery Plan 19: Power — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan is executed subagent-driven.

**Goal:** With the screen dark or on the night clock, Culvery sends nothing to the speakers, the Hue bridge, Google Tasks or Home Assistant, and each card is fresh a moment after waking. The camera asks for less, logs what it got and why it failed, and comes back in seconds. A failing loop backs off and logs once. Bins are read the same day after an overnight outage, Hue is read every 5 minutes, and runs B and C give the numbers.

**Architecture:**
- `core/plugin` gets `ScreenLit` (Culvery in front and the screen awake). The app provides it from `ShellAlerts.inFront` and `DisplayState.screen`. `MusicChecks`, `LightsFollow`, `ShoppingChecks` and `HomeAssistant` gate on it in place of `AlertHost.inFront`; Ring alerts, door pruning and the music quieter keep `inFront`.
- `FlowRetry.kt` gets `FailureRun`: a loop's failures in a row, its doubling wait and its two log lines (first failure, recovery).
- The camera's request turns off noise reduction, edges and autofocus where it can, and logs its choice. Its failures carry their cause, and `Presence` retries device errors after 10 s, doubling. `ShellViewModel.kioskExited` moves into a `KioskExit` singleton that `DisplayState` also reads.
- Bins split today's single catch so network-down attempts don't use up the day. Hue reads every 5 minutes and reopens a stream that has been silent for 10.

**Tech Stack:** Kotlin, coroutines, Compose, Hilt, Camera2, OkHttp, JUnit4, Robolectric, Truth.

**Spec:** `docs/superpowers/specs/2026-11-06-culvery-19-power-design.md`. Read it before any task. Its "(Claude's call)" items are settled; don't reopen them.

## Global Constraints

- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug`. Per task, run only the modules it names (`./gradlew :<module>:testDebugUnitTest`). `LogHygieneTest` runs with `./gradlew -p build-logic :convention:test`.
- **No deprecated APIs** without asking the user. minSdk 29, targetSdk and compileSdk 35 (`build-logic/convention/src/main/kotlin/AndroidConfig.kt`, `AndroidApplicationConventionPlugin.kt`). The Camera2 names this plan uses are current at API 35: `CaptureRequest.NOISE_REDUCTION_MODE`, `EDGE_MODE`, `CONTROL_AF_MODE`, `CameraMetadata.NOISE_REDUCTION_MODE_OFF`, `EDGE_MODE_OFF`, `CONTROL_AF_MODE_OFF`, `CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES`, `EDGE_AVAILABLE_EDGE_MODES`, `CONTROL_AF_AVAILABLE_MODES`, and `CameraDevice.StateCallback.ERROR_CAMERA_IN_USE` (1), `ERROR_MAX_CAMERAS_IN_USE` (2), `ERROR_CAMERA_DISABLED` (3), `ERROR_CAMERA_DEVICE` (4), `ERROR_CAMERA_SERVICE` (5). Check the IDE's deprecation marks on anything else you reach for.
- **Logs carry only numbers, mode names and exception class names:** no names, addresses, URLs, tokens or file names. A new interpolated value is a qualified id added to `LogHygiene.ALLOWED` with a KDoc line saying what it is, or a `.code` / `::class.simpleName`. `LogHygieneTest` must pass.
- **Release builds strip `Log.d`, `Log.v` and `Log.i`** (`app/proguard-rules.pro`). A line the tablet check reads must be `Log.w`.
- Comments say only what the code can't. Files stay UTF-8 with their existing line endings.
- **Commits:** plain imperative message, no Co-Authored-By or any other trailer. Don't push or merge.
- **Robolectric DB paths break on long Gradle temp dirs:** if a temp dir is needed, use a short one such as `C:/t9`.
- **The tablet (SM-T510):** release builds with `adb -s <tablet serial> install -r` only, never uninstall. Write `<tablet serial>`, never a real serial. Ask the user before changing any tablet setting.
- **Public repo:** review findings by number only (P1, N12…), no security detail.
- Roborazzi re-records rewrite unrelated baselines. Restore every one a task didn't mean to change, file by file, then run `verifyRoborazziDebug`. This plan changes no screen, so no baseline should move.

## Rulings made while planning (the spec is the authority)

1. **Backoff counts from the second failure.** After failure n in a row the wait is `every × 2^(n−1)`, capped: playback 2, 4, 8, 16, 32, 60 s; layout 30, 60, 120, 240, 300 s; Tasks 60, 120, 240, 300 s. A reset (Music tab, Shopping ask) sets the waits back to `every` but keeps the run for logging; waking starts a new run.
2. **The quieter's layout read gets 250 ms** (`DOORBELL_LAYOUT_MS`), half of `QUIET_CAP_MS`, so `playingNow()` and the volume commands keep the other half. Slow or failed, the quieter uses the last layout. Its timing line is `Log.w`, so the release build on the tablet shows it (§8.2).
3. **The layout's age is `MusicChecks`'.** It records the time of each layout read it makes. `MusicSystem` gains nothing.
4. **Hue's silence needs a heartbeat:** `LightsSystem.stream()` now emits on opening and again for every line the bridge sends (events and keep-alive lines alike). `HueClient.events` gets a `heard` callback per line.
5. **The bridge's quiet-spell check runs on the emulator's debug build** (Task 7 Step 1), as the tablet runs release builds, whose `HueCapture` logs nothing, and today's capture skips non-`data:` lines. The tablet check (Task 15) adds an hour's count of silent reopenings.
6. **Bins: free attempts** are `OfflineException` and an `UnreachableException` whose cause is a `ConnectException`, `NoRouteToHostException` or `UnknownHostException`. A 429 or 5xx ("asked to try later"), a socket timeout, the engine's cancellation and anything unexpected still count.
7. **Quiet logging covers the three backed-off loops and the scan/search lines** (`SonosDiscovery`, `HueDiscovery`). `LightsFollow`'s and `HomeAssistant`'s stream-drop lines stay as they are (at most one a minute, and now only while lit). The providers' own per-call lines (`ShoppingSync`, `TasksApi`) are unchanged; the backoff cuts their rate.
8. **A camera failure run** counts every failure in a row. It ends after 10 clean minutes (spec) and also when the camera stops because it isn't wanted (night, Settings, Use other apps), so the next opening starts again at 10 s. Errors not named in §4.2 (a `SecurityException`, a `CameraAccessException`) keep the 5-minute wait.
9. **The gated loops log `Log.d(TAG, "Polling on")` and `"Polling off"`** as their gate opens and closes, for the emulator walk-through. Release strips them.

## Review Focus

1. **A quick dark-and-wake** (a doorbell card waking the dark screen, then the 1-minute night wait): one loop per gate, never two overlapping (Task 3: `aQuickSleepAndWakeRunsOneLoop`).
2. **A ring while the screen is dark and the speakers don't answer:** the quieter still lowers what it can from the last layout, and the chime isn't held past the cap (Task 4: `aFailedLayoutReadStillLowersFromTheLastLayout`, `aSlowLayoutReadFallsBackAtTheBudget`).
3. **Camera wake-up switched off and on, or night falling, during a failure run:** the next opening starts again at 10 s (Task 10: `aStopEndsTheRunOfFailures`).
4. **A silent Hue stream while the screen is dark:** silence counts only while lit, so waking doesn't reopen a stream at once for a silence it never had (Task 7: `silenceCountsOnlyWhileLit`).
5. **An offline night, then a council site that answers 503:** the free attempts leave all four of the day's tries for the site (Task 12: `freeAttemptsLeaveTheDaysFourForTheSite`).

---

### Task 1: The screen lit signal

**Files:**
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/ScreenLit.kt`, `app/src/main/java/uk/co/siland/culvery/display/ScreenLitState.kt`.
- Modify: `app/src/main/java/uk/co/siland/culvery/display/di/DisplayModule.kt` (`@Binds abstract fun screenLit(impl: ScreenLitState): ScreenLit`).
- Test: `app/src/test/java/uk/co/siland/culvery/display/DisplayStateTest.kt` (the `Wall` rig gains a lit signal).

**Interfaces:**
- Produces:
  - `interface ScreenLit { val lit: StateFlow<Boolean> }` in `core/plugin`, its KDoc: true while Culvery is resumed in front and the screen is awake (19 §3.1); false on the dark screen, the night clock, Holiday's dark screen and whenever Culvery isn't in front. `AlertHost.inFront` keeps its meaning.
  - `@Singleton class ScreenLitState internal constructor(inFront: StateFlow<Boolean>, screen: StateFlow<Screen>, scope: CoroutineScope) : ScreenLit`, with `@Inject constructor(alerts: ShellAlerts, display: DisplayState, @ApplicationScope scope: CoroutineScope)`. `lit` is `combine(inFront, screen) { f, s -> f && s == Screen.AWAKE }.distinctUntilChanged()`, `stateIn` eagerly from the current values.

- [ ] **Step 1:** In `DisplayStateTest`, give `Wall` a `resumed = MutableStateFlow(inFront)` and `val lit = ScreenLitState(resumed, display.screen, test.backgroundScope)`. Write:
  - `litWhenAwakeInFront` (true at noon, in front)
  - `notLitOnTheDarkScreen` (after `asleep()`)
  - `notLitOnTheNightClock` (at 22:00)
  - `notLitOnHolidaysDarkScreen` (`away(true)`)
  - `notLitWhenNotResumed` (`resumed.value = false`, screen still `AWAKE`)
  - `litAgainOnAWake` (asleep, then `tap()`: true)
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*DisplayStateTest*'`. Expect FAIL (no `ScreenLit`).
- [ ] **Step 3:** Implement both files and the binding.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Add a screen lit signal: in front and awake`.

### Task 2: Failure runs

**Files:**
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/FlowRetry.kt`.
- Test: `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/FlowRetryTest.kt`.

**Interfaces:**
- Produces: `class FailureRun(private val onFirst: (Exception) -> Unit, private val onRecovered: (FailureRun) -> Unit)`, KDoc citing 19 §5. Not thread-safe unless noted; Task 8 wraps it in `synchronized`.
  - `val failures: Int` (since the last success, for the recovery line)
  - `fun failed(e: Exception)`: counts it; calls `onFirst(e)` on the first of a run only.
  - `fun succeeded()`: after a run, calls `onRecovered(this)` with `failures` still set, then clears the run. Nothing when there was no run.
  - `fun resetWaits()`: the next `waitMillis` is `every` again; `failures` is kept.
  - `fun waitMillis(everyMs: Long, capMs: Long): Long`: `everyMs` with no failure or one; after n in a row since the last reset, `min(everyMs shl (n − 1), capMs)`.

- [ ] **Step 1:** Write in `FlowRetryTest`:
  - `theWaitDoublesFromTheSecondFailureToTheCap`: `every = 2_000`, `cap = 60_000`; after 0..7 failures the waits are `2_000, 2_000, 4_000, 8_000, 16_000, 32_000, 60_000, 60_000`.
  - `onlyTheFirstFailureOfARunIsReported` (three `failed`, `onFirst` called once with the first exception)
  - `theRecoveryIsReportedOnceWithTheCount` (three `failed`, `succeeded()` twice: `onRecovered` once, seeing `failures == 3`)
  - `aSuccessWithNoRunReportsNothing`
  - `aResetKeepsTheRunButNotTheWait` (three failures, `resetWaits()`: wait `every`; another failure: `every` still, `onFirst` not called again; `succeeded()` reports 4)
- [ ] **Step 2:** Run `./gradlew :core:plugin:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Add failure runs: a doubling wait and one line each way`.

### Task 3: Music checks wait on lit and back off

**Files:**
- Modify: `capability/music/src/main/java/uk/co/siland/culvery/capability/music/MusicChecks.kt`; the four test call sites of `MusicChecks(` (`MusicChecksTest`, `MusicCapabilityTest`, `ui/MusicCardTest`, `ui/MusicTabTest`); `build-logic/convention/src/test/kotlin/LogHygieneTest.kt` (`run.failures` in `ALLOWED`, "how many checks failed in a row (19 §5)").
- Test: `capability/music/src/test/java/uk/co/siland/culvery/capability/music/MusicChecksTest.kt`.

**Interfaces:**
- Consumes: `ScreenLit` (Task 1), `FailureRun` (Task 2).
- Produces:
  - `MusicChecks internal constructor(system: StateFlow<MusicSystem?>, lit: StateFlow<Boolean>, clock: WallClock, scope: CoroutineScope)`, `@Inject constructor(speakers: Speakers, lit: ScreenLit, clock: WallClock, @ApplicationScope scope: CoroutineScope)`.
  - `const val PLAYBACK_BACKOFF_CAP_MS = 60_000L`, `const val LAYOUT_BACKOFF_CAP_MS = 300_000L`.
  - `suspend fun layoutIfOlderThan(s: MusicSystem, ageMs: Long): Boolean`: reads the layout when no read by `MusicChecks` succeeded within `ageMs`; true when it read. Throws what `checkLayout` throws. Every successful layout read the loop makes also records its time.
- Each `checking(s)` makes two `FailureRun`s, playback and layout. Lines: `Log.w(TAG, "A music playback check failed (${e::class.simpleName})")` and `Log.i(TAG, "Music playback checks work again after ${run.failures} failures")`; the layout pair says "layout read" and "Music layout reads". The playback loop waits `withTimeoutOrNull(playback.waitMillis(PLAYBACK_CHECK_MS, PLAYBACK_BACKOFF_CAP_MS)) { playbackAsked.receive() }`; the layout loop the same with its own run and `layoutAsked`.
- `watch(layoutNow = true)` resets both runs' waits, reads the layout at once, then checks playback at once.
- `Log.d(TAG, "Polling on")` / `"Polling off"` as the gate opens and closes (ruling 9).

- [ ] **Step 1:** In `MusicChecksTest`, rename `inFront` to `lit` and pass a `WallClock { currentTime }`. Rework `checksStopWhenTheLastScreenGoesOrCulveryLeavesTheFront` into `checksStopWhenTheLastScreenGoesOrTheScreenDarkens`. Add:
  - `wakingChecksTheLayoutThenPlaybackAtOnce` (lit false, `watch()`, nothing for `LAYOUT_CHECK_MS`; lit true: `checkLayout`, `checkPlayback` in order with no time passing)
  - `aQuickSleepAndWakeRunsOneLoop` (lit true, false, true with `runCurrent()` between; then after `PLAYBACK_CHECK_MS` exactly one more `checkPlayback`)
  - `aFailingPlaybackCheckBacksOffToSixtySeconds` (playback failing: `checkPlayback` at 0, 2, 6, 14, 30, 62, 122 s; none in between)
  - `aFailingLayoutReadBacksOffToFiveMinutes` (layout failing: `checkLayout` at 0, 30, 90, 210, 450, 750 s)
  - `theMusicTabResetsTheBackoff` (playback failing to the 16 s wait; `watch(layoutNow = true)`: `checkLayout` and `checkPlayback` at once; the next `checkPlayback` 2 s later)
  - `oneLineForARunAndOneForTheRecovery` (three failed playback checks, then a good one: `ShadowLog` for `MusicChecks` is exactly `WARN "A music playback check failed (MusicUnreachableException)"` and `INFO "Music playback checks work again after 3 failures"`)
  - `aRecentLayoutIsNotReadAgain` (a loop read at 0; `layoutIfOlderThan(s, LAYOUT_CHECK_MS)` at 29 999 ms is false with no call; at 30 000 ms it reads and is true)
  - `noLayoutReadYetIsRead` (lit false, no read ever: `layoutIfOlderThan` reads)
- [ ] **Step 2:** Run `./gradlew :capability:music:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. Update the other three call sites.
- [ ] **Step 4:** Run the same and `./gradlew -p build-logic :convention:test`. Expect PASS.
- [ ] **Step 5:** Commit: `Check the speakers only while the screen is lit, backing off when they fail`.

### Task 4: The doorbell's quieter reads a stale layout first

**Files:**
- Modify: `capability/music/src/main/java/uk/co/siland/culvery/capability/music/MusicQuieter.kt`; `LogHygieneTest.kt` (`mark.elapsedNow().inWholeMilliseconds` in `ALLOWED`, "how long the doorbell's layout read took (19 §3.2)").
- Test: `capability/music/src/test/java/uk/co/siland/culvery/capability/music/MusicQuieterTest.kt`.

**Interfaces:**
- Consumes: `MusicChecks.layoutIfOlderThan` (Task 3).
- Produces:
  - `MusicQuieter internal constructor(system: () -> MusicSystem?, share: () -> Int, fresh: suspend (MusicSystem) -> Boolean = { false })`, `@Inject constructor(speakers: Speakers, settings: MusicSettings, checks: MusicChecks)` with `fresh = { checks.layoutIfOlderThan(it, LAYOUT_CHECK_MS) }`.
  - `internal const val DOORBELL_LAYOUT_MS = 250L` (ruling 2).
  - `quiet()`, before `playingNow()`: `withTimeoutOrNull(DOORBELL_LAYOUT_MS)` around `fresh(s)`. A read logs `Log.w(TAG, "The doorbell's layout read took ${mark.elapsedNow().inWholeMilliseconds} ms")` (`TimeSource.Monotonic.markNow()` before it). A timeout or failure logs `Log.w(TAG, "The doorbell used the last layout")` and goes on. Nothing logs when the layout was recent.

- [ ] **Step 1:** Write:
  - `aStaleLayoutIsReadBeforeThePlayingGroups` (`fresh` records into `system.calls`; it comes before `playingNow`)
  - `aFailedLayoutReadStillLowersFromTheLastLayout` (`fresh` throws `MusicUnreachableException`: the same three `setRoomVolume` as `eachRoomOfEachPlayingGroupDropsToItsShare`; the "used the last layout" line)
  - `aSlowLayoutReadFallsBackAtTheBudget` (`fresh` is `delay(1_000)`: the first `setRoomVolume` at virtual 250 ms)
  - `aRecentLayoutLogsNothing` (`fresh` returns false: no `MusicQuieter` line)
  - `quietWorksWithTheChecksStopped` (a real `MusicChecks` with lit false and no watcher, the quieter built over its `layoutIfOlderThan`: `checkLayout` once, then the three sets)
- [ ] **Step 2:** Run `./gradlew :capability:music:testDebugUnitTest --tests '*MusicQuieterTest*'`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `./gradlew :capability:music:testDebugUnitTest` and `./gradlew -p build-logic :convention:test`. Expect PASS.
- [ ] **Step 5:** Commit: `Read the speakers' layout before lowering them for the doorbell when it's old`.

### Task 5: Shopping checks wait on lit and back off

**Files:**
- Modify: `capability/shopping/src/main/java/uk/co/siland/culvery/capability/shopping/ShoppingChecks.kt`.
- Test: `capability/shopping/src/test/java/uk/co/siland/culvery/capability/shopping/ShoppingChecksTest.kt`.

**Interfaces:**
- Consumes: `ScreenLit`, `FailureRun`.
- Produces: `ShoppingChecks internal constructor(connected: Flow<Boolean>, lit: StateFlow<Boolean>, check: suspend () -> Unit, scope: CoroutineScope)`, `@Inject constructor(store: ShoppingStore, sync: ShoppingSync, lit: ScreenLit, @ApplicationScope scope: CoroutineScope)`. `const val SHOPPING_BACKOFF_CAP_MS = 300_000L`. The wait is `run.waitMillis(SHOPPING_CHECK_MS, SHOPPING_BACKOFF_CAP_MS)`; a request on `asked` (from `askNow()` or `watch(checkNow = true)`) resets it. Lines: `"A shopping check failed (${e::class.simpleName})"` (warn, first only) and `"Shopping checks work again after ${run.failures} failures"` (info). `Polling on`/`off` debug lines.

- [ ] **Step 1:** Rename `inFront` to `lit`. Add:
  - `nothingChecksWhileTheScreenIsDarkAndWakingChecksAtOnce`
  - `failuresDoubleTheWaitUpToFiveMinutes` (always failing: checks at 0, 60, 180, 420, 720, 1 020 s)
  - `anAskResetsTheWait` (failing to the 240 s wait; `askNow()` at 200 s checks at 200 s; still failing, the next at 260 s)
  - `oneLineForARunAndOneForTheRecovery` (as Task 3's, `IllegalStateException`, "Shopping checks work again after 3 failures")
- [ ] **Step 2:** Run `./gradlew :capability:shopping:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Check the shopping list only while the screen is lit, backing off when it fails`.

### Task 6: Home Assistant's socket waits on lit

**Files:**
- Modify: `core/homeassistant/src/main/java/uk/co/siland/culvery/core/homeassistant/HomeAssistant.kt`, `core/homeassistant/src/testFixtures/java/uk/co/siland/culvery/core/homeassistant/test/TestHomeAssistant.kt` (parameter `lit`).
- Test: `core/homeassistant/src/test/java/uk/co/siland/culvery/core/homeassistant/HomeAssistantTest.kt`.

**Interfaces:**
- Consumes: `ScreenLit`.
- Produces: `HomeAssistant(api, saved, lit: StateFlow<Boolean>, clock, scope)`, `@Inject` taking `lit: ScreenLit` in place of `host: AlertHost`. The socket follows only while lit; `read`, `states`, `call`, `awaitEntity` and `check` are untouched. `Polling on`/`off` debug lines. The class KDoc says "while lit" where it said "in front".

- [ ] **Step 1:** Rename `inFront` to `lit` in the tests and the fixture. Add:
  - `theSocketClosesWhileTheScreenIsDark` (watching, lit false: the follow flow is cancelled, `liveSince` null)
  - `wakingReopensTheSocketWithItsFullRead` (lit true again: a new follow, and its first event fills `entities`)
  - `aCommandWorksWhileTheScreenIsDark` (lit false: `call(...)` reaches the fake API)
- [ ] **Step 2:** Run `./gradlew :core:homeassistant:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. Run `./gradlew :provider:plugs-homeassistant:testDebugUnitTest :provider:hoover-homeassistant:testDebugUnitTest :provider:heating-homeassistant:testDebugUnitTest` too, as they use the fixture.
- [ ] **Step 4:** Expect PASS.
- [ ] **Step 5:** Commit: `Close the Home Assistant socket while the screen is dark`.

### Task 7: Hue every 5 minutes, waiting on lit, and a silent stream reopened

**Files:**
- Modify: `capability/lights/src/main/java/uk/co/siland/culvery/capability/lights/LightsFollow.kt`, `LightsContract.kt` (`stream()`'s KDoc); `provider/lights-hue/src/main/java/uk/co/siland/culvery/provider/lights_hue/HueClient.kt` (`events`), `HueJson.kt` (`sse` becomes per line), `HueSystem.kt` (`stream`); `app/src/debug/.../sample/SampleHouse.kt` (its `stream()` emits a beat each minute).
- Test: `capability/lights/src/test/.../LightsFollowTest.kt`, `TestLights.kt` (each stream is a `Channel<Unit>`; `fun heard(stream: Int = streams.lastIndex)`), `provider/lights-hue/src/test/.../HueClientTest.kt`, `HueJsonTest.kt`, `HueSystemTest.kt`.

**Interfaces:**
- Consumes: `ScreenLit`.
- Produces:
  - `const val READ_ALL_MS = 300_000L`; `const val STREAM_SILENCE_MS = 600_000L` (or the capture's value, Step 1).
  - `LightsFollow internal constructor(system, lit: StateFlow<Boolean>, scope)`, `@Inject` with `lit: ScreenLit`.
  - `LightsSystem.stream()`: emits once the stream is open and again for each line the bridge sends, events and keep-alive lines alike.
  - `HueClient.events(call, opened: suspend () -> Unit = {}, heard: suspend () -> Unit = {})`: `heard()` after each line read. `HueJson.sseData(line: String): String?` replaces `sse(lines)`: a `data:` line's payload, else null. Non-`data:` lines go to `capture("line", line)`.
  - `HueSystem.stream()` sends `Unit` from both `opened` and `heard`.
  - In `following`, a silence of `STREAM_SILENCE_MS` between the stream's emissions fails it with `private class StreamSilent : Exception("The lights stream went quiet")`, so `retryWithBackoff` reopens it with a full read. A stream that ends must still reopen at once, not after the silence.

- [ ] **Step 1 (the capture check, before any code):** Install the debug build on `emulator-5554` (`adb -s emulator-5554 install -r`; if it refuses, stop and ask). Ask the user whether the emulator may be paired with the bridge (a press of its link button). If yes: add the `capture("line", line)` call locally first, `adb -s emulator-5554 shell setprop log.tag.HueCapture DEBUG`, leave the Lights tab open for 30 minutes with nobody changing a light, and read the gaps between `HueCapture` lines. If the bridge sends nothing for long spells, set `STREAM_SILENCE_MS` to twice the longest gap seen and write the value and the gap here, under this step. If the user says no, keep 10 minutes and rely on Task 15's count. Set the property back with `… HueCapture INFO`.
- [ ] **Step 2:** Write:
  - `LightsFollowTest`: rename `inFront` to `lit`; `aFullReadEverySixtySeconds` becomes `aFullReadEveryFiveMinutes` (299 999 ms: 1 read; 300 000: 2). Add `aSilentStreamIsReopened` (no `heard` for `STREAM_SILENCE_MS - 1`: still open; at `STREAM_SILENCE_MS`: `stream closed`, then after `retryDelayMillis(0)`: `readAll`, `stream`), `aHeardStreamStaysOpen` (`heard()` at 9 min; still open at 18 min; closed at 19 min), `silenceCountsOnlyWhileLit` (lit false for 30 min, then true: `readAll`, `stream`, and no close before another `STREAM_SILENCE_MS`), `theStreamClosesWhileTheScreenIsDark`.
  - `HueClientTest`: `everyLineIsHeard` (the fake bridge's stream sends `: hi`, a blank line and a `data:` line: `heard` three times, one events list).
  - `HueJsonTest`: the `sse` cases moved to `sseData` (a `data:` line with and without the space, a comment, an `id:` line, a blank payload).
  - `HueSystemTest`: `theStreamEmitsForEachLine`.
- [ ] **Step 3:** Run `./gradlew :capability:lights:testDebugUnitTest :provider:lights-hue:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 4:** Implement. `Polling on`/`off` debug lines in `LightsFollow`.
- [ ] **Step 5:** Run the same and `./gradlew :app:testDebugUnitTest`. Expect PASS.
- [ ] **Step 6:** Commit: `Read Hue every five minutes while lit, and reopen a stream that has gone quiet`.

### Task 8: Quiet scan and search lines

**Files:**
- Modify: `provider/music-sonos/src/main/java/uk/co/siland/culvery/provider/music_sonos/SonosDiscovery.kt`, `provider/lights-hue/src/main/java/uk/co/siland/culvery/provider/lights_hue/HueDiscovery.kt`.
- Test: `provider/music-sonos/src/test/.../SonosDiscoveryTest.kt` (create if there is none), `provider/lights-hue/src/test/.../HueDiscoveryTest.kt`.

**Interfaces:**
- Consumes: `FailureRun`.
- Produces: `RESCAN_GAP_MS` and `SEARCH_GAP_MS` unchanged. `SonosDiscovery` holds one `FailureRun`: `"The speaker scan stopped (${e::class.simpleName})"` on the first failed scan of a run, `Log.i(TAG, "The speaker scan works again after ${run.failures} failures")` on the next scan that doesn't throw. `HueDiscovery` holds one for "The bridge search didn't start" and one for "A bridge didn't resolve", each under `synchronized`, as NSD calls back on its own threads; recovery is a search that starts, and a bridge that resolves.

- [ ] **Step 1:** Write `aFailingScanLogsOnceUntilItWorks` (three failing scans, one good: one warning, one info) and the two Hue equivalents.
- [ ] **Step 2:** Run `./gradlew :provider:music-sonos:testDebugUnitTest :provider:lights-hue:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same and `./gradlew -p build-logic :convention:test`. Expect PASS.
- [ ] **Step 5:** Commit: `Log a failing speaker scan or bridge search once, and its recovery`.

### Task 9: The camera's cheaper request and its line

**Files:**
- Modify: `app/src/main/java/uk/co/siland/culvery/display/camera/Camera2Frames.kt`, `Presence.kt` (`TAG` becomes `internal`); `LogHygieneTest.kt` (`choice.width`, `choice.height`, `choice.fps.first`, `choice.fps.last`, `choice.offNames` in `ALLOWED`, "the camera's size, rate range and the modes turned off (19 §4.1)"; `Camera2Frames.kt` and `Presence.kt` in `theScanReachesTheShippedCode`; a `theCameraLinesPass` case).
- Test: `app/src/test/java/uk/co/siland/culvery/display/camera/Camera2FramesTest.kt`.

**Interfaces:**
- Produces:
  - `internal enum class QuietMode(val available: CameraCharacteristics.Key<IntArray>, val key: CaptureRequest.Key<Int>, val off: Int)`: `NOISE_REDUCTION` (`NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES`, `NOISE_REDUCTION_MODE`, `NOISE_REDUCTION_MODE_OFF`), `EDGE` (`EDGE_AVAILABLE_EDGE_MODES`, `EDGE_MODE`, `EDGE_MODE_OFF`), `AUTOFOCUS` (`CONTROL_AF_AVAILABLE_MODES`, `CONTROL_AF_MODE`, `CONTROL_AF_MODE_OFF`).
  - `internal fun quietModes(available: (QuietMode) -> IntArray?): Set<QuietMode>`: the modes whose `off` is listed.
  - `internal data class CameraChoice(val width: Int, val height: Int, val fps: IntRange, val off: Set<QuietMode>)` with `val offNames: String` (names in enum order joined by ", ", or `none`).
  - `configure` builds the choice, sets each `off` mode on the repeating request, and logs `Log.w(TAG, "The camera opened at ${choice.width}x${choice.height}, ${choice.fps.first}-${choice.fps.last} fps, off: ${choice.offNames}")`.

- [ ] **Step 1:** Write `onlyListedOffModesAreTurnedOff` (`NOISE_REDUCTION → [0, 1, 2]`, `EDGE → [1, 2]`, `AUTOFOCUS → null`: `{NOISE_REDUCTION}`), `allThreeOffWhenListed`, `theOffNamesAreModeNames` (`"NOISE_REDUCTION, EDGE"`, and `"none"`), and in `LogHygieneTest` `theCameraLinesPass` (the opened line and Task 10's two stopped lines pass; `"${choice}"` doesn't).
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*Camera2FramesTest*'` and `./gradlew -p build-logic :convention:test`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Ask the camera for no noise reduction, edges or focus, and log what it opened at`.

### Task 10: The camera's failures, with their cause and a sooner retry

**Files:**
- Modify: `Camera2Frames.kt` (`onError` and `onConfigureFailed`), `Presence.kt`.
- Test: `Camera2FramesTest.kt`, `PresenceTest.kt`.

**Interfaces:**
- Produces:
  - `internal class CameraError(val code: Int) : Exception()` from `onError`; `internal class SessionFailed : Exception()` from `onConfigureFailed`; `CameraStopped` stays for `onDisconnected`.
  - `internal const val FIRST_CAMERA_RETRY_MS = 10_000L`, `internal const val CLEAN_RUN_MS = 600_000L`; `CAMERA_RETRY_MS = 300_000L` stays.
  - `internal fun cameraRetryMs(e: Exception, inARow: Int): Long?`: null for `NoFrontCamera`; `min(FIRST_CAMERA_RETRY_MS shl (inARow − 1), CAMERA_RETRY_MS)` for `CameraError` 4 or 5, `SessionFailed` and `CameraStopped`; `CAMERA_RETRY_MS` for everything else (ruling 8).
  - `Presence` counts failures in a row. It notes the time at each `open()`. A failure after `CLEAN_RUN_MS` or more since the last open starts a new run, and `stop()` ends the run. Lines: `Log.w(TAG, "The camera stopped (CameraError ${e.code})")` for a `CameraError`, else `"The camera stopped (${e::class.simpleName})"`. Closing still happens on the scope, never on the camera's thread.

- [ ] **Step 1:** Write:
  - `Camera2FramesTest`: `anErrorClosesTheCameraAndReportsOnce` now expects `CameraError` with code 4; add `anErrorCarriesItsCode` (`ERROR_CAMERA_SERVICE`: 5). `aDisconnectClosesTheCameraAndReportsOnce` keeps `CameraStopped`.
  - `PresenceTest`: `aDeviceErrorIsRetriedAfterTenSeconds` (9 999 ms: 1 open; 10 000: 2), `eachFailureInARowDoublesTheWaitUpToFiveMinutes` (failing straight after each reopen: waits 10, 20, 40, 80, 160, 300, 300 s), `aSessionFailureADisconnectAndAServiceErrorAreRetriedSoon` (each in its own rig: 10 s), `aCameraInUseDisabledOrAtItsLimitWaitsFiveMinutes` (codes 1, 2, 3), `tenCleanMinutesEndTheRun` (three failures, then open for 600 000 ms, then a failure: 10 s), `nineCleanMinutesKeepTheRun` (599 999 ms: 80 s), `aStopEndsTheRunOfFailures` (three failures, `want(false)`, `want(true)`, a failure: 10 s), `eachCauseIsLogged` (`"The camera stopped (CameraError 4)"`, `"(SessionFailed)"`, `"(CameraStopped)"`). Switch `aFailedCameraIsTriedAgainAfterFiveMinutes` and `aStopBetweenTheRetrysCheckAndItsOpenLeavesTheCameraClosed` to `CameraError(ERROR_CAMERA_IN_USE)`, keeping their timings.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*camera*'`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same and `./gradlew -p build-logic :convention:test`. Expect PASS.
- [ ] **Step 5:** Commit: `Log why the camera stopped, and retry device errors after ten seconds, doubling`.

### Task 11: Use other apps stops the camera

**Files:**
- Create: `app/src/main/java/uk/co/siland/culvery/shell/KioskExit.kt`.
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt` (`kioskExited` reads the singleton; `leaveKiosk()` and `returnedToFront()` write it), `app/src/main/java/uk/co/siland/culvery/display/DisplayState.kt`.
- Test: `app/src/test/.../shell/ShellViewModelTest.kt`, `app/src/test/.../display/DisplayStateTest.kt`, `DisplayWiringTest.kt` (constructor).

**Interfaces:**
- Produces:
  - `@Singleton class KioskExit @Inject constructor()` with `val exited: StateFlow<Boolean>`, `fun left()`, `fun returned()`. Its KDoc carries `kioskExited`'s present one (4c K2, ruling 6): process death forgets it.
  - `ShellViewModel` takes `kioskExit: KioskExit`; `val kioskExited: Boolean get() = kioskExit.exited.value`. `MainActivity` and `KioskLifecycle` don't change.
  - `DisplayState internal constructor(..., kioskExited: StateFlow<Boolean>, scope)`, `@Inject` taking `KioskExit`. `cameraWanted` is `combine(state, camera, kioskExited) { d, a, k -> !k && d.wantsCamera(a) }`. `wantsCamera`'s KDoc adds "and not left with Use other apps (19 §4.3)".

- [ ] **Step 1:** Write:
  - `DisplayStateTest`: `useOtherAppsStopsTheCamera` (in front, awake, by day: `wanted` false once `exited` is true) and `theCameraComesBackWhenTheWallPinsAgain` (`exited` false: `wanted` true).
  - `ShellViewModelTest`: the existing `kioskExited` tests read the same through a shared `KioskExit`; add `twoViewModelsShareTheExit` (one view model's `exitKiosk()` shows in a second's `kioskExited`).
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*DisplayState*' --tests '*ShellViewModel*' --tests '*DisplayWiring*' --tests '*KioskLifecycle*'`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Stop the camera while the wall is left with Use other apps`.

### Task 12: Bins after an outage

**Files:**
- Modify: `provider/calendar-midsussex/src/main/java/uk/co/siland/culvery/provider/calendar_midsussex/MidSussexBinsProvider.kt` (`latest`, `attempted`), `BinsReads.kt` (`failuresToday`'s KDoc: network-down attempts not included).
- Test: `provider/calendar-midsussex/src/test/.../MidSussexBinsProviderTest.kt`.

**Interfaces:**
- Produces: `private fun attempted(stored, key, now, today, householdZone, counts: Boolean): BinsRead`. `latest` keeps `catch (failed: BinsReadFailed)` (counts) and splits the general catch: a free attempt (ruling 6) records `unreached` and `lastAttemptMillis` but not a failure; anything else counts as today. The hour's gap holds for both, as `due` already reads `lastAttemptMillis`.

- [ ] **Step 1:** Write, with an interceptor as in `anOfflineAttemptKeepsSayingOffline` that counts the attempts it fails:
  - `anOfflineNightDoesNotUseUpTheDay` (offline, attempts at 00:05 and each hour to 07:05: eight attempts; online at 08:05: a good read)
  - `aConnectFailureIsFree` (`ConnectException`, five hourly attempts, then a good read)
  - `aFreeAttemptStillWaitsAnHour` (offline; at +30 min no attempt; at +60 min one)
  - `freeAttemptsLeaveTheDaysFourForTheSite` (three offline attempts, then 503: four council requests an hour apart, then none that day)
  - `retriesAnHourApartThreeTimes` and `anUnreachableCouncilKeepsSayingSoUntilAGoodRead` stand as they are (a page that can't be read and a 503 still count).
- [ ] **Step 2:** Run `./gradlew :provider:calendar-midsussex:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Don't count a bins read the network stopped toward the day's four`.

### Task 13: Simplify the branch

- [ ] **Step 1:** Run the `simplify` skill over the whole branch (`git diff main...plan-19-power`).
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest verifyRoborazziDebug` and `./gradlew -p build-logic :convention:test`. Expect PASS.
- [ ] **Step 3:** Commit, if anything changed: `Simplify Plan 19`.

### Task 14: Emulator walk-through

Walk-through rule: collect faults and put them to the user before fixing any.

- [ ] **Step 1:** `./gradlew :app:assembleDebug`, then `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk` (if it refuses, stop and ask; never uninstall). Connect the sample house: `adb -s emulator-5554 shell am broadcast -n uk.co.siland.culvery/.DebugSampleHouseReceiver --ez sample true`. Watch `adb -s emulator-5554 logcat -s MusicChecks LightsFollow ShoppingChecks HomeAssistant DisplayState`.
- [ ] **Step 2:** Tick each, one line of what was seen:
  1. Let the screen go dark (5 minutes, or a shorter wait in Settings › Display). Each loop logs `Polling off`, and nothing more from them while dark.
  2. Tap to wake: `Polling on` from each, and Home's music, lights and shopping cards and the hoover row fill within a moment. Repeat on the Music, Lights, Shopping and Plugs tabs.
  3. Night clock: move the clock to 22:00 with `adb -s emulator-5554 shell am broadcast -n uk.co.siland.culvery/.DebugClockReceiver -a uk.co.siland.culvery.debug.CLOCK --el minutes <n>`: `Polling off` from each. Tap: the cards fill; after a minute the clock returns and polling stops again. Clear with `--el minutes 0`.
  4. By day, the screen dark, sample music playing: `ring() { adb -s emulator-5554 shell "cmd notification post -t 'Front Door' culvery-fake-ring '$1'"; }`, then `ring 'Someone is at your Front Door'`. The screen wakes with the door card and the chime, and the music lowers. Got it: it comes back.
  5. `--ez down true` on the sample house: one `A music playback check failed` line and no more while it's down; `--ez down false`: one `work again` line.
  6. Use other apps (Admin PIN, the debug sample 1234): the camera mark goes. Back to Culvery and pinned: it returns if the emulator's camera is allowed.
- [ ] **Step 3:** Put any faults to the user. Fix only what they choose, each as its own commit.

### Task 15: On the tablet

- [ ] **Step 1:** `./gradlew :app:assembleRelease` (signed as before), then `adb -s <tablet serial> install -r app/build/outputs/apk/release/app-release.apk`. Never uninstall.
- [ ] **Step 2:** With the user, tick each (`adb -s <tablet serial> logcat -d -s Presence MusicQuieter LightsFollow`):
  1. The camera's line: `The camera opened at …x…, …-… fps, off: …`. Write the numbers and modes here.
  2. An hour of the dark screen by day with nobody there: no camera wakes (read from the battery history's `brightness=` changes, as release builds strip `DisplayState`'s info lines). Noise reduction off must not wake the wall.
  3. Walking up still wakes it in about 1 s.
  4. A ring while the screen is dark and music plays: the `The doorbell's layout read took … ms` line. If it's over 250 ms, or the lowering lands after the chime, put it to the user: the fallback is the last layout (spec §3.2).
  5. Any `The camera stopped (…)` lines carry a code, and the camera is back within seconds (`dumpsys media.camera` connect lines).
  6. An hour with the Lights tab open: count `The lights stream dropped (StreamSilent)` lines. More than one an hour means the bridge goes quiet longer than 10 minutes; put it to the user before changing `STREAM_SILENCE_MS`.
- [ ] **Step 3:** Put any faults to the user before fixing any.

### Task 16: Runs B and C again

As the review's Part 3 Stage 1: by day, both before about 15:30, on similar days, the tablet on battery, Culvery not restarted between them. Nobody touches the wall; walking past is fine.

- [ ] **Step 1 (B):** The user charges the tablet to about 80 % on its own charger, switches camera wake-up **off** in Settings › Display (their Admin PIN), leaves it on Home and unplugs, and gives the time. Check the camera stays closed more than 5 minutes first (`dumpsys media.camera`), so no retry is pending.
- [ ] **Step 2:** After 2 h the user plugs into the PC and gives the time. Read, all read-only, into `C:\Users\si\code\siland\culvery-notes\power-2026-11\`:
  - `adb -s <tablet serial> shell dumpsys batterystats --history > p19-runB-bshistory.txt`
  - `adb -s <tablet serial> shell dumpsys media.camera > p19-runB-camera.txt`
  - `adb -s <tablet serial> logcat -d > p19-runB-logcat.txt` (Culvery's lines only are kept in the notes)
  Then the tablet goes on its charger.
- [ ] **Step 3 (C):** At about 80 % again, the user switches camera wake-up **on**, unplugs and gives the time; 2 h; the same three reads as `p19-runC-*`.
- [ ] **Step 4:** Work out from the battery history (`charge=` in mAh at the unplug and the replug, `temp=` for the battery temperature, `brightness=` for each wake and each return to dark):
  - each run's drain in mA and %/h, and its temperature rise;
  - **B against November's B (4.5 %/h):** what P1 saved;
  - **C against B:** decision 9's drain test, C split into dark spans with the camera streaming (their rate less B's), awake spans after a wake (minutes × (awake rate − dark rate)), and any camera gaps from `p19-runC-camera.txt` taken out of the dark spans.
- [ ] **Step 5:** Write the results:
  - `docs/superpowers/plans/2026-09-23-plan1-followups.md`, item 1: the B and C numbers, the split, and the outcome. If C is still more than 2 %/h over B, camera wake-up stays on (ruling 9) and the camera resting between looks is the next idea (spec §9).
  - `docs/superpowers/specs/2026-10-19-culvery-8-sleep-and-wake-design.md`, a note under decision 9 (§2, item 9): measured again after Plan 19, the two drains, the difference, and the user's ruling that it stays on.
  - The private detail (times, the split, file names) in `culvery-notes/culvery-review-2026-11.md` Part 3.
- [ ] **Step 6:** Commit the two repo files: `Record runs B and C after Plan 19`.
