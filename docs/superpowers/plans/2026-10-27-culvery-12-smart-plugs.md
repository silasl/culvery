# Culvery — Plan 12: Tapo smart plugs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–7 are subagent tasks (sonnet implementers, a reviewer per task; **opus** reviews where the table says so). Task 8 is the controller. Task 9 is the controller on the `culvery-5` AVD with the user. Tasks 10–11 need the user and the son's SM-T510.

**Goal:** the household's Tapo plugs show on a new Plugs tab through Home Assistant and switch from it, and Holiday mode switches the plugs in its list on turning on and puts them back on turning off, with lists built so Party mode adds its own and nothing else changes.

**Architecture:**
- **`:core:house`** gains `HouseModeInfo` (a mode's id, name and order, multibound), `HousePlugs` (optional, implemented by plugs) and `PlugChange`.
- **`:capability:plugs`** (new) holds:
  - the `PlugSystem` seam;
  - the store (`plug_settings`): the added plugs, their PIN setting, each mode's list, each running mode's snapshot and the notes;
  - `PlugsHouse`, which implements `HousePlugs` (notes, enter, leave);
  - the two permissions, `PlugActions`, the capability and the Plugs tab with its two sheets.
- **`:provider:plugs-homeassistant`** (new) implements `PlugSystem` over Plan 11's `HomeAssistant`: switch state, watts found by name, `switch.turn_on` / `turn_off` with the 30-second check, the list of switches for Add a plug.
- **Holiday mode** gains a `PLUGS_ON` step after Eco, `leave("holiday")` at tap-off, the plug lines on its sheet and tile, and binds its `HouseModeInfo`.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2, OkHttp 4.12.0 and MockWebServer 4.12.0; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-27-culvery-12-smart-plugs-design.md`, all of it. §3 is the user's set-up in Home Assistant, not code. The brainstorm mockups (`.superpowers/brainstorm/`, v2) are the drawing; their visual details are provisional until the end-of-v1 design review.

**Not this plan** (spec §9): Party mode and its section; plugs joining Holiday lights; the rail redesign; plug buttons in the One-tap tray, energy history, schedules or timers.

**Previous plans (format, shared parts):**
- `docs/superpowers/plans/2026-10-26-culvery-11a-home-assistant-hoover-heating.md`: `HomeAssistant`, its fixtures, a capability with an HA provider, the 30-second check, the capture, the image routine, `TestAccess`.
- `docs/superpowers/plans/2026-10-27-culvery-11b-holiday-mode.md`: `:core:house`, `HolidayHouse`, its record and retries, the sheet and tile.

**Builds on:** branch `plan-12-smart-plugs` in the one checkout `C:\Users\si\code\siland\culvery`, cut from `main` at 82b81db9. No worktrees. Never push.

## Global Constraints

**From the spec, verbatim:**
- §2.1 "**Culvery never assumes which plugs it may control.** … No plug is special-cased by its name, its model or what it powers."
- §2.2 "**Settings › Other integrations keeps only the Home Assistant link.**" Nothing about plugs goes there.
- §2.4 "**Per mode, each plug is Leave, On or Off.** Leave is the default for a newly added plug and for a newly added mode".
- §2.6 "**When a mode ends, each plug it switched goes back to how it was just before the mode started.**"
- §2.11 "**The PIN that turns a mode on covers every plug in its list for as long as the mode is on**, restarts included. "Needs a PIN to switch" applies only to switching a plug by hand on the Plugs tab."
- §2.12 "each plug's state is remembered from before the **first** mode that touched it, and it is put back only when the **last** of those modes ends."
- §4.1 "Watts: found by name in `/api/states` … No such sensor, no watts. No entity-registry read." Commands over REST "with a 5 s timeout; off screen (Holiday mode's steps), REST reads every 5 s for up to 30 s to see the change."
- §4.2 "Culvery stores only the entity id." The name is "Home Assistant's friendly name, read live".
- §4.4 `plugs.switch`, "Switch plugs that need a PIN", and `plugs.manage`, "Add, remove and set up plugs": default roles Admin and Adult, a fresh PIN every time. "A Child's PIN is refused for both."
- §4.5 the store is "Excluded from cloud backup and device transfer, as Plan 11's stores are."
- §5.1 "**No grant on `enter` or `leave`**".
- §6 "Logs carry the call and its outcome only: no entity ids, no plug names …, no watts."
- §8 "**The plugs are real devices: no plug is switched on or off, from any build or test, without the user's go each time.** Tests use MockWebServer and fakes."

**Copy** (verbatim from the spec; curly apostrophes and "›", "·", "…" in code):
- **Tab:** "Plugs"; the header line "{on} of {n} on · Home Assistant"; "Add a plug"; "No plugs yet. Add one from Home Assistant."
- **Card states:** "On · 182 W", "On", "Off", "Can’t reach this plug."; "Switching…"; a gone plug "Home Assistant has no plug called that any more. Remove it or add it again."
- **Plug settings:** "Needs a PIN to switch"; "Remove".
- **Mode rows:** "Leave", "On", "Off"; "Edit".
- **Toasts:** "Can’t reach {name}. Check it’s plugged in and the Tapo app can see it."; "{name} didn’t switch. Try again, or use the Tapo app."
- **Holiday sheet:** "Switch {name} off." / "Switch {name} on."; "Plugs go back as they were when you turn it off."
- **Holiday tile:** "{name} didn’t switch off. It’ll keep trying." (one plug) / "2 plugs didn’t switch. They’ll keep trying."; "{name} didn’t switch back on. Switch it on the Plugs tab."
- **TalkBack:** "Plug {name}, on, 182 watts." with the button "Switch off"; "Plug {name}, off." with "Switch on"; "Plug {name}, can’t reach this plug."; with the PIN setting the button is "Switch on, needs a PIN"; mode rows "Holiday mode, {name}, leave alone" (and "on", "off"), the chip's selected state read by its role.
- **This plan's copy** (spec gaps; the owning task tests each):
  - the button's visible text is the action, as TalkBack says it: "Switch on" / "Switch off";
  - a plug before its first read, or while Home Assistant has a problem: "Checking…" (`CHECKING`), spoken "Plug {name}, checking.";
  - a gone plug spoken "Plug {name}. Home Assistant has no plug called that any more. Remove it or add it again.";
  - the › on a card: spoken "Settings for {name}";
  - Edit's other state: "Done";
  - the Add a plug sheet: title "Add a plug"; with nothing to add "Home Assistant has no other switches."; a link problem gives its §6 line (`problemLine`);
  - more than one plug not put back: "2 plugs didn’t switch back. Switch them on the Plugs tab.";
  - a plugs step that threw rather than answer: "The plugs didn’t switch. They’ll keep trying.".

**From the house rules** (as Plans 11a and 11b; binding):
- **Platform:**
  - package root `uk.co.siland.culvery`; `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only;
  - **no new library; never change a version**;
  - no deprecated API, and any one met is flagged to the user before use. Known and avoided: the plug switch's old `current_power_w` attribute (gone from Home Assistant; the sensor is used); 11a's list (`battery_level`, `KeyboardOptions(autoCorrect = …)`, `LocalClipboardManager`).
- **New modules:**
  - `:capability:plugs`: as `capability/hoover/build.gradle.kts`, plus `api(project(":core:house"))`.
  - `:provider:plugs-homeassistant`: as `provider/hoover-homeassistant/build.gradle.kts` with `:capability:plugs`, plus `testImplementation(libs.okhttp.mockwebserver)`.
  - Each is added to `settings.gradle.kts`, to `app/build.gradle.kts` and to the README's module list in the task that creates it. `ModuleBoundariesTest` gains a pin, `thePlugModulesKeepToTheirFamilies`.
- **The test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash, or `gradlew.bat`), plus `./gradlew -p build-logic :convention:test` in any task that adds a log line or a module.
  - Never plain `test`, `check` or `build`, and never a Gradle `install…` task. No subagent touches a device.
  - **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`**: a long temp path breaks Robolectric's SQLite.
  - Scratch goes in `$LISTS` = `C:/t9`, a plain shell variable, never exported.
- **Tests that can fail:**
  - Every new test fails before its change; one that passes before it is labelled **pin**.
  - HTTP tests use MockWebServer; nothing reaches a real address. Timer tests run on virtual time and assert one tick before the boundary and at it.
  - Each module that needs a PIN copies Lights' `TestAccess(role)` with its own `PermissionSource`.
- **Every UI task ends as Plan 11a's:**
  1. `compareRoborazziDebug` on the module; open each `*_compare.png`, and check each new image against the drawing named;
  2. `recordRoborazziDebug`;
  3. the baseline check (`$LISTS/expected-baselines.txt`, `$LISTS/expected-prefixes.txt`, then Plan 10's four `comm`/`grep` lines);
  4. the gate;
  5. commit.

  Every new screen is recorded `_dark` and `_light`. `CopyScanTest` passes; every screen test ends with `compose.assertClickablesHaveRoleAndLabel()`.
- **Code:**
  - Icons come from `Icons`. `power` is added to `tools/fonts/icons.txt` and the subset rebuilt as Plan 11a's Code rule says (the full font from `git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf` into `$LISTS`); a missing glyph means a download, so stop and ask.
  - Layout numbers go in `PlugsDimens`.
  - Comments say only what the code can't, in one line by default, with spec refs as `(12 §5.4)`, never a task or ruling number.
  - Each new file with a `TAG` is named in `LogHygieneTest.theScanReachesTheShippedCode`.
- **Privacy:**
  - No entity id, plug name or watt figure goes into a log, an exception message, a `toString`, a test name or a test tag. Tags use the plug's index.
  - `PlugState`, `PlugCandidate`, `PlugChange`, `PlugPrefs` and `PlugNote` override `toString` without them.
  - Sample data only: `switch.lamp` ("Lamp"), `sensor.lamp_current_consumption` ("182", "W"), `switch.fan` ("Fan"), `switch.fairy_lights` ("Fairy lights"), `switch.desk` ("Desk"), the extra `switch.lamp_led` ("Lamp LED"), and Plan 11's `TEST_LINK`. Real entity ids and names stay in `$LISTS`.
- **Devices:**
  - The emulator is `culvery-5`. `emulator-5554` holds a signed release and the household: never install on it.
  - The tablet (SM-T510) gets `adb install -r` of a signed release only; never uninstall, never wipe.
  - **No plug is switched, and Holiday mode is never turned on with a plug on On or Off, except on the user's go each time** (Tasks 9–11).
- **Git:**
  - A commit is a plain sentence with no prefix and no trailer of any kind; never push.
  - **Never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **A plug Home Assistant no longer has, or one renamed there, while a mode lists it or holds a note on it.**
   - **Expected:** at enter it is not reached (Failed, retried by the mode, no note); at leave it is treated as unreachable (Failed with its line, note dropped); the card says the gone line; nothing throws or sticks.
   - **Tests** (Task 2): `PlugsHouseTest.aGonePlugAtEnterIsRetriedNotNoted`, `aGonePlugAtLeaveDropsItsNote`; (Task 3) `HaPlugSystemTest.aDeletedPlugIsGone`.
2. **A plug removed from the tab while a mode is on and still retrying it.**
   - **Expected:** the next retry doesn't read or switch it; leave doesn't touch it; its note, list entries, snapshot and pending entry are all gone; an enter running at that moment doesn't write it back.
   - **Tests** (Task 2): `PlugsHouseTest.removingAPlugMidModeStopsItsRetries`, `aRemoveDuringEnterIsNotWrittenBack`.
3. **Taps on a card: twice quickly, while switching, on an unreachable plug, or after the state changed under the finger.**
   - **Expected:** one PIN pad and one switch per plug at a time; taps while "Switching…" are ignored; an unreachable, gone or checking plug's button does nothing; the target is the opposite of the state shown at the tap.
   - **Tests** (Task 4): `PlugActionsTest.aSecondTapWhileSwitchingDoesNothing`, `anUnreachablePlugIsNotSwitched`; (Task 3) `HaPlugSystemTest.aSecondSwitchOfOnePlugIsBusy`.
4. **The tablet restarts mid-enter: after a note was written but before its switch was seen.**
   - **Expected:** the note's *before* is the state read before the first switch, not re-read; the retry finds the plug either already switched (confirmed, no command) or not (switched); the snapshot is the one taken at turn-on.
   - **Tests** (Task 2): `PlugsHouseTest.aRestartBetweenTheNoteAndTheSwitchKeepsTheFirstBefore`, `notesSurviveARestart`.
5. **Home Assistant down (NAS off, token refused) when Holiday mode turns off.**
   - **Expected:** Holiday mode still ends; every note this mode held is dropped; the tile says which plug, or how many, didn't switch back; nothing is retried after off.
   - **Tests** (Task 2): `PlugsHouseTest.leaveWithHomeAssistantDownDropsEveryNoteAndSaysHowMany`; (Task 6) `HolidayHouseTest.turningOffWithPlugsFailingStillEnds`.

---

| # | Task | Review |
|---|---|---|
| 1 | `:core:house` mode seams; `:capability:plugs` 1: the seam, the store, the permissions, the words, the icon | sonnet |
| 2 | `:capability:plugs` 2: `PlugsHouse`: notes, enter, leave, two modes | **opus** (put-back, restarts, races) |
| 3 | `:provider:plugs-homeassistant`: state, watts by name, switching with its check, the candidates | **opus** (states, races) |
| 4 | `:capability:plugs` 3: `PlugActions` and the capability | **opus** (access) |
| 5 | The Plugs tab: cards, the mode sections, Edit, the two sheets | sonnet |
| 6 | Holiday mode switches plugs: the step, tap-off, the sheet's and tile's lines, its `HouseModeInfo` | **opus** (restarts, turn-off) |
| 7 | The app wired in, the README | sonnet |
| 8 | Simplify the whole branch | controller |
| 9 | The emulator walk-through on `culvery-5`, the device checks and captures | controller + user |
| 10 | Fixtures from the captures; on the tablet | controller + user, **needs the SM-T510** |
| 11 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:** the seams and the store first (1), so the put-back logic (2) and the provider (3) each meet a tested store and contract; the actions (4) before the screen that drives them (5); Holiday's part (6) once `HousePlugs` is real; the wiring (7) last.

## Rulings against the code

The spec leaves these to the builder, and a few fall out of the code. Each is this plan's call.

1. **The mode seams** (`:core:house`, `HouseSeams.kt`):
   - `data class HouseModeInfo(val id: String, val name: String, val order: Int)`.
   - `object HouseModes { const val HOLIDAY = "holiday" }`. Party adds its own id.
   - `data class PlugChange(val name: String, val on: Boolean)`, `toString` without the name.
   - `interface HousePlugs { fun ready(modeId: String): Flow<Boolean>; suspend fun preview(modeId: String): List<PlugChange>; suspend fun enter(modeId: String): HouseStep; suspend fun leave(modeId: String): HouseStep }`.
   - `ready` is narrowed to the given mode (spec §5.1 allows it): true while that mode's list has at least one On or Off for an added plug and a `PlugSystem` is bound.
   - `HouseModule` gains `@Multibinds Set<HouseModeInfo>` and `@BindsOptionalOf HousePlugs`.
2. **The store** (`plug_settings`, `PlugSettings`), one DataStore Preferences file. Entity ids are `[a-z0-9_.]` and mode ids `[a-z]`, so plain separators are safe:
   - `plugs`: the added ids in order, joined by `,`;
   - `pin`: a string set of ids that need a PIN;
   - `mode.{modeId}`: a string set of `"{id}=on"` / `"{id}=off"`; a plug not listed is Leave;
   - `run.{modeId}`: the mode's snapshot at turn-on, the same form; `pending.{modeId}`: the snapshot's ids not yet reached;
   - `note.{id}`: `"{before};{mode}={on|off},{mode}={on|off}"`, holders oldest first, e.g. `"off;holiday=on"`.

   Malformed entries are skipped on read. `PlugPrefs` is the whole file as one value; every write is `update { prefs -> … }`, a transform inside one `store.edit`, so a write never puts back an older copy.
3. **Reached** means the plug was switched and seen, or was already as the mode wants. The note is written at the first successful read, before any switch, so *before* is never re-read. A plug whose switch wasn't seen stays pending and is read again at the next `enter`: if it is now as wanted it is reached without a command, else it is switched again. *Expected* is the newest holder's setting, derived, not stored.
4. **`enter(modeId)`**, under `PlugsHouse`'s `Mutex`:
   1. No run for the mode: write `run` = the mode's list now (added plugs only) and `pending` = its ids.
   2. For each pending id still added, in tab order: `read` it. Not `ON` or `OFF` → not reached. No note → write one (before = read state, holders = [this mode]). A note without this mode → add it as the newest holder. If the state differs from the snapshot's setting, `switch`; not `Done` → not reached. Reached → remove from pending.
   3. Done when nothing is pending, else `Failed(enterLine(...))` naming the plugs not reached by their names from the reads.
5. **`leave(modeId)`**, under the same `Mutex`. No run → `Done`. Otherwise remove the run first, then for each note holding this mode, in tab order, with the plug's `read`:
   - unreachable or gone → drop this mode from the holders; when none remain, drop the note; `Failed` when a switch would have been needed;
   - not pending in this mode's removed run or in any other running mode, and its state differs from *expected* → a hand change: drop the whole note, switch nothing;
   - other holders remain → remove this mode; if it was the newest and the new *expected* differs from the state, switch to it;
   - last holder → switch to *before* if the state differs; drop the note.

   A failed switch still drops or updates the note. The result is `Done`, or `Failed(leaveLine(...))` for the plugs not put back.
6. **Remove** (`PlugPrefs.without(id)`) drops the id from `plugs`, `pin`, every list, every run's snapshot and pending set, and its note. The plug is left as it is. Every `PlugsHouse` write re-checks the id is still in `plugs`, so a remove during an `enter` stands.
7. **Retries are the mode's.** `PlugsHouse` has no timer: Holiday retries its `PLUGS_ON` step every 5 minutes as it does its other steps (11 §5.3), and a later Party mode does the same.
8. **The provider's state** (`HaPlugSystem`):
   - `plugs` is null with no link; else one `PlugState` per added id, in order. `name` = the friendly name, else the entity id. With a link problem every plug is `UNKNOWN` and `problem` carries `problemLine`. Otherwise: the id null in the cache → `GONE`; `"unavailable"` → `UNREACHABLE`; `"on"` / `"off"` → `ON` / `OFF`; absent, `"unknown"` or another word → `UNKNOWN`.
   - Watts: `sensor.` + the switch's object id + `_current_consumption` (spec §4.1; Task 9 confirms the name). A number in 0..100 000, rounded, only while `ON`; else null.
   - `watch()` follows the added switches and their watt sensors.
   - `read(id)` is `ha.current(id)` and never throws: a failure gives `UNREACHABLE` with the cached name.
   - `switch(id, on)`: one per plug at a time (a per-id lock; `Busy` otherwise). No link → `Failed(NOT_LINKED)`; gone → `Failed(GONE_PLUG)`; `unavailable` → `Unreachable`; already so → `Done` with nothing sent; then `switch/turn_on` or `switch/turn_off`; `REFUSED` → `NotSwitched`; `awaitEntity(id, CONFIRM_FOR_MS)` on the wanted state → `Done`, else `NotSwitched` (or `Unreachable` when the last state seen is `unavailable`); other `HomeAssistantException`s → `Failed(problemLine)`.
   - `candidates()` is every `switch.*` from `ha.states()`, by name; a failure is `Failed(problemLine)`.
9. **Switching by hand** runs on `@ApplicationScope` in `PlugActions`, so its toast shows if the tab closes. `switching: StateFlow<Set<String>>` holds a plug from the tap (PIN pad included) until the result. Only the Plugs tab watches the socket: Home has no plug UI in this plan.
10. **The tab** (`id = "plugs"`, `label = "Plugs"`, `icon = Icons.POWER`, `order = 55`): `hasTab` = the system's `plugs` not null.
    - A plug's own settings open from a **›** on its card (`HhRoundButton`), not a long press: it's visible and TalkBack reaches it.
    - **One Edit** for all mode sections, at the column's head. It ends on Done, on leaving the tab, or `EDIT_IDLE_MS = 120_000L` after the last change. At rest a row shows the chosen word; in Edit, three `HhChoiceChip`s in a radio group.
    - Add a plug, the ›, and Edit each ask `plugs.manage` first; inside an open sheet or Edit the changes need no second PIN.
    - Remove takes effect at once, with no second confirm: the fresh PIN is the check.
    - With no plugs, the mode column isn't drawn.
11. **Holiday's step:**
    - `HolidayStep` becomes `LIGHTS_ON, ECO_ON, PLUGS_ON, HOOVER, ECO_OFF`. Stored by name, so old records still read.
    - `PLUGS_ON` is planned when `plugs?.ready(HOLIDAY)` is true. Its window is always open while on, and it is never `MISSED`.
    - `turnOff` calls `plugs.leave(HOLIDAY)` after the heating whenever the seam exists. The off line is the first that applies: `STILL_ON_ECO`, the plugs' leave line, `LIGHTS_STILL_ON`.
12. **Emulator walk-through of Holiday with plugs** (spec §8.1): no fake `PlugSystem` in the debug build. Holiday mode is walked with a plug on On or Off and the AVD in airplane mode from before turning on until after turning off, as Plan 11b's walk-through was. Every step fails and is read without reaching a plug, and nothing is retried after off.

## Units

| Where | dp / sp |
|---|---|
| Tab | the left grid `weight(1f)`, the mode column 380 wide, 15 apart (`PlugsDimens.modeColumn`, `gap`) |
| Plug card | `HhCard` radius 24, padding 20 × 21, 180 high; name 23 sp, status 15 sp `mute`; the button `HhButton` Large, `Primary` while off, `accent` fill card while on, as an on room; `lock` 24 `mute` beside it; › `HhRoundButton` 48 |
| Grid | `gridColumns`'s rule as Lights' (1–4 two a row, more three), 15 apart, scrolls when it overflows |
| Mode section | `HhSectionLabel` title; rows 56 high, chips 48, 8 apart |
| Sheets | `HhSheet`, padding 30 × 28 as Lights' sheets; rows `HhListRow`, `HhSwitchRow` |

## File Structure

```
core/house: HouseSeams.kt (HouseModeInfo, HouseModes, HousePlugs, PlugChange), di/HouseModule.kt              (Task 1)
capability/plugs/ (new): PlugsContract.kt, PlugSettings.kt, PlugsPermissions.kt, PlugsWords.kt, di/PlugsModule.kt;
  core/ui Icons.kt, tools/fonts/icons.txt, the font (power)                                                 (Task 1)
  PlugsHouse.kt                                                                                             (Task 2)
  PlugActions.kt, PlugsCapability.kt                                                                        (Task 4)
  ui/PlugsTab.kt, ui/PlugCard.kt, ui/ModeSection.kt, ui/AddPlugSheet.kt, ui/PlugSettingsSheet.kt, ui/PlugsDimens.kt (Task 5)
