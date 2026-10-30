# Culvery Plan 15a: the end-of-v1 review's screen changes. Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–7 are subagent tasks; Task 8 is the controller with the user on the emulator; Task 9 needs the user and the SM-T510.

**Goal:** The One-tap tray as the handoff draws it, a Scene pill on the Lights card, a Calendar that goes 4, 8 or 12 weeks ahead, door history kept 2, 7 or 30 days with a Clear history button, and plugs as tiles on the Lights tab.

**Architecture:** Changes to existing modules only.
- **Tray:** `TrayButton` gains a width, and `HhTrayGrid` (replacing `HhTrayColumn`) lays the tray out in three fixed rows.
- **Calendar:** a small DataStore, `CalendarSettings`, holds the weeks shown. `CalendarRepository` reads it and exposes it to the week view and the editor. The sync always reads 12 weeks.
- **Door:** `DoorStore` takes a keep-from cut-off, read from the stored `DoorSettings` and the household zone. It gains `clear()`, behind a new `DoorPermissions.CLEAR` that also guards shortening the keep.
- **Plugs:** plugs reach the Lights tab through a `LightsTabSection` seam in `:core:plugin`, because it needs `@Composable`, which `:core:house` doesn't have. The Plugs capability stops having a tab.
- **Rail:** `Capability` gains a reactive rail label and icon, so the tab can read "Plugs" when there are no lights.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, Room 2.8.5, DataStore 1.2.1, Coroutines 1.10.2, JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1.

