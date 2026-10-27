# Culvery — Plan 11b: Holiday mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–8 are subagent tasks (sonnet implementers, a reviewer per task; **opus** reviews where the table says so). Task 9 is the controller. Task 10 is the controller on the `culvery-5` AVD with the user. Tasks 11–12 need the user and the son's SM-T510.

**Goal:** an Adult or Admin turns on the house Holiday mode from the One-tap tray with a fresh PIN and a back date (filled in from the calendar when it can be). Culvery then switches on Holiday lights, puts the heating on Eco, silences the door and darkens the screen. It runs the hoover at 10:00 the day before the back date and takes the heating off Eco at 00:01 on it. It all ends when someone taps it off at home.

**Architecture:**
- **`:core:house`** (new) holds the seams the house's parts meet through, since `ModuleBoundaries` forbids one capability depending on another:
  - `HouseMode`, written by holiday and read by door and `DisplayState`;
  - `HouseLights`, `HouseHeating` and `HouseHoover`, implemented by lights, heating and hoover;
  - `MultiDayEvents`, implemented by calendar;
  - the shared `HouseStep` result and the `holiday.mode` permission id.
- **`:capability:holiday`** (new) holds:
  - the record (`holiday_settings`);
  - `HolidayHouse`, which turns the mode on and off and runs due steps;
  - `HolidayScheduler`, a `Startable` that ticks each minute and on start;
  - the permission, the tray tile (order 10), and the "When are you back?" sheet.
- Lights' own Holiday mode becomes **Holiday lights** (spec §2.12).
- The calendar's month grid moves to `:core:ui` as `HhDateGrid`, so the sheet can use it.
- Door and `DisplayState` read `HouseMode`.
- A debug-only clock offset lets the walk-through reach 10:00 the day before and 00:01.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2, java.time; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-26-culvery-11-hoover-heating-holiday-design.md`:
- §2 (decisions 3–7, 11–13);
- §5 whole;
- §6's Holiday row and privacy;
- the 11b parts of §7 and §8 (§8.1 with the clock offset, §8.3's 11b line).

The drawing is the handoff `docs/design/2026-10-redesign/handoff.md`, "One-tap tray (note 13)" and note 15. Where the spec and the handoff disagree, the spec wins.

**Not this plan:** §8.2.1, which moved to the first real holiday (§2 decision 13); Party mode; Arlo; Settings › Home buttons; changing the back date while away.

**Previous plans (format, shared parts):**
- `docs/superpowers/plans/2026-10-26-culvery-11a-home-assistant-hoover-heating.md`: the house format, the tray, `HooverSystem`, `HeatingSystem`, the image routine and `TestAccess`.
- `docs/superpowers/plans/2026-10-16-culvery-07-hue.md`: `LightsHouse` and a fresh-PIN permission.
- Plan 8: `DisplayState`, `DoorAlerts` at night.

**Builds on:** branch `plan-11b-holiday` in the one checkout `C:\Users\si\code\siland\culvery`, cut from `main` at be7c0af2. No worktrees. Never push.

## Global Constraints

**From the spec, verbatim:**
- §2.4 "**Holiday mode is explicit.** The wall never works out a holiday from the calendar on its own".
- §2.6 "**Holiday mode ends only when someone taps it off** at home".
- §2.13 Culvery turns Eco off **once**, at 00:01; Home/Away Assist stays on for now, and the first real holiday shows whether it puts Eco back.
- §5.2 "a fresh Adult or Admin PIN (permission `holiday.mode`, "Turn Holiday mode on and off", default roles Admin and Adult, fresh PIN every time". The date picker runs "from tomorrow up to 90 days ahead". "**Tap, while on:** the same fresh PIN, then it turns off (§5.4). No sheet." "The sheet lists only the steps that will happen."
- §5.3 "A step that fails while turning on … doesn't stop Holiday mode … the failed step is retried every 5 minutes until it succeeds or Holiday mode is turned off". The hoover is "due from 10:00 to 22:00 on the day before. Missed, it is skipped". Heating is "due from 00:01 on the back day until Holiday mode is turned off". "Changes of clock (BST) are handled by working in local dates and times". There are "no new Android permissions" and nothing uses `AlarmManager` or WorkManager.
- §5.7 "if someone takes the Nest off Eco from their phone, Culvery doesn't put it back."
- §6 privacy: logs carry "no address, no token, no entity ids or names … no back dates", and "No calendar event titles in logs".
- §8.1 the clock offset is "debug-only … never in release".

**Copy** (verbatim from the spec; curly apostrophes and "›", "·" in code):
- **Tile:**
  - the label is "Holiday mode";
  - when on, the tile shows "ON" and, under the label, "Back Sat 2 Nov".
- **TalkBack:**
  - "Holiday mode, off. Turn on, button." / "Holiday mode, on, back Saturday 2 November. Turn off, button.";
  - on the sheet: "When are you back? Saturday 2 November. Change date, button."
- **Sheet:**
  - "When are you back?" and "Change date";
  - "From the calendar: Cornwall, ends Saturday." or "Pick the day you’re back.";
  - "Holiday mode will:";
  - "Turn on Holiday mode" and "Cancel";
  - while a change runs, "Turning on…" / "Turning off…".
- **§6 and §5.4 lines:**
  - "The heating didn’t come off Eco. Turn it off in the Nest app."
  - "The hoover didn’t run."
  - "The heating is still on Eco. Turn it off in the Nest app."
- **Holiday lights (§2.12):** "Holiday lights" in place of "Holiday mode" everywhere Plan 7's feature shows. The permission label becomes "Turn Holiday lights on and off". Settings › Lights uses "Holiday lights switch on" and "Holiday lights turns all lights off and shows On."
- **This plan's copy** (spec gaps; the owning task tests each):
  - the sheet's steps, each shown only when it will happen:
    - "Switch on Holiday lights now."
    - "Put the heating on Eco now, and take it off Eco at 00:01 on {Saturday 2 November}."
    - "Run the hoover at 10:00 on {Friday 1 November}."
    - "Keep the doorbell quiet. Rings and motion still show in the Door tab."
    - "Turn the screen off until someone taps it."
  - the calendar line's day: the weekday alone when the last day is within 6 days of today ("ends Saturday."), else the full date ("ends Saturday 2 November.");
  - a lights step that couldn't be retried after a restart: "Holiday lights didn’t switch on. Turn them on in the Lights tab.";
  - turning off with the lights failing: "Holiday lights are still on. Turn them off in the Lights tab.";
  - the heating's Eco-on line when it isn't confirmed: "The heating didn’t go to Eco. Check the Nest app.".

**From the house rules** (as Plan 11a; binding):
- **Platform:**
  - package root `uk.co.siland.culvery`; `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only;
  - **no new library; never change a version**;
  - no deprecated API, and any one met is flagged to the user before use.