provider/plugs-homeassistant/ (new): HaPlugSystem.kt, HaPlugWords.kt, di/HaPlugsModule.kt;
  core/homeassistant test resources ha/plug_states.json, HaJsonTest, HaRedactTest                            (Task 3)
capability/holiday: HolidaySettings.kt (HolidayStep), HolidayHouse.kt, HolidayWords.kt, ui/HolidaySheet.kt,
  di/HolidayModule.kt                                                                                       (Task 6)
app: build.gradle.kts, src/debug di/HouseGraphProbe.kt; settings.gradle.kts; README.md; build-logic tests     (Tasks 1, 3, 7)
docs/superpowers/plans/2026-09-23-plan1-followups.md                                                        (Tasks 9–11)
```

---

### Task 1: The mode seams; `:capability:plugs` 1: the seam, the store, the permissions, the words (spec §4.1, §4.4–§4.6, §5.1, §5.2; rulings 1, 2, 6)

**Review:** sonnet.

**Files:**
- Modify `core/house/.../HouseSeams.kt`, `core/house/.../di/HouseModule.kt`.
- Create `capability/plugs/build.gradle.kts` and, in `capability/plugs/src/main/java/uk/co/siland/culvery/capability/plugs/`: `PlugsContract.kt`, `PlugSettings.kt`, `PlugsPermissions.kt`, `PlugsWords.kt`, `di/PlugsModule.kt` (`@Multibinds Set<PlugSystem>`, `@IntoSet PermissionSource`).
- Modify `settings.gradle.kts`, `app/build.gradle.kts`, README; `core/ui/.../Icons.kt` (`POWER = "power"`), `tools/fonts/icons.txt`, the font; `ModuleBoundariesTest`; `LogHygieneTest` (expects `PlugSettings.kt`).
- Test: `core/house` `HouseSeamsTest`; `PlugSettingsTest`, `PlugsPermissionsTest`, `PlugsWordsTest`; `core/ui` `IconFontTest`; in `capability/plugs/src/test`: `MemoryStore` (as Holiday's), `FakePlugSystem`, `TestAccess` with `PlugsPermissionSource`.

**Interfaces:**
- Produces:
  - Ruling 1's types.
  - `enum class PlugPower { ON, OFF, UNREACHABLE, GONE, UNKNOWN }`.
  - `data class PlugState(val entityId: String, val name: String, val power: PlugPower, val watts: Int?)`.
  - `sealed interface PlugResult { data object Done; data object Unreachable; data object NotSwitched; data object Busy; data class Failed(val line: String) }`.
  - `data class PlugCandidate(val entityId: String, val name: String)`; `sealed interface PlugCandidates { data class Found(val all: List<PlugCandidate>); data class Failed(val line: String) }`.
  - ```kotlin
    interface PlugSystem {
        /** Null with no Home Assistant link: no tab (12 §4.3). The added plugs, in tab order. */
        val plugs: Flow<List<PlugState>?>
        /** The link's §6 line, null when nothing is wrong. */
        val problem: Flow<String?>
        /** The Plugs tab is showing; call the result when it goes. */
        fun watch(): () -> Unit
        suspend fun candidates(): PlugCandidates
        /** One plug as it is now; never throws. */
        suspend fun read(entityId: String): PlugState
        /** No PIN here: the tab asks for it, and a mode's own PIN covers its plugs (12 §5.1). */
        suspend fun switch(entityId: String, on: Boolean): PlugResult
    }
    ```
  - `enum class ModeChoice { LEAVE, ON, OFF }`.
  - `data class Holder(val mode: String, val on: Boolean)`; `data class PlugNote(val before: Boolean, val holders: List<Holder>) { val expected: Boolean }`; `data class ModeRun(val snapshot: Map<String, Boolean>, val pending: Set<String>)`.
  - `data class PlugPrefs(val plugs: List<String> = emptyList(), val needsPin: Set<String> = emptySet(), val modes: Map<String, Map<String, Boolean>> = emptyMap(), val runs: Map<String, ModeRun> = emptyMap(), val notes: Map<String, PlugNote> = emptyMap())`, with `fun choice(modeId: String, id: String): ModeChoice`, `fun with(modeId: String, id: String, choice: ModeChoice): PlugPrefs`, `fun without(id: String): PlugPrefs` (ruling 6), `fun adding(id: String): PlugPrefs` (no duplicate).
  - `@Singleton class PlugSettings(store: DataStore<Preferences>)` (public), `@Inject constructor(@ApplicationContext context)` over `plug_settings`: `val prefs: Flow<PlugPrefs>`; `suspend fun update(change: (PlugPrefs) -> PlugPrefs)`. Writes throw `IOException`.
  - `object PlugsPermissions { const val SWITCH = "plugs.switch"; const val MANAGE = "plugs.manage" }`; `class PlugsPermissionSource @Inject constructor() : PermissionSource`.
  - `PlugsWords` (`object`), the Copy's strings plus:
    - `fun plugsOn(plugs: List<PlugState>): String`;
    - `fun plugStatus(state: PlugState): String`;
    - `fun plugSpoken(state: PlugState): String`;
    - `fun switchSpoken(state: PlugState, needsPin: Boolean): String`;
    - `fun modeRowSpoken(mode: String, name: String, choice: ModeChoice): String`;
    - `fun cantReach(name: String): String`; `fun didntSwitch(name: String): String`;
    - `fun enterLine(missed: List<PlugChange>): String`; `fun leaveLine(missed: List<PlugChange>): String`.

- [ ] **Step 1: The failing tests.**
  - `HouseSeamsTest.aPlugChangeHidesItsName`: `PlugChange("Lamp", true).toString()` has no "Lamp".
  - `PlugSettingsTest` (a store on a file under `context.filesDir`):
    - `everythingSurvivesARestart`: plugs `[switch.lamp, switch.fan]`, `pin {switch.fan}`, holiday `{switch.lamp: false}`, a run with `pending {switch.lamp}`, a note `PlugNote(true, [Holder("holiday", false), Holder("party", true)])` → a new store on the same file reads the same `PlugPrefs`, the list order kept and `expected == true`.
    - `aNewPlugIsLeaveInEveryMode`: `adding("switch.desk").choice("holiday", "switch.desk") == LEAVE`, and for a mode never seen.
    - `settingLeaveRemovesTheEntry`.
    - `removeDropsItEverywhere` (ruling 6): after `without("switch.lamp")` nothing in any field holds it.
    - `malformedEntriesAreSkipped`: `note.switch.lamp = "maybe;x"` and `mode.holiday = {"switch.fan=perhaps"}` → no note, no entry, no throw.
    - `toStringHasNoIds`.
  - `PlugsPermissionsTest`: both ids with their labels verbatim, Admin and Adult, `freshPin`; a `PermissionRegistry` grants neither to `Role.CHILD`.
  - `PlugsWordsTest`:
    - statuses: ON with 182 → "On · 182 W"; ON without → "On"; OFF with 182 → "Off"; UNREACHABLE → "Can’t reach this plug."; GONE → the gone line; UNKNOWN → "Checking…";
    - spoken: "Plug Lamp, on, 182 watts.", "Plug Lamp, off.", "Plug Lamp, can’t reach this plug.", "Plug Lamp, checking."; `switchSpoken` "Switch off", "Switch on, needs a PIN";
    - `modeRowSpoken("Holiday mode", "Lamp", LEAVE)` = "Holiday mode, Lamp, leave alone"; ON "…, on"; OFF "…, off";
    - `plugsOn` with 2 of 4 on = "2 of 4 on · Home Assistant";
    - the two toasts with "Lamp";
    - `enterLine([Lamp off])` = "Lamp didn’t switch off. It’ll keep trying."; two → "2 plugs didn’t switch. They’ll keep trying.";
    - `leaveLine([Lamp on])` = "Lamp didn’t switch back on. Switch it on the Plugs tab."; two → "2 plugs didn’t switch back. Switch them on the Plugs tab.".
  - `ModuleBoundariesTest.thePlugModulesKeepToTheirFamilies` (**pin**): `:capability:plugs` → `:core:house` (`api`) allowed; `:provider:plugs-homeassistant` → `:capability:plugs` and → `:core:homeassistant` allowed; → `:capability:hoover` refused; `:capability:plugs` → `:capability:holiday` refused; `:capability:holiday` → `:capability:plugs` refused.
  - `IconFontTest` covers `power`.

  Run `./gradlew :core:house:testDebugUnitTest :capability:plugs:testDebugUnitTest :core:ui:testDebugUnitTest --tests '*IconFont*'` and `./gradlew -p build-logic :convention:test`. Expected: FAIL (the pin aside).
- [ ] **Step 2: Implement** per the Interfaces and rulings 1, 2 and 6.
- [ ] **Step 3: The gate and the convention tests.** No screenshot changes.
- [ ] **Step 4: Commit.**
```bash
git add core/house core/ui tools/fonts capability/plugs settings.gradle.kts app/build.gradle.kts README.md build-logic
git commit -m "Add the plugs capability's seam, its store of plugs, mode lists and notes, its two permissions and its words, and let modes name themselves to the house"
```

---

### Task 2: `PlugsHouse`: notes, enter, leave (spec §5.1–§5.4; rulings 3–7; Review Focus 1, 2, 4, 5)

**Review:** opus. Put-back, two modes, restarts, a remove racing an enter.

**Files:**
- Create `capability/plugs/.../PlugsHouse.kt`; modify `di/PlugsModule.kt` (`@Binds HousePlugs`); `LogHygieneTest` expects `PlugsHouse.kt`.
- Test: `PlugsHouseTest.kt`, `PlugsHouseTwoModesTest.kt`. `FakePlugSystem` gains scriptable states per id, a queue of `switch` results, `switches: List<Pair<String, Boolean>>` and `reads: List<String>`.

**Interfaces:**
- Consumes: Task 1.
- Produces: `@Singleton class PlugsHouse internal constructor(settings: PlugSettings, system: PlugSystem?) : HousePlugs`, `@Inject constructor(settings, systems: Set<@JvmSuppressWildcards PlugSystem>)`. Logs say only "Plugs {enter|leave}: {n} not switched", with counts.

- [ ] **Step 1: The failing tests.** States are the fake's; "restart" is a new `PlugsHouse` and a new `PlugSettings` on the same file.
  - `PlugsHouseTest`:
    - `enterNotesThenSwitches`: Lamp on, holiday Lamp → Off: the note (before on, holders [holiday off]) is in the store before the fake sees the switch; `switches == [(switch.lamp, false)]`; `Done`.
    - `aPlugAlreadyAsWantedIsNotedNotSwitched`.
    - `leavePlugsAreUntouched`: Fan on Leave → never read or switched.
    - `leavePutsBackToBefore`: then `leave` → `(switch.lamp, true)`, the note and the run gone.
    - `aHandSwitchedPlugIsLeftAndItsNoteDropped`: after enter, Lamp set on by hand → `leave` switches nothing; no note.
    - `aNeverReachedPlugIsLeft`: Lamp `UNREACHABLE` throughout → enter `Failed("Lamp didn’t switch off. It’ll keep trying.")`, no note; `leave` → nothing switched, `Done`.
    - `unreachableAtLeaveIsFailedAndDropped`: → `Failed("Lamp didn’t switch back on. Switch it on the Plugs tab.")`, no note.
    - `aRetrySwitchesOnlyUnreachedPlugs`: Lamp and Fan Off; Fan unreachable at the first enter; the second enter reads and switches only Fan.
    - `aSwitchNotSeenIsTriedAgain` (ruling 3): `NotSwitched` for Lamp → pending, `Failed`; the next enter switches it again and the note's *before* is still on.
    - `aRestartBetweenTheNoteAndTheSwitchKeepsTheFirstBefore` (Review Focus 4): the note written, then the fake throws on `switch` and the house is dropped; a new house, Lamp now off (the command had landed) → no switch, `Done`, before still on.
    - `notesSurviveARestart`: enter, restart, `leave` → puts back.
    - `editingTheListMidModeDoesntChangeWhatIsPutBack`: after enter, holiday Lamp set to Leave and Fan to Off → a retry doesn't touch Fan; `leave` still puts Lamp back.
    - `removeDropsTheNote`: after enter, `without(switch.lamp)` → `leave` switches nothing.
    - `removingAPlugMidModeStopsItsRetries` (Review Focus 2): Lamp unreachable, removed → the next enter doesn't read it, `Done`.
    - `aRemoveDuringEnterIsNotWrittenBack` (Review Focus 2): the fake's `read` of Lamp gated; remove Lamp; release → no note, no pending entry.
    - `aGonePlugAtEnterIsRetriedNotNoted`, `aGonePlugAtLeaveDropsItsNote` (Review Focus 1).
    - `leaveWithHomeAssistantDownDropsEveryNoteAndSaysHowMany` (Review Focus 5): two plugs held, both `UNREACHABLE` at leave → `Failed("2 plugs didn’t switch back. Switch them on the Plugs tab.")`, no notes, no run.
    - `leaveWaitsForARunningEnter`: an enter gated mid-switch; `leave` called → it returns only after enter ends, then puts back.
    - `enterTakesNoGrant` (**pin** on the signature): a Lamp with `needsPin` is switched by enter.
    - `readyFollowsTheModesList`: false with all Leave; true once Lamp is Off in holiday; still false for `"party"`; false with no `PlugSystem`.
    - `previewListsOnlyOnAndOff`: Lamp Off, Fan Leave, Desk On → `[PlugChange("Lamp", false), PlugChange("Desk", true)]`, in tab order.
  - `PlugsHouseTwoModesTest` (a second mode `"party"` standing in for Party mode), each with Lamp before = off:
    - `sameSettingAThenBEndingAThenB`: both On → while B alone holds, Lamp on; after both, off.
    - `sameSettingEndingBThenA`.
    - `oppositeSettingsEndingAThenB`: A On, B Off → Lamp off while both; ending A switches nothing (B was newest); ending B → back to off, the before from A.
    - `oppositeSettingsEndingBThenA`: ending B → Lamp switched to A's On; ending A → off.
    - `aHandChangeWinsOverBothModes`: both hold, Lamp switched by hand → ending A drops the whole note; ending B switches nothing.

  Run `./gradlew :capability:plugs:testDebugUnitTest --tests '*PlugsHouse*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 3–7.
