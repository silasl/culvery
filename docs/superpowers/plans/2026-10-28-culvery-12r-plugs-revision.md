# Culvery — Plan 12r: the plugs revision Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–6 are subagent tasks (sonnet implementers, a reviewer per task; **opus** reviews Tasks 1 and 2). Task 7 is the controller. Task 8 is the controller on the `culvery-5` AVD with the user. After Task 8, Plan 12's own Tasks 9–10 (tablet, final review, merge) run as written there, against this plan's result.

**Goal:** Plan 12 as built (817583bf) changed to the revised spec: plugs are chosen in Settings › Other integrations from Home Assistant's TP-Link plugs only, the Plugs tab is one row per plug with its mode choices, "Leave" is "Ignore", and the six walk-through faults are fixed.

**Architecture:**
- **`:core:homeassistant`** gains a one-shot registry read (`config/entity_registry/list_for_display` over its own short socket), `liveSince` (when the open socket last started reporting), a `HomeAssistantSection` seam that Settings › Other integrations renders below its device pickers, and a capture rule that blanks power and energy readings.
- **`:provider:plugs-homeassistant`** decides which entities are plugs, and finds their watts, from the registry, and calls a missing plug gone only after 2 minutes of steady socket.
- **`:capability:plugs`**: the store shows and hides plugs instead of adding and removing them (choices kept), `ModeChoice.LEAVE` becomes `IGNORE`, the tab is rows with mode columns, the Add and settings sheets go, and a `PlugsSettingsSection` binds into Settings.
- **`:capability:holiday`**: at tap-off the tile's line holds every failure, one per line.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1, DataStore 1.2.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-27-culvery-12-smart-plugs-design.md` as revised at 03214056: decisions 2, 4, 5, 15–19; §3, §4.1–§4.6, §5.4, §5.5, §6, §7, §8. Everything else in it is built and stays.

**Previous plan:** `docs/superpowers/plans/2026-10-27-culvery-12-smart-plugs.md` (Tasks 1–8 done; its ledger in `.superpowers/sdd/2026-10-27-culvery-12-smart-plugs/progress.md` holds the walk-through faults and rulings). This plan has its own SDD workspace; read that ledger's Task 8 lines once.

**Builds on:** branch `plan-12-smart-plugs` at 03214056, the one checkout `C:\Users\si\code\siland\culvery`. No worktrees. Never push.

## Global Constraints

**Every house rule in Plan 12's Global Constraints still binds** (platform, the test gate and its ban on TMP/TEMP, `$LISTS` = `C:/t9`, tests that fail first or are labelled **pin**, the UI-task ending with Roborazzi and the baseline check, `PlugsDimens`, comments, `LogHygieneTest`, privacy, devices, git). Read that section once; it is not repeated here. Its copy list is replaced by the one below where they differ.

**From the revised spec, verbatim:**
- §2.4 "**Per mode, each plug is Ignore, On or Off.** Ignore is the default for a newly shown plug and for a newly added mode".
- §2.16 "Culvery lists the TP-Link integration's main switch per plug and nothing else". "This filters by integration, not by name or model".
- §2.17 "A plug's own schedule in the Tapo app … counts as a hand change" (already true of the built put-back; tests pin it).
- §4.1 "A plug is an entry with platform (`pl`) `tplink`, domain `switch` and no entity category (`ec`). Its watts are the `sensor` entry with platform `tplink` on the same device (`di`) whose translation key (`tk`) is `current_consumption`; none, no watts."
- §4.2 "**Show on the Plugs tab** (off for a plug Culvery hasn't seen before …) and **Needs a PIN to switch** (off by default; usable only while the plug is shown)." Turning Show off: "Its mode choices and PIN setting are **kept** in the store"; "a running mode's note for it is dropped and the plug is left as it is". Settings is admin-only, so no extra PIN on these switches.
- §4.3 the tab "shows **once at least one plug is shown**"; "{n} counts only plugs whose state is known; with none known, just "Home Assistant""; "**No button** when the plug can't be switched: unreachable, gone, or its state unknown"; "Switching…" shows only once the PIN is accepted.
- §4.4 `plugs.manage`, "Choose what modes do with plugs", Admin and Adult, fresh PIN: Edit only.
- §5.5 "**At tap-off the tile shows every failure line, heating first, then plugs, then lights** … While Holiday mode is on, the tile keeps its one latest line as built."
- §6 gone: "Not in the states, after the connection has been steady for 2 minutes (until then, "Checking…")"; a shown plug no longer a TP-Link plug by §4.1 counts as gone. Captures "blank the state of any sensor whose `device_class` is `power` or `energy`".