**Spec:** `docs/superpowers/specs/2026-10-30-culvery-15a-review-screens-design.md`. Read it with this plan. The source of truth for what was agreed is `culvery-notes/culvery-design-review-2026-10-30/changes.md`. Handoff: `docs/design/2026-10-redesign/handoff.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Task 9 updates it).

**Builds on:** branch `plan-15a-review-screens` at 4a159003 (the spec), in the one checkout `C:\Users\si\code\siland\culvery`. No worktrees. Never push. File and line references were read at 4a159003. Subagents use `C:/t9` for scratch, and never point Gradle's temp folder at a long path.

**Plan review (2026-10-30):** three reviewers (buildability; security and state; tests and simplicity). The user ruled that shortening the keep asks for a confirm and then a fresh PIN, and that sync always reads 12 weeks. Everything else is folded in below.

| # | Task | Review |
|---|---|---|
| 1 | The tray: `TrayButton.width`, `HhTrayGrid`, Home's tray, the shared ON pill, Party's "Off" goes | sonnet |
| 2 | The Lights card's Scene pill | sonnet |
| 3 | Calendar weeks shown: `CalendarSettings`, the repository, the week view, the editor, the 12-week sync, Settings › Calendars | sonnet |
| 4 | Door history kept 2, 7 or 30 days: the setting, `DoorPermissions`, the prune, the loop, every kept day on the tab, the shorter-keep confirm and PIN | **opus** (data, privacy) |
| 5 | Clear history: `DoorStore.clear()`, the button, confirm, PIN, toast, the ring card, secure delete | **opus** (deletes files) |
| 6 | The `LightsTabSection` seam and plug tiles in the plugs module; Edit, the mode columns and `PlugRow` go; the stale copy | sonnet |
| 7 | Lights and plugs: the tab draws the section, one scroll, the header, the title, the rail label | **opus** (shell, Hilt graph) |
| 8 | Emulator walk-through with the user; simplify | controller + user |
| 9 | Tablet install, final review, local merge, to-do list | controller + user, **needs the SM-T510** |

**Task order and why:**
- Tasks 1 and 2 are independent Home changes.
- The Calendar (3) and the Door (4, 5) don't touch each other.
- 4 comes before 5: it adds `DoorPermissions` and the access wiring, and reworks the file sweep that `clear()` reuses.
- 6 builds the plugs half of the merge before 7 wires it into Lights and the shell.
- Every task leaves `testDebugUnitTest verifyRoborazziDebug` green.

## Global Constraints

- **Units.** Measurements in the spec and the handoff are px on a 1920 × 1200 artboard: **dp = px ÷ 1.5**, and sp likewise. Never go below a 48 dp touch target (`ButtonTokens.regularHeight`).
- **Exact copy:**
  - "Clear all {n} door events? Pictures saved on this tablet are deleted too. Ring keeps its own copies."
  - "Door history cleared"
  - "Delete door events older than {n} days?"
  - "Keep door history for", with "2 days", "7 days", "30 days"
  - "Weeks ahead in the Calendar", with "4 weeks", "8 weeks", "12 weeks"
  - "Lights and plugs"
  - "{on} of {n} rooms on · {on} of {n} plugs on"
  - "More below"
  - "Scene"
  - "None"
  - Use curly apostrophes (’) in copy.
- **Logs.** No personal data in logs: only exception class names, as in the existing `Log.w(TAG, "… (${e::class.simpleName})")`. A new log line or TAG means running `.\gradlew.bat -p build-logic :convention:test` (LogHygieneTest).
- **No deprecated APIs** without asking the user first. Flag any you meet.
- **Commits** are plain sentences with no trailers.
- **Dependencies.** No new libraries, fonts, sounds or icons. Adding an existing library or project to another module's `build.gradle.kts` is allowed: Task 3 adds `libs.androidx.datastore.preferences` to the calendar, and Task 4 adds `:core:access` to the door. If an icon is missing from the icon font, stop and ask.
- **Tests:**
  - Run `.\gradlew.bat :<module>:testDebugUnitTest` per task.
  - Before each commit, run the gate: `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug`.
  - Record screenshots only on Windows (`recordRoborazziDebug`), and only for the screens the task changes. Look at every changed PNG before committing it.
  - Delete the reference PNGs of screenshot tests the task removes.

## Review Focus

1. **A record mid-write when Clear history runs** must not leave a `.jpg` or `.part` file. A clear cancelled halfway must not leave pictures either. Tested in Task 5.
2. **The clocks going back on Sun 25 Oct 2026 in London** must not shift the keep cut-off or the day groups by an hour. Tested in Task 4 with today Mon 26 Oct.
3. **The weeks setting shrinking** (12 to 4) while the week view is 10 weeks on, and the editor's `WeekFocus` asking for a week past the max, must both clamp. Tested in Task 3.
4. **Plugs only, no Hue bridge:** the tab shows titled "Plugs", the rail says "Plugs", and there's no Lights card on Home. Tested in Task 7.
5. **A cold start with "30 days" saved** must not prune to 2 days before the settings file is read. Tested in Task 4.

---

## Rulings against the code

Where the spec doesn't fit the code at 4a159003:

1. **`HouseActions.onHoliday` stays.** The Lights tab's tray uses it (`LightsTab.kt:77`), and Holiday lights stays on the tab. Only `LightsCard` stops using it: `LightsCard` and `LightsCardContent` lose `holidayOn` and `run`.
2. **The Scene pill is a 48 dp quiet pill**, the height of All lights off beside it. The handoff's 68 px is 45 dp, under the touch target. Its inner chip is 31 dp (46 px). The ON pill keeps its current sizes (24 dp, `HhType.labelSmall`).
3. **The Hoover Start pill stays at 48 dp.** The tray's edge stays at `PageHeaderTokens.trayEdge`.
4. **The plugs section is a `LightsTabSection` seam in `:core:plugin`.** `:core:house` has no Compose, and `ModuleBoundaries` forbids lights and plugs depending on each other. The Plugs module binds it `@IntoSet`; the Lights module declares `@Multibinds` and draws whatever is bound (none or one).
5. **The rail label and icon become reactive.** `Capability` gains `val railLabel: Flow<String> get() = flowOf(label)` and `val railIcon: Flow<String> get() = flowOf(icon)`. `ShellViewModel.tabs` (`:94-111`) combines them with the same `onStart` and `retryWithBackoff` it gives `hasTab` and `badge`, so a stuck flow can't block the rail. Only Lights overrides them.
6. **Lights' `hasTab` widens to bridge-or-plugs, and its Home card doesn't.** `cards()` (`LightsCapability.kt:50`) is gated on the bridge alone.
7. **Sync always reads 12 weeks** (the user's ruling). `SYNC_FUTURE_DAYS` becomes 84, and `SYNC_AHEAD_DAYS` stays 42. The setting doesn't reach `CalendarSync`, so nothing triggers a sync when it changes. On the first pass after the update, the store's cursor (`CalendarStore.kt:260-265`) doesn't cover the wider window, so it reads in full once.
8. **The weeks setting is an enum** stored by name in a DataStore file, `calendar_settings`: `enum class WeeksShown(val weeks: Int) { FOUR(4), EIGHT(8), TWELVE(12) }`, default `EIGHT`. Door's keep uses the same pattern. No Room migration.
9. **`lastShownDay` and `weeksTo` take the setting with no default:** `lastShownDay(today, shown: WeeksShown)` and `weeksTo(today, date, shown)`. That way the compiler finds every caller. Fixtures pass `WeeksShown.EIGHT`.
10. **The door's cut-off comes from the stored setting, never the stand-in default.** `DoorStore` gets `keepFrom: suspend (nowMillis: Long) -> Long?`. Its `@Inject` constructor reads `settings.prefs.first().keep`, not `current.value`, which holds the defaults until the file is read. On a read failure the result is null, and the prune skips the row delete but still sweeps the orphan files. The internal constructor defaults `keepFrom` to two days in Europe/London, so the five existing `DoorStore(db, dir)` test constructions keep compiling.
11. **The prune also runs at each household midnight and when the keep changes.** `DoorPruneLoop` waits until the next `atStartOfDay(zone)` instead of a fixed `DAY_MS`. It also collects `settings.prefs.map { it.keep }.distinctUntilChanged().drop(1)`, inside its existing catch, so a keep change prunes off the UI. `DoorPruneLoop.kt:21`'s "48 hours" KDoc is updated.
12. **`doorDays(events, today, zone, keep): List<DoorDay>`** groups by household date, newest first, with empty days left out. It drops anything before the keep cut-off, so the tab never shows a day the prune hasn't reached yet. `data class DoorDay(val date: LocalDate, val events: List<DoorEvent>)`.
13. **Clear history also closes the ring card.** No bitmap cache exists (`SnapshotImage` holds its bitmap only in `produceState`), and the tab's `SnapshotSheet` can't be open during a clear, because the confirm card replaces it in the overlay host. The 60-second ring card from `DoorAlerts` (`:78-91`) can be, so `clear()`'s caller closes it.
14. **Deleted door rows don't linger in the database file.** `DoorDatabase` turns on `PRAGMA secure_delete=ON` in a `RoomDatabase.Callback.onOpen`, and `clear()` ends with `PRAGMA wal_checkpoint(TRUNCATE)`.
15. **Copy that says "Plugs tab" changes.** It becomes "Lights tab" in `HouseLines.PLUGS_MISSED` and `PLUGS_LEAVE_THREW` (`HouseSeams.kt:35-36`), in `PlugsWords.leaveLine` (`:87-89`), and in `SHOW_ON_TAB` and `PLUGS_HELP` (`:18-19`). `PartyWords.PLUGS_LINE` (`:47`) becomes "Party mode keeps the plug choices it has. A later update lets you change them here." The KDocs at `HouseSeams.kt:101`, `HouseModule.kt:36`, `PlugsModule.kt:20`, `Capability.kt:12`, `PlugsContract.kt:45`, `PlugSettings.kt:52`, `PlugActions.kt:21` and `PlugsDimens.kt:6` are updated to match.
16. **The Plugs capability keeps `id = "plugs"` and its Settings page.** `hasTab` becomes `flowOf(false)`. `PlugsTab.kt`, `PlugRow.kt`, `EDIT_IDLE_MS` and `PlugActions.mayManage` are deleted. `PlugActions.setChoice` and `PlugsPermissions.MANAGE` stay for 15b, each with a one-line KDoc saying so.

---

### Task 1: The tray

**Files:**
- Modify: `core/plugin/.../TrayButton.kt`, `core/ui/.../PageHeader.kt` (`HhTrayColumn:144` becomes `HhTrayGrid`), `core/ui/.../HouseMode.kt`
- Modify: `app/.../shell/ui/HomeTray.kt`
- Modify: `capability/holiday/.../HolidayCapability.kt:32`, `ui/HolidayTile.kt:36-71`, `ui/HolidayDimens.kt`, `HolidayWords.kt:13`
- Modify: `capability/party/.../PartyCapability.kt:43`, `ui/PartyTile.kt:24-42`, `PartyWords.kt:24-27`
- Test: `app/src/test/.../shell/ui/TrayRowsTest.kt` (new), `HomeGridTest.kt`, `ShellScreenshotTest.kt`; `core/ui/src/test/.../PageHeaderTest.kt:152-213`, `PageHeaderScreenshotTest.kt:48-65`; `capability/party/src/test/.../ui/PartyTileTest.kt` (`:64`, `:74-77`), `PartySheetTest.kt` (`:161`, `:184`, `:215`), `PartyScreenshotTest.kt`; `capability/holiday/src/test/.../ui/HolidayTileTest.kt`, `HolidaySheetTest.kt:85`, `HolidayScreenshotTest.kt:44`; `capability/hoover/src/test/.../ui/HooverRowTest.kt:64`, `HooverRowScreenshotTest.kt:28`

**Interfaces:**
- Produces: `enum class TrayWidth { HALF, FULL }` and `class TrayButton(val id: String, val order: Int, val width: TrayWidth = TrayWidth.FULL, val content: @Composable () -> Unit)`. `width` comes before the trailing lambda, so existing `TrayButton(id, order) { }` calls still compile.
- Produces: in `:core:ui`, `typealias TrayCell = Pair<String, @Composable () -> Unit>`, `const val TRAY_ROWS = 3` and `@Composable fun HhTrayGrid(label: String, rows: List<List<TrayCell>>, modifier: Modifier = Modifier)`.
- Produces: in `:app`, `internal fun trayRows(buttons: List<TrayButton>): List<List<TrayCell>>`. `buttons` arrive already sorted by `HomeCardPlacer.place` (`:31`).
- Produces: `@Composable fun HouseModeOnPill(tag: String)` and `HouseModeWords.ON = "ON"` in `core/ui/.../HouseMode.kt`. The pill's sizes move from `HolidayDimens` into `HouseModeDimens`.

- [ ] **Step 1: Write the failing tests**

`TrayRowsTest`:
```kotlin
@Test fun twoHalvesShareARow() = assertThat(ids(trayRows(listOf(half("holiday.mode", 10), half("party.mode", 15), full("hoover.start", 20)))))
    .containsExactly(listOf("holiday.mode", "party.mode"), listOf("hoover.start")).inOrder()