- **New modules:**
  - `:core:house`: `culvery.android.library` and `culvery.hilt`; `api(project(":core:access"))` for `Authorised`.
  - `:capability:holiday`: as `capability/hoover/build.gradle.kts`, plus `api(project(":core:house"))`.
  - Each is added to `settings.gradle.kts`, to `app/build.gradle.kts` and to the README's module list in the task that creates it. `ModuleBoundariesTest` gains a pin, `theHouseModulesKeepToTheirFamilies`.
- **The test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug`, plus `./gradlew -p build-logic :convention:test` in any task that adds a log line or a module.
  - Never plain `test`, `check` or `build`, and never a Gradle `install…` task. No subagent touches a device.
  - **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`.**
  - Scratch goes in `$LISTS` = `C:/t9`, a plain shell variable, never exported.
- **Tests that can fail:**
  - Every new test fails before its change; one that passes before it is labelled **pin**.
  - Time tests feed local date-times and wall millis directly, or run on virtual time, and assert one minute before each boundary and at it.
  - Each module that needs a PIN copies Lights' `TestAccess(role)` with its own `PermissionSource`.
- **Every UI task ends as Plan 11a's:**
  1. `compareRoborazziDebug` on the module; open each `*_compare.png`, and check each new image against the drawing named;
  2. `recordRoborazziDebug`;
  3. the baseline check (`$LISTS/expected-baselines.txt`, `$LISTS/expected-prefixes.txt`, then Plan 10's four `comm`/`grep` lines);
  4. the gate;
  5. commit.

  Every new screen is recorded `_dark` and `_light`. `CopyScanTest` passes; every screen test ends with `compose.assertClickablesHaveRoleAndLabel()`.
- **Code:**
  - Icons come from `Icons`. `beach_access` is added to `tools/fonts/icons.txt` and the subset rebuilt as Plan 11a's Code rule says; a missing glyph means a download, so stop and ask.
  - Layout numbers go in the module's dimens object.
  - Comments say what the code can't, in one line, with spec refs as `(11 §5.3)`, never a task or ruling number.
  - Each new file with a `TAG` is named in `LogHygieneTest.theScanReachesTheShippedCode`.
- **Privacy:**
  - No back date, event title, entity id or name goes into a log, an exception message or a `toString`.
  - `HolidayRecord`, `MultiDayEvent` and `BackDatePick` override `toString` without them.
  - Sample data only: "Cornwall", "Half term", the thermostat "Hall", and dates in 2026–2027.
- **Devices:**
  - The emulator is `culvery-5`. `emulator-5554` holds a signed release and the household: never install on it.
  - The tablet (SM-T510) gets `adb install -r` of a signed release only; never uninstall, never wipe.
  - **The hoover runs, and the thermostat changes, only when the user says so** (Tasks 10–11).
- **Git:**
  - A commit is a plain sentence with no prefix and no trailer of any kind; never push.
  - **Never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **The tablet restarts (a power cut, an update) mid-holiday, including across 00:01 or 10:00.**
   - **Expected:**
     - The record is read and the mode is still on; the screen goes dark and the door stays quiet.
     - Eco off runs at the first tick on or after 00:01 on the back day, however late.
     - A hoover window that passed while the tablet was off is skipped and the tile says "The hoover didn’t run."
     - A lights step left pending is reported, not retried, because the PIN's grant isn't kept.
   - **Tests** (Task 5): `HolidayHouseTest.aRestartAfter0001TurnsEcoOffAtOnce`, `aRestartAfterTheHooverWindowSkipsIt`, `aPendingLightsStepAfterARestartIsReported`; `HolidaySettingsTest.theRecordSurvivesARestart`.
2. **The calendar holds both half term and a real holiday.**
   - **Expected:**
     - An event on today wins over one starting tomorrow.
     - Among events on today, the one that started most recently wins.
     - Timed events, one-day events, and events ending today or more than 90 days ahead are ignored.
     - Google's exclusive end is turned into the last day.
   - **Tests** (Task 5): `BackDatePickTest.*`; (Task 2) `CalendarMultiDayEventsTest.theExclusiveEndBecomesTheLastDay`, `hiddenCalendarsAreLeftOut`.
3. **The clocks change during a holiday.**
   - **Expected:**
     - With a back date of Mon 29 Mar 2027, the hoover runs at 10:00 local on Sun 28 Mar (after the spring jump) and Eco comes off at 00:01 local on 29 Mar.
     - With a back date of Sun 31 Oct 2027, Eco comes off at 00:01 BST that morning, not an hour late.
     - The 5-minute retry spacing is measured on wall millis, so the autumn hour doesn't stall a retry.
   - **Tests** (Task 5): `HolidayHouseTest.springForwardKeepsLocalTimes`, `autumnBackKeepsLocalTimes`, `retriesAreSpacedOnTheWallClock`.
4. **Taps while a change runs, a Child at the wall, a cancelled PIN or sheet.**
   - **Expected:**
     - One PIN pad per tap. A Child's PIN or a cancelled pad changes nothing, and so does Cancel on the sheet.
     - Taps during "Turning on…" or "Turning off…" are ignored.
     - Turning on while the mode is already on (two tablets are impossible, but a double tap isn't) runs nothing twice.
   - **Tests** (Task 6): `HolidayActionsTest.aChildsPinChangesNothing`, `aCancelledSheetChangesNothing`; `HolidayTileTest.tapsWhileTurningAreIgnored`; (Task 5) `HolidayHouseTest.turningOnTwiceRunsOnce`.
5. **Parts missing or failing.**
   - **Expected:**
     - With no Hue link, no thermostat or no hoover, that step is neither listed nor run.
     - Home Assistant down at turn-on still turns the mode on, and the failed step is retried every 5 minutes.
     - A hoover already cleaning at 10:00 counts as done.
     - Turning off with the thermostat unreachable still ends the mode and shows "The heating is still on Eco…".
   - **Tests** (Task 5): `HolidayHouseTest.eachMissingPartIsLeftOut`, `aFailedStepIsRetriedEveryFiveMinutes`, `aHooverAlreadyCleaningCountsAsDone`, `turningOffEndsEvenWhenAPartFails`.

---

| # | Task | Review |
|---|---|---|
| 1 | The 11a seam fixes M2–M4 in `:core:homeassistant` and the two providers | sonnet |
| 2 | `:core:house`; heating, hoover and calendar implement their seams | sonnet |
| 3 | Lights: "Holiday lights" everywhere; `HouseLights` accepts the `holiday.mode` grant | **opus** (access) |
| 4 | `HhDateGrid` in `:core:ui`; the calendar uses it unchanged | sonnet |
| 5 | `:capability:holiday` 1: the permission, the record, `HolidayHouse`, the scheduler, `HouseMode`, the back-date pick | **opus** (time, restarts, retries) |
| 6 | `:capability:holiday` 2: the tile, the sheet, `HolidayActions`, the tray button | **opus** (access) |
| 7 | Door and `DisplayState` read `HouseMode`; the debug clock offset | sonnet |
| 8 | The app wired in, the README | sonnet |
| 9 | Simplify the whole branch | controller |
| 10 | The emulator walk-through on `culvery-5` | controller + user |
| 11 | On the tablet | controller + user, **needs the SM-T510** |
| 12 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:**
1. 11a's fixes go first, since 11b's scheduled calls are what exposed them.
2. Next come the seams and their implementations (2–3), then the shared grid (4).
3. The logic (5) comes before the screen that drives it (6).
4. Then the house's readers (7) and the wiring (8).

## Rulings against the code

The spec leaves these to the builder, and a few fall out of the code. Each is this plan's call.

1. **The seams** (`:core:house`, package `uk.co.siland.culvery.core.house`):
   - `sealed interface HouseStep { data object Done; data class Failed(val line: String) }`.
   - `interface HouseLights { val ready: Flow<Boolean>; suspend fun setHoliday(on: Boolean, by: Authorised): HouseStep }`.
   - `interface HouseHeating { val ready: Flow<Boolean>; suspend fun setEco(on: Boolean): HouseStep }`.
   - `interface HouseHoover { val ready: Flow<Boolean>; suspend fun start(): HouseStep }`.
   - `interface HouseMode { val holiday: StateFlow<Boolean?> }`, where null means not yet read.
   - `interface MultiDayEvents { suspend fun around(today: LocalDate): List<MultiDayEvent> }` and `data class MultiDayEvent(val title: String, val first: LocalDate, val last: LocalDate)`.
   - `object HousePermissions { const val HOLIDAY_MODE = "holiday.mode" }`.
   - `di/HouseModule.kt` has `@BindsOptionalOf` for the four device seams and `MultiDayEvents`. Holiday injects `Optional<…>`; a seam that is absent, or whose `ready` is false at turn-on, is no step.
   - `ready` means: lights, a Hue link is saved; heating, a thermostat is chosen; hoover, a hoover is chosen.
2. **The lights grant (spec §5.1):**
   - `LightsHouse.setHoliday`'s `require` accepts `LightsPermissions.HOLIDAY` or `HousePermissions.HOLIDAY_MODE`.
   - `LightsHouseSeam : HouseLights` maps `setHoliday(true)` with the record already on to `Done` without running ("Already on: left as it is").
   - Results map as follows: `Done` and `Partly` are `Done`; `CantReach`, `Busy` and `Refused` are `Failed(houseLine(result))`.
3. **The grant is held in memory only.** `HolidayHouse` keeps the turn-on's `Authorised` while the process lives, for retrying the lights step. After a restart, a lights step still pending is marked missed and reported with the "Holiday lights didn’t switch on…" line. The record never holds a person.
4. **The record** (`holiday_settings`, `HolidaySettings`):
   - `HolidayRecord(on: Boolean, back: LocalDate?, since: Long?, steps: Map<HolidayStep, StepState>)`.
   - `enum class HolidayStep { LIGHTS_ON, ECO_ON, HOOVER, ECO_OFF }` and `enum class StepState { PENDING, DONE, MISSED }`.
   - Only the steps that will happen are in the map, chosen at turn-on from the seams' `ready`.
   - `prefs: Flow<HolidayRecord>`; `read: StateFlow<HolidayRecord?>`, which is null until the DataStore answers. Turning off clears the record.
5. **When each step is due** (local date-times in the household zone, from `HouseholdClock`; `B` is the back date):
   - `LIGHTS_ON` and `ECO_ON` run at turn-on, then are retried while due. `ECO_ON` stops being due at `B` 00:01: if it never succeeded, it is `MISSED` and `ECO_OFF` still runs.
   - `HOOVER` is due from 10:00 to 22:00 on `B − 1`, and is only in the map when the mode was turned on before 10:00 that day. Turned on later, the sheet doesn't list it. Past 22:00 and not done, it is `MISSED`.
   - `ECO_OFF` is due from `B` 00:01 until the mode is turned off.
6. **Retries:** a step that returned `Failed` is tried again when 5 minutes of wall millis (`WallClock`) have passed since its last try. Last tries live in memory, so a start retries at once. One tick runs at a time (`Mutex`); a tick that finds one running returns.
7. **The scheduler:** `HolidayScheduler : Startable` launches on `@ApplicationScope`. It calls `house.tick()` on start, then on each `HouseholdClock.minutes`.
8. **The tile's line** is the first of these that applies, in step order `ECO_OFF`, `HOOVER`, `ECO_ON`, `LIGHTS_ON`:
   - a failed step's last `Failed.line` (for `ECO_OFF`, "The heating didn’t come off Eco. Turn it off in the Nest app.");
   - a missed step's line ("The hoover didn’t run.", the Holiday lights line, or for `ECO_ON` nothing, since `ECO_OFF` covers it).

   After turning off, the off-failure lines show for `OFF_LINE_MS = 600_000L` or until the next turn-on.
9. **Turning off** (§5.4):
   - `setHoliday(false)` always runs, when a lights seam exists.
   - `setEco(false)` runs unless `ECO_OFF` is `DONE`.
   - Then the record is cleared. Failures don't stop the rest.
10. **The hoover's result (M2):** `HooverSystem.start()` gains `HooverResult.AlreadyRunning` for `CLEANING` and `RETURNING`, and `HouseHoover` maps it to `Done`. A gone or `unavailable` vacuum is `Failed` with its §6 line, not `NotStarted`. The row's `resultLine(AlreadyRunning)` is null.
11. **The link before the DataStore answers (M3):** `HomeAssistant` gains `suspend fun savedLink(): HomeAssistantLink?`, which is `link.value` or else the settings' first value. `start()`, `setEco()` and `calling` use it.
12. **No link (M4):** `HaProblem.NOT_LINKED` is added. `calling` throws it when no link is saved, and it is never put in `problem`. Each provider maps it to its existing "Home Assistant isn’t linked…" line.
13. **The back-date pick** (`BackDatePick`, pure; spec §5.2):
    - The candidates are `MultiDayEvents.around(today)`: all-day events of two days or more, from the calendars shown on the wall, with the last day from tomorrow to today + 90 days.
    - An event on today wins: of those, the latest first day, then the later last day.
    - Otherwise an event starting tomorrow, by the same rule. Otherwise there is no pick, the date is tomorrow, and the line reads "Pick the day you’re back."
14. **The sheet is an `OverlayHost` sheet** (`HhSheet`) opened after the PIN. "Change date" opens `HhDateGrid` in place, with days from tomorrow to today + 90 allowed. "Turn on Holiday mode" turns it on and dismisses the overlay; Cancel dismisses it. The sheet reads the steps' readiness and the candidates as it opens.
15. **The screen** (§5.6): `DisplayState` gains the input `holiday: StateFlow<Boolean?>`.
    - **When `true`:**
      - the screen goes `ASLEEP` at once, unless held;
      - `NIGHT_CLOCK` never shows;
      - a tap wakes it `AWAKE`, and it sleeps again after `sleepAfterMinutes`, day or night;
      - `moved()` does nothing and `cameraWanted` is false.
    - **When it turns false:** the screen is `AWAKE`, and then Plan 8's rules apply.
16. **The door** (§5.5): `DoorAlerts` takes `HouseMode`. With `holiday == true`, a ring is recorded, but no card and no chime, as at night, so the rail badge shows. A null value counts as not on, because a lost ring at home is worse than a chime in an empty house.
17. **The debug clock:**
    - `WallClock`'s binding moves from `AppModule` to `app/src/release/.../di/ClockModule.kt`, which keeps `System.currentTimeMillis()`.
    - `app/src/debug/.../di/DebugClockModule.kt` adds an offset, set with `adb shell am broadcast -a uk.co.siland.culvery.debug.CLOCK --el minutes <n>`, where 0 clears it.
    - The receiver is registered as `DebugOfflineReceiver` is, and release has no receiver.
18. **`HhDateGrid`** is the calendar's `DateGrid` moved to `:core:ui`, with `dots: Map<LocalDate, List<Color>>`; the calendar maps people to their colours. The calendar's screenshots must not change.

## Units

| Where | Handoff px | dp / sp |
|---|---|---|
| Holiday tile | row 1 of the tray; tile radius 26; icon 36; label 22/700; "ON" pill | in `HhTrayColumn`, `surf2` off / `accent` fill on, radius 17, padding 10 × 12; `beach_access` 24, filled; label 15 sp W700; "Back Sat 2 Nov" 12 sp; "ON" 12 sp W700 in a 24-high pill (`HolidayDimens`) |
| Sheet | right sheet 920 | `HhSheet` (613 dp); the date row 28 sp W700; gaps 16; buttons 12 apart |

## File Structure

```
core/house/ (new): HouseSeams.kt (HouseStep, HouseLights, HouseHeating, HouseHoover, HouseMode, MultiDayEvents,
  MultiDayEvent, HousePermissions), di/HouseModule.kt                                                     (Task 2)
core/homeassistant: HomeAssistant.kt, HomeAssistantClient.kt (NOT_LINKED); provider/hoover-homeassistant,
  provider/heating-homeassistant; capability/hoover HooverContract.kt, HooverWords.kt                     (Task 1)
capability/heating: HouseHeatingSeam.kt; capability/hoover: HouseHooverSeam.kt;
  capability/calendar: CalendarMultiDayEvents.kt; their di modules                                       (Task 2)
capability/lights: LightsHouse.kt, LightsHouseSeam.kt, LightsWords.kt, LightsPermissions.kt, di, tests, images (Task 3)
core/ui: DateGrid.kt (HhDateGrid); capability/calendar ui/DateGrid.kt → a wrapper                       (Task 4)
capability/holiday/ (new): HolidayPermissions.kt, HolidaySettings.kt, HolidayHouse.kt, HolidayScheduler.kt,
  BackDatePick.kt, HolidayWords.kt, di/HolidayModule.kt                                                  (Task 5)
  HolidayActions.kt, HolidayCapability.kt, ui/HolidayTile.kt, ui/HolidaySheet.kt, ui/HolidayDimens.kt;
  core/ui Icons.kt, tools/fonts/icons.txt, the font                                                      (Task 6)
capability/door: DoorAlerts.kt; app: display/DisplayState.kt, di/AppModule.kt, src/debug|release ClockModule (Task 7)
settings.gradle.kts, app/build.gradle.kts, README.md, build-logic tests                                 (Tasks 2, 5, 8)
docs/superpowers/plans/2026-09-23-plan1-followups.md                                                     (Tasks 10–12)
```

---

### Task 1: The 11a seam fixes (M2–M4; rulings 10–12)

**Review:** sonnet.

**Files:**
- Modify:
  - `core/homeassistant/.../HomeAssistant.kt` (`savedLink`, `calling`);
  - `HomeAssistantClient.kt` (`HaProblem.NOT_LINKED`);
  - `HomeAssistantWords.kt`, if a `problemLine` switch needs the new value;
  - `provider/hoover-homeassistant/.../HaHooverSystem.kt`;
  - `provider/heating-homeassistant/.../HaHeatingSystem.kt`;
  - `capability/hoover/.../HooverContract.kt` (`AlreadyRunning`);
  - `HooverWords.kt` (`resultLine`).
- Test: `HomeAssistantTest`, `HaHooverSystemTest`, `HaHeatingSystemTest`, `HooverWordsTest`.

- [ ] **Step 1: The failing tests.**
  - `HomeAssistantTest`:
    - `savedLinkReadsTheStoreBeforeStart`: a saved link, `start()` never called → `savedLink()` is the link.
    - `noLinkIsNotLinkedNotTokenRefused`: `read` with nothing saved throws `NOT_LINKED`, and `problem` stays null.
  - `HaHooverSystemTest`:
    - `aColdStartFindsTheLink`: the link saved but `HomeAssistant` not started → `start()` sends `vacuum/start`, not `Failed(NOT_LINKED)`.
    - `startWhileCleaningIsAlreadyRunning`: `CLEANING` and `RETURNING` → `AlreadyRunning`, and nothing sent.
    - `aGoneOrUnavailableHooverSaysWhy`: gone → `Failed("Home Assistant has no hoover called that any more. Choose it again in Settings › Other integrations.")`; `unavailable` → `Failed("Home Assistant can’t reach the hoover. Check the Eufy app.")`.
  - `HaHeatingSystemTest.aColdStartFindsTheLink`.
  - `HooverWordsTest.alreadyRunningHasNoLine`.

  Run `./gradlew :core:homeassistant:testDebugUnitTest :provider:hoover-homeassistant:testDebugUnitTest :provider:heating-homeassistant:testDebugUnitTest :capability:hoover:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 10–12. The row's behaviour doesn't change: it offers Start only in `STARTABLE`.
- [ ] **Step 3: The gate**, then commit.
```bash
git add core/homeassistant provider capability/hoover
git commit -m "Read the saved Home Assistant link before the first collect, say Not linked rather than token refused, and tell an already-cleaning hoover apart from one that didn't start"
```

---

### Task 2: `:core:house`; heating, hoover and calendar implement their seams (spec §5.1; ruling 1; Review Focus 2)

**Review:** sonnet.

**Files:**
- Create:
  - `core/house/build.gradle.kts`;
  - `core/house/src/main/java/uk/co/siland/culvery/core/house/HouseSeams.kt`;
  - `core/house/src/main/java/uk/co/siland/culvery/core/house/di/HouseModule.kt`;
  - `capability/heating/.../HouseHeatingSeam.kt`;
  - `capability/hoover/.../HouseHooverSeam.kt`;
  - `capability/calendar/.../CalendarMultiDayEvents.kt`.
- Modify:
  - the three capabilities' `build.gradle.kts` (`implementation(project(":core:house"))`) and their di modules (`@Binds` each seam);
  - `settings.gradle.kts`, `app/build.gradle.kts`, the README;
  - `ModuleBoundariesTest` (the pin `theHouseModulesKeepToTheirFamilies`: `:core:house` → `:core:access` allowed; `:capability:holiday` → `:capability:lights` refused).
- Test: `HouseHeatingSeamTest`, `HouseHooverSeamTest`, `CalendarMultiDayEventsTest`.

**Interfaces:**
- Produces:
  - ruling 1's types;
  - `HouseHeatingSeam @Inject constructor(systems: Set<HeatingSystem>)`, where `ready` = the first system's `state` is not null;
  - `HouseHooverSeam` likewise over `HooverSystem`;
  - `CalendarMultiDayEvents @Inject constructor(store: CalendarStore, zone: HouseholdZone)`.

- [ ] **Step 1: The failing tests.**
  - `HouseHeatingSeamTest`:
    - `ecoMapsEachResult`: `Done` → `Done`; `NotConfirmed` with `on = true` → `Failed("The heating didn’t go to Eco. Check the Nest app.")`, and with `on = false` → `Failed("The heating didn’t come off Eco. Turn it off in the Nest app.")`; `Failed(line)` → `Failed(line)`.
    - `readyFollowsTheChosenThermostat`.
    - `noSystemIsNeverReady`.
  - `HouseHooverSeamTest`:
    - `startMapsEachResult`: `Started` and `AlreadyRunning` → `Done`; `NotStarted` → `Failed("The hoover didn’t run.")`; `Busy` → `Failed("The hoover didn’t run.")`; `Failed(line)` → `Failed(line)`.
    - `readyFollowsTheChosenHoover`.
  - `CalendarMultiDayEventsTest` (the store's in-memory test set-up; today Wed 28 Oct 2026):
    - `theExclusiveEndBecomesTheLastDay`: all-day 24 Oct → end 2 Nov exclusive gives `first` 24 Oct, `last` 1 Nov.
    - `oneDayAndTimedEventsAreLeftOut`.
    - `hiddenCalendarsAreLeftOut`: a source with `visible = 0`.
    - `onlyEventsAroundTodayAndTomorrow`: an event ending 27 Oct and one starting 30 Oct are left out; one starting 29 Oct is kept.
    - `theTitleIsNotInToString`.

  Run `./gradlew :capability:heating:testDebugUnitTest :capability:hoover:testDebugUnitTest :capability:calendar:testDebugUnitTest --tests '*MultiDay*' :core:house:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per ruling 1. The calendar reads `eventsBetween` for today to tomorrow plus `ZONE_MARGIN_MS`, keeps `AllDay` starts, and turns the end with `daysOf`.
- [ ] **Step 3: The gate and the convention tests**, then commit.
```bash
git add core/house capability/heating capability/hoover capability/calendar settings.gradle.kts app/build.gradle.kts README.md build-logic
git commit -m "Add :core:house, the seams Holiday mode reaches the lights, heating, hoover and calendar through, and implement the heating, hoover and calendar ones"
```

---

### Task 3: Holiday lights, and the house's lights seam (spec §2.12, §5.1, §5.3, §5.4; ruling 2)

**Review:** opus. This task is about the grant `setHoliday` accepts.

**Files:**
- Modify in `capability/lights/.../`:
  - `LightsWords.kt`: "Holiday lights" in `HOLIDAY_MODE` (renamed `HOLIDAY_LIGHTS`), `HOLIDAY_SWITCHES_ON` and `HOLIDAY_JUST_LIGHTS`;
  - `LightsPermissions.kt` (the label);
  - `LightsHouse.kt` (the `require` and its message, the log line's words);
  - KDoc mentions;
  - `di/LightsModule.kt` (`@Binds HouseLights`).
- Create `LightsHouseSeam.kt`.
- Tests and images: every test the explorer listed asserting "Holiday mode" in `capability/lights/src/test` (LightsActions, LightsHouse, LightsPermissions, LightsSettings, LightsWords, TestLights, LightsAllOffLit, LightsCard, LightsSettingsPage and LightsTab), and `LightsHouseSeamTest` (new). `core/ui`'s sample "Holiday mode" button text stays.

- [ ] **Step 1: The failing tests.**
  - The text asserts are changed to "Holiday lights".
  - `LightsPermissionsTest`: the label is "Turn Holiday lights on and off".
  - `LightsHouseTest.theHouseGrantTurnsItOnToo`: `Authorised(person, ADULT, setOf("holiday.mode"))` → `Done`.
  - `LightsHouseTest.anotherGrantIsRefused`: `setOf("hoover.start")` → `IllegalArgumentException`.
  - `LightsHouseSeamTest`:
    - `alreadyOnIsLeftAlone`: record on → `Done`, and no automation started.
    - `partlyIsDone`.
    - `cantReachIsFailedWithItsLine`: the line equals `houseLine(CantReach(...))`.
    - `offAlwaysRuns`.
    - `readyFollowsTheBridge`.

  Run `./gradlew :capability:lights:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per ruling 2.
- [ ] **Step 3: Images.** **Changed on purpose:** each Lights image showing the Holiday button or Settings' Holiday section. List them in `$LISTS/expected-baselines.txt` before recording; nothing else may change. **Check against** note 15.
```bash
git add capability/lights
git commit -m "Call Plan 7's lights-only mode Holiday lights, and let the house's Holiday mode turn it on and off with its own PIN"
```

---

### Task 4: `HhDateGrid` in `:core:ui` (ruling 18)

**Review:** sonnet.

**Files:**
- Create `core/ui/.../DateGrid.kt` (`HhDateGrid`, `monthCells`, the header, the letters and the cell, with their dimens). Move rather than copy.
- Modify `capability/calendar/.../ui/DateGrid.kt` to a wrapper that maps people to colours, and `rememberGridDots`.
- Test:
  - `core/ui` `DateGridTest`: `monthCells` (the old calendar tests move here), `aDisallowedDayCantBeTapped` and `theSpokenDaySaysItsDate`;
  - `DateGridScreenshotTest`: `date_grid_dark` and `date_grid_light`, a range and one chosen day.

- [ ] **Step 1:** Move the tests with the code. Add `aDisallowedDayCantBeTapped` (**pin**, since it passes in the calendar today).
- [ ] **Step 2:** Move the code.
- [ ] **Step 3: Images.** **New:** the 2 above. **Must not change:** every `capability/calendar` image.
```bash
git add core/ui capability/calendar
git commit -m "Move the calendar's month grid into core:ui as HhDateGrid so Holiday mode can pick a date with it"
```

---

### Task 5: `:capability:holiday` 1: the record, `HolidayHouse`, the scheduler and the pick (spec §5.1–§5.4, §5.7; rulings 3–8, 13; Review Focus 1, 2, 3, 5)

**Review:** opus. Covers time, restarts, retries and windows.

**Files:**
- Create `capability/holiday/build.gradle.kts` and, in `.../capability/holiday/`:
  - `HolidayPermissions.kt` (`HolidayPermissionSource`: `holiday.mode`, "Turn Holiday mode on and off", Admin and Adult, `freshPin = true`);
  - `HolidaySettings.kt`, `HolidayHouse.kt`, `HolidayScheduler.kt`, `BackDatePick.kt`, `HolidayWords.kt`;
  - `di/HolidayModule.kt`, which binds `HouseMode` to `HolidayHouse`, `@IntoSet Startable` and `@IntoSet PermissionSource`.
- Modify `settings.gradle.kts`, `app/build.gradle.kts` and the README.
- Test:
  - `HolidaySettingsTest`, `HolidayHouseTest`, `BackDatePickTest` and `HolidayWordsTest`;
  - in `src/test`, fakes for the four seams (scriptable `ready` and a queue of results), plus `TestAccess` with `HolidayPermissionSource`.

**Interfaces:**
- Consumes: Task 2's seams; `HouseholdClock`, `WallClock`, `Startable`, `@ApplicationScope`, `Authorised`.
- Produces:
  - `@Singleton class HolidayHouse internal constructor(settings: HolidaySettings, lights: HouseLights?, heating: HouseHeating?, hoover: HouseHoover?, wall: WallClock, now: () -> LocalDateTime?) : HouseMode`, with an `@Inject` constructor over `Optional`s and `HouseholdClock.now`. Its members:
    - `val record: StateFlow<HolidayRecord?>`
    - `val running: StateFlow<HolidayRun?>` (`enum class HolidayRun { TURNING_ON, TURNING_OFF }`)
    - `val line: StateFlow<String?>` (ruling 8)
    - `suspend fun plan(back: LocalDate): List<HolidayStep>`: the steps that will happen, for the sheet
    - `suspend fun turnOn(back: LocalDate, by: Authorised)`
    - `suspend fun turnOff(by: Authorised)`
    - `suspend fun tick()`
  - Both `turnOn` and `turnOff` `require` `holiday.mode` in `by.granted`.
  - `const val RETRY_MS = 300_000L`, `OFF_LINE_MS = 600_000L`.
  - `fun backDatePick(events: List<MultiDayEvent>, today: LocalDate): BackDatePick?`, where `data class BackDatePick(val title: String, val back: LocalDate)`.
  - `HolidayWords`:
    - `stepLine(step, back)` (the Copy's sheet lines);
    - `backShort(date)` ("Back Sat 2 Nov");
    - `backSpoken(date)` ("Saturday 2 November");
    - `fromCalendar(pick, today)`;
    - `tileSpoken(on, back)`.

- [ ] **Step 1: The failing tests.** In `HolidayHouseTest`, `now` and the wall millis are set by the test; Turbine reads `line`.
  - `HolidayHouseTest`, turning on and off:
    - `turningOnRunsTheStepsInOrder`: the record is saved first, then `setHoliday(true)`, then `setEco(true)`; `holiday` becomes true.
    - `turningOnTwiceRunsOnce` (Review Focus 4): two `turnOn` calls at once → one `setHoliday` and one `setEco`.
    - `eachMissingPartIsLeftOut` (Review Focus 5): no lights seam, a heating seam not `ready`, or no hoover → those steps are absent from `plan` and from the record, and are never called.
    - `aFailedStepIsRetriedEveryFiveMinutes` (Review Focus 5): `setEco` fails → no retry at `RETRY_MS − 60_000`, a retry at `RETRY_MS`; success → `DONE` and the line clears.
    - `turningOffEndsEvenWhenAPartFails` (Review Focus 5): `setEco(false)` fails → the record is cleared, `holiday` is false, and the line is "The heating is still on Eco. Turn it off in the Nest app." until `OFF_LINE_MS`.
    - `turningOffEarlyTakesEcoOff`: `ECO_OFF` pending → `setEco(false)` is called.
    - `turningOffAfterEcoOffLeavesTheHeating`: `ECO_OFF` done → no `setEco` call (§5.7).
    - `aGrantWithoutHolidayModeIsRefused`: a grant without `holiday.mode` → `IllegalArgumentException`.
  - `HolidayHouseTest`, the schedule (back date Sat 31 Oct 2026, turned on Thu 29 Oct 09:00):
    - `theHooverRunsAt1000TheDayBefore`: nothing at Fri 30 Oct 09:59, `start()` at 10:00, and no second start at 10:01.
    - `aHooverAlreadyCleaningCountsAsDone`.
    - `aHooverTurnedOnAfter1000TheDayBeforeIsNotPlanned`: turned on Fri 30 Oct 10:30 → no `HOOVER`.
    - `aRestartAfterTheHooverWindowSkipsIt` (Review Focus 1): the first tick at Fri 30 Oct 22:00 → `MISSED`, no start, and the line is "The hoover didn’t run.".
    - `ecoComesOffAt0001`: nothing at Sat 31 Oct 00:00, `setEco(false)` at 00:01.
    - `aRestartAfter0001TurnsEcoOffAtOnce` (Review Focus 1): the first tick on Sun 1 Nov 14:00 → `setEco(false)`.
    - `ecoOnThatNeverWorkedIsMissedAt0001`: `setEco(true)` failing throughout → `MISSED` at 00:01, then `setEco(false)` is called.
    - `aPendingLightsStepAfterARestartIsReported` (Review Focus 1): a new `HolidayHouse` over a record with `LIGHTS_ON` pending → no `setHoliday` call, `MISSED`, and the line is "Holiday lights didn’t switch on. Turn them on in the Lights tab.".
    - `springForwardKeepsLocalTimes` (Review Focus 3): back date Mon 29 Mar 2027 → the hoover at Sun 28 Mar 10:00 and Eco off at Mon 29 Mar 00:01.
    - `autumnBackKeepsLocalTimes` (Review Focus 3): back date Sun 31 Oct 2027 → Eco off at 00:01.
    - `retriesAreSpacedOnTheWallClock` (Review Focus 3): local time repeating an hour while the wall moves 5 minutes → a retry.
  - `HolidaySettingsTest`:
    - `theRecordSurvivesARestart`: written, then a new store on the same file reads it.
    - `readIsNullUntilTheStoreAnswers`.
    - `clearEmptiesIt`.
    - `toStringHasNoDate`.
  - `BackDatePickTest` (Review Focus 2; today Wed 28 Oct 2026):
    - `noEventsNoPick`.
    - `oneEventOnTodayIsPicked`: Cornwall 24 Oct–1 Nov → back 1 Nov.
    - `aHolidayOnTodayBeatsHalfTermTomorrow`: Cornwall on today, Half term starting 29 Oct → Cornwall.
    - `tomorrowsEventWhenNothingIsOnToday`.
    - `theMostRecentlyStartedWins`.
    - `endingTodayOrBeyond90DaysIsIgnored`.
  - `HolidayWordsTest`:
    - the Copy's sheet lines with dates;
    - "Back Sat 31 Oct";
    - "From the calendar: Cornwall, ends Sunday." (within 6 days) and "…, ends Saturday 2 January." (beyond);
    - "Pick the day you’re back.";
    - `tileSpoken(false, null)` is "Holiday mode, off."; `tileSpoken(true, …)` is "Holiday mode, on, back Saturday 31 October.".

  Run `./gradlew :capability:holiday:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces and rulings 3–8 and 13. `HolidayScheduler` follows ruling 7. Log lines carry the step name and the result's class only.
- [ ] **Step 3: The gate and the convention tests** (`LogHygieneTest` names `HolidayHouse.kt` and `HolidaySettings.kt`), then commit.
```bash
git add capability/holiday settings.gradle.kts app/build.gradle.kts README.md build-logic
git commit -m "Add Holiday mode's record and scheduler: lights and Eco on when it turns on, the hoover at 10:00 the day before, Eco off at 00:01 on the back day, retried every five minutes"
```

---

### Task 6: `:capability:holiday` 2: the tile, the sheet, the PIN (spec §5.2, §5.8; rulings 13, 14; Review Focus 4)

**Review:** opus. Covers access and the sheet's choices.

**Files:**
- Create in `capability/holiday/.../`:
  - `HolidayActions.kt`;
  - `HolidayCapability.kt` (`id = "holiday"`, `order = 75`, no tab, `trayButtons()` = `[TrayButton("holiday.mode", 10) { HolidayTileHost(...) }]` always);
  - `ui/HolidayTile.kt`, `ui/HolidaySheet.kt`, `ui/HolidayDimens.kt`.
- Modify `core/ui` `Icons.kt` (`BEACH_ACCESS`), `tools/fonts/icons.txt` and the subset font.
- Test:
  - `HolidayActionsTest`, `ui/HolidayTileTest`, `ui/HolidaySheetTest`;
  - `ui/HolidayScreenshotTest`: `holiday_tile_off`, `holiday_tile_on`, `holiday_tile_line`, `holiday_sheet_from_calendar`, `holiday_sheet_pick`, `holiday_sheet_change_date`, each `_dark` and `_light`, with the tile in an `HhTrayColumn` 333 wide above a Hoover-sized row.

**Interfaces:**
- Consumes: Task 5's `HolidayHouse` and `HolidayWords`; `Optional<MultiDayEvents>`; `AccessControl`; `OverlayHost`; `HhSheet`, `HhDateGrid`, `HhButton`; `rememberSingleAction`.
- Produces:
  - `@Singleton class HolidayActions internal constructor(access: AccessControl, house: HolidayHouse, events: MultiDayEvents?, today: () -> LocalDate)`, `@Inject` over `Optional<MultiDayEvents>` and `HouseholdClock`, with:
    - `suspend fun ask(): Authorised?` (`access.authorise(HOLIDAY_MODE)`);
    - `suspend fun pick(): BackDatePick?`;
    - `suspend fun toggle(open: (Authorised) -> Unit)`: off → `ask()` then `open(by)`; on → `ask()` then `turnOff(by)`.
  - `HolidayTile(on, back, line, running, onTap)`, with tags `holiday_tile`, `holiday_tile_on`, `holiday_tile_back` and `holiday_tile_line`.
  - `HolidaySheet(back, pick, steps, choosing, turning, onChangeDate, onPickDate, onTurnOn, onCancel)`, with tags `holiday_sheet`, `holiday_back`, `holiday_change_date`, `holiday_from_calendar`, `holiday_steps`, `holiday_turn_on` and `holiday_cancel`.

- [ ] **Step 1: The failing tests.** Screen tests end with `assertClickablesHaveRoleAndLabel()`.
  - `HolidayActionsTest`:
    - `anAdultsPinOpensTheSheet`: `asked == [["holiday.mode"]]`, and `open` is called once.
    - `aChildsPinChangesNothing` (Review Focus 4): no `open`, no `turnOff`.
    - `aCancelledPadChangesNothing`.
    - `whileOnAPinTurnsItOff`: no sheet.
    - `everyTapAsksAgain`.
  - `HolidayTileTest`:
    - `offReadsAsTheSpecSays`: "Holiday mode"; spoken "Holiday mode, off." with the click label "Turn on".
    - `onShowsOnAndTheBackDate`: "ON", "Back Sat 31 Oct"; spoken "Holiday mode, on, back Saturday 31 October." with the click label "Turn off".
    - `aStepsLineShowsUnderIt`.
    - `tapsWhileTurningAreIgnored` (Review Focus 4): with `running = TURNING_ON`, "Turning on…" shows and a tap does nothing.
  - `HolidaySheetTest`:
    - `aCalendarPickFillsTheDate`: "Sunday 1 November" and "From the calendar: Cornwall, ends Sunday."; the group is spoken "When are you back? Sunday 1 November."; "Change date" is a button.
    - `noPickMeansTomorrow`: tomorrow, and "Pick the day you’re back.".
    - `changeDateOffersTomorrowTo90Days`: today and today + 91 can't be tapped; picking a day updates the date and the steps' dates.
    - `onlyTheStepsThatWillHappenAreListed`: with no hoover, no hoover line.
    - `turnOnTurnsOnAndCloses` (Review Focus 4): one `turnOn(back, by)`, then `dismiss`.
    - `aCancelledSheetChangesNothing` (Review Focus 4).
  - `HolidayCapabilityTest.theTileIsRow1`: one `TrayButton("holiday.mode", 10)`.
  - `HolidayScreenshotTest`: the 12 images.

  Run `./gradlew :capability:holiday:testDebugUnitTest :core:ui:testDebugUnitTest --tests '*Icons*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces, rulings 13–14, Copy and Units.
- [ ] **Step 3: Images.** **New:** the 12 above. **Check against** note 13's mode tile and the accepted brainstorm mockup ("When are you back?").
```bash
git add capability/holiday core/ui tools/fonts
git commit -m "Add the Holiday mode tile to the One-tap tray and the When are you back? sheet, behind a fresh Adult or Admin PIN both ways"
```

---

### Task 7: The door and the screen while away; the debug clock (spec §5.5, §5.6, §8.1; rulings 15–17)

**Review:** sonnet.

**Files:**
- Modify:
  - `capability/door/.../DoorAlerts.kt` (it takes `HouseMode`) and `capability/door/build.gradle.kts`;
  - `app/.../display/DisplayState.kt` (the `holiday` input, `next`, `moved`, `wantsCamera`) and `di/AppModule.kt` (the `WallClock` binding removed);
  - the manifests or registration as `DebugOfflineReceiver`'s.
- Create `app/src/release/.../di/ClockModule.kt`, `app/src/debug/.../di/DebugClockModule.kt` and `app/src/debug/.../DebugClockReceiver.kt`.
- Test: `DoorAlertsTest`, `DisplayStateTest` (`Wall` gains `holiday`), `DebugClockTest` in `app/src/testDebug` (or the debug test source the app uses).

- [ ] **Step 1: The failing tests.**
  - `DoorAlertsTest`:
    - `aRingWhileAwayIsRecordedQuietly`: `holiday = true` by day and in front → recorded, with no `show` and no chime.
    - `unknownIsNotAway`: null → as today (**pin**).
  - `DisplayStateTest`:
    - `holidayDarkensAtOnce`: `AWAKE` by day, then `holiday = true` → `ASLEEP`.
    - `holidayNeverShowsTheNightClock`: 22:00 passing → still `ASLEEP`.
    - `aTapWakesItAndItSleepsAgain`: a tap → `AWAKE`; `sleepAfterMinutes` later → `ASLEEP`, also at night.
    - `noCameraWhileAway`: `cameraWanted` is false, and `move()` leaves it `ASLEEP`.
    - `holidayEndingWakesIt`.
    - `heldStaysAwake`: the PIN pad holding → `AWAKE` until released.
  - `DebugClockTest`: the offset of 90 minutes moves `nowMillis` 5 400 000 ahead, and 0 clears it.

  Run `./gradlew :capability:door:testDebugUnitTest :app:testDebugUnitTest --tests '*Display*' --tests '*DebugClock*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 15–17. `assembleRelease` must still build: run `./gradlew :app:assembleRelease`, then `grep -c DebugClock app/build/outputs/mapping/release/mapping.txt` must print 0.
```bash
git add capability/door app
git commit -m "Keep the door quiet and the screen dark while Holiday mode is on, and add a debug-only clock offset for walking through its schedule"
```

---

### Task 8: The app wired in

**Review:** sonnet.

- [ ] **Step 1:**
  - `ShellViewModelTest.theHolidayTileComesFirst`: with the hoover and holiday capabilities, the tray is `["holiday.mode", "hoover.start"]`.
  - The app's Hilt graph test, or `HiltWiringTest` if present, resolves `HolidayHouse`, `HouseMode` and every `Optional<House…>` as present.
  - `NoBackupTest` (**pin**).
- [ ] **Step 2:**
  - Add the README rows for `:core:house` and `:capability:holiday`.
  - `TrayButton`'s KDoc already names Holiday mode 10; leave it.
  - Run `ShellScreenshotTest`: `home_tray_*` images now show the tile above the Hoover row. Record them as expected changes, listing them first.
- [ ] **Step 3:** The gate and the convention tests, then commit.
```bash
git add app README.md
git commit -m "Wire Holiday mode into the app, first in the One-tap tray"
```

---

### Task 9: Simplify the whole branch

- [ ] **Step 1:** Run the `simplify` skill over `git diff main...HEAD`. Look hardest at `HolidayHouse`'s step table, the four seam mappings, and `DisplayState`'s new branches.
- [ ] **Step 2:** For each change, run the affected module's tests, then the gate.
- [ ] **Step 3:** Commit when anything changed: `git commit -am "Simplify the Holiday mode code"`.

---

### Task 10: The emulator walk-through on `culvery-5` (spec §8.1)

**Rules** (as Plan 11a's Task 11):
- Use only the `culvery-5` AVD and a debug build with `install -r`.
- The user types the real PINs; Claude enters the debug sample PINs.
- Collect faults and ask before fixing.
- **The hoover runs, and the thermostat changes, only when the user says so.** Without their go, walk with the AVD's Home Assistant link removed: the steps then read "isn't linked" and can be watched without touching a device.
- The clock offset is set with `adb -s "$S" shell am broadcast -a uk.co.siland.culvery.debug.CLOCK --el minutes <n>` and cleared at the end.

- [ ] **Step 1: Build, install, walk.**

  **Not in this build, on purpose:** changing the back date while away; Arlo; Party mode; Settings › Home buttons; Eco put back if Nest changes it (§5.7). **Design cuts to say again:** collected in the build (the handoff's mode tile size and the sheet's layout against the mockup).
  1. Lights tab and Settings › Lights read "Holiday lights".
  2. Home: the tile is first in the tray; tap it; a Child's PIN is refused; an Adult's PIN opens the sheet.
  3. The sheet with a sample multi-day event on today ("From the calendar…"), and without one ("Pick the day you’re back."); Change date limits; the steps listed.
  4. Turn on: "Turning on…", then the screen goes dark; a tap wakes it; the tile reads ON and "Back …"; a debug ring is recorded with no card or chime, and the rail badge shows.
  5. The offset to 10:00 the day before: the hoover step runs (or reads its line); to 00:01: Eco off (or its line).
  6. Turn off with an Adult's PIN: the screen wakes, Holiday lights are off, the door is back to normal, and the record is cleared.
- [ ] **Step 2: The fault loop**, put back every setting, `emu kill`.
- [ ] **Step 3: Record** under "Plan 11b (Holiday mode): emulator walk-through". Commit: `git commit -am "Record Plan 11b's emulator walk-through"`.

---

### Task 11: On the tablet (spec §8.3)

**Needs the SM-T510 on the cable.**

4e's tablet rules apply:
- Signed release, `install -r`, never uninstall.
- Save and restore every setting changed.
- The log scan before any commit.

This run is real: Holiday lights, the thermostat's Eco and the hoover will act. **Ask the user before starting** and agree the back date with them.

- [ ] **Step 1:** Install the release (keep the mapping in `culvery-notes/mappings/`).
- [ ] **Step 2:** With the user, turn Holiday mode on with a back date a day or two ahead. Check:
  - the lights, Eco in the Nest app, the chime silent and the screen dark;
  - the next day at 10:00, the hoover;
  - at 00:01 on the back day, Eco off (read Home Assistant's history if nobody is up).
  Then turn it off.
- [ ] **Step 3: The log scan.** `adb -s <tablet> logcat -d -s HolidayHouse HolidayScheduler HolidaySettings DoorAlerts > "$LISTS/plan11b-log.txt"`, then grep it for the back date, event titles, the address and names: no match.
- [ ] **Step 4: Record** under "Plan 11b: on the tablet". Commit: `git commit -am "Record Plan 11b on the tablet"`.

---

### Task 12: Final review, merge, install

- [ ] **Step 1:** Opus reviews the whole branch against the spec and this plan, looking first at the PINs and grants, the schedule and restarts, and the logs. Then one fix wave, each fix with its failing test first, and the gate.
- [ ] **Step 2:** Finish with `superpowers:finishing-a-development-branch`: on the user's say, `git merge --ff-only plan-11b-holiday` on `main`, then the gate on `main`. Never push without asking. Move the design cuts to the follow-ups.
- [ ] **Step 3:** Install the release from the merged `main` on the tablet with `install -r`.

---

## Spec coverage (11b → tasks)

| Spec | Task |
|---|---|
| §2.3–§2.7, §2.11 the back day, explicit, the prefilled date, ends at home, the clean, the mockup's guesses | 5, 6, 7 |
| §2.12 Holiday lights | 3 |
| §2.13 Eco off once | 5 |
| §5.1 `:core:house`, the seams, optional parts | 2, 3, 5 |
| §5.2 the tile, the PIN, the sheet, the calendar pick, the range | 5, 6 |
| §5.3 turning on, failures retried, the hoover and Eco windows, the scheduler, BST | 5 |
| §5.4 turning off | 5 |
| §5.5 the door, §5.6 the screen | 7 |
| §5.7 the record | 5 |
| §5.8 TalkBack | 6 |
| §6 the Holiday row, privacy | 5, 6, 11 |
| §7 holiday logic, access, door and screen, UI | 5–8 |
| §8.1 the clock offset, §8.3 | 7, 10, 11 |
| Follow-ups M2–M4 | 1 |
