# Culvery Plan 22: Reminders — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The wall shows a reminder card with one chime at a set time (the family's own reminders, once or weekly, and "Bins out tonight" the evening before a collection), and keeps it until someone taps Done or Later.

**Architecture:**
- A new `:capability:reminders` module: a Room store (`reminders.db`), pure timing functions over the spec's "answered at" rule, and an engine (`Startable`) that decides each minute, and on any input change, whether the card is up and whether a chime is owed. It reads time only from `HouseholdClock` and `WallClock`.
- The alert layer gives each alert an owner's handle, so Door and Reminders can never remove or replace each other's card; an alert can say it doesn't hold the wall awake; Reminders can wake a day dark screen and duck music for the chime's length.
- Bins come through a new `:core:house` seam the calendar implements from its stored events. The chime is a new generated built-in in `:core:sound`. The who-picker, the hour and minute steppers and the striped bar move from the calendar to `:core:ui`.

**Tech Stack:** Kotlin, Compose, Hilt, Room, coroutines, JUnit4, Robolectric, Roborazzi. Python (standard library) for the chime, fontTools for the icon subset. No new library.

**Spec:** `docs/superpowers/specs/2026-11-17-culvery-22-reminders-design.md` (amended after the plan review). Read it with this plan; section numbers below are the spec's.

## Global Constraints

- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug :build-logic:convention:test --continue`. Baselines are recorded with `recordRoborazziDebug` on Windows and committed under `<module>/src/test/screenshots/`. A re-record rewrites unrelated baselines: restore each one the task didn't mean to change, file by file, then verify.
- **Temp dir:** never point Gradle's temp dir at a long path (Robolectric database paths pass MAX_PATH). If a scratch folder is needed use a short one such as `C:/t22`.
- **Git:** commits on `plan-22-reminders` only; plain messages, no Co-Authored-By or any attribution trailer; never a blanket `git checkout -- .` or `git restore .`; no worktrees.
- **Deprecated APIs:** none without asking the user first. If a step turns out to need one, or the compiler reports a deprecation in code the step touches: STOP and ask.
- **No new Android permission, no AlarmManager or WorkManager, no network call.**
- **Privacy:** a reminder's words, people's names, person ids and chosen times never reach a log, a test fixture name, a screenshot outside the sample household (Alex, Sam, Mia), or a commit. Every type that carries words, a person or a chosen time hides them in `toString`: `Reminder`, `ReminderDraft`, the entity, `Schedule`, `Due`, `CardUi`, `RowUi`, `WhoOption`.
- **Logging:** tag `Reminders`, `Log.i`, lines exactly: `due 2`, `card up 1 of 2`, `chime`, `held night`, `held holiday`, `held ring`, `held away`, `done`, `later`, `bins due`, `bins cleared`. One `Log.w` beside them: `A pass failed (<exception class>)` (ruling 13). `LogHygieneTest` must pass.
- **Copy:** every shown string is the spec's, verbatim, with curly apostrophes; none says master, kiosk, sync, read-only, assign or feed. `CopyScanTest` must pass with no new allow-list entry.
- **Comments:** one line, only where the code can't say it.
- **Module boundary:** `:capability:reminders` depends on `:core:*` only, never `:capability:calendar` or `:capability:door`.
- **Values:** words 1 to 60 characters after trimming; at most 50 reminders (bins aside); Later is 30 minutes; a one-off's day runs from today to a year ahead; bins time 18:00 unless changed, from 12:00 up to the start of night hours; bins clear at 12:00 on collection day; rail order 25.

## Rulings made while planning (the spec is the authority; each is cheap to reverse)

1. **Alert API (spec 9):** `show` returns an `AlertHandle`; `AlertHost.dismiss()` goes. Reminders shows with `showIfFree`, which never replaces. The card's replacement is read from its handle, not from `DoorQuiet.ringShowing`.
2. **Bins state (spec 9):** in `reminders.db`, the single-row table `reminder_state`, beside the engine's night and Holiday mark.
3. **The chime paid mark:** one column, `chimedAtMillis`. A chime is owed when the latest of (the due time, a snooze's end, the last time night or Holiday ended) is after it. The end of night or Holiday is stored when the engine sees it, so a restart keeps it.
4. **A chime plays only on a lit wall** (spec 4.4 "never plays to a dark wall"): the engine wakes the wall, and pays the chime on the pass that sees `ScreenLit.lit`.
5. **`NightHours` gains `startsAt()`**, because the bins time rule (4.7) needs the start of night hours and the seam only says whether it is night.
6. **Door's volume is left alone** (the user's ruling; spec 4.4 amended). The chime plays at `DoorQuiet.volume` whatever is connected, and at 1 only with no door capability.
7. **Asking who's there** (the user's ruling; spec 4.6 amended): the form opens with no PIN, as Add event does, and `Add reminder`, `Save changes` and `Delete reminder` ask. The bins sheet does the same at its `Save changes`. A child's PIN still in force when the form opens makes it theirs from the start (`AccessControl.session`, as the calendar's editor reads it).
8. **The minute stepper moves in fives.** The stored time is to the minute; the hour and minute steppers leave the arithmetic to the caller.
9. **A change to words or who keeps "answered at" and the snooze;** only a change of time, day or weekdays resets them (4.2).
10. **A waiting repeating reminder is listed under Waiting only,** not again under Today or Coming up.
11. **Setup state** comes from `:core:setup`'s `SetupState`; Reminders is the first capability to depend on it outside tests.
12. **Copy the spec doesn't give:** `Done`, `Later`, `Add reminder`, `Save changes`, `Delete reminder`, `Waiting`, `Today`, `Coming up`, `Once`, `Repeats`, `Reminder deleted.`, the field label `Reminder` with placeholder `What’s it for?`, `Shows as Family’s` under Everyone, PIN pad labels `Add a reminder`, `Change this reminder`, `Change the bins reminder`, the badge's spoken `2 waiting`, `<Name> can add reminders for <Name> only.` (the calendar's pattern), repeat words for other sets (`Mon, Wed, Fri`), and a due time on another day as `Mon 16:30`.
13. **A pass that throws** logs `Log.w(TAG, "A pass failed (${e::class.simpleName})")`, the one line outside spec 6's list: the class name only, never the message.
14. **No Undo on delete** (the user's ruling; spec 4.6 amended): a delete needs a PIN and shows `Reminder deleted.`, as a deleted event shows its line.
15. **Two minutes after a PIN** anyone at the wall can change a reminder or the bins reminder without one (the user's ruling: the events rule, accepted). Nothing in Reminders ends the session early.
16. **A started bins reminder keeps its collection** (the user's ruling; spec 4.7 amended): the first pass that finds a bins reminder waiting stores that collection in `reminder_state`; it stays waiting until Done or 12:00 on its day even if the calendar stops listing it. While the calendar still lists it, the calendar's bins win. A collection that goes before its reminder starts has none.
17. **The bins seam gives today and tomorrow only:** a reminder never needs a later collection, and the tab lists none.
18. **The alert layer being taken** has one signal: `showIfFree` answering null. `Wall` carries no flag for it.

## Review Focus

1. Two quick taps on Done with two reminders waiting: the second tap must not answer the next card (Task 7 `aSecondDoneForTheSameKeyLeavesTheNextCard`, Task 8 `doneAndLaterCarryTheKeyTheyWereDrawnWith`).
2. A one-off whose time passes while the form or the PIN pad is open: Save says "Pick a time that hasn’t passed." and stores nothing (Task 10 `aTimeThatPassedWhileThePinPadWasUpIsRefused`).
3. The wall clock set back (a network time fix, the debug clock after a restart) while a reminder is snoozed: the card must not stay closed longer than 30 minutes (Task 6 `aSnoozeFurtherAheadThanLaterHasExpired`).
4. The chime's file can't open: the card still shows and the chime isn't tried again every minute (Task 7 `aChimeThatCannotPlayIsNotTriedAgain`).
5. A pass that throws (a failed write): the engine keeps running, so later reminders still show (Task 7 `aPassThatThrowsLeavesTheEngineRunning`).

## File map

Roots: `PLUGIN` = `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin`, `UI` = `core/ui/src/main/java/uk/co/siland/culvery/core/ui`, `CAL` = `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar`, `DOOR` = `capability/door/src/main/java/uk/co/siland/culvery/capability/door`, `APP` = `app/src/main/java/uk/co/siland/culvery`, `R` = `capability/reminders/src/main/java/uk/co/siland/culvery/capability/reminders`. Tests sit at the same path under `src/test`.

| New file | Holds |
|---|---|
| `R/Reminder.kt` | `Reminder`, `ReminderDraft`, `Schedule`, `ReminderState`, limits, `tidyWords` |
| `R/db/RemindersDatabase.kt`, `R/RemindersStore.kt` | the two tables; every write |
| `R/Timing.kt`, `R/BinsTiming.kt` | pure: due, snoozed, chime owed; the bins window |
| `R/Showing.kt`, `R/RemindersEngine.kt` | pure: why the card is down, whether sound is allowed; the loop |
| `R/RemindersView.kt`, `R/RepeatWords.kt`, `R/People.kt` | what the card and the tab show |
| `R/RemindersPermissions.kt`, `R/RemindersActions.kt`, `R/ReminderForm.kt` | who may do what; add, change, delete, bins; the form's state |
| `R/RemindersCapability.kt`, `R/di/RemindersModule.kt` | tab, badge, bindings |
| `R/ui/ReminderCard.kt`, `ReminderCardHost.kt`, `RemindersTab.kt`, `ReminderSheet.kt`, `BinsSheet.kt`, `RemindersDimens.kt` | drawing only |
| `UI/WhoPicker.kt`, `UI/TimeSteppers.kt`, `UI/StripedBar.kt` | the moved controls |
| `CAL/CalendarBinCollections.kt` | the bins seam's implementation |
| `tools/sound/reminder_chime.py` | the chime's generator |

---

### Task 1: Move the shared pickers to `:core:ui`

Moved, not copied. `:core:ui` has no `:core:household`, so the who-picker takes plain values. `HhDateGrid` and `HhTimeStepperRow` are already there.

**Files:**
- Create: `UI/WhoPicker.kt`, `UI/TimeSteppers.kt`, `UI/StripedBar.kt`
- Modify: `CAL/ui/WhoPane.kt`, `CAL/Who.kt`, `CAL/EventWhen.kt`, `CAL/ui/StartsEndsPanes.kt`, `CAL/ui/Components.kt`, `CAL/ui/BinStripes.kt`, `CAL/ui/CalendarType.kt`
- Delete: `CAL/ui/TimeSteppers.kt`
- Move: `capability/calendar/src/test/.../ui/TimeSteppersTest.kt` → `core/ui/src/test/.../HhTimeSteppersTest.kt` (package and the call's name change; assertions untouched)
- Test: `core/ui/src/test/.../WhoPickerTest.kt`, `FormPiecesScreenshotTest.kt`

**Interfaces (produces):**
- `data class WhoOption(val key: String, val name: String, val role: String, val colour: Long)`, printing only its type name. `everyoneColour` is passed in because `FAMILY_COLOR` lives in `:core:household`.
- `fun <T> toggledWho(current: List<T>, tapped: T, family: T, mayClear: Boolean = false): List<T>`: the body of the calendar's `toggled`.
- `fun <T> lockedForWho(option: T, chosen: List<T>, only: T?): Boolean`: the body of `lockedFor`.
- `@Composable fun HhWhoPicker(options: List<WhoOption>, chosen: Set<String>, everyone: Boolean, everyoneLine: String, everyoneColour: Long, locked: (key: String?) -> Boolean, enabled: Boolean, onPick: (key: String?) -> Unit, onRefused: () -> Unit, modifier: Modifier = Modifier)`. A null key is Everyone. A tap on a locked chosen tile does nothing; on a locked unchosen one it calls `onRefused`. Test tags stay `who_<name>`, `who_everyone`, with `_dot`, `_check`, `_lock`.
- `enum class ClockField { Hour, Minute }`; `@Composable fun HhTimeSteppers(time: LocalTime, tagPrefix: String = "", onStep: (ClockField, Boolean) -> Unit, enabled: (ClockField, Boolean) -> Boolean = { _, _ -> true }, modifier: Modifier = Modifier)`.
- `data class Stripe(val fill: Color, val edge: Color? = null)`; `@Composable fun HhStripedBar(stripes: List<Stripe>, width: Dp, modifier: Modifier = Modifier, radius: Dp = width / 2)`.
- The tiles' and steppers' sizes and type styles move with them into `WhoPickerTokens` and `TimeStepperTokens`; a token the calendar still uses elsewhere stays where it is.

What stays in the calendar, so its tests and baselines pass unedited: `WhoPane(form, members, childOnly, childName, busy, onRefusedWho)` as a wrapper (the picker, then its own hint or locked note, `everyoneLine = SHOWS_AS_FAMILY`); `lockedFor` and `toggled` as one-line calls to the moved functions; `typealias ClockField = uk.co.siland.culvery.core.ui.ClockField` in `EventWhen.kt`; the `StripedBar` overloads for colours, people and `List<Stripe>`, calling `HhStripedBar`, with `typealias Stripe = uk.co.siland.culvery.core.ui.Stripe`, so `ComingUpCard`, `EventDetailSheet`, `TodayCard` and `WeekView` are unedited.

- [ ] **Step 1: Write the failing tests.** `WhoPickerTest`: `familyReplacesThePicksAndAPersonReplacesFamily`, `theLastPersonStaysUnlessMayClear`, `aChildsTilesAreLockedOnceAnyoneIsChosen` (the cases `WhoTest` and `WhoPaneTest` already pin, on `String` keys), `aLockedUnchosenTileCallsOnRefused`. `FormPiecesScreenshotTest`: `whoPickerDark` → `who_picker_dark`, `timeSteppersDark` → `time_steppers_dark`, with the sample household.
- [ ] **Step 2:** Run `./gradlew :core:ui:testDebugUnitTest --tests '*WhoPicker*' --tests '*HhTimeSteppers*'`. Expect FAIL (unresolved).
- [ ] **Step 3:** Move the code. If a control can't move without a calendar type, stop and report it rather than copying.
- [ ] **Step 4:** Run `./gradlew :core:ui:testDebugUnitTest :capability:calendar:testDebugUnitTest :capability:calendar:verifyRoborazziDebug`. Expect PASS with no calendar test or baseline edited. Record the two new `:core:ui` baselines.
- [ ] **Step 5:** Commit: `git commit -m "Move the who-picker, time steppers and striped bar to core ui"`.

### Task 2: The alert layer, and Door's side of it

**Stronger reviewer: state-heavy.**

**Files:**
- Modify: `PLUGIN/Alerts.kt`, `APP/shell/ShellOverlays.kt` (`ShellAlerts`), `APP/SetupWiring.kt` (`holding`), `APP/MainActivity.kt` (the `held` wiring), `APP/display/DisplayState.kt`, `DOOR/DoorAlerts.kt`
- Move: `FakeAlertHost` from `capability/door/src/test/.../TestDoor.kt` to `core/plugin/src/testFixtures/.../test/FakeAlertHost.kt`; `TestDoor.kt` keeps a `typealias FakeAlertHost`, and the moved fake keeps the members `DoorTabTest`, `DoorCardTest`, `DoorCapabilityTest` and `DoorAlertsInjectedTest` read, so those four are unedited.
- Modify (to compile): `capability/taekwondo/src/test/.../TestTaekwondo.kt`, `app/src/test/.../shell/ui/ShellScreenshotTest.kt`, `app/src/test/.../DisplayWiringTest.kt` (the named argument `alertShowing` becomes `alertHolds`; its positional `DisplayState` constructor call gains the new parameter)
- Test: `app/src/test/.../shell/ShellAlertsTest.kt`, `SetupWiringTest.kt`, `DisplayWiringTest.kt`, `display/DisplayStateTest.kt`, `capability/door/src/test/.../DoorAlertsTest.kt`

**Interfaces (produces):**

```kotlin
/** An owner's hold on the alert layer. */
interface AlertHandle {
    /** True while this alert is the one on the layer; false for good once it is replaced or dismissed. */
    val showing: StateFlow<Boolean>
    /** Takes this alert off the layer if it is still the one showing; nothing otherwise. */
    fun dismiss()
}