@Test fun aLoneHalfTakesItsOwnRow() = assertThat(ids(trayRows(listOf(half("party.mode", 15), full("hoover.start", 20)))))
    .containsExactly(listOf("party.mode"), listOf("hoover.start")).inOrder()
@Test fun aFullBetweenHalvesBreaksThePair() = assertThat(ids(trayRows(listOf(half("a", 10), full("b", 20), half("c", 30)))))
    .containsExactly(listOf("a"), listOf("b"), listOf("c")).inOrder()
```
- `PageHeaderTest`: rewrite the `HhTrayColumn` cases on `HhTrayGrid`. Add `halvesAreEqualWidthAndShareATop`.
- `HomeGridTest`: `trayKeepsThreeRowsWithOnlyOneMode`. With only `holiday.mode`, `tray_row_holiday.mode` is a third of the tray's height, less the label and two gaps, to within 1 dp. Its width is the tray's width less padding.
- `PartyTileTest`:
  - Replace `offReadsOff` (`:64`) with `offShowsNoStateLine`: `party_tile_state` doesn't exist when off and not running.
  - Replace `onReadsOn` (`:74-77`) with `onShowsTheOnPill`: `party_tile_on` reads "ON".
  - `tileSpoken` still says on or off.
  - Move `PartySheetTest`'s "On" state checks (`:161`, `:184`, `:215`) to `party_tile_on`.
- `HolidayTileTest`, `HolidaySheetTest`, `HooverRowTest` and their screenshot tests: wrap the tile in `HhTrayGrid` at its real width. Holiday is half of the 333 dp tray, Hoover is full width.

- [ ] **Step 2: Run them to see them fail**

Run: `.\gradlew.bat :core:ui:testDebugUnitTest :app:testDebugUnitTest :capability:party:testDebugUnitTest`
Expected: the new code doesn't compile yet (`TrayWidth`, `trayRows`, `HhTrayGrid`).

- [ ] **Step 3: Implement**
- `HhTrayGrid` draws the "ONE-TAP" label, then `TRAY_ROWS` rows, each at `weight(1f)` with `TrayTokens.gap` between them. Rows past `rows.size` are empty `Spacer`s with no outline. Two cells split a row `weight(1f)` each. Keep the testTag `tray_row_$id` on each cell.
- Delete `HhTrayColumn`: it has no other caller in main code, and the Lights tab uses `HhTray`.
- `HomeTray` calls `HhTrayGrid("One-tap", trayRows(buttons), Modifier.fillMaxSize())`.
- Holiday and Party pass `width = TrayWidth.HALF`.
- `PartyTile`: `second` is null unless running. Pass `trailing = { if (on) HouseModeOnPill("party_tile_on") }`. Delete `PartyWords.ON` and `OFF`.
- `HolidayTile`: `HouseModeOnPill("holiday_tile_on")` replaces the private `OnPill`.
- Check `TrayTokens` and `HouseModeDimens` against the handoff's "One-tap tray", with px ÷ 1.5: tray radius 24 dp, padding 11 dp, gap 8 dp; tile radius 17 dp; icon 24 dp; label 15 sp/700. Change only what differs.

- [ ] **Step 4: Run the tests to see them pass.** Run the same command. Expected: PASS.

- [ ] **Step 5: Record and look at the screenshots**

Record `home_tray_*` (shopping, no shopping, alone; dark and light), the party and holiday tile shots, the hoover row shots and the tray grid shots. Delete the PNGs of renamed tests. Then run the gate.

- [ ] **Step 6: Commit:** `git commit -m "Lay the One-tap tray out as the handoff draws it, with the modes side by side and no Off line"`

---

### Task 2: The Lights card's Scene pill

**Files:**
- Modify: `capability/lights/.../ui/LightsCard.kt:44-134`, `ui/LightsParts.kt:50` (`MainRoomActions`), `LightsWords.kt` (beside `cardStatus:43`), `ui/LightsDimens.kt`
- Test: `capability/lights/src/test/.../LightsWordsTest.kt`, `ui/LightsCardTest.kt`, `ui/LightsCardScreenshotTest.kt`, `ui/LightsAllOffLitTest.kt:50,98`, `ui/Screenshots.kt`; `app/src/test/.../ShellScreenshotTest.kt:247`

**Interfaces:**
- Produces: `internal fun nextScene(room: LightRoom): LightScene?`, `internal fun currentSceneName(room: LightRoom): String` (the active scene's name, or "None"), `internal const val NEXT_SCENE = "go to the next scene"` (the click label), and `internal fun scenePillSpoken(room: LightRoom) = "Scene, ${currentSceneName(room)}"`.
- Changes: `MainRoomActions(onToggle, onOpen, onScene: (LightScene) -> Unit, onDrag = {}, onLetGo = {})`. `onScene` has no default, so a host can't forget it.
- Changes: `LightsCard(main, allOffLit, status, line, mainRoom, house, modifier)`. `holidayOn` and `run` go (Ruling 1).

- [ ] **Step 1: Write the failing tests**

`LightsWordsTest`:
```kotlin
@Test fun nextSceneFollowsTheActiveOne() = assertThat(nextScene(room(scenes = listOf(RELAX, READ, BRIGHT), active = READ.id))).isEqualTo(BRIGHT)
@Test fun nextSceneWrapsRound() = assertThat(nextScene(room(scenes = listOf(RELAX, READ), active = READ.id))).isEqualTo(RELAX)
@Test fun withNoActiveSceneTheFirstIsNext() = assertThat(nextScene(room(scenes = listOf(RELAX, READ), active = null))).isEqualTo(RELAX)
@Test fun anUnknownActiveSceneCountsAsNone() = assertThat(nextScene(room(scenes = listOf(RELAX), active = "gone"))).isEqualTo(RELAX)
@Test fun noScenesNoNext() = assertThat(nextScene(room(scenes = emptyList()))).isNull()
@Test fun theNameIsNoneWithoutAnActiveScene() = assertThat(currentSceneName(room(scenes = listOf(RELAX), active = null))).isEqualTo("None")
```
`LightsCardTest`:
- `thePillShowsTheCurrentScene`: `lights_card_scene` contains "Relax".
- `noScenesNoPillAndAllOffTakesTheRow`: there's no `lights_card_scene`, and `lights_card_all_off` is as wide as the row.
- `noHolidayButton`: there's no `lights_card_holiday`.
- `theHostRecallsTheNextScene`: through `LightsCardHost` with `TestLights`, tapping `lights_card_scene` records `recallScene Lounge Read`.
- Delete the Holiday cases at `:142` and `:195-218`.

`LightsCardScreenshotTest`: drop `holidayDark` (`:56`) and its PNG. Add `sceneNoneDark` and `noScenesDark`.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :capability:lights:testDebugUnitTest`

