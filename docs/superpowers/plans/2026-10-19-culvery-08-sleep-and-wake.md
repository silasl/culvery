# Culvery — Plan 8: Sleep and wake Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–12 are subagent tasks; Task 13 is the controller on the `culvery-5` AVD with the user; Tasks 14–15 need the user and the son's SM-T510.

> **Needs the user's OK: nothing.** No new library: the camera is read with the platform's Camera2 (`android.hardware.camera2`), as CameraX isn't in the version catalogue. `:app` gains `libs.androidx.datastore.preferences`, already in the catalogue at 1.2.1 and used by the capabilities (no version changes). No deprecated API is needed. Flagged and avoided: `CameraDevice.createCaptureSession(List<Surface>, …)` (deprecated in API 30; use `SessionConfiguration`), the no-argument `Handler()`, `Context.registerReceiver` without flags (use `ContextCompat.registerReceiver(…, RECEIVER_NOT_EXPORTED)`), `WindowManager.getDefaultDisplay`. One new permission, `android.permission.CAMERA` (spec §4.3). If the compiler shows a deprecation warning on a line you wrote, **stop and ask**.

**Goal:** At night the wall shows only a dim clock between set hours; in the day it goes dark after 5 minutes with nobody around and wakes when the front camera sees someone walk up, or on a tap; while awake, Culvery's window follows a daytime and a softer evening brightness by the sun; and the tablet's thermal state and battery temperature are logged, so the camera's power and heat can be measured on the SM-T510.

**Architecture:** Everything lives in `:app`, package `uk.co.siland.culvery.display`, beside the shell that already knows the PIN pad, the sheet, the alert and Settings. `DisplayState` is the one state machine (**Awake**, **Asleep**, **NightClock**) over the household's clock, the touches from `ShellUserActivity`, `AlertHost.inFront`, whether the shell holds the screen awake, and presence; it also says whether the camera is wanted, and it is the `NightHours` that `:capability:door` reads through a new `:core:plugin` interface, so a ring at night is only stored. The sealed package `display.camera` holds `Presence`, which runs a `FrameSource` (Camera2, front camera, smallest YUV size, lowest frame rate) and `FrameDiff` (frame differencing on a 32 × 24 greyscale image once a second) only while `DisplayState.cameraWanted`; it gives out a timestamp and nothing else. `ScreenBrightness` turns the state, the night hours and the sun times into a level for Culvery's window only. `HeatLog` is a `Startable` logging the thermal status and the battery as numbers. The dark screen and the night clock are full-screen layers in `ShellLayers` above the alert; Settings › Display is a core page bound by `:app`.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2, `android.hardware.camera2` + `android.media.ImageReader`, `PowerManager.addThermalStatusListener` (API 29+), `Intent.ACTION_BATTERY_CHANGED`, `WindowManager.LayoutParams.screenBrightness`, `ActivityResultContracts.RequestPermission`; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-19-culvery-8-sleep-and-wake-design.md`, with the plan review's toast ruling in §4.1. Read it with this plan.
**Previous plan (format, patterns):** `docs/superpowers/plans/2026-10-16-culvery-07-hue.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Tasks 13–15 update it).

**Plan series:** 1 · 2a · 2b-1 · 2b-2 · 3a · 4a · 4b · 4c · 4d-1 · 4d-2 · 4e · 5 Ring · 6 Sonos · 7 Philips Hue · **8 Sleep and wake (this plan)**.

**Builds on:** branch `plan-8-sleep-wake`, cut from `main` at 9990fee, in the worktree `C:\Users\si\code\siland\culvery-sleep`. Work only there; never touch any other checkout. Never push. File references were read at 410366e.

| # | Task | Review |
|---|---|---|
| 1 | The camera icon | sonnet |
| 2 | Core pieces: the daily window, the clock text and the switch and time rows, `NightHours` | sonnet |
| 3 | `DisplaySettings` and `CameraAccess` | sonnet |
| 4 | `DisplayState`: the three states, the timers, the hold, the camera wanted | **opus** (timers, every row of §4.1) |
| 5 | The doorbell at night | sonnet |
| 6 | `FrameDiff`, `Presence` and the sealed camera package | **opus** (privacy, timers) |
| 7 | `Camera2Frames`: the front camera, small and slow | **opus** (a system service, threads) |
| 8 | `ScreenBrightness`: the levels, the fade, the window only | **opus** (timers) |
| 9 | `HeatLog` | sonnet |
| 10 | The dark screen, the night clock, the camera mark and held toasts in the shell's layers | sonnet |
| 11 | Settings › Display | sonnet |
| 12 | Wiring: `MainActivity`, the camera prompt | **opus** (the activity, pinning) |
| 13 | The emulator walk-through on `culvery-5` | controller + user |
| 14 | On the tablet: power and heat, decision 9, waking by the camera, the night clock in the dark | controller + user, **needs the SM-T510** |
| 15 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:** the icon alone first (1: a font regeneration, which can stop for a download), then the shared pieces (2), the settings everything reads (3), the state machine (4) and its one consumer outside the app (5). Presence is split into the logic tested with fake frames (6) and the Camera2 reader only the tablet really proves (7). Brightness (8) and heat (9) stand alone. The screens (10, 11) are tested against plain values; the wiring (12) joins them in `MainActivity`. The emulator (13) before the tablet (14), and the merge last (15).

## Rulings against the code

Where the spec leaves a choice open, or doesn't fit the code at 410366e. Each is the plan's call unless it cites the spec or the user's plan-review rulings U1, U2 (2026-10-19).