interface AlertHost {
    val inFront: StateFlow<Boolean>
    val showing: StateFlow<Boolean>
    val lowered: StateFlow<Boolean>
    /** Replaces any alert there. [holdsAwake] false: the wall's sleep timer runs as if the alert weren't there. */
    fun show(loud: Boolean = false, holdsAwake: Boolean = true, content: @Composable () -> Unit): AlertHandle
    /** A quiet alert onto an empty layer only: null, and nothing changed, while any alert shows. */
    fun showIfFree(holdsAwake: Boolean = true, content: @Composable () -> Unit): AlertHandle?
    suspend fun quieted()
    /** Wakes a dark screen as someone moving in front of it would: by day and in front only, never the night clock or on Holiday mode. */
    fun wake()
    /** Turns the quieters down, returns once they finish or after [QUIET_CAP_MS], and restores them [forMillis] later. Nothing while a loud alert shows; a loud alert showing when the restore comes due keeps them down. Never sets [lowered]. */
    suspend fun duck(forMillis: Long)
}
```

- `AlertHost.dismiss()` is removed.
- `ShellAlerts` adds `val holds: StateFlow<Boolean>` (the alert showing holds the wall awake) and `val wakes: SharedFlow<Unit>` (one per `wake()`, buffer 1, drop oldest).
- `holding(pinShowing, sheetShowing, alertHolds, settingsOpen, complete)`: the third parameter is renamed; `MainActivity` passes `alerts.holds`.
- `DisplayState`'s internal constructor takes `alertWakes: Flow<Unit>` after `kioskExited` (`wakes` is already a property there); each one runs the rule `moved()` runs. The injected constructor passes `alerts.wakes`.
- `DoorAlerts` keeps the handle its `show` returned and closes with `handle.dismiss()`.
- `FakeAlertHost` (testFixtures) behaves as the shell does for handles and records `shown`, `dismissed`, `loud`, `holdsAwake`, `content`, `wakes: Int`, `ducks: List<Long>`, and keeps `quietedGate` and `quietedCalls`.

- [ ] **Step 1: Write the failing tests.**
  - `ShellAlertsTest` (existing tests move to `handle.dismiss()`):
    - `aHandleDismissesOnlyItsOwnAlert`: `a = show {}`, `b = show {}`, `a.dismiss()` leaves `content` as b's and `showing` true; `a.showing.value == false`, `b.showing.value == true`.
    - `showIfFreeNeverReplaces`: with an alert up `showIfFree {}` is null and `content` is unchanged; after its dismiss `showIfFree {}` returns a handle whose `showing` is true.
    - `aReplacedHandleSaysSo`: `h = showIfFree {}`, then `show(loud = true) {}`: `h.showing.value == false`, and `h.dismiss()` leaves the loud alert up.
    - `aNonHoldingAlertDoesNotHold`: `showIfFree(holdsAwake = false) {}` gives `holds == false` with `showing == true`; a `show(loud = true) {}` gives `holds == true`.
    - `wakeIsOneSignal`.
    - `aDuckQuietsThenRestoresAfterItsLength`: `duck(1_100)` returns with quiet run once and restore not; after 1 100 ms restore has run once; `lowered` stayed false.
    - `aDuckUnderALoudAlertDoesNothing`; `aLoudAlertDuringADuckKeepsMusicDown` (no restore until the loud alert is dismissed).
  - `SetupWiringTest`: `aNonHoldingAlertDoesNotHoldTheWall` (`holding(false, false, alertHolds = false, false, true) == false`), `theDoorCardStillHolds`.
  - `DisplayStateTest`: `anAlertsWakeWakesADarkScreenByDayOnce` (asleep by day in front: one wake gives `AWAKE` and one `dayWakes`; with nothing held it sleeps again after the sleep time), `anAlertsWakeNeverWakesTheNightClockOrHoliday`, `anAlertsWakeOnALitWallRestartsTheWait`.
  - `DisplayWiringTest`, through the real `ShellAlerts`, `holding()` and `DisplayState`: `aNonHoldingAlertLetsTheWallSleepByDay` (`showIfFree(holdsAwake = false) {}` on an awake wall by day: `ASLEEP` after the sleep time, the alert still showing), `aNonHoldingAlertLetsTheNightClockReturn` (at night, tapped awake with that alert up: `NIGHT_CLOCK` after `NIGHT_AWAKE_MS`), `aHoldingAlertStillHolds` (the same day case with `show {}` stays `AWAKE`).
  - `DoorAlertsTest`: `aCloseAfterAnotherAlertTookTheLayerRemovesNothing` (ring; another owner's `host.show {}`; after `CARD_MS` that alert still shows), `aRingTakesTheLayerFromAnotherOwner` (a `showIfFree` handle goes false; the door card is up, loud, holding).
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*ShellAlerts*' --tests '*SetupWiring*' --tests '*DisplayState*' :capability:door:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. In `ShellAlerts` every change to the content, the current handle, `holds` and the loud passes happens under the one lock it has; a duck's passes join the same chain as the loud passes.
- [ ] **Step 4:** Run `./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug :capability:door:testDebugUnitTest :capability:taekwondo:testDebugUnitTest`. Expect PASS, no baseline changed.
- [ ] **Step 5:** Commit: `git commit -m "Give each alert an owner's handle; let an alert not hold the wall"`.

### Task 3: The reminder chime

**Files:**
- Create: `tools/sound/reminder_chime.py`; generated `core/sound/src/main/res/raw/reminder_chime.wav`
- Modify: `core/sound/src/main/java/uk/co/siland/culvery/core/sound/BuiltInSound.kt`, `SoundLibrary.kt`, `SoundPlayer.kt`
- Test: `core/sound/src/test/.../BuiltInSoundTest.kt`, `SoundLibraryTest.kt`, `SoundPlayerTest.kt`

**Interfaces (produces):**
- `BuiltInSound.REMINDER("reminder", "Reminder chime", R.raw.reminder_chime, null)`.
- `const val REMINDER_CHIME_MS = 1_100L` in `SoundPlayer.kt`.
- `suspend fun SoundPlayer.playReminder(volume: Float): PadStart`: `DOORBELL` while the doorbell's sound opens or plays; otherwise it plays in the pad slot, so a ring cuts it, without changing `padPlaying` or `previewing`. If the pad slot's start path always sets that state, give it a private flag; never a second player.
- `SoundLibrary` leaves `REMINDER` out of its built-ins, so `sounds`, `choices` and `byId` never hold it.
- The generator follows `door_chime.py` (`write_peak`, 22 050 Hz, headroom 0.95): two notes, G5 783.99 Hz at 0.0 s and C6 1046.50 Hz at 0.18 s, length 1.1 s.

- [ ] **Step 1: Write the failing tests.** `SoundLibraryTest.theReminderChimeIsInNoList` (`sounds.value`, `choices.first()` and `byId("builtin:reminder")` hold none). `SoundPlayerTest`: `theReminderChimeRefusesWhileTheDoorbellOpensOrPlays` (returns `DOORBELL`, opens nothing), `aRingCutsTheReminderChime`, `theReminderChimeLeavesThePadStateAlone`. `BuiltInSoundTest.theReminderChimeLastsAboutASecond` (the WAV's frames over its rate is within 50 ms of `REMINDER_CHIME_MS`); `everySoundOpens` now covers it. The existing assertions that list or count the built-ins change with it: `BuiltInSoundTest.kt:15-16` (every entry, `REMINDER` included) and `SoundLibraryTest.kt:98,105` (the library's built-ins, `REMINDER` left out).
- [ ] **Step 2:** Run `./gradlew :core:sound:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Write the generator, run `python tools/sound/reminder_chime.py` from the repo root, and implement the three changes.
- [ ] **Step 4:** Run `./gradlew :core:sound:testDebugUnitTest :capability:door:testDebugUnitTest :capability:party:testDebugUnitTest`. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add the reminder chime"`.

### Task 4: The bins seam, and when night starts

**Files:**
- Modify: `core/house/src/main/java/uk/co/siland/culvery/core/house/HouseSeams.kt`, `di/HouseModule.kt`, `CAL/di/CalendarModule.kt`, `PLUGIN/NightHours.kt`, `APP/display/DisplayState.kt`
- Create: `CAL/CalendarBinCollections.kt`
- Test: `capability/calendar/src/test/.../CalendarBinCollectionsTest.kt`, `app/src/test/.../display/DisplayStateTest.kt`

**Interfaces (produces):**
- In `HouseSeams.kt`:
  - `data class HouseBin(val name: String, val inkId: String?)`: the wall name ("Food") and the chosen `BinInks` id, null for a kind with none.
  - `data class BinCollection(val date: LocalDate, val bins: List<HouseBin>)`
  - `data class BinDays(val connected: Boolean, val readFailed: Boolean, val collections: List<BinCollection>)`
  - `interface BinCollections { val days: Flow<BinDays> }`
- `HouseModule`: `@BindsOptionalOf abstract fun binCollections(): BinCollections`. `CalendarModule`: `@Binds abstract fun binCollections(impl: CalendarBinCollections): BinCollections`.
- `CalendarBinCollections @Inject constructor(clock: HouseholdClock, store: CalendarStore, connections: CalendarConnections, colours: BinColours, zone: HouseholdZone)`, built as `CalendarBirthdays` is. `collections`: the all-day events with `bins` from `store.eventsBetween` (so only calendars the wall shows) dated today or tomorrow, following the day as it changes, one per date with kinds merged through `orderedBins`, names from `binLabel`, ink from `BinColours`. `connected`: a connection row with `carriesBins`. `readFailed`: such a row whose health isn't `ConnectionHealth.Ok`. It makes no council read.
- `NightHours`: `fun startsAt(): LocalTime? = null` (null while not known). `DisplayState` overrides it with the saved `nightFrom`.

- [ ] **Step 1: Write the failing tests.** `CalendarBinCollectionsTest`: `todayAndTomorrowInDateOrder` (yesterday's and the day after tomorrow's are left out), `theDaysFollowMidnight`, `twoConnectionsOnOneDateMerge` (one `BinCollection`, names `Food`, `Recycling` in that order), `aChosenColourFollows`, `aHiddenBinsCalendarGivesNoCollectionsButStaysConnected`, `aFailedReadSaysSoAndKeepsTheStoredDates`, `noBinsConnectionIsNotConnected`. `DisplayStateTest.nightStartIsTheSavedTime` (null before the prefs are read).
- [ ] **Step 2:** Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*BinCollections*' :app:testDebugUnitTest --tests '*DisplayState*'`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `./gradlew :core:house:testDebugUnitTest :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add the bins seam and the start of night hours"`.

### Task 5: The module, the alarm icon and the store