- [ ] **Step 3: Implement**
- Add a private `ScenePill(room, onNext)` in `LightsCard.kt`: a quiet 48 dp pill on `ButtonSurface.Surf` reading "Scene", with a 31 dp chip holding `currentSceneName(room)`. Tag it `lights_card_scene`, and use `scenePillSpoken` and `NEXT_SCENE`. Show it only when `room.scenes.isNotEmpty()`.
- In `LightsCardHost`, set `onScene = { scene -> room?.let { actions.recallScene(it, scene) } }`. `recallScene` already turns an off room on (`HueSystem.kt:163-170`).
- Update `LightsAllOffLitTest:50,98`, `ShellScreenshotTest:247` and every `MainRoomActions(...)` construction, `NO_MAIN_ROOM_ACTIONS` included.

- [ ] **Step 4: Run the tests to see them pass.** Then record the lights card shots and the app's Home shots, look at them, and run the gate.

- [ ] **Step 5: Commit:** `git commit -m "Swap Holiday lights on the Home Lights card for a Scene pill that steps to the room's next scene"`

---

### Task 3: Calendar weeks shown

**Files:**
- Create: `capability/calendar/.../CalendarSettings.kt`
- Modify:
  - `capability/calendar/build.gradle.kts`: add `implementation(libs.androidx.datastore.preferences)`.
  - `CalendarUi.kt:184-227`, `CalendarSync.kt:21-31`, `CalendarRepository.kt:117-135` and its constructor
  - `ui/WeekView.kt:443-460`, `ui/WeekHeader.kt:154`, `ui/CardHosts.kt:40-78`
  - `EventEditorHost.kt:179`, `CalendarEditor.kt:153`
  - `ui/ReviewCalendars.kt:144-200`, `CalendarReview.kt:45-116` and its constructor
  - `CalendarSetupSteps.kt:45-52`, `CalendarCapability.kt:28-47`
- Test:
  - New: `CalendarSettingsTest.kt`, and a `testCalendarSettings()` fixture next to `TestEngines.kt`.
  - `CalendarSyncTest.kt:118-124,157-165,187-199`, `CalendarRepositoryTest.kt:636,668`, `MonthTitleTest.kt:55`, `ui/WeekViewTest.kt:63,643,655,756`, `ui/WeekViewHostTest.kt`, the review-calendars screen test and screenshot.
  - Every construction of `CalendarRepository`, `CalendarSync`, `WeekViewHost` and `CalendarReview`: `CalendarRepositoryTest`, `TestEngines.kt:46`, `WeekViewHostTest`, `OpenEventTest`, `CardHostsMidnightRolloverTest`, `CalendarConnectHostTest`, `CalendarCapabilityTest.kt:42` (`stubReview`), `CalendarReviewTest`, and `app/src/testDebug/.../SampleAddTest.kt` and `SampleRollbackTest.kt`.
  - Every fixture that calls `lastShownDay(today)`: HeldTest, ClashTest, ClashLineTest, CheckPaneTest, EventEditorScreenTest, StartsEndsPanesTest, AddEventScreenshotTest, CalendarEditorTest.

**Interfaces:**
- Produces:
  - `enum class WeeksShown(val weeks: Int) { FOUR(4), EIGHT(8), TWELVE(12) }`, with `val weeksAfter get() = weeks - 1`.
  - `class CalendarSettings internal constructor(store: DataStore<Preferences>, scope: CoroutineScope)`, plus an `@Inject @Singleton` secondary constructor that takes `@ApplicationContext context` and `@ApplicationScope scope`, as `DoorSettings` does (`DoorSettings.kt:77-93`). File `calendar_settings`. It has `val shown: Flow<WeeksShown>` and `suspend fun setShown(shown: WeeksShown)`. An unknown name reads as `EIGHT`.
- Produces: `fun lastShownDay(today: LocalDate, shown: WeeksShown): LocalDate` and `internal fun weeksTo(today: LocalDate, date: LocalDate, shown: WeeksShown): Int?`. `MAX_WEEKS_AHEAD` goes, and `SYNC_FUTURE_DAYS = 84L` (Ruling 7).
- Produces: `CalendarRepository.shown: StateFlow<WeeksShown>`. The repository is the one owner, and the week view, the editor and `CalendarEditor.kt:153` read it there.
- Changes: `WeekShown` holds `max` as Compose state. `rememberWeekShown(today, max: Int)` re-clamps `weeks` when `max` changes, and the host clamps `WeekFocus.requested` to `0..max` too.

- [ ] **Step 1: Write the failing tests**
- `CalendarSettingsTest`: `defaultsToEight`, `keepsAChoice`, `anUnknownNameFallsBack`.
- `lastShownDayFollowsTheSetting`: from today, Fri 30 Oct 2026, `FOUR` gives Thu 26 Nov, `EIGHT` gives Thu 24 Dec, and `TWELVE` gives Thu 21 Jan 2027.
- `CalendarSyncTest`:
  - `theWindowIsTwelveWeeks`: `a.calls.single().range.endExclusive == today + 85 + SYNC_AHEAD_DAYS`.
  - Rewrite `:118-124` (the furthest week of `TWELVE` lies inside the window), and `:157-165` and `:187-199` with the new numbers.
  - `aWiderWindowReadsInFullOnce`: a store holding a cursor keyed to the old 29-day window gets `cursor == null` on the next pass, and the pass after that reuses the new token.
- `CalendarRepositoryTest:636,668`: the `held.lastDay` cap is `min(lastShownDay(today, shown), syncDay + 84)`.
- `WeekViewTest`:
  - `laterStopsAtTheLastChosenWeek`: with `EIGHT`, after 7 clicks `week_later` is disabled.
  - `aShorterSettingStepsBack`: shown at 10, `max` goes from 11 to 3, and week 3 shows.