1. **Where it lives:** `:app`, package `uk.co.siland.culvery.display` (and `display.camera`, `display.ui`, `display.di`); no new module. Door, a capability, may not depend on `:app`, so **`NightHours` goes in `:core:plugin`**: `interface NightHours { fun isNight(): Boolean? }`, null while the clock or the saved night times aren't known yet, computed when called (never from a collector that may not have run). Bound by `:app` to `DisplayState`.
2. **The daily window moves to core:** Door's `isQuiet(now, from, until)` becomes `inDailyWindow(now, from, until)` in `:core:plugin` (`DailyWindow.kt`); `isQuiet` calls it. Night uses the same rule, so **equal night times mean no night** (as equal quiet hours mean never quiet). Night times are wall-clock times from `HouseholdClock.now` (the household's zone), re-checked on every tick and zone change (§5).
3. **The rows move to core:** Door's private `SwitchRow` and `StepperRow` become `HhSwitchRow` and `HhTimeStepperRow` in `:core:ui` (`Rows.kt`), with Door's `EARLIER`/`LATER` words, its 30-minute step as `TIME_STEP_MINUTES = 30L`, and `clockText(time)` with its `HH:mm` formatter; Door imports them; **Door's images don't change**.
4. **The hold** is one Boolean the shell reports, `DisplayState.held(on)`: the PIN pad, a sheet, the alert (the door card), Settings or the wizard showing (`holding(…)` in the wiring, Task 12). It keeps the screen Awake, day or night (§4.1). **The hold starting while Asleep wakes the screen**: that is how the day doorbell wakes the wall (its card is the alert), with no ring signal of its own. **Night starting while held** leaves the screen Awake (night) until 1 minute after the hold ends (§4.5's door card rule, applied to every hold). When the hold ends, the idle wait starts again from that moment.
5. **The idle waits:** day `sleepAfterMinutes × 60 000 ms` from the last touch, movement, hold end, wake or settings change; night `NIGHT_AWAKE_MS = 60_000L` from the last touch. A changed sleep delay starts the wait again. **Touches only restart a wait while Awake**: only `tapped()` (the layer's own tap or TalkBack click) and `moved()` wake; a touch counted while Asleep or on the NightClock (a TalkBack focus move, say) changes nothing.
6. **Not in front** (`AlertHost.inFront` false): the state is Awake and no timer runs; the camera isn't wanted; the window's brightness is Android's (`BRIGHTNESS_OVERRIDE_NONE`). Back in front: Awake (or NightClock at night after the 1 minute), waits fresh.
7. **The start:** `screen` and `isNight()` are right from construction: a `DisplayState` built at 23:00 with the night times read starts as NightClock. Until the saved settings are read (`DisplaySettings.read`, null until then) or the clock knows its zone, `isNight()` is null and the screen is Awake; **Door treats null as night** (a ring just after a restart at night is only stored).
8. **The waking tap** is swallowed by the dark screen's and the night clock's layer, which calls `DisplayState.tapped()` and **not** `ShellUserActivity.touched()`: waking is not a touch for the screens beneath. For `WAKE_GRACE_MS = 600L` after waking, a transparent layer still swallows taps, so a second sleepy tap presses nothing.
9. **Brightness numbers** (provisional, tuned in Task 14): day 70 %, evening 25 %, sliders 5–100 % so an awake screen is never black; window level = percent / 100f. **Asleep and NightClock** use `BRIGHTNESS_OVERRIDE_OFF` (0f, "the lowest value when this window is in front", not off). **Evening** is `ThemeSchedule.isDark` with usable sun times (sunrise before sunset); **with no usable sun times it is never evening** (§4.4: "the daytime level applies"), unlike the theme's 07:00/19:00. Awake at night is always the evening level (decision 8). **The switch at sunrise and sunset fades** linearly over `FADE_MS = 60_000L` in steps of `FADE_STEP_MS = 2_000L`; every other change (waking, sleeping, a slider let go) is at once.
10. **The camera's rule:** `DisplayState.cameraWanted = inFront && night == false && wakes && screen != NIGHT_CLOCK`, where `wakes` is `prefs.wakesByCamera(cameraState)` (the permission granted and the switch on, §4.3). One frame a second is looked at (`LOOK_EVERY_MS = 1_000L`); the rest are closed unread.
11. **`FrameDiff`** (provisional, tuned in Task 14): the frame is averaged down to 32 × 24 luma, read as `byte and 0xFF`; the mean difference from the last frame is taken off every pixel first, so a lamp, a scene or the camera's exposure changing the whole frame isn't movement; a pixel changed by more than `PIXEL_DELTA = 24` counts; movement is at least `CHANGED_SHARE = 0.04f` of the pixels. The first frame after opening is never movement; closing forgets the last frame.
12. **A camera that fails** (`CameraAccessException`, `SecurityException`, `onError`, `onDisconnected`, no front camera) is closed, logged once as `"The camera stopped (${e::class.simpleName})"`, and tried again after `CAMERA_RETRY_MS = 300_000L` while still wanted (§5: "treated as no presence"). No front camera is never retried; Settings says so. `Presence.running` turns false only after `close()` has returned, so the mark never goes while the camera may still be open.
13. **The camera package is sealed:** `Luma`, `FrameSource`, `FrameDiff`, `Camera2Frames`, `Presence` and their Hilt bindings (`display/camera/CameraModule.kt`) are `internal` to `:app`; nothing outside `display/camera/` names `Luma`, `FrameSource`, `ImageReader` or `CameraManager`. The package's one `Log` call is that warning. A source test pins all of it (Task 6).
14. **The camera permission** is asked through `KioskLifecycle.openSystemScreen`, as Choose home app's dialog: unpinned while Android's dialog shows; after the answer, `kiosk.returnToPinning()` (as Google's screens do), and the next resume pins too. Settings is already Admin-only, so no fresh PIN. **`CameraState`**: `NO_CAMERA`, `NOT_ASKED` (never asked, or denied with a rationale), `REFUSED` (denied with no rationale after Culvery asked: Android won't ask again), `ALLOWED`. "Asked" is saved in `display_settings`. The permission is read again on every resume. **Refused** offers Android's app details screen through `ShellNavigator.openSystemScreen` (a fresh Admin PIN, as every system screen).
15. **The switch's default:** `wakeByCamera: Boolean?` is saved only when the Admin flips it; null means `WAKE_BY_CAMERA_DEFAULT = true`. Decision 9's fallback is that constant set to `false` (Task 14), which also changes a tablet that never flipped it.
16. **The doorbell at night:** `DoorAlerts` treats `isNight() != false` like not in front: the ring is stored, no card, no chime; unseen, it shows in the rail badge as today. A card already showing stays until Got it or its 2 minutes (§4.5). Door's quiet hours don't change; the overlap is said once, on Settings › Display's night line.
17. **Heat lines** are all `Log.w` (§4.7 asks warnings for a status above none; the plan logs the battery readings and a status back to none at warning too, because the tablet measurement reads a release build, whose info lines R8 removes). One line at start, one when the thermal status differs from the last one logged, one every `HEAT_EVERY_MS = 900_000L`. **The line also carries the battery level in percent.** `LogHygiene.ALLOWED` gains `heat.status`, `heat.batteryTenths`, `heat.batteryPercent`.
18. **State changes** are logged at `Log.i` (stripped from release) in `DisplayState`: `"Screen ${next.name}"`.
19. **Settings › Display is page id `display`, order 440**: before Music (450), after Calendars (400). `SettingsPage`'s KDoc becomes "Home location 0, People 100, Calendars 400, Display 440, Music 450, Lights 470, Door 500, Tablet lock 900".
20. **Sleep delay choices:** 2, 5, 10, 15 minutes as chips (§4.6), default 5.
21. **The night clock:** `clockText` (`HH:mm`), `NightClockTokens.size = 160.sp`, `NightClockTokens.ink = Color(0xFF5A5A5A)` on `Color.Black` in both themes, centred; nothing else (decision 2). Its semantics merge: its name is the time, its click label "Wake the screen". The dark screen's name is "Screen asleep", its click label "Wake the screen". Provisional for the design walk-through; the first lever if the clock lights the room is a dimmer ink (§4.2).
22. **The camera mark:** the `photo_camera` icon, 20 dp, `Culvery.colors.mute`, 12 dp from the top-right corner, on every screen while `Presence.running` (the dark screen included); not clickable; content description "The camera is on". Provisional (§4.3).
23. **The layers' order (U2):** shell and sheet, alert, the sleep layer, the PIN pad, toasts and warnings, the camera mark. **While Asleep or NightClock, no toast or warning is drawn**: a toast that arrives is held and shown, with its full reading time, when the screen wakes (the newest if several came); only the mark draws over the sleep layer. The PIN pad stays above the sleep layer, but it is a hold, so it wakes the screen first.
24. **Window brightness reaches the window** through `applyBrightness(window, level)`, collected while started. Nothing writes Android's brightness setting and the manifest has no `WRITE_SETTINGS` (§4.4); a source test pins both.

## Global Constraints

**From the spec, verbatim:**

- §1 "**The tablet has no light sensor.** … Brightness therefore follows the time of day, not the room."
- §2.2 "only the time, large and very dim on black. Nothing else (no date, no doorbell dot)." §2.3 "set times in Settings, default 22:00 to 07:00." §2.4 "At night only a tap wakes the wall. The camera is off at night and never wakes it."
- §2.6 "a dark screen after **5 minutes** with nobody around (adjustable in Settings). Walking up, or a tap, wakes it."
- §2.7 "in the day it wakes the screen and shows the door card as usual. At night it is recorded in the Door tab only: the wall stays on the clock and the chime stays silent."
- §2.8 "a daytime level, and a softer level from sunset to sunrise … The softer level also applies when a tap wakes the night clock."
- §2.9 "camera wake-up **off** by default: the day screen still sleeps, and a tap wakes it." §2.10 "logs the tablet's thermal state changes and, now and then, the battery temperature (numbers only)."
- §4.1 the table; "A tap that wakes the screen only wakes it: it is not passed on"; "While Culvery is not in front … none of this runs: no camera, Android's own screen timeout and brightness apply."; "The PIN pad, an open sheet, the door card, Settings and the setup wizard keep the wall awake."; "A toast that arrives while Asleep or on the NightClock waits and shows when the screen next wakes. Nothing draws over the dark screen or the night clock except the camera mark".
- §4.2 "`FLAG_KEEP_SCREEN_ON` kept." "Changing Android's own brightness (`WRITE_SETTINGS` …) stays out of this plan".
- §4.3 "Low resolution and the lowest frame rate the camera offers; … about **once a second** … No face detection, no recognition." "**Nothing leaves the reader:** no frame or image is stored, written to disk, logged, cached or sent. The reader emits only "someone moved" with a timestamp." "A small camera mark shows … whenever the camera is running". "The camera permission is asked once, from Settings › Display, by an Admin. Without it, camera wake-up stays off and Settings says why."
- §4.4 "It never changes Android's brightness setting". "Without the household's location (no sun times), the daytime level applies until it's set."
- §4.6 "Admin only." "Sleep when nobody's around: after [minutes], default 5". "Wake when someone walks up: a switch, on by default once the camera permission is granted".
- §4.7 "one line on each thermal status change … and the battery temperature in tenths of a degree every 15 minutes and on each status change. Numbers only, no names."
- §5 "The camera fails or is taken by another app | Treated as no presence; wakes by tap; one log line with the exception class". "Logs carry state changes and outcomes only, never image data."

**Copy** (the spec fixes the names; the sentences are the plan's, for the end-of-v1 design walk-through; curly apostrophes in code): page "Display"; sections "Night clock", "Brightness", "Sleep"; "From", "Until", "Earlier", "Later"; `nightClockLine(from, until)` = "A dim clock shows from 22:00 until 07:00. Only a tap wakes the screen, and rings wait in the Door tab."; sliders "Daytime" and "Evening, from sunset", spoken "Daytime brightness" / "Evening brightness" with "70 percent"; no sun times: "Until the home location is set, the daytime level applies all day."; "Sleep when nobody’s around", chips "2 minutes" … "15 minutes", spoken "Sleep after 5 minutes"; switch "Wake when someone walks up", line "The front camera looks for movement about once a second. Nothing is recorded or sent."; not asked: "Walking up can wake the screen once Culvery may use the camera." with the button "Allow the camera"; refused: "The camera isn’t allowed, so only a tap wakes the screen. Allow it in Android’s settings for Culvery." with "Open Android’s settings for Culvery"; its missing screen: "Culvery can’t open Android’s settings for Culvery."; no camera: "This tablet has no front camera, so a tap wakes the screen."; the prompt's missing screen: "Culvery can’t ask for the camera on this tablet."; the layers' click label "Wake the screen", the dark screen's name "Screen asleep"; the mark "The camera is on".

**From the house rules:**

- Package root `uk.co.siland.culvery`. `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only. **No new library; never change a version.**
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` from the worktree (or `.\gradlew.bat …`). Never plain `test`, `check` or `build`, except `./gradlew -p build-logic :convention:test` (Task 9 changes `LogHygieneTest`). A task runs its module's tests while working; every task ends with the full gate before its commit. **Never run a Gradle `install…` task**, and no subagent touches a device.
- **Tests that can fail:** every new test fails before its change, and its step says why; a "Fails if" line names the mutation it catches where it isn't plain. A test that passes before its change is labelled **pin**. Timer tests use `TestScope`'s scheduler and never sleep; each checks 1 ms before its boundary and at it. The clock is a `MutableStateFlow<LocalDateTime?>` the test sets. **Any test whose code reaches `android.util.Log` runs under Robolectric** (`DisplayStateTest`, `CameraAccessTest`, `PresenceTest`, `HeatLogTest` among them).
- **Screenshots:** baselines in `app/src/test/screenshots/` (`core/ui`'s for core). Compare (`compareRoborazziDebug`) before re-recording, record (`recordRoborazziDebug`), open every new image, and run Plan 6's baseline check (the step's expected names in `$TMP/expected-baselines.txt`; `git status --porcelain -- "*.png"` compared both ways; both lists empty).
- **Copy** names what the user does; an action keeps its name through its flow; errors say what went wrong and how to fix it; curly quotes and apostrophes; "›", "…" as written. `CopyScanTest` flags straight quotes and the words master, kiosk, sync, read-only, assign, feed; a protocol word never shown goes in `core/ui/src/test/resources/copy-scan-allow.txt`. Sentences end with a full stop; labels don't.
- **Accessibility:** every new screen's test runs `compose.assertClickablesHaveRoleAndLabel()`.
- **Logs:** `LogHygieneTest` scans every module's `main` and `release`. Never a frame, a size read from a frame, or a camera id in a log.
- **Reflection tests** read `declaredMethods`, skip synthetic ones, and compare names up to any `$` (Kotlin mangles `internal` names).
- **Layout numbers are named**, in `DisplayDimens` and `NightClockTokens`.
- **Comments say what the code can't**, one line by default; spec references as `(8 §4.3)`. **No comment names this plan's task or ruling numbers.**
- **Robolectric and waiting:** `compose.waitUntil(5_000) { compose.waitForIdle(); condition() }`, never `runBlocking { flow.first { … } }` after a click.
- **No personal data in files:** no names or addresses in a test or a doc; from the tablet, only battery, temperature and timing figures, in the follow-ups file.
- **Write every file as UTF-8.**
- **Commit messages are a plain sentence in the repo's style and contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Never push. **Never** `git checkout -- .` or `git restore .`.

## Review Focus

The five failure modes the spec implies, most likely to bite the household, that the spec's own test list doesn't cover; each is pinned by a test in its owning task:

1. **A light changing the whole room in an empty room** (the evening Hue scene, a lamp, the sun going behind a cloud, the camera's own exposure). Expected: no wake; only something moving in part of the frame wakes it.
   - Task 6 `FrameDiffTest.aWholeFrameGettingBrighterIsNotMovement` and `FrameDiffTest.sensorNoiseIsNotMovement`.
2. **Culvery leaving the front while asleep with the camera running** (Use other apps, the permission dialog, a crash back to home). Expected: the camera closes, the mark goes only once it has, Android's brightness applies; coming back is Awake with fresh waits.
   - Task 4 `DisplayStateTest.leavingTheFrontWakesAndStopsTheWaits`, `DisplayStateTest.theCameraIsWantedOnlyInTheDayInFront`; Task 6 `PresenceTest.runningIsTrueWheneverTheCameraIsOpen`; Task 8 `ScreenBrightnessTest.notInFrontLeavesAndroidsBrightness`.
3. **A restart or a clock change at night** (a power cut at 02:00; 01:30 twice in late October; night from 01:30 on the day 01:00–01:59 is skipped). Expected: NightClock from the first frame, a ring just after the restart only stored, Awake at 07:00 once.
   - Task 4 `DisplayStateTest.startsAsTheNightClockAtNight`, `nightAcrossMidnightAndTheClocksGoingBack`, `nightStartingInTheSkippedHourStillStarts`; Task 5 `DoorAlertsTest.aRingJustAfterARestartAtNightIsStored`.
4. **The camera taken, failing or switched off in Android's settings while camera wake-up is on.** Expected: no crash, one log line, a tap still wakes, Settings shows why on the next resume, and the camera is tried again later only while it is wanted.
   - Task 6 `PresenceTest.aCameraErrorIsNoPresenceAndOneLine`, `PresenceTest.aFailedCameraIsTriedAgainAfterFiveMinutes`; Task 3 `CameraAccessTest.aPermissionTakenAwayInAndroidsSettingsIsSeenOnResume`.
5. **Sleepy taps and TalkBack on a sleeping screen** (a double tap on the night clock; a TalkBack swipe over the dark screen). Expected: the first tap only wakes, the second within 600 ms presses nothing, and TalkBack can only wake the screen through the layer's own action.
   - Task 10 `SleepLayersTest.aSecondTapWithinTheGraceIsSwallowed`, `whatTheSleepLayerCoversIsHiddenFromTalkBack`; Task 4 `DisplayStateTest.aTouchWhileAsleepDoesNotWake`.

The spec's own tests (§6) each have their task: the state machine's rows, the doorbell both ways, holds, not in front, a waking tap (4, 5, 10, 12); presence only when wanted, frame differencing, the no-image-path guard (4, 6, 7); brightness at sunrise and sunset, no location, the window only (8); heat lines and `LogHygieneTest` (9); the UI with its screenshots (10, 11).

---

## File Structure

```
core/ui/.../Icons.kt, tools/fonts/icons.txt, core/ui/src/main/res/font/material_symbols_rounded.ttf (Task 1)
core/plugin/.../DailyWindow.kt   NightHours.kt (create, Task 2)   Setup.kt (Task 2: the order KDoc)
core/ui/.../Rows.kt (create, Task 2)
capability/door/.../QuietHours.kt, ui/DoorSettingsPage.kt, ui/DoorDimens.kt, DoorWords.kt (Task 2)   DoorAlerts.kt (Task 5)
build-logic/convention/src/test/kotlin/LogHygieneTest.kt (Task 9)
app/build.gradle.kts (Task 3: datastore)   app/src/main/AndroidManifest.xml (Task 7: the camera)
app/src/main/java/uk/co/siland/culvery/
  display/DisplaySettings.kt   display/CameraAccess.kt (Task 3)
  display/DisplayState.kt   display/di/DisplayModule.kt (Task 4; Tasks 9, 11 add to it)
  display/camera/FrameDiff.kt   display/camera/Presence.kt (Task 6)
  display/camera/Camera2Frames.kt   display/camera/CameraModule.kt (Task 7)
  display/ScreenBrightness.kt (Task 8)   shell/ThemeSchedule.kt, shell/ShellViewModel.kt (Task 8: sunToday)
  display/HeatLog.kt (Task 9)
  display/ui/SleepLayers.kt   display/ui/DisplayDimens.kt   shell/ui/OverlayLayers.kt (Task 10)
  display/ui/DisplaySettingsPage.kt   display/DisplayWords.kt (Task 11)
  display/CameraPrompt.kt   MainActivity.kt (Task 12)
app/src/test/java/uk/co/siland/culvery/display/
  DisplaySettingsTest.kt   CameraAccessTest.kt (Task 3)   DisplayStateTest.kt (Task 4)
  camera/FrameDiffTest.kt   camera/PresenceTest.kt   camera/CameraSealTest.kt (Task 6)   camera/Camera2FramesTest.kt (Task 7)
  ScreenBrightnessTest.kt   BrightnessSourceTest.kt (Task 8)   HeatLogTest.kt (Task 9)
  ui/SleepLayersTest.kt   ui/SleepLayersScreenshotTest.kt (Task 10)   ui/DisplaySettingsPageTest.kt   ui/DisplaySettingsScreenshotTest.kt (Task 11)
  DisplayWiringTest.kt (Task 12)
docs/superpowers/plans/2026-09-23-plan1-followups.md (Tasks 13–15)
```

`…` stands for the module's package directory.

---

### Task 1: The camera icon (ruling 22)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Icons.kt`, `tools/fonts/icons.txt`, `core/ui/src/main/res/font/material_symbols_rounded.ttf` (regenerated); maybe `IconFontTest.kt` (`NO_FILLED_FORM`)

**Interfaces:**
- Produces: `Icons.PHOTO_CAMERA = "photo_camera"`.

- [ ] **Step 1: The failing test** — add the icon to `Icons` and `icons.txt` in alphabetical order. Run: `./gradlew :core:ui:testDebugUnitTest --tests '*IconFontTest*'` — Expected: FAIL (the font lacks `photo_camera`).
- [ ] **Step 2: The subset** — `tools/fonts/README.md` steps 3–4: `git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$TMP/material-symbols-full.ttf"`, then `python tools/fonts/subset.py --source "$TMP/material-symbols-full.ttf"` (fontTools via `python -m pip install --user fonttools` if missing). If the script says the source lacks it, step 3 needs Google's current font: **a download — stop and ask the user**, naming the file, source and size. If `everyNamedIconKeepsItsFilledForm` names it, add it to `NO_FILLED_FORM`.
- [ ] **Step 3: The gate and commit** — Expected: PASS, **no image changed**.
```bash
git add core/ui tools/fonts/icons.txt
git commit -m "Add the camera icon"
```

---

### Task 2: Core pieces: the daily window, the clock text and the switch and time rows, `NightHours` (rulings 1–3, 19)

**Review:** sonnet.

**Files:**
- Create: `core/plugin/.../DailyWindow.kt`, `core/plugin/.../NightHours.kt`, `core/ui/.../Rows.kt`; tests `core/plugin/src/test/.../DailyWindowTest.kt`, `core/ui/src/test/.../RowsTest.kt`
- Modify: `capability/door/.../QuietHours.kt` (`isQuiet` calls `inDailyWindow`), `ui/DoorSettingsPage.kt` (its `SwitchRow`, `StepperRow` deleted; calls the core rows), `ui/DoorDimens.kt` (`QUIET_STEP_MINUTES` gone), `DoorWords.kt` (`EARLIER`, `LATER`, `clockText` and its formatter gone; Door imports core's); `core/plugin/.../Setup.kt` (ruling 19's KDoc)

**Interfaces:**
- Produces (`uk.co.siland.culvery.core.plugin`): `fun inDailyWindow(now: LocalTime, from: LocalTime, until: LocalTime): Boolean`; `interface NightHours { fun isNight(): Boolean? }` (KDoc: whether the night clock's hours are now on the household's clock, null while that isn't known yet; 8 §4.5).
- Produces (`uk.co.siland.culvery.core.ui`): `fun clockText(time: LocalTime): String` (`HH:mm`); `const val EARLIER = "Earlier"`, `const val LATER = "Later"`, `const val TIME_STEP_MINUTES = 30L`; `@Composable fun HhSwitchRow(title: String, line: String?, checked: Boolean, tag: String, onChange: (Boolean) -> Unit)`; `@Composable fun HhTimeStepperRow(label: String, subject: String, time: LocalTime, tag: String, onTime: (LocalTime) -> Unit)` (tags `${tag}_earlier`, `${tag}_later`; spoken "Earlier, [subject]").

- [ ] **Step 1: The failing tests** — move `QuietHoursTest`'s `isQuiet` cases into `DailyWindowTest.kt` (`aWindowWithinTheDay`, `aWindowAcrossMidnight`, `equalTimesAreNeverInside`, each a minute before and at both ends); Door's `QuietHoursTest` keeps `quietAt`'s cases; Door's `clockText` cases move to `RowsTest`. `RowsTest.kt` (Robolectric): `aSwitchRowIsOneSwitch` (`Role.Switch`, toggling calls `onChange(true)`), `theStepperStepsByHalfAnHour` (22:00 → `_earlier` gives 21:30, `_later` 22:30; the subtitle "22:00"; spoken "Earlier, night clock start"), each with `assertClickablesHaveRoleAndLabel()`. Run: `./gradlew :core:ui:testDebugUnitTest :core:plugin:testDebugUnitTest` — Expected: FAIL to compile.
- [ ] **Step 2: Move the code** — unchanged in look; Door's callers pass the same tags. `NightHours` with its KDoc only.
- [ ] **Step 3: Run them, then the gate** — `./gradlew :core:ui:testDebugUnitTest :core:plugin:testDebugUnitTest :capability:door:testDebugUnitTest`, then the gate — Expected: PASS, **no image changed**.
```bash
git add core capability/door
git commit -m "Move the daily time window, the clock text and the switch and time rows into core, and add the night hours"
```

---

### Task 3: `DisplaySettings` and `CameraAccess` (§4.3, §4.6; rulings 7, 9, 14, 15, 20; Review Focus 4)

**Review:** sonnet.

**Files:**
- Modify: `app/build.gradle.kts` (`implementation(libs.androidx.datastore.preferences)`)
- Create: `app/.../display/DisplaySettings.kt`, `display/CameraAccess.kt`; tests `display/DisplaySettingsTest.kt`, `display/CameraAccessTest.kt`

**Interfaces:**
- Produces (package `uk.co.siland.culvery.display`):
  - `data class DisplayPrefs(val nightFrom: LocalTime = LocalTime.of(22, 0), val nightUntil: LocalTime = LocalTime.of(7, 0), val dayPercent: Int = 70, val eveningPercent: Int = 25, val sleepAfterMinutes: Int = 5, val wakeByCamera: Boolean? = null, val cameraAsked: Boolean = false)`
  - `val SLEEP_CHOICES = listOf(2, 5, 10, 15)`; `val BRIGHTNESS_RANGE = 5..100`; `const val WAKE_BY_CAMERA_DEFAULT = true`
  - `class DisplaySettings(store: DataStore<Preferences>, scope: CoroutineScope)` (+ `@Inject` on file `display_settings`): `prefs: Flow<DisplayPrefs>`, `read: StateFlow<DisplayPrefs?>` (null until the file is first read), `current: StateFlow<DisplayPrefs>` (the defaults until then), `setNight(from: LocalTime, until: LocalTime)`, `setDayPercent(p: Int)`, `setEveningPercent(p: Int)`, `setSleepAfter(minutes: Int)`, `setWakeByCamera(on: Boolean)`, `setCameraAsked()`; `internal fun displaySettingsStore(scope, produceFile)` (Door's shape: a corrupt file is the defaults; writes throw `IOException`)
  - `enum class CameraState { NO_CAMERA, NOT_ASKED, REFUSED, ALLOWED }`
  - `fun cameraState(hasFrontCamera: Boolean, granted: Boolean, asked: Boolean, rationale: Boolean): CameraState`
  - `fun DisplayPrefs.wakesByCamera(state: CameraState): Boolean` = `state == ALLOWED && (wakeByCamera ?: WAKE_BY_CAMERA_DEFAULT)`
  - `@Singleton class CameraAccess internal constructor(hasFrontCamera: () -> Boolean, granted: () -> Boolean, settings: DisplaySettings, scope: CoroutineScope)` (+ `@Inject` from the application context: `PackageManager.FEATURE_CAMERA_FRONT`, `ContextCompat.checkSelfPermission(CAMERA)`): `state: StateFlow<CameraState>` = `combine(settings.current, permission) { … }.stateIn(…)` over a `MutableStateFlow(granted to rationale)`; `fun refresh(rationale: Boolean)`; `fun ask()`; `val requests: Flow<Unit>` (a `Channel`, for the activity); `suspend fun answered(granted: Boolean, rationale: Boolean)` (saves `cameraAsked`, then refreshes)

- [ ] **Step 1: Write the failing tests** (both Robolectric)

`DisplaySettingsTest.kt` (a `TemporaryFolder` store): `theDefaults` (22:00, 07:00, 70, 25, 5, null, false); `readIsNullUntilTheFileIsRead`; `everySettingIsKept` (each setter round-trips through a new store on the same file); `aFileThatCantBeReadIsTheDefaults`; `percentsAreKeptInRange` (`setDayPercent(0)` → 5, `setEveningPercent(120)` → 100); `onlyTheOfferedDelaysAreKept` (`setSleepAfter(7)` throws `IllegalArgumentException`).
`CameraAccessTest.kt`:
```kotlin
/** 8 §4.3: Android won't ask again after a denial with no rationale. Fails if a denial without a rationale reads as NOT_ASKED. */
@Test
fun aDenialWithoutARationaleAfterAskingIsRefused() {
    assertThat(cameraState(hasFrontCamera = true, granted = false, asked = false, rationale = false)).isEqualTo(CameraState.NOT_ASKED)
    assertThat(cameraState(true, granted = false, asked = true, rationale = true)).isEqualTo(CameraState.NOT_ASKED)
    assertThat(cameraState(true, granted = false, asked = true, rationale = false)).isEqualTo(CameraState.REFUSED)
    assertThat(cameraState(true, granted = true, asked = true, rationale = false)).isEqualTo(CameraState.ALLOWED)
    assertThat(cameraState(false, granted = true, asked = true, rationale = false)).isEqualTo(CameraState.NO_CAMERA)
}
```
`theSwitchIsOnByDefaultOnlyOnceAllowed` (`DisplayPrefs().wakesByCamera(ALLOWED)` true, `(NOT_ASKED)` false, `DisplayPrefs(wakeByCamera = false).wakesByCamera(ALLOWED)` false); `askSendsOneRequest` (Turbine on `requests`); `answeredSavesThatCulveryAsked` (`state.first { it == REFUSED }` after `answered(false, false)`); `aPermissionTakenAwayInAndroidsSettingsIsSeenOnResume` (Review Focus 4: `state.first { it == ALLOWED }`; the fake turns false; `refresh(rationale = false)` → `state.first { it == REFUSED }`, as `cameraAsked` is saved).
Run: `./gradlew :app:testDebugUnitTest --tests '*DisplaySettingsTest*' --tests '*CameraAccessTest*'` — Expected: FAIL to compile.

- [ ] **Step 2: Implement** `DoorSettings`' shape (minutes of the day as int keys, as quiet hours); `read` is `prefs.stateIn(scope, Eagerly, null)`.

- [ ] **Step 3: Run them, the gate, commit**
```bash
git add app
git commit -m "Keep the display's settings, and tell whether Culvery may use the front camera"
```

---

### Task 4: `DisplayState`: the three states, the timers, the hold, the camera wanted (§4.1, §4.3, §5; rulings 2, 4–7, 10, 18; Review Focus 2, 3, 5)

**Review:** opus.

**Files:**
- Create: `app/.../display/DisplayState.kt`, `display/di/DisplayModule.kt` (`@Binds nightHours(DisplayState): NightHours`, so Task 5's `DoorAlerts` can be injected); test `display/DisplayStateTest.kt`

**Interfaces:**
- Consumes: `DisplaySettings.read`, `CameraAccess.state`, `wakesByCamera` (Task 3); `inDailyWindow`, `NightHours` (Task 2); `HouseholdClock.now`, `UserActivity.touches`, `AlertHost.inFront`, `ApplicationScope`.
- Produces:
  - `enum class Screen { AWAKE, ASLEEP, NIGHT_CLOCK }`; `const val NIGHT_AWAKE_MS = 60_000L`
  - `@Singleton class DisplayState internal constructor(prefs: StateFlow<DisplayPrefs?>, now: StateFlow<LocalDateTime?>, touches: Flow<Long>, inFront: StateFlow<Boolean>, camera: StateFlow<CameraState>, scope: CoroutineScope) : NightHours` (+ `@Inject` from `DisplaySettings`, `HouseholdClock`, `UserActivity`, `AlertHost`, `CameraAccess`, the application scope): `screen: StateFlow<Screen>` (its first value computed in the constructor), `override fun isNight(): Boolean?` (from `now.value` and `prefs.value` when called), `cameraWanted: StateFlow<Boolean>`, `fun tapped()`, `fun moved()`, `fun held(on: Boolean)`

- [ ] **Step 1: Write the failing tests** (Robolectric for `Log`; `runTest`, the state on `backgroundScope`, `now` starting 2026-10-19T12:00, prefs read, `inFront` true, camera ALLOWED; every timer test checks 1 ms before and at its boundary)
- `startsAwake`; `startsAsTheNightClockAtNight` (Review Focus 3, ruling 7): built at 23:00 → `screen.value` NIGHT_CLOCK before any time passes, `isNight()` true; `nothingKnownYetIsAwakeAndNotKnown` (prefs or `now` null → AWAKE, `isNight()` null).
- `fiveMinutesWithNobodyAroundSleeps` (§4.1 row 1): AWAKE at 299 999 ms, ASLEEP at 300 000.
- `aTouchOrMovementRestartsTheWait`: a touch at 200 s → ASLEEP at 500 000, not 499 999; the same with `moved()`.
- `theDelayFollowsSettings` (2 minutes: ASLEEP at 120 000); `aChangedDelayStartsTheWaitAgain`.
- `movementOrATapWakesTheDarkScreen` (row 2), each separately.
- `aTouchWhileAsleepDoesNotWake` (ruling 5, Review Focus 5): ASLEEP, a touch → ASLEEP; the same on the NIGHT_CLOCK.
- `theHoldStartingWakesTheDarkScreen` (row 3, ruling 4): ASLEEP, `held(true)` → AWAKE; 30 minutes on, AWAKE; `held(false)` → ASLEEP 300 000 later.
- `nightStartsAtTwentyTwo` (row 4): 21:59 → AWAKE; 22:00 → NIGHT_CLOCK, from AWAKE and from ASLEEP.
- `aTapAtNightWakesForOneMinute` (rows 5, 6): `tapped()` → AWAKE; touches restart; NIGHT_CLOCK 60 000 after the last touch, not 59 999.
- `movementNeverWakesTheNightClock` (decision 4).
- `aHoldAtNightKeepsItAwakeThenTheClockFollowsAMinuteLater` (row 6).
- `aHoldAsNightStartsStaysThenTheClockFollows` (§4.5, the door card): held at 21:59; 22:00 → AWAKE; released → NIGHT_CLOCK 60 000 later.
- `nightEndingWakesTheWall` (row 8): NIGHT_CLOCK, 07:00 → AWAKE, ASLEEP 300 000 later.
- `nightAcrossMidnightAndTheClocksGoingBack` (**pin**, Review Focus 3): 23:59, 00:00, 01:30, 01:30 again, 06:59 → NIGHT_CLOCK each; 07:00 → AWAKE.
- `nightStartingInTheSkippedHourStillStarts` (Review Focus 3): night from 01:30; ticks 00:59 then 02:00 → NIGHT_CLOCK.
- `notInFrontNothingRuns` (§4.1): `inFront` false → AWAKE; 30 minutes and a 22:00 tick → AWAKE; `tapped()`, `moved()` change nothing.
- `leavingTheFrontWakesAndStopsTheWaits` (Review Focus 2): ASLEEP, `inFront` false → AWAKE; back → ASLEEP 300 000 after the return; at night, back → AWAKE then NIGHT_CLOCK 60 000 later.
- `changedNightTimesApplyAtOnce`: 21:00; prefs with `nightFrom = 20:00` → NIGHT_CLOCK at once; held meanwhile → AWAKE until 60 000 after release.
- `equalNightTimesMeanNoNight` (ruling 2): both 22:00; 23:00 → AWAKE, `isNight()` false.
- `theCameraIsWantedOnlyInTheDayInFront` (§4.3, ruling 10, Review Focus 2): `cameraWanted` true AWAKE and ASLEEP by day; false on the NIGHT_CLOCK, at night while AWAKE, not in front, with `wakeByCamera = false`, with the camera NOT_ASKED, REFUSED or NO_CAMERA, and while `isNight()` is null.
Run: `./gradlew :app:testDebugUnitTest --tests '*DisplayStateTest*'` — Expected: FAIL to compile.

- [ ] **Step 2: Implement** — the first value from a pure `fun next(…)` over the current inputs; one coroutine on `scope` re-deciding on each input, with a `collectLatest` per change of "what the wait is", restarted by activity through a `MutableSharedFlow<Unit>` (touches feed it only while AWAKE). `Log.i(TAG, "Screen ${next.name}")` on each change.

- [ ] **Step 3: Run them, the gate, commit**
```bash
git add app
git commit -m "Decide when the wall is awake, asleep or showing the night clock, and when the camera may look"
```

---

### Task 5: The doorbell at night (§2.7, §4.5; rulings 7, 16; Review Focus 3)

**Review:** sonnet.

**Files:**
- Modify: `capability/door/.../DoorAlerts.kt`; test `capability/door/src/test/.../DoorAlertsTest.kt`

**Interfaces:**
- Consumes: `NightHours` (Task 2; bound in Task 4).
- Produces: `DoorAlerts` internal constructor gains a last parameter `night: () -> Boolean = { false }`; the `@Inject` constructor takes `night: NightHours` and passes `{ night.isNight() != false }`.

- [ ] **Step 1: Write the failing tests** (the `alerts()` helper taking `night`)
- `aRingAtNightIsStoredWithoutACardOrAChime` (§2.7): night, in front → no `host.show`, no chime, `seen` empty (so the badge counts it).
- `aRingJustAfterARestartAtNightIsStored` (ruling 7): the `@Inject` constructor's mapping with a `NightHours` answering null → as night. Fails if null reads as day.
- `aCardShowingWhenNightStartsStaysUntilItCloses` (§4.5, **pin**); `aRingAfterNightEndsAlertsAsUsual` (**pin**).
Run: `./gradlew :capability:door:testDebugUnitTest --tests '*DoorAlertsTest*'` — Expected: FAIL (the first two).
- [ ] **Step 2: Implement** `if (!host.inFront.value || night()) return@withLock`; the class KDoc's "anything while Culvery isn't in front" becomes "… isn't in front, or at night (8 §2.7)".
- [ ] **Step 3: Run them, the gate, commit**
```bash
git add capability/door
git commit -m "Keep a ring at night in the Door tab, with no card and no chime"
```

---

### Task 6: `FrameDiff`, `Presence` and the sealed camera package (§4.3, §5; rulings 10–13; Review Focus 1, 2, 4)

**Review:** opus.

**Files:**
- Create: `app/.../display/camera/FrameDiff.kt`, `display/camera/Presence.kt` (nothing binds `Presence` until Task 7 brings its `FrameSource`); tests `display/camera/FrameDiffTest.kt`, `display/camera/PresenceTest.kt`, `display/camera/CameraSealTest.kt`

**Interfaces:**
- Consumes: `DisplayState.cameraWanted`, `moved()` (Task 4); `WallClock`, `Startable`.
- Produces (package `uk.co.siland.culvery.display.camera`, all `internal`):
  - `class Luma(val width: Int, val height: Int, val values: ByteArray)` (greyscale, read `and 0xFF`)
  - `const val DIFF_WIDTH = 32`, `DIFF_HEIGHT = 24`, `PIXEL_DELTA = 24`, `CHANGED_SHARE = 0.04f`, `LOOK_EVERY_MS = 1_000L`, `CAMERA_RETRY_MS = 300_000L`
  - `class FrameDiff { fun moved(frame: Luma): Boolean; fun reset() }`
  - `class NoFrontCamera : Exception()`; `class CameraStopped : Exception()`
  - `fun interface FrameSource { fun open(onFrame: (Luma) -> Unit, onFailed: (Exception) -> Unit): AutoCloseable }`
  - `@Singleton class Presence internal constructor(frames: FrameSource, clock: WallClock, wanted: StateFlow<Boolean>, moved: () -> Unit, scope: CoroutineScope, diff: FrameDiff = FrameDiff()) : Startable` (+ `@Inject` from `FrameSource`, `WallClock`, `DisplayState`, the application scope): `running: StateFlow<Boolean>`

- [ ] **Step 1: Write the failing tests**

`FrameDiffTest.kt` (plain JVM; 32 × 24 frames from a helper `frame { x, y -> value }` that clamps to 0–255):
- `theFirstFrameIsNeverMovement`; `identicalFramesAreStill`; `aShapeMovingIsMovement` (a 6 × 8 block of 150 on 60 moves 6 pixels → true).
- `aWholeFrameGettingBrighterIsNotMovement` (Review Focus 1): every pixel +80 → false; +80 with the block moved → true.
- `sensorNoiseIsNotMovement` (±10 seeded noise → false); `aSmallChangeBelowTheShareIsNotMovement` (25 pixels changed by 100 → false; 31 → true); `valuesAboveOneTwentySevenAreRead` (a block of 200 moving is movement: fails if bytes are read signed); `resetForgetsTheLastFrame`.
`PresenceTest.kt` (Robolectric for `ShadowLog`; `runTest`; a `FakeFrameSource` with `isOpen`, frames on demand, and a close that records `running.value` at the moment it is called):
- `nothingOpensUntilWanted`; `wantedOpensOnce`; `notWantedCloses`.
- `runningIsTrueWheneverTheCameraIsOpen` (ruling 12, Review Focus 2): after each step `running.value == source.isOpen`; at the moment of `close()`, `running` is still true.
- `onlyOneFrameASecondIsLookedAt`: frames at 0, 300, 700, 1 000 ms; `moved` reachable only from 0 and 1 000 (a moving pair at 300/700 doesn't call it).
- `movementCallsMovedOnce`; `closingForgetsTheLastFrame` (reopen; the first frame isn't movement).
- `aCameraErrorIsNoPresenceAndOneLine` (Review Focus 4): `onFailed(CameraStopped())` → closed, `running` false, exactly one `W` line "The camera stopped (CameraStopped)".
- `aFailedCameraIsTriedAgainAfterFiveMinutes`: no open at 299 999 ms, one at 300 000; not wanted meanwhile → none; `noFrontCameraIsNeverRetried`.
`CameraSealTest.kt` (plain JVM over `app/src/main`):
- `thePresenceCodeHasNoFileNetworkOrImagePath` (§4.3): the files of `display/camera/` plus `display/DisplayState.kt`; `containsAtLeast("FrameDiff.kt", "Presence.kt")`; none contains any of `java.io`, `java.nio.file`, `java.net`, `android.net`, `okhttp`, `writeBytes`, `writeText`, `contentResolver`, `ParcelFileDescriptor`, `Bitmap`, `compress(`, `YuvImage`, `ImageFormat.JPEG`, `MediaStore`, `ImageWriter`, `MediaRecorder`, `openFileOutput`, `cacheDir`, `filesDir`, `getExternal`, `DataStore`, `SharedPreferences`, `Socket`, `Intent(`, `sendBroadcast`. Fails today: no files.
- `theCameraTypesStayInTheirPackage` (ruling 13): no `app/src/main` file outside `display/camera/` contains `Luma`, `FrameSource`, `ImageReader` or `CameraManager`.
- `theCameraPackageLogsOnlyTheOneWarning`: its `Log.` calls are exactly one, `Log.w(TAG, "The camera stopped (${e::class.simpleName})")`.
- `everyCameraTypeIsInternal`: each top-level `class`, `interface`, `object`, `fun` and `val` declared in `display/camera/` starts with `internal` (or `private`).
Run: `./gradlew :app:testDebugUnitTest --tests '*FrameDiffTest*' --tests '*PresenceTest*' --tests '*CameraSealTest*'` — Expected: FAIL to compile.

- [ ] **Step 2: Implement**

`FrameDiff.moved`: shift = `mean(frame) − mean(last)`; a pixel counts when `|v − last − shift| > PIXEL_DELTA`; moved when `count ≥ CHANGED_SHARE × pixels`; then `last = frame`. `Presence.start()` collects `wanted` (opening or closing on a change); `onFrame` drops a frame within `LOOK_EVERY_MS` of the last looked at; a close calls `close()`, then `diff.reset()`, then `running = false`; a failure closes, logs, and (unless `NoFrontCamera`) launches one `delay(CAMERA_RETRY_MS)` retry cancelled when no longer wanted. `FrameDiff` runs on the camera's thread; only the call to `moved` crosses into `scope`.

- [ ] **Step 3: Run them, the gate, commit**
```bash
git add app
git commit -m "Look for movement with the front camera once a second while the display wants it, keeping no image"
```

---

### Task 7: `Camera2Frames`: the front camera, small and slow (§4.3, §5; rulings 12, 13)

**Review:** opus.

**Files:**
- Create: `app/.../display/camera/Camera2Frames.kt`, `display/camera/CameraModule.kt` (`@Binds frames(Camera2Frames): FrameSource`, `@Binds @IntoSet presence(Presence): Startable`); test `display/camera/Camera2FramesTest.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`<uses-permission android:name="android.permission.CAMERA" />`, `<uses-feature android:name="android.hardware.camera.front" android:required="false" />`), `CameraSealTest` (`containsAtLeast` gains `"Camera2Frames.kt"`)

**Interfaces:**
- Consumes: `FrameSource`, `Luma`, `NoFrontCamera`, `CameraStopped`, `DIFF_WIDTH`, `DIFF_HEIGHT` (Task 6).
- Produces (internal): `class Camera2Frames internal constructor(context: Context, executor: Executor) : FrameSource` (+ `@Inject` from the application context, with a single-thread executor); `fun smallestSize(sizes: List<Pair<Int, Int>>): Pair<Int, Int>` (smallest by area at least 32 × 24); `fun slowestRange(ranges: List<IntRange>): IntRange` (lowest upper bound, then lowest lower); `fun lumaOf(plane: ByteBuffer, rowStride: Int, pixelStride: Int, width: Int, height: Int): Luma` (block-averages the Y plane to 32 × 24).

- [ ] **Step 1: Write the failing tests** (`Camera2FramesTest.kt`; the executor is direct)
- `theSmallestSizeIsPicked` (`[(1920,1080), (320,240), (176,144), (16,12)]` → `(176,144)`); `theSlowestRateIsPicked` (`[15..30, 7..15, 30..30]` → `7..15`).
- `lumaReadsThePlaneWithItsStrides`: 64 × 48, `rowStride = 72`, `pixelStride = 1`, left half 0, right half 200 → columns 0–15 at 0, 16–31 at 200; and a `pixelStride = 2` case.
- `noFrontCameraFailsAtOnce` (Robolectric `ShadowCameraManager` with one back camera): `onFailed(NoFrontCamera)`, no frame.
- `closingBeforeTheCameraOpensClosesItWhenItDoes` (a fake front camera): `open` → `close()` before `onOpened` → when it opens, the device is closed at once; `close()` again throws nothing.
Run: `./gradlew :app:testDebugUnitTest --tests '*Camera2FramesTest*'` — Expected: FAIL to compile.

- [ ] **Step 2: Implement** — `CameraManager.openCamera(id, executor, StateCallback)` for the first `LENS_FACING_FRONT` id; a `HandlerThread("CulveryPresence")` for `ImageReader.newInstance(w, h, YUV_420_888, 2)` at `smallestSize`; a repeating `TEMPLATE_PREVIEW` request with `CONTROL_AE_TARGET_FPS_RANGE = slowestRange(…)`; the session by `SessionConfiguration(SESSION_REGULAR, listOf(OutputConfiguration(surface)), executor, …)`. Each image: `acquireLatestImage()`, `lumaOf(planes[0] …)`, `close()` in `finally`, then `onFrame`. `onError`/`onDisconnected`/a thrown `CameraAccessException` or `SecurityException` → close everything, then `onFailed`. `close()` is idempotent and remembered, so a late `onOpened` closes the device. No `Log` call.

- [ ] **Step 3: Run them, the gate, commit**
```bash
git add app
git commit -m "Read the front camera at its smallest size and slowest rate, as a small greyscale frame"
```

---

### Task 8: `ScreenBrightness`: the levels, the fade, the window only (§2.8, §4.2, §4.4; rulings 9, 24; Review Focus 2)

**Review:** opus.

**Files:**
- Create: `app/.../display/ScreenBrightness.kt`; tests `display/ScreenBrightnessTest.kt`, `display/BrightnessSourceTest.kt`
- Modify: `shell/ThemeSchedule.kt` (`internal fun sunToday(daylight: Optional<Daylight>): Flow<SunTimes?>`, moved from `ShellViewModel` with its retry and `onStart { emit(null) }`), `shell/ShellViewModel.kt` (calls it)

**Interfaces:**
- Consumes: `Screen`, `DisplayState.screen`, `isNight()` (Task 4); `DisplaySettings.current` (Task 3); `Daylight`, `HouseholdClock.now`, `AlertHost.inFront`, `ThemeSchedule.isDark`.
- Produces: `const val LOWEST = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF`, `const val ANDROIDS = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE`, `const val FADE_MS = 60_000L`, `const val FADE_STEP_MS = 2_000L`; `fun isEvening(now: LocalTime, sun: SunTimes?): Boolean` (`isDark` with usable sun times, else false); `fun targetBrightness(screen: Screen, inFront: Boolean, night: Boolean, evening: Boolean, prefs: DisplayPrefs): Float`; `@Singleton class ScreenBrightness internal constructor(screen: StateFlow<Screen>, night: () -> Boolean?, inFront: StateFlow<Boolean>, now: Flow<LocalDateTime?>, sun: Flow<SunTimes?>, prefs: StateFlow<DisplayPrefs>, scope: CoroutineScope)` (+ `@Inject` from `DisplayState`, `AlertHost`, `HouseholdClock`, `Optional<Daylight>`, `DisplaySettings`, the application scope): `level: StateFlow<Float>`; `fun applyBrightness(window: Window, level: Float)`.

- [ ] **Step 1: Write the failing tests** (`ScreenBrightnessTest.kt`)
- `eveningIsTheThemesDarkWithUsableSunTimes` (§2.8): sun 07:12–18:05 → false at 12:00, true at 18:05; `noUsableSunTimesIsNeverEvening` (§4.4): null, and a sunrise after sunset, both false at 23:00.
- `asleepAndTheNightClockAreTheLowest`; `awakeIsTheDayOrEveningLevel` (0.70f, 0.25f); `aTapAtNightGivesTheEveningLevel` (decision 8: AWAKE, night, not evening → 0.25f).
- `notInFrontLeavesAndroidsBrightness` (Review Focus 2): any screen → `ANDROIDS`.
- `theSwitchAtSunsetFadesOverAMinute` (`runTest`): AWAKE, a tick at 18:05 → 0.70 at 0 ms, between 0.46 and 0.49 at 30 000, above 0.25 at 58 000, 0.25 at 60 000.
- `wakingAndSleepingAreAtOnce`; `aSliderChangeIsAtOnce` (70 → 40 gives 0.40 at once).
- `theLevelIsSetOnCulverysWindow` (Robolectric `Activity`): `applyBrightness(window, 0.25f)` → `window.attributes.screenBrightness == 0.25f`.
`BrightnessSourceTest.kt` (**pin**, ruling 24): no `app` main source contains `SCREEN_BRIGHTNESS` or `Settings.System.put`; the manifest has no `WRITE_SETTINGS`.
Run: `./gradlew :app:testDebugUnitTest --tests '*ScreenBrightnessTest*' --tests '*BrightnessSourceTest*' --tests '*ShellViewModelTest*'` — Expected: FAIL to compile; `ShellViewModelTest` passes.

- [ ] **Step 2: Implement** — `level` a `MutableStateFlow` driven by one collector of the inputs → (target, cause); "evening flipped while AWAKE and in front" fades in `FADE_STEP_MS` steps under `collectLatest`; any other change is set at once.

- [ ] **Step 3: Run them, the gate, commit**
```bash
git add app
git commit -m "Set Culvery's own window to a daytime or evening brightness by the sun, and to its lowest while asleep"
```

---

### Task 9: `HeatLog` (§4.7; ruling 17)

**Review:** sonnet.

**Files:**
- Create: `app/.../display/HeatLog.kt`; test `display/HeatLogTest.kt`
- Modify: `display/di/DisplayModule.kt` (`@Binds tabletHeat(AndroidTabletHeat): TabletHeat`, `@Binds @IntoSet heatLog(HeatLog): Startable`); `build-logic/convention/src/test/kotlin/LogHygieneTest.kt` (`ALLOWED` gains `"heat.status", "heat.batteryTenths", "heat.batteryPercent"`, KDoc: "`heat.*` are the tablet's thermal status and battery numbers (8 §4.7).")

**Interfaces:**
- Produces: `data class HeatReading(val status: Int, val batteryTenths: Int?, val batteryPercent: Int?)`; `internal fun readingOf(status: Int, battery: Intent?): HeatReading` (`EXTRA_TEMPERATURE`; `EXTRA_LEVEL` × 100 / `EXTRA_SCALE`; a missing extra or intent is null); `interface TabletHeat { fun reading(): HeatReading; fun onStatusChange(listener: () -> Unit): AutoCloseable }`; `class AndroidTabletHeat @Inject constructor(@ApplicationContext context: Context) : TabletHeat` (`PowerManager.currentThermalStatus`, `addThermalStatusListener(executor, …)`, the sticky battery intent by `ContextCompat.registerReceiver(context, null, filter, RECEIVER_NOT_EXPORTED)`, then `readingOf`); `const val HEAT_EVERY_MS = 900_000L`; `class HeatLog internal constructor(tablet: TabletHeat, scope: CoroutineScope) : Startable` (+ `@Inject`).

- [ ] **Step 1: Write the failing tests** (`HeatLogTest.kt`, Robolectric, `runTest`, a fake `TabletHeat`)
- `oneLineAtStart`; `aStatusChangeLogsOneLineWithTheBattery` (§4.7); `aRepeatedStatusIsNotLoggedAgain` (the listener fires with the same status → no line).
- `theBatteryIsLoggedEveryFifteenMinutes`: none more at 899 999 ms; one at 900 000; two at 1 800 000.
- `theLinesAreWarningsWithNumbersOnly`: `Log.WARN`, tag `HeatLog`, matching `^Heat: thermal \d+, battery (\d+|null) tenths of a degree, (\d+|null) percent$`.
- `readingOfReadsTheBatteryIntent`: a built `Intent` with temperature 312, level 45, scale 100 → `HeatReading(2, 312, 45)` for status 2; a null intent → `HeatReading(2, null, null)`.
`LogHygieneTest` as it is (**pin**).
Run: `./gradlew :app:testDebugUnitTest --tests '*HeatLogTest*'` and `./gradlew -p build-logic :convention:test` — Expected: FAIL to compile; then `LogHygieneTest` fails until `ALLOWED` has the three.
- [ ] **Step 2: Implement** — each line `Log.w(TAG, "Heat: thermal ${heat.status}, battery ${heat.batteryTenths} tenths of a degree, ${heat.batteryPercent} percent")` with `val heat = tablet.reading()`; the last logged status kept. The registration itself is untested (the tablet shows it).
- [ ] **Step 3: Run them, the gate, commit**
```bash
git add app build-logic
git commit -m "Log the tablet's thermal status and battery temperature as numbers, on each change and every 15 minutes"
```

---

### Task 10: The dark screen, the night clock, the camera mark and held toasts in the shell's layers (§2.2, §4.1–4.3; rulings 8, 21–23; Review Focus 5)

**Review:** sonnet.

**Files:**
- Create: `app/.../display/ui/SleepLayers.kt`, `display/ui/DisplayDimens.kt`; tests `display/ui/SleepLayersTest.kt`, `display/ui/SleepLayersScreenshotTest.kt`
- Modify: `shell/ui/OverlayLayers.kt` (`ShellLayers` gains `sleeping: Boolean = false`, `sleep: @Composable () -> Unit = {}` after the alert and before the PIN pad, outside the counting box, the shell, sheet and alert cleared from semantics while `sleeping`; toasts and warnings not drawn while `sleeping`; `cameraMark: Boolean = false` drawn last)

**Interfaces:**
- Consumes: `Screen` (Task 4); `Icons.PHOTO_CAMERA` (Task 1); `clockText` (Task 2).
- Produces: `object NightClockTokens { val size; val ink }`; `object DisplayDimens { val markSize = 20.dp; val markInset = 12.dp }`; `const val WAKE_GRACE_MS = 600L`; `@Composable fun SleepLayer(screen: Screen, time: () -> LocalDateTime, onWake: () -> Unit)` (the dark screen or the night clock; after a wake, the transparent swallowing layer for `WAKE_GRACE_MS`; nothing else while AWAKE); `@Composable fun CameraMark()`; `const val WAKE_THE_SCREEN = "Wake the screen"`, `const val SCREEN_ASLEEP = "Screen asleep"`, `const val THE_CAMERA_IS_ON = "The camera is on"`.

- [ ] **Step 1: Write the failing tests** (`SleepLayersTest.kt`, Robolectric; `ShellLayers` with a real `HhButton` in the shell counting presses; `sleeping` and `screen` from one `mutableStateOf` that `onWake` sets to AWAKE; each ends with `assertClickablesHaveRoleAndLabel()`)
- `theNightClockShowsOnlyTheTime` (§2.2, ruling 21): at 23:41 one merged node named "23:41" with click label "Wake the screen"; no other text.
- `theDarkScreenIsNamedApartFromItsAction`: named "Screen asleep", click label "Wake the screen".
- `aWakingTapPressesNothingBeneath` (§4.1): a click on the button's spot while ASLEEP → AWAKE, the button's count 0, `onTouch` not called; the same on the NIGHT_CLOCK.
- `aSecondTapWithinTheGraceIsSwallowed` (ruling 8, Review Focus 5): woken; a click at 300 ms (`mainClock`) → count 0; at 600 ms → count 1.
- `whatTheSleepLayerCoversIsHiddenFromTalkBack` (Review Focus 5): ASLEEP, the button has no semantics node; AWAKE, it has.
- `aToastDoesNotShowOverTheNightClock` (U2): a toast arrives on the NIGHT_CLOCK → no toast node, its timer not started; woken → the toast shows for its full time. The same for a closing warning while ASLEEP.
- `theCameraMarkShowsWhileTheCameraRuns`: `cameraMark = true` → "The camera is on" over the dark screen too; false → none.
- `theSleepLayerIsAboveTheAlertAndBelowThePinPad`: with an alert, a tap on its spot wakes and doesn't reach it; with the PIN pad up, its key is pressed.
`SleepLayersScreenshotTest.kt`: `night_clock`, `night_clock_fs15`, `camera_mark_dark`, `camera_mark_light` (Home with the mark).
Run: `./gradlew :app:testDebugUnitTest --tests '*SleepLayersTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — each layer `Box(Modifier.fillMaxSize().background(Color.Black))` taking the finger down (`awaitFirstDown(requireUnconsumed = false).consume()`, then `onWake()`) with `semantics(mergeDescendants = true) { contentDescription = …; onClick(WAKE_THE_SCREEN) { onWake(); true }; role = Role.Button }`; the night clock's name is its text.
- [ ] **Step 3: Run them; record the images; the baseline check with the four names**
- [ ] **Step 4: The gate and commit**
```bash
git add app
git commit -m "Add the dark screen and the night clock above the alert, hold toasts while asleep, and show the camera mark"
```

---

### Task 11: Settings › Display (§4.3, §4.6; rulings 14, 15, 19, 20)

**Review:** sonnet.

**Files:**
- Create: `app/.../display/ui/DisplaySettingsPage.kt`, `display/DisplayWords.kt`; tests `display/ui/DisplaySettingsPageTest.kt`, `display/ui/DisplaySettingsScreenshotTest.kt`
- Modify: `display/di/DisplayModule.kt` (`@Binds @IntoSet displayPage(DisplayPage): SettingsPage`)

**Interfaces:**
- Consumes: `DisplaySettings`, `DisplayPrefs`, `SLEEP_CHOICES`, `BRIGHTNESS_RANGE`, `CameraAccess`, `CameraState`, `wakesByCamera` (Task 3); `HhSwitchRow`, `HhTimeStepperRow`, `clockText` (Task 2); `sunToday` (Task 8); `HhPercentSlider`, `HhChoiceChip`, `HhButton`, `HhSectionLabel`; `Toaster.save`, `LocalShellNavigator`.
- Produces: `const val DISPLAY_PAGE_ID = "display"`; `class DisplayPage @Inject constructor(settings: DisplaySettings, access: CameraAccess, daylight: Optional<Daylight>, toaster: Toaster) : SettingsPage` (id `display`, title "Display", order 440; `Content()` reads `LocalShellNavigator.current`); `data class DisplaySettingsUi(val prefs: DisplayPrefs, val camera: CameraState, val sunKnown: Boolean)`; `class DisplaySettingsActions(onNight: (LocalTime, LocalTime) -> Unit, onDay: (Int) -> Unit, onEvening: (Int) -> Unit, onSleepAfter: (Int) -> Unit, onWakeByCamera: (Boolean) -> Unit, onAllowCamera: () -> Unit, onOpenAndroidSettings: () -> Unit)`; `@Composable fun DisplaySettingsScreen(ui, actions)`; words in `DisplayWords.kt` (the Copy list), `fun nightClockLine(from: LocalTime, until: LocalTime): String`, `const val CANT_OPEN_ANDROIDS_SETTINGS`, `fun appDetailsIntents(packageName: String): List<Intent>` (`ACTION_APPLICATION_DETAILS_SETTINGS`, `package:` uri).

- [ ] **Step 1: Write the failing tests** (`DisplaySettingsPageTest.kt`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `itSitsBeforeMusic` (ruling 19): id, title, order 440.
- `theNightClockTimesStepByHalfAnHour` (§4.6): `_later` on From → `onNight(22:30, 07:00)`; the line as in the Copy list.
- `theBrightnessSlidersSaveWhenLetGo` (spoken "Daytime brightness, 70 percent"); `withoutSunTimesItSaysTheDaytimeLevelApplies` (§4.4).
- `theSleepDelayChoices` (§4.6): four chips, "5 minutes" selected; "10 minutes" → `onSleepAfter(10)`.
- `allowedShowsTheSwitchOnByDefault`: ALLOWED, `wakeByCamera = null` → on; toggled → `onWakeByCamera(false)`.
- `notAskedOffersAllowTheCamera` (§4.3): no switch; the line and "Allow the camera" → `onAllowCamera()`.
- `aRefusalSaysHowToAllowIt`: the refused line; "Open Android’s settings for Culvery" → `onOpenAndroidSettings()`.
- `noFrontCameraSaysATapWakes`.
`DisplaySettingsScreenshotTest.kt`: `settings_display_allowed_dark`, `settings_display_allowed_light`, `settings_display_not_asked_dark`, `settings_display_refused_dark`.
Run — Expected: FAIL to compile.
- [ ] **Step 2: Implement** `DoorSettingsScreen`'s and `DoorSettingsHost`'s shapes (`toaster.save` with `Log.w(TAG, "Couldn't save a display setting (${it::class.simpleName})")`); `onAllowCamera` is `access.ask()`; `onOpenAndroidSettings` is `navigator.openSystemScreen(appDetailsIntents(context.packageName), CANT_OPEN_ANDROIDS_SETTINGS)`.
- [ ] **Step 3: Run them; record the images; the baseline check with the four names**
- [ ] **Step 4: The gate and commit**
```bash
git add app
git commit -m "Add Settings › Display: the night clock's hours, the two brightness levels, the sleep delay and waking by the camera"
```

---

### Task 12: Wiring: `MainActivity`, the camera prompt (§3, §4.1–4.4; rulings 4, 6, 14, 23, 24)

**Review:** opus.

**Files:**
- Create: `app/.../display/CameraPrompt.kt`; test `display/DisplayWiringTest.kt`
- Modify: `MainActivity.kt`

**Interfaces:**
- Consumes: everything above; `KioskLifecycle.openSystemScreen`, `returnToPinning`; `ShellLayers(sleeping, sleep, cameraMark)`, `SleepLayer` (Task 10); `DisplayModule` and `CameraModule` as Tasks 4, 7, 9, 11 left them.
- Produces: `internal fun holding(pinShowing: Boolean, sheetShowing: Boolean, alertShowing: Boolean, settingsOpen: Boolean, complete: Boolean?): Boolean`; `internal class CameraPrompt(access: CameraAccess, openSystemScreen: (missing: String, launch: () -> Unit) -> Unit, returnToPinning: () -> Unit)`: `fun request(launch: () -> Unit)`, `suspend fun answered(granted: Boolean, rationale: Boolean)`; `internal const val CAMERA_PROMPT_MISSING = "Culvery can’t ask for the camera on this tablet."`

- [ ] **Step 1: Write the failing tests** (`DisplayWiringTest.kt`, Robolectric)
- `eachShownThingHoldsTheScreen` (§4.1): `holding(…)` true for each flag alone (the PIN pad, a sheet, the alert, Settings, `complete == false` the wizard, `complete == null` still reading), false for none.
- `thePinComesBackAfterTheCameraAnswer` (ruling 14): `request` goes through `openSystemScreen` with `CAMERA_PROMPT_MISSING`; `answered(true, false)` → `access` refreshed, then `returnToPinning` called once.
- `theModulesBindEachPiece` (**pin**; reflection per the house rule): `NightHours` from `DisplayState`, `TabletHeat` from `AndroidTabletHeat`, `FrameSource` from `Camera2Frames`; `@IntoSet` `Startable` from `HeatLog` and `Presence`; `@IntoSet` `SettingsPage` from `DisplayPage`.
Run: `./gradlew :app:testDebugUnitTest --tests '*DisplayWiringTest*'` — Expected: FAIL to compile.

- [ ] **Step 2: Implement in `MainActivity`**
- Inject `display: DisplayState`, `brightness: ScreenBrightness`, `camera: CameraAccess`, and `presence: Presence` as an `internal lateinit var`; `prompt = CameraPrompt(camera, kiosk::openSystemScreen, kiosk::returnToPinning)`.
- `FLAG_KEEP_SCREEN_ON` stays (§4.2).
- In `repeatOnLifecycle(STARTED)`: `brightness.level.collect { applyBrightness(window, it) }`; `camera.requests.collect { prompt.request { askCamera.launch(Manifest.permission.CAMERA) } }`.
- `askCamera = registerForActivityResult(RequestPermission()) { granted -> lifecycleScope.launch { prompt.answered(granted, shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) } }`; `onResume`: `camera.refresh(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))`.
- In `setContent`: `screen` and `presence.running` collected; `LaunchedEffect(held) { display.held(held) }` with `held = holding(pinRequest != null, overlay.isShowing, alert != null, state.settingsOpen, complete)`; `ShellLayers(…, sleeping = screen != Screen.AWAKE, sleep = { SleepLayer(screen, { now.value }, display::tapped) }, cameraMark = running)`.

- [ ] **Step 3: Run them, the gate** — `./gradlew :app:testDebugUnitTest`, then the gate — Expected: PASS, no image changed; `./gradlew :app:assembleDebug` builds (Hilt's graph is whole).
- [ ] **Step 4: Commit**
```bash
git add app
git commit -m "Wire sleep and wake into the shell: the layers, the window's brightness and the camera prompt"
```

---

### Task 13: The emulator walk-through on `culvery-5` (§7)

**Review:** the controller, with the user at the emulator. (The workflow runs `simplify` over the branch first.)

**Files:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Step 6); any file a fault fix needs.

**Rules.** As Plan 7's Task 17: the emulator `emulator-5554` holds a signed release and the household — **never install on it, never uninstall anything, never run a Gradle `install…` task while any device is connected**. Only the `culvery-5` AVD; every `adb` command names `-s "$S"`. The user types every PIN. The AVD's own config is not changed: the camera is given on the command line. Put back every Culvery setting changed before closing.

- [ ] **Step 1: The AVD** — `-list-avds` shows `culvery-5` (else stop and ask); start it with `-no-snapshot-save -camera-front emulated` (its config has no front camera; the emulated one is a moving test scene, so presence itself is checked on the tablet); its serial to `$TMP/culvery-5-serial`; `S=$(cat "$TMP/culvery-5-serial")`.
- [ ] **Step 2: Install the debug build there only** — `./gradlew :app:assembleDebug`; `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 3: Walk the checklist** (tick each with one line)
1. Settings › Display (Admin PIN) sits after Calendars, before Music; its sections read as written.
2. **The camera prompt:** "Allow the camera" → Culvery unpins, Android's dialog → Deny → "Allow the camera" again → Deny (Android 11's "Don't ask again" on the second) → the refused line; after each answer Culvery is pinned again (`adb -s "$S" shell dumpsys activity activities | grep -i "mLockTaskModeState"` shows PINNED). "Open Android’s settings for Culvery" (fresh Admin PIN) → allow there → back: pinned, the switch on.
3. **The camera mark** shows exactly while `adb -s "$S" shell dumpsys media.camera | grep -i -A2 "active camera clients"` names Culvery's package.
4. **Asleep:** the sleep delay at 2 minutes, signed out; at 2 minutes the screen goes black, the mark still there; a tap on a Home card's button only wakes; a quick second tap presses nothing. `adb -s "$S" shell dumpsys window windows | grep -i -o "sbrt=[0-9.-]*"` shows the lowest (if not printed, the user judges by eye).
5. **Holds:** a sheet, the PIN pad, Settings: none sleeps past 2 minutes; each closed: asleep 2 minutes later.
6. **The doorbell by day**, if this AVD's Door tab is connected (the `ring()` helper from Plan 5's emulator walk-through): asleep → a fake ring wakes the wall and shows the card with the chime.
7. **Night:** the night times set to start 3 minutes from now and end 10 minutes from now: the night clock shows, the mark goes and no camera client; a tap wakes at the evening level; 1 minute later back to the clock; a fake ring: no card, no chime, the Door badge counts it; at the end the wall wakes.
8. **A restart at night:** with night on, `adb -s "$S" shell am force-stop uk.co.siland.culvery`, then start Culvery: the night clock from the first frame; a fake ring at once: only stored. Then put the night times back.
9. **Not in front:** Use other apps (fresh Admin PIN), open Android's Settings: no mark, no client, Android's own brightness; back: awake, then asleep 2 minutes later.
10. **Without a camera:** `adb -s "$S" emu kill`; start again without `-camera-front`: "This tablet has no front camera, so a tap wakes the screen."; the day screen still sleeps and a tap wakes it.
11. **Heat lines:** `adb -s "$S" logcat -d -s HeatLog:W` shows the start line in the format, numbers only.
12. TalkBack (short, with the user's yes): the night clock reads its time, the dark screen "Screen asleep", each with the action "Wake the screen"; a swipe over the dark screen doesn't wake it; a double-tap does; the mark reads "The camera is on".
- [ ] **Step 4: The fault loop** — each fault a failing test, the fix, the module's tests and the gate, a commit naming the fault, `install -r`, the item re-checked.
- [ ] **Step 5: Put back** the night times (22:00–07:00), the sleep delay (5), the levels (70, 25); `adb -s "$S" emu kill`.
- [ ] **Step 6: Record and close** — under a new "Plan 8 (Sleep and wake): emulator walk-through" heading: the outcome; how the permission dialog behaved with pinning; faults and commits; taste notes for the design walk-through.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 8's emulator walk-through"
```

---

### Task 14: On the tablet: power and heat, decision 9, waking by the camera, the night clock in the dark (§1, §2.9, §7; U1)

**Review:** the controller with the user. **Needs the son's SM-T510 on the cable and the household's yes.** The measurement needs about 5 hours, recharges included, with nobody walking past the wall, and **the user unplugs and replugs the tablet** each time (the controller can't, and the USB cable charges it).

**Files:** maybe `display/DisplaySettings.kt` (`WAKE_BY_CAMERA_DEFAULT`), `camera/FrameDiff.kt`'s constants, `ScreenBrightness`'s defaults, `NightClockTokens`, with their tests; the follow-ups file.

**Rules.** 4e's tablet rules (`docs/superpowers/plans/2026-10-12-4e-walkthrough.md`, "Rules"): save and restore every setting changed (its save and restore blocks), never touch other apps' data, signed releases with `adb install -r`, **never uninstall Culvery**, the serial in `$TMP/culvery-4e-serial`, device paths in one quoted `shell` string, the names check before any commit. This task changes only Culvery's own Settings › Display values (written down first, put back last); Android's brightness and screen timeout are read, never changed.

**A reading** (at each run's start, while plugged, and at its end, at once after replugging): `adb -s "$S" shell dumpsys battery | grep -E "level|temperature|Charge counter"` and `adb -s "$S" shell dumpsys thermalservice | grep -iE "status|temperature"` to `$TMP/culvery-8-run-<X>-<start|end>.txt`; at the start also `adb -s "$S" logcat -c`. **The charge counter is required:** if `dumpsys battery` shows none, stop and ask.

- [ ] **Step 1: Run A, for context only, before installing** — the tablet still runs Plan 7's release, always on at the son's brightness. Save `settings get system screen_brightness` and `screen_brightness_mode` (read back at the end). A reading; the user unplugs; **30 minutes**, Culvery on Home, nobody near; the user replugs; a reading.
- [ ] **Step 2: Install the release** — signed per `docs/setup/release.md`; `adb -s "$S" install -r app/build/outputs/apk/release/app-release.apk`; Settings › Display: allow the camera (the user taps Allow); write down the defaults.
- [ ] **Step 3: Runs B and C, 2 hours each, unplugged** (each starting above 50 %; recharge between if needed)
- **Run B:** camera wake-up **off**, the day screen sleeping after 5 minutes.
- **Run C:** camera wake-up **on**, nobody near (asleep with the camera running).
After each: a reading, then `adb -s "$S" logcat -d -s HeatLog:W > "$TMP/culvery-8-run-<X>-heat.txt"` (a bonus; note any run whose first 15-minute line rolled out of logcat).
For each run: **drain in % per hour from the charge counter**, as `(counter_start − counter_end) / (counter_start × 100 / level_start) × 100 / hours`; the battery temperature's **rise** (end − start); the highest thermal status seen (readings and heat lines). A table in the notes, numbers only.
- [ ] **Step 4: Decision 9 (U1)** — camera wake-up runs warm or costs noticeable power if **any** of: run C's thermal status rose above its start where run B's didn't; run C's temperature rise is 3 °C or more above run B's rise; run C drained more than 2 % per hour more than run B. If so: `WAKE_BY_CAMERA_DEFAULT = false` (`theSwitchIsOnByDefaultOnlyOnceAllowed` updated first so it fails, then the change), the gate, `git commit -m "Ship camera wake-up off by default, as it costs the tablet too much"`, rebuild and `install -r`; the switch then reads off unless the Admin turns it on. Either way, the numbers and the outcome go in the notes.
- [ ] **Step 5: Waking by the camera** (wake-up on for this step even if Step 4 turned the default off)
1. Asleep in daylight: the user walks straight towards the wall from 5 m at a normal pace; note the distance at which it wakes and roughly how long after entering the camera's view (a phone stopwatch). Then from the side at 2 m, and once in lamp light at dusk.
2. Nobody near for 15 minutes with the room's lights and a Hue scene changed once (the user's choice): no wake (Review Focus 1).
3. If it wakes too late or by itself: tune `PIXEL_DELTA`, `CHANGED_SHARE` or `smallestSize`'s floor with a failing `FrameDiffTest` first, the gate, a commit, `install -r`, and repeat 1–2.
- [ ] **Step 6: The night clock in the dark** (the user, at the household's bedtime)
1. The room dark: the clock readable at arm's length; the room not lit by it (the user's judgement); black's glow at the lowest backlight noted.
2. A tap: the evening level doesn't dazzle; 1 minute later back to the clock.
3. If it lights the room: a dimmer `NightClockTokens.ink` first (§4.2), the screenshot re-recorded, the gate, a commit, `install -r`. If the evening level dazzles: a lower default `eveningPercent`, the same way.
- [ ] **Step 7: Put back and read the logs** — Settings › Display to its defaults (or the household's chosen values, in the notes); `screen_brightness` and its mode read back equal to Step 1's. The camera permission stays granted (the feature needs it) unless the user says otherwise. Culvery pinned. `adb -s "$S" logcat -d | grep -E "HeatLog|Presence|DisplayState"`: numbers, states and exception classes only. From here on the tablet's logs are read whenever it is plugged in while work is under way (§7). Mention Samsung's "Protect battery" (charging to 85 %) to the user as their choice for the son's tablet; change nothing.
- [ ] **Step 8: Record** — the follow-ups file under "Plan 8 (Sleep and wake): on the tablet": the three runs' numbers, decision 9's outcome, the wake-up distance and speed, the night clock's verdict, tuned values, taste notes; no names.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 8 on the tablet: the camera's power and heat, waking by the camera and the night clock in the dark"
```

---

### Task 15: Final review, merge, install

**Review:** an opus final review of the whole branch against the spec; the controller. **Needs the SM-T510 for the install.**

- [ ] **Step 1: The review** — opus, the whole branch against the spec and this plan; one fix round, each fix with its failing test first and the gate.
- [ ] **Step 2: Merge** — `superpowers:finishing-a-development-branch`: merge to `main` locally; never push.
- [ ] **Step 3: Install** — the release from the merged `main`, signed, `adb -s "$S" install -r app/build/outputs/apk/release/app-release.apk` (4e's rules); the night clock and one camera wake checked once more; Culvery pinned.

---

## Spec coverage (8 design → tasks)

| Spec | Task |
|---|---|
| §1 outcome; no light sensor; done on the tablet | 8, 14, 15 |
| §2.1–2.3 night and day; the dim clock; set times | 3, 4, 10, 11 |
| §2.4 only a tap wakes at night; no camera at night | 4, 6 |
| §2.5–2.6 the camera by day; 5 minutes, adjustable | 3, 4, 6, 7, 11 |
| §2.7 the doorbell day and night | 4 (the card wakes), 5 |
| §2.8 brightness by the time of day, evening on a night tap | 8, 11 |
| §2.9 the fallback | 3 (`WAKE_BY_CAMERA_DEFAULT`), 14 |
| §2.10 heat | 9 |
| §3 modules: `DisplayState`, `Presence`, `ScreenBrightness`, `HeatLog`, the layers, Settings › Display | 4, 6–12 |
| §4.1 the table, the waking tap, not in front, holds, held toasts | 4, 10, 12 |
| §4.2 dark, not off; `FLAG_KEEP_SCREEN_ON`; the clock's colour | 8, 10, 12, 14 |
| §4.3 the camera, privacy first; the mark; the permission | 1, 3, 4, 6, 7, 10–12 |
| §4.4 the window only; no sun times | 8 |
| §4.5 the doorbell; a card showing as night starts | 4, 5 |
| §4.6 Settings › Display | 2, 3, 11 |
| §4.7 heat lines in release | 9 |
| §5 when things go wrong; privacy | 3, 4, 6–9 |
| §6 tests | every task |
| §7 emulator; tablet; power and heat; decision 9; Protect battery | 13, 14 |
| §8 not in this plan | none |
