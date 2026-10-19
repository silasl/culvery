# Culvery — Plan 8: Sleep and wake Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–11 are subagent tasks; Task 12 is the controller on the `culvery-5` AVD with the user; Task 13 needs the user and the son's SM-T510.

> **Needs the user's OK: nothing.** No new library: the camera is read with the platform's Camera2 (`android.hardware.camera2`), as CameraX isn't in the version catalogue. `:app` gains `libs.androidx.datastore.preferences`, already in the catalogue at 1.2.1 and used by the capabilities (no version changes). No deprecated API is needed. Flagged and avoided: `CameraDevice.createCaptureSession(List<Surface>, …)` (deprecated in API 30; use `SessionConfiguration`), the no-argument `Handler()`, `Context.registerReceiver` without flags (use `ContextCompat.registerReceiver(…, RECEIVER_NOT_EXPORTED)`), `WindowManager.getDefaultDisplay`. One new permission, `android.permission.CAMERA` (spec §4.3). If the compiler shows a deprecation warning on a line you wrote, **stop and ask**.

**Goal:** At night the wall shows only a dim clock between set hours; in the day it goes dark after 5 minutes with nobody around and wakes when the front camera sees someone walk up, or on a tap; while awake, Culvery's window follows a daytime and a softer evening brightness by the sun; and the tablet's thermal state and battery temperature are logged, so the camera's power and heat can be measured on the SM-T510.

**Architecture:** Everything lives in `:app`, package `uk.co.siland.culvery.display`, beside the shell that already knows the PIN pad, the sheet, the alert and Settings. `DisplayState` is the one state machine (**Awake**, **Asleep**, **NightClock**) over the household's minute ticks, the touches from `ShellUserActivity`, `AlertHost.inFront`, the holds the shell reports, and presence; it is also the `NightHours` that `:capability:door` reads through a new `:core:plugin` interface, so a ring at night is only stored. `Presence` runs a `FrameSource` (Camera2, front camera, smallest YUV size, lowest frame rate) and `FrameDiff` (frame differencing on a 32 × 24 greyscale image once a second) only while `cameraAllowed(…)`; it emits timestamps and nothing else. `ScreenBrightness` turns the state, the night hours and the sun times (`Daylight`, as `ThemeSchedule` reads them) into a level for Culvery's window only. `HeatLog` is a `Startable` logging the thermal status and the battery as numbers. The dark screen and the night clock are full-screen layers in `ShellLayers` between the alert and the PIN pad; Settings › Display is a core page bound by `:app`.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2, `android.hardware.camera2` + `android.media.ImageReader`, `PowerManager.addThermalStatusListener` (API 29+), `Intent.ACTION_BATTERY_CHANGED`, `WindowManager.LayoutParams.screenBrightness`, `ActivityResultContracts.RequestPermission`; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-19-culvery-8-sleep-and-wake-design.md`. Read it with this plan.
**Previous plan (format, patterns):** `docs/superpowers/plans/2026-10-16-culvery-07-hue.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Tasks 12, 13 update it).

**Plan series:** 1 · 2a · 2b-1 · 2b-2 · 3a · 4a · 4b · 4c · 4d-1 · 4d-2 · 4e · 5 Ring · 6 Sonos · 7 Philips Hue · **8 Sleep and wake (this plan)**.

**Builds on:** branch `plan-8-sleep-wake`, cut from `main` at 9990fee, in the worktree `C:\Users\si\code\siland\culvery-sleep`. Work only there; never touch any other checkout. Never push. File references were read at 410366e.

| # | Task | Review |
|---|---|---|
| 1 | Core pieces: the camera icon, the daily window, the switch and time rows, `NightHours` | sonnet |
| 2 | `DisplaySettings` and `CameraAccess` | sonnet |
| 3 | `DisplayState`: the three states, the timers, the holds | **opus** (timers, every row of §4.1) |
| 4 | The doorbell at night | sonnet |
| 5 | `FrameDiff`, `Presence`, `CameraWake` and the privacy guard | **opus** (privacy, timers) |
| 6 | `Camera2Frames`: the front camera, small and slow | **opus** (a system service, threads) |
| 7 | `ScreenBrightness`: the levels, the fade, the window only | **opus** (timers) |
| 8 | `HeatLog` | sonnet |
| 9 | The dark screen, the night clock and the camera mark in the shell's layers | sonnet |
| 10 | Settings › Display | sonnet |
| 11 | Wiring: `MainActivity`, `DisplayModule`, the camera prompt | **opus** (the activity, pinning) |
| 12 | The emulator walk-through on `culvery-5` | controller + user |
| 13 | On the tablet: wake-up, the night clock in the dark, power and heat, decision 9; final review, merge, install | controller + user, **needs the SM-T510** |

**Task order and why:** the shared pieces first (1), then the settings everything reads (2), the state machine (3) and its one consumer outside the app (4). Presence is split into the logic tested with fake frames (5) and the Camera2 reader only the tablet really proves (6). Brightness (7) and heat (8) stand alone. The screens (9, 10) are tested against plain values; the wiring (11) joins them in `MainActivity`. The emulator (12) before the tablet (13).

## Rulings against the code

Where the spec leaves a choice open, or doesn't fit the code at 410366e. Each is the plan's call unless it cites the spec.