- `WeekViewHostTest`: `aFocusPastTheMaxShowsTheLastWeek`. `WeekFocus.show(10)` with `FOUR` shows week 3.
- Review screen test: `weeksRowsShowAndChoose`. The tags `calendars_weeks_4`, `_8` and `_12` exist, `_8` is selected, and a click on `_12` calls `onWeeks(WeeksShown.TWELVE)`.
- `CalendarReviewTest`: `weeksChangeAsksForTheSettingsPin`. A refused PIN saves nothing, and an allowed one saves.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :capability:calendar:testDebugUnitTest`

- [ ] **Step 3: Implement**
- `CalendarRepository.held(today)` combines with `settings.shown` for `to` and the cap.
- `WeekViewHost` reads `repo.shown`, then passes `shown.weeksAfter` as `max` to `rememberWeekShown` and `WeekHeader` (`enabled = weeksAhead < max`), and `shown` to `weeksTo`.
- Settings › Calendars: add a "Weeks ahead in the Calendar" section, with three `HhRadioRow`s in a `selectableGroup` after the calendars and before the Connect rows. `ReviewActions` gains `onWeeks: (WeeksShown) -> Unit = {}`. `CalendarReview.setShown(shown)` goes through `change { }` (the `SETTINGS_MANAGE` PIN).

- [ ] **Step 4: Run the tests to see them pass.** Then record the Settings › Calendars shots (dark and light), look at them, and run the gate.

- [ ] **Step 5: Commit:** `git commit -m "Let Settings choose 4, 8 or 12 weeks ahead in the Calendar, and sync 12 weeks always"`

---

### Task 4: Door history kept 2, 7 or 30 days

**Files:**
- Create: `capability/door/.../DoorPermissions.kt`
- Modify:
  - `capability/door/build.gradle.kts`: add `implementation(project(":core:access"))` and `testImplementation(testFixtures(project(":core:access")))`. If `:core:access` has no test fixtures, move `TestAccess` from the calendar's tests into `core/access/src/testFixtures` and point calendar, plugs and lights at it.
  - `DoorStore.kt:27-122`, `DoorSettings.kt:43-144`, `DoorDays.kt`, `DoorPruneLoop.kt`, `DoorWords.kt:37-39,95`, `DoorCapability.kt:30-68`, `di/DoorModule.kt`
  - `ui/DoorTab.kt:67-142`, `ui/DoorSettingsPage.kt:80-215`
- Test: `DoorStoreTest.kt`, `DoorSettingsTest.kt`, `DoorDaysTest.kt`, `DoorPruneLoopTest.kt`, `DoorCapabilityTest.kt`, `DoorPermissionsTest.kt` (new), `ui/DoorTabTest.kt`, `ui/DoorTabScreenshotTest.kt`, `ui/DoorSettingsPageTest.kt`, `ui/DoorSettingsScreenshotTest.kt`, `TestDoor.kt`

**Interfaces:**
- Produces: `object DoorPermissions { const val CLEAR = "door.clear" }` and `class DoorPermissionSource @Inject constructor() : PermissionSource`, with `PermissionDef(CLEAR, "Clear door history", setOf(Role.ADMIN, Role.ADULT), freshPin = true)`. Bind it `@IntoSet` in `DoorModule`, as `PlugsModule.kt:25-26` does.
- Produces: `enum class KeepDays(val days: Int) { TWO(2), SEVEN(7), THIRTY(30) }`, `DoorPrefs.keep: KeepDays = TWO`, and `suspend fun DoorSettings.setKeep(keep: KeepDays)`. Read it with the enum fallback (`DoorSettings.kt:124`).
- Produces: `fun keepFrom(today: LocalDate, zone: ZoneId, keep: KeepDays): Long = today.minusDays(keep.days - 1L).atStartOfDay(zone).toInstant().toEpochMilli()`.
- Changes: `class DoorStore internal constructor(db, dir: File, io = Dispatchers.IO, keepFrom: suspend (Long) -> Long? = { keepFrom(dateIn(it, LONDON), LONDON, KeepDays.TWO) })` (Ruling 10). `KEEP_MS` goes.
- Produces: `data class DoorDay(...)`, `fun doorDays(events, today, zone, keep): List<DoorDay>`, and `fun dayLabel(date: LocalDate, today: LocalDate): String`, which gives "Today", "Yesterday" or `"EEE d MMM"` in `Locale.UK` ("Tue 27 Oct").
- Produces: `fun shortenQuestion(keep: KeepDays) = "Delete door events older than ${keep.days} days?"`.

- [ ] **Step 1: Write the failing tests**
- `DoorSettingsTest`: `keepDefaultsToTwoDays`, `keepIsKept`, `anUnknownKeepFallsBack`.
- `DoorPermissionsTest`: the roles are exactly Admin and Adult, and `freshPin` is set (as `PlugsPermissionsTest`).
- `DoorStoreTest` (replacing the `KEEP_MS` cases at `:108-118`):
  - `twoDaysKeepsTodayAndYesterday`: at `NOW`, an event at 23:59 the day before yesterday goes, with its file. One at 00:00 yesterday stays.
  - `sevenAndThirtyDaysCutAtMidnight`.
  - `theCutHoldsAcrossTheClockChange`: Europe/London, today Mon 26 Oct 2026, `TWO`. The cut is Sun 25 Oct 00:00 BST (24 Oct 23:00Z). An event at 24 Oct 22:30Z goes and one at 24 Oct 23:30Z stays. A cut at UTC midnight fails this test.
  - `aColdStartKeepsThirtyDays` (Review Focus 5): write `THIRTY` to the settings file, then build `DoorSettings` and the store fresh over it, through the `@Inject`-equivalent path. Prune at once: a 10-day-old event stays.
  - `anUnreadableSettingDeletesNoRows`: when `keepFrom` gives null, rows stay and orphan files go.
- `DoorDaysTest`:
  - `groupsEveryKeptDayNewestFirst`: events on 30, 29 and 27 Oct with `SEVEN` give three days in that order.
  - `dropsADayPastTheCut`: with `TWO` on 30 Oct, an unpruned event from 28 Oct isn't shown.
  - `theLongDayIsOneDay`: 25 Oct has 25 hours. An event at 24 Oct 23:30Z is on 25 Oct, and so is one at 25 Oct 23:30Z.
  - `dayLabels`: "Today", "Yesterday", "Tue 27 Oct".
- `DoorPruneLoopTest`:
  - `prunesAtTheHouseholdMidnight`: started at 23:50, it prunes again at 00:00 and not at 23:50 the next day.
  - `aKeepChangePrunes`.
  - `aPruneFailureIsLoggedNotThrown`: the existing case, extended to the keep path.
- `DoorCapabilityTest`: `theBadgeIgnoresOlderUnseenRings`. With `THIRTY`, an unseen ring 3 days old doesn't light the badge.
- `DoorTabTest`: `olderDaysShowUnderTheirDate` ("TUE 27 OCT" with its rows). The header count covers every shown event. Rewrite the existing `DoorDays(today, yesterday)` constructions.
- `DoorSettingsPageTest`:
  - `keepRowsChooseAndSave`: the tags `door_keep_2`, `_7` and `_30`, with `_2` selected. `_7` → `_30` saves with no extra PIN.
  - `aShorterKeepConfirmsThenAsksAFreshPin`: from `THIRTY`, a tap on `_2` shows `door_keep_confirm` with "Delete door events older than 2 days?". Then:
    - Keep: nothing is saved.
    - Confirm with a refused PIN: nothing is saved.
    - Confirm with an Adult PIN: `TWO` is saved.
    - The test's `TestAccess` must have been asked for `door.clear`, even when a Settings session is already signed in.
  - `aChildCantShorten`: a child's PIN doesn't save the shorter keep.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :capability:door:testDebugUnitTest`

