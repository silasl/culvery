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
- "Remind us" on a calendar event.
- Monthly or yearly repeats.
- A sound chosen per reminder, a chime that repeats, or a spoken reminder.
- Anything drawn on the night clock or the dark screen.
- Reminders shown on the calendar or on a Home card.
- Night-holding in the alert layer for every capability (the Plan 8 follow-up). Reminders applies its own rules.