- [ ] **Step 3: The gate and the convention tests**, then commit.
```bash
git add capability/plugs build-logic
git commit -m "Let modes switch plugs and put them back: one note per plug remembers its state from before the first mode, and the last mode to end puts it back unless someone switched it by hand"
```

---

### Task 3: `:provider:plugs-homeassistant` (spec §4.1, §6, §7; ruling 8; Review Focus 1, 3)

**Review:** opus. State mapping, watts, one switch per plug, the 30-second check.

**Files:**
- Create `provider/plugs-homeassistant/build.gradle.kts`; register it. In `provider/plugs-homeassistant/src/main/java/uk/co/siland/culvery/provider/plugs_homeassistant/`: `HaPlugSystem.kt`, `HaPlugWords.kt`, `di/HaPlugsModule.kt` (`@IntoSet PlugSystem`).
- Create `core/homeassistant/src/test/resources/ha/plug_states.json`: `switch.lamp` on ("Lamp"), `switch.fan` off, `switch.fairy_lights` `unavailable`, `switch.lamp_led` on ("Lamp LED"), `sensor.lamp_current_consumption` `"182"` with `unit_of_measurement` `"W"` and `device_class` `"power"`, no sensor for Fan. Shapes from Home Assistant's REST docs; Task 10 replaces them with redacted captures.
- Modify `LogHygieneTest`: expects `HaPlugSystem.kt`.
- Test: `core/homeassistant` `HaJsonTest`, `HaRedactTest`; `HaPlugSystemTest.kt`, `HaPlugWireTest.kt` (MockWebServer).