- [ ] **Step 3: Implement**
- **The store's cut-off:** `DoorStore`'s `@Inject constructor(db, @ApplicationContext context, settings: DoorSettings, zone: HouseholdZone)` builds `keepFrom` from `settings.prefs.first()`. On an `IOException`, give null. `pruneLocked` calls `keepFrom(now)?.let { dao.deleteBefore(it) }`, then the file sweep.
- **The loop:** `DoorPruneLoop` injects `DoorSettings` and `HouseholdZone` for the midnight wait and the keep change (Ruling 11).
- **Settings › Door:** add `HhSectionLabel("Keep door history for")` and three `HhRadioRow`s in a `selectableGroup`, after quiet hours. `DoorSettingsUi` gains `keep`, and `DoorSettingsActions` gains `onKeep`.
  - A shorter choice in `DoorSettingsHost` shows an inline `HhConfirmCard` on the page, as `ShoppingSettingsPage.kt:195-212` does: tag `door_keep_confirm`, keep "Keep", confirm "Delete", the `question` slot `shortenQuestion(it)`.
  - On confirm it runs `access.authorise(DoorPermissions.CLEAR) ?: return@run`, then `settings.setKeep(it)`.
  - A longer choice calls `change { settings.setKeep(it) }` as the other rows do.
  - `DoorPage` and `DoorSettingsHost` gain `access`, from `DoorCapability`.
- **The Door tab:** `DoorTab` draws `days.forEach { HhSectionLabel(dayLabel(it.date, today)); items(it.events) }`. Keep `NO_RINGS_TODAY` for an empty today. The badge (`DoorCapability.kt:53-57`) is unchanged.

- [ ] **Step 4: Run the tests to see them pass**, plus `.\gradlew.bat -p build-logic :convention:test`. Record the Door tab (with an older day) and Settings › Door shots, dark and light, including the keep confirm. Look at them, then run the gate.

- [ ] **Step 5: Commit:** `git commit -m "Keep door history 2, 7 or 30 days, asking for a fresh PIN to shorten it, and show every kept day on the Door tab"`

---

### Task 5: Clear history