**Copy** (curly apostrophes and "›", "·", "…" in code; the rest of Plan 12's copy stands):
- **Mode choices:** "Ignore", "On", "Off"; spoken "Holiday mode, {name}, ignore" (and "on", "off"), the chip's selected state read by its role; at rest the column cell is spoken "Holiday mode, ignore" (or "on", "off").
- **Row states:** "On · 182 W", "On", "Off", "Can’t reach this plug.", "Checking…", "Can’t reach Home Assistant."; gone "Home Assistant has no plug called that any more. Turn it off in Settings."; spoken "Plug {name}, checking." / "Plug {name}, can’t reach Home Assistant." / "Plug {name}. Home Assistant has no plug called that any more. Turn it off in Settings."
- **Header:** "{on} of {n} on · Home Assistant", or "Home Assistant" with no plug's state known; "Edit" / "Done" on the right.
- **Settings section:** label "Plugs"; help "Plugs from Home Assistant’s TP-Link integration. Turn one on to show it on the Plugs tab."; none "Home Assistant has no TP-Link plugs. Add them in Home Assistant first."; unreadable "Can’t list the plugs. Check Home Assistant is up to date."; switches "Show on the Plugs tab" and "Needs a PIN to switch", spoken "{name}, show on the Plugs tab" and "{name}, needs a PIN to switch"; a gone shown plug's line "Home Assistant has no plug called that any more." Sorted by name.
- **Gone:** "Add a plug", "No plugs yet. Add one from Home Assistant.", "Home Assistant has no other switches.", "Remove", "Settings for {name}", "Leave", "leave alone".

**Sample data** (Plan 12's, plus): devices `dev_lamp` (P110) and `dev_fan` (P100); `switch.lamp_led` ("Lamp LED") and `switch.lamp_auto_off` ("Lamp Auto-off enabled") with category config; `switch.kitchen_crossfade` ("Kitchen Crossfade") on platform `sonos`.

## Review Focus

1. **Home Assistant restarting** (entities missing for about 30 s, then back): the row says "Checking…", never gone. → Task 2, `aRestartIsNotGone`. (A mode's put-back reads over REST, which fails while Home Assistant restarts and reads as unreachable, as built.)
2. **A plug hidden while Holiday holds it, then shown again**: no note, no put-back, the old choice back on the row, and the next turn-on snapshots it afresh. → Task 3, `hidingDropsTheNoteAndShowingKeepsTheChoice`.
3. **The registry read failing or slow on connect** (an old Home Assistant, a dropped socket): the tab keeps the last registry, plugs aren't called gone for it, and Settings shows its unreadable line. → Task 2, `aFailedRegistryKeepsTheLast`; Task 4, `anUnreadableRegistrySaysSo`.
4. **A PIN plug tapped twice quickly, or the PIN cancelled**: one prompt, no "Switching…" until the PIN is accepted, the button back at once on Cancel. → Task 3, `aPinPlugShowsSwitchingOnlyAfterThePin`, `aSecondTapWhileAskingDoesNothing`.
5. **Watts in captures across socket messages**: the first message carries the sensor's `device_class`, later change messages only its `s`; both are blanked. → Task 1, `powerReadingsStayBlankAcrossChanges`.

## Rulings against the code

1. **The registry read is its own short socket**, not a command on the following socket: `HomeAssistantClient.follow` has no request path, and a one-shot socket keeps `follow` unchanged. Opened per read; reads are once per connect and on Settings opening.
2. **The registry body is not captured.** `HaCapture` gets nothing for it; the provider logs, at debug under tag `HaCapture` and only when that tag is loggable, `registry: {p} plugs, {w} with watts, {e} entries` — counts, which the names check allows, and enough for §8.2's check. *(Plan 12's "no counts" log rule is for shipped warnings; this is the debug capture, off in release.)*
3. **Hidden plugs keep their choices because the snapshot already reads only `plugs`** (`PlugsHouse.listed`): hiding removes the id from `plugs`, drops its note and pending runs, and leaves `modes` and `needsPin` alone.
4. **Unknown splits in two**: `PlugPower.UNKNOWN` ("Checking…") before a first read or within the 2 minutes; new `PlugPower.NO_LINK` ("Can’t reach Home Assistant.") while the link has a problem. Both are null in `isOn`.
5. **Power and energy blanking is learned**: the redactor records an id as blanked when an entity body carries `device_class` `power` or `energy`, and blanks that id's `state`/`s` in that message and every later one, until `HaCapture.keep` changes.
6. **The tap-off tile line is one string joined with "\n"**; the tile's line `Text` keeps no `maxLines`. `line: StateFlow<String?>` keeps its type.

## File Structure

- `core/homeassistant/…/HaCall.kt`, `HaJson.kt`, `HomeAssistantClient.kt`, `HomeAssistant.kt`, `HaCapture.kt`, new `HomeAssistantSection.kt`, `di/HomeAssistantModule.kt`, `ui/OtherIntegrationsPage.kt`; testFixtures `FakeHomeAssistantApi.kt`; tests `HaJsonTest`, `HaRedactTest`, `HomeAssistantSocketTest`, `HomeAssistantTest`, `ui/OtherIntegrations*Test`; resource `ha/registry_display.json`.
- `provider/plugs-homeassistant/…/HaPlugSystem.kt`, new `PlugRegistry.kt`; `HaPlugSystemTest`.
- `capability/plugs/…`: `PlugsContract.kt`, `PlugSettings.kt`, `PlugActions.kt`, `PlugsWords.kt`, `PlugsPermissions.kt`, `PlugsCapability.kt`, `di/PlugsModule.kt`; `ui/PlugsTab.kt`, new `ui/PlugRow.kt` (replaces `PlugCard.kt` and `ModeSection.kt`), new `ui/PlugsSettingsSection.kt`, `ui/PlugsDimens.kt`; deleted `ui/AddPlugSheet.kt`, `ui/PlugSettingsSheet.kt` and their tests.
- `capability/holiday/…/HolidayHouse.kt`, `ui/HolidayTile.kt`; `HolidayHouseTest`, `HolidayScreenshotTest`.

---

### Task 1: Home Assistant: the registry, `liveSince`, the section seam, power blanking (spec §3, §4.1, §4.2, §6; rulings 1, 2, 5; Review Focus 5) — opus review

**Files:** as File Structure's `core/homeassistant` line.

**Interfaces — Produces:**
- `data class RegistryEntry(val entityId: String, val platform: String, val deviceId: String?, val category: String?, val translationKey: String?)`, `toString()` = `"RegistryEntry(platform=$platform)"`; `val RegistryEntry.domain: String`.
- `HaJson.registry(text: String): List<RegistryEntry>`: reads a `result` message's `entities` (`ei`, `pl`, `di`, `ec`, `tk`), mapping `ec` through the message's own `entity_categories` (`{"0":"config","1":"diagnostic"}`).
- `HaCall.REGISTRY("registry")`; `HomeAssistantApi.registry(link: HomeAssistantLink): List<RegistryEntry>` (auth, `{"id":1,"type":"config/entity_registry/list_for_display"}`, the result, close; a failed result is `REFUSED`, a close before it `UNREACHABLE`; 5 s timeout as commands); `FakeHomeAssistantApi.registry` returns a settable list or throws a settable problem.
- `HomeAssistant.registry(): List<RegistryEntry>` through `calling`.
- `HomeAssistant.liveSince: StateFlow<Long?>`: `WallClock.nowMillis()` at the open socket's first event, null while no socket is live (set in `following`'s collect when `liveIds` goes from empty; cleared in `socketClosed`). `HomeAssistant`'s constructors take a `WallClock`.
- `interface HomeAssistantSection { val order: Int; @Composable fun Content() }`, `@Multibinds` in `HomeAssistantModule`; `OtherIntegrationsPage` renders each, sorted by `order`, below the device pickers, only while a link is saved.
- `HaJson.redact(text, keep, blanked: MutableSet<String>)` and `HaCapture` holding `blanked`, cleared when `keep` changes (ruling 5).

- [ ] **Step 1: Failing tests.**
  - `HaJsonTest.registryReadsPlugsDevicesAndCategories`: from `ha/registry_display.json` (sample data: `switch.lamp`, `switch.lamp_led` and `switch.lamp_auto_off` with `ec` 0, `sensor.lamp_current_consumption` with `tk` `current_consumption`, all `pl` `tplink` and `di` `dev_lamp`; `switch.fan` `tplink`/`dev_fan`; `switch.kitchen_crossfade` `sonos`): six entries; `switch.lamp_led`'s `category` is `"config"`; `switch.lamp`'s is null; the sensor's `translationKey` is `"current_consumption"`.
  - `HomeAssistantSocketTest.registryAsksAndCloses`: MockWebServer socket sees auth, then exactly the `list_for_display` command with an id, answers, and the socket closes; the result parses. `aRefusedRegistryIsRefused`: `success:false` → `HomeAssistantException(REFUSED)`.
  - `HomeAssistantTest.liveSinceMarksTheSocketsFirstEvent`: null before; the wall time of the first event after the socket opens; unchanged by later events; null after the socket drops; a new time after it reopens.
  - `HaRedactTest.powerReadingsAreBlank`: a states array with `sensor.lamp_current_consumption` (`device_class` `power`, state `"182"`) and an energy sensor → both states `0`; `aTemperatureSensorIsKept` (pin): a `device_class` `temperature` sensor keeps its state. `powerReadingsStayBlankAcrossChanges`: a socket `a` message with the power sensor's attributes, then a `c` message with only `{"+":{"s":"200"}}` → `0` in both.
  - `OtherIntegrationsTest.sectionsShowBelowThePickersWhenLinked`: a fake section (a `Text("Section")`) shows with a saved link and not without.
- [ ] **Step 2:** `./gradlew :core:homeassistant:testDebugUnitTest`. Expected: FAIL (unresolved `registry`, `liveSince`, `HomeAssistantSection`).
- [ ] **Step 3:** Implement the Produces block.
- [ ] **Step 4:** The module's tests pass; then the gate and `-p build-logic :convention:test`.
- [ ] **Step 5:** `git commit -am "Read Home Assistant's registry and blank power readings in captures"` (add the new files first).

---

### Task 2: The provider: plugs and watts from the registry; gone after 2 steady minutes (spec §4.1, §6; rulings 2, 4; Review Focus 1, 3) — opus review

**Interfaces:**
- Consumes: Task 1's `registry()`, `liveSince`, `RegistryEntry`.
- Produces:
  - `internal data class PlugRegistry(val plugs: Set<String>, val watts: Map<String, String>)` and `internal fun plugRegistry(entries: List<RegistryEntry>): PlugRegistry` (§4.1's rule, verbatim above). `wattsIdOf` is deleted.
  - `const val GONE_AFTER_MS = 120_000L` (the provider).
  - `PlugPower.NO_LINK` (in `PlugsContract.kt`; ruling 4). `stateOf` gives `NO_LINK` for a link problem; `GONE` only when steady (`liveSince` set and `now − liveSince ≥ GONE_AFTER_MS`) and the switch is missing from the states or, with a registry known, not in `registry.plugs`; otherwise missing is `UNKNOWN`. Watts from `registry.watts[id]`.
  - `PlugSystem.candidates()` is renamed `available(): PlugCandidates`: the registry's plugs named from the states (`friendlyName ?: entityId`), sorted by name; a registry or states failure is `Failed(line)` with `problemLine`, or for `REFUSED` the copy "Can’t list the plugs. Check Home Assistant is up to date.".
  - The registry is re-read on each new non-null `liveSince` and by `available()`; a failed read keeps the last. `watch()` follows the shown ids plus their watts ids from the registry. The debug count line of ruling 2 after each read.
  - `HaPlugSystem` takes a `WallClock` and the `@ApplicationScope` scope (Hilt constructor; the internal one for tests).

- [ ] **Step 1: Failing tests** (`HaPlugSystemTest`, on `FakeHomeAssistantApi` and a test wall clock):
  - `onlyTpLinkMainSwitchesArePlugs`: the Task 1 fixture → `available()` is `[Fan, Lamp]` (by name), without the LED, auto-off or Sonos switches.
  - `wattsComeFromTheSameDevice`: an on `switch.lamp` with the sensor at `"182.4"` → 182 W; `switch.fan` on → no watts.
  - `aRestartIsNotGone`: socket live 3 min, then `switch.lamp` missing → `GONE`; socket drops and reopens with `switch.lamp` missing → `UNKNOWN` at 1 min 59 s of steady socket and `GONE` at 2 min (one tick before and at the boundary); back in the states → `ON`.
  - `aShownPlugLeftTheIntegrationIsGone`: steady, registry without `switch.lamp`, states with it → `GONE`.
  - `aLinkProblemIsNoLink`: `problem = UNREACHABLE` → `NO_LINK` for every plug.
  - `aFailedRegistryKeepsTheLast`: a good read, then a reconnect whose read throws → `switch.lamp` still a plug, still has watts.
  - Update the existing tests that used `wattsIdOf` or `candidates()`.
- [ ] **Step 2:** `./gradlew :provider:plugs-homeassistant:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement; fix the compile in `:capability:plugs` callers of `candidates()` only as far as compiling (Task 3 and 4 own the UI).
- [ ] **Step 4:** Module tests pass; the gate and `-p build-logic :convention:test`.
- [ ] **Step 5:** `git commit -am "Find plugs and their watts from Home Assistant's registry"`.

---

### Task 3: The store, the actions, the words: show and hide, Ignore, Switching after the PIN (spec §2.4, §4.2–§4.6, §5.4; rulings 3, 4; Review Focus 2, 4)

**Interfaces:**
- Consumes: `PlugPower.NO_LINK` (Task 2).
- Produces:
  - `ModeChoice { IGNORE, ON, OFF }` (was `LEAVE`).
  - `PlugPrefs.showing(id: String): PlugPrefs` (append to `plugs` if a plug id and absent; `modes` and `needsPin` untouched) and `PlugPrefs.hiding(id: String): PlugPrefs` (out of `plugs`; its note and every run's pending entry dropped; `modes` and `needsPin` kept). `adding` and `without` are deleted.
  - `PlugActions.show(entityId: String, on: Boolean)`, `PlugActions.setNeedsPin(entityId, on)` a no-op unless shown; `add`/`remove` deleted. `switch` asks the PIN first and only then marks the plug in `switching`; a second tap while the PIN is asked does nothing (a private `asking` set).
  - `PlugsWords`: `IGNORE = "Ignore"`; `CANT_REACH_HA = "Can’t reach Home Assistant."`; `GONE_PLUG` = "Home Assistant has no plug called that any more. Turn it off in Settings."; `plugsOn` per §4.3 (counts ON and OFF only; none → "Home Assistant"); `plugStatus`/`plugSpoken` for `NO_LINK`; `modeRowSpoken` word "ignore"; `modeCellSpoken(mode, choice)` "Holiday mode, ignore"; the Settings strings and `showSpoken(name)`, `needsPinSpoken(name)` from the Copy list. Deleted: `ADD_A_PLUG`, `NO_PLUGS`, `NOTHING_TO_ADD`, `REMOVE`, `LEAVE`, `settingsFor`.
  - `PlugsPermissions` MANAGE label "Choose what modes do with plugs".

- [ ] **Step 1: Failing tests:**
  - `PlugSettingsTest.hidingDropsTheNoteAndShowingKeepsTheChoice`: shown `switch.lamp`, Holiday On, a note and a pending run → `hiding` → not in `plugs`, no note, not pending, Holiday's choice and `needsPin` kept → `showing` → back last in `plugs`, choice On. `aStoredLeaveReadsAsIgnore` (pin): a store with no entry for a plug reads `IGNORE`.
  - `PlugsHouseTest.aHiddenPlugIsLeftOutOfTheNextSnapshot`: hide during Holiday, `leave` switches nothing for it; show again, a new `begin` lists it.
  - `PlugsHouseTest.aScheduleChangeCountsAsAHandChange` (pin, §2.17): Holiday switched it off; the plug reads on before `leave` → note dropped, not switched.
  - `PlugActionsTest.aPinPlugShowsSwitchingOnlyAfterThePin`: `switching` stays empty while `authorise` suspends; contains the id after it returns; empty after Cancel (null). `aSecondTapWhileAskingDoesNothing`: two taps → one `authorise`.
  - `PlugActionsTest.needsPinOnlyWhileShown`; `PlugsWordsTest` for every changed and new string, `plugsOn` with one ON, one OFF, one UNKNOWN → "1 of 2 on · Home Assistant", all UNKNOWN → "Home Assistant".
  - `PlugsPermissionsTest` label.
- [ ] **Step 2:** `./gradlew :capability:plugs:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement; the UI compiles against the new names (Task 5 rebuilds it).
- [ ] **Step 4:** Module tests pass; the gate.
- [ ] **Step 5:** `git commit -am "Show and hide plugs instead of adding and removing them"`.

---

### Task 4: Settings › Other integrations: the Plugs section (spec §4.2, §4.6; Review Focus 3)

**Interfaces:**
- Consumes: `HomeAssistantSection` (Task 1), `PlugSystem.available()`, `plugs` (Task 2), `PlugActions.show`/`setNeedsPin`, the Settings words (Task 3).
- Produces: `ui/PlugsSettingsSection.kt` — `class PlugsSettingsSection @Inject constructor(...) : HomeAssistantSection` (`order = 10`), bound `@IntoSet` in `PlugsModule` only when a `PlugSystem` is bound; a stateless `PlugsSettings(ui: PlugsSettingsUi, onShow: (String, Boolean) -> Unit, onNeedsPin: (String, Boolean) -> Unit)`. Rows: the available plugs by name, then any shown id not among them with the gone line. Each row: the name (`HhType` as the device pickers' labels), two switches (the app's existing switch component as Settings uses), Needs a PIN disabled while not shown. `available()` is read when the section appears and after each Show change. Tags use the row index.
- Deleted: `ui/AddPlugSheet.kt`, `ui/PlugSettingsSheet.kt`, their tests and their screenshots' baselines.

- [ ] **Step 1: Failing tests** (`PlugsSettingsSectionTest`, Robolectric with `TestAccess`):
  - `listsOnlyTheAvailablePlugsByName`; `showPutsAPlugOnTheTab` (`show(id, true)` called; the row's Show on); `needsPinIsOffUntilShown`; `aGoneShownPlugStaysListed` (its line shown, Show can turn it off); `noPlugsSaysSo`; `anUnreadableRegistrySaysSo`; TalkBack: the switches read `showSpoken`/`needsPinSpoken`; ends with `assertClickablesHaveRoleAndLabel()`.
  - Roborazzi (`_dark`/`_light`): `plugs_settings_list` (two plugs, one shown with PIN), `plugs_settings_none`, `plugs_settings_unreadable`.
- [ ] **Step 2:** Run the module's tests. Expected: FAIL.
- [ ] **Step 3:** Implement and bind.
- [ ] **Step 4:** Plan 12's UI-task ending (compare, record, baseline check with the deleted sheets' images removed, gate).
- [ ] **Step 5:** `git commit -am "Choose the plugs in Settings › Other integrations"` (with `git add` for new files and `git rm` for the deleted ones).

---

### Task 5: The Plugs tab: a row per plug with its mode columns (spec §2.5, §4.3, §4.6, decision 14; Review Focus 4)

**Interfaces:**
- Consumes: Tasks 2–3.
- Produces:
  - `PlugsCapability.hasTab` = linked and at least one shown plug (`plugs` non-null and non-empty).
  - `ui/PlugRow.kt`: `PlugRow(state: PlugState, needsPin: Boolean, switching: Boolean, modes: List<HouseModeInfo>, choices: Map<String, ModeChoice>, editing: Boolean, onSwitch: () -> Unit, onChoose: (String, ModeChoice) -> Unit)`. The name wraps to at most 2 lines (no ellipsis on 1); the status below; padlock then the Switch on/off button only for ON or OFF; the accent fill when on; one cell per mode, the word at rest (spoken `modeCellSpoken`), Ignore/On/Off chips in Edit (`Role.RadioButton` in a `selectableGroup`, spoken `modeRowSpoken`). The row is one semantics group spoken `plugSpoken`; the button `switchSpoken`. `PlugCard.kt` and `ModeSection.kt` are deleted (their tests become `PlugRowTest`).
  - `PlugsTab`: the header (title, `plugsOn`, Edit/Done right), the mode names over their columns once, then the rows in store order in a vertical list. No Add a plug, no empty state, no ›.

- [ ] **Step 1: Failing tests:**
  - `PlugRowTest`: `onAndOffShowTheirButton`; `unknownNoLinkUnreachableAndGoneHaveNoButton`; `aLongNameWrapsToTwoLines` (the 2-line name's text fully present, no "…"); `thePadlockShowsWhenNeedsPin` and is hidden from TalkBack; `atRestTheCellIsReadOnly`; `inEditTheChipsChoose`; spoken texts per Copy.
  - `PlugsTabTest`: `theHeaderCountsKnownPlugsOnly`; `editAsksForAPinAndEndsAfterTwoIdleMinutes` (kept from Plan 12, adjusted); `switchingShowsOnlyAfterThePin`; `aChildPinIsRefused` (kept).
  - `PlugsCapabilityTest.theTabShowsOnceAPlugIsShown` (no tab when linked with none shown; tab with one).
  - Roborazzi (`_dark`/`_light`): `plugs_tab_rows` (four rows: on with watts and PIN, off, unreachable, unknown), `plugs_tab_long_name`, `plugs_tab_edit`. The old card and section images are removed.
- [ ] **Step 2:** Run the module's tests. Expected: FAIL.
- [ ] **Step 3:** Implement. Layout numbers in `PlugsDimens`; the mode column width fits "Ignore On Off" chips.
- [ ] **Step 4:** The UI-task ending.
- [ ] **Step 5:** `git commit -am "Show each plug as a row with its mode choices"`.

---

### Task 6: Holiday's tap-off lines (spec §5.5; ruling 6)

**Interfaces:** `HolidayHouse.turnOff` sets `line` to the non-null of (the Eco line, the plugs' line, `LIGHTS_STILL_ON`) joined with `"\n"`, or null; the in-mode `showLine()` is unchanged. `HolidayTile`'s line `Text` has no `maxLines`.

- [ ] **Step 1: Failing tests:** `HolidayHouseTest.tapOffShowsEveryFailureInOrder` (heating, plugs and lights all fail → `"$STILL_ON_ECO\n$LAMP_BACK_LINE\n${HolidayWords.LIGHTS_STILL_ON}"`; the ten-minute clear still clears it); update `turningOffWithPlugsFailingStillEnds`' last assertion to both lines; `whileOnTheTileKeepsOneLine` (pin). Roborazzi: `holiday_tile_three_lines` `_dark`/`_light`.
- [ ] **Step 2:** `./gradlew :capability:holiday:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** The UI-task ending.
- [ ] **Step 5:** `git commit -am "Show every Holiday failure at tap-off"`.

---

### Task 7: Simplify the revision

- [ ] **Step 1:** The `simplify` skill over `git diff 03214056...HEAD`. Look hardest at `HaPlugSystem`'s steady timer beside `liveSince`, and anything left of Add/Remove.
- [ ] **Step 2:** For each change, the module's tests, then the gate.
- [ ] **Step 3:** When anything changed: `git commit -am "Simplify the plugs revision"`.

---

### Task 8: The emulator walk-through again (spec §8.1, §8.2)

On `culvery-5` (`adb install -r` of the debug build; never on `emulator-5554` if it holds the household's release — check `adb devices` and the AVD name first). **No plug switched, and Holiday mode never turned on with a plug on On or Off, without the user's go each time.**

- [ ] **Step 1: Before installing:** the emulator's store holds the plug added in the first walk-through, with Holiday on Off; after install it must read as shown, choice kept (the store's shape is unchanged).
- [ ] **Step 2: Walk** (one line each in the ledger):
  1. Settings › Other integrations lists only the household's TP-Link plugs (no Sonos, eufy or the plug's own setting switches); Show and Needs a PIN work; hiding then showing keeps Holiday's choice.
  2. The tab: rows, the name not cut short, watts, the padlock, Edit with Ignore / On / Off, the two-minute end.
  3. A PIN plug: no "Switching…" until the PIN; a Child's PIN refused.
  4. The user restarts Home Assistant (their action): the row says "Checking…" and never gone.
  5. Offline (`svc wifi disable`, `svc data disable`): "Can’t reach Home Assistant.", no button, the header without the plug. Then, on the user's go, Holiday on and off offline: the tap-off tile's lines, heating then plugs (then lights if they failed). Online again; nothing retried.
  6. TalkBack from the accessibility tree: a row, a mode chip, a Settings switch.
- [ ] **Step 3: Captures and the names check** as Plan 12 Task 8 Step 2, plus the debug `registry:` count line (ruling 2): its plug count matches what Settings lists. The watt figure no longer appears.
- [ ] **Step 4:** The fault loop with the user; put back every setting; `emu kill` only on the user's say.
- [ ] **Step 5: Record** under "Plan 12 (smart plugs): the revision's walk-through", with no ids, names or watts: `git commit -am "Record the plugs revision's walk-through"`.

Then Plan 12's Task 9 (fixtures from the captures, now including a redacted `registry_display.json` shape check; the tablet) and Task 10 (final opus review of `main...HEAD`, merge with `--ff-only` on the user's say, install).

---

## Spec coverage (revised parts → tasks)

| Spec | Task |
|---|---|
| §2.2, §4.2 Settings, Show, PIN, gone listed | 1 (seam), 3, 4 |
| §2.4 Ignore | 3, 5 |
| §2.5, §4.3 rows, header, no button, Switching after PIN | 3, 5 |
| §2.16, §4.1 registry plugs and watts | 1, 2 |
| §2.17 schedule wins | 3 (pin) |
| §2.18 Edit keeps its PIN | 5 |
| §4.4 `plugs.manage` label | 3 |
| §4.5 choices kept when hidden | 3 |
| §4.6 TalkBack | 3, 4, 5 |
| §5.5 tap-off lines | 6 |
| §6 gone after 2 steady minutes; no link; captures blank watts | 1, 2 |
| §7 tests | 1–6 |
| §8.1, §8.2 | 8 |