1. **Where it lives:** `:app`, package `uk.co.siland.culvery.display` (and `display.camera`, `display.ui`). `:app` already holds the shell's layers and `ShellUserActivity`; no new module. Door, a capability, may not depend on `:app`, so **`NightHours` goes in `:core:plugin`** (`interface NightHours { val isNight: StateFlow<Boolean> }`), bound by `:app` to `DisplayState`.
2. **The daily window moves to core:** Door's `isQuiet(now, from, until)` becomes `inDailyWindow(now, from, until)` in `:core:plugin` (`DailyWindow.kt`); `isQuiet` calls it. Night uses the same rule, so **equal night times mean no night** (as equal quiet hours mean never quiet). Night times are wall-clock times from `HouseholdClock.minutes` (the household's zone), re-checked on every tick and zone change (§5).
3. **The rows move to core:** Door's private `SwitchRow` and `StepperRow` become `HhSwitchRow` and `HhTimeStepperRow` in `:core:ui` (`Rows.kt`), with Door's `EARLIER`/`LATER` words and its 30-minute step as `TIME_STEP_MINUTES = 30L`; Door calls them; **Door's images don't change**.
4. **The holds** are one value the shell reports: `Holds(pinPad, sheet, alert, settings, wizard)`. Any hold keeps the screen Awake, day or night (§4.1). **A hold appearing while Asleep wakes the screen**: that is how the day doorbell wakes the wall (its card is the alert), with no ring signal of its own. **Night starting while a hold is up** leaves the screen Awake (night) until 1 minute after the hold ends (§4.5's door card rule, applied to every hold). When a hold ends, the idle wait starts again from that moment.
5. **The idle waits:** day `sleepAfterMinutes × 60 000 ms` from the last touch, movement, hold end, wake or settings change; night `NIGHT_AWAKE_MS = 60_000L` from the last touch. A changed sleep delay starts the wait again.
6. **Not in front** (`AlertHost.inFront` false): the state is Awake and no timer runs; the camera is off; the window's brightness is Android's (`BRIGHTNESS_OVERRIDE_NONE`). Back in front: Awake (or NightClock at night after the 1 minute), waits fresh.
7. **The waking tap** is swallowed by the dark screen's and the night clock's layer: it takes the finger down and the layer leaves the composition, so the rest of that gesture reaches nothing. The layer calls `DisplayState.tapped()` and **not** `ShellUserActivity.touched()`: waking is not a touch for the screens beneath.
8. **Brightness numbers** (provisional, tuned in Task 13): day 70 %, evening 25 %, sliders 5–100 % so an awake screen is never black; window level = percent / 100f. **Asleep and NightClock** use `BRIGHTNESS_OVERRIDE_OFF` (0f, "the lowest value when this window is in front", not off). **Evening** is sunset to sunrise by `Daylight`'s times; **with no sun times it is never evening** (§4.4: "the daytime level applies"), unlike the theme's 07:00/19:00. Awake at night is always the evening level (decision 8). **The switch at sunrise and sunset fades** linearly over `FADE_MS = 60_000L` in steps of `FADE_STEP_MS = 2_000L`; every other change (waking, sleeping, a slider let go) is at once.
9. **The camera's rule:** `cameraAllowed(screen, night, inFront, wakes) = inFront && !night && wakes && screen != NIGHT_CLOCK`, where `wakes` is the permission granted and the switch on (§4.3). One frame a second is looked at (`LOOK_EVERY_MS = 1_000L`); the rest are closed unread.
10. **`FrameDiff`** (provisional, tuned in Task 13): the frame is averaged down to 32 × 24 luma; the mean difference from the last frame is taken off every pixel first, so a lamp, a scene or the camera's exposure changing the whole frame isn't movement; a pixel changed by more than `PIXEL_DELTA = 24` counts; movement is at least `CHANGED_SHARE = 0.04f` of the pixels. The first frame after opening is never movement.
11. **A camera that fails** (`CameraAccessException`, `SecurityException`, `onError`, `onDisconnected`, no front camera) is closed, logged once as `"The camera stopped (${e::class.simpleName})"`, and tried again after `CAMERA_RETRY_MS = 300_000L` while still allowed (§5: "treated as no presence"). No front camera is never retried; Settings says so.
12. **The camera permission** is asked through `KioskLifecycle.openSystemScreen`, as Choose home app's dialog: unpinned while Android's dialog shows, pinned again on resume. Settings is already Admin-only, so no fresh PIN. **`CameraState`**: `NO_CAMERA` (no front camera), `NOT_ASKED`, `ASK_AGAIN` (denied, rationale shown), `REFUSED` (denied with no rationale after Culvery asked: Android won't ask again), `ALLOWED`. "Asked" is saved in `display_settings`. It is read again on every resume, as the permission can change in Android's settings. **Refused** offers Android's app details screen through `ShellNavigator.openSystemScreen` (a fresh Admin PIN, as every system screen).
13. **The switch's default:** `wakeByCamera: Boolean?` is saved only when the Admin flips it; null means `WAKE_BY_CAMERA_DEFAULT = true`. Decision 9's fallback is that constant set to `false` (Task 13), which also changes a tablet that never flipped it.
14. **The doorbell at night:** `DoorAlerts` treats `night.isNight.value` like not in front: the ring is stored, no card, no chime; unseen, it shows in the rail badge as today. A card already showing stays until Got it or its 2 minutes (§4.5). Door's quiet hours don't change; the overlap is said once, on Settings › Display's night line.
15. **Heat lines** are all `Log.w` (§4.7 asks warnings for a status above none; the plan logs the battery readings and a status back to none at warning too, because the tablet measurement reads a release build, whose info lines R8 removes). One line at start, one on each thermal status change, one every `HEAT_EVERY_MS = 900_000L`. **The line also carries the battery level in percent** (a number, for the discharge measurement). `LogHygiene.ALLOWED` gains `heat.status`, `heat.batteryTenths`, `heat.batteryPercent`.
16. **State changes** are logged at `Log.i` (stripped from release): `"Screen ${next.name}"` with the enum `Screen`; `screen.name` isn't in `ALLOWED` and doesn't need to be, as info lines aren't scanned.
17. **Settings › Display is page id `display`, order 440**: before Music (450), after Calendars (400). `SettingsPage`'s KDoc becomes "Home location 0, People 100, Calendars 400, Display 440, Music 450, Lights 470, Door 500, Tablet lock 900".
18. **Sleep delay choices:** 2, 5, 10, 15 minutes as chips (§4.6), default 5.
19. **The night clock:** `HH:mm` (Home's clock format), `NightClockTokens.size = 160.sp`, `NightClockTokens.ink = Color(0xFF5A5A5A)` on `Color.Black` in both themes, centred; nothing else (decision 2). Provisional for the design walk-through; the first lever if it lights the room is a dimmer ink (§4.2).
20. **The camera mark:** the `photo_camera` icon, 20 dp, `Culvery.colors.mute`, 12 dp from the top-right corner, on every screen while `Presence.running` (the dark screen included); not clickable; content description "The camera is on". Provisional (§4.3).
21. **Window brightness reaches the window** through `applyBrightness(window, level)` in `:app`, collected while started. Nothing writes Android's brightness setting and the manifest has no `WRITE_SETTINGS` (§4.4); a source test pins both.

## Global Constraints

**From the spec, verbatim:**

- §1 "**The tablet has no light sensor.** … Brightness therefore follows the time of day, not the room."
- §2.2 "only the time, large and very dim on black. Nothing else (no date, no doorbell dot)." §2.3 "set times in Settings, default 22:00 to 07:00." §2.4 "At night only a tap wakes the wall. The camera is off at night and never wakes it."
- §2.6 "a dark screen after **5 minutes** with nobody around (adjustable in Settings). Walking up, or a tap, wakes it."
- §2.7 "in the day it wakes the screen and shows the door card as usual. At night it is recorded in the Door tab only: the wall stays on the clock and the chime stays silent."
- §2.8 "a daytime level, and a softer level from sunset to sunrise … The softer level also applies when a tap wakes the night clock."
- §2.9 "camera wake-up **off** by default: the day screen still sleeps, and a tap wakes it." §2.10 "logs the tablet's thermal state changes and, now and then, the battery temperature (numbers only)."
- §4.1 the table; "A tap that wakes the screen only wakes it: it is not passed on"; "While Culvery is not in front … none of this runs: no camera, Android's own screen timeout and brightness apply."; "The PIN pad, an open sheet, the door card, Settings and the setup wizard keep the wall awake."
- §4.2 "`FLAG_KEEP_SCREEN_ON` kept." "Changing Android's own brightness (`WRITE_SETTINGS` …) stays out of this plan".
- §4.3 "Low resolution and the lowest frame rate the camera offers; … about **once a second** … No face detection, no recognition." "**Nothing leaves the reader:** no frame or image is stored, written to disk, logged, cached or sent. The reader emits only "someone moved" with a timestamp." "A small camera mark shows … whenever the camera is running". "The camera permission is asked once, from Settings › Display, by an Admin. Without it, camera wake-up stays off and Settings says why."
- §4.4 "It never changes Android's brightness setting". "Without the household's location (no sun times), the daytime level applies until it's set."
- §4.7 "one line on each thermal status change … and the battery temperature in tenths of a degree every 15 minutes and on each status change. Numbers only, no names."
- §4.6 "Admin only." "Sleep when nobody's around: after [minutes], default 5". "Wake when someone walks up: a switch, on by default once the camera permission is granted".
- §5 "The camera fails or is taken by another app | Treated as no presence; wakes by tap; one log line with the exception class". "Logs carry state changes and outcomes only, never image data."

**Copy** (the spec fixes the names; the sentences are the plan's, for the end-of-v1 design walk-through; curly apostrophes in code): page "Display"; sections "Night clock", "Brightness", "Sleep"; "From", "Until", "Earlier", "Later" (Door's); `nightClockLine(from, until)` = "A dim clock shows from 22:00 until 07:00. Only a tap wakes the screen, and rings wait in the Door tab."; sliders "Daytime" and "Evening, from sunset", spoken "Daytime brightness" / "Evening brightness" with "70 percent"; no sun times: "Until the home location is set, the daytime level applies all day."; "Sleep when nobody’s around", chips "2 minutes" … "15 minutes", spoken "Sleep after 5 minutes"; switch "Wake when someone walks up", line "The front camera looks for movement about once a second. Nothing is recorded or sent."; not asked: "Walking up can wake the screen once Culvery may use the camera." with the button "Allow the camera"; refused: "The camera isn’t allowed, so only a tap wakes the screen. Allow it in Android’s settings for Culvery." with "Open Android’s settings for Culvery"; its missing screen: "Culvery can’t open Android’s settings for Culvery."; no camera: "This tablet has no front camera, so a tap wakes the screen."; the layers' click label "Wake the screen"; the mark "The camera is on".

**From the house rules:**

- Package root `uk.co.siland.culvery`. `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only. **No new library; never change a version.**
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` from the worktree (or `.\gradlew.bat …`). Never plain `test`, `check` or `build`, except `./gradlew -p build-logic :convention:test` (Task 8 changes `LogHygieneTest`). A task runs its module's tests while working; every task ends with the full gate before its commit. **Never run a Gradle `install…` task**, and no subagent touches a device.
- **Tests that can fail:** every new test fails before its change, and its step says why; a "Fails if" line names the mutation it catches where it isn't plain. A test that passes before its change is labelled **pin**. Timer tests use `TestScope`'s scheduler and never sleep; each checks 1 ms before its boundary and at it. Minute ticks are a `MutableStateFlow<LocalDateTime>` the test sets.
- **Screenshots:** baselines in `app/src/test/screenshots/` (`core/ui`'s for core). Compare (`compareRoborazziDebug`) before re-recording, record (`recordRoborazziDebug`), open every new image, and run Plan 6's baseline check (the step's expected names in `$TMP/expected-baselines.txt`; `git status --porcelain -- "*.png"` compared both ways; both lists empty).
- **Copy** names what the user does; an action keeps its name through its flow; errors say what went wrong and how to fix it; curly quotes and apostrophes; "›", "…" as written. `CopyScanTest` flags straight quotes and the words master, kiosk, sync, read-only, assign, feed; a protocol word never shown goes in `core/ui/src/test/resources/copy-scan-allow.txt`. Sentences end with a full stop; labels don't.
- **Accessibility:** every new screen's test runs `compose.assertClickablesHaveRoleAndLabel()`.
- **Logs:** `LogHygieneTest` scans every module's `main` and `release`. Never a frame, a size read from a frame, or a camera id in a log.
- **Layout numbers are named**, in `DisplayDimens` and `NightClockTokens`.
- **Comments say what the code can't**, one line by default; spec references as `(8 §4.3)`. **No comment names this plan's task or ruling numbers.**
- **Robolectric and waiting:** `compose.waitUntil(5_000) { compose.waitForIdle(); condition() }`, never `runBlocking { flow.first { … } }` after a click.
- **No personal data in files:** no names or addresses in a test or a doc; from the tablet, only battery, temperature and timing figures, in the follow-ups file.
- **Write every file as UTF-8.**
- **Commit messages are a plain sentence in the repo's style and contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Never push. **Never** `git checkout -- .` or `git restore .`.

## Review Focus

The five failure modes the spec implies, most likely to bite the household, that the spec's own test list doesn't cover; each is pinned by a test in its owning task:

1. **A light changing the whole room in an empty room** (the evening Hue scene, a lamp, the sun going behind a cloud, the camera's own exposure). Expected: no wake; only something moving in part of the frame wakes it.
   - Task 5 `FrameDiffTest.aWholeFrameGettingBrighterIsNotMovement` and `FrameDiffTest.sensorNoiseIsNotMovement`.
2. **Culvery leaving the front while asleep with the camera running** (Use other apps, the permission dialog, a crash back to home). Expected: the camera closes, the mark goes, Android's brightness applies; coming back is Awake with fresh waits.
   - Task 3 `DisplayStateTest.leavingTheFrontWakesAndStopsTheWaits`; Task 5 `CameraWakeTest.theCameraClosesWhenCulveryLeavesTheFront`; Task 7 `ScreenBrightnessTest.notInFrontLeavesAndroidsBrightness`.
3. **The clocks going back, or night across midnight** (22:00–07:00 with 01:30 twice in late October). Expected: NightClock throughout, Awake at 07:00 once.
   - Task 3 `DisplayStateTest.nightAcrossMidnightAndTheClocksGoingBack`.
4. **The camera taken, failing or switched off in Android's settings while camera wake-up is on.** Expected: no crash, one log line, a tap still wakes, Settings shows why on the next resume, and the camera is tried again later only while it may run.
   - Task 5 `PresenceTest.aCameraErrorIsNoPresenceAndOneLine`, `PresenceTest.aFailedCameraIsTriedAgainAfterFiveMinutes`; Task 2 `CameraAccessTest.aPermissionTakenAwayInAndroidsSettingsIsSeenOnResume`.
5. **The night times changed in Settings, or set equal.** Expected: the screen follows at once when Settings closes (Settings holds it awake until then); equal times mean no night clock at all, and the camera then runs at any hour.
   - Task 3 `DisplayStateTest.changedNightTimesApplyAtOnce`, `DisplayStateTest.equalNightTimesMeanNoNight`.

The spec's own tests (§6) each have their task: the state machine's rows, the doorbell both ways, holds, not in front, a waking tap (3, 4, 9); presence only in the allowed states, frame differencing, the no-image-path guard (5, 6); brightness at sunrise and sunset, no location, the window only (7); heat lines and `LogHygieneTest` (8); the UI with its screenshots (9, 10).

---

## File Structure

```
core/plugin/.../DailyWindow.kt (create, Task 1)   NightHours.kt (create, Task 1)   Setup.kt (Task 1: the order KDoc)
core/ui/.../Rows.kt (create, Task 1)   Icons.kt, tools/fonts/icons.txt, core/ui/src/main/res/font/material_symbols_rounded.ttf (Task 1)
capability/door/.../QuietHours.kt, ui/DoorSettingsPage.kt, ui/DoorDimens.kt, DoorWords.kt (Task 1)   DoorAlerts.kt (Task 4)
build-logic/convention/src/test/kotlin/LogHygieneTest.kt (Task 8)
app/build.gradle.kts (Task 2: datastore)   app/src/main/AndroidManifest.xml (Task 6: the camera)
app/src/main/java/uk/co/siland/culvery/
  display/DisplaySettings.kt   display/CameraAccess.kt (Task 2)
  display/DisplayState.kt (Task 3)
  display/camera/FrameDiff.kt   display/camera/Presence.kt   display/CameraWake.kt (Task 5)
  display/camera/Camera2Frames.kt (Task 6)
  display/ScreenBrightness.kt (Task 7)   shell/ThemeSchedule.kt, shell/ShellViewModel.kt (Task 7: sunToday)
  display/HeatLog.kt (Task 8)
  display/ui/SleepLayers.kt   display/ui/DisplayDimens.kt   shell/ui/OverlayLayers.kt (Task 9)
  display/ui/DisplaySettingsPage.kt   display/DisplayWords.kt (Task 10)
  display/di/DisplayModule.kt (Task 3; Tasks 6, 8, 10 add to it)   MainActivity.kt (Task 11)
app/src/test/java/uk/co/siland/culvery/display/
  DisplaySettingsTest.kt   CameraAccessTest.kt (Task 2)   DisplayStateTest.kt (Task 3)
  camera/FrameDiffTest.kt   camera/PresenceTest.kt   CameraWakeTest.kt   camera/PresenceSourceTest.kt (Task 5)
  camera/Camera2FramesTest.kt (Task 6)   ScreenBrightnessTest.kt   BrightnessSourceTest.kt (Task 7)   HeatLogTest.kt (Task 8)
  ui/SleepLayersTest.kt   ui/SleepLayersScreenshotTest.kt (Task 9)   ui/DisplaySettingsPageTest.kt   ui/DisplaySettingsScreenshotTest.kt (Task 10)
  DisplayWiringTest.kt (Task 11)
docs/superpowers/plans/2026-09-23-plan1-followups.md (Tasks 12, 13)
```

`…` stands for the module's package directory.

---

### Task 1: Core pieces: the camera icon, the daily window, the switch and time rows, `NightHours` (rulings 1–3, 17, 20)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Icons.kt`, `tools/fonts/icons.txt`, `core/ui/src/main/res/font/material_symbols_rounded.ttf` (regenerated); maybe `IconFontTest.kt` (`NO_FILLED_FORM`)
- Create: `core/plugin/.../DailyWindow.kt`, `core/plugin/.../NightHours.kt`, `core/ui/.../Rows.kt`; tests `core/plugin/src/test/.../DailyWindowTest.kt`, `core/ui/src/test/.../RowsTest.kt`
- Modify: `capability/door/.../QuietHours.kt` (`isQuiet` calls `inDailyWindow`), `ui/DoorSettingsPage.kt` (its `SwitchRow`, `StepperRow` deleted; calls the core rows), `ui/DoorDimens.kt` (`QUIET_STEP_MINUTES` gone), `DoorWords.kt` (`EARLIER`, `LATER` gone); `core/plugin/.../Setup.kt` (ruling 17's KDoc)

**Interfaces:**
- Produces: `Icons.PHOTO_CAMERA = "photo_camera"`.
- Produces (`uk.co.siland.culvery.core.plugin`): `fun inDailyWindow(now: LocalTime, from: LocalTime, until: LocalTime): Boolean`; `interface NightHours { val isNight: StateFlow<Boolean> }` (KDoc: the night clock's hours are now, on the household's clock; 8 §4.5).
- Produces (`uk.co.siland.culvery.core.ui`): `const val EARLIER = "Earlier"`, `const val LATER = "Later"`, `const val TIME_STEP_MINUTES = 30L`; `@Composable fun HhSwitchRow(title: String, line: String?, checked: Boolean, tag: String, onChange: (Boolean) -> Unit)` (Door's `SwitchRow`); `@Composable fun HhTimeStepperRow(label: String, subject: String, time: LocalTime, tag: String, onTime: (LocalTime) -> Unit)` (Door's `StepperRow`; tags `${tag}_earlier`, `${tag}_later`; spoken "Earlier, [subject]").

- [ ] **Step 1: The failing tests**

Add the icon to `Icons` and `icons.txt` in alphabetical order. Move `QuietHoursTest`'s `isQuiet` cases into `DailyWindowTest.kt` (`aWindowWithinTheDay`, `aWindowAcrossMidnight`, `equalTimesAreNeverInside`, each checking a minute before and at both ends); Door's `QuietHoursTest` keeps `quietAt`'s cases. `RowsTest.kt` (Robolectric): `aSwitchRowIsOneSwitch` (`Role.Switch`, toggling calls `onChange(true)`), `theStepperStepsByHalfAnHour` (22:00 → `_earlier` gives 21:30, `_later` 22:30; spoken "Earlier, night clock start"), each with `assertClickablesHaveRoleAndLabel()`.
Run: `./gradlew :core:ui:testDebugUnitTest :core:plugin:testDebugUnitTest` — Expected: FAIL (`IconFontTest` names the icon; the rest don't compile).

- [ ] **Step 2: The subset**

`tools/fonts/README.md` steps 3–4: `git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$TMP/material-symbols-full.ttf"`, then `python tools/fonts/subset.py --source "$TMP/material-symbols-full.ttf"` (fontTools via `python -m pip install --user fonttools` if missing). If the script says the source lacks it, step 3 needs Google's current font: **a download — stop and ask the user**, naming the file, source and size. If `everyNamedIconKeepsItsFilledForm` names it, add it to `NO_FILLED_FORM`.

- [ ] **Step 3: Move the code**

`inDailyWindow` from `isQuiet`'s body; the rows from Door's, unchanged in look; Door's callers pass the same tags. `NightHours` with its KDoc only.

- [ ] **Step 4: Run them, then the gate**

Run: `./gradlew :core:ui:testDebugUnitTest :core:plugin:testDebugUnitTest :capability:door:testDebugUnitTest`, then the gate — Expected: PASS, **no image changed**.
```bash
git add core capability/door tools/fonts/icons.txt
git commit -m "Add the camera icon, and move the daily time window and the switch and time rows into core"
```

---

### Task 2: `DisplaySettings` and `CameraAccess` (§4.3, §4.6; rulings 8, 12, 13, 18; Review Focus 4)

**Review:** sonnet.

**Files:**
- Modify: `app/build.gradle.kts` (`implementation(libs.androidx.datastore.preferences)`)
- Create: `app/.../display/DisplaySettings.kt`, `display/CameraAccess.kt`; tests `display/DisplaySettingsTest.kt`, `display/CameraAccessTest.kt`

**Interfaces:**
- Produces (package `uk.co.siland.culvery.display`):
  - `data class DisplayPrefs(val nightFrom: LocalTime = LocalTime.of(22, 0), val nightUntil: LocalTime = LocalTime.of(7, 0), val dayPercent: Int = 70, val eveningPercent: Int = 25, val sleepAfterMinutes: Int = 5, val wakeByCamera: Boolean? = null, val cameraAsked: Boolean = false)`
  - `val SLEEP_CHOICES = listOf(2, 5, 10, 15)`; `val BRIGHTNESS_RANGE = 5..100`; `const val WAKE_BY_CAMERA_DEFAULT = true`
  - `class DisplaySettings(store: DataStore<Preferences>, scope: CoroutineScope)` (+ `@Inject` on file `display_settings`): `prefs: Flow<DisplayPrefs>`, `current: StateFlow<DisplayPrefs>`, `setNight(from: LocalTime, until: LocalTime)`, `setDayPercent(p: Int)`, `setEveningPercent(p: Int)`, `setSleepAfter(minutes: Int)`, `setWakeByCamera(on: Boolean)`, `setCameraAsked()`; `internal fun displaySettingsStore(scope, produceFile)` (Door's shape: a corrupt file is the defaults; writes throw `IOException`)
  - `enum class CameraState { NO_CAMERA, NOT_ASKED, ASK_AGAIN, REFUSED, ALLOWED }`
  - `fun cameraState(hasFrontCamera: Boolean, granted: Boolean, asked: Boolean, rationale: Boolean): CameraState`
  - `fun DisplayPrefs.wakesByCamera(state: CameraState): Boolean` = `state == ALLOWED && (wakeByCamera ?: WAKE_BY_CAMERA_DEFAULT)`
  - `@Singleton class CameraAccess internal constructor(hasFrontCamera: () -> Boolean, granted: () -> Boolean, settings: DisplaySettings, scope)` (+ `@Inject` from the application context: `PackageManager.FEATURE_CAMERA_FRONT`, `ContextCompat.checkSelfPermission(CAMERA)`): `state: StateFlow<CameraState>`, `fun refresh(rationale: Boolean)`, `fun ask()`, `val requests: Flow<Unit>` (a `Channel`, for the activity), `suspend fun answered(granted: Boolean, rationale: Boolean)` (saves `cameraAsked`, then refreshes)

- [ ] **Step 1: Write the failing tests**

`DisplaySettingsTest.kt` (Robolectric, a `TemporaryFolder` store): `theDefaults` (22:00, 07:00, 70, 25, 5, null, false); `everySettingIsKept` (each setter round-trips through a new store on the same file); `aFileThatCantBeReadIsTheDefaults`; `percentsAreKeptInRange` (`setDayPercent(0)` → 5, `setEveningPercent(120)` → 100); `onlyTheOfferedDelaysAreKept` (`setSleepAfter(7)` throws `IllegalArgumentException`).
`CameraAccessTest.kt` (plain JVM for `cameraState`, `runTest` for the class):
```kotlin
/** 8 §4.3: Android won't ask again after a denial with no rationale. Fails if a denial without a rationale reads as NOT_ASKED. */
@Test
fun aDenialWithoutARationaleAfterAskingIsRefused() {
    assertThat(cameraState(hasFrontCamera = true, granted = false, asked = false, rationale = false)).isEqualTo(CameraState.NOT_ASKED)
    assertThat(cameraState(true, granted = false, asked = true, rationale = true)).isEqualTo(CameraState.ASK_AGAIN)
    assertThat(cameraState(true, granted = false, asked = true, rationale = false)).isEqualTo(CameraState.REFUSED)
    assertThat(cameraState(true, granted = true, asked = true, rationale = false)).isEqualTo(CameraState.ALLOWED)
    assertThat(cameraState(false, granted = true, asked = true, rationale = false)).isEqualTo(CameraState.NO_CAMERA)
}
```
`theSwitchIsOnByDefaultOnlyOnceAllowed` (`DisplayPrefs().wakesByCamera(ALLOWED)` true, `(NOT_ASKED)` false, `DisplayPrefs(wakeByCamera = false).wakesByCamera(ALLOWED)` false); `askSendsOneRequest` (Turbine on `requests`); `answeredSavesThatCulveryAsked`; `aPermissionTakenAwayInAndroidsSettingsIsSeenOnResume` (Review Focus 4: `granted` true → ALLOWED; the fake turns false; `refresh(rationale = false)` → REFUSED, as `cameraAsked` is saved).
Run: `./gradlew :app:testDebugUnitTest --tests '*DisplaySettingsTest*' --tests '*CameraAccessTest*'` — Expected: FAIL to compile.

- [ ] **Step 2: Implement** `DoorSettings`' shape (minutes of the day as int keys, as quiet hours); `CameraAccess.state` a `MutableStateFlow` set by `refresh` from the three readers and `settings.current.value.cameraAsked`.

- [ ] **Step 3: Run them, the gate, commit**
```bash
git add app
git commit -m "Keep the display's settings, and tell whether Culvery may use the front camera"
```

---

### Task 3: `DisplayState`: the three states, the timers, the holds (§4.1, §5; rulings 2, 4–7, 16; Review Focus 2, 3, 5)

**Review:** opus.

**Files:**
- Create: `app/.../display/DisplayState.kt`, `display/di/DisplayModule.kt` (`@Binds nightHours(DisplayState): NightHours`, so Task 4's `DoorAlerts` can be injected; later tasks add their bindings); test `display/DisplayStateTest.kt`

**Interfaces:**
- Consumes: `DisplaySettings.current` (Task 2); `inDailyWindow`, `NightHours` (Task 1); `HouseholdClock.minutes`, `UserActivity.touches`, `AlertHost.inFront`, `ApplicationScope`.
- Produces:
  - `enum class Screen { AWAKE, ASLEEP, NIGHT_CLOCK }`
  - `data class Holds(val pinPad: Boolean = false, val sheet: Boolean = false, val alert: Boolean = false, val settings: Boolean = false, val wizard: Boolean = false) { val any: Boolean }`
  - `const val NIGHT_AWAKE_MS = 60_000L`
  - `@Singleton class DisplayState internal constructor(prefs: StateFlow<DisplayPrefs>, minutes: Flow<LocalDateTime>, touches: Flow<Long>, inFront: StateFlow<Boolean>, scope: CoroutineScope) : NightHours` (+ `@Inject` from `DisplaySettings`, `HouseholdClock`, `UserActivity`, `AlertHost`, the application scope): `screen: StateFlow<Screen>`, `override val isNight: StateFlow<Boolean>`, `fun tapped()`, `fun moved()`, `fun holds(holds: Holds)`

- [ ] **Step 1: Write the failing tests** (`runTest`, the state on `backgroundScope`, `minutes` a `MutableStateFlow` starting 2026-10-19T12:00, `inFront` true; every timer test checks 1 ms before and at its boundary)
- `startsAwake`.
- `fiveMinutesWithNobodyAroundSleeps` (§4.1 row 1): AWAKE at 299 999 ms, ASLEEP at 300 000.
- `aTouchOrMovementRestartsTheWait`: a touch at 200 s → AWAKE at 499 999, ASLEEP at 500 000; the same with `moved()`.
- `theDelayFollowsSettings` (2 minutes: ASLEEP at 120 000); `aChangedDelayStartsTheWaitAgain`.
- `movementOrATapWakesTheDarkScreen` (row 2), each case separately.
- `aDoorCardWakesTheDarkScreen` (row 3, ruling 4): ASLEEP, `holds(Holds(alert = true))` → AWAKE; 10 minutes on, still AWAKE; the alert gone → ASLEEP 5 minutes later.
- `eachHoldKeepsItAwake` (§4.1: the PIN pad, a sheet, the door card, Settings, the wizard; one case each): 30 minutes → AWAKE; released → ASLEEP at release + 300 000.
- `nightStartsAtTwentyTwo` (row 4): ticks 21:59 → AWAKE; 22:00 → NIGHT_CLOCK, from AWAKE and from ASLEEP; `isNight` true at 22:00, false at 21:59.
- `aTapAtNightWakesForOneMinute` (rows 5, 6): `tapped()` → AWAKE; touches restart; NIGHT_CLOCK at 60 000 after the last touch, not at 59 999.
- `movementNeverWakesTheNightClock` (decision 4).
- `aHoldAtNightKeepsItAwakeThenTheClockFollowsAMinuteLater` (row 6; ruling 4; a sheet at night).
- `aDoorCardShowingAsNightStartsStaysThenTheClockFollows` (§4.5): `alert = true` at 21:59; 22:00 → AWAKE; the alert gone → NIGHT_CLOCK 60 000 later.
- `nightEndingWakesTheWall` (row 8): NIGHT_CLOCK, tick 07:00 → AWAKE, then ASLEEP 300 000 later; also from AWAKE at night.
- `nightAcrossMidnightAndTheClocksGoingBack` (Review Focus 3): ticks 23:59, 00:00, 01:30, 01:30 again, 06:59 → NIGHT_CLOCK each; 07:00 → AWAKE.
- `notInFrontNothingRuns` (§4.1): `inFront` false → AWAKE; 30 minutes and a 22:00 tick → still AWAKE; `tapped()` and `moved()` change nothing.
- `leavingTheFrontWakesAndStopsTheWaits` (Review Focus 2): ASLEEP, `inFront` false → AWAKE; back in front → AWAKE, ASLEEP 300 000 after the return; at night, back in front → AWAKE then NIGHT_CLOCK 60 000 later.
- `changedNightTimesApplyAtOnce` (Review Focus 5): 21:00 AWAKE; `prefs.value = prefs.value.copy(nightFrom = 20:00)` → NIGHT_CLOCK at once (no tick needed); the same with Settings held → AWAKE until 60 000 after release.
- `equalNightTimesMeanNoNight` (ruling 2): prefs with both times 22:00; a 23:00 tick → AWAKE, `isNight` false.
Run: `./gradlew :app:testDebugUnitTest --tests '*DisplayStateTest*'` — Expected: FAIL to compile.

- [ ] **Step 2: Implement**

One coroutine on `scope` collecting the merged inputs (`combine` of prefs, the night flag from minutes and prefs, `inFront`, the holds) and a `collectLatest` per change of "what the wait is": the wait is `delay(...)` restarted by each touch, movement or tap through a `MutableSharedFlow<Unit>` of activity. `isNight` = `inDailyWindow(minute.toLocalTime(), nightFrom, nightUntil)`, recomputed when prefs or the minute change. Log each change at `Log.i(TAG, "Screen ${next.name}")`.

- [ ] **Step 3: Run them, the gate, commit**
```bash
git add app
git commit -m "Decide when the wall is awake, asleep or showing the night clock"
```

---

### Task 4: The doorbell at night (§2.7, §4.5; ruling 14)

**Review:** sonnet.

**Files:**
- Modify: `capability/door/.../DoorAlerts.kt`; test `capability/door/src/test/.../DoorAlertsTest.kt`

**Interfaces:**
- Consumes: `NightHours` (Task 1).
- Produces: `DoorAlerts` internal constructor gains a last parameter `night: () -> Boolean = { false }`; the `@Inject` constructor takes `night: NightHours` and passes `{ night.isNight.value }`. (`:app` binds `NightHours` in Task 3.)

- [ ] **Step 1: Write the failing tests** (in the existing class, its `alerts()` helper taking `night: () -> Boolean = { false }`)
- `aRingAtNightIsStoredWithoutACardOrAChime` (§2.7): night true, in front → no `host.show`, no chime, `seen` empty (so the badge counts it). Fails today: the card shows.
- `aCardShowingWhenNightStartsStaysUntilItCloses` (§4.5, **pin**): a ring in the day; night turns true; the card stays; at `CARD_MS` it closes as usual.
- `aRingAfterNightEndsAlertsAsUsual` (**pin**): night false again → card and chime.
Run: `./gradlew :capability:door:testDebugUnitTest --tests '*DoorAlertsTest*'` — Expected: FAIL (the first).

- [ ] **Step 2: Implement** `if (!host.inFront.value || night()) return@withLock`, and the class KDoc's "anything while Culvery isn't in front" becomes "… isn't in front, or at night (8 §2.7)".

- [ ] **Step 3: Run them, the gate, commit**
```bash
git add capability/door
git commit -m "Keep a ring at night in the Door tab, with no card and no chime"
```

---

### Task 5: `FrameDiff`, `Presence`, `CameraWake` and the privacy guard (§4.3, §5; rulings 9–11; Review Focus 1, 2, 4)

**Review:** opus.

**Files:**
- Create: `app/.../display/camera/FrameDiff.kt`, `display/camera/Presence.kt`, `display/CameraWake.kt`; tests `display/camera/FrameDiffTest.kt`, `display/camera/PresenceTest.kt`, `display/CameraWakeTest.kt`, `display/camera/PresenceSourceTest.kt`

**Interfaces:**
- Consumes: `DisplayState.screen`, `isNight`, `moved()` (Task 3); `CameraAccess.state`, `DisplaySettings.current`, `wakesByCamera` (Task 2); `AlertHost.inFront`, `WallClock`, `Startable`.
- Produces (package `uk.co.siland.culvery.display.camera` unless said):
  - `class Luma(val width: Int, val height: Int, val values: ByteArray)` (internal; a greyscale frame, never kept past one comparison)
  - `const val DIFF_WIDTH = 32`, `const val DIFF_HEIGHT = 24`, `const val PIXEL_DELTA = 24`, `const val CHANGED_SHARE = 0.04f`
  - `internal class FrameDiff { fun moved(frame: Luma): Boolean }` (ruling 10; holds only the last 32 × 24 frame)
  - `class NoFrontCamera : Exception()`; `class CameraStopped : Exception()`
  - `fun interface FrameSource { fun open(onFrame: (Luma) -> Unit, onFailed: (Exception) -> Unit): AutoCloseable }`
  - `const val LOOK_EVERY_MS = 1_000L`, `const val CAMERA_RETRY_MS = 300_000L`
  - `@Singleton class Presence internal constructor(frames: FrameSource, clock: WallClock, scope: CoroutineScope, diff: () -> FrameDiff = ::FrameDiff)` (+ `@Inject`): `running: StateFlow<Boolean>`, `movements: SharedFlow<Long>` (epoch millis, nothing else), `fun run(allowed: Boolean)`
  - (package `uk.co.siland.culvery.display`) `fun cameraAllowed(screen: Screen, night: Boolean, inFront: Boolean, wakes: Boolean): Boolean` (ruling 9); `class CameraWake internal constructor(screen: StateFlow<Screen>, night: StateFlow<Boolean>, inFront: StateFlow<Boolean>, prefs: StateFlow<DisplayPrefs>, camera: StateFlow<CameraState>, presence: Presence, moved: () -> Unit, scope: CoroutineScope) : Startable` (+ `@Inject` from `DisplayState`, `AlertHost`, `DisplaySettings`, `CameraAccess`, `Presence`, the application scope)

- [ ] **Step 1: Write the failing tests**

`FrameDiffTest.kt` (plain JVM; frames built 32 × 24 by a helper `frame { x, y -> value }`):
- `theFirstFrameIsNeverMovement`; `identicalFramesAreStill`.
- `aShapeMovingIsMovement`: a 6 × 8 block of 200 on 60 moves 6 pixels → true.
- `aWholeFrameGettingBrighterIsNotMovement` (Review Focus 1): every pixel +80 → false; +80 with the block moved → true.
- `sensorNoiseIsNotMovement`: ±10 seeded noise on every pixel → false.
- `aSmallChangeBelowTheShareIsNotMovement`: 25 pixels (3 %) changed by 100 → false; 31 (4 %) → true.
`PresenceTest.kt` (`runTest`, a `FakeFrameSource` recording opens and closes and delivering frames on demand, `ShadowLog` via Robolectric):
- `nothingOpensUntilAllowed`; `allowedOpensOnceAndRunningIsTrue`; `notAllowedClosesAndRunningIsFalse`.
- `onlyOneFrameASecondIsLookedAt`: frames at 0, 300, 700, 1 000 ms; the diff sees 0 and 1 000 only (a spy `FrameDiff` counting calls).
- `movementIsATimestampOnly`: a moving pair → one emission equal to the clock's millis.
- `aCameraErrorIsNoPresenceAndOneLine` (Review Focus 4): `onFailed(CameraStopped())` → closed, `running` false, exactly one `W` line "The camera stopped (CameraStopped)".
- `aFailedCameraIsTriedAgainAfterFiveMinutes`: no open at 299 999 ms, one at 300 000; not allowed meanwhile → none.
- `noFrontCameraIsNeverRetried`.
`CameraWakeTest.kt` (`runTest`; the internal constructor with flows, a real `Presence` over the `FakeFrameSource`, `moved` counting):
- `theCameraRunsOnlyInTheDayInFrontWithWakeUpOn` (§4.3): a table over `Screen × night × inFront × wakes` → `cameraAllowed` true exactly for (AWAKE or ASLEEP), day, in front, wakes.
- `aMovementWakesTheDisplay`: a moving pair of frames → `moved` called once.
- `theCameraClosesWhenCulveryLeavesTheFront` (Review Focus 2); `theCameraClosesAtNight`; `switchingWakeUpOffClosesTheCamera`.
`PresenceSourceTest.kt` (plain JVM over `src/main/java/uk/co/siland/culvery/display/camera/*.kt`):
```kotlin
/** 8 §4.3: nothing leaves the reader. Fails today: there are no files; later, if any of these appears. */
@Test
fun thePresenceCodeHasNoFileNetworkOrImagePath() {
    val files = cameraSources()
    assertThat(files.map { it.name }).containsAtLeast("FrameDiff.kt", "Presence.kt")
    val banned = listOf("java.io", "java.net", "okhttp", "Bitmap", "compress(", "YuvImage", "MediaStore", "ImageWriter",
        "MediaRecorder", "openFileOutput", "cacheDir", "filesDir", "getExternal", "DataStore", "SharedPreferences", "Socket", "Intent(", "sendBroadcast")
    val found = files.flatMap { f -> banned.filter { it in f.readText() }.map { "${f.name}: $it" } }
    assertThat(found).isEmpty()
}

@Test
fun presenceGivesOutOnlyTimestamps() // by Java reflection: Presence's public methods are getRunning, getMovements, run; no public method's generic return or parameter type names Luma or byte[]
```
Run: `./gradlew :app:testDebugUnitTest --tests '*FrameDiffTest*' --tests '*PresenceTest*' --tests '*CameraWakeTest*' --tests '*PresenceSourceTest*'` — Expected: FAIL to compile (the source test fails on the empty list).

- [ ] **Step 2: Implement**

`FrameDiff.moved`: the shift is `mean(frame) − mean(last)`; a pixel counts when `|v − last − shift| > PIXEL_DELTA`; moved when `count ≥ CHANGED_SHARE × pixels`; then `last = frame`. `Presence.run` opens or closes on a change only; `onFrame` drops a frame within `LOOK_EVERY_MS` of the last looked at (by `clock.nowMillis()`); a failure closes, logs, and (unless `NoFrontCamera`) launches one `delay(CAMERA_RETRY_MS)` retry cancelled by `run(false)`. Frames reach `onFrame` on the camera's thread: `FrameDiff` runs there; only the timestamp crosses into `scope`. `CameraWake.start()` collects `combine(screen, night, inFront, prefs, camera)` into `presence.run(cameraAllowed(…))` and `presence.movements` into `moved()`. Nothing binds `CameraWake` yet: `Presence` needs Task 6's `FrameSource`.

- [ ] **Step 3: Run them, the gate, commit**
```bash
git add app
git commit -m "Look for movement with the front camera once a second, only in the day while Culvery is in front, keeping no image"
```

---

### Task 6: `Camera2Frames`: the front camera, small and slow (§4.3, §5; ruling 11)

**Review:** opus.

**Files:**
- Create: `app/.../display/camera/Camera2Frames.kt`; test `display/camera/Camera2FramesTest.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`<uses-permission android:name="android.permission.CAMERA" />`, `<uses-feature android:name="android.hardware.camera.front" android:required="false" />`), `display/di/DisplayModule.kt` (`@Binds frames(Camera2Frames): FrameSource`, `@Binds @IntoSet cameraWake(CameraWake): Startable`)

**Interfaces:**
- Consumes: `FrameSource`, `Luma`, `NoFrontCamera`, `CameraStopped`, `DIFF_WIDTH`, `DIFF_HEIGHT` (Task 5).
- Produces: `class Camera2Frames @Inject constructor(@ApplicationContext context: Context) : FrameSource`; pure helpers `internal fun smallestSize(sizes: List<Pair<Int, Int>>): Pair<Int, Int>` (the smallest by area at least `DIFF_WIDTH` × `DIFF_HEIGHT`), `internal fun slowestRange(ranges: List<IntRange>): IntRange` (lowest upper bound, then lowest lower), `internal fun lumaOf(plane: ByteBuffer, rowStride: Int, pixelStride: Int, width: Int, height: Int): Luma` (block-averages the Y plane to 32 × 24).

- [ ] **Step 1: Write the failing tests**

`Camera2FramesTest.kt`:
- `theSmallestSizeIsPicked` (`[(1920,1080), (320,240), (176,144), (16,12)]` → `(176,144)`); `theSlowestRateIsPicked` (`[15..30, 7..15, 30..30]` → `7..15`).
- `lumaReadsThePlaneWithItsStrides`: a 64 × 48 plane with `rowStride = 72`, `pixelStride = 1`, left half 0, right half 200 → 32 × 24 with columns 0–15 at 0 and 16–31 at 200; and a `pixelStride = 2` case.
- `noFrontCameraFailsAtOnce` (Robolectric `ShadowCameraManager` with one back camera): `open` → `onFailed(NoFrontCamera)`, no frame.
- `closingTwiceIsSafe` (Robolectric, no camera): `open(...).close()` twice, no exception.
Run: `./gradlew :app:testDebugUnitTest --tests '*Camera2FramesTest*'` — Expected: FAIL to compile.

- [ ] **Step 2: Implement**

A `HandlerThread("CulveryPresence")` per open; `CameraManager.openCamera(id, executor, StateCallback)` for the first `LENS_FACING_FRONT` id; `ImageReader.newInstance(w, h, YUV_420_888, 2)` at `smallestSize`; a repeating `TEMPLATE_PREVIEW` request to the reader's surface with `CONTROL_AE_TARGET_FPS_RANGE = slowestRange(…)`; the session through `SessionConfiguration(SESSION_REGULAR, listOf(OutputConfiguration(surface)), executor, …)`. Each image: `acquireLatestImage()`, `lumaOf(planes[0] …)`, `close()` in `finally`, then `onFrame`. `onError`/`onDisconnected`/a thrown `CameraAccessException` or `SecurityException` → close everything, `onFailed(CameraStopped())` or the exception. `close()` stops the repeat, closes session, device and reader, quits the thread; idempotent.

`PresenceSourceTest.thePresenceCodeHasNoFileNetworkOrImagePath`'s `containsAtLeast` gains `"Camera2Frames.kt"`, so the guard covers the reader.

- [ ] **Step 3: Run them, the gate, commit**
```bash
git add app
git commit -m "Read the front camera at its smallest size and slowest rate, as a small greyscale frame"
```

---

### Task 7: `ScreenBrightness`: the levels, the fade, the window only (§2.8, §4.2, §4.4; rulings 8, 21; Review Focus 2)

**Review:** opus.

**Files:**
- Create: `app/.../display/ScreenBrightness.kt`; tests `display/ScreenBrightnessTest.kt`, `display/BrightnessSourceTest.kt`
- Modify: `shell/ThemeSchedule.kt` (`internal fun sunToday(daylight: Daylight?): Flow<SunTimes?>`, moved from `ShellViewModel`), `shell/ShellViewModel.kt` (calls it)

**Interfaces:**
- Consumes: `Screen`, `DisplayState.screen`, `isNight` (Task 3); `DisplaySettings.current` (Task 2); `Daylight`, `HouseholdClock.minutes`, `AlertHost.inFront`.
- Produces: `const val LOWEST = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF`, `const val ANDROIDS = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE`, `const val FADE_MS = 60_000L`, `const val FADE_STEP_MS = 2_000L`; `fun isEvening(now: LocalTime, sun: SunTimes?): Boolean`; `fun targetBrightness(screen: Screen, inFront: Boolean, night: Boolean, evening: Boolean, prefs: DisplayPrefs): Float`; `@Singleton class ScreenBrightness internal constructor(screen: StateFlow<Screen>, night: StateFlow<Boolean>, inFront: StateFlow<Boolean>, minutes: Flow<LocalDateTime>, sun: Flow<SunTimes?>, prefs: StateFlow<DisplayPrefs>, scope: CoroutineScope)` (+ `@Inject` from `DisplayState`, `AlertHost`, `HouseholdClock`, `Optional<Daylight>` through `sunToday`, `DisplaySettings`, the application scope): `level: StateFlow<Float>`; `fun applyBrightness(window: Window, level: Float)`.

- [ ] **Step 1: Write the failing tests**

`ScreenBrightnessTest.kt`:
- `daytimeBetweenSunriseAndSunset` (§2.8): sun 07:12–18:05; `isEvening` true at 07:11, false at 07:12 and 18:04, true at 18:05.
- `noSunTimesMeansTheDaytimeLevel` (§4.4): `isEvening(23:00, null)` false; also a sunrise after sunset (unusable) is never evening.
- `asleepAndTheNightClockAreTheLowest`: `targetBrightness(ASLEEP, …)` and `(NIGHT_CLOCK, …)` == `LOWEST`.
- `aTapAtNightGivesTheEveningLevel` (decision 8): AWAKE, night true, evening false (a June morning) → 0.25f.
- `notInFrontLeavesAndroidsBrightness` (Review Focus 2): any screen, `inFront` false → `ANDROIDS`.
- `awakeIsTheDayOrEveningLevel`: 0.70f and 0.25f.
- `theSwitchAtSunsetFadesOverAMinute` (`runTest`): AWAKE, a tick at 18:05 → `level` 0.70 at 0 ms, between 0.46 and 0.49 at 30 000, 0.25 at 60 000 and not before 58 000.
- `wakingAndSleepingAreAtOnce`: ASLEEP → AWAKE gives 0.70 at the next emission; AWAKE → ASLEEP gives `LOWEST`.
- `aSliderChangeIsAtOnce`: `dayPercent` 70 → 40 gives 0.40 at once.
- `theLevelIsSetOnCulverysWindow` (Robolectric `Activity`): `applyBrightness(window, 0.25f)` → `window.attributes.screenBrightness == 0.25f`.
`BrightnessSourceTest.kt` (§4.4, ruling 21): `nothingChangesAndroidsBrightnessSetting`: no `app` main source contains `SCREEN_BRIGHTNESS` or `Settings.System.put`; the manifest has no `WRITE_SETTINGS` (**pin**, kept for later changes).
Run: `./gradlew :app:testDebugUnitTest --tests '*ScreenBrightnessTest*' --tests '*BrightnessSourceTest*' --tests '*ShellViewModelTest*'` — Expected: FAIL to compile; `ShellViewModelTest` passes.

- [ ] **Step 2: Implement**

`level` is a `MutableStateFlow` driven by one collector of `combine(...)` → (target, cause); a cause of "evening flipped while AWAKE and in front" fades with `FADE_STEP_MS` steps under `collectLatest`, any other change sets it at once. `sunToday` keeps `ShellViewModel`'s retry and `onStart { emit(null) }`.

- [ ] **Step 3: Run them, the gate, commit**
```bash
git add app
git commit -m "Set Culvery's own window to a daytime or evening brightness by the sun, and to its lowest while asleep"
```

---

### Task 8: `HeatLog` (§4.7; ruling 15)

**Review:** sonnet.

**Files:**
- Create: `app/.../display/HeatLog.kt`; test `display/HeatLogTest.kt`
- Modify: `display/di/DisplayModule.kt` (`@Binds tabletHeat(AndroidTabletHeat): TabletHeat`, `@Binds @IntoSet heatLog(HeatLog): Startable`)
- Modify: `build-logic/convention/src/test/kotlin/LogHygieneTest.kt` (`ALLOWED` gains `"heat.status", "heat.batteryTenths", "heat.batteryPercent"`, with a KDoc line: "`heat.*` are the tablet's thermal status and battery numbers (8 §4.7).")

**Interfaces:**
- Consumes: `Startable`, `ApplicationScope`.
- Produces: `data class HeatReading(val status: Int, val batteryTenths: Int?, val batteryPercent: Int?)`; `interface TabletHeat { fun reading(): HeatReading; fun onStatusChange(listener: () -> Unit): AutoCloseable }`; `class AndroidTabletHeat @Inject constructor(@ApplicationContext context: Context) : TabletHeat` (`PowerManager.currentThermalStatus`, `addThermalStatusListener(executor, …)`; the sticky `ACTION_BATTERY_CHANGED` through `ContextCompat.registerReceiver(context, null, filter, RECEIVER_NOT_EXPORTED)`, `EXTRA_TEMPERATURE`, `EXTRA_LEVEL` × 100 / `EXTRA_SCALE`; a missing extra is null); `const val HEAT_EVERY_MS = 900_000L`; `class HeatLog internal constructor(tablet: TabletHeat, scope: CoroutineScope) : Startable` (+ `@Inject` from `TabletHeat` and the application scope).

- [ ] **Step 1: Write the failing tests** (`HeatLogTest.kt`, Robolectric for `ShadowLog`, `runTest`, a fake `TabletHeat`)
- `oneLineAtStart`.
- `aStatusChangeLogsOneLineWithTheBattery` (§4.7): the fake fires → one more line, with the new status.
- `theBatteryIsLoggedEveryFifteenMinutes`: one line at start; none more at 899 999 ms; one at 900 000; two at 1 800 000.
- `theLinesAreWarningsWithNumbersOnly`: every line's level is `Log.WARN`, tag `HeatLog`, and matches `^Heat: thermal \d+, battery (\d+|null) tenths of a degree, (\d+|null) percent$`.
- `androidsReadingIsNumbers` (Robolectric, a sticky battery intent with temperature 312, level 45, scale 100) → `HeatReading(status = 0, 312, 45)`.
`LogHygieneTest` runs as it is (**pin**: the new lines must pass it).
Run: `./gradlew :app:testDebugUnitTest --tests '*HeatLogTest*'` and `./gradlew -p build-logic :convention:test` — Expected: FAIL to compile; then `LogHygieneTest` fails until `ALLOWED` has the three.

- [ ] **Step 2: Implement** `start()` logs once, registers the listener (logs on each change), and launches the 15-minute loop; each line is `Log.w(TAG, "Heat: thermal ${heat.status}, battery ${heat.batteryTenths} tenths of a degree, ${heat.batteryPercent} percent")` with `val heat = tablet.reading()` (the local's name is the allowed ids' prefix).

- [ ] **Step 3: Run them, the gate, commit**
```bash
git add app build-logic
git commit -m "Log the tablet's thermal status and battery temperature as numbers, on each change and every 15 minutes"
```

---

### Task 9: The dark screen, the night clock and the camera mark in the shell's layers (§2.2, §3, §4.1, §4.2, §4.3; rulings 7, 19, 20)

**Review:** sonnet.

**Files:**
- Create: `app/.../display/ui/SleepLayers.kt`, `display/ui/DisplayDimens.kt`; tests `display/ui/SleepLayersTest.kt`, `display/ui/SleepLayersScreenshotTest.kt`
- Modify: `shell/ui/OverlayLayers.kt` (`ShellLayers` gains `sleeping: Boolean = false` and `sleep: @Composable () -> Unit = {}` drawn after the alert, before the PIN pad, outside the counting box, with the shell, sheet and alert cleared from semantics while `sleeping`; `cameraMark: Boolean = false` drawn above the toasts)

**Interfaces:**
- Consumes: `Screen` (Task 3); `Icons.PHOTO_CAMERA` (Task 1).
- Produces: `object NightClockTokens { val size; val ink }` (ruling 19); `object DisplayDimens { val markSize = 20.dp; val markInset = 12.dp }`; `@Composable fun DarkScreen(onWake: () -> Unit)`; `@Composable fun NightClockScreen(time: () -> LocalDateTime, onWake: () -> Unit)`; `@Composable fun CameraMark()`; `@Composable fun SleepLayer(screen: Screen, time: () -> LocalDateTime, onWake: () -> Unit)` (nothing for AWAKE); `const val WAKE_THE_SCREEN = "Wake the screen"`, `const val THE_CAMERA_IS_ON = "The camera is on"`.

- [ ] **Step 1: Write the failing tests** (`SleepLayersTest.kt`, Robolectric; each ends with `assertClickablesHaveRoleAndLabel()`)
- `theNightClockShowsOnlyTheTime` (§2.2): at 23:41 the only text node is "23:41"; its click label is "Wake the screen".
- `aWakingTapPressesNothingBeneath` (§4.1): `ShellLayers` with a full-screen clickable shell counting clicks, `sleeping = true`, `sleep = { SleepLayer(ASLEEP, …, onWake = { woke++ }) }`; a click at the centre → `woke == 1`, the shell's count 0, `onTouch` not called; the same for NIGHT_CLOCK. Fails if the layer lets the gesture through.
- `theSleepLayerSitsAboveTheSheetAndTheAlertAndBelowThePinPad`: with a sheet, an alert and the PIN pad showing, a tap on the pad's key reaches the pad; with no PIN pad, a tap on the alert's spot wakes and doesn't reach the alert.
- `awakeDrawsNothing`.
- `whatTheSleepLayerCoversIsHiddenFromTalkBack`: with ASLEEP, the shell's button has no semantics node; AWAKE, it has (as under the PIN pad). Fails if TalkBack could press a control under the black screen.
- `theCameraMarkShowsWhileTheCameraRuns`: `cameraMark = true` → a node with content description "The camera is on", over the dark screen too; false → none.
`SleepLayersScreenshotTest.kt`: `night_clock` (dark theme; the clock in the token colour on black), `night_clock_fs15`, `camera_mark_dark`, `camera_mark_light` (Home with the mark).
Run: `./gradlew :app:testDebugUnitTest --tests '*SleepLayersTest*'` — Expected: FAIL to compile.

- [ ] **Step 2: Implement** each layer a `Box(Modifier.fillMaxSize().background(Color.Black))` with `pointerInput { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume(); onWake() } }` and `semantics { onClick(WAKE_THE_SCREEN) { onWake(); true }; role = Role.Button }`; the clock in `HH:mm`.

- [ ] **Step 3: Run them; record the images; the baseline check with the four names**

- [ ] **Step 4: The gate and commit**
```bash
git add app
git commit -m "Add the dark screen, the night clock and the camera mark as layers above the alert and below the PIN pad"
```

---

### Task 10: Settings › Display (§4.3, §4.6; rulings 12, 13, 17, 18)

**Review:** sonnet.

**Files:**
- Create: `app/.../display/ui/DisplaySettingsPage.kt`, `display/DisplayWords.kt`; tests `display/ui/DisplaySettingsPageTest.kt`, `display/ui/DisplaySettingsScreenshotTest.kt`
- Modify: `display/di/DisplayModule.kt` (`@Binds @IntoSet displayPage(DisplayPage): SettingsPage`)

**Interfaces:**
- Consumes: `DisplaySettings`, `DisplayPrefs`, `SLEEP_CHOICES`, `BRIGHTNESS_RANGE`, `CameraAccess`, `CameraState`, `wakesByCamera` (Task 2); `HhSwitchRow`, `HhTimeStepperRow`, `TIME_STEP_MINUTES` (Task 1); `HhPercentSlider`, `HhChoiceChip`, `HhButton`, `HhSectionLabel`; `Daylight` (sun times known or not); `Toaster.save`, `ShellNavigator.openSystemScreen`.
- Produces: `const val DISPLAY_PAGE_ID = "display"`; `class DisplayPage @Inject constructor(settings: DisplaySettings, access: CameraAccess, daylight: Optional<Daylight>, toaster: Toaster) : SettingsPage` (id `display`, title "Display", order 440; sun known is `sunToday(daylight).map { it != null }`); `data class DisplaySettingsUi(val prefs: DisplayPrefs, val camera: CameraState, val sunKnown: Boolean)`; `class DisplaySettingsActions(onNight: (LocalTime, LocalTime) -> Unit, onDay: (Int) -> Unit, onEvening: (Int) -> Unit, onSleepAfter: (Int) -> Unit, onWakeByCamera: (Boolean) -> Unit, onAllowCamera: () -> Unit, onOpenAndroidSettings: () -> Unit)`; `@Composable fun DisplaySettingsScreen(ui, actions)`; `@Composable internal fun DisplaySettingsHost(settings, access, sunKnown: Flow<Boolean>, toaster)`; words in `DisplayWords.kt` (the Copy list), `fun nightClockLine(from: LocalTime, until: LocalTime): String`, `const val CANT_OPEN_ANDROIDS_SETTINGS = "Culvery can’t open Android’s settings for Culvery."`, `fun appDetailsIntents(packageName: String): List<Intent>` (`ACTION_APPLICATION_DETAILS_SETTINGS` with `package:` uri).

- [ ] **Step 1: Write the failing tests** (`DisplaySettingsPageTest.kt`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `itSitsBeforeMusic` (ruling 17): id, title, order 440.
- `theNightClockTimesStepByHalfAnHour` (§4.6): `_later` on From → `onNight(22:30, 07:00)`; the line reads "A dim clock shows from 22:00 until 07:00. Only a tap wakes the screen, and rings wait in the Door tab."
- `theBrightnessSlidersSaveWhenLetGo`: the day slider let go → `onDay(n)` once; spoken "Daytime brightness, 70 percent".
- `withoutSunTimesItSaysTheDaytimeLevelApplies` (§4.4).
- `theSleepDelayChoices` (§4.6): four chips "2 minutes" … "15 minutes", "5 minutes" selected; tapping "10 minutes" → `onSleepAfter(10)`.
- `allowedShowsTheSwitchOnByDefault` (§4.6): ALLOWED, `wakeByCamera = null` → the switch on; toggled → `onWakeByCamera(false)`.
- `notAskedOffersAllowTheCamera` (§4.3): no switch; the line and "Allow the camera" → `onAllowCamera()`; ASK_AGAIN the same.
- `aRefusalSaysHowToAllowIt` (§4.3): the refused line and "Open Android’s settings for Culvery" → `onOpenAndroidSettings()`.
- `noFrontCameraSaysATapWakes`.
`DisplaySettingsScreenshotTest.kt`: `settings_display_allowed_dark`, `settings_display_allowed_light`, `settings_display_not_asked_dark`, `settings_display_refused_dark`.
Run — Expected: FAIL to compile.

- [ ] **Step 2: Implement** `DoorSettingsScreen`'s shape and `DoorSettingsHost`'s (`toaster.save` with `Log.w(TAG, "Couldn't save a display setting (${it::class.simpleName})")`); `onAllowCamera` is `access.ask()`; `onOpenAndroidSettings` is `navigator.openSystemScreen(appDetailsIntents(context.packageName), CANT_OPEN_ANDROIDS_SETTINGS)`.

- [ ] **Step 3: Run them; record the images; the baseline check with the four names**

- [ ] **Step 4: The gate and commit**
```bash
git add app
git commit -m "Add Settings › Display: the night clock's hours, the two brightness levels, the sleep delay and waking by the camera"
```

---

### Task 11: Wiring: `MainActivity`, `DisplayModule`, the camera prompt (§3, §4.1–4.4; rulings 1, 4, 6, 12, 21)

**Review:** opus.

**Files:**
- Create: test `display/DisplayWiringTest.kt`
- Modify: `MainActivity.kt`

**Interfaces:**
- Consumes: everything above; `KioskLifecycle.openSystemScreen`; `ShellLayers(sleeping, sleep, cameraMark)`, `SleepLayer` (Task 9).
- Consumes also: `DisplayModule` as Tasks 3, 6, 8 and 10 left it.
- Produces: `internal fun holdsOf(pinShowing: Boolean, sheetShowing: Boolean, alertShowing: Boolean, settingsOpen: Boolean, complete: Boolean?): Holds`; `internal const val CAMERA_PROMPT_MISSING = "Culvery can’t ask for the camera on this tablet."`

- [ ] **Step 1: Write the failing tests** (`DisplayWiringTest.kt`)
- `theHoldsAreWhatTheShellShows`: `holdsOf(...)` for each flag alone; `complete == false` is the wizard; `complete == null` (still reading) holds too.
- `theModuleBindsEachPiece` (**pin** over Tasks 3, 6, 8, 10; Java reflection over `DisplayModule`'s abstract methods): `NightHours` from `DisplayState` (what Door's `DoorAlerts` reads), `FrameSource` from `Camera2Frames`, `TabletHeat` from `AndroidTabletHeat`, `@IntoSet` `Startable` from `HeatLog` and from `CameraWake`, `@IntoSet` `SettingsPage` from `DisplayPage`.
Run: `./gradlew :app:testDebugUnitTest --tests '*DisplayWiringTest*'` — Expected: FAIL to compile.

- [ ] **Step 2: Implement in `MainActivity`**
- Inject `display: DisplayState`, `brightness: ScreenBrightness`, `presence: Presence`, `camera: CameraAccess`.
- `FLAG_KEEP_SCREEN_ON` stays (§4.2).
- In `repeatOnLifecycle(STARTED)`: `brightness.level.collect { applyBrightness(window, it) }`; `camera.requests.collect { kiosk.openSystemScreen(CAMERA_PROMPT_MISSING) { askCamera.launch(Manifest.permission.CAMERA) } }`.
- `askCamera = registerForActivityResult(RequestPermission()) { granted -> lifecycleScope.launch { camera.answered(granted, shouldShowRequestPermissionRationale(CAMERA)) } }`.
- `onResume`: `camera.refresh(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))`.
- In `setContent`: `val screen by display.screen.collectAsStateWithLifecycle()`, `val running by presence.running.collectAsStateWithLifecycle()`; `LaunchedEffect(holds) { display.holds(holds) }` with `holds = holdsOf(pinRequest != null, overlay.isShowing, alert != null, state.settingsOpen, complete)`; `ShellLayers(…, sleeping = screen != Screen.AWAKE, sleep = { SleepLayer(screen, { now.value }, display::tapped) }, cameraMark = running)`.

- [ ] **Step 3: Run them, the gate**

Run: `./gradlew :app:testDebugUnitTest`, then the gate — Expected: PASS, no image changed; `./gradlew :app:assembleDebug` builds (Hilt's graph is whole).

- [ ] **Step 4: Commit**
```bash
git add app
git commit -m "Wire sleep and wake into the shell: the layers, the window's brightness and the camera prompt"
```

---

### Task 12: The emulator walk-through on `culvery-5` (§7)

**Review:** the controller, with the user at the emulator. (The workflow runs `simplify` over the branch first.)

**Files:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Step 6); any file a fault fix needs.

**Rules.** As Plan 7's Task 17: the emulator `emulator-5554` holds a signed release and the household — **never install on it, never uninstall anything, never run a Gradle `install…` task while any device is connected**. Only the `culvery-5` AVD; every `adb` command names `-s "$S"`. The user types every PIN. The AVD's own config is not changed: the camera is given on the command line. Put back every Culvery setting changed (night times, the sleep delay, the levels) before closing.

- [ ] **Step 1: The AVD** — `-list-avds` shows `culvery-5` (else stop and ask); start it with `-no-snapshot-save -camera-front emulated` (its config has no front camera; the emulated one is a moving test scene, so presence itself is checked on the tablet); its serial to `$TMP/culvery-5-serial`; `S=$(cat "$TMP/culvery-5-serial")`.
- [ ] **Step 2: Install the debug build there only** — `./gradlew :app:assembleDebug`; `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 3: Walk the checklist** (tick each with one line)
1. Settings › Display (Admin PIN) sits after Calendars, before Music; the four sections read as written; no sun times line only if the AVD's household has no location.
2. **The camera prompt:** "Allow the camera" → Culvery unpins, Android's dialog → Deny → "Allow the camera" again → Deny (Android 11's "Don't ask again" on the second) → the refused line; "Open Android’s settings for Culvery" (fresh Admin PIN) → allow there → back in Culvery: pinned again (`adb -s "$S" shell dumpsys activity activities | grep -i "mLockTaskModeState"` shows PINNED), the switch on.
3. **The camera mark** shows while the camera runs: `adb -s "$S" shell dumpsys media.camera | grep -i -A2 "active camera clients"` names Culvery's package when the mark shows, and nothing when it doesn't.
4. **Asleep:** the sleep delay at 2 minutes; Settings closed, signed out; at 2 minutes the screen goes black, the mark still there; a tap wakes it and presses nothing (start the tap on a Home card's button: it isn't opened). The window brightness drops: `adb -s "$S" shell dumpsys window windows | grep -i -o "sbrt=[0-9.-]*"` (if the attribute isn't printed, the user judges by eye).
5. **Holds:** a sheet open, the PIN pad up, Settings open: none sleeps past 2 minutes; each closed: asleep 2 minutes later.
6. **The doorbell by day**, if this AVD's Door tab is connected (the `ring()` helper from Plan 5's emulator walk-through): asleep → a fake ring wakes the wall and shows the card with the chime.
7. **Night:** the night times set to start 3 minutes from now and end 10 minutes from now: at the start the night clock shows, the mark goes and `dumpsys media.camera` shows no client; a tap wakes at the evening level; 1 minute later back to the clock; a fake ring at night: no card, no chime, the Door badge counts it; at the end the wall wakes.
8. **Not in front:** Use other apps (fresh Admin PIN), open Android's Settings: no mark, no client, Android's own brightness; back in Culvery: awake, then asleep 2 minutes later.
9. **Without a camera:** `adb -s "$S" emu kill`; start again without `-camera-front`: Settings › Display says "This tablet has no front camera, so a tap wakes the screen."; the day screen still sleeps and a tap wakes it.
10. **Heat lines:** `adb -s "$S" logcat -d -s HeatLog:W` shows the start line in the format, numbers only.
11. TalkBack (short, with the user's yes): the dark screen and the night clock say "Wake the screen" and a double-tap wakes; the mark is read as "The camera is on".
- [ ] **Step 4: The fault loop** — each fault a failing test, the fix, the module's tests and the gate, a commit naming the fault, `install -r`, the item re-checked.
- [ ] **Step 5: Put back** the night times (22:00–07:00), the sleep delay (5), the levels (70, 25) in Settings › Display; `adb -s "$S" emu kill`.
- [ ] **Step 6: Record and close** — under a new "Plan 8 (Sleep and wake): emulator walk-through" heading: the outcome; how the permission dialog behaved with pinning; faults and commits; taste notes for the design walk-through.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 8's emulator walk-through"
```

---

### Task 13: On the tablet: wake-up, the night clock in the dark, power and heat, decision 9; final review, merge, install (§1, §2.9, §7)

**Review:** the controller with the user; an opus final review of the whole branch. **Needs the son's SM-T510 on the cable and the household's yes.** The measurement needs an afternoon of about 4 hours with nobody walking past the wall, and **the user to unplug and replug the tablet** each time (the controller can't, and the USB cable charges it).

**Files:** maybe `display/DisplaySettings.kt` (`WAKE_BY_CAMERA_DEFAULT`), `camera/FrameDiff.kt` and its constants, `ScreenBrightness`'s defaults, `NightClockTokens`, with their tests; the follow-ups file.

**Rules.** 4e's tablet rules (`docs/superpowers/plans/2026-10-12-4e-walkthrough.md`, "Rules"): save and restore every setting changed (the save and restore blocks there), never touch other apps' data, signed releases with `adb install -r`, **never uninstall Culvery**, the serial in `$TMP/culvery-4e-serial`, device paths in one quoted `shell` string, the names check before any commit. The settings this task may change: Culvery's own Settings › Display values (written down first, put back last) and, only if used, `dumpsys battery` (put back with `adb -s "$S" shell dumpsys battery reset`). Android's brightness and screen timeout are read, never changed.

- [ ] **Step 1: The baseline, before installing** (the tablet still runs Plan 7's release, always on at the son's brightness)
1. Save `settings get system screen_brightness` and `screen_brightness_mode` (to show they are the same at the end; nothing changes them).
2. Charge to at least 80 %. Then, per run: `adb -s "$S" shell dumpsys battery | grep -E "level|temperature|Charge counter"` → `$TMP/culvery-8-run-A-start.txt`; `adb -s "$S" logcat -c`; the user unplugs and notes the time; **60 minutes**, Culvery on Home, nobody near; the user plugs in; at once the same `dumpsys battery` → `…-run-A-end.txt`.
- [ ] **Step 2: Install the release** — signed per `docs/setup/release.md`; `adb -s "$S" install -r app/build/outputs/apk/release/app-release.apk`; Settings › Display: allow the camera (the user taps Allow); write down the defaults.
- [ ] **Step 3: The measurement runs** (each exactly as Step 1's run, 60 minutes unplugged, starting within 2 % of run A's start level; recharge between runs if needed)
- **Run B:** camera wake-up **off**, the day screen sleeps after 5 minutes.
- **Run C:** camera wake-up **on**, nobody near (asleep with the camera running).
After each run, while plugged: `adb -s "$S" logcat -d -s HeatLog:W > "$TMP/culvery-8-run-X-heat.txt"` (the start line, each status change, the 15-minute lines with temperature and level).
For each run: drained % per hour (`level` start − end), the charge counter's difference when the tablet reports one, the highest battery temperature, the highest thermal status. A table in the follow-ups notes, numbers only.
- [ ] **Step 4: Decision 9** — the plan's proposed bar, **the user decides**: camera wake-up counts as running warm or costing noticeable power if run C's thermal status ever rose above 0 where run B's didn't, or its highest battery temperature is 3 °C or more above run B's, or it drained more than 2 % per hour more than run B. If so: `WAKE_BY_CAMERA_DEFAULT = false` (a failing `theSwitchIsOnByDefaultOnlyOnceAllowed` updated first, then the change), the gate, a commit `git commit -m "Ship camera wake-up off by default, as it costs the tablet too much"`, rebuild and `install -r`; on this tablet, Settings › Display's switch then reads off unless the Admin turns it on. Either way, the numbers and the decision go in the notes.
- [ ] **Step 5: Waking by the camera** (camera wake-up on for this step even if Step 4 turned the default off)
1. Asleep in daylight: the user walks straight towards the wall from 5 m, at a normal pace; note the distance at which it wakes and roughly how long after entering the camera's view (a phone stopwatch). Then from the side at 2 m, and once in lamp light at dusk.
2. Nobody near for 15 minutes with the room's lights and a Hue scene changed once (the user's choice): no wake (Review Focus 1). `HeatLog` lines are left as they are.
3. If it wakes too late or by itself: tune `PIXEL_DELTA`, `CHANGED_SHARE` or `smallestSize`'s floor with a failing `FrameDiffTest` first, the gate, a commit, `install -r`, and repeat 1–2.
- [ ] **Step 6: The night clock in the dark** (the user, at the household's bedtime; the controller is only needed if a change follows)
1. The room dark: the clock readable at arm's length; the room not lit by it (the user's judgement); black's glow at the lowest backlight noted.
2. A tap: the evening level doesn't dazzle; 1 minute later back to the clock.
3. If it lights the room: a dimmer `NightClockTokens.ink` first (§4.2), then the screenshot re-recorded, the gate, a commit, `install -r`. If the evening level dazzles: a lower default `eveningPercent`, the same way.
- [ ] **Step 7: Put back and read the logs** — Settings › Display to its defaults (or the values the user chose for the household, in the notes); `dumpsys battery reset` if used; `screen_brightness` and its mode read back equal to Step 1's (nothing changed them). The camera permission stays granted (the feature needs it) unless the user says otherwise. Culvery pinned. `adb -s "$S" logcat -d | grep -E "HeatLog|Presence|CameraWake|DisplayState"`: numbers, states and exception classes only. From here on the tablet's logs are read whenever it is plugged in while work is under way (§7). Mention Samsung's "Protect battery" (charging to 85 %) to the user as their choice for the son's tablet; change nothing.
- [ ] **Step 8: Final review, merge, install** — the follow-ups file under "Plan 8 (Sleep and wake): on the tablet": the three runs' numbers, decision 9's outcome, the wake-up distance and speed, the night clock's verdict, tuned values, taste notes; no names. Commit `git commit -m "Record Plan 8 on the tablet: the camera's power and heat, waking by the camera and the night clock in the dark"`. The opus final review against the spec, one fix round, `superpowers:finishing-a-development-branch` (merge locally; never push). The release from the merged `main`, `install -r`, the night clock and a camera wake checked once more.

---

## Spec coverage (8 design → tasks)

| Spec | Task |
|---|---|
| §1 outcome; no light sensor; done on the tablet | 7, 13 |
| §2.1–2.3 night and day; the dim clock; set times | 2, 3, 9, 10 |
| §2.4 only a tap wakes at night; no camera at night | 3, 5 |
| §2.5–2.6 the camera by day; 5 minutes, adjustable | 2, 3, 5, 6, 10 |
| §2.7 the doorbell day and night | 3 (the card wakes), 4 |
| §2.8 brightness by the time of day, evening on a night tap | 7, 10 |
| §2.9 the fallback | 2 (`WAKE_BY_CAMERA_DEFAULT`), 13 |
| §2.10 heat | 8 |
| §3 modules: `DisplayState`, `Presence`, `ScreenBrightness`, `HeatLog`, the layers, Settings › Display | 3, 5–10, 11 |
| §4.1 the table, the waking tap, not in front, holds | 3, 9, 11 |
| §4.2 dark, not off; `FLAG_KEEP_SCREEN_ON`; the clock's colour | 7, 9, 11, 13 |
| §4.3 the camera, privacy first; the mark; the permission | 2, 5, 6, 9, 10, 11 |
| §4.4 the window only; no sun times | 7 |
| §4.5 the doorbell; a card showing as night starts | 3, 4 |
| §4.6 Settings › Display | 1, 2, 10 |
| §4.7 heat lines in release | 8 |
| §5 when things go wrong; privacy | 2, 3, 5, 6, 7, 8 |
| §6 tests | every task |
| §7 emulator; tablet; power and heat; decision 9; Protect battery | 12, 13 |
| §8 not in this plan | none |