**Files:**
- Modify: `db/DoorDatabase.kt` (`deleteAll`, the `onOpen` callback), `DoorStore.kt` (`clear`, and the `.part` cleanup in `save:100-118`), `DoorAlerts.kt:78-91` (`dismissCard()`), `DoorWords.kt`, `DoorCapability.kt`, `ui/DoorTab.kt:84,117-142`
- Test: `DoorStoreTest.kt`, `DoorAlertsTest.kt` (or wherever the card's dismissal is tested), `ui/DoorTabTest.kt`, `ui/DoorTabScreenshotTest.kt`, `TestDoor.kt`

**Interfaces:**
- Consumes: `DoorPermissions.CLEAR` and the `:core:access` wiring (Task 4).
- Produces: `@Query("DELETE FROM door_event") suspend fun deleteAll()`.
- Produces: `suspend fun DoorStore.clear()`. Under `lock`, inside `withContext(NonCancellable + io)`, it deletes every file in `dir`, then runs `deleteAll()`, then `PRAGMA wal_checkpoint(TRUNCATE)`. Files go first, because a row whose picture is gone draws nothing (`decodeSnapshot` returns null).
- Produces: `fun DoorAlerts.dismissCard()`, which closes the ring card if one shows.
- Produces: `CLEAR_HISTORY = "Clear history"`, `KEEP = "Keep"`, `HISTORY_CLEARED = "Door history cleared"`, `CLEAR_FAILED = "Couldn’t clear the door history. Try again."`, and `fun clearQuestion(n: Int)`. That gives "Clear all $n door events? Pictures saved on this tablet are deleted too. Ring keeps its own copies.", and for n = 1, "Clear the 1 door event? Pictures saved on this tablet are deleted too. Ring keeps its own copies."

- [ ] **Step 1: Write the failing tests**
- `DoorStoreTest`:
  - `clearDeletesEveryRowAndFile`: three events, two of them with pictures, plus a stray `.part` and an orphan `.jpg`. Afterwards `events.first()` is empty and `dir.listFiles()` is empty.
  - `aRecordMidClearLeavesNoPicture` (Review Focus 1), modelled on `aRingArrivingDuringTheMarkIsNotLost` (`:187`), with `runBlocking` and a real IO dispatcher:
    1. A `Snapshot` whose blocking `saveTo` counts down a `started` latch, then waits on a `release` latch.
    2. Launch `record(ringWithPicture)`, await `started`, then launch `clear()`.
    3. Release, and join both.
    4. With one `clear()`, assert there are no rows, `dir` is empty, and `record` returned normally.
  - `aCancelledClearLeavesNoPicture`: cancel the calling job while the file loop runs. Afterwards no `.jpg` remains.
  - `aCancelledSaveLeavesNoPart`: cancel during `saveTo`, and no `.part` file is left.
- `DoorTabTest`:
  - `clearHistoryShowsOnlyWithEvents`: there's no `door_clear` on an empty tab.
  - `clearAsksThenAsksForAPinThenClears`:
    1. Tap `door_clear`. `door_clear_confirm` shows `clearQuestion(3)`.
    2. Tap `door_clear_confirm_yes` (the `confirmTag`; both buttons read "Clear history").
    3. `TestAccess` was asked for `door.clear`. The store has no events and `dir` is empty. The toast says "Door history cleared".
  - `keepChangesNothing`.
  - `aRefusedPinChangesNothing`: the rows are still there and there's no toast.
  - `aChildIsRefused`.
- `DoorAlertsTest`: `dismissCardClosesTheRingCard`.
- `DoorTabScreenshotTest`: `withClearHistoryDark` and `withClearHistoryLight`, and `clearConfirmDark`.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :capability:door:testDebugUnitTest`

- [ ] **Step 3: Implement**
- **The button:** the `DoorTab` header's `trailing` holds a 48 dp `HhButton(CLEAR_HISTORY, icon = Icons.DELETE, tone = ButtonTone.Danger, on = ButtonSurface.Bg)`, tagged `door_clear`. It shows only when the store holds at least one event. `n` counts stored rows, not just shown ones.
- **The confirm card:** `DoorTabHost` shows the confirm through the overlay host as a small sheet holding `HhConfirmCard(tag = "door_clear_confirm", title = CLEAR_HISTORY, keep = KEEP, keepTag = "door_clear_confirm_keep", confirm = CLEAR_HISTORY, confirmTag = "door_clear_confirm_yes", busy, onKeep, onConfirm, confirmIcon = Icons.DELETE) { Text(clearQuestion(n)) }`.
- **On confirm,** dismiss the card, then launch on the application scope, as `PlugActions.switch` does, so the toast survives leaving the tab:
  1. `access.authorise(DoorPermissions.CLEAR) ?: return`.
  2. `alerts.dismissCard()`.
  3. `store.clear()`.
  4. `toaster.show(HISTORY_CLEARED)`.

  An exception logs its class name only and shows `CLEAR_FAILED` as urgent.
- **Wiring:** `DoorCapability` passes `access`, `toaster`, `alerts` and the application scope to `DoorTabHost`.
- **The database:** `DoorDatabase`'s builder adds a callback whose `onOpen` runs `PRAGMA secure_delete=ON` (Ruling 14).
- **`save`:** delete the `.part` file in a `finally` whenever the rename didn't happen.

- [ ] **Step 4: Run the tests to see them pass**, plus the build-logic check if a TAG was added. Record and look at the shots, then run the gate.

- [ ] **Step 5: Commit:** `git commit -m "Add Clear history to the Door tab, behind a confirm and an Adult or Admin PIN, deleting every row and picture"`

---

### Task 6: The `LightsTabSection` seam and plug tiles

**Files:**
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/LightsTabSection.kt`, `capability/plugs/.../ui/PlugTile.kt`, `capability/plugs/.../ui/PlugsSection.kt`
- Delete: `capability/plugs/.../ui/PlugsTab.kt` and `ui/PlugRow.kt`, with `PlugsTabTest.kt`, `PlugRowTest.kt` and their screenshot PNGs. Carry their state, switch, PIN and problem-line cases over to the new tests.
- Modify: `PlugsCapability.kt`, `di/PlugsModule.kt`, `PlugActions.kt` (delete `mayManage`; KDoc on `setChoice`), `PlugsWords.kt:6-89`, `ui/PlugsDimens.kt`, `PlugsContract.kt:45`, `PlugSettings.kt:52`, `core/house/.../HouseSeams.kt:35-36,101`, `core/house/.../di/HouseModule.kt:36`, `capability/party/.../PartyWords.kt:47` (Ruling 15)
- Test: `ui/PlugTileTest.kt` (new), `ui/PlugsSectionTest.kt` (new), `PlugsCapabilityTest.kt:16-50`, `PlugsWordsTest.kt`, `PlugActionsTest.kt:291-294` (the `mayManage` cases go), `ui/PlugsScreenshotTest.kt`; the party settings screenshot (the new `PLUGS_LINE`)

**Interfaces:**
- Produces, in `:core:plugin`:
```kotlin
/** The plugs' part of the Lights tab: drawn under the rooms, watched while the tab shows. */
interface LightsTabSection {
    val shown: Flow<Boolean>
    /** "1 of 2 plugs on"; null when there are none. */
    val summary: Flow<String?>
    /** Starts watching; call the result when the tab goes. */
    fun watch(): () -> Unit
    @Composable fun Content(modifier: Modifier)
}
```
- Produces: `class PlugsSection @Inject constructor(systems: Set<@JvmSuppressWildcards PlugSystem>, actions: PlugActions, settings: PlugSettings) : LightsTabSection`.
  - `shown` is true when the plug list isn't empty.
  - `watch()` calls `system.watch()`.
  - `Content` draws `HhSectionLabel("Plugs · Home Assistant")` with the power icon, the problem line, then rows of four `PlugTile`s.
  - Bind it with `@Binds @IntoSet` in `PlugsModule`.
- Produces: `fun plugsOnSummary(plugs: List<PlugState>): String?` ("1 of 2 plugs on"; null when the list is empty), replacing `plugsOn`.
- Produces: `@Composable internal fun PlugTile(state: PlugState, needsPin: Boolean, switching: Boolean, onSwitch: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write the failing tests**
- `PlugTileTest`, moved over from `PlugRowTest`: `onAndOffShowTheirButton`, `aTapSwitches`, `unknownNoLinkUnreachableAndGoneHaveNoButton`, `thePadlockShowsWhenNeedsPin`, `switchingShowsUntilTheStateComes`, and `assertClickablesHaveRoleAndLabel`.
- `PlugsSectionTest`:
  - `fourAcross`: five plugs give two rows, and the first row's tiles are equal in width.
  - `theProblemLineShowsUnderTheLabel`.
  - `aPinPlugAsksForThePin`.
  - `summaryCounts`: "1 of 2 plugs on", or null when there are none.
  - `watchStartsAndStops`: the watch count goes 0, 1, 0.
- `PlugsCapabilityTest`: `hasTab` is always false. Build `PlugsSection` directly; it's shown once a plug is.
- `PlugsWordsTest`: `leaveLine`, `SHOW_ON_TAB` and `PLUGS_HELP` say "Lights tab". `HouseSeamsTest` (or a words test in `:core:house`) checks the same for `PLUGS_MISSED` and `PLUGS_LEAVE_THREW`.
- `PlugsScreenshotTest`: `tilesDark` and `tilesLight`. An on plug fills `accentSoft`; the others are unreachable, checking and PIN plugs.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :capability:plugs:testDebugUnitTest :core:house:testDebugUnitTest`

- [ ] **Step 3: Implement**
- **Tile sizes** (from §4.5, px ÷ 1.5): radius 19 dp; padding 15 dp at the top and 17 dp at the sides; the name at 17 sp/700; the state at 13 sp muted, or `danger` when unreachable or gone.
- **The pill:** 48 dp, at the bottom right. An on plug uses the `accentSoft` fill (the rule from `PlugRow.kt:70`).
- **The tile** is about 100 dp tall.
- **Copy:** update the words and KDocs in Ruling 15.

- [ ] **Step 4: Run the tests to see them pass.** Record and look at the plug tile shots and the Party settings shot, then run the gate.

- [ ] **Step 5: Commit:** `git commit -m "Turn plugs into small tiles in a section the Lights tab can draw, and take Edit and the mode columns away"`

---

### Task 7: Lights and plugs

**Files:**
- Modify: `core/plugin/.../Capability.kt:8-33`, `app/.../shell/ShellViewModel.kt:94-111`
- Modify: `capability/lights/.../LightsCapability.kt:39-53`, `di/LightsModule.kt` (`@Multibinds abstract fun sections(): Set<LightsTabSection>`), `ui/LightsTab.kt:45-106`, `ui/LightsGrid.kt:52-164`, `LightsWords.kt:36-40`, `ui/LightsDimens.kt:35-40`
- Test: `LightsCapabilityTest.kt`, `LightsWordsTest.kt:76-90`, `ui/LightsTabTest.kt`, `ui/LightsGridTest.kt`, `ui/RoomOrderTest.kt`, `ui/LightsTabScreenshotTest.kt`, `app/.../shell/ShellViewModelTest.kt`; `ModuleBoundariesTest.kt` (unchanged, still passing)

**Interfaces:**
- Consumes: `LightsTabSection` (Task 6).
- Produces: `Capability.railLabel` and `Capability.railIcon` (Ruling 5).
- Produces:
  - `internal fun tabTitle(hasLights: Boolean, hasPlugs: Boolean): String`: "Lights and plugs", "Lights" or "Plugs".
  - `internal fun tabSummary(rooms: List<LightRoom>, plugs: String?): String?`: "2 of 2 rooms on · 1 of 2 plugs on", or only the parts that exist. The "· Hue Bridge" ending goes.
  - `internal fun morePill(roomsBelow: Int): String`: `moreRooms(roomsBelow)` when it's over 0, otherwise "More below".

- [ ] **Step 1: Write the failing tests**
- `LightsWordsTest`:
  - `titles`: all three.
  - `summaryJoinsWhatThereIs`: both, rooms only, plugs only.
  - `morePill`: 1, 2 and 0.
- `LightsCapabilityTest`:
  - `plugsOnlyShowsTheTabAsPlugs`: no bridge, a section shown. `hasTab` is true, `railLabel` is "Plugs", `railIcon` is `Icons.POWER`, and `cards()` is empty.
  - `lightsAndPlugsKeepTheRailLabelLights`.
  - `neitherHidesTheTab`.
- `ShellViewModelTest`:
  - `aRailLabelChangeReachesTheRail`.
  - `aRailLabelThatNeverEmitsDoesntBlockTheRail`, alongside `NeverEmittingCapability`.
  - Keep the `openTab` cases (`:413-441`) passing.
- `LightsGridTest`:
  - `aRoomWithNoScenesIsShorterThanItsNeighbour`.
  - `theSectionFollowsTheRooms`: the top of `plugs_section` is below the last room's bottom.
  - `pagesScrollAsOne`: with 9 rooms and 8 plugs, scrolling to the end shows the last plug tile.
  - `thePillCountsRoomsThenSaysMoreBelow`.
  - The column rules still hold: 2 columns for 1–4 rooms, 3 for 5 or more.
- `LightsTabTest`:
  - `theSectionIsWatchedWhileTheTabShows`: the watch count is 1 even when the plugs are scrolled out of view, and 0 once the tab goes.
  - The header reads the summary.
  - Holiday lights is still in the tray (`lights_tray_holiday`).
  - With plugs only, there's no tray and no room grid.
- `LightsTabScreenshotTest`: `twoRoomsFourPlugsDark` and `twoRoomsFourPlugsLight`, `nineRoomsAndPlugsDark` and `plugsOnlyDark`. Delete the replaced PNGs.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :capability:lights:testDebugUnitTest :app:testDebugUnitTest`

- [ ] **Step 3: Implement**
- **`LightsCapability`:**
  - Inject `Set<@JvmSuppressWildcards LightsTabSection>`.
  - `hasTab = combine(bridge present, any section shown)`.
  - `cards()` is gated on the bridge alone (Ruling 6).
  - `railLabel` and `railIcon` are "Plugs" and `Icons.POWER` when there's no bridge and a section is shown. Otherwise they're "Lights" and the bulb.
- **`LightsTabHost`:** a `DisposableEffect` per section calls `section.watch()` and stops it on dispose.
- **`LightsGrid`:**
  - Make it one `LazyVerticalGrid` with `gridColumns(roomCount)` columns at every count. Rows are top-aligned with no `fillMaxHeight` and no fixed row height, so the cards wrap their content.
  - The header stays item 0 (`GridItemSpan(maxLineSpan)`). Each section is a full-span item: `section.Content(Modifier.testTag("plugs_section"))`.
  - The fade and pill show while the grid can scroll further, using `morePill(roomsBelow)`.
  - Delete `scrolls()`, `SharedRows` and `LightsDimens.fixedRow`.
- **With no bridge,** `LightsTab` draws only the header (no tray) and the sections.
- **`ShellViewModel.tabs`** combines `railLabel` and `railIcon` per capability (Ruling 5).

- [ ] **Step 4: Run the tests to see them pass**, plus `.\gradlew.bat -p build-logic :convention:test` (ModuleBoundariesTest). Record and look at the Lights tab and Home shots, then run the gate.

- [ ] **Step 5: Commit:** `git commit -m "Bring the plugs into the Lights tab as Lights and plugs, with one scroll and cards that fit what they hold"`

---

### Task 8: Emulator walk-through (controller with the user)

- [ ] Run the `simplify` skill on the branch's changes. If it changes anything, re-run the gate.
- [ ] Start the `culvery-5` AVD and take it offline before Culvery opens, after every restart (`svc wifi disable`, `svc data disable`). Put Halloween's dates back to 24–31 Oct.
- [ ] Install the debug build and walk through it with the user on the debug sample house. Claude enters the debug sample PINs.
  - **The tray:** 1, 2 and 3 buttons; a mode on, and one running.
  - **The Scene pill:** cycle, wrap, None, no scenes, an off room.
  - **Weeks:** 4, 8 and 12. Step to the end, then shrink while far on.
  - **Keep:** 2, 7 and 30 days. Shorten with Keep, with a refused PIN, as a child, and as an Adult.
  - **Clear history:** Keep, a refused PIN, a child, an Adult.
  - **Lights and plugs:** 2 rooms with plugs, 9 rooms, plugs only.
  - **TalkBack** on the new controls, in short batches.
- [ ] Collect faults and ask the user before fixing any.
- [ ] List the cuts again:
  - Settings and Modes (15b).
  - Party's speed (15c).
  - The wizard.
  - Settings › Home buttons.
  - The status tab.
  - The rail redesign.
  - The sound board.
  - Mark all seen and the latest-picture panel.
  - Editing what modes do with plugs (until 15b).

### Task 9: Tablet, final review, merge, to-do list (controller with the user)

- [ ] Run the final whole-branch review (opus), and fix what the user agrees to.
- [ ] Build a release and check it carries no DebugClock. Run `adb install -r` on the SM-T510 only; never uninstall or wipe. Scan its logs for names and placeholders. No real light, plug or speaker is switched without the user's go.
- [ ] Merge to `main` locally with `git merge --ff-only`. Don't push without asking, and check for personal data first.
- [ ] In `docs/superpowers/plans/2026-09-23-plan1-followups.md`, remove the items this plan closed and add only what it leaves open. Add any ideas the user mentioned in passing to Feature ideas. Commit.
