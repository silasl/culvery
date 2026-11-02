# Culvery Plan 15b: Settings and Modes. Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–10 are subagent tasks; Task 11 is the controller with the user on the emulator; Task 12 needs the user and the SM-T510.

**Goal:** Settings becomes eight pages with one App settings page, and a Modes page where Holiday mode, Party mode and the family's own modes are set up; a mode on Home turns on or off with one tap (and a PIN only where it needs one); Settings closes after 90 s; the review's visual items; a throwaway Taekwondo practice screen.

**Architecture:** `:capability:party` becomes the home of every stored mode. Holiday mode stays in `:capability:holiday` and reaches the Modes page through Compose seams in `:core:plugin`; plugs and lights reach it through widened `:core:house` seams, since capabilities can't depend on each other (`ModuleBoundaries`).
- **Grants:** a mode runs on a `ModeGrant`: a person's fresh PIN, or the wall's word for one no-PIN mode (decision 7). The plug store refuses a PIN plug to the wall.
- **Store:** `party_settings` is widened in place into the modes store, with a DataStore migration that moves Party's choices into the first stored mode in one atomic write. The plug and lights stores don't move: Party keeps the id `party`, and Holiday lights' automations stay in `lights_settings`.
- **One switch for every stored mode:** `StoredModesHouse` (today's `PartyHouse`, generalised) is the one `HouseModeSwitch` for every stored mode. One mode is on at a time, so one record is enough.
- **Home:** `ModeShelf` says where each shown mode sits on Home. The tray fills its half-width spots in order.
- **App settings** lives in `:app`, because Display does. It holds sub-pages, opened by a back-linked inner navigation, and deep links still reach them.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, Room 2.8.5, DataStore 1.2.1, Coroutines 1.10.2, JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1, Android `TextToSpeech` (platform).

**Spec:** `docs/superpowers/specs/2026-11-02-culvery-15b-settings-and-modes-design.md`. Read it with this plan; it is the binding authority. The source of truth for what was agreed is `culvery-notes/culvery-design-review-2026-10-30/changes.md` ("Settings" 1–3, 5, 6). Handoff: `docs/design/2026-10-redesign/handoff.md`. Earlier specs: 11 (`2026-10-26-…-hoover-heating-holiday`), 12 (`2026-10-27-…-smart-plugs`), 13 (`2026-10-29-…-party-mode`). **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Task 12 updates it).

**Builds on:** branch `plan-15b-settings-modes` at f2e841cd (the spec), in the one checkout `C:\Users\si\code\siland\culvery`. No worktrees. Never push. File and line references were read at f2e841cd. Subagents use `C:/t9` for scratch, and never point Gradle's temp folder at a long path.

| # | Task | Review |
|---|---|---|
| 1 | Mode grants and the plugs seam: `ModeGrant`, one permission rule, `HousePlugs` widened, a no-PIN run never switches a PIN plug | **opus** (access, anti-goal 1) |
| 2 | Light choices: scene, On at a percent, or Off, and the hand-change rules | sonnet |
| 3 | The modes store: `party_settings` widened, Party's choices moved, ids | **opus** (data move, anti-goal 3) |
| 4 | One tap on Home: `StoredModesHouse`, `StoredModeActions`, `ModeShelf`, the tray's spots, Holiday placed by the shelf, the sheet goes | **opus** (state, one at a time) |
| 5 | Settings › Modes: the list, Add a mode, rename and icon, the 12 icons | sonnet |
| 6 | A stored mode's page: Turn on now, the two switches, Music, Lights, Plugs, Delete; Party's old page goes | sonnet |
| 7 | Holiday mode's page: `BuiltInModePage`, Lights while away moved from Settings › Lights | sonnet |
| 8 | Settings' list, App settings and its sub-pages, Home Assistant, 90 s, Close, W26, W29, W34 | **opus** (shell, deep links, session) |
| 9 | The review's other visual items: People, Music's ring line, Display's chevron, Shopping, Calendars | sonnet |
| 10 | Taekwondo: `:capability:taekwondo` | sonnet |
| 11 | Simplify; emulator walk-through | controller + user |
| 12 | Tablet install, final review, local merge, to-do list | controller + user, **needs the SM-T510** |

**Task order and why:**
- Tasks 1–2 change the `:core:house` seams and their two implementers. Everything after them uses the new types.
- 3 (the store and the move) lands before anything reads a stored mode. 4 switches Home to it before any page can make a second mode.
- 5–7 build the pages on 3 and 4. 6 deletes Party's old page only once its replacement exists.
- 8 rebuilds the list once Modes exists. 9 is independent polish.
- 10 is self-contained and late, so deleting it later touches nothing else.
- Every task leaves `testDebugUnitTest verifyRoborazziDebug` green.

## Global Constraints

- **Units.** Measurements in the spec and the handoff are px on a 1920 × 1200 artboard: **dp = px ÷ 1.5**, and sp likewise. Never go below a 48 dp touch target (`ButtonTokens.regularHeight`).
- **Exact copy (spec §7):**
  - "App settings"
  - "The home, the people who use the wall, the screen and the lock."
  - "Modes"
  - "One tap sets the house up. Each mode can sit on Home and can need a PIN."
  - "Add a mode", "Show on Home", "Needs a PIN"
  - "Turn on now" / "Turn off now"
  - "Home has room for 4 modes." (with Taekwondo bound, "Home has room for 3 modes.")
  - "A plug in this mode needs a PIN.", "Holiday mode always needs a PIN."
  - "Changes apply the next time {mode} turns on."
  - "every other light is left alone", "every other plug is left alone"
  - "Add a light", "Add a plug", "Delete mode"
  - "Delete {mode}? Its music, lights and plugs choices go too."
  - "Turn {mode} off first."
  - "Nothing to do yet. Choose music, lights or plugs in Settings › Modes."
  - "Home Assistant", "Built in", "On Home", "Not on Home", "PIN", "No PIN", "On", "Lights, heating, hoover, plugs, door chime"
  - "Tablet lock is on" / "Tablet lock is off"
  - "Used for the clock’s time zone and the weather. Tap to change."
  - "Brightness and night", "Seasonal looks", "Scene: {scene}", "On · {n}%", "Off", "‹ Modes", "‹ App settings", "Lower music on a ring to"
  - Use curly apostrophes (’) in copy. The one exception is the Taekwondo move text (Task 10), which is carried exactly as in `culvery-notes/taekwondo-choong-moo.html`.
- **Copy this plan rules (not in §7), each marked "(plan)" where used:** Task 5's name problems and its summary pieces, Task 7's fixed Holiday lines, Task 8's summaries, Task 9's Shopping line. A reviewer checks them against the copy rules in `~/.claude/CLAUDE.md` (actions keep their name; errors say how to fix).
- **Logs.** No personal data in logs: only exception class names, as in the existing `Log.w(TAG, "… (${e::class.simpleName})")`. A mode is named in a log only as "Holiday" or "a stored mode" (§5); never a mode's, person's, room's or plug's name, and never a stored mode's id. A new log line or TAG means running `.\gradlew.bat -p build-logic :convention:test` (LogHygieneTest).
- **No deprecated APIs** without asking the user first. Stop and report any you meet. Task 10 has a known one.
- **Commits** are plain sentences with no trailers: no Co-Authored-By, no Signed-off-by.
- **Dependencies.** No new libraries, fonts or sounds. Icons come only from the full font in git history, by `tools/fonts/README.md` (Task 5). Adding an existing library or project to another module's `build.gradle.kts` is allowed. `ModuleBoundariesTest` must keep passing: no capability depends on another.
- **Devices.** Never switch a real light, plug or speaker. Hue and Sonos are read-only.
- **Tests:**
  - Run `.\gradlew.bat :<module>:testDebugUnitTest` per task.
  - Before each commit, run the gate: `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug`.
  - Record screenshots only on Windows (`recordRoborazziDebug`), and only for the screens the task changes. Look at every changed PNG before committing it.
  - Delete the reference PNGs of screenshot tests the task removes.
- **Comments:** one line by default, in the surrounding KDoc style, with spec refs like "15b §4.3".

## Review Focus

1. **Party mode on while the update installs.** The move runs with the record on and lights saved in the pre-15b six-field form. Party stays on, its tile shows ON, and turning it off still puts the music, plugs and every room back. Tested in Task 3 (the store) and Task 4 (the turn-off).
2. **A plug locked after a no-PIN mode was tapped.** The plug is set to need a PIN between the tap's check and `begin`, or between `begin` and a retried `enter`. The plug isn't switched by that run, and the line says the plugs didn't all switch. Tested in Task 1.
3. **More modes marked Show on Home than Home has room for.** For example, four shown and then Taekwondo's spot is bound, or Holiday is switched back on to Home with four stored modes shown. Home shows only the first that fit, in list order, and the page's switch for any mode not placed reads off and disabled with the room line. Tested in Task 4 (`ModeShelf`) and Task 6.
4. **Names that are nearly the same.** "  party MODE ", "Holiday Mode", 24 versus 25 characters, a name of spaces only, a name holding the store's field separator or a line break. Each is refused with its line, or trimmed and kept. Tested in Task 5.
5. **Deleting an off mode whose last turn-off threw on the plugs.** The plug store still holds a run for it. Delete puts those plugs back before the choices go, and a later mode can't inherit the run. Tested in Task 1 (`forget`) and Task 6.

---

## Rulings against the code

Where the spec doesn't fit the code at f2e841cd, or §9 leaves the builder a choice:

1. **The module stays `:capability:party`** (package `…capability.party`). §9 says "widened into a modes module". Renaming the module is churn with no effect for the family. New classes are named for modes (`StoredModesHouse`, `ModesStore`, `ModesPage`). The capability keeps `id = "party"`.
2. **One permission rule for every mode.** `HousePermissions.PARTY_MODE` becomes `STORED_MODE = "party.mode"`, labelled "Turn modes on and off" (§9). `fun modePermission(modeId: String) = if (modeId == HouseModes.HOLIDAY) HOLIDAY_MODE else STORED_MODE`. `HouseModeInfo`, its `@Multibinds`, both `@Provides` and `HouseGraphProbe.modes()` are deleted (`HouseSeams.kt:101-102`, `HouseModule.kt:36-38`). The PIN pad names the mode through `authorise(…, label = "Turn {mode} on")`, giving "Enter your PIN to turn Film night on."
3. **The wall's grant.**
   ```kotlin
   sealed interface ModeGrant {
       class Person(val by: Authorised) : ModeGrant
       /** No PIN (15b decision 7): the wall vouches for [modeId]'s own choices only. */
       class Wall(val modeId: String) : ModeGrant
   }
   ```
   `fun ModeGrant.allows(modeId: String): Boolean`: a person needs `modePermission(modeId)` in `by.granted`, and the wall's `modeId` must be the mode's own. Neither `toString` prints a person or an id.