**Files:**
- Modify: `settings.gradle.kts` (`include(":capability:reminders")`), `UI/Icons.kt` (`const val ALARM = "alarm"`), `tools/fonts/icons.txt`, `core/ui/src/main/res/font/material_symbols_rounded.ttf` (re-subset per `tools/fonts/README.md`)
- Create: `capability/reminders/build.gradle.kts`, `capability/reminders/src/test/resources/robolectric.properties` (as Door's), `R/Reminder.kt`, `R/db/RemindersDatabase.kt`, `R/RemindersStore.kt`, `R/di/RemindersModule.kt`; generated `capability/reminders/schemas/**/1.json`
- Test: `capability/reminders/src/test/.../RemindersStoreTest.kt`, `ReminderTest.kt`

The build file follows Door's: plugins `culvery.android.library`, `culvery.android.compose`, `culvery.hilt`, `culvery.room`, roborazzi; `api(project(":core:plugin"))`; `implementation` of `:core:house`, `:core:household`, `:core:access`, `:core:ui`, `:core:sound`, `:core:setup`; test fixtures of `:core:ui`, `:core:plugin`, `:core:access`.

**Interfaces (produces):**

```kotlin
const val WORDS_MAX = 60
const val REMINDERS_MAX = 50
const val LATER_MS = 1_800_000L

sealed interface Schedule {
    data class Once(val date: LocalDate) : Schedule
    data class Weekly(val days: Set<DayOfWeek>) : Schedule   // never empty
}
data class ReminderDraft(val words: String, val forPeople: List<PersonId>, val time: LocalTime, val schedule: Schedule)
data class Reminder(
    val id: Long, val words: String, val forPeople: List<PersonId>, val madeBy: PersonId, val time: LocalTime,
    val schedule: Schedule, val answeredAtMillis: Long, val snoozedUntilMillis: Long?, val chimedAtMillis: Long?,
)
data class ReminderState(
    val binsOn: Boolean = true, val binsTime: LocalTime = LocalTime.of(18, 0), val binsDoneFor: LocalDate? = null,
    val binsSnoozedUntilMillis: Long? = null, val binsChimedAtMillis: Long? = null,
    val heldSeen: Boolean = false, val heldEndedAtMillis: Long? = null,
    val binsKept: BinCollection? = null,               // the collection a started bins reminder is for (ruling 16)
)
/** Line breaks and tabs as spaces, runs of spaces as one, trimmed. */
fun tidyWords(raw: String): String

@Singleton
class RemindersStore @Inject constructor(db: RemindersDatabase) {
    val reminders: Flow<List<Reminder>>
    val state: Flow<ReminderState>                     // the defaults before the first write
    suspend fun add(draft: ReminderDraft, madeBy: PersonId, nowMillis: Long): Long?   // null with REMINDERS_MAX stored
    suspend fun change(id: Long, draft: ReminderDraft, nowMillis: Long): Boolean       // false when it has gone
    suspend fun done(id: Long, nowMillis: Long)        // a one-off is deleted; a repeat's answeredAt is now, its snooze cleared
    suspend fun later(id: Long, nowMillis: Long)       // snoozedUntil = now + LATER_MS
    suspend fun chimed(ids: Collection<Long>, bins: Boolean, nowMillis: Long)
    suspend fun delete(id: Long): Boolean              // false when it had already gone
    suspend fun setBins(on: Boolean, time: LocalTime)
    suspend fun binsDone(date: LocalDate)
    suspend fun binsKept(collection: BinCollection?)
    suspend fun binsLater(nowMillis: Long)
    suspend fun markHeld(held: Boolean, nowMillis: Long)   // true then false stores heldEndedAtMillis = now
}
```

- `forPeople` is person ids, `PersonId.FAMILY` alone for Family. `Reminder`, `ReminderDraft` and the entity print only their type name and id; each `Schedule` prints only its type name.
- Tables, version 1, schema exported: `reminder` (`id` autogenerated key, `words`, `forPeople` comma-joined ids, `madeBy`, `minuteOfDay`, `onDate` ISO text or null, `weekdays` bit mask Monday 1 to Sunday 64 and 0 for a one-off, `answeredAtMillis`, `snoozedUntilMillis`, `chimedAtMillis`); `reminder_state` (`slot` key, always 0, then `ReminderState`'s fields; `binsKept` as two columns, `binsKeptDate` ISO text and `binsKeptBins`, each bin as name and ink id). `add` counts and inserts in one transaction, with `answeredAtMillis = nowMillis`.
- `RemindersModule` provides the database: `Room.databaseBuilder(context, RemindersDatabase::class.java, "reminders.db").build()`.

- [ ] **Step 1: Write the failing tests.** `RemindersStoreTest` (in-memory Room): `aOneOffAndARepeatRoundTrip`, `theFiftyFirstIsRefused` (`add` returns null, 50 stored), `doneDeletesAOneOffAndRestartsARepeat`, `aChangeOfTimeDayOrWeekdaysResetsAnsweredAtAndClearsTheSnooze`, `aChangeOfWordsOrWhoKeepsThem`, `laterIsThirtyMinutes`, `deletingTwiceSaysItHadGone`, `aKeptCollectionRoundTripsWithItsBinsAndInks`, `theStateStartsSwitchedOnAtSix`, `markHeldStoresWhenHoldingEnds` (false then false stores nothing). `ReminderTest`: `storedTypesHideTheirWordsAndPeople` (no `toString`, a `Schedule`'s included, holds the words, an id of a person, the day or the time), `wordsAreTidied` (`tidyWords("  PE\nkit ") == "PE kit"`).
- [ ] **Step 2:** Run `./gradlew :capability:reminders:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. Add the icon: the constant, the line in `icons.txt`, then the subset command in `tools/fonts/README.md`.
- [ ] **Step 4:** Run `./gradlew :capability:reminders:testDebugUnitTest :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug`. Expect PASS (`IconFontTest` included), `1.json` written, no baseline changed.
- [ ] **Step 5:** Commit: `git commit -m "Add the reminders module, its store and the alarm icon"`.

### Task 6: Timing

**Files:**
- Create: `R/Timing.kt`
- Test: `capability/reminders/src/test/.../TimingTest.kt`

**Interfaces (produces):**

```kotlin
sealed interface DueKey {
    data class Mine(val id: Long) : DueKey
    data class Bins(val date: LocalDate) : DueKey
}
/** One waiting thing: the latest of its times after "answered at" and at or before now. */
data class Due(val key: DueKey, val dueAtMillis: Long, val snoozedUntilMillis: Long?, val chimedAtMillis: Long?)   // prints its key only

internal fun timeOn(date: LocalDate, time: LocalTime, zone: ZoneId): Long    // ZonedDateTime.of's rule for a gap or an overlap
internal fun Reminder.due(nowMillis: Long, zone: ZoneId): Due?               // null when not waiting
internal fun Reminder.nextDue(afterMillis: Long, zone: ZoneId): Long?        // null for a one-off already past
internal fun Due.snoozed(nowMillis: Long): Boolean
internal fun Due.chimeOwed(nowMillis: Long, heldEndedAtMillis: Long?): Boolean
internal fun inOrder(dues: List<Due>): List<Due>                             // oldest due first; a tie puts Bins first, then the lower id
```

The two rules the signatures don't fix:
- `snoozed`: `snoozedUntil` is set, `now < snoozedUntil`, `snoozedUntil - now <= LATER_MS`, and `dueAt <= snoozedUntil - LATER_MS` (a newer time ends a snooze).
- `chimeOwed`: not snoozed, and the latest of `dueAt`, `snoozedUntil` and `heldEndedAt` (each only when at or before now) is after `chimedAt`; always owed with no `chimedAt`. A `chimedAt` after now (the clock was set back) counts as none.

- [ ] **Step 1: Write the failing tests** (`Europe/London`, fixed instants, a reminder built by a test helper that takes no words of note):
  - `aOneOffIsWaitingFromItsMinute`: once on 2026-11-20 at 16:30, answered 10:00 that day: null at 16:29:59, due at 16:30:00, and still that `Due` three days on.
  - `weeklyDaysFireOnTheirDays`: Monday, Wednesday, Friday 07:30: null on a Tuesday after Monday's was answered; Wednesday 07:30 gives Wednesday's instant.
  - `threeMissedTimesAreOneWaitingShowingTheLatest`; `aTimeMissedWhileOffIsStillWaiting`.
  - `doneEndsItUntilTheNextTime`: `answeredAtMillis = now` gives null, and `nextDue` the next chosen weekday.
  - `aSpringGapTimeFiresOnceAnHourOn`: Sunday 01:30 on 2027-03-28 is due at `2027-03-28T01:30:00Z`.
  - `anAutumnOverlapTimeFiresOnce`: Sunday 01:30 on 2027-10-31 is due at `2027-10-31T00:30:00Z`; answered a minute later, it is null at `01:30:00Z`.
  - `laterClosesItForThirtyMinutesThenOwesAChime`; `aSecondLaterMovesItOn`; `aNewerTimeEndsASnooze`.
  - `aSnoozeFurtherAheadThanLaterHasExpired`: `snoozedUntil = now + LATER_MS + 60_000` is not snoozed.
  - `aChimeIsOwedOncePerCause`: owed when due; not after `chimedAt = now`; owed again once `heldEndedAt` is later; not while snoozed across that end, then owed when the snooze ends.
  - `aChimeMarkAheadOfTheClockCountsAsNone`.
  - `aRepeatMadeAfterTodaysTimeWaitsForItsNextDay`: weekly on today's weekday at 07:30, made (answered) at 09:00 today: null all day, due at 07:30 a week on.
  - `aDueHidesItsTimes` (its `toString` holds no millis).
  - `oldestDueComesFirst`.
- [ ] **Step 2:** Run `./gradlew :capability:reminders:testDebugUnitTest --tests '*TimingTest*'`. Expect FAIL.
- [ ] **Step 3:** Implement. `due` for a weekly reminder looks back at most seven days from now's date in `zone`.
- [ ] **Step 4:** Run again. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add the reminder timing rules"`.

### Task 7: The engine

**Stronger reviewer: state-heavy.** Spec 4.4 is the authority for every row.

**Files:**
- Create: `R/Showing.kt`, `R/RemindersEngine.kt`
- Modify: `build-logic/convention/src/test/kotlin/LogHygieneTest.kt` (and `LogHygiene.ALLOWED` where it lives)
- Test: `capability/reminders/src/test/.../ShowingTest.kt`, `RemindersEngineTest.kt`, `LogHygieneTest.kt`

**Interfaces:**
- Consumes: Task 2's `AlertHost`, Task 3's `playReminder` and `REMINDER_CHIME_MS`, Task 5's store, Task 6's functions.
- Produces:

```kotlin
/** [ready]: setup complete and Culvery in front. [quiet]: door quiet hours. */
internal data class Wall(val ready: Boolean, val holiday: Boolean?, val night: Boolean?, val lit: Boolean, val quiet: Boolean)
internal enum class Held(val word: String) { AWAY("away"), HOLIDAY("holiday"), NIGHT("night"), RING("ring") }
/** Why the card is down, checked in that order (never RING: that is the layer answering null); null when it may show. Holiday not known holds as Holiday. Night holds only while the wall isn't lit; night not known holds whatever the wall. */
internal fun heldBy(wall: Wall): Held?
/** Day for certain, not quiet hours, and the wall lit. */
internal fun soundAllowed(wall: Wall): Boolean

/** [of]: how many wait unsnoozed; the card shown is always the first. */
data class Showing(val due: Due, val of: Int)

@Singleton
class RemindersEngine internal constructor(
    store: RemindersStore, host: AlertHost, minutes: Flow<LocalDateTime>, clock: WallClock, zone: suspend () -> ZoneId,
    setupComplete: Flow<Boolean>, holiday: StateFlow<Boolean?>, night: NightHours, lit: StateFlow<Boolean>, door: DoorQuiet?,
    chime: suspend (volume: Float) -> PadStart, scope: CoroutineScope, card: @Composable () -> Unit,
) : Startable {
    val waiting: StateFlow<List<Due>>     // every waiting reminder, snoozed ones included, in order
    val showing: StateFlow<Showing?>      // what the card shows; null while it is down
    fun done(key: DueKey)
    fun later(key: DueKey)
}
```

The injected constructor and the `Startable` binding come in Task 8, with the card.

**One pass** (run under one `Mutex`; the triggers are `minutes`, `store.reminders`, `store.state`, `setupComplete`, `host.inFront`, `host.showing`, `holiday`, `lit` and the live handle's `showing`, conflated):

1. Read now and the `Wall` (`door?.quietNow() ?: false`). When night and Holiday are both known, `store.markHeld(night || holiday, now)`.
2. `dues` = each reminder's `due`, `inOrder`; publish as `waiting`. More dues than last pass: log `due N`.
3. `open` = the dues not snoozed. The card is up when `open` isn't empty and `heldBy(wall)` is null.
4. Up with no live handle: `host.showIfFree(holdsAwake = false, card)`; a null answer is `Held.RING`. Not up with a live handle: `handle.dismiss()`. Publish `showing` (the first of `open`, with `open.size`). Log `card up 1 of N` when it changes, and `held <word>` when the reason changes while `open` isn't empty.
5. `owed` = `open` filtered by `chimeOwed`, reading each `chimedAt` as the later of the stored mark and the engine's own in-memory mark for that key. Card up and a key in `owed` that wasn't last pass: `host.wake()`.
6. Card up, `owed` not empty, `soundAllowed`: `host.wake()`, `host.duck(REMINDER_CHIME_MS)`. Then read the handle's `showing` again: false (a ring took the layer during the duck) leaves them owed and plays nothing. Otherwise `chime(door?.volume ?: 1f)`. `STARTED` or `FAILED` sets the in-memory mark for every owed key first, then `store.chimed`; `STARTED` logs `chime`. `DOORBELL` and `STOPPED` leave them owed. So a mark that can't be saved costs one repeat after a restart, not one a minute.

`done` and `later` take the same `Mutex`, do nothing for a key not in `waiting`, write the store, log `done` or `later`, and run a pass. A pass that throws anything but cancellation logs `Log.w(TAG, "A pass failed (${e::class.simpleName})")` (ruling 13) and the loop goes on.

**Logs:** the engine's calls are exactly `Log.i(TAG, "due ${dues.size}")`, `Log.i(TAG, "card up 1 of ${open.size}")`, `Log.i(TAG, "held ${held.word}")` and the fixed lines. `LogHygiene.ALLOWED` gains `dues.size`, `open.size` and `held.word` (and `e::class.simpleName` if it isn't there), each with its line in the comment above: counts and a fixed word, never the words, a name, a person id or a chosen time.

- [ ] **Step 1: Write the failing tests.**
  - `ShowingTest`: `awayThenHolidayThenNight` (the order), `nightHoldsOnlyWhileTheWallIsDark`, `anUnknownNightHoldsEvenOnALitWall`, `anUnknownHolidayHolds`, `soundNeedsDayOutsideQuietHoursOnALitWall`.
  - `LogHygieneTest.theRemindersLinesPass`: the three calls above pass; `Log.i(TAG, "due ${reminder.words}")` and `Log.i(TAG, "due at ${due.dueAtMillis}")` are each one problem. `theScanReachesTheShippedCode` also expects `RemindersEngine.kt`.
  - `RemindersEngineTest` (`runTest`, a settable clock, in-memory store, `FakeAlertHost`, a recording `chime`), one per row of 4.4's table and per line of spec 7:
    - `byDayOnALitWallTheCardShowsAndChimes`
    - `byDayOnADarkWallItWakesThenChimesOnceLit`: one `host.wakes` and no chime while `lit` is false; the chime after it turns true.
    - `leftAloneTheWallGoesDarkOverItSilently`: `lit` false then true: the handle still shows, one chime in all.
    - `aNewerTimeOnAWaitingReminderWakesAndChimesAgain`
    - `inDoorQuietHoursItShowsAndOwesUntilTheyEnd`: quiet hours end by the fake door's answer changing; the next minute's pass plays the chime (`quietNow` is read each pass, it is no flow).
    - `atNightOnTheClockItStaysDown`; `atNightATapShowsItSilentlyThenItComesDown`; `whenNightEndsItShowsAndChimesEvenIfItChimedTheEveningBefore`
    - `onHolidayItStaysDownAndChimesWhenHolidayEnds`
    - `aDoorCardTakesTheLayerAndTheReminderReturns`: a loud `host.show` drops `showing` to null; its dismiss brings the card back with no second chime.
    - `aChimeRefusedForARingIsStillOwed`: `DOORBELL`, then `STARTED` next pass: paid once.
    - `awayOrInSetupItStaysDown`: and on return the card shows with its one chime.
    - `theChimeIsPaidOnceAcrossARestart`: a second engine over the same database shows the card and plays nothing.
    - `nightEndingWhileTheWallWasOffStillOwesTheChime`: chimed in the evening, held at night, the engine stopped; a second engine over the same database started by day shows the card and chimes once.
    - `aRingDuringTheDuckLeavesTheChimeOwed`: a loud `host.show` lands inside `duck`: no chime then; one after the ring's card is dismissed.
    - `aChimeWhoseMarkCannotBeSavedPlaysOnce`: `store.chimed` throws every time; ten minutes on, `chime` was called once.
    - `aTimeMissedWhileOffIsWaitingAtStart`
    - `severalOwedShareOneChime`
    - `musicDucksForTheChimesLengthOnly`: `host.ducks == listOf(REMINDER_CHIME_MS)`; `host.loud` was never true.
    - `theCardNeverHoldsTheWall`: `host.holdsAwake == false`.
    - `doneMovesToTheNextAndLaterBringsItBackWithItsChime`
    - `doneOnAReminderThatHasGoneIsNothing`
    - `aSecondDoneForTheSameKeyLeavesTheNextCard`
    - `aChimeThatCannotPlayIsNotTriedAgain`: `FAILED` once; ten minutes on, `chime` was called once.
    - `aPassThatThrowsLeavesTheEngineRunning`: `chime` throws once; the next minute's reminder still shows.
- [ ] **Step 2:** Run `./gradlew :capability:reminders:testDebugUnitTest --tests '*Showing*' --tests '*RemindersEngine*' :build-logic:convention:test`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run again. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add the reminders engine"`.

### Task 8: The card

**Files:**
- Create: `R/People.kt`, `R/RemindersView.kt`, `R/ui/ReminderCard.kt`, `R/ui/ReminderCardHost.kt`, `R/ui/RemindersDimens.kt`
- Modify: `R/RemindersEngine.kt` (the injected constructor), `R/di/RemindersModule.kt` (`@Binds @IntoSet` the engine as a `Startable`)
- Test: `capability/reminders/src/test/.../PeopleTest.kt`, `RemindersViewTest.kt`, `ui/ReminderCardTest.kt`, `ui/ReminderCardScreenshotTest.kt`

**Interfaces (produces):**
- `data class StripeUi(val colour: Long? = null, val inkId: String? = null)`: a person's colour, or a bin's ink.
- `data class CardUi(val key: DueKey, val words: String, val stripes: List<StripeUi>, val who: String, val dueAt: String, val detail: String? = null, val of: Int? = null)`; `of` is set only when more than one waits, and the card then reads `1 of 2`. It prints only its type name and key.
- `internal fun forWho(ids: List<PersonId>, household: List<Person>): List<Person>`: those still in the household, in its order; Family, or nobody left, is `listOf(Person.Family)`.
- `internal fun dueText(dueAtMillis: Long, nowMillis: Long, zone: ZoneId): String`: `16:30` today, else `Mon 16:30`.
- `@Singleton class RemindersView @Inject constructor(store: RemindersStore, engine: RemindersEngine, household: HouseholdRepository, clock: HouseholdClock, zone: HouseholdZone)` with `val card: Flow<CardUi?>` from `engine.showing`. `who` joins the names with `HouseLines.roomList`.
- `@Composable fun ReminderCard(card: CardUi, onDone: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier)`: the door card's shape; the `Icons.ALARM` mark, the words, `HhStripedBar` and `who`, `dueAt`, `detail` under the words, `1 of 2`, then `Later` (quiet) and `Done` (primary). TalkBack reads one description, the words then who (then `1 of 2`), then the two buttons.
- `@Composable fun ReminderCardHost(view: RemindersView, engine: RemindersEngine)`: each button calls the engine with the key of the card it was drawn with. While `engine.showing` has a card and `view.card` has none for it (the row has gone, the view failed), it draws a bare card for the engine's key: the mark, `Reminder`, `Later` and `Done`. The layer is never a scrim with no buttons.
- The engine's injected constructor: `(store, host: AlertHost, clock: HouseholdClock, wall: WallClock, zone: HouseholdZone, setup: SetupState, house: HouseMode, night: NightHours, lit: ScreenLit, door: Optional<DoorQuiet>, player: SoundPlayer, view: Lazy<RemindersView>, @ApplicationScope scope)`.

- [ ] **Step 1: Write the failing tests.** `PeopleTest`: `aRemovedPersonDropsOff`, `nobodyLeftShowsAsFamilys`. `RemindersViewTest`: `theCardCarriesWhoAndTheDueTime`, `theCountShowsOnlyWhenSeveralWait`, `theDueTimeGainsItsDayAfterMidnight`, `theCardHidesItsWords` (`CardUi.toString`). `ReminderCardTest`: `theCardSaysTheWordsThenWho`, `laterIsQuietAndDonePrimary`, `doneAndLaterCarryTheKeyTheyWereDrawnWith` (two waiting: two taps on Done before the next frame call `done` with the first key both times), `detailShowsUnderTheWords`, `aCardWithNothingToDrawStillOffersDone` (the host with an engine key and a null `CardUi`: Done and Later are there and call the engine with that key). `ReminderCardScreenshotTest`, light and dark: `reminder_card_one`, `reminder_card_two`, `reminder_card_bins` (words `Bins out tonight`, who `Bins`, detail `Food, Recycling`, two ink stripes), `reminder_card_1_of_2`.
- [ ] **Step 2:** Run `./gradlew :capability:reminders:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run again, record the eight baselines, look at each, then `./gradlew :capability:reminders:verifyRoborazziDebug :core:ui:testDebugUnitTest --tests '*CopyScan*'`. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add the reminder card"`.

### Task 9: The tab, the badge and the list

**Files:**
- Create: `R/RepeatWords.kt`, `R/RemindersCapability.kt`, `R/ui/RemindersTab.kt`
- Modify: `R/RemindersView.kt`, `R/di/RemindersModule.kt` (`@Binds @IntoSet` the capability)
- Test: `capability/reminders/src/test/.../RepeatWordsTest.kt`, `RemindersViewTest.kt`, `RemindersCapabilityTest.kt`, `ui/RemindersTabTest.kt`, `ui/RemindersTabScreenshotTest.kt`

**Interfaces (produces):**
- `const val REMINDERS_ID = "reminders"`; `RemindersCapability`: label `Reminders`, icon `Icons.ALARM`, order 25, `hasTab` = `SetupState.setupComplete`, `badge` from `engine.waiting`.
- `internal fun badgeOf(waiting: Int): RailBadge?`: `RailBadge.Count(n, "$n waiting")`, null at 0.
- `internal fun repeatWords(schedule: Schedule): String`: `Once`; one day `Every Friday`; all seven `Every day`; three or more days in a row within Monday to Sunday `Mon to Fri`; anything else the short names joined, `Mon, Wed, Fri`.
- `data class RowUi(val key: DueKey?, val id: Long?, val stripes: List<StripeUi>, val time: String, val words: String, val who: String, val note: String?)`: `key` is set on a waiting row (its Done button), `id` on the family's own (a tap opens it). It prints only its type name, key and id.
- `data class BinsRowUi(val on: Boolean, val time: LocalTime, val cantRead: Boolean)`; `fun binsRowTitle(time: LocalTime) = "Bins · The evening before a collection, ${clockText(time)}"`; `const val CANT_READ_BINS = "Can’t read the bin dates"`.
- `data class TabUi(val waiting: List<RowUi>, val today: List<RowUi>, val comingUp: List<RowUi>, val bins: BinsRowUi?)`; `RemindersView.tab: Flow<TabUi>` (its `bins` stays null until Task 11).
- `@Composable fun RemindersTab(ui: TabUi, onAdd: () -> Unit, onOpen: (Long) -> Unit, onDone: (DueKey) -> Unit, onBins: () -> Unit)`. `NO_REMINDERS = "No reminders yet. Tap Add reminder to make one."` shows when the three groups are empty.

- [ ] **Step 1: Write the failing tests.** `RepeatWordsTest`: the five forms above and `Sat, Sun`. `RemindersViewTest`: `waitingRowsShowTheDueTimeOrBackAt` (a snoozed one's note is `Back at 18:30`), `todayHoldsNextTimesToday`, `comingUpIsByNextTimeWithItsRepeatWords`, `aWaitingRepeatIsListedOnce`, `theCardAndTodayFollowMidnight` (a row under Coming up for tomorrow is under Today one minute past midnight, with no write to the store). `RemindersCapabilityTest`: `theOrderIsTwentyFive`, `theBadgeCountsSnoozedOnesToo`, `noBadgeWithNothingWaiting`. `RemindersTabTest`: `aGroupWithNoRowsIsLeftOut`, `doneOnAWaitingRowNeedsNoPin` (calls `onDone` with the row's key), `theEmptyLineShowsWithNoReminders`, `theBinsRowShowsOnlyWhenGiven`, `theCantReadLineShowsUnderTheBinsRow`. `RemindersTabScreenshotTest`, light and dark: `reminders_tab_filled`, `reminders_tab_empty`, `reminders_tab_bins_cant_read`.
- [ ] **Step 2:** Run `./gradlew :capability:reminders:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement with `HhListRow`, `HhSectionLabel`, `HhButton`, `HhSwitch` and `HhStripedBar`. `TabContent` passes no-op `onAdd`, `onOpen` and `onBins` until Tasks 10 and 11.
- [ ] **Step 4:** Run again with `:core:ui:testDebugUnitTest --tests '*CopyScan*'`, record the six baselines, verify. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add the Reminders tab"`.

### Task 10: Adding and changing, and who may

**Stronger reviewer: security.** The rule is checked where the write happens, never only in the form.

**Files:**
- Move: `TestAccess` and `testAccess` from `capability/calendar/src/test/.../TestAccess.kt` to `core/access/src/testFixtures/.../test/TestAccess.kt`, taking `vararg sources: PermissionSource`; the calendar's file keeps a `typealias` and a forwarding `testAccess`, so its tests are unedited. The recording toaster it uses moves with it as a public class (a `typealias` stays behind if the calendar's tests name it).
- Modify: `core/access/build.gradle.kts`: the test fixtures gain what `TestAccess` needs, `testFixturesImplementation(testFixtures(project(":core:plugin")))`, AndroidX test core and coroutines-test, by the version catalog's names. If the move needs more than that, stop and report; don't copy the class.
- Create: `R/RemindersPermissions.kt`, `R/RemindersActions.kt`, `R/ReminderForm.kt`, `R/ui/ReminderSheet.kt`
- Modify: `R/RemindersCapability.kt` (`onAdd`, `onOpen`), `R/di/RemindersModule.kt` (`@Binds @IntoSet` the `PermissionSource`)
- Test: `capability/reminders/src/test/.../RemindersPermissionsTest.kt`, `RemindersActionsTest.kt`, `ReminderFormTest.kt`, `ui/ReminderSheetTest.kt`, `ui/ReminderSheetScreenshotTest.kt`

**Interfaces (produces):**

| Id | Label | Roles |
|---|---|---|
| `reminders.add` | Add reminders for anyone | Admin, Adult |
| `reminders.add.self` | Add your own reminders | everyone |
| `reminders.change` | Change any reminder | Admin, Adult |
| `reminders.change.own` | Change reminders you made | everyone |
| `reminders.bins` | Change the bins reminder | Admin, Adult |

```kotlin
internal fun mayChange(granted: Set<String>, who: Identified, madeBy: PersonId): Boolean
internal fun mayAddFor(granted: Set<String>, who: Identified, forPeople: List<PersonId>): Boolean   // add, or add.self for exactly themselves
fun cannotChangeOthers(name: String) = "$name can only change reminders they made."
fun cannotAddForOthers(name: String) = "$name can add reminders for $name only."
const val PICK_A_LATER_TIME = "Pick a time that hasn’t passed."
const val LIST_FULL = "The list is full. Delete a reminder to add another."

enum class SaveResult { Saved, Cancelled, Past, Full, Gone, Invalid }

@Singleton
class RemindersActions @Inject constructor(access: AccessControl, store: RemindersStore, clock: WallClock, zone: HouseholdZone, toaster: Toaster, @ApplicationScope scope: CoroutineScope) {
    fun childHere(): PersonId?                            // the child whose PIN is still in force, from access.session; no PIN pad
    suspend fun openExisting(id: Long): Reminder?         // no PIN pad; null when it has gone, or with a toast when childHere() may not change it
    suspend fun save(editing: Long?, draft: ReminderDraft): SaveResult   // PIN pad label "Add a reminder" or "Change this reminder"
    suspend fun delete(id: Long): Boolean                 // PIN pad label "Change this reminder"
}

class ReminderForm(original: Reminder?, today: LocalDate, now: LocalTime, childOnly: PersonId?)
```

- `save` first checks the draft as the form does (tidied words of 1 to `WORDS_MAX`, someone chosen, a weekday when repeating, a one-off's day from today to a year ahead): anything else is `Invalid`, with no PIN pad. Then it authorises with `Refusal.Toast`: a new one under `mayAddFor`; a change under `mayChange`, and `mayAddFor` when who changed. Then, on the application scope, it reads the wall clock: a one-off at or before now is `Past`, but only for a new reminder or one whose time or day changed, so a waiting one-off's words can be corrected; `store.add` null is `Full`; `madeBy` is whoever authorised.
- `delete` authorises as a change, deletes, and shows the toast `Reminder deleted.`. No Undo (ruling 14).
- Nothing here calls `access.lock()` (ruling 15).
- `ReminderForm`: `words` (input stops at `WORDS_MAX`), `who` (a child's is themselves and locked; otherwise Family to start), `repeats`, `date` (today), `days`, `time` (now, up to the next five minutes); `step(field: ClockField, up: Boolean)` moves the hour by one and the minute to the next or last multiple of five, wrapping the day; `allowedDay(d)` is today to `today.plusYears(1)`; `canSave` needs tidied words, someone chosen, and a weekday when repeating; `toDraft()`.
- `ReminderSheet` (opened with no PIN pad; an `HhSheet` through `LocalOverlayHost`, `markEdited` as it changes): `HhTextField`, `HhWhoPicker`, `HhSegmented(listOf("Once", "Repeats"))`, `HhDateGrid` or seven `HhChoiceChip`s (`Mon` to `Sun`, `Role.Checkbox`), `HhTimeSteppers`; `Add reminder` or `Save changes`; `Delete reminder` on an existing one; `Past` and `Full` show their line by the button and keep the sheet open.

- [ ] **Step 1: Write the failing tests.**
  - `RemindersPermissionsTest`: `theFivePermissionsTheirLabelsAndRoles`, `anAdultChangesAnyAndAChildOnlyTheirOwn`, `aRemovedMakersReminderIsForAdultsOnly`, `aChildAddsOnlyForThemselves`.
  - `RemindersActionsTest` (real access rules through `testAccess`, PINs by its constants):
    - `anAdultAddsForAnyone`; `aChildAddsForThemselvesAndMayRepeat`
    - `aChildsSaveForSomeoneElseIsRefusedByName`: toast `Mia can add reminders for Mia only.`, nothing stored.
    - `openingNeedsNoPin`: `openExisting` returns the reminder with no PIN pad shown.
    - `aChildHereCannotOpenAnAdults`: with Mia's PIN in force, toast `Mia can only change reminders they made.`, null.
    - `aChildsSaveOfAnAdultsIsRefusedByName`; `aChildCannotDeleteAnAdults`: the same toast, nothing changed.
    - `madeByIsWhoeverAuthorisedTheSave`: Sam's PIN at Save makes `madeBy` Sam.
    - `childHereIsOnlyAChildsLiveSession`: null with no session and with an adult's; Mia's id after Mia's PIN.
    - `aTimeThatPassedWhileThePinPadWasUpIsRefused`: `Past`, nothing stored.
    - `aWaitingOneOffsWordsCanChange`: its time has passed; new words save as `Saved`, and it is still waiting.
    - `aWaitingOneOffMovedToAnotherPastTimeIsRefused`
    - `saveRefusesADraftTheFormWouldNot`: empty words, 61 characters, nobody, no weekday, a day two years ahead: each `Invalid`, no PIN pad, nothing stored.
    - `theFiftyFirstSaysTheListIsFull`
    - `deleteSaysReminderDeletedWithNoUndo`: the toast has no action; the row is gone.
    - `savingAReminderThatHasGoneSaysGone`
  - `ReminderFormTest`: `aChildsWhoIsFixedToThem`, `saveNeedsWordsSomeoneAndADay`, `theDayRunsFromTodayToAYearAhead`, `minutesStepInFives`, `wordsStopAtSixty`.
  - `ReminderSheetTest`: `aNewOneSaysAddReminderAndAnExistingOneSaveChangesWithDelete`, `repeatsSwapsTheDayForSevenChips`, `aPastTimeShowsItsLineAndKeepsTheSheet`.
  - `ReminderSheetScreenshotTest`, light and dark: `reminder_form_once`, `reminder_form_repeats`.
- [ ] **Step 2:** Run `./gradlew :capability:reminders:testDebugUnitTest :capability:calendar:testDebugUnitTest`. Expect the new tests to FAIL and the calendar's to PASS.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run again with `:core:access:testDebugUnitTest` and `:core:ui:testDebugUnitTest --tests '*CopyScan*'`, record the four baselines, verify. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add, change and delete reminders, by the events rule"`.

### Task 11: The bins reminder

**Files:**
- Create: `R/BinsTiming.kt`, `R/ui/BinsSheet.kt`
- Modify: `R/RemindersEngine.kt`, `R/RemindersView.kt`, `R/RemindersActions.kt`, `R/RemindersCapability.kt` (`onBins`)
- Test: `capability/reminders/src/test/.../BinsTimingTest.kt`, `RemindersEngineTest.kt`, `RemindersViewTest.kt`, `RemindersActionsTest.kt`, `ui/BinsSheetTest.kt`

**Interfaces:**
- Consumes: Task 4's `BinCollections` (as `Optional`) and `NightHours.startsAt()`; Task 5's `ReminderState` and bins writes.
- Produces:
  - `internal fun binsDue(days: BinDays?, state: ReminderState, nowMillis: Long, zone: ZoneId): Pair<Due, BinCollection>?`: null unless `days.connected`. The candidates are `days.collections` and `state.binsKept` when its date isn't among them (ruling 16). The answer is the first candidate, by date, with the switch on, `timeOn(date - 1, binsTime) <= now < timeOn(date, 12:00)` and `binsDoneFor != date`. Its `Due` is `DueKey.Bins(date)`, due at that evening time, with the state's bins snooze and chime marks.
  - `internal fun binsWords(date: LocalDate, today: LocalDate): String`: `Bins out tonight` before `date`, `Bins out today` on it. `const val BINS_WHO = "Bins"`.
  - `internal fun binsTimeAllowed(time: LocalTime, nightStarts: LocalTime?): Boolean`: from 12:00, and before `nightStarts` when that is known and after 12:00. `const val PICK_A_BINS_TIME = "Pick a time from midday until night hours start."`
  - `RemindersActions`: `suspend fun saveBins(on: Boolean, time: LocalTime): BinsSave` with `enum class BinsSave { Saved, Cancelled, BadTime }`: `BadTime` first, with no PIN pad, then it authorises `reminders.bins` (label `Change the bins reminder`). The sheet opens with no PIN pad (ruling 7).
  - The engine takes `bins: Flow<BinDays?>` (a trigger too; the injected constructors of the engine and the view gain `Optional<BinCollections>`, null days without one); step 2 of its pass adds `binsDue`; `done(DueKey.Bins(d))` is `store.binsDone(d)`, `later` is `store.binsLater(now)`. It logs `bins due` when a bins key first appears and `bins cleared` when last pass's bins key has reached its noon. Each pass with a bins reminder waiting keeps its collection: `store.binsKept(collection)` when the stored one differs from the calendar's; `store.binsKept(null)` once the kept one's noon has passed or it is Done.
  - `RemindersView`: the bins card (stripes from each bin's `inkId`, `detail` the names joined with `, `, who `Bins`), a bins row in Waiting, and `TabUi.bins` while `days.connected`, with `cantRead` from `readFailed`.
  - `BinsSheet`: `HhSwitchRow`, `HhTimeStepperRow` (its 30-minute steps), `Save changes`; `BadTime` shows `PICK_A_BINS_TIME`.

- [ ] **Step 1: Write the failing tests.**
  - `BinsTimingTest`: `waitingFromTheEveningBeforeUntilNoon` (17:59 no, 18:00 yes, 11:59 on the day yes, 12:00 no), `tonightUntilMidnightThenToday`, `aCollectionSeenLateIsWaitingAtOnce`, `switchedOnAfterTheTimeIsWaitingAtOnce`, `switchedOffIsNeverWaiting`, `doneEndsItForThatCollectionOnly`, `movingTheTimeLaterTakesItDownUntilThen`, `aCollectionThatVanishedBeforeItStartedHasNoReminder`, `aKeptCollectionStaysUntilNoonThoughTheCalendarDroppedIt`, `theCalendarsBinsWinOverTheKeptOnes`, `aKeptCollectionMarkedDoneIsOver`, `aKeptCollectionWithNoBinsConnectionIsNothing`, `noStoredCollectionsNothingFires`, `theTimeMustFallFromMiddayToNightsStart` (12:00 and 21:30 allowed with night from 22:00; 11:30 and 22:00 not; an unknown start allows 23:30).
  - `RemindersEngineTest`: `binsShowAndChimeLikeAnyReminder`, `laterAndDoneWorkOnTheBinsCard`, `binsClearAtNoonEvenOnHoliday`, `aFailedReadStillFiresFromStoredDates`, `binsAndAReminderShareTheCardOldestFirst`, `aBinsReminderOutlivesTheMorningsRead` (shown the evening before; after midnight the seam lists only the next collection; the card reads "Bins out today" with the same bins, across a second engine over the same database, and clears at 12:00 with `bins cleared`).
  - `RemindersViewTest`: `changedBinsFollowOnTheCard`, `aWaitingBinsReminderIsARowAndCountsInTheBadge`, `theBinsRowShowsOnlyWhileABinsConnectionExists`.
  - `RemindersActionsTest`: `onlyAnAdultChangesTheBinsReminder`, `aBinsTimeInNightHoursIsRefused`.
  - `BinsSheetTest`: `aBadTimeShowsItsLineAndKeepsTheSheet`.
- [ ] **Step 2:** Run `./gradlew :capability:reminders:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `./gradlew :capability:reminders:testDebugUnitTest :capability:reminders:verifyRoborazziDebug :core:ui:testDebugUnitTest --tests '*CopyScan*'`. Expect PASS, no baseline changed.
- [ ] **Step 5:** Commit: `git commit -m "Remind about the bins the evening before"`.

### Task 12: Wiring and the README

**Files:**
- Modify: `app/build.gradle.kts` (`implementation(project(":capability:reminders"))`), `app/src/debug/java/uk/co/siland/culvery/di/HouseGraphProbe.kt`, `PLUGIN/Capability.kt` (the `order` comment: Shopping 20, Reminders 25), `README.md`
- Test: `app/src/test/.../HouseGraphTest.kt`

- README: a Modules row for `:capability:reminders` (the store, the engine, the tab at 25, its five permissions); `BinCollections` in the `:core:house` row; `AlertHandle` beside `AlertHost`'s mention if there is one.

- [ ] **Step 1: Write the failing tests.** `HouseGraphTest`: `theRemindersEngineStartsWithTheApp` (the `Startable` set holds it), `theBinsSeamIsTheCalendars`, `remindersSitsBetweenShoppingAndMusicOnTheRail` (the real capability set, by `order`).
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*HouseGraph*'`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the whole gate: `./gradlew testDebugUnitTest verifyRoborazziDebug :build-logic:convention:test --continue`. Expect PASS, `CopyScanTest` with no new allow-list entry.
- [ ] **Step 5:** Commit: `git commit -m "Wire Reminders into the app"`.

### Task 13: Walk-through support in the debug build

**Can be cut:** nothing else depends on it; without it the walk-through works the minute offsets out by hand.

**Files:**
- Modify: `app/src/debug/java/uk/co/siland/culvery/DebugClockReceiver.kt`, `di/DebugClockModule.kt`
- Test: `app/src/testDebug/java/uk/co/siland/culvery/DebugClockTest.kt`

**Interfaces (produces):**
- `fun DebugClock.moveTo(time: LocalTime, zone: ZoneId)`: sets `minutes` so the wall reads the next `time` after now. `fun DebugClock.add(more: Long)`.
- The receiver takes `--es at HH:mm` (the household's zone) and `--el add N` beside `--el minutes N`; a mistyped time moves nothing.
- The sample council is left as it is: it lists nothing for today, so check 4 shows a kept collection outliving the morning's read (ruling 16).

- [ ] **Step 1: Write the failing tests.** `DebugClockTest`: `atMovesForwardToTheNextSuchTime`, `aTimeAlreadyPastTodayIsTomorrows`, `addStacksOnTheOffset`, `aMistypedTimeMovesNothing`.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*DebugClock*'`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run again. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Let the debug clock move to a time of day"`.

### Task 14: Simplify

- [ ] **Step 1:** Run the `simplify` skill on `git diff main...HEAD`. Keep personal data out and the moved code moved.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest verifyRoborazziDebug :build-logic:convention:test --continue`. Rerun a load flake by itself.
- [ ] **Step 3:** Commit: `git commit -m "Simplify the reminders code"`.

### Task 15: The emulator walk-through

- [ ] **Step 1:** Walk the eleven checks in "Walk-through" below on `emulator-5554`, a debug build, `install -r`, the sample household. Collect faults and ask before fixing.
- [ ] **Step 2:** Put everything back (the last block of "Walk-through").
- [ ] **Step 3:** Record under "Plan 22 (Reminders): emulator walk-through" in `docs/superpowers/plans/2026-09-23-plan1-followups.md`: one line per check, no words of a reminder, no names beyond the sample's. Commit: `git commit -am "Record Plan 22's emulator walk-through"`.

### Task 16: Final review and merge

- [ ] **Step 1:** Run the whole-branch review on the most capable model, with Tasks 2, 7 and 10 named as the state and security work.
- [ ] **Step 2:** Merge and push only on the user's say.

---

## Tasks that can be cut

- **Task 13** alone, at the cost named there.
- **Tasks 4 and 11 together** (bins) without breaking the family's own reminders; Task 9's Bins row then never shows. Task 5's `binsKept` goes with them, as it uses Task 4's `BinCollection`. Bins are the spec's first case, so this is a fallback, not a suggestion.

## Self-review

- **Spec coverage:** 2.1 to 2.11 and 4.1 → Tasks 5, 10; 4.2 → 5, 6; 4.3 → 8; 4.4 → 2, 3, 7; 4.5 → 9; 4.6 → 10; 4.7 → 4, 11; 5 → 1 to 5, 12; 6 → 7, 12; 7 → the tests named in each task; 8 → 13, 15; 9 → the rulings.
- **Names checked across tasks:** `AlertHandle`, `showIfFree`, `duck`, `wake`, `playReminder`, `REMINDER_CHIME_MS`, `BinDays`, `startsAt`, `Due`, `DueKey`, `Showing`, `CardUi`, `StripeUi`, `TabUi`, `BinsRowUi`, `SaveResult`, `BinsSave`.
- **Review Focus:** each of the five has its test in Task 6, 7, 8 or 10.

## Walk-through

Spec section 8, on `emulator-5554`. The sample household's PINs are in the README. Two terminals:

```bash
adb -s emulator-5554 logcat -s Reminders:I
```

```bash
clock() { adb -s emulator-5554 shell am broadcast -n uk.co.siland.culvery/.DebugClockReceiver -a uk.co.siland.culvery.debug.CLOCK "$@"; }
ring() { adb -s emulator-5554 shell "cmd notification post -t 'Front Door' culvery-fake-ring '$1'"; }
```

`clock --es at 18:00` moves the wall forward to the next 18:00; `clock --el add 30` moves it 30 minutes on; `clock --el minutes 0` puts it back. After a move the wall notices within a minute: wait for the log line, not the screen. "T" below is the time chosen in the form, on a five-minute mark.

1. **A one-off.** Reminders › Add reminder, for today at T a few minutes ahead; an adult's PIN at "Add reminder" in the form. `clock --es at T`. Expect `due 1`, `card up 1 of 1`, `chime`, and the card.
2. **Later, then Done.** Tap Later: `later`. `clock --el add 30`: `due 1`, `card up 1 of 1`, `chime`. Tap Done: `done`, and the row is gone.
3. **Weekly.** Add one that repeats on tomorrow's weekday at 08:00. `clock --es at 00:01`, then `clock --es at 08:00`: the card and `chime`. Done. `clock --el add 10080`: the card and `chime` again. Done.
4. **Bins.** `clock --es at 18:00` first, then connect the sample council in Settings › Calendars (`1 Example Road`, `ZZ99 9ZZ`); connecting reads at once, and tomorrow's collection is first seen after its time. Expect `bins due`, `card up 1 of 1`, `chime`, and "Bins out tonight" with Food and one stripe. Leave it. `clock --es at 00:01`: `held night`. `clock --es at 07:00`: the card reads "Bins out today" with the same bins, though the sample council no longer lists that day, with `chime`. Leave it. `clock --es at 12:00`: `bins cleared`, no card. Then switch the Bins row off (an adult's PIN at Save changes), so it stays out of the checks below.
5. **Night.** Add a one-off for tomorrow at 06:45. `clock --es at 06:45`: `due 1`, `held night`, the night clock untouched. Tap the clock: the card, and no `chime`. Wait a minute: the clock is back, the card gone. `clock --es at 07:00`: `card up 1 of 1`, `chime`. Done.
6. **Door quiet hours.** In Settings › Door connect the doorbell if it isn't (the debug build counts Ring as installed), and set quiet hours over the wall's time now. Add a one-off at T; `clock --es at T`: `card up 1 of 1`, and no `chime`. Done. Turn quiet hours off.
7. **Day, dark screen.** Add a one-off at T. Leave the wall until it goes dark (`adb -s emulator-5554 logcat -s DisplayState:I` shows `Screen ASLEEP`). `clock --es at T`: `Screen AWAKE`, `card up 1 of 1`, `chime`. Leave it for the sleep time: `Screen ASLEEP` with the card unanswered. Tap: the card is there, and no new `chime`.
8. **A ring.** With that card up: `ring 'Someone is at your Front Door'`. The door card takes over: `held ring`. Got it: `card up 1 of 1`, no `chime`. Done.
9. **A child.** Wait two minutes from the last PIN. Add reminder, choose Sam, save with Mia's PIN: the toast "Mia can add reminders for Mia only." Choose Mia, save with Mia's PIN: it is added. Within two minutes, Add reminder again: every tile but Mia's is locked. Tap an adult's reminder: the toast "Mia can only change reminders they made."
10. **Holiday mode.** Add a one-off at T. Turn Holiday mode on. `clock --es at T`: `due 1`, `held holiday`, nothing on screen. Turn it off: `card up 1 of 1`, `chime`. Done.
11. **A restart.** `clock --el minutes 0` (a restart clears the offset anyway). Add a one-off for the next five-minute mark and wait for `chime` in real time. Leave the card. Then:
    ```bash
    adb -s emulator-5554 shell am force-stop uk.co.siland.culvery
    adb -s emulator-5554 shell am start -n uk.co.siland.culvery/.MainActivity
    ```
    Expect `due 1`, `card up 1 of 1`, and no `chime`.

Put back: `clock --el minutes 0`; delete the reminders made; the Bins row on at 18:00; quiet hours and Holiday mode as found; disconnect the sample council.

## Not in this build, on purpose

Each is on the follow-ups list under Plan 22.
- Reminders made on a phone or read from Google (a "Reminders" calendar was the route weighed).
- A repeat that ends on a date, or one the calendar's picker doesn't offer.
- Following one date of a repeating event that was skipped or moved on a phone.
- A sound chosen per reminder, a chime that repeats, or a spoken reminder.
- Anything drawn on the night clock or the dark screen.
- Reminders shown on the calendar or on a Home card.
- Night-holding in the alert layer for every capability (the Plan 8 follow-up). Reminders applies its own rules.

---

# Part 2 (2026-11-18): shared repeats, and reminders on calendar events

Spec section 10, which wins over the earlier sections where they differ. The Global Constraints at the top of this plan bind every task below, with these changes:

- **Logging** gains two lines on the `Reminders` tag: `event gone` and `event moved`. Nothing else is on either line.
- **Privacy** covers the new types too: `EventSeed`, `EventTimes` and anything carrying an event's title, people or key hides them in `toString`. An event key never reaches a log.
- **Module boundary:** `:capability:calendar` and `:capability:reminders` still never see each other. They meet only at the `EventReminders` seam in `:core:house`.
- **Values:** Stops after runs 2 to 52, default 10; the picker's order is Doesn’t repeat, weekly, every 2 weeks, monthly on the nth weekday, yearly, daily; an all-day event's reminder opens at 08:00.
- **Copy, verbatim:** `Add reminder`, `Change reminder`, `Event and its reminder deleted.`, `Changes saved. Its reminder moved too.`, `As the event: <words>`, `Every week, on Mon, Wed`, `Every week, on Mon to Fri`, `Every 2 weeks, on Mon, Wed`, `Every day`.

## Rulings made while planning Part 2 (mine; each is cheap to reverse)

19. **One new module, `:core:repeat`,** holds the repeat model; the picker and its state go in `:core:ui`. `:capability:calendar` exposes `:core:repeat` with `api`, so the providers need no build change.
20. **Reminders' database is redefined at version 1** (spec 10.3), with the link's columns added in the same task so the schema is written once. `1.json` is regenerated. No migration.
21. **A reminder stores its rule as the RRULE line** the calendar writes, with no DTSTART line, beside its first day.
22. **The event key** is `connectionId`, `sourceId` and `seriesId ?: remoteId` joined as `EventRef.listKey` joins its parts. It is the same for a one-off and for the series it may become.
23. **A series' time** for the link is the commonest time of day among its stored dates from today on (all of them when none is ahead); its day is the first-date line of its rule, or unknown.
24. **A reminder is deleted when the wall's delete is accepted or queued,** not when the provider confirms (spec 10.5).
25. **A sync's removals and changes are told to Reminders after the transaction,** from a list the transaction built. A restart in between loses them (spec 10.5 accepts this).
26. **The reminder form puts Repeats last,** under the time, with the picker inline. The layout is provisional like the rest.
27. **Chosen days on an event** are offered only while the event is a day or shorter; the lock line is the existing `Too long to repeat that often.`
28. **W4:** if the calendar's editor leaves "Save changes" live with nothing changed, the reminder form stays as it is; if the editor greys it, the form matches. Task 22 checks and records which.

## Review Focus for Part 2

1. A daily or weekly reminder years old: the latest time on or before now is still found, and quickly (Task 18 `aDailyRuleTenYearsOnStillFindsTodaysDate`).
2. A sync that arrives while a wall move is still queued must not shift the reminder back, or delete it (Task 26 `aQueuedChangeHoldsTheSyncOff`).
3. An event the read no longer covers must not delete its reminder, and a sync read before a wall move must not undo it (Task 26 `aPastEventTheReadNoLongerCoversIsNotARemoval`, `aSyncReadBeforeAWallMoveDoesNotReportTheOldTimes`).
4. A counted series opened from its fifth date: the reminder must not run five times too many (Task 18 `aRuleStartedLaterHasItsCountReduced`, Task 24 `aSeriesFromALaterDateCarriesWhatIsLeft`).
5. A rule the shared code refuses must never crash a pass or the detail card (Task 18 `aRuleItCannotWorkOutIsRefused`, Task 21 `aReminderWithARuleItCannotReadNeverFires`).

### Task 17: Move the repeat model to `:core:repeat`

No behaviour changes in this task.

**Files:**
- Create: `core/repeat/build.gradle.kts` (as `core/household`'s without Room and Hilt), `core/repeat/src/main/java/uk/co/siland/culvery/core/repeat/Recurrence.kt`, `RepeatWords.kt`; add `include(":core:repeat")` to `settings.gradle.kts`.
- Move: everything in `capability/calendar/.../Recurrence.kt`; from `Repeats.kt` everything but the `EventTime` wrapper; `DAYS_PER_WEEK`, `SHORT_DAY`, `shortDay` from `CalendarUi.kt` (the calendar imports them back).
- Move tests: `RecurrenceTest.kt` whole; `RepeatsTest.kt`'s cases follow the function they test.
- Modify: `capability/calendar/build.gradle.kts` (`api(project(":core:repeat"))`), `capability/reminders/build.gradle.kts` and `core/ui/build.gradle.kts` (`implementation`/`api` as needed later; add now), every import in the calendar, `provider/calendar-google`, `provider/calendar-fake` and their tests.

**Interfaces:**
- Produces, in package `uk.co.siland.culvery.core.repeat`, all public: `RepeatChoice`, `EDITOR_REPEATS`, `fitsLength`, `DOESNT_REPEAT`, `LAST`, `weekdayName`, `wording`, `nthWeekday`, `ordinal`, `code`, `occurrences`, `lastOccurrence`, `MIN_TIMES`, `MAX_TIMES`, `DEFAULT_TIMES`, `RepeatEnd`, `Recurrence`, `UNTIL_DATE`, `UNTIL_UTC`, `recurrenceRule`, `withSeriesStart`, `seriesStart`, `withoutSeriesStart`, `ruleParts`, `BOUNDING_PARTS`, `readChoice`, `choiceOf`, `isLastOfWeekday`, `nthOf`, `REPEATS_YES`, `DAYS_PER_WEEK`, `shortDay`, and
  `fun repeatWords(rule: String?, day: LocalDate, zone: ZoneId, today: LocalDate): String` — the body of today's `repeatsLabel` with `day` in place of the `EventTime`.
- The calendar keeps `fun repeatsLabel(rule: String?, start: EventTime, zone: ZoneId, today: LocalDate): String`, now one line over `repeatWords`.

**After the plan review (binding; wins over this task's other text where they differ):**
- `RecurrenceTest.whatSaveWritesReadsBackInItsWords` uses `EventTime` and `repeatsLabel`: it stays in the calendar, in a small test file of its own. Every other case moves.
- `SHORT_DAY_AND_YEAR` moves with `shortDay`.

- [ ] **Step 1:** Create the module and move the files with `git mv`, then fix packages and imports.
- [ ] **Step 2:** Run `./gradlew :core:repeat:testDebugUnitTest :capability:calendar:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest :provider:calendar-google:testDebugUnitTest :build-logic:convention:test`. Expected: all pass, the same test count as before across the moved files.
- [ ] **Step 3:** Commit: `git commit -m "Move the repeat rules to core"`.

### Task 18: The dates of a rule, worked out in one place

**Files:**
- Create: `core/repeat/src/main/java/uk/co/siland/culvery/core/repeat/RepeatRule.kt`, `core/repeat/src/test/java/.../RepeatRuleTest.kt`.
- Modify: `Recurrence.kt` (`occurrences` and `lastOccurrence` keep their signatures and may delegate), `provider/calendar-fake/.../RecurrenceExpander.kt` (its `step`, COUNT and UNTIL-by-date logic is replaced by `RepeatRule`; its exact UNTIL-instant cut and its occurrence ids stay).

**Interfaces:**
- Produces:
```kotlin
/** A rule the shared code can work out. [toString] prints nothing of it. */
class RepeatRule {
    /** The rule's dates from [first], in order, ended by COUNT or UNTIL. */
    fun dates(first: LocalDate): Sequence<LocalDate>
    fun latestOnOrBefore(first: LocalDate, day: LocalDate): LocalDate?
    fun firstAfter(first: LocalDate, day: LocalDate): LocalDate?
    companion object {
        /** Null for anything but FREQ (DAILY, WEEKLY, MONTHLY, YEARLY), INTERVAL of 1 or more, a weekly BYDAY list, a monthly BYDAY of one nth weekday (1 to 4, −1), a monthly BYMONTHDAY of one day, COUNT of 1 or more, UNTIL (a date, or a UTC time read in [zone]) and WKST=MO. A DTSTART line is skipped. */
        fun parse(rule: String, zone: ZoneId): RepeatRule?
    }
}

/** [rule] started from its first date on or after [day]: that date, and the rule with COUNT reduced by the dates before it (null when one date is left). Null when no date is left or [rule] can't be parsed. */
fun startedFrom(rule: String, first: LocalDate, day: LocalDate, zone: ZoneId): Pair<LocalDate, String?>?
```
- A weekly rule counts its weeks from the Monday of [first]'s week; dates before [first] are not dates. A weekly rule with no BYDAY is on [first]'s weekday. A monthly rule with neither BY part is on [first]'s day of the month and skips months without it. A yearly rule skips years without the date.
- `latestOnOrBefore` and `firstAfter` on a rule with no COUNT must not walk every date from [first]: skip whole periods first.

**After the plan review (binding; wins over this task's other text where they differ):**
- `parse(rule: String, zone: ZoneId, strict: Boolean = true)`. A COUNT that isn't a whole number of 1 or more is ignored in both modes (`RecurrenceExpanderTest.aBadCountIsIgnored` stands). With `strict = false`, which only the sample calendar's expander passes, an unknown part is ignored and an INTERVAL below 1 counts as 1, as that expander does today. Every existing `RecurrenceExpanderTest` case must pass unchanged.
- `WKST` other than `MO` is refused when strict, ignored when not.
- `aDailyRuleTenYearsOnStillFindsTodaysDate` asserts the dates only, with no timing assert; the task reviewer reads the code for the skip.
- `startedFrom` on a rule with COUNT needs the series' true first day. Callers that don't know it must not call it (Task 24 seeds a one-off then).

- [ ] **Step 1: Write the failing tests** in `RepeatRuleTest`, one per name, with literal dates:
  - `eachChoiceGivesItsDates` (daily, weekly, every 2 weeks, monthly on the 4th Saturday, monthly on the last Thursday, yearly) against `occurrences` for the same choice;
  - `weeklyOnSeveralDaysGivesEachDayInOrder` (`FREQ=WEEKLY;BYDAY=MO,WE` from Wednesday 2026-11-18 → 18 Nov, 23 Nov, 25 Nov, 30 Nov);
  - `everyTwoWeeksOnSeveralDaysSkipsTheOddWeeks`;
  - `aCountRunsAcrossTheDays` (`COUNT=3` on the rule above → three dates);
  - `everyThreeMonthsByDate`, `monthlyOnThe31stSkipsShortMonths`, `yearlyOn29FebruarySkipsOtherYears`;
  - `untilADateIsInclusive`, `untilAUtcTimeIsReadInTheZone`;
  - `theLatestOnOrBeforeAndTheFirstAfter` (before the first day → null and the first day);
  - `aDailyRuleTenYearsOnStillFindsTodaysDate` (also asserts under 50 ms for 1,000 calls);
  - `aRuleStartedLaterHasItsCountReduced` (`COUNT=10` weekly, started from its fifth date → that date and `COUNT=6`; from its tenth → that date and null; after its tenth → null);
  - `aRuleStartedLaterKeepsUntilAndTheRest`;
  - `aRuleItCannotWorkOutIsRefused` (`BYSETPOS`, `BYMONTH`, two BYMONTHDAY values, `FREQ=HOURLY`, `INTERVAL=0`, an empty string).
- [ ] **Step 2:** Run `./gradlew :core:repeat:testDebugUnitTest --tests '*RepeatRuleTest'`. Expected: FAIL, unresolved `RepeatRule`.
- [ ] **Step 3:** Implement `RepeatRule` and `startedFrom`.
- [ ] **Step 4:** Rebuild `RecurrenceExpander.expand` on `RepeatRule.parse(...).dates(first)`; a rule that doesn't parse returns `listOf(series)` as an unknown FREQ does today. Add to `RecurrenceExpanderTest`: `aWeeklySeriesOnSeveralDaysExpandsToEachDay`.
- [ ] **Step 5:** Run `./gradlew :core:repeat:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest :capability:calendar:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 6:** Commit: `git commit -m "Work out a repeat's dates in one place"`.

### Task 19: Weekly on several chosen days, in the model

**Files:**
- Modify: `core/repeat/.../Recurrence.kt`, `RepeatWords.kt`, their tests.

**Interfaces:**
- Changes (each new parameter last, defaulting to empty so existing callers compile):
  - `fun RepeatChoice.wording(day: LocalDate, nth: Int = nthWeekday(day), days: Set<DayOfWeek> = emptySet()): String`
  - `fun recurrenceRule(choice: RepeatChoice, end: RepeatEnd, day: LocalDate, nth: Int = nthWeekday(day), days: Set<DayOfWeek> = emptySet()): String?` — for Weekly and Fortnightly with more than one day in `days + day.dayOfWeek`, adds `;BYDAY=MO,WE` in Monday-first order before COUNT.
  - `fun occurrences(choice, day, count, nth = …, days: Set<DayOfWeek> = emptySet())` and `lastOccurrence(…, days)` likewise.
  - `fun daysOf(rule: String, start: LocalDate): Set<DayOfWeek>` — the weekly BYDAY list when it includes `start`'s weekday and has more than one day, else empty.
  - `readChoice` and `choiceOf` read a weekly BYDAY list that includes `start`'s weekday as Weekly or Fortnightly.
  - `fun RepeatChoice.fitsLength(length: Duration, days: Set<DayOfWeek> = emptySet()): Boolean` — with more than one day, only a day or shorter fits.
- Words: one day as today. Several: `Every week, on Mon, Wed`; three or more in a row within Monday to Sunday: `Every week, on Mon to Fri`; all seven weekly: `Every day`; `Every 2 weeks, on Mon, Wed`. `repeatWords` words a phone's list the same way.

**After the plan review (binding; wins over this task's other text where they differ):**
- Add `GoogleWriteTest.aSeveralDayRuleIsSentAsWritten` and `GoogleReadTest.aSeveralDayRuleReadsBackWithItsDays` (`provider/calendar-google`).

- [ ] **Step 1: Write the failing tests** in `RecurrenceTest` and the repeat-words test: `severalDaysAreWrittenMondayFirst` (`RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE;COUNT=6`), `oneDayWritesNoByDay`, `aListWithTheStartsDayReadsBackAsWeekly`, `aListWithoutTheStartsDayIsNoChoice`, `severalDaysAreWorded` (each string above), `aCountedListsLastDate` (`lastOccurrence` and the `repeatWords` tail `· 6 times · last on …`), `severalDaysFitOnlyADay`.
- [ ] **Step 2:** Run `./gradlew :core:repeat:testDebugUnitTest`. Expected: the new tests FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `./gradlew :core:repeat:testDebugUnitTest :capability:calendar:testDebugUnitTest :provider:calendar-google:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -m "Let a weekly repeat fall on several chosen days"`.

### Task 20: The shared picker, and the event editor on it

**Files:**
- Create: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/RepeatPick.kt`, `RepeatPicker.kt`, tests `RepeatPickTest.kt`, `RepeatPickerTest.kt`, a screenshot test `RepeatPickerScreenshotTest.kt` (dark and light, weekly with two days, Stops after).
- Modify: `capability/calendar/.../EditorForm.kt` (its repeat state becomes a `RepeatPick`; `repeat`, `repeatEnd`, `chooseRepeat`, `stopsAfter`, `keepsGoing`, `fewerTimes`, `moreTimes`, `setRepeat` stay as thin delegates so its tests keep their shape), `ui/RepeatsPane.kt` (keeps `NeedsADay`, the lock line and the skip-one-date hint; the rows, chips, ends and stepper come from `HhRepeatPicker`), `EditorWords.kt` (`checkSentence` and `firstDates` take the days), `CalendarEditor.kt:158` (`addedLineFor` words the days), and whatever builds `draft()`'s rule.

**Interfaces:**
- Consumes: Task 19's signatures.
- Produces, in `uk.co.siland.culvery.core.ui`:
```kotlin
/** The repeat picker's state. [kept] is a rule shown as its own row because no choice writes it; it stays chosen until a choice is tapped. */
class RepeatPick(choice: RepeatChoice = RepeatChoice.Never, end: RepeatEnd = RepeatEnd.Never, extraDays: Set<DayOfWeek> = emptySet(), val kept: String? = null) {
    val choice: RepeatChoice            // Compose state
    val end: RepeatEnd                  // Compose state
    val extraDays: Set<DayOfWeek>       // Compose state; days beyond the chosen day's own
    val keeping: Boolean                // Compose state; true while [kept] is the chosen row
    fun choose(choice: RepeatChoice)    // leaves [kept]; Never clears the end and the days
    fun keep()
    fun toggleDay(day: DayOfWeek, own: DayOfWeek)   // [own] can't be toggled
    fun stopsAfter(); fun keepsGoing(); fun fewerTimes(); fun moreTimes()
    fun daysOn(day: LocalDate): Set<DayOfWeek>      // own + extra for Weekly and Fortnightly, else empty
    fun rule(day: LocalDate, nth: Int = nthWeekday(day)): String?   // [kept] while keeping, else recurrenceRule(…)
    val repeats: Boolean                // keeping, or a choice other than Never
    companion object {
        /** [rule] read against [first]: a choice with its days and a count of 2 to 52 when the picker can show all of it, else kept. Null rule is Never. */
        fun read(rule: String?, first: LocalDate): RepeatPick
    }
}

@Composable
fun HhRepeatPicker(
    pick: RepeatPick,
    day: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    nth: Int = nthWeekday(day),
    lockedLine: (RepeatChoice) -> String? = { null },   // a locked row's reason
    daysLocked: Boolean = false,
    onPicked: (RepeatChoice) -> Unit = {},
)
```
- Test tags keep today's names: `repeat_<Choice>`, `ends_mode`, `ends_fewer`, `ends_count`, `ends_more`; new: `repeat_kept`, `repeat_day_<DAY>`. The kept row reads `As the event: ${repeatWords(kept, day, zone, today)}`.
- The stepper label is what `EditorForm.stepperLabel` gives today, moved with it.

**After the plan review (binding; wins over this task's other text where they differ):**
- More files: `RememberedTitles.kt` (`keptRule`), `SuggestionWords.kt` (`Suggestion` gains `days()`), `TitleSuggestions.kt`, and `EditorForm`'s `use`, `repeatsValue`, `Answers`/`edited` and `editWhen`'s `fitsLength`: every one carries the days. Tests: `aRememberedSeriesOnTwoDaysIsSuggestedWithBoth`, `tickingADayIsAnEdit`, `theRepeatsRowWordsTheDays`.
- `RepeatPick.read` keeps the rule string it was given. `rule(day)` returns that very string while nothing has been tapped and `day` is the day it was read against; `val touched: Boolean` says whether anything has. (A rule read and written back gains `;INTERVAL=1`, so strings can't be compared.)

- [ ] **Step 1: Write the failing tests.** `RepeatPickTest`: `choosingNeverClearsTheEndAndTheDays`, `theOwnDayCannotBeUnticked`, `theRuleCarriesTheDays`, `aRuleThePickerShowsReadsBackWhole` (weekly Mon+Wed, COUNT 6), `aRuleItCannotShowIsKept` (`FREQ=MONTHLY;INTERVAL=3`, a COUNT of 100, an UNTIL), `choosingARowLeavesTheKeptRule`, `theCountStaysBetweenTwoAndFiftyTwo`. `RepeatPickerTest`: `theDayChipsShowOnlyForWeeklyAndFortnightly`, `aLockedRowShowsItsLineAndCannotBeChosen`, `lockedDaysCannotBeTicked`, `theKeptRowShowsOnlyWhenThereIsOne`.
- [ ] **Step 2:** Run `./gradlew :core:ui:testDebugUnitTest --tests '*RepeatPick*'`. Expected: FAIL, unresolved.
- [ ] **Step 3:** Implement `RepeatPick` and `HhRepeatPicker` from `RepeatsPane`'s rows, segmented control and stepper, with `HhChoiceChip` for the days.
- [ ] **Step 4:** Put `EditorForm` and `RepeatsPane` on them. Add to `EditorFormTest`: `aNewEventOnTwoDaysWritesBothInItsRule`, `daysAreLockedOnceTheEventIsLongerThanADay`; to `RepeatsPaneTest`: `tickingASecondDayRewordsTheRow`.
- [ ] **Step 5:** Run `./gradlew :core:ui:testDebugUnitTest :capability:calendar:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 6:** Record the new baselines and the calendar's changed ones (`add_event_repeats_*`): `./gradlew :core:ui:recordRoborazziDebug :capability:calendar:recordRoborazziDebug`; restore every baseline this task didn't mean to change, file by file; then `verifyRoborazziDebug` for both. Add `add_event_repeats_days_dark.png` (weekly, two days).
- [ ] **Step 7:** Commit: `git commit -m "Share the repeat picker and add chosen days to events"`.

### Task 21: Reminders on the shared repeats

**Files:**
- Modify: `capability/reminders/.../Reminder.kt`, `Timing.kt`, `RepeatWords.kt` (deleted; callers use `repeatWords`), `RemindersStore.kt`, `db/RemindersDatabase.kt`, `schemas/…/1.json` (regenerated), `ReminderForm.kt`, `RemindersActions.kt`, `RemindersView.kt`, `ui/ReminderSheet.kt`, the eight test files and the form's screenshot tests.

**Interfaces:**
- Produces:
```kotlin
sealed interface Schedule {
    val first: LocalDate
    data class Once(override val first: LocalDate) : Schedule
    /** [rule] is an RRULE line with no first-date line. */
    data class Repeating(override val first: LocalDate, val rule: String) : Schedule
}

/** The event a reminder was added from (22 §10.5). [toString] prints nothing. */
data class EventLink(val key: String, val day: LocalDate?, val time: LocalTime?, val rule: String?, val followsRepeat: Boolean)

data class ReminderDraft(val words: String, val forPeople: List<PersonId>, val time: LocalTime, val schedule: Schedule, val event: EventLink? = null)
// Reminder gains: val event: EventLink?
```
- `ReminderEntity` columns: `id`, `words`, `forPeople`, `madeBy`, `minuteOfDay`, `firstDate: String`, `rule: String?`, `answeredAtMillis`, `snoozedUntilMillis`, `chimedAtMillis`, `eventKey: String?` (indexed), `eventDay: String?`, `eventMinute: Int?`, `eventRule: String?`, `followsRepeat: Boolean`. Version stays 1.
- `Reminder.due(nowMillis, zone)` and `nextDue(afterMillis, zone)` keep their signatures: for `Repeating`, the latest date on or before today whose time is after "answered at" and at or before now (try today's, then the one before), and the first time after `afterMillis`. A rule `RepeatRule.parse` refuses gives no due and no next.
- `RemindersStore.done`: deletes a `Once`, and a `Repeating` with no time after now; else restarts it. `change` restarts on a new time, first day or rule.
- `ReminderForm(original: Reminder?, today: LocalDate, now: LocalTime, childOnly: PersonId?)` keeps that constructor; `repeats`, `days`, `toggleDay` go; it gains `val repeat: RepeatPick` (read from the original's rule and first day). `canSave` no longer looks at days. `toDraft()` gives `Once(date)` when `repeat.rule(date)` is null, else `Repeating(date, rule)`, and carries the original's `event`.
- The sheet: the Once / Repeats switch and `DayChips` go; `DayGrid` always shows; `HhRepeatPicker(form.repeat, form.date, …)` sits under the time, under the heading `Repeats`.
- `acceptable`: the first day is within a year, or is the day it already has. `write`'s Past check is for `Once` only.
- The tab's note: `Once`, else `repeatWords(rule, first, zone, today)`.

**After the plan review (binding; wins over this task's other text where they differ):**
- More files: `ui/ReminderSheetTest.kt`, `RemindersEngine.kt`.
- `RemindersStore.done(id: Long, nowMillis: Long, zone: ZoneId)`: the engine passes its zone.
- `RemindersStore.change` restarts on a new time or first day, or a rule the form's `RepeatPick` says was touched; an untouched rule keeps its string, so a change of words restarts nothing. A change of first day or rule on a linked reminder also sets `followsRepeat` false; the link stays.
- W4 (answered by the review: the calendar's "Save changes" stays live and closes with no PIN when nothing changed): the reminder form's "Save changes" with nothing edited closes the sheet with no PIN and no write. Test `saveChangesWithNothingChangedClosesWithNoPin`.

- [ ] **Step 1: Rewrite the failing tests first.** Keep every existing test whose behaviour stands, moved to the new types (weekly on chosen weekdays becomes a `Repeating` with a BYDAY list). Add: `TimingTest` `aFortnightlyReminderIsDueEveryOtherWeek`, `aMonthlyReminderMissedWhileOffIsWaitingOnce`, `aCountedReminderHasNoNextAfterItsLast`, `aReminderWithARuleItCannotReadNeverFires`; `RemindersStoreTest` `doneOnTheLastTimeDeletesARepeat`, `aNewRuleRestartsTheReminder`, `theLinkSurvivesAChangeOfWords`; `ReminderFormTest` `aRepeatFromThePickerBecomesARepeatingSchedule`, `anExistingRepeatOpensOnItsChoice`; `RemindersActionsTest` `aRepeatingReminderForEarlierTodayIsAccepted`; `RemindersViewTest` `aRepeatIsWordedAsTheCalendarWordsIt`.
- [ ] **Step 2:** Run `./gradlew :capability:reminders:testDebugUnitTest`. Expected: FAIL to compile.
- [ ] **Step 3:** Implement. Regenerate `1.json` by building (`./gradlew :capability:reminders:kspDebugKotlin`), and check the diff is the columns above.
- [ ] **Step 4:** Run `./gradlew :capability:reminders:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Re-record the reminder form and tab baselines; restore the unrelated ones; verify. The form's two baselines become: one-off, and weekly on two days.
- [ ] **Step 6:** Commit: `git commit -m "Give reminders the calendar's repeat choices"`.

### Task 22: The three walk-through fixes

**Files:**
- Modify: `capability/reminders/.../RemindersActions.kt` (`save`), the who-picker's locked-tile drawing (`core/ui`'s `HhWhoPicker` or the reminders `WhoPart`, wherever the padlock is chosen), their tests; maybe `ui/ReminderSheet.kt` for W4.

**After the plan review (binding; wins over this task's other text where they differ):**
- W3: the padlock is drawn by `HhWhoPicker` (`WhoPicker.kt:172`) and the calendar's `WhoPaneTest` pins it there. Leave `HhWhoPicker` alone; change what the reminders' `WhoPart` passes it (`ReminderSheet.kt`, near line 266) so the child's own tile isn't locked. The test goes in `ReminderSheetTest`.
- W4 is done in Task 21. Skip Step 4.
- `LogHygieneTest` runs in `:build-logic:convention:test`.

- [ ] **Step 1: Write the failing tests.** `RemindersActionsTest.aOneOffAlreadyPastIsRefusedBeforeThePinPad`: a new one-off at a time at or before now returns `SaveResult.Past` and `authorise` is never called. The existing `aTimeThatPassedWhileThePinPadWasUpIsRefused` still passes. A who-picker test `aChildsOwnTickedTileHasNoPadlock`: with the picker locked to a child, their own tile has no padlock and the others keep theirs.
- [ ] **Step 2:** Run the two tests. Expected: FAIL.
- [ ] **Step 3:** Implement: the Past check runs once before `authoriseAdd`/`authoriseChange` and again in `write`.
- [ ] **Step 4 (W4):** Read `EventEditorScreen.kt` near `enabled = form.canSave && !busy`. If an unchanged event's "Save changes" is live, change nothing and write `Ruling: W4 left as is — the calendar's editor does the same` in the report. If it is greyed, make the reminder form's `enabled` also need `form.edited` when editing, with a test `saveChangesIsGreyUntilSomethingChanges`.
- [ ] **Step 5:** Run `./gradlew :capability:reminders:testDebugUnitTest :core:ui:testDebugUnitTest`; re-record only the baselines the padlock changes; verify.
- [ ] **Step 6:** Commit: `git commit -m "Check a reminder's time before the PIN and unlock a child's own tile"`.

### Task 23: The seam, and Reminders' side of the link

**Files:**
- Modify: `core/house/.../HouseSeams.kt`, `core/house/.../di/HouseModule.kt` (`@BindsOptionalOf abstract fun eventReminders(): EventReminders`), `core/house/build.gradle.kts` if `OverlayHost` isn't visible there (`api(project(":core:plugin"))`; there is no cycle).
- Create: `capability/reminders/.../RemindersForEvents.kt`, test `RemindersForEventsTest.kt`.
- Modify: `RemindersStore.kt`, `db/RemindersDatabase.kt` (DAO queries only), `di/RemindersModule.kt` (`@Binds`), `ui/ReminderSheet.kt` (`openReminder` takes a seed), `ReminderForm.kt`, `RemindersActions.kt`, `LogHygieneTest`'s allow-list if it needs the two lines.

**Interfaces:**
- Produces, in `:core:house`:
```kotlin
/** An event's times as the calendar has them now. [day] is null for a series whose first day isn't known; [time] is null for all day; [rule] has no first-date line. [toString] prints nothing. */
data class EventTimes(val day: LocalDate?, val time: LocalTime?, val rule: String?)

/** What the reminder form opens with for an event (22 §10.4). [day], [time] and [rule] are the form's: the date tapped or the next one, and the rule from that date. [now] is what the link remembers. [toString] prints nothing. */
data class EventSeed(val key: String, val title: String, val people: List<PersonId>, val day: LocalDate, val time: LocalTime?, val rule: String?, val now: EventTimes)

interface EventReminders {
    /** Whether the event with [key] has a reminder. */
    fun has(key: String): Flow<Boolean>
    /** Opens the event's reminder, or the form filled in from [seed] when it has none. */
    suspend fun open(host: OverlayHost, seed: EventSeed)
    /** The events have gone. True when a reminder went with them. */
    suspend fun gone(keys: Collection<String>): Boolean
    /** The event's times are now [now], and its start is [startsAtMillis] (the next date's, for a series; null when it has none). True when a reminder moved. */
    suspend fun changed(key: String, now: EventTimes, startsAtMillis: Long?): Boolean
}
```
- Reminders: `class RemindersForEvents @Inject constructor(store, actions, members…, clock: WallClock, zone: HouseholdZone, @ApplicationScope scope) : EventReminders`.
  - `open`: an existing reminder for the key opens through `openReminder(id, …)` as a row tap does (the child rule included); otherwise the form opens new with words `seed.title.take(WORDS_MAX)`, who `seed.people` or Family (a signed-in child: themselves), day `seed.day`, time `seed.time ?: 08:00`, `RepeatPick.read(seed.rule, seed.day)`.
  - Saving that form stores `EventLink(seed.key, seed.now.day, seed.now.time, seed.now.rule, followsRepeat = draft day == seed.day && draft rule == seed.rule)`. A second save for a key that now has a reminder changes that reminder.
  - `gone`: deletes by key with no PIN and no toast; logs `event gone` once when anything went.
  - `changed`: nothing when `now` equals the stored link. Otherwise the reminder's first day and time shift by the difference between the stored and new day (when both are known) and time (when both are set); the link takes `now`. When the rule changed and `followsRepeat`: the reminder's first day and rule become `startedFrom(now.rule, now.day, today, zone)` (a null rule makes it `Once`; an unknown `now.day` or a rule that doesn't parse leaves the rule as it was). When it has its own repeat and the weekday moved, a rule `RepeatPick.read` can show is rewritten from the new first day with its extra days moved by the same number of days. Then: `startsAtMillis` at or before now deletes a `Once`; otherwise the reminder restarts from now, unless only its own time is now past, when "answered at" is left so it shows. Logs `event moved`.
  - A shift never moves the time across midnight for a reminder that follows the repeat: it stops at 00:00 or 23:55.
- `RemindersStore` gains `fun hasEvent(key: String): Flow<Boolean>`, `suspend fun byEvent(key: String): Reminder?`, `suspend fun deleteForEvents(keys: Collection<String>): Int`, and `suspend fun put(reminder: Reminder)` for the move.

**After the plan review (binding; wins over this task's other text where they differ):**
- `:core:house` already sees `OverlayHost` through `:core:access`: no build change.
- **The seed's path:** `openReminder(id: Long?, actions, members, seed: EventSeed? = null)`; `ReminderForm(original, today, now, childOnly, seed: EventSeed? = null)`. `toDraft()` on a seeded form carries `EventLink(seed.key, seed.now.day, seed.now.time, seed.now.rule, followsRepeat = date == seed.day && !repeat.touched)`. `RemindersActions.save(editing = null, draft)` with a `draft.event` whose key already has a reminder is a change to that reminder, through `authoriseChange`. Test `aSecondSaveForTheSameEventChangesTheFirst`.
- **`changed`, in this order:**
  1. No reminder for the key, or `now` equals the stored link: false, nothing written.
  2. The shift is one length of time: `(new day at new time) − (old day at old time)`, with a missing time read as 00:00 and an unknown day on either side read as no day shift. The reminder's first day and time move by it as one date-time, so a one-off can cross midnight.
  3. A reminder that follows the repeat keeps its day: only its time moves, stopping at 00:00 or 23:55.
  4. The rule changed and the reminder follows the repeat: `startedFrom(now.rule, now.day, today, zone)` gives its new first day and rule. A null answer (the series has ended) deletes the reminder. A null `now.rule` makes it `Once` on its shifted day. An unknown `now.day` with a COUNT, or a rule that doesn't parse, leaves the rule alone.
  5. The reminder has its own repeat and its weekday moved: step as written above.
  6. `startsAtMillis` null (a series with no date left) deletes the reminder. `startsAtMillis` at or before now deletes a `Once`. An all-day event's start, for this rule and for Task 24's, is the end of its day.
  7. Otherwise it restarts: "answered at" is now, or, when the reminder's own next time is already past, one millisecond before that time, so it shows.
  8. The link takes `now`; log `event moved`; true.
  Add tests: `aOneOffShiftedAcrossMidnightChangesDay`, `aSeriesThatHasEndedDeletesAFollowingReminder`, `anAllDayEventMovedToTodayKeepsItsReminder`, `aReminderMovedToBeforeItWasMadeStillShows`, `changingTheDayOnTheTabStopsFollowingTheRepeat`.
- The event key holds no control character (Task 24 builds it); it is stored as given.
- The two log lines need no `LogHygieneTest` allow-list entry; that test runs in `:build-logic:convention:test`.

- [ ] **Step 1: Write the failing tests** in `RemindersForEventsTest` (Robolectric, in-memory database, fake clock): `anEventWithNoReminderOpensTheFormFilledIn`, `anAllDayEventOpensAtEight`, `aChildsFormIsTheirOwn`, `anEventWithAReminderOpensThatReminder`, `savingRemembersTheEvent`, `aReminderOnTheEventsDayWithItsRepeatFollowsIt`, `aReminderMovedToAnotherDayKeepsItsOwnRepeat`, `goneDeletesWithNoToast`, `goneForAnUnknownKeyIsFalse`, `aMovedEventShiftsTheReminderByTheSameAmount`, `theSameTimesChangeNothing`, `aNewRuleReplacesAFollowingRemindersRule`, `aNewRuleLeavesAnOwnRepeatAlone`, `anEventMovedIntoThePastDeletesAOneOff`, `aReminderWhoseOwnTimeHasPassedShowsNow`, `aSeriesWithNoKnownFirstDayOnlyShiftsTheTime`, `theLogLinesCarryNothingElse`.
- [ ] **Step 2:** Run `./gradlew :capability:reminders:testDebugUnitTest --tests '*RemindersForEventsTest'`. Expected: FAIL, unresolved.
- [ ] **Step 3:** Implement the seam types, the optional binding, the store queries and `RemindersForEvents`.
- [ ] **Step 4:** Run `./gradlew :capability:reminders:testDebugUnitTest :core:house:testDebugUnitTest :app:testDebugUnitTest --tests '*LogHygiene*'` (or wherever `LogHygieneTest` lives). Expected: PASS.
- [ ] **Step 5:** Add one screenshot, `reminder_form_from_event_dark.png`: the form opened from a sample event with a kept rule. Record, restore the unrelated, verify.
- [ ] **Step 6:** Commit: `git commit -m "Let a reminder remember the event it was added from"`.

### Task 24: The button on the event's card

**Files:**
- Create: `capability/calendar/.../EventReminderSeeds.kt`, test `EventReminderSeedsTest.kt`.
- Modify: `ui/EventDetailSheet.kt` (a footer for every event; the new button first, then Delete and Change when editable), `ui/EventDetailHost.kt`, `ui/Sheets.kt`, `CalendarRepository.kt` or wherever `EventDetailUi` is built, `di/CalendarModule.kt` (inject `Optional<EventReminders>`), `DetailScreenshotTest.kt`.

**Interfaces:**
- Consumes: Task 23's `EventReminders`, `EventSeed`, `EventTimes`; Task 18's `startedFrom`.
- Produces:
```kotlin
/** The link's key for [event]: connection, source and the series' id or the event's own, joined as EventRef.listKey joins. */
internal fun eventKey(event: StoredEvent): String

/** [event]'s times for the link; [siblings] are the stored dates of its series (empty for a one-off). */
internal fun eventTimes(event: StoredEvent, siblings: List<StoredEvent>, zone: ZoneId, today: LocalDate): EventTimes

/** Null when no reminder can be added: a bin collection, a one-off that has started, a series with no date left. A rule the shared code can't work out gives a one-off for [event]'s own date. */
internal fun reminderSeed(event: StoredEvent, siblings: List<StoredEvent>, people: List<PersonId>, nowMillis: Long, zone: ZoneId): EventSeed?
```
- `EventDetailSheet` gains `reminder: ReminderButton?` (`enum class ReminderButton { Add, Change }`, null hides it) and `onReminder: () -> Unit`; tag `detail_reminder`; labels `Add reminder` and `Change reminder`; the alarm icon Task 5 added.
- The host collects `has(eventKey)` and calls `open(host, seed)`; with no `EventReminders` bound the button is null.

**After the plan review (binding; wins over this task's other text where they differ):**
- `eventKey(connectionId: String, sourceId: String, id: String): String` joins the three parts, each URL-encoded, with `/`; `eventKey(event: StoredEvent)` calls it with `seriesId ?: remoteId`. Not `EventRef.listKey`, whose NUL would be stored in `reminders.db`.
- `CalendarStore` gains `suspend fun seriesRows(connectionId: String, sourceId: String, seriesId: String): List<StoredEvent>`.
- `Optional<EventReminders>` is injected into `CalendarEditor` (a constructor default of `Optional.empty()` keeps its tests compiling). The detail host reaches Reminders only through the editor: `fun reminderButton(ref: EventRef): Flow<ReminderButton?>` and `suspend fun openReminder(host: OverlayHost, ref: EventRef)`.
- `eventTimes`: the rule is `event`'s own (the row tapped); the time is the commonest among `siblings` from today on; the day is the rule's first-date line.
- A series with a COUNT and no first-date line seeds a one-off for the date tapped.
- An all-day one-off has "started" only once its day is over.
- `EventDetailSheet`'s two new parameters have defaults (`reminder = null`, `onReminder = {}`).
- More files: `TestEngines.kt`, `SampleAddTest`, `SampleRollbackTest` (they build `CalendarEditor`).

- [ ] **Step 1: Write the failing tests.** `EventReminderSeedsTest`: `aOneOffSeedsItsStart`, `anAllDayEventHasNoTime`, `aStartedOneOffHasNoSeed`, `aBinCollectionHasNoSeed`, `aSeriesSeedsTheDateTapped`, `aPastDateOfASeriesSeedsTheNext`, `aSeriesFromALaterDateCarriesWhatIsLeft` (COUNT reduced), `aFinishedSeriesHasNoSeed`, `aRuleThatCannotBeWorkedOutSeedsAOneOff`, `aOneOffAndItsLaterSeriesShareAKey`, `aSeriesTimeIsItsCommonestComingTime`. A detail sheet test: `everyEventHasTheReminderButton` (a repeating one and one on another calendar included), `itReadsChangeReminderOnceThereIsOne`, `withNoRemindersThereIsNoButton`.
- [ ] **Step 2:** Run them. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Re-record the detail baselines (every one gains the button); add `detail_change_reminder_dark.png`; restore the unrelated; verify.
- [ ] **Step 6:** Commit: `git commit -m "Add a reminder from an event's card"`.

### Task 25: Deleting and moving an event on the wall

**Files:**
- Modify: `capability/calendar/.../CalendarEditor.kt` (`delete`/`write`/`report`, `undo`/`reportUndo`, `update`), `CalendarEditorTest.kt`.

**Interfaces:**
- Consumes: `Optional<EventReminders>`, `eventKey`, `eventTimes`.
- Copy: `internal const val EVENT_AND_REMINDER_DELETED = "Event and its reminder deleted."`, `internal const val CHANGES_SAVED_REMINDER_MOVED = "Changes saved. Its reminder moved too."`.
- A delete that ends `Done` or `Queued` calls `gone(listOf(key))` before the toast and picks the toast from its answer. Undo of an add calls `gone` too; its toast is unchanged. An update whose fields include the times, ending `Done` or `Queued`, calls `changed(key, times of the event as now shown, its new start)` and picks the toast from its answer. A failed or refused write calls neither. A failure inside the seam is caught, logged as the class name only, and never fails the event's own write.

**After the plan review (binding; wins over this task's other text where they differ):**
- `say` isn't suspend: call the seam in the suspending part before it and hand its answer in.
- Undo's key comes from the `AddedEvent`: `eventKey(added.connectionId, added.sourceId, added.key)`.
- Every wall write that is accepted or queued stamps its key with the wall's time in `CalendarStore` (in memory): Task 26 reads it.

- [ ] **Step 1: Write the failing tests** in `CalendarEditorTest` with a recording `EventReminders`: `deletingAnEventTakesItsReminder`, `aQueuedDeleteTakesItToo`, `aDeleteThatFailsLeavesTheReminder`, `theToastSaysSoOnlyWhenAReminderWent`, `undoingAnAddTakesItsReminder`, `movingAnEventTellsRemindersItsNewTimes`, `aChangeOfTitleAloneTellsNothing`, `theMovedToastSaysSoOnlyWhenAReminderMoved`, `aSeamThatThrowsDoesNotFailTheWrite`, `withNoRemindersNothingIsCalled`.
- [ ] **Step 2:** Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*CalendarEditorTest'`. Expected: the new tests FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -m "Take or move an event's reminder with it on the wall"`.

### Task 26: What a sync removed or changed

**Files:**
- Modify: `capability/calendar/.../CalendarStore.kt` (`applySync`), `CalendarSync.kt` (`syncSource`), `CalendarStoreTest.kt`, `CalendarSyncTest.kt` (or the nearest existing sync test).

**Interfaces:**
- Produces:
```kotlin
/** What one sync did to events a reminder may hang on. */
sealed interface EventChange {
    data class Gone(val key: String) : EventChange
    data class Changed(val key: String, val now: EventTimes, val startsAtMillis: Long?) : EventChange
}
```
- `CalendarStore.applySync(…)` returns `List<EventChange>` (it returns nothing today; callers that ignore it still compile). Inside its transaction, before the deletes: the keys and `eventTimes` of the rows this result removes or replaces (a full read: the stored rows of the source inside the read range; an incremental one: `removedIds` and the upserted ids). After the upserts: a key with no row left anywhere in the store is `Gone`; a key whose `eventTimes` differ is `Changed`. A key with a change waiting in the outbox is left out.
- `prune`, `removeSourceRows`, `removeConnection`, `applyDeleted` and `applySeriesDeleted` report nothing.
- `CalendarSync.syncSource` passes the list to `Optional<EventReminders>` after `applySync` returns: `gone(all the Gone keys)` once, then `changed` for each. A failure there is caught, logged as the class name only, and never fails the sync.

**After the plan review (binding; wins over this task's other text where they differ):**
- `applySync` gains `nowMillis: Long` as a named parameter after `today`; update `DebugSeedTest`'s positional call.
- **Which rule counts** after an incremental read, where stored rows of one series can carry old and new rules: the rule on this result's upserts for the key when it has any, else the stored rows'.
- **A sync read before a wall write** (`CalendarEditor.kt:405` says it happens): `CalendarSync` notes the wall's time before each provider read, and a key stamped by Task 25 since then is left out of the list. Test `aSyncReadBeforeAWallMoveDoesNotReportTheOldTimes`.
- `ageingOutIsNotARemoval` and `aHiddenCalendarIsNotARemoval` prove nothing (`prune` and a hidden calendar never reach `applySync`). Replace them with `aPastEventTheReadNoLongerCoversIsNotARemoval` and keep `anEventOutsideTheReadRangeIsNotARemoval`.
- A full read can't tell an event a phone moved beyond the read range from a removal: it is reported Gone (spec 10.5 now says so). Test `anEventMovedBeyondTheReadRangeIsTakenAsGone`.
- A series ended on a phone ("this and following") arrives as Changed with a rule that has no date left; Task 23 deletes a following reminder then. `startsAtMillis` is null for it.

- [ ] **Step 1: Write the failing tests.** `CalendarStoreTest`: `aFullReadWithoutAnEventReportsItGone`, `anIncrementalRemovalReportsItGone`, `aChangedStartIsReported`, `anUnchangedEventReportsNothing`, `ageingOutIsNotARemoval` (`prune`), `aHiddenCalendarIsNotARemoval` (`removeSourceRows`), `anEventOutsideTheReadRangeIsNotARemoval`, `aSeriesWithEveryDateRemovedIsGone`, `aSeriesWithOneDateRemovedIsNotGone`, `oneMovedDateOfASeriesChangesNothing`, `aSeriesWhoseUsualTimeMovedIsChanged`, `aSeriesWithANewRuleIsChanged`, `anEventMadeRepeatingKeepsItsKey`, `aQueuedChangeHoldsTheSyncOff`. The sync test: `removalsAndChangesReachReminders`, `aSeamThatThrowsDoesNotFailTheSync`.
- [ ] **Step 2:** Run them. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `./gradlew :capability:calendar:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest :provider:calendar-google:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -m "Tell reminders what a sync removed or moved"`.

### Task 27: Walk-through support, the README and the list

**Files:**
- Modify: the debug source set (`app/src/debug/…`) and `provider/calendar-fake` only as far as its existing debug hooks allow; `README.md`; `docs/superpowers/plans/2026-09-23-plan1-followups.md`.

**After the plan review (binding; wins over this task's other text where they differ):**
- Step 1 needs no main-code change: a debug receiver can call the public `FakeCalendarProvider.delete` and `update`, then `syncSoon()`.
- Follow-ups also gain: Google's "this and following events" makes a new series, so a reminder doesn't follow onto it; an event moved on a phone beyond the stored weeks loses its reminder.

- [ ] **Step 1:** Give the debug build a way to remove one sample event from the sample calendar's side and to move one by an hour, so the next sync sees it as a phone's change (walk-through check 17). Prefer an existing debug receiver's pattern (`DebugClockReceiver`). Nothing of it may exist in a release build: the release mapping must show no trace, as the debug clock's doesn't. If the sample provider can't be reached from the debug source set without changing its main code, STOP and report; this step can be cut (check 17 then rests on Task 26's tests).
- [ ] **Step 2:** README: the reminders lines (repeat choices, the event button, what follows an event) and the calendar's repeat limits (chosen days).
- [ ] **Step 3:** Follow-ups list: take "remind us on a calendar event" out of item 41 and out of "Plan 22 (Reminders) not in this build, on purpose"; add there: a repeat that ends on a date; one date of a series skipped or moved isn't followed; a reminder isn't told when its event's title or people change.
- [ ] **Step 4:** Run the whole gate. Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -m "Support the second Reminders walk-through and bring the README up to date"`.

### Task 28: Simplify

- [ ] **Step 1:** Run the `simplify` skill on the diff since `3afd4982`.
- [ ] **Step 2:** Run the whole gate. Rerun a load flake by itself.
- [ ] **Step 3:** Commit: `git commit -m "Simplify the shared repeats and the event link"`.

### Task 29: The second walk-through, the review and the release

- [ ] **Step 1:** Delete the emulator's `reminders.db` (`adb -s emulator-5554 shell run-as uk.co.siland.culvery rm -f databases/reminders.db databases/reminders.db-shm databases/reminders.db-wal` with the app stopped), install the debug build with `install -r`.
- [ ] **Step 2:** Walk checks 12 to 18 of spec 10.8, and checks 1, 2 and 9 of the first walk-through again (the form changed). Collect faults and ask before fixing. Put everything back.
- [ ] **Step 3:** Whole-branch review of `3afd4982..HEAD` on the most capable model, with Tasks 21, 23 and 26 named as the state work.
- [ ] **Step 4:** Record the walk-through on the follow-ups list; mark item 20 done.
- [ ] **Step 5:** Release build, mapping to `culvery-notes/mappings`, `adb install -r` on the SM-T510 when the user has plugged it in. Merge only on the user's say.

## Part 2 tasks that can be cut

- **Task 27 Step 1** alone, at the cost named there.
- **Task 26** alone: reminders then follow only what is done on the wall. That breaks ruling 5 of spec 10.1, so it is a fallback, not a suggestion.

## Part 2 self-review

- **Spec coverage:** 10.2 → Tasks 17 to 20; 10.3 → 21, 22; 10.4 → 23, 24; 10.5 → 23, 25, 26; 10.6 → 17, 20, 23; 10.7 → the tests named in each task; 10.8 → 27, 29.
- **Names checked across tasks:** `RepeatRule`, `startedFrom`, `daysOf`, `repeatWords`, `RepeatPick`, `HhRepeatPicker`, `Schedule.Repeating`, `EventLink`, `EventTimes`, `EventSeed`, `EventReminders`, `eventKey`, `eventTimes`, `reminderSeed`, `EventChange`.
- **Review Focus:** each of the five has its test in Task 18, 21, 24 or 26.