**Interfaces:**
- Consumes: Task 1; `HomeAssistant` (`current`, `call`, `awaitEntity`, `states`, `watch`, `link`, `entities`, `problem`, `savedLink`), `CONFIRM_FOR_MS`, `NOT_LINKED`, `problemLine`, the fixtures (`testHomeAssistant`, `FakeHomeAssistantApi`, `entityState`, `TEST_LINK`).
- Produces:
  - `internal fun wattsIdOf(switchId: String): String`; `internal fun powerOf(state: String): PlugPower`.
  - `const val GONE_PLUG` (the Copy's gone line) in `HaPlugWords.kt`.
  - `internal fun plugStates(prefs: PlugPrefs, linked: Boolean, entities: Map<String, EntityState?>, problem: HaProblem?): List<PlugState>?` (pure; ruling 8).
  - `@Singleton class HaPlugSystem internal constructor(ha: HomeAssistant, prefs: Flow<PlugPrefs>)`, `@Inject constructor(ha, settings: PlugSettings)` `: PlugSystem`. Log lines: "switch failed (Type)", "the plug didn't switch", "candidates failed (Type)".

- [ ] **Step 1: The failing tests.**
  - `HaJsonTest.plugStatesRead`: `states(plug_states.json)` gives Lamp `on` with `friendlyName` "Lamp", Fairy lights `unavailable`, the sensor `"182"` with unit `"W"`.
  - `HaRedactTest.plugNamesGo`: with `keep` = the three plug switches and the sensor, none of "Lamp", "Fan", "Fairy"; kept `"on"`, `"off"`, `"unavailable"`, `"W"`, `"power"`, `182`, `"switch.[1]"`; nothing of `switch.lamp_led`.
  - `HaPlugSystemTest` (the fixtures, virtual time):
    - `noLinkIsNull`: `plugs.first() == null`; with a link and no plugs added → empty list.
    - `eachStateReads`: Lamp `ON` 182 W, Fan `OFF` null watts, Fairy lights `UNREACHABLE`, in the store's order.
    - `theWattsSensorIsFoundByName`: `wattsIdOf("switch.lamp") == "sensor.lamp_current_consumption"`.
    - `oddWattsReadSafely`: `"unavailable"`, `"-3"`, `"abc"` → null; `"0.4"` → 0; Lamp off with `"182"` → null.
    - `aDeletedPlugIsGone` (Review Focus 1): the id null in the cache → `GONE`, the name the entity id.
    - `oddStatesAreUnknown`: `"unknown"` and an id not yet cached → `UNKNOWN`.
    - `aLinkProblemMakesEveryPlugUnknown`: `failNext = TOKEN_REFUSED` then a read → every plug `UNKNOWN`, `problem` the token line.
    - `nonPlugSwitchesAreOnlyCandidates`: `switch.lamp_led` isn't in `plugs` unless added; `candidates()` is `Found` with all four switches and no sensor.
    - `candidatesWithHomeAssistantDownSayWhy`: `failAll = UNREACHABLE` → `Failed("Can’t reach Home Assistant. Check the NAS is on.")`.
    - `switchSendsTheServiceAndWaitsForTheChange`: Fan off; `onService` turns it on from the second read → `"service switch/turn_on switch.fan"`, `Done` at 10 s, not at 9 999 ms.
    - `aPlugThatDoesntChangeIsNotSwitched`: `NotSwitched` at 30 s, not before.
    - `aLiveChangeCountsBeforeTheNextRead`: the socket emits `on` at 2 s → `Done` at 2 s.
    - `alreadyAsWantedSendsNothing`.
    - `unavailableIsUnreachableAndSendsNothing`; `aGonePlugSaysSo` (`Failed(GONE_PLUG)`); `noLinkSendsNothing` (`Failed(NOT_LINKED)`, `calls` empty).
    - `aRefusedServiceIsNotSwitched`: `REFUSED` → `NotSwitched`, `ha.problem` null.
    - `aSecondSwitchOfOnePlugIsBusy` (Review Focus 3): two switches of Fan at once → one service call, the second `Busy`; Lamp alongside is not `Busy`; the lock is released after cancel.
    - `readNeverThrows`: `failAll = UNREACHABLE` → `UNREACHABLE` with the cached name.
    - `watchFollowsThePlugsAndTheirWatts`: `follows.last() == {switch.lamp, sensor.lamp_current_consumption, switch.fan, sensor.fan_current_consumption}`.
  - `HaPlugWireTest` (a real `HomeAssistantClient` on the Robolectric context, `HomeAssistantLink(server.url("/"), "test-token")`, MockWebServer):
    - `onAndOffSendTheirPathsAndBodies`: `POST /api/services/switch/turn_on` and `/turn_off`, each with body `{"entity_id":"switch.fan"}` and `Authorization: Bearer test-token`.
    - `aDelayedAnswerIsUnreachable`: on a client built as `HomeAssistantClientTest`'s short one, a service delayed past it → `Failed("Can’t reach Home Assistant. Check the NAS is on.")`. The five-second figure itself is pinned in `:core:homeassistant`.
    - `nothingPersonalReachesTheLog` (`ShadowLog`): after a 500, a timeout and a `NotSwitched`, no line holds "switch.", "Lamp", "Fan" or "182".

  Run `./gradlew :core:homeassistant:testDebugUnitTest --tests '*HaJson*' --tests '*HaRedact*' :provider:plugs-homeassistant:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per ruling 8.
- [ ] **Step 3: The gate and the convention tests**, then commit.
```bash
git add core/homeassistant provider/plugs-homeassistant settings.gradle.kts app/build.gradle.kts README.md build-logic
git commit -m "Read and switch the plugs through Home Assistant, with the watts from the sensor named after each plug and a 30-second check that a switch took"
```

---

### Task 4: `PlugActions` and the capability (spec §4.2–§4.4; rulings 9, 10; Review Focus 3)

**Review:** opus (access).

**Files:**
- Create `capability/plugs/.../PlugActions.kt`, `PlugsCapability.kt`; modify `di/PlugsModule.kt` (`@IntoSet Capability`); `core/plugin/.../Capability.kt` (the `order` KDoc adds "Plugs 55"); `LogHygieneTest` expects `PlugActions.kt`.
- Test: `PlugActionsTest.kt`, `PlugsCapabilityTest.kt`.

**Interfaces:**
- Consumes: Tasks 1–2; `AccessControl`, `Toaster`, `@ApplicationScope`.
- Produces:
  - `@Singleton class PlugActions internal constructor(access: AccessControl, settings: PlugSettings, system: PlugSystem?, toaster: Toaster, scope: CoroutineScope)`, `@Inject` over `Set<PlugSystem>` and `@ApplicationScope`:
    - `val switching: StateFlow<Set<String>>`;
    - `fun switch(plug: PlugState, needsPin: Boolean)`: ignored unless `plug.power` is `ON` or `OFF` and the plug isn't switching; with `needsPin`, `access.authorise(PlugsPermissions.SWITCH)` first (null → nothing); then `system.switch(plug.entityId, plug.power == OFF)`; the toast per result (`Unreachable` → `cantReach`, `NotSwitched` → `didntSwitch`, `Failed` → its line, `Done` and `Busy` none);
    - `suspend fun mayManage(): Boolean` (`authorise(PlugsPermissions.MANAGE) != null`);
    - `suspend fun add(entityId: String)`, `suspend fun remove(entityId: String)`, `suspend fun setNeedsPin(entityId: String, on: Boolean)`, `suspend fun setChoice(modeId: String, entityId: String, choice: ModeChoice)`: store updates; a write that throws logs and toasts nothing (the tab shows the store as it is).
  - `@Singleton class PlugsCapability @Inject constructor(systems: Set<@JvmSuppressWildcards PlugSystem>, actions: PlugActions, settings: PlugSettings, modes: Set<@JvmSuppressWildcards HouseModeInfo>) : Capability`, per ruling 10, `cards()` empty, `TabContent()` empty until Task 5.

- [ ] **Step 1: The failing tests.**
  - `PlugActionsTest` (`TestAccess`, `FakePlugSystem`, a recording `Toaster`, virtual time):
    - `aPlainPlugSwitchesWithNoPin`: `asked` empty, `switches == [(switch.fan, true)]`.
    - `aPinPlugAsksForAFreshPin`: `TestAccess(ADULT)` → `asked == [["plugs.switch"]]`, then the switch; `ADMIN` likewise.
    - `aChildsPinSwitchesNothing`; `aCancelledPadSwitchesNothing`.
    - `everySwitchAsksAgain`: two switches of a PIN plug → two asks.
    - `aSecondTapWhileSwitchingDoesNothing` (Review Focus 3): the fake gated → `switching == {switch.fan}`, a second `switch` adds no ask and no call; released → empty.
    - `anUnreachablePlugIsNotSwitched` (Review Focus 3): `UNREACHABLE`, `GONE` and `UNKNOWN` → nothing asked or called.
    - `theTargetIsTheOppositeOfTheStateShown`: `ON` → `false`.
    - `eachResultHasItsToast`: `Unreachable` → "Can’t reach Fan. Check it’s plugged in and the Tapo app can see it."; `NotSwitched` → "Fan didn’t switch. Try again, or use the Tapo app."; `Failed("x")` → "x"; `Done` → none.
    - `manageAsksForAFreshPinAndRefusesAChild`: `asked == [["plugs.manage"]]`; `CHILD` → false.
    - `addRemovePinAndChoiceReachTheStore`: `add` twice keeps one; `setChoice(…, LEAVE)` removes the entry; `remove` drops it from every list.
  - `PlugsCapabilityTest`:
    - `theTabShowsOnceLinked`: the fake's `plugs` null → `hasTab` false; empty list → true.
    - `noSystemNoTab`.
    - `itSitsAfterTheDoor`: `order == 55`, icon `power`, label "Plugs", `cards()` empty.

  Run `./gradlew :capability:plugs:testDebugUnitTest --tests '*PlugActions*' --tests '*PlugsCapability*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces and rulings 9–10.
- [ ] **Step 3: The gate and the convention tests**, then commit.
```bash
git add capability/plugs core/plugin build-logic
git commit -m "Switch a plug by hand, asking a fresh Adult or Admin PIN only for plugs set to need one, and put set-up behind its own PIN"
```

---

### Task 5: The Plugs tab (spec §4.2, §4.3, §4.6; ruling 10)

**Review:** sonnet.

**Files:**
- Create in `capability/plugs/.../ui/`: `PlugsTab.kt` (`PlugsTab` stateless, `PlugsTabHost`), `PlugCard.kt`, `ModeSection.kt`, `AddPlugSheet.kt`, `PlugSettingsSheet.kt`, `PlugsDimens.kt`.
- Modify `PlugsCapability.kt` (`TabContent()` = `PlugsTabHost(…)`).
- Test: `ui/PlugsTabTest.kt`, `ui/PlugCardTest.kt`, `ui/ModeSectionTest.kt`, `ui/AddPlugSheetTest.kt`, `ui/PlugSettingsSheetTest.kt`, `ui/PlugsScreenshotTest.kt`; `FakePlugSystem` gains `watchers: Int`.

**Interfaces:**
- Consumes: Tasks 1, 4; `HhPageHeader`, `HhCard`, `HhButton`, `HhRoundButton`, `HhChoiceChip`, `HhSectionLabel`, `HhSheet`, `HhListRow`, `HhSwitchRow`, `HhIcon`, `LocalOverlayHost`, `rememberSingleAction`.
- Produces:
  - `@Composable internal fun PlugsTab(plugs: List<PlugState>, needsPin: Set<String>, switching: Set<String>, problem: String?, modes: List<HouseModeInfo>, prefs: PlugPrefs, editing: Boolean, onSwitch: (PlugState) -> Unit, onSettings: (PlugState) -> Unit, onAdd: () -> Unit, onEdit: () -> Unit, onDone: () -> Unit, onChoice: (modeId: String, entityId: String, ModeChoice) -> Unit)`.
  - `PlugsTabHost(system, actions, settings, modes)`: collects the flows, `system.watch()` in a `DisposableEffect`, `editing` in plain `remember` (ruling 10's three ends); Add and › ask `actions.mayManage()` through `rememberSingleAction`, then `LocalOverlayHost.current.show { … }`.
  - `AddPlugSheet(candidates: PlugCandidates?, added: List<String>, onPick: (PlugCandidate) -> Unit, onClose: () -> Unit)`: `null` while reading; lists `Found` minus `added`, by name; picking adds and closes.
  - `PlugSettingsSheet(plug: PlugState, needsPin: Boolean, onNeedsPin: (Boolean) -> Unit, onRemove: () -> Unit, onClose: () -> Unit)`: the plug's name as title; "Needs a PIN to switch"; "Remove" removes and closes.
  - Tags: `plugs_tab`, `plugs_add`, `plugs_empty`, `plugs_line`, `plugs_edit`; `plug_card_{i}`, `plug_card_{i}_status`, `plug_card_{i}_switch`, `plug_card_{i}_lock`, `plug_card_{i}_settings`; `mode_{modeId}`, `mode_{modeId}_{i}`, `mode_{modeId}_{i}_leave|on|off`; `add_plug_sheet`, `add_plug_{i}`, `add_plug_none`; `plug_settings_sheet`, `plug_settings_pin`, `plug_settings_remove`.
- Behaviour:
  - Header: "Plugs", `plugsOn` (none with no plugs), Add a plug on the right as an `HhButton` with `add`. A `problem` shows under it in `danger`.
  - Each card is one semantics group spoken `plugSpoken`. Its button is spoken `switchSpoken`: "Switching…" while switching (taps ignored), disabled for `UNREACHABLE`, `GONE` and `UNKNOWN`. An on card has the accent fill. The lock shows when `needsPin`, hidden from TalkBack (the button says it). The › is spoken "Settings for {name}".
  - Mode sections are sorted by `order`. Rows are in tab order: name, then the chosen word at rest; in Edit, three chips `Role.RadioButton` in a `selectableGroup`, each spoken `modeRowSpoken`.

- [ ] **Step 1: The failing tests.** Screen tests end with `assertClickablesHaveRoleAndLabel()`.
  - `PlugsTabTest`:
    - `noPlugsSaysHowToAdd`: "No plugs yet. Add one from Home Assistant.", `plugs_add` "Add a plug", no `mode_holiday`.
    - `theHeaderCountsWhatsOn`: "2 of 4 on · Home Assistant".
    - `aProblemShowsUnderTheHeader`.
    - `aPlainPlugSwitchesOnTap`: `onSwitch` with Fan once.
    - `addAsksBeforeTheSheet` (host, `TestAccess`): Add a plug → `asked == [["plugs.manage"]]` then `add_plug_sheet`; `CHILD` → no sheet.
    - `settingsAskBeforeTheSheet`: likewise for the ›.
    - `editAsksForAPin`: Edit → asked, then chips; a Child → no chips.
    - `editEndsOnDoneOrAfterTwoIdleMinutes` (`compose.mainClock`): chips at `EDIT_IDLE_MS − 1`, gone at `EDIT_IDLE_MS`; Done ends it at once.
    - `theTabWatchesWhileShown`: the fake's watchers 1 while composed, 0 after.
  - `PlugCardTest`:
    - `anOnPlugReadsAsTheSpecSays`: "Lamp", "On · 182 W"; the group "Plug Lamp, on, 182 watts."; `plug_card_0_switch` "Switch off", a button.
    - `anOffPinPlugSaysItNeedsAPin`: "Switch on, needs a PIN"; `plug_card_0_lock` shown.
    - `anUnreachablePlugsButtonIsInactive`: "Can’t reach this plug."; spoken "Plug Fairy lights, can’t reach this plug."; a tap calls nothing.
    - `aGonePlugSaysRemoveItOrAddItAgain`.
    - `switchingShowsUntilTheStateComes`: "Switching…" and taps ignored.
  - `ModeSectionTest`:
    - `atRestARowIsReadOnly`: "Lamp" and "Off"; no node with a click action in `mode_holiday`.
    - `editOffersLeaveOnOff`: three chips, "Off" selected; `mode_holiday_0_on` spoken "Holiday mode, Lamp, on"; a tap → `onChoice("holiday", "switch.lamp", ON)`.
    - `aSecondModeIsASecondSection`: modes holiday (10) and party (20) → two sections in that order, nothing else differs.
  - `AddPlugSheetTest`: `listsOnlySwitchesNotYetAdded` (Lamp added → Fan, Desk, Lamp LED shown, not Lamp); `pickingAddsAndCloses`; `nothingToAddSaysSo` ("Home Assistant has no other switches."); `aFailureShowsItsLine`.
  - `PlugSettingsSheetTest`: `thePinSettingSwitches`; `removeRemovesAndCloses`.
  - `PlugsScreenshotTest` (each `_dark` and `_light`): `plugs_tab_empty`, `plugs_tab_four` (Lamp on 182 W, Fan off with PIN, Fairy lights unreachable, Desk switching; the Holiday section at rest), `plugs_tab_edit`, `plugs_add_sheet`, `plugs_settings_sheet`.

  Run `./gradlew :capability:plugs:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces, Copy and Units.
- [ ] **Step 3: Images.** **New:** the 10 above. **Check against** the brainstorm mockup v2 (cards left, mode sections right, Leave / On / Off) and the Lights tab's header and room cards.
```bash
git add capability/plugs
git commit -m "Add the Plugs tab: a card per plug that switches it, a section per mode with Leave, On or Off for each plug, and Add a plug and each plug's settings behind a PIN"
```

---

### Task 6: Holiday mode switches plugs (spec §5.1, §5.5, §6; ruling 11; Review Focus 5)

**Review:** opus. The step's retries and restarts, and turning off.

**Files:**
- Modify in `capability/holiday/.../`:
  - `HolidaySettings.kt` (`PLUGS_ON`);
  - `HolidayHouse.kt` (the `HousePlugs?` constructor argument, `plan`, `perform`, `window`, `turnOff`, `suspend fun plugChanges(): List<PlugChange>`);
  - `HolidayWords.kt` (`stepLine(PLUGS_ON) = null`; `missedLine(PLUGS_ON) = null`; `failedLine(PLUGS_ON)` = the threw line; `fun willLines(steps: List<HolidayStep>, back: LocalDate, plugs: List<PlugChange>): List<String>`);
  - `ui/HolidaySheet.kt` (takes `plugs: List<PlugChange>`, shows `willLines`; the host reads `plugChanges()` with `plan`);
  - `di/HolidayModule.kt` (a companion `@Provides @IntoSet HouseModeInfo(HouseModes.HOLIDAY, HolidayWords.LABEL, 10)`).
- Test: `HolidayHouseTest`, `HolidayWordsTest`, `ui/HolidaySheetTest`, `ui/HolidayScreenshotTest`; `TestHouse.kt` gains `FakePlugs : HousePlugs` (scriptable `ready`, `preview`, queues of `enter` and `leave` results, `calls`).

- [ ] **Step 1: The failing tests.**
  - `HolidayHouseTest`:
    - `thePlugsStepRunsAfterEco`: the calls in order `setHoliday(true)`, `setEco(true)`, `enter("holiday")`.
    - `noPlugsReadyNoStep`: `ready` false → no `PLUGS_ON` in `plan` or the record, `enter` never called; `leave` still called at turn-off.
    - `aFailedPlugsStepIsRetriedEveryFiveMinutes`: `Failed("Lamp didn’t switch off. It’ll keep trying.")` → the tile line; no retry at `RETRY_MS − 60_000`, one at `RETRY_MS`; `Done` → `DONE`, the line clears.
    - `aFailedPlugDoesntStopHolidayMode`: the hoover and Eco off still run on time.
    - `aPendingPlugsStepIsRetriedAfterARestart`: a new house over a record with `PLUGS_ON` pending → `enter("holiday")` at the first tick, with no grant (unlike the lights step).
    - `plugsGoBackAtTapOff`: `turnOff` → `leave("holiday")` after `setEco(false)`, before the record is cleared; nothing at 00:01.
    - `turningOffWithPlugsFailingStillEnds` (Review Focus 5): `leave` → `Failed("Lamp didn’t switch back on. Switch it on the Plugs tab.")` → the record cleared, the off line that line for `OFF_LINE_MS`; with Eco also failing, the Eco line wins.
    - `oldRecordsStillRead` (**pin**): a record saved with steps `LIGHTS_ON`, `ECO_ON`, `HOOVER`, `ECO_OFF` reads unchanged.
  - `HolidayWordsTest.willLinesPutThePlugsAfterTheHeating`: steps `[LIGHTS_ON, ECO_ON, PLUGS_ON, HOOVER, ECO_OFF]` with Lamp off and Desk on → the lights line, the Eco line, "Switch Lamp off.", "Switch Desk on.", "Plugs go back as they were when you turn it off.", the hoover line, then the door and screen lines; with no plug changes, no plug lines.
  - `HolidaySheetTest.thePlugLinesShow`: with `plugs = [Lamp off]`, `holiday_steps` holds "Switch Lamp off." and "Plugs go back as they were when you turn it off.".
  - `HolidayScreenshotTest`: `holiday_sheet_plugs`, `_dark` and `_light`.

  Run `./gradlew :capability:holiday:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per ruling 11.
- [ ] **Step 3: Images.** **New:** the 2 above. **Must not change:** every other `holiday_*` image. Then the gate, and commit.
```bash
git add capability/holiday
git commit -m "Let Holiday mode switch the plugs in its list after the heating, keep trying any it couldn't reach, and put them back when it's turned off"
```

---

### Task 7: The app wired in

**Review:** sonnet.

**Files:** `app/build.gradle.kts` (both new modules, if Tasks 1 and 3 didn't), `app/src/debug/.../di/HouseGraphProbe.kt` (`plugs(): Optional<HousePlugs>`, `modes(): Set<HouseModeInfo>`), `app/src/test/.../HouseGraphTest.kt`, README (the two module rows; `:core:house`'s row names `HousePlugs` and `HouseModeInfo`; "Adding a capability" names Plugs 55 if it lists rail orders).

- [ ] **Step 1: The failing tests.**
  - `HouseGraphTest.thePlugsAreInTheHouse`: `plugs()` is present; `modes()` holds `holiday`.
  - `ShellViewModelTest.plugsSitAfterTheDoor` if the test builds the real capability list; else skip and say so.
  - `NoBackupTest` (**pin**: `plug_settings` is under `files/datastore/`).
- [ ] **Step 2:** Wire, then run `ShellScreenshotTest`: no image may change (the shell's tests build their own capabilities). If one does, stop and ask.
- [ ] **Step 3:** The gate and the convention tests, then commit.
```bash
git add app README.md
git commit -m "Wire the Plugs tab and the Home Assistant plugs into the app"
```

---

### Task 8: Simplify the whole branch

- [ ] **Step 1:** Run the `simplify` skill over `git diff main...HEAD`. Look hardest at `PlugsHouse`'s enter and leave (one read-then-decide helper if they read alike), `plugStates` beside `hooverState`, and the store's encoding.
- [ ] **Step 2:** For each change, run the affected module's tests, then the gate.
- [ ] **Step 3:** Commit when anything changed: `git commit -am "Simplify the plugs code"`.

---

### Task 9: The emulator walk-through on `culvery-5`; the device checks and captures (spec §3, §8.1, §8.2)

**Rules** (as Plan 11a's Task 11 and 11b's Task 10):
- Use only the `culvery-5` AVD and a debug build with `install -r`; it keeps Plan 11's Home Assistant token.
- The user types the real PINs; Claude enters the debug sample PINs.
- Collect faults and ask before fixing.
- **No plug is switched, from the tab or by Holiday mode, without the user's go each time.**
- Entity ids, names, addresses and watt figures never go into a file or commit; the captures and the user's list stay in `$LISTS`.

- [ ] **Step 0: Set-up in Home Assistant** (the user, spec §3): the TP-Link Smart Home integration with each plug (by IP where discovery misses it, the router plug included), fixed addresses on the router, whether "Third-Party Compatibility" was needed, and the Culvery user seeing the entities. Note the real switch and power-sensor names in `$LISTS`; **check that the power sensor is `sensor.<switch object id>_current_consumption`** (ruling 8). If it isn't, stop: fix `wattsIdOf` with a failing test first, on the user's go.
- [ ] **Step 1: Build, install, walk** (one line each).
  1. Settings › Other integrations is unchanged, with no plug in it.
  2. The rail shows Plugs after Door; "No plugs yet…"; Add a plug: a Child's PIN refused; an Adult's opens the list of switches, the LED switches among them; add each plug.
  3. The cards: names, states, the P110's watts; the router plug offered like the others; TalkBack on a card and a mode row.
  4. A plug's › (PIN): Needs a PIN to switch on; the padlock shows; Remove one and add it back.
  5. Edit (PIN): Leave / On / Off for the Holiday section; Done; the two-minute end.
  6. Switching by hand: on the user's go only, one plug, then a PIN plug with a Child's PIN refused.
  7. Holiday mode with a plug on Off, **airplane mode on before turning on** (ruling 12): the sheet's "Switch {name} off." and the go-back line; turn on; the tile's "didn't switch… It'll keep trying."; turn off; the tile's "didn't switch back…" line; **only then** airplane mode off. Nothing is retried after off.
- [ ] **Step 2: The captures** (§8.2.3): `adb -s "$S" logcat -c`; `setprop log.tag.HaCapture DEBUG`; open the Plugs tab and Add a plug; `adb -s "$S" logcat -d -s HaCapture:D > "$LISTS/plugs-capture.txt"`; `setprop log.tag.HaCapture INFO`. **The names check:** grep the file for the address, the token's first 12 characters, every entity id and friendly name on the user's list: no match, else widen `HaJson.redact` (failing case first) and recapture.
- [ ] **Step 3: The fault loop**, put back every setting, `emu kill`.
- [ ] **Step 4: Record** under "Plan 12 (smart plugs): emulator walk-through", with no ids, names or watts. Commit: `git commit -am "Record Plan 12's emulator walk-through"`.

---

### Task 10: Fixtures from the captures; on the tablet (spec §7, §8.2, §8.3)

**Needs the SM-T510 on the cable.** 4e's tablet rules apply: a signed release with `install -r`, never uninstall; save and restore every setting changed; the names check before any commit.

- [ ] **Step 1: Fixtures (failing first).** Rewrite `ha/plug_states.json` from `$LISTS/plugs-capture.txt` with the sample ids and names. Run `./gradlew :core:homeassistant:testDebugUnitTest :provider:plugs-homeassistant:testDebugUnitTest`. Expected: FAIL where the shapes differ (else say so). Fix with the user's go, the gate, the names check, then commit: `git commit -am "Replace the plug fixtures with redacted answers from the household's instance"`.
- [ ] **Step 2: Install the release** (mapping kept in `culvery-notes/mappings/`).
- [ ] **Step 3: With the user:** add the plugs on the Plugs tab; the states and watts match the Tapo app; switch one only on the user's go. Holiday mode with plugs is not turned on for real: its first real use is the check (spec §8.3).
- [ ] **Step 4: The log scan.** `adb -s <tablet> logcat -d -s HaPlugSystem PlugsHouse PlugActions PlugSettings HolidayHouse > "$LISTS/plan12-log.txt"`, then grep it for the entity ids, names and watt figures: no match.
- [ ] **Step 5: Record** under "Plan 12: on the tablet". Commit: `git commit -am "Record Plan 12 on the tablet"`.

---

### Task 11: Final review, merge, install

- [ ] **Step 1:** Opus reviews the whole branch against the spec and this plan, looking first at the put-back rules and two modes, the PINs, and the logs. Then one fix wave, each fix with its failing test first, and the gate.
- [ ] **Step 2:** Finish with `superpowers:finishing-a-development-branch`: on the user's say, `git merge --ff-only plan-12-smart-plugs` on `main`, then the gate on `main`. Never push without asking. Move the design cuts to the follow-ups, and add to "Feature ideas" the rail redesign (§2.9) and plugs joining Holiday lights (§2.10) if they aren't there.
- [ ] **Step 3:** Install the release from the merged `main` on the tablet with `install -r`.

---

## Walk-through

1. Settings › Other integrations holds only the Home Assistant link.
2. Plugs is on the rail after Door, once Home Assistant is linked; "No plugs yet…" before any are added.
3. Add a plug lists every switch not yet added (LED switches included), behind an Adult or Admin PIN; a Child's PIN is refused.
4. Each card shows the name, "On · {n} W" / "On" / "Off" / "Can’t reach this plug.", and switches with no PIN, or with a fresh one when set to need it.
5. A plug's › opens Needs a PIN to switch and Remove, behind a PIN.
6. The Holiday section: read-only at rest; Edit (PIN) for Leave / On / Off; Done or two idle minutes ends it.
7. Holiday mode's sheet lists the plug changes and "Plugs go back as they were when you turn it off."
8. Holiday mode in airplane mode: the tile's keep-trying line, then at tap-off the switch-back line; nothing retried after off.
9. TalkBack reads a card and a mode row as the spec says.

**Design cuts to say again:** collected in the build (the card size, the mode column's width, the › rather than a long press, the button showing the action).

## Not in this build, on purpose

- Party mode and its section on the Plugs tab (the next plan binds a `HouseModeInfo` and calls `enter` / `leave`).
- Plugs joining Holiday lights.
- The rail redesign.
- Plug buttons in the One-tap tray, energy history beyond the current watts, schedules or timers per plug.

---

## Spec coverage (12 → tasks)

| Spec | Task |
|---|---|
| §2.1 no plug assumed, §2.2 Settings unchanged, §2.3 the tab | 3, 4, 5, 9 |
| §2.4 Leave default, §2.5 by mode, §2.8 the PIN setting | 1, 5 |
| §2.6 put back, §2.11 the mode's PIN covers its plugs, §2.12 two modes | 2, 6 |
| §2.7 Holiday only, lists ready for Party | 1, 2, 5, 6 |
| §3 set-up in Home Assistant | 9 |
| §4.1 modules, switching, state, watts by name, updates | 1, 3 |
| §4.2 which plugs, Add, Remove, live names | 3, 4, 5 |
| §4.3 the tab | 4, 5 |
| §4.4 permissions | 1, 4, 5 |
| §4.5 the store, no backup | 1, 7 |
| §4.6 TalkBack | 1, 5 |
| §5.1 `HouseModeInfo`, `HousePlugs`, no grant | 1, 2, 6 |
| §5.2–§5.4 notes, enter, leave, Remove | 2 |
| §5.5 Holiday's part | 6 |
| §6 failure lines, privacy | 1–6, 10 |
| §7 tests | 1–6 |
| §8.1–§8.3 rollout | 9, 10, 11 |