4. **The plug store guards the wall.** A `Wall` run's snapshot leaves out every plug in `needsPin`. Its `ModeRun` carries `wall = true` (key `wall.{mode}`), and each `enter` drops any pending plug that has come to need a PIN. `leave` stays grant-free: it only puts back. This holds anti-goal 1 even when a plug is locked mid-run.
5. **The move is an in-file DataStore migration**, `PartyMove : DataMigration<Preferences>`, on the same `party_settings` file. It runs before the first read, and its write is one atomic file replace. So a restart during the move either finds the old keys (and moves again) or the new ones (§4.4's last point). `shouldMigrate` is "no `modes` key". A fresh install therefore gets Party mode too, with nothing to do. `settingsDataStore` gains `migrations: List<DataMigration<Preferences>> = emptyList()`.
6. **Nothing else moves.** The plug store's `mode.party` and `mode.holiday` entries already are the added plugs with On or Off: Ignore was never stored (`PlugPrefs.with`). Holiday lights' automations stay in `lights_settings` (`holidayAutomations`), and only their page moves (Task 7).
7. **A stored mode's id** is `"mode"` plus six random letters `a–z`, which fits `isModeId` (`^[a-z]+$`, `PlugSettings.kt:26`). It is drawn again on a clash with any stored id, `holiday`, `party` or `stored`. Party keeps `party`.
8. **One `HouseModeSwitch` for every stored mode.** It has `modeId = HouseModes.STORED = "stored"` and `isOn` true while any stored mode is on. `modeName` is a getter for the on mode's name, so Holiday's `ModeRefusal` says "Turn Film night off first." The switch interface gains `val logName: String` ("Holiday", "a stored mode"). `ModeRefusal.kt:45` and `ModePlugs`' four lines log `logName`, never `modeName` or the id. Between two stored modes, `StoredModeActions` makes the same refusal itself.
9. **Room choices replace scene choices in `:core:house`.**
   ```kotlin
   sealed interface LightChoice {
       data class Scene(val sceneId: String, val sceneName: String) : LightChoice
       /** [percent] is 10–100 in steps of 10 (15b §4.2). */
       data class On(val percent: Int) : LightChoice
       data object Off : LightChoice
   }
   data class RoomChoice(val roomId: String, val roomName: String, val choice: LightChoice)
   ```
   `SceneChoice` goes. `RoomBefore` swaps `sceneId` for `choice: LightChoice`. `HouseLights.partyOptions/partyBefore/partyOn/partyBack` become `lightOptions/modeBefore/modeOn/modeBack`. Each `toString` hides ids and names.
10. **"Changed by hand" reads the bridge only (§4.3):** Scene: the active scene isn't the mode's (today's rule). On n%: the room is off, or more than 5 points from n. Off: the room is on. `setBrightness` already turns a room on (`HueSystem.kt:151-159`).
11. **The tray's spots.** `trayRows` returns exactly `TRAY_ROWS` rows. Half-width buttons fill row 1 and then row 3, two to a row, in placement order. Full-width buttons fill row 2, then any row still empty. So with three modes: modes 1–2 / Hoover / mode 3 (decision 3). A row with no cell draws as an empty, outline-less spacer. Mode buttons have orders 10–13 by place, and Taekwondo 90.
12. **`ModeShelf` (in `:core:house`) places the modes.** It is bound by the party module and read by Holiday:
    ```kotlin
    interface ModeShelf {
        /** Where [modeId] sits among the modes on Home, 0 first; null while it isn't on Home (15b decision 3). */
        fun place(modeId: String): Flow<Int?>
    }
    ```
    The room is `4 − reservations`, where `interface TrayReservation` (`:core:plugin`) is a `@Multibinds` set that Taekwondo adds to. Shown modes past the room get no place. `HouseModule` declares `@BindsOptionalOf ModeShelf`. With none bound, Holiday shows as today.
13. **Holiday's page parts are Compose seams in `:core:plugin`.** `BuiltInModePage` is bound by holiday and drawn by Modes. `HolidayLightsSection` is bound by lights and drawn inside Holiday's part. Each consumer declares the `@Multibinds` set, as `LightsTabSection` does (15a Ruling 4).
14. **Plugs on a mode's page** go through `HousePlugs`. The party module draws them: `options()`, `added(modeId)`, `choose(modeId, plugId, on: Boolean?)` and `forget(modeId)`. `PlugActions.setChoice` and `PlugsPermissions.MANAGE` are deleted (decision 8: Settings' Admin session is the gate). Choosing a plug that needs a PIN also writes the mode's `needsPin = true` (decision 6). The tap's rule is `mode.needsPin || added.any { it.needsPin }`, so a plug locked later on the Home Assistant page counts too.
15. **A sub-page is a page's own state.** `SettingsPage` gains `val opens: Set<String> get() = emptySet()`, the sub-page ids it holds. `SettingsScreen` opens the page whose `id` is `initialPage` or whose `opens` holds it, and provides `LocalSettingsStart` (`:core:plugin`, `staticCompositionLocalOf<String?> { null }`) so that page opens that sub-page. `openSettings(SettingsPageIds.LOCATION)` from the weather header (`WeatherHeaderItem.kt:48`) therefore lands on App settings › Home location. App settings sits in `:app` (`app/.../settings/`), because Display lives there. `LocationPage`, `PeoplePage`, `KioskPage` and `DisplayPage` stop being bound `@IntoSet SettingsPage`.
16. **90 s is the session's clock.** Settings closes when the session ends (`ShellViewModel.kt:185`), so `SESSION_TIMEOUT_MS` becomes `90_000L`. `OVERLAY_IDLE_MS` and `EDIT_HOLD_MS` stay. A PIN session started outside Settings ends after 90 s too. Nothing reads it outside Settings but the next authorise, and every mode and plug permission is fresh-PIN anyway.
17. **W34's section labels.** The handoff's 18 px/700 with 1.5 px letter-spacing is 12 sp with 1 sp. `HhSectionLabel` reads `LocalSectionLabelStyle` (default `HhType.sectionLabel`, unchanged off Settings). `SettingsScreen` provides `SettingsTokens.sectionLabel` over the pane. Rows already clear 84 px (`ListRowTokens.minHeight` 69 dp > 56 dp). The new pages (App settings, Modes, a mode's page) space rows `SettingsTokens.rowGap = 7.dp`. Older pages keep their inner gaps until the walk-through says otherwise.
18. **Taekwondo's spot and staying awake.** A full-screen overlay already holds the wall awake (`holding(…, sheetShowing = overlay.isShowing, …)`, `SetupWiring.kt:89`). So the night clock and sleep stay away with no new code. `OverlayHost` gains `fun keepOpen(on: Boolean) = Unit`, which `OverlayState` maps to `closesWhenIdle`. A doorbell ring stops the run through an `AlertQuieter` whose `quiet()` stops it and returns false (it turned nothing down).
19. **The People rows two to a row keep Move up and Move down.** TalkBack reads them left to right, top to bottom, as the list's order. `ADD_PERSON` becomes "Add a person" everywhere, the wizard included, drawn as the dashed add row.
20. **The Taekwondo text is carried exactly**, straight apostrophes included ("opponent's", "Yi's"), because the user asked for the file's text exactly. Hangul for step 11 and End is `null` until the instructor fills it. A move with no Hangul is spoken from its romanised term in the English voice.

---

### Task 1: Mode grants and the plugs seam

**Files:**
- Modify: `core/house/.../HouseSeams.kt` (`HouseModeInfo:101-102` goes; `HouseModes`, `HousePermissions:152-155`, `HouseModeSwitch` (`logName`), `HousePlugs:131-143`; new `ModeGrant`, `PlugOption`, `AddedPlug`), `ModePlugs.kt`, `ModeRefusal.kt:45`, `di/HouseModule.kt:36-38`
- Modify: `capability/plugs/.../PlugsHouse.kt`, `PlugSettings.kt` (`ModeRun.wall`, key `wall.{mode}`), `PlugActions.kt:86-89` (`setChoice` goes), `PlugsPermissions.kt` (`MANAGE` goes)
- Modify: `capability/party/.../PartyHouse.kt` (begin with `ModeGrant.Person`; `logName = "a stored mode"`), `PartyPermissions.kt` (label "Turn modes on and off"), `di/PartyModule.kt` (the `@Provides` goes); `capability/holiday/.../HolidayHouse.kt` (`logName = "Holiday"`), `di/HolidayModule.kt`
- Modify: `app/src/debug/.../di/HouseGraphProbe.kt:35`
- Test: `core/house/src/test/.../HouseSeamsTest.kt`, `ModePlugsTest.kt`, `ModeRefusalTest.kt`; `capability/plugs/src/test/.../PlugsHouseTest.kt`, `PlugsHouseTwoModesTest.kt`, `PlugSettingsTest.kt`, `PlugActionsTest.kt`, `PlugsPermissionsTest.kt`, `TestPlugsHouse.kt`; `capability/party/src/test/.../PartyHouseTest.kt`, `PartyPermissionsTest.kt`; `capability/holiday/src/test/.../HolidayHouseTest.kt`; `app/src/test/.../HouseGraphTest.kt:55-60`

**Interfaces:**
- Produces: `ModeGrant`, `ModeGrant.allows` (Ruling 3), `HousePermissions.STORED_MODE`, `modePermission(modeId)` (Ruling 2), `HouseModes.STORED = "stored"`, `HouseModeSwitch.logName`.
- Produces, on `HousePlugs`:
  ```kotlin
  suspend fun begin(modeId: String, grant: ModeGrant)
  /** The plugs on the Lights tab, named, in tab order; empty with no plug system (15b §4.2). */
  suspend fun options(): List<PlugOption>
  /** [modeId]'s added plugs, in tab order. */
  fun added(modeId: String): Flow<List<AddedPlug>>
  /** Adds the plug at [on], or takes it out with null; nothing for a plug not on the Lights tab. */
  suspend fun choose(modeId: String, plugId: String, on: Boolean?)
  /** Puts back anything [modeId]'s run still holds, then drops its choices and run (15b §4.2 Delete). */
  suspend fun forget(modeId: String)
  ```
  The existing `ready`, `preview`, `enter` and `leave` are unchanged. `data class PlugOption(val id: String, val name: String, val needsPin: Boolean)` and `data class AddedPlug(val id: String, val on: Boolean, val needsPin: Boolean)`; each `toString` leaves out the id and the name.
- Changes: `class ModePlugs(plugs: HousePlugs, modeId: String, logName: String)`, with `suspend fun begin(grant: ModeGrant)`.
- Changes: `PlugsHouse internal constructor(settings, system)`. The `modes` parameter goes.

- [ ] **Step 1: Write the failing tests**
- `HouseSeamsTest`:
  - `holidayTakesItsOwnPermission`: `modePermission("holiday") == "holiday.mode"`, while `"party"` and `"modeabcdef"` give `"party.mode"`.
  - `aPersonNeedsTheModesPermission`: a person holding only `holiday.mode` doesn't allow `"party"`.
  - `theWallVouchesForOneMode`: `Wall("party").allows("party")` is true, and `.allows("modeabcdef")` is false.
- `PlugsHouseTest`:
  - `aWallRunLeavesOutAPinPlug`: `lamp` On with no PIN, `heater` On with a PIN. `begin("party", Wall("party"))`, then `enter`. Only `lamp` is switched. `TestPlugSystem.switches` is `[lamp=on]`.
  - `aPlugLockedAfterAWallBeginIsNotSwitched` (Review Focus 2): `heater` is unreachable at the first `enter`, then `setNeedsPin(heater, true)`, then `enter` again. `heater` is never switched, and the step is `Failed` with `PlugsWords.enterLine`.
  - `aPersonRunStillSwitchesAPinPlug`.
  - `aWallGrantForAnotherModeIsRefused`: `begin("party", Wall("modeabcdef"))` throws `IllegalArgumentException`.
  - `optionsAreNamedInTabOrder`: the names come from `TestPlugSystem`, the plugs not on the tab are left out, and the result is empty with no system.
  - `chooseAddsSwitchesAndRemoves`: `added` emits `[lamp on]`, then `[lamp off]`, then `[]`.
  - `chooseIgnoresAPlugNotOnTheTab`.
  - `forgetPutsBackFirst` (Review Focus 5): a run left by a `leave` whose last write threw is put back (the plug switched to `before`), then `added("party")` is empty and `runs` has no `party`.
- `PlugSettingsTest`: `aWallRunIsKept`. The key `wall.party` reads back, and a run without it reads `wall = false`.
- `ModeRefusalTest` and `ModePlugsTest`: `logsNameNoMode`. A switch with `modeName = "Film night"` and `logName = "a stored mode"` is refused. No `ShadowLog` line contains "Film night" or the id.
- Delete `PlugActionsTest`'s `setChoice` cases and `PlugsPermissionsTest`'s `MANAGE` case. `HouseGraphTest.partyIsInTheHouse` loses its `modes()` assertions; the switch ids stay `holiday` and `party` until Task 4.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :core:house:testDebugUnitTest :capability:plugs:testDebugUnitTest`. Expected: compile errors on `ModeGrant`, `options` and `added`.

- [ ] **Step 3: Implement**
- `PlugsHouse.begin`: `require(grant.allows(modeId))`. The wall's snapshot is `listed(p, modeId).filterNot { it.first in p.needsPin }`, with `wall = true`. `enter` starts by dropping, under the same `settings.update`, any pending plug of a `wall` run that is now in `needsPin`, logged as `"Plugs: a no-PIN run left out a plug that needs a PIN"`. A dropped plug counts as missed in the step's line.
- `options()` reads each listed plug's name with `read(id)`, as `preview` does, at once.
- `forget` runs `leaveLocked(modeId)` under the mutex, then removes `modes[modeId]`.
- PartyHouse and HolidayHouse pass `ModeGrant.Person(by)` to `ModePlugs.begin`.

- [ ] **Step 4: Run the tests to see them pass**, plus `.\gradlew.bat -p build-logic :convention:test` (the changed log lines) and `.\gradlew.bat :app:testDebugUnitTest --tests "*HouseGraphTest*"`. Then run the gate.

- [ ] **Step 5: Commit:** `git commit -m "Run modes on a person's grant or the wall's, and let the plug store refuse the wall any plug that needs a PIN"`

---

### Task 2: Light choices

**Files:**
- Modify: `core/house/.../HouseSeams.kt` (`SceneChoice:226` goes; `LightChoice`, `RoomChoice`; `RoomBefore:231-241`; `HouseLights:62-78`)
- Rename: `capability/lights/.../LightsParty.kt` → `LightsModes.kt` (class `LightsModes`); modify `LightsHouseSeam.kt:33-40`
- Modify: `capability/party/.../PartySettings.kt` (`scenes: List<RoomChoice>`; the `LIGHTS_BEFORE` record form), `PartyHouse.kt:189-203,257-262`, `PartyWords.kt`, `ui/PartySettingsPage.kt:232-260`
- Test: `capability/lights/src/test/.../LightsPartyTest.kt` → `LightsModesTest.kt`, `LightsHouseSeamTest.kt`, `TestLights.kt`; `capability/party/src/test/.../PartySettingsTest.kt`, `PartyHouseTest.kt`, `TestParty.kt`, `ui/PartySettingsPageTest.kt`

**Interfaces:**
- Produces: `LightChoice`, `RoomChoice` (Ruling 9), and `RoomBefore(roomId, name, choice: LightChoice, on, brightness, activeSceneId)`.
- Produces, on `HouseLights`: `suspend fun lightOptions(): LightOptions`, `suspend fun modeBefore(choices: List<RoomChoice>): List<RoomBefore>`, `suspend fun modeOn(rooms: List<RoomBefore>): List<String>`, `suspend fun modeBack(rooms: List<RoomBefore>): List<String>`. The KDocs are as today's, with "the mode" for "Party".
- Produces, in `:capability:lights`: `internal fun LightChoice.stillAsLeft(room: LightRoom): Boolean` (Ruling 10).
- Produces: the record's room form, `roomId␟name␟kind␟sceneId␟percent␟on␟brightness␟active`, where kind is `scene`, `on` or `off`. A six-field entry written before 15b reads as `Scene(sceneId, "")`.

- [ ] **Step 1: Write the failing tests**
- `LightsModesTest`:
  - `onAtAPercentSetsTheBrightness`: `modeOn` with `On(60)` gives `setBrightness(Lounge, 60)`.
  - `offTurnsTheRoomOff`.
  - `aSceneStillRecalls` (today's cases, moved).
  - `aGoneSceneIsLeftOut`, and `aGoneRoomIsLeftOutWhateverItsChoice`.
  - `onRoomsGoBackWithinFivePoints`: chosen 60, the room now at 65, goes back. At 66 it stays, and so does an off room.
  - `anOffRoomTurnedOnByHandStays`, and `anOffRoomStillOffGoesBack`.
- `PartySettingsTest`:
  - `aPreFifteenBRecordReadsAsScenes`: write the six-field `lights.before` entry raw, then `record.first().lights!!.single().choice` is a `Scene` with that scene id.
  - `aRoomChoiceRoundTrips`, for each kind.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :capability:lights:testDebugUnitTest :capability:party:testDebugUnitTest`

- [ ] **Step 3: Implement**
- `LightsModes.before` keeps a `Scene` only when the room has that scene, and an `On` or `Off` whenever the room exists.
- `on`: a `Scene` recalls, `On(n)` calls `setBrightness(room, n)`, and `Off` calls `setOn(room, false)`.
- `back` puts back only rooms where `choice.stillAsLeft(room)`, by today's `putBack`.
- Party's page still offers scenes only. It saves `RoomChoice(id, name, Scene(sceneId, sceneName))`.

- [ ] **Step 4: Run the tests to see them pass**, then the gate.

- [ ] **Step 5: Commit:** `git commit -m "Let a mode set a room to a scene, on at a brightness or off, and put back only rooms still as it left them"`

---

### Task 3: The modes store

**Files:**
- Create: `capability/party/.../ModesStore.kt` (replaces `PartySettings.kt`), `StoredMode.kt`, `PartyMove.kt`, `ModeIds.kt`
- Modify: `core/plugin/.../SettingsStore.kt:17-26` (`migrations`)
- Modify: `PartyHouse.kt` (reads Party's mode from `ModesStore`; `PartyRecord` becomes `ModeRecord`), `PartyCapability.kt`, `ui/PartySettingsPage.kt` (writes through `ModesStore.update` on the `party` mode)
- Test: `capability/party/src/test/.../ModesStoreTest.kt` (replaces `PartySettingsTest.kt`), `ModeIdsTest.kt`, `PartyHouseTest.kt`, `TestParty.kt`; `app/src/test/.../HouseGraphTest.kt:64-66`

**Interfaces:**
- Produces:
  ```kotlin
  /** A mode the family set up (15b §4.1). [speakers] null is every speaker. [toString] leaves out all but [id]'s presence. */
  data class StoredMode(
      val id: String,
      val name: String,
      val icon: String,
      val showOnHome: Boolean,
      val needsPin: Boolean,
      val music: MusicChoice? = null,
      val speakers: List<HouseRoom>? = null,
      val lights: List<RoomChoice> = emptyList(),
  )
  data class ModesPrefs(val holidayOnHome: Boolean = true, val modes: List<StoredMode> = emptyList()) {
      fun mode(id: String): StoredMode?
  }
  /** What the mode that is on changed (13 §4.3), one at a time; [modeId] null with none on. */
  data class ModeRecord(
      val modeId: String? = null,
      val on: Boolean = false,
      val since: Long? = null,
      val musicBefore: List<GroupBefore>? = null,
      val made: PartyGroup? = null,
      val lights: List<RoomBefore>? = null,
  )
  @Singleton class ModesStore(store: DataStore<Preferences>, scope: CoroutineScope) {
      val prefs: Flow<ModesPrefs>
      /** Null until the file is first read. */
      val record: StateFlow<ModeRecord?>
      suspend fun update(change: (ModesPrefs) -> ModesPrefs)
      suspend fun save(record: ModeRecord)
      suspend fun clearRecord()
  }
  ```
  The `@Inject` constructor opens `party_settings` with `migrations = listOf(PartyMove)`.
- Produces: `fun newModeId(taken: Set<String>, random: Random = Random.Default): String` (Ruling 7), and `const val PARTY_ICON = Icons.CELEBRATION`.
- Keys:
  - `modes`: the ids in the order they were added, comma-joined.
  - Per mode: `mode.{id}.name`, `.icon`, `.home`, `.pin`, `.pick`, `.pick_name`, `.rooms`, and `.lights` (a string set of `roomId␟roomName␟kind␟sceneId␟sceneName␟percent`).
  - `holiday.home`.
  - The record's keys as today, plus `record.mode`.

- [ ] **Step 1: Write the failing tests**
- `ModesStoreTest`. Each writes the old keys raw into a test file, then opens a `ModesStore` over it with `PartyMove`:
  - `aFreshFileHasPartyModeWithNothingToDo`: the modes are exactly `[party]`, with name "Party mode", icon `celebration`, `showOnHome` and `needsPin` both true, and no music or lights. `holidayOnHome` is true.
  - `theMoveKeepsPartysChoices`: the pick, the pick's name, two speaker rooms and two scenes come through as `RoomChoice(…, Scene(…))`. The old keys `music.pick`, `music.pick_name`, `music.rooms` and `scenes` are gone.
  - `theMoveKeepsAnOnRecord` (Review Focus 1): `on`, `since`, `music.made`, `music.before` and a six-field `lights.before` give `ModeRecord(modeId = "party", on = true, …)` with the lights read as scenes.
  - `anOffRecordMovesWithNoMode`.
  - `theMoveRunsOnce`: after `update { it.copy(modes = emptyList()) }` (Party deleted), a store reopened over the file still has no modes.
  - `everyFieldRoundTrips`, including `On(40)` and `Off`.
  - `aCorruptModeEntryIsSkipped`: a mode id listed with no name is left out, not thrown.
- `ModeIdsTest`:
  - `idsFitThePlugStore`: 1,000 draws all match `^[a-z]+$` and start "mode".
  - `aClashDrawsAgain`: a `Random` that repeats.
  - `neverAReservedId`.
- `PartyHouseTest`: every existing case passes against `ModesStore` with the `party` mode, and `aMovedOnRecordTurnsOffAndPutsBack` (Review Focus 1, the house half) passes too.
- `HouseGraphTest.theAppStartsPartysSchedulerAndItLoadsTheRecord` still passes.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :capability:party:testDebugUnitTest`

- [ ] **Step 3: Implement**
- `PartyMove.migrate`: in one `toMutablePreferences()`:
  1. Read the old choice keys and build `StoredMode("party", "Party mode", PARTY_ICON, showOnHome = true, needsPin = true, …)`.
  2. Remove the old choice keys.
  3. Write `modes = "party"` and its keys.
  4. If `on` is true, write `record.mode = "party"`.

  `cleanUp` does nothing.
- `ModesStore.update` writes the whole mode list over its keys: remove every `mode.*` and `modes` key, then write. The `holiday.home` key and the record's keys stay.
- `PartyHouse` takes `prefs.mode(HouseModes.PARTY)` in place of `PartyPrefs`, and `ModeRecord` in place of `PartyRecord`. The record always carries `modeId = "party"` while on.

- [ ] **Step 4: Run the tests to see them pass**, then the gate.

- [ ] **Step 5: Commit:** `git commit -m "Widen Party mode's settings file into the modes store and move Party's choices into it as the first stored mode"`

---

### Task 4: One tap on Home

**Files:**
- Rename: `capability/party/.../PartyHouse.kt` → `StoredModesHouse.kt`, `PartyActions.kt` → `StoredModeActions.kt`, `ui/PartyTile.kt` → `ui/StoredModeTile.kt`
- Delete: `ui/PartySheet.kt`, with `PartySheetTest.kt` and its PNGs. Also delete `PartyPlan`, `willLines` and `backLines` from `PartyWords.kt`, which becomes `ModesWords.kt`.
- Create: `capability/party/.../ModesShelf.kt`; `core/house/.../HouseSeams.kt` (`ModeShelf`); `core/plugin/.../TrayButton.kt` (`TrayReservation`)
- Modify: `PartyCapability.kt` → `ModesCapability.kt`, `di/PartyModule.kt` (bind `ModesShelf` as `ModeShelf`, `@Multibinds` `TrayReservation`, `StoredModesHouse` as the switch), `core/house/.../di/HouseModule.kt` (`@BindsOptionalOf ModeShelf`)
- Modify: `capability/holiday/.../HolidayCapability.kt:33` (placed by `ModeShelf`)
- Modify: `app/.../shell/ui/HomeTray.kt` (`trayRows`, Ruling 11), `core/ui/.../PageHeader.kt` (`HhTrayGrid`: an empty row)
- Test:
  - `capability/party/src/test/.../StoredModesHouseTest.kt` (from `PartyHouseTest`), `StoredModeActionsTest.kt` (from `PartyActionsTest`), `ModesShelfTest.kt`, `ModesCapabilityTest.kt`
  - `ui/StoredModeTileTest.kt`, `ui/PartyScreenshotTest.kt` → `ui/StoredModeScreenshotTest.kt`
  - `capability/holiday/src/test/.../HolidayCapabilityTest.kt`, `HolidayActionsTest.kt`
  - `app/src/test/.../shell/ui/TrayRowsTest.kt`, `HomeGridTest.kt`, `ShellScreenshotTest.kt`, `HouseGraphTest.kt:59`

**Interfaces:**
- Consumes: `ModeGrant` and `HousePlugs.added` (Task 1), `RoomChoice` (Task 2), `ModesStore` (Task 3).
- Produces, in `StoredModesHouse : HouseModeSwitch`:
  - `modeId = HouseModes.STORED` and `logName = "a stored mode"`.
  - `modeName`: the on mode's name, else "a mode".
  - `val onMode: StateFlow<String?>`, the on mode's id. `isOn` is `onMode != null`, still null until read.
  - `val running: StateFlow<ModeRunning?>`, with `data class ModeRunning(val modeId: String, val change: PartyRun)`.
  - `fun line(modeId: String): Flow<String?>`.
  - `suspend fun needsPin(modeId: String): Boolean`: the mode's flag, or any added plug that needs a PIN (Ruling 14).
  - `suspend fun turnOn(modeId: String, grant: ModeGrant)` and `suspend fun turnOff(grant: ModeGrant)`. Each `require`s that the grant allows the mode, and that a `Wall` grant is for a mode that doesn't `needsPin`.
- Produces, in `StoredModeActions`:
  - `suspend fun tap(modeId: String)`, the tile's and the page's one action.
  - `fun refusal(modeId: String): Flow<String?>`.
- Produces: `class ModesShelf @Inject constructor(store: ModesStore, reservations: Set<@JvmSuppressWildcards TrayReservation>) : ModeShelf`, with `val room: Flow<Int>` and `val shown: Flow<List<String>>` (Holiday first, if on Home, then stored, capped at `room`).
- Produces: `interface TrayReservation { val id: String }` in `:core:plugin`.
- Produces: `internal fun trayRows(buttons: List<TrayButton>): List<List<TrayCell>>`, which always returns `TRAY_ROWS` rows (Ruling 11).
- Produces: the tile tags `mode_tile_{id}`, `mode_tile_{id}_on` and `mode_tile_{id}_state`, and the tray ids `mode.{id}`. Party's tags change to `mode_tile_party`.
- Produces: `ModesWords.NOTHING_TO_DO` (§7), `fun turnLabel(name: String, on: Boolean) = if (on) "Turn $name on" else "Turn $name off"`, and `fun tileSpoken(name: String, on: Boolean)`.

- [ ] **Step 1: Write the failing tests**
- `StoredModesHouseTest`: every `PartyHouseTest` case, re-keyed to `turnOn("party", Person(adult))`. Then:
  - `aSecondModeDoesntTurnOnOverTheFirst`: `turnOn("modeb")` while `party` is on leaves `onMode == "party"`.
  - `theWallIsRefusedForAPinMode`: it throws, and so does a no-PIN mode with a locked plug added.
  - `nothingToDoTurnsOnWithTheLine`: `onMode` is the mode, and `line` is `NOTHING_TO_DO`.
  - `percentAndOffRoomsRunAndGoBack`.
  - `anEditWhileOnChangesOnlyTheNextRun`: on with Lounge, add Kitchen, off. Only Lounge is put back, and Kitchen is never touched.
  - `aRenameWhileOnShowsInTheRefusal`.
  - `logsNeverNameAMode`: a mode named "Film night" through every step and failure. No `ShadowLog` line holds the name or the id, and a no-PIN turn-on logs "A stored mode turned on with no PIN".
- `StoredModeActionsTest`, with `TestAccess`:
  - `aNoPinModeTurnsOnForAChild`: `TestAccess` is never asked.
  - `aPinModeAsksAFreshPinNamingTheMode`: asked for `party.mode` with label "Turn Film night on". An Adult turns it on, and a Child (refused) leaves it off.
  - `aLockedPlugMakesTheTapAskForAPin`: the mode's flag is off, and one added plug needs a PIN.
  - `aPlugLockedMidRunMakesTurnOffAsk`.
  - `holidayOnRefusesBeforeThePin`: no PIN is asked, and `refusal("party")` emits "Turn Holiday mode off first.".
  - `anotherStoredModeRefusesBeforeThePin`: "Turn Party mode off first." on the tapped mode's tile only.
  - `turningOffNeverRefuses`, and `aTapWhileRunningDoesNothing`.
  - `theRunOutlivesTheTile`: cancel the caller after the PIN, and the turn-on still completes.
- `ModesShelfTest`:
  - `holidayFirstThenStoredInOrder`, and `hiddenModesTakeNoPlace`.
  - `fourAtMost`: five shown give places 0–3, and the fifth is null.
  - `aReservationLeavesThree` (Review Focus 3): four stored shown plus one `TrayReservation` place the first three only.
  - `holidayBackOnHomePushesTheLastOff` (Review Focus 3).
- `HolidayCapabilityTest`: `offHomeNoButton`, and `placedFirstWhenShown` (order 10).
- `TrayRowsTest` (replacing the 15a cases):
  - `threeRowsAlways`.
  - `halvesFillRowOneThenRowThree`: three halves and the Hoover give `[[h1, h2], [hoover], [h3]]`.
  - `withNoHooverTheMiddleRowIsEmpty`.
  - `aLoneHalfStaysInRowOne`.
- `HomeGridTest`: `fourModesAndTheHoover`. The four tiles are equal halves, and the hoover's row is full width.
- `StoredModeTileTest` (from `PartyTileTest`): `aTapTurnsOnWithNoSheet` (no `party_sheet` exists), `onShowsTheOnPill`, `runningShowsTurningOn`, and `theLineShows`.
- `HouseGraphTest`: the switch ids are `holiday` and `stored`.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :capability:party:testDebugUnitTest :capability:holiday:testDebugUnitTest :app:testDebugUnitTest`

- [ ] **Step 3: Implement**
- **`StoredModesHouse`** is `PartyHouse` with the mode read at `turnOn` from `store.prefs.first().mode(modeId)`, and a `ModePlugs(plugs, modeId, logName)` for that mode.
  - Its lines are kept per mode id: the on mode's, and the last turned-off mode's off line for `OFF_LINE_MS`.
  - The first `tick` that finds the record off calls `leaveQuietly()` for every stored id.
  - The log lines say "A stored mode's {STEP} step …".
  - A mode with nothing to do: nothing runs, and its line is `NOTHING_TO_DO`.
- **`StoredModeActions.tap`:**
  1. Return if `running.value != null`.
  2. If turning on, refuse when Holiday is on (`ModeRefusal`) or another stored mode is on.
  3. If `needsPin(modeId)`, run `access.authorise(STORED_MODE, label = turnLabel(name, !on)) ?: return` and wrap it as `Person`. Otherwise use `Wall(modeId)`.
  4. Run `withContext(NonCancellable) { house.turnOn/turnOff }`.

  The tile shows `TURNING_ON` from the moment `running` is set.
- **`ModesCapability.trayButtons()`:** `combine(store.prefs, shelf.shown)` gives one `TrayButton("mode.$id", 10 + place, TrayWidth.HALF) { StoredModeTileHost(id, …) }` per stored mode with a place.
- **`HolidayCapability`** takes `Optional<ModeShelf>` and maps `place(HOLIDAY)` to its button, at order `10 + place`. With no shelf, it shows at order 10 as today.

- [ ] **Step 4: Run the tests to see them pass**, plus the build-logic check.

- [ ] **Step 5: Record and look at the screenshots:** the tile shots (off, on, turning on, nothing to do), `home_tray_*` with one, two and four modes, and the Home shots. Delete the sheet PNGs. Then run the gate.

- [ ] **Step 6: Commit:** `git commit -m "Turn stored modes on and off from Home with one tap and a PIN only where it is needed, placing up to four modes in the tray"`

---

### Task 5: Settings › Modes

**Files:**
- Modify: `core/ui/.../Icons.kt`, `tools/fonts/icons.txt`, `core/ui/src/main/res/font/material_symbols_rounded.ttf` (re-subset). Add `MOVIE`, `SPORTS_ESPORTS`, `RESTAURANT`, `MENU_BOOK`, `SELF_IMPROVEMENT`, `FITNESS_CENTER`, `CAKE`, `PETS`, `PARK` (the grid) and `SPORTS_MARTIAL_ARTS` (Task 10's). All ten are in 608ce00's full font.
- Create: `core/ui/.../Rows.kt` (`HhAddRow`), `core/ui/.../Components.kt` (`HhBackLink`); `capability/party/.../ui/ModesPage.kt`, `ui/AddModeSheet.kt`, `ui/ModeHeader.kt`, `ModeIcons.kt`
- Modify: `ModesWords.kt`, `ModesCapability.kt` (`settingsPages()` adds `ModesPage`; Party's old page stays until Task 6)
- Test: `core/ui/src/test/.../IconFontTest.kt` (unchanged, must pass), `RowsTest.kt`; `capability/party/src/test/.../ModesWordsTest.kt`, `ui/ModesPageTest.kt`, `ui/AddModeSheetTest.kt`, `ui/ModesScreenshotTest.kt`

**Interfaces:**
- Consumes: `ModesStore`, `newModeId` (Task 3), and `StoredModesHouse.onMode`, `ModesShelf.room` (Task 4).
- Produces:
  - `data class ModeIcon(val name: String, val spoken: String)` and `object ModeIcons { val GRID: List<ModeIcon> }`: celebration "Party", movie "Film", sports_esports "Games", restaurant "Dinner", menu_book "Reading", bedtime "Bedtime", self_improvement "Calm", fitness_center "Exercise", cake "Birthday", pets "Pets", park "Outdoors", music_note "Music" (spoken names are plan copy), in that order.
  - `@Composable fun HhAddRow(label: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier)`: a dashed 2 dp `border` outline, the add icon, at least `ListRowTokens.minHeight`.
  - `@Composable fun HhBackLink(label: String, tag: String, onBack: () -> Unit)`: "‹ {label}" with `CHEVRON_LEFT`, 48 dp tall.
- Produces: `class ModesPage(…) : SettingsPage`, with `id = "modes"`, `title = "Modes"` and `order = 475`. It holds `var open: String?` (`rememberSaveable`), the mode whose page shows; null shows the list.
- Produces, in `ModesWords` (the summary pieces are §7 copy; the joins and "Nothing chosen yet" are this plan's):
  - `fun summary(mode: StoredMode, on: Boolean, plugs: Int): String`, and `fun holidaySummary(on: Boolean, onHome: Boolean): String`.
  - Pieces are joined " · ": "On" (when on), then "Built in" (Holiday only), "On Home" or "Not on Home", "PIN" or "No PIN".
  - Then what the mode does: "{pick} on {n} speakers" or "{pick} on every speaker", "1 room" or "{n} rooms", "1 plug" or "{n} plugs", each only if chosen; "Nothing chosen yet" (plan) when none is. Holiday's is "Lights, heating, hoover, plugs, door chime".
  - `fun nameProblem(typed: String, taken: List<String>): String?` (plan copy). It checks the trimmed name:
    - empty → "Type a name for the mode."
    - over 24 characters → "Keep the name to 24 characters or fewer."
    - equal ignoring case to a taken name, "Holiday mode" included → "There’s already a mode called {name}."
    - holding a control character → "Use letters, numbers and spaces in the name."

- [ ] **Step 1: Write the failing tests**
- `ModesWordsTest`:
  - `holidaysSummary`: "Built in · On Home · PIN · Lights, heating, hoover, plugs, door chime".
  - `anOnModesSummary`: "On · On Home · PIN · Sunday morning on 3 speakers · 2 rooms · 1 plug".
  - `everySpeaker`, `oneRoomOnePlug`, and `nothingChosen`.
  - `nameProblems` (Review Focus 4): "   " is refused, and "  party MODE " against `[Party mode]` is a duplicate. "Holiday Mode" is a duplicate. 24 characters pass and 25 are refused. "Film\u001Fnight" and "Film\nnight" are refused. " Film night " passes, saved as "Film night".
- `ModesPageTest`:
  - `rowsInOrder`: Holiday, Party mode, then Film night, then the dashed `modes_add`.
  - `anOnModeSaysOnFirst`.
  - `addAModeAsksANameThenAnIcon`: type "Film night", then Next, then pick `movie`, then "Add a mode". The store gains one mode with that name, icon `movie`, and both switches on. The page shows `mode_header` with "Film night".
  - `aNewModeStartsOffHomeWhenHomeIsFull`.
  - `aRefusedNameKeepsTheSheet`: the problem line shows, and Next is disabled.
  - `renameAndIconSave`.
  - `holidaysHeaderDoesntOpenAnything`.
  - `backReturnsToTheList`.
- `AddModeSheetTest`: `iconGridIsTwelveRadios`: one is selected, and each is spoken by its `ModeIcon.spoken`.
- `RowsTest`: `addRowIsAButtonWithItsLabel`.
- `ModesScreenshotTest`: `listDark`, `listLight`, `addNameDark` and `addIconDark`.

- [ ] **Step 2: Add the icons.** Follow `tools/fonts/README.md` steps 2–5, with the source `git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > C:/t9/msr-full.ttf` and `python tools/fonts/subset.py --source C:/t9/msr-full.ttf`. If fontTools is missing, or `subset.py` names a missing ligature, stop and ask. Nothing is downloaded. Run `.\gradlew.bat :core:ui:testDebugUnitTest --tests "*IconFontTest*"`. Expected: PASS.

- [ ] **Step 3: Run the new tests to see them fail:** `.\gradlew.bat :capability:party:testDebugUnitTest :core:ui:testDebugUnitTest`

- [ ] **Step 4: Implement.** The Add sheet is an `HhSheet` with two steps. The name step uses `HhTextField` with `maxLength` 24 and the keyboard's Next action. The icon step is a 4 × 3 grid of 72 dp tiles in a `selectableGroup`. Saving runs through the page's `saving(toaster)` writer, which shows `COULD_NOT_SAVE` when a write fails. The rename sheet ("Save name") and the icon sheet ("Save icon") reuse the two steps. Holiday's header has no tap.

- [ ] **Step 5: Run the tests to see them pass.** Then record the Modes shots, look at them, and run the gate.

- [ ] **Step 6: Commit:** `git commit -m "Add Settings › Modes with its list, Add a mode, renaming and a grid of twelve icons"`

---

### Task 6: A stored mode's page

**Files:**
- Create: `capability/party/.../ui/StoredModePage.kt`, `ui/ModeMusicSection.kt` (moved from `PartySettingsPage.kt:97-130,172-216`), `ui/ModeLightsSection.kt`, `ui/ModePlugsSection.kt`, `ui/LightChoiceSheet.kt`
- Delete: `ui/PartySettingsPage.kt` and `PartyPage`, with `PartySettingsPageTest.kt`, `PartySettingsScreenshotTest.kt` and their PNGs. Move their music cases over.
- Modify: `ModesCapability.kt` (`settingsPages()` is `ModesPage` alone), `ui/ModesPage.kt`, `ModesWords.kt`
- Test: `capability/party/src/test/.../ui/StoredModePageTest.kt`, `ui/ModeMusicSectionTest.kt`, `ui/LightChoiceSheetTest.kt`, `ui/StoredModeScreenshotTest.kt`

**Interfaces:**
- Consumes: `StoredModeActions.tap` and `refusal`, `StoredModesHouse.onMode` and `needsPin`, and `ModesShelf.room` and `place` (Task 4); `HousePlugs.options`, `added`, `choose` and `forget` (Task 1); `HouseLights.lightOptions` (Task 2); `HhAddRow` and `HhBackLink` (Task 5).
- Produces: `@Composable internal fun ModePlugsSection(modeId: String, plugs: HousePlugs, onLockedAdded: () -> Unit)`, which Task 7 reuses for Holiday.
- Produces: `internal fun lightChip(choice: LightChoice): String`, giving "Scene: {scene}", "On · {n}%" or "Off".
- Produces: `internal val PERCENTS = (10..100 step 10).toList()`.

- [ ] **Step 1: Write the failing tests** (`StoredModePageTest`, with fakes for the actions, the plugs and the lights):
- **Turning on and off:**
  - `turnOnNowTaps`: `mode_turn` reads "Turn on now", and a click calls `tap("party")`. When on, it reads "Turn off now".
  - `oneAtATimeFromThePage`: with Holiday on, the refusal line "Turn Holiday mode off first." shows under the button.
  - `changesApplyLineWhileOn`: "Changes apply the next time Party mode turns on."
- **Show on Home:**
  - `showOnHomeWhenFull` (Review Focus 3): with four placed and this one not, `mode_home` is off and disabled, with "Home has room for 4 modes.". With a reservation it says "Home has room for 3 modes.".
  - `aShownModePastTheRoomReadsOff` (Review Focus 3).
- **Needs a PIN:**
  - `aLockedPlugHoldsThePin`: adding `heater` (needs a PIN) saves `needsPin = true`. `mode_pin` is on and disabled, with "A plug in this mode needs a PIN.".
  - `removingTheLastLockedPlugFreesTheSwitch`: it is still on and now enabled.
- **Lights:**
  - `lightsLabelSaysTheRestAreLeftAlone`.
  - `addALightListsOnlyRoomsNotAdded`.
  - `aRoomTakesASceneOnOrOff`: the chips read "Scene: Relax", "On · 60%" and "Off".
  - `removeALight`.
- **Plugs:**
  - `addAPlugListsTabPlugsNotAdded`.
  - `plugChipSwitchesOnAndOff`.
  - `aPinPlugShowsThePadlock`.
- **Music:** `noMusicLeavesTheSpeakersAlone` (the music cases, moved).
- **Delete:**
  - `deleteConfirmsThenGoes` (Review Focus 5): `mode_delete`, then `mode_delete_confirm` with "Delete Film night? Its music, lights and plugs choices go too.", then confirm. `plugs.forget("modeabcdef")` runs before the mode leaves the store, and the page returns to the list.
  - `deleteIsRefusedWhileOn`: "Turn Film night off first.", and nothing is deleted.
  - `keepChangesNothing`.
- `LightChoiceSheetTest`:
  - `percentStepsByTen`: − at 10 is disabled, + at 100 is disabled.
  - `scenesAreRadios`, and `offIsARadio`.
- `StoredModeScreenshotTest`: `partyPageDark`, `partyPageLight`, `lightChoiceSheetDark` and `deleteConfirmDark`.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :capability:party:testDebugUnitTest`

- [ ] **Step 3: Implement**
- The page's order:
  1. `HhBackLink("Modes")`, then `ModeHeader`.
  2. The Turn button (`HhButton`, `ButtonSurface.Bg`) and its refusal line.
  3. A `Row` of the two `HhSwitchRow`s at `weight(1f)` each.
  4. MUSIC, LIGHTS and PLUGS.
  5. Delete mode (`ButtonTone.Danger`, quiet), with an inline `HhConfirmCard` as `ShoppingSettingsPage.kt:195-212` draws one.
- Section labels: "Lights · every other light is left alone" and "Plugs · every other plug is left alone".
- Each change saves through `store.update` at once. Choosing a plug that needs a PIN updates the mode's `needsPin` in the same save step.

- [ ] **Step 4: Run the tests to see them pass.** Then record the page shots, look at them, and run the gate.

- [ ] **Step 5: Commit:** `git commit -m "Give each stored mode its page with music, lights by room, plugs and Delete, in place of Settings › Party mode"`

---

### Task 7: Holiday mode's page

**Files:**
- Create: `core/plugin/.../ModePages.kt` (`BuiltInModePage`, `HolidayLightsSection`); `capability/holiday/.../ui/HolidayModePage.kt`; `capability/lights/.../ui/LightsHolidaySection.kt` (holding `LightsSettingsPage.kt:174-194`'s `HolidayChoice` and `readBridge:342-348`)
- Modify:
  - `capability/holiday/.../di/HolidayModule.kt` (bind `BuiltInModePage`; `@Multibinds` `HolidayLightsSection`) and `HolidayWords.kt`
  - `capability/lights/.../di/LightsModule.kt` (bind `HolidayLightsSection`) and `ui/LightsSettingsPage.kt` (the Holiday section goes)
  - `capability/party/.../ui/ModesPage.kt` (Holiday's page), `di/PartyModule.kt` (`@Multibinds` `BuiltInModePage`)
- Test: `capability/holiday/src/test/.../ui/HolidayModePageTest.kt`; `capability/lights/src/test/.../ui/LightsHolidaySectionTest.kt`, `ui/LightsSettingsPageTest.kt`, `ui/LightsSettingsScreenshotTest.kt`; `capability/party/src/test/.../ui/HolidayPageTest.kt`

**Interfaces:**
- Produces, in `:core:plugin`:
  ```kotlin
  /** A built-in mode's own parts of its page in Settings › Modes (15b §4.2): Holiday mode's. */
  interface BuiltInModePage {
      val modeId: String
      /** "Turn on now" or "Turn off now", running the mode's own flow (Holiday's sheet asks the day you're back). */
      @Composable fun TurnButton()
      /** What sits between the switches and Plugs: Holiday's Lights while away and its fixed lines. */
      @Composable fun Sections()
  }
  /** Holiday lights' choice of presence automations, drawn on Holiday mode's page (15b decision 5). */
  interface HolidayLightsSection { @Composable fun Content(modifier: Modifier) }
  ```
- Produces: `HolidayWords.FIXED_LINES` (plan copy):
  - "Put the heating on Eco, and take it off Eco when you’re back."
  - "Run the hoover the day before you’re back."
  - `HolidayWords.DOOR`
  - `LIGHTS_WHILE_AWAY = "Lights while away"`

- [ ] **Step 1: Write the failing tests**
- `HolidayModePageTest`:
  - `turnOnNowAsksThePinThenTheSheet`: `holiday_sheet` shows after an Adult PIN.
  - `turnOffNowAsksThePinAndTurnsOff`: there is no sheet.
  - `aChildIsRefused`.
  - `fixedLinesShow`.
  - `noLightsSectionWithNoHue`.
- `LightsHolidaySectionTest`: the `lights_automation_*` and `lights_holiday_note` cases, moved from `LightsSettingsPageTest`. `LightsSettingsPageTest`: `noHolidaySection`.
- `HolidayPageTest` (Modes, with a fake `BuiltInModePage`):
  - `pinHeldOn`: `mode_pin` is on and disabled, with "Holiday mode always needs a PIN.".
  - `noRenameNoMusicNoDelete`.
  - `showOnHomeOffTakesHolidayOffHome`: `holidayOnHome` is saved false, and `shelf.place("holiday")` is null.
  - `plugsForHoliday`: `ModePlugsSection` works on mode id `holiday`.
- `ModuleBoundariesTest` (unchanged) passes.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :capability:holiday:testDebugUnitTest :capability:lights:testDebugUnitTest :capability:party:testDebugUnitTest`

- [ ] **Step 3: Implement**
- `HolidayModePage.TurnButton` reuses `HolidayActions.toggle`, opening `HolidaySheetHost` through `LocalOverlayHost`, as the tile does.
- `Sections` draws `HhSectionLabel(LIGHTS_WHILE_AWAY)`, then each bound `HolidayLightsSection`, then the fixed lines as body text.
- `LightsHolidaySection` reads the automations as the Lights page does today, and saves with `settings.setHolidayAutomations` through `saving(toaster)`.
- Modes draws Holiday's page:
  1. The back link and the header (`BEACH_ACCESS`, "Holiday mode").
  2. `TurnButton()`.
  3. The switches.
  4. `Sections()`.
  5. `ModePlugsSection("holiday", …)`.

- [ ] **Step 4: Run the tests to see them pass**, plus `.\gradlew.bat -p build-logic :convention:test`. Record Holiday's page and Settings › Lights, look at them, and run the gate.

- [ ] **Step 5: Commit:** `git commit -m "Give Holiday mode its page under Modes and move Holiday lights' automations there from Settings › Lights"`

---

### Task 8: Settings' list, App settings and 90 s

**Files:**
- Create: `app/src/main/java/uk/co/siland/culvery/settings/AppSettingsPage.kt`, `settings/di/SettingsModule.kt`; `core/plugin/.../Setup.kt` (`SettingsPage.opens`, `LocalSettingsStart`); `core/ui/.../SettingsTokens.kt` (`sectionLabel`, `rowGap`), `core/ui/.../Components.kt` (`LocalSectionLabelStyle`)
- Modify:
  - `core/setup/.../SettingsScreen.kt` (deep link, the scrolling list, the label style), `SetupDimens.kt` (`SetupType.settingsTitle` at 37 sp; the person sheet keeps `panelTitle` at 35)
  - `di/SetupModule.kt:51-61` (the three bindings go), `pages/LocationPage.kt`, `pages/PeoplePage.kt` (an App settings section), `pages/KioskPage.kt` (a status row), `PeopleUi.kt` (two to a row), `SetupCopy.kt`
  - `app/.../display/ui/DisplaySettingsPage.kt` (split into `BrightnessAndNight` and `SeasonalLooks`), `display/di/DisplayModule.kt:42` (the binding goes), `display/DisplayWords.kt`
  - `core/homeassistant/.../ui/OtherIntegrationsPage.kt:81-83` and `HomeAssistantWords.kt:31` ("Home Assistant", order 900)
  - `core/access/.../AccessControl.kt` (`SESSION_TIMEOUT_MS = 90_000L` and its KDoc)
  - `core/plugin/.../Setup.kt:89` (the order KDoc)
- Test:
  - `app/src/test/.../SetupWiringTest.kt`, `settings/AppSettingsPageTest.kt`, `display/...DisplaySettings*Test.kt`, `shell/ui/ShellLayersTimeoutTest.kt`
  - `core/setup/src/test/.../SettingsScreenTest.kt`, `SettingsScreenshotTest.kt`, `KioskPageTest.kt`, `PeopleUiTest.kt`
  - `core/access/src/test/.../DefaultAccessControlTest.kt`, `core/homeassistant/src/test/...`

**Interfaces:**
- Consumes: `HhBackLink`, `HhAddRow` (Task 5).
- Produces: `val SettingsPage.opens: Set<String>` and `val LocalSettingsStart` (Ruling 15). Also `class AppSettingsPage : SettingsPage`, with `id = "app"`, `title = "App settings"`, `order = 0`, and `opens = {LOCATION, PEOPLE, TABLET_LOCK, DISPLAY_PAGE_ID, "seasonal"}`.
- Produces, in `:core:setup`:
  - `@Composable fun HomeLocationRow(onOpen: () -> Unit)`, and `LocationPage.Content()` kept as the sub-page.
  - `@Composable fun PeopleSection(editor: PeopleEditor)`.
  - `@Composable fun TabletLockSection(homeApp: HomeApp)`.
- Produces, in `:app` display: `@Composable internal fun BrightnessAndNightScreen(ui, actions)` and `@Composable internal fun SeasonalLooksScreen(ui, actions)`, cut from `DisplaySettingsScreen` with no change to what each control does.
- Produces: `internal fun brightnessSummary(prefs: DisplayPrefs): String`, giving "Night clock 22:00–07:00 · Sleep after 5 min" (plan), and `internal fun seasonalSummary(dates: Map<SeasonalTheme, SeasonDates>): String`, giving "Halloween 24–31 Oct" (plan; each theme's `datesLine`, joined " · ").

- [ ] **Step 1: Write the failing tests**
- `SetupWiringTest`:
  - `theListIsEightInOrder`: the titles are exactly App settings, Calendars, Shopping, Music, Lights, Modes, Door, Home Assistant.
  - `noPartyOrOtherIntegrations`.
- `SettingsScreenTest`:
  - `aSubPageIdOpensItsParent`: `initialPage = "location"` selects `settings_page_app`, and the Home location sub-page shows.
  - `theBackLinkReturns`: `‹ App settings` goes back to App settings.
  - `closeNeverCoversTheLastRow`: at 1280 × 800 dp with font scale 1.3, `settings_close`'s top is at or below the last item's bottom.
  - `theTitleIs37sp`.
  - `labelsUseTheSettingsStyle`: a page's `HhSectionLabel` measures 12 sp.
- `AppSettingsPageTest`:
  - `fourSectionsInOrder`: HOME LOCATION, PEOPLE, DISPLAY, TABLET LOCK.
  - `homeLocationRowShowsThePlaceAndOpens`.
  - `peopleTwoToARow`: the first two rows share a top and have equal widths.
  - `addAPersonIsTheDashedRow`.
  - `displayRowsOpenTheirSubPages`, with each summary's text.
  - `tabletLockSaysOnOrOff`: `homeApp.isDefault` true gives "Tablet lock is on", with the change and Use other apps buttons. False gives "Tablet lock is off" with Choose.
- Display tests: the existing night, brightness, sleep, camera and seasonal cases pass on the two sub-screens.
- `DefaultAccessControlTest`: `theSessionEndsAfterNinetySeconds`. It is still signed in at 89 s, and null at 90 s.
- `ShellLayersTimeoutTest`: passes with the constant. `aSheetStillClosesAfterTwoMinutes` (`OVERLAY_IDLE_MS` unchanged).
- `OtherIntegrationsPageTest`: the title is "Home Assistant".
- `SettingsScreenshotTest`: `allPagesDark` and `allPagesLight`, `appSettingsDark` and `appSettingsLight`, `homeLocationSubPageDark`, `brightnessSubPageDark`, `seasonalSubPageDark`. Delete the PNGs of the removed pages' shots.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :core:setup:testDebugUnitTest :core:access:testDebugUnitTest :app:testDebugUnitTest`

- [ ] **Step 3: Implement**
- **`AppSettingsPage.Content()`:** reads `LocalSettingsStart` once, as the initial `rememberSaveable` sub-page. With a sub-page, it draws `HhBackLink("App settings")` then that sub-page. Otherwise it draws `HhPageTitle("App settings", line)` and the four sections. `PEOPLE` and `TABLET_LOCK` open the main page scrolled to their section (`bringIntoViewRequester`).
- **`SettingsScreen`:**
  - The list column holds the title, then `Column(Modifier.weight(1f).verticalScroll(…))` with the items, then Close in its own space.
  - Provide `LocalSectionLabelStyle provides SettingsTokens.sectionLabel` and `LocalSettingsStart provides initialPage` around `page?.Content()`.
- **The bindings:** `SettingsModule` binds `AppSettingsPage @IntoSet SettingsPage`.

- [ ] **Step 4: Run the tests to see them pass.** Then record the Settings shots, look at them (the list at font 1.3 included), and run the gate.

- [ ] **Step 5: Commit:** `git commit -m "Gather Home location, People, Display and Tablet lock into App settings, list Settings' eight pages and close it after 90 seconds"`

---

### Task 9: The review's other visual items

**Files:**
- Modify: `core/setup/.../PeopleUi.kt` (names at 19 sp), `PersonEditorSheet.kt` (the Set PIN pill's scroll padding)
- Modify: `capability/music/.../MusicWords.kt:113` ("Lower music on a ring to"). Spec §6 lists it under Door, but the row is on Settings › Music.
- Modify: `app/.../display/ui/DisplaySettingsPage.kt` (the dates row's trailing `CHEVRON_RIGHT`)
- Modify: `capability/shopping/.../ui/ShoppingSettingsPage.kt:216-231` (`Health`), `ShoppingWords.kt` (`permissionOnSettings`)
- Test: `core/setup/src/test/.../PeopleUiTest.kt`, `PersonFormTest.kt`; `capability/music/src/test/.../ui/MusicSettingsPageTest.kt`; `app/src/test/.../display/...`; `capability/shopping/src/test/.../ui/ShoppingSettingsPageTest.kt`, `ShoppingWordsTest.kt`; `capability/calendar/src/test/.../ui/ReviewCalendars*Test.kt`

**Interfaces:**
- Produces: `fun permissionOnSettings(service: String) = "$service needs permission again."` (plan). Settings › Shopping shows it, with its Allow button, in place of `problemLine`'s "Allow … in Settings › Shopping".

- [ ] **Step 1: Write the failing tests**
- `PeopleUiTest`: `namesAre19sp`.
- `PersonFormTest`: `setPinIsWhollyOnScreenAt800dp`. In a 1280 × 800 dp window, scroll to `person_set_pin`: its bounds sit inside the sheet's scroll viewport.
- `MusicSettingsPageTest`: `theRingLineIsOneLine`. At 1280 × 800 dp, the label's `TextLayoutResult.lineCount == 1`, and its text is "Lower music on a ring to".
- Display: `datesRowsHaveAChevron` (`display_dates_halloween` holds the `chevron_right` icon node).
- `ShoppingSettingsPageTest`:
  - `nothingBeforeTheFirstRead`: with `OK` and `lastReadMillis = null`, there is no `shopping_health`.
  - `permissionLineDoesntSendYouHere`: `NEEDS_PERMISSION` shows "Google Tasks needs permission again." and `shopping_allow`, with no "Settings › Shopping" anywhere on the page.
- Calendars: `familyIsTheHouseholdsName`. The review rows' person chip offers "Family" (9c-2), and never "Everyone".

- [ ] **Step 2: Run them to see them fail**, module by module.

- [ ] **Step 3: Implement.** Each item is one change: no layout beyond what the test names.

- [ ] **Step 4: Run the tests to see them pass.** Record People, the person sheet, Music, Seasonal looks and Shopping (dark and light), look at them, and run the gate.

- [ ] **Step 5: Commit:** `git commit -m "Build the review's visual items for People, the ring line, the dates rows and Shopping"`

---

### Task 10: Taekwondo

A throwaway module (§8): deleting it means deleting `capability/taekwondo/`, the `include` in `settings.gradle.kts`, and the `implementation(project(":capability:taekwondo"))` line in `app/build.gradle.kts`.

**Files:**
- Create: `capability/taekwondo/build.gradle.kts` (the plugins and dependencies as `capability/party/build.gradle.kts`: `api(:core:plugin)`, `:core:ui`, `libs.androidx.datastore.preferences`, test fixtures, roborazzi)
- Create: `src/main/AndroidManifest.xml`, with `<queries><intent><action android:name="android.intent.action.TTS_SERVICE"/></intent></queries>`. This is Android 11's package visibility; the SM-T510 runs API 30.
- Create in `src/main/java/uk/co/siland/culvery/capability/taekwondo/`: `Moves.kt`, `TaekwondoRun.kt`, `Speaker.kt` (interface and `TtsSpeaker`), `TaekwondoSettings.kt`, `TaekwondoCapability.kt`, `TaekwondoQuieter.kt`, `ui/TaekwondoScreen.kt`, `di/TaekwondoModule.kt`
- Modify: `settings.gradle.kts`, `app/build.gradle.kts`; `core/plugin/.../Overlays.kt` (`keepOpen`), `app/.../shell/ShellOverlays.kt` (`OverlayState.keepOpen`)
- Test: `capability/taekwondo/src/test/.../MovesTest.kt`, `TaekwondoRunTest.kt`, `TaekwondoCapabilityTest.kt`, `TaekwondoQuieterTest.kt`, `ui/TaekwondoScreenTest.kt`, `ui/TaekwondoScreenshotTest.kt`; `app/src/test/.../shell/ui/OverlayLayersTest.kt`; `build-logic` `ModuleBoundariesTest` (passes unchanged)

**Interfaces:**
- Consumes: `TrayReservation` (Task 4), `Icons.SPORTS_MARTIAL_ARTS` (Task 5).
- Produces:
  ```kotlin
  /** [hangul] null until the instructor fills it in; the English voice reads [romanised] then (15b §8). */
  data class Move(val number: String, val romanised: String, val hangul: String?, val english: String, val description: String)
  data class Pattern(val name: String, val moves: List<Move>)
  val PATTERNS: List<Pattern> = listOf(CHOONG_MOO)

  interface Speaker {
      /** True once a Korean voice is known to be on the tablet. */
      val hasKorean: Boolean
      /** Returns when [text] has been spoken, or at once if it can't be. */
      suspend fun say(text: String, korean: Boolean)
      fun stop()
  }

  sealed interface RunState {
      data object Idle : RunState
      data class Counting(val seconds: Int) : RunState
      data class Moving(val index: Int, val move: Move) : RunState
      data class Again(val seconds: Int) : RunState
  }

  class TaekwondoRun(private val speaker: Speaker, private val scope: CoroutineScope) {
      val state: StateFlow<RunState>
      fun start(pattern: Pattern, detailed: Boolean)
      fun stop()
  }
  ```
- Produces: `TaekwondoCapability`: `id = "taekwondo"`, `label = "Taekwondo"`, `icon = Icons.SPORTS_MARTIAL_ARTS`, `order = 95`, `hasTab = flowOf(false)`. Its tray button is `TrayButton("taekwondo", 90, TrayWidth.HALF)`, a `HouseModeTile` with no ON state, which opens the screen with `showFullScreen`.
- Produces: `TaekwondoModule`, which binds the capability, a `TrayReservation` (id `taekwondo`), and `TaekwondoQuieter : AlertQuieter` (Ruling 18), all `@IntoSet`.
- Produces: `fun OverlayHost.keepOpen(on: Boolean)`.
- Produces: `TaekwondoSettings`, a DataStore `taekwondo_settings` holding `val detailed: Flow<Boolean>` (default false) and `suspend fun setDetailed(on: Boolean)`.
- Copy: "Taekwondo", "Detailed mode", "Cancel", "Close", and "Again in {n}" (plan). The card's title is the pattern's name.

**The moves.** `CHOONG_MOO = Pattern("Choong-Moo", …)` holds these 32 entries in order. The romanised, English and description text is exactly `culvery-notes/taekwondo-choong-moo.html`'s, straight apostrophes included. The Hangul is this plan's transliteration of the romanised ITF term, for the reviewer to check.

| # | Romanised | Hangul | English | Description |
|---|---|---|---|---|
| Start | Narani junbi sogi | 나란히 준비 서기 | Parallel ready stance | Feet parallel, shoulder width apart; calm and ready to begin. |
| 1 | Niunja so sang sonkal makgi | 니은자 서 쌍 손칼 막기 | L-stance, twin knife-hand block | Both knife-hands block at once, guarding the front and the side. |
| 2 | Gunnun so sonkal nopunde ap taerigi | 걷는 서 손칼 높은데 앞 때리기 | Walking stance, high front knife-hand strike | Strike the neck with the knife-hand while the other hand guards the forehead. |
| 3 | Niunja so sonkal kaunde daebi makgi | 니은자 서 손칼 가운데 대비 막기 | L-stance, middle knife-hand guarding block | Turn and block the middle section, the rear hand guarding the chest. |
| 4 | Gunnun so opun sonkut nopunde tulgi | 걷는 서 엎은 손끝 높은데 뚫기 | Walking stance, high flat fingertip thrust | Fingertips thrust at the eyes, palm facing down. |
| 5 | Niunja so sonkal kaunde daebi makgi | 니은자 서 손칼 가운데 대비 막기 | L-stance, middle knife-hand guarding block | Turn to face a new attacker and guard the middle section. |
| 6 | Guburyo junbi sogi A | 구부려 준비 서기 A | Bending ready stance A | Stand on one leg, chambered for the kick that follows. |
| 7 | Kaunde yopcha jirugi | 가운데 옆차 지르기 | Middle side piercing kick | Drive the foot sword straight out to the side. |
| 8 | Niunja so sonkal kaunde daebi makgi | 니은자 서 손칼 가운데 대비 막기 | L-stance, middle knife-hand guarding block | Land from the kick and guard the middle section. |
| 9 | Twimyo yopcha jirugi, wen niunja so sonkal kaunde daebi makgi | 뛰며 옆차 지르기, 왼 니은자 서 손칼 가운데 대비 막기 | Flying side piercing kick, then L-stance knife-hand guarding block | Jump and kick in the air, then land balanced and guard at once. |
| 10 | Niunja so palmok najunde yop makgi | 니은자 서 팔목 낮은데 옆 막기 | L-stance, low forearm side block | Sweep a low kick aside with the forearm. |
| 11 | Gunnun so, mori japgi? | TO FILL (`null`) | Walking stance, grab the opponent's head | Reach up with both hands to seize the head. |
| 12 | Moorup ollyo chagi | 무릎 올려 차기 | Knee upward kick | Pull the head down with both hands and drive the knee up into it. |
| 13 | Gunnun so sonkal dung nopunde bandae ap taerigi | 걷는 서 손칼등 높은데 반대 앞 때리기 | Walking stance, high reverse ridge-hand strike | The ridge of the hand swings in to the side of the head; the other hand rests under the elbow. |
| 14 | Nopunde dollyo chagi | 높은데 돌려 차기 | High turning kick | A circular kick to the head; flow straight into the next kick. |
| 15 | Kaunde dwitcha jirugi | 가운데 뒤차 지르기 | Middle back piercing kick | Drive the heel straight back into the body, fast after the turning kick. |
| 16 | Niunja so palmok kaunde daebi makgi | 니은자 서 팔목 가운데 대비 막기 | L-stance, middle forearm guarding block | Guard the middle section with both forearms. |
| 17 | Kaunde dollyo chagi | 가운데 돌려 차기 | Middle turning kick | A circular kick to the body. |
| 18 | Gojung so digutja makgi | 고정 서 디귿자 막기 | Fixed stance, U-shape block | Both arms block high and low at once, as if stopping a staff. |
| 19 | Twigi, wen niunja so sonkal kaunde daebi makgi | 뛰기, 왼 니은자 서 손칼 가운데 대비 막기 | Jump and spin, then L-stance knife-hand guarding block | Jump and spin a full turn, landing on the same spot ready to guard. |
| 20 | Gunnun so dwijibun sonkut najunde tulgi | 걷는 서 뒤집은 손끝 낮은데 뚫기 | Walking stance, low upset fingertip thrust | Fingertips thrust up into the lower body, palm facing up. |
| 21 | Niunja so dung joomuk baro yopdwi taerigi wa palmok najunde bandae makgi | 니은자 서 등주먹 바로 옆뒤 때리기 와 팔목 낮은데 반대 막기 | L-stance, side back strike with the back fist and low block | Strike behind you with the back fist while blocking low in front. |
| 22 | Gunnun so sun sonkut kaunde tulgi | 걷는 서 선 손끝 가운데 뚫기 | Walking stance, middle straight fingertip thrust | Straight fingertips thrust into the solar plexus. |
| 23 | Gunnun so doo palmok nopunde makgi | 걷는 서 두 팔목 높은데 막기 | Walking stance, high double forearm block | One forearm blocks high while the other backs it up. |
| 24 | Annun so orun palmok kaunde ap makgi, orun dung joomuk nopunde yop taerigi | 앉은 서 오른 팔목 가운데 앞 막기, 오른 등주먹 높은데 옆 때리기 | Sitting stance, middle front block, then high side back-fist strike | Block in front with the forearm, then snap the back fist out to the side of the head. |
| 25 | Kaunde yopcha jirugi | 가운데 옆차 지르기 | Middle side piercing kick, right foot | The first of two side kicks along the same line. |
| 26 | Kaunde yopcha jirugi | 가운데 옆차 지르기 | Middle side piercing kick, left foot | The second side kick, with the other leg. |
| 27 | Niunja so kyocha sonkal momchau makgi | 니은자 서 교차 손칼 멈추어 막기 | L-stance, X knife-hand checking block | Crossed knife-hands stop a kick before it lands. |
| 28 | Gunnun so sang sonbadak ollyo makgi | 걷는 서 쌍 손바닥 올려 막기 | Walking stance, twin palm upward block | Both palms push upward to block a rising attack. |
| 29 | Gunnun so palmok chookyo makgi | 걷는 서 팔목 추켜 막기 | Walking stance, rising block | Lift the forearm to block a strike to the head. |
| 30 | Gunnun so kaunde bandae jirugi | 걷는 서 가운데 반대 지르기 | Walking stance, middle reverse punch | The pattern ends with a left-hand punch, marking Admiral Yi's death in battle. |
| End | Baro? | TO FILL (`null`) | Return to ready posture | Bring the left foot back to the ready posture. |

- [ ] **Step 1: Write the failing tests**
- `MovesTest`:
  - `choongMooHas32InOrder`: the numbers are "Start", "1" … "30", "End".
  - `textIsTheInstructorsList`: entries Start, 11, 21, 30 and End equal the table's romanised, English and description exactly.
  - `onlyElevenAndEndWaitForHangul`.
- `TaekwondoRunTest`, with a `FakeSpeaker` that records `(text, korean)` and returns after a virtual second, under `runTest`:
  - `countsDownTenThenSaysTheFirstMove`: `Counting(10)` … `Counting(1)`, then `Moving(0)` with `say("나란히 준비 서기", korean = true)` at 10 s.
  - `detailedAddsTheDescriptionInEnglish`.
  - `threeSecondsOfQuietBetweenMoves`.
  - `afterTheLastTenSecondsThenTheFirstAgain`: `Again(10)` … then `Moving(0)`.
  - `noKoreanVoiceReadsTheRomanisedInEnglish`.
  - `noHangulReadsTheRomanisedInEnglish`: step 11.
  - `stopEndsAtOnceAndStopsTheSpeaker`.
  - `onlyStartAndStopAreLogged`: `ShadowLog` holds exactly "A run started" and "A run stopped", with no move text.
- `TaekwondoQuieterTest`: `aRingStopsTheRun` (`quiet()` returns false, and the state is `Idle`).
- `TaekwondoCapabilityTest`:
  - `aHalfButtonAfterTheModes`: order 90, `TrayWidth.HALF`.
  - `holdsOneTraySpot`: the module's `TrayReservation` binding.
- `TaekwondoScreenTest`:
  - `theGridShowsChoongMoo`.
  - `aTapCountsDown`: "10" shows large.
  - `theMoveShowsItsNumberKoreanAndEnglish`.
  - `cancelStops`.
  - `closeStopsAndCloses`.
  - `detailedIsRemembered`: the switch writes `TaekwondoSettings`, and a new screen reads it on.
  - `keptOpenWhileRunning`: `keepOpen(true)` on start, `false` on stop.
- `OverlayLayersTest`: `aKeptOpenOverlayWaitsThenClosesOnceLetGo`. Nothing closes at `OVERLAY_IDLE_MS + 1 s` while it is kept open. After `keepOpen(false)`, it closes `OVERLAY_IDLE_MS` later.
- `TaekwondoScreenshotTest`: `gridDark`, `countingDark`, `movingDark`.

- [ ] **Step 2: Run them to see them fail:** `.\gradlew.bat :capability:taekwondo:testDebugUnitTest :app:testDebugUnitTest`

- [ ] **Step 3: Write `TtsSpeaker`, and STOP before its progress listener.** `TextToSpeech(context, listener)`, `speak(CharSequence, Int, Bundle?, String)` and `setLanguage` are current. Waiting for an utterance to end needs `UtteranceProgressListener`, whose abstract `onError(String)` is deprecated (API 21) but must be overridden. Report this to the controller, who asks the user (the global constraint on deprecated APIs). The proposal: override `onError(String)` only to call the current `onError(String, Int)`, with a one-line comment saying why. Don't write the listener until the user has answered.

- [ ] **Step 4: Implement**
- `TtsSpeaker.hasKorean` is `isLanguageAvailable(Locale.KOREAN) >= LANG_AVAILABLE`, read once the engine starts.
- `say` sets the language (Korean or `Locale.UK`), then speaks with `QUEUE_FLUSH`. It suspends on `suspendCancellableCoroutine` until `onDone` or `onError` for that utterance id. Cancellation calls `stop()`.
- Nothing installs voice data: no `ACTION_INSTALL_TTS_DATA`.
- `TaekwondoRun` is one coroutine on `scope`: the countdown in one-second `delay`s, then per move `say` the term (and the description when `detailed`), then `delay(3_000)`, then `Again` and repeat. `stop()` cancels the coroutine and calls `speaker.stop()`.

- [ ] **Step 5: Run the tests to see them pass**, plus `.\gradlew.bat -p build-logic :convention:test` (a new TAG; ModuleBoundaries). Record the Taekwondo shots and `home_tray_*` with Taekwondo, look at them, and run the gate.

- [ ] **Step 6: Commit:** `git commit -m "Add a throwaway Taekwondo practice screen that reads Choong-Moo's moves aloud from a tray button"`

---

### Task 11: Simplify and the emulator walk-through (controller with the user)

- [ ] Run the `simplify` skill on the branch's changes. If it changes anything, re-run the gate.
- [ ] Start the `culvery-5` AVD and take it offline before Culvery opens, after every restart (`svc wifi disable`, `svc data disable`).
- [ ] Install the debug build over the 15a one, with Party mode set up before the update (music, two scenes, a plug), once off and once on. This checks the move on a real file: Review Focus 1.
- [ ] Walk through it with the user on the debug sample house. Claude enters the debug sample PINs.
  - **Settings:** the eight pages, Close at font 1.3, the 90 s close and its warning, the weather header's link to Home location.
  - **App settings:** each section and sub-page, and back.
  - **Modes:**
    - Add Film night (name problems, icons), rename it, change its icon.
    - Show on Home up to the limit (with and without Taekwondo), Needs a PIN with a locked plug.
    - Lights as a scene, On · 60% and Off, plugs, then Delete (on and off).
  - **Holiday's page:** Turn on now (the sheet), Lights while away, plugs.
  - **Home:** one tap with no PIN as a Child; a PIN mode as a Child (refused) and as an Adult; one at a time from the tile and from the page; four modes in the tray; the lines.
  - **The visual items** (§6).
  - **Taekwondo:**
    - A run in detailed mode.
    - The emulator's Korean voice, if it has one. If not, the English fallback.
    - A ring stops it. Close and Cancel.
    - It doesn't sleep while running.
  - **TalkBack** on the new controls, in short batches.
- [ ] Collect faults and ask the user before fixing any.
- [ ] List the cuts again:
  - The setup wizard.
  - Settings › Home buttons.
  - The status tab.
  - The rail redesign.
  - The sound board.
  - Party mode's speed (15c).
  - Reordering modes.
  - Several modes on at once (non-goal).
  - Taekwondo's step 11 and End Hangul, waiting on the instructor.

### Task 12: Tablet, final review, merge, to-do list (controller with the user)

- [ ] Run the final whole-branch review (opus), and fix what the user agrees to.
- [ ] Build a release and check it carries no DebugClock. Run `adb install -r` on the SM-T510 only; never uninstall or wipe.
  - Check that Party mode's settings and plug choices came through the move on the family's real file.
  - Scan the logs for names, mode names and placeholders.
  - No real light, plug or speaker is switched without the user's go.
  - Taekwondo's Korean voice: note whether the SM-T510 has one.
- [ ] Merge to `main` locally with `git merge --ff-only`. Don't push without asking, and check for personal data first.
- [ ] In `docs/superpowers/plans/2026-09-23-plan1-followups.md`, remove the items this plan closed (W26, W29, W34, People, the ring line, the dates row, Shopping, the clipped Close, "Editing what modes do with plugs") and add only what it leaves open. Add any ideas the user mentioned in passing to Feature ideas. Commit.
