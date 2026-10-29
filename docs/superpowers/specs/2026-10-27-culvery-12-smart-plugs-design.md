# Culvery 12: Tapo smart plugs

Brainstormed 2026-10-27. Follow-ups file: `docs/superpowers/plans/2026-09-23-plan1-followups.md` ("Feature ideas": Tapo smart plugs). Builds on Plan 11 (`docs/superpowers/specs/2026-10-26-culvery-11-hoover-heating-holiday-design.md`): the Home Assistant link, `:core:house` and Holiday mode.

Branch `plan-12-smart-plugs`, from `main` at 82b81db9, in the one `culvery` checkout (no worktree). One plan, then one revision plan.

**Revised 2026-10-28 after the emulator walk-through** (Tasks 1–7 built at 817583bf). The user's rulings move setting up plugs into Settings, list only real plugs, show one row per plug on the tab, and rename Leave to Ignore; six faults from the walk-through are folded in. Changed: decisions 2, 4, 5, 15 and new 16–19; §3, §4.1–§4.6, §5.4 (schedules), §5.5 (the tile), §6, §7, §8. The rest stands as built.

## 1. Purpose and outcome

The house has TP-Link Tapo smart plugs: a P110 (it reports power) on an appliance, a P100 on the router (not discoverable at the moment), and two more P100s coming for party lights. Party mode is the next plan and will switch chosen plugs; Holiday mode (Plan 11b) is already built.

The family want:

- **a Plugs tab on the rail**: every plug with its state, switched on and off straight from the wall;
- **modes that switch plugs**: for each mode, each plug is left alone, switched on or switched off, and **goes back to how it was when the mode ends**;
- **the plugs ready for Party mode**: the mode lists are built so that Party mode adds its own list and nothing else changes.

Done means: the household's plugs show on the Plugs tab through Home Assistant and switch from it; Holiday mode switches the plugs in its list on turning on and puts them back on turning off; the final review, a local merge and the release installed on the SM-T510 with `install -r`.

## 2. Decisions (the user, 2026-10-27)

1. **Culvery never assumes which plugs it may control.** Every plug is offered the same way, the router included ("maybe I'll use it for something else"). No plug is special-cased by its name, its model or what it powers. Whatever a plug does is the household's choice on the Plugs tab.
2. **Setting up the plugs is in Settings › Other integrations**, under the Home Assistant link: which plugs show on the tab, and each plug's PIN setting. What each mode does stays on the Plugs tab. *(Revised 2026-10-28, the user: "adding a plug should be done in settings". Was: Settings keeps only the link.)*
3. **A new Plugs tab on the rail** shows each plug's state and **switches it on and off directly**.
4. **Per mode, each plug is Ignore, On or Off.** Ignore is the default for a newly shown plug and for a newly added mode, so a plug is never switched by a mode until someone says so. *(Revised 2026-10-28, the user: the word is "Ignore", not "Leave".)*
5. **Layout: one row per plug**, with the plug's state, padlock and switch, and its choice for each mode in the same row. A new mode adds a column. *(Revised 2026-10-28, the user, after seeing the by-mode layout on the emulator. Was: one section per mode.)*
6. **When a mode ends, each plug it switched goes back to how it was just before the mode started.**
7. **Plan 12 wires Holiday mode only.** The Party mode plan adds its own column. The lists are built so a new mode just plugs in (§5.1).
8. **A per-plug "Needs a PIN to switch" setting**, off by default, chosen by the household. *(Proposed in the brainstorm mockup and accepted, 2026-10-27.)*
9. **The rail is filling up**: a small redesign of it is likely soon (to Feature ideas; not in this plan).
10. **Holiday lights may use plugs later** (a lamp on a plug joining the mimic-presence lights). Not in this plan; to Feature ideas.
11. **The PIN that turns a mode on covers every plug in its list for as long as the mode is on**, restarts included. "Needs a PIN to switch" applies only to switching a plug by hand on the Plugs tab. *(The user, after the builder check.)*
12. **Two modes on at once:** each plug's state is remembered from before the **first** mode that touched it, and it is put back only when the **last** of those modes ends. *(The user, after the builder check.)*
13. **Starting a mode's plug switching needs that mode's own grant.** Taking the mode's snapshot (`begin`, §5.1) takes the `Authorised` from the mode's fresh PIN (for Holiday, `holiday.mode`); retries of a mode already begun take none, so decision 11 still holds across restarts. *(The user, after the plan review, 2026-10-27.)*
14. **Edit on the mode choices ends by itself after 2 idle minutes**, as the 2-minute session does, as well as on Done and on leaving the tab. *(The user, after the plan review, 2026-10-27.)*
15. **The router plug is not special-cased in code.** At the walk-through Claude tells the household that switching the router plug off cuts Culvery off from switching it back on, and suggests setting Needs a PIN to switch on it. A plug not shown on the tab is out of every mode. *(The user, after the plan review, 2026-10-27; revised 2026-10-28: "Show on the Plugs tab" in Settings is now how a plug is added or taken off.)*
16. **Only real plugs, one entity each.** Culvery lists the TP-Link integration's main switch per plug and nothing else: not other integrations' switches (Sonos, eufy) and not the plug's own setting switches (LED, auto-off). *(The user, 2026-10-28: "list should only show plugs and only the main plug entity".)* This filters by integration, not by name or model, so decision 1 holds.
17. **A Tapo schedule wins.** A plug's own schedule in the Tapo app (for example off each evening and on each morning) counts as a hand change (§5.4): a mode's choice holds only until the schedule next acts, and that plug isn't put back when the mode ends. *(The user confirmed, 2026-10-28.)*
18. **Mode choices keep Edit behind an Adult or Admin PIN** on the tab; the PIN setting is set in Settings and the tab only shows the padlock. *(The user, 2026-10-28.)*
19. **Walk-through faults fixed in the revision** (the user, 2026-10-28): a plug briefly missing while Home Assistant starts isn't called gone; a plug whose state isn't known says so and offers no switch; "Switching…" shows only once the PIN is accepted; watt readings are blanked in captures; at tap-off Holiday's tile shows a plug that didn't go back even when the heating also failed. Details in §4.3, §5.5 and §6.

The brainstorm's mockups (`.superpowers/brainstorm/`, not committed): the tab with plug cards on the left and the mode sections on the right, v2 with Leave / On / Off. Superseded by decision 5 (revised); visual details are provisional until the end-of-v1 design review.

### What the plugs allow (research, 2026-10-27)

- Home Assistant's built-in **TP-Link Smart Home** integration (`tplink`, python-kasa) controls Tapo P100 and P110 plugs **on the home network**. It asks for the TP-Link account email and password once, to authenticate the plugs' encrypted local protocol (KLAP); those stay in Home Assistant on the NAS. A plug that discovery misses can be added by its IP address.
- Each plug is a `switch` entity (`switch.turn_on` / `switch.turn_off`, state `on` / `off` / `unavailable`). The P110 adds power sensors, among them current consumption in watts. The integration also creates other entities per plug (for example an LED switch), so not every `switch.*` is a plug.
- **Unverified:** newer Tapo firmware may need "Third-Party Compatibility" turned on in the Tapo app before Home Assistant can reach a plug. Check while adding the plugs (§3).

### Approaches weighed

- **A. Through Home Assistant (chosen).** Plan 11's rule: new devices go through Home Assistant. The TP-Link sign-in stays on the NAS, Culvery reuses its one link, client and socket, and Home Assistant keeps the plug protocol up to date. Cost: the plugs need the NAS as well as the tablet.
- **B. Culvery talks to the plugs directly.** A Kotlin copy of Tapo's KLAP protocol to maintain, and the TP-Link password on the wall tablet. The router plug's discovery problem would be the same. Rejected.

## 3. Setting up the plugs in Home Assistant

The user does this, with help, before the build's device checks (§8.2). Not code.

- Add the **TP-Link Smart Home** integration in Home Assistant on the NAS; add each plug (by IP address where discovery misses it, as the router plug may need). Give the plugs fixed addresses on the router.
- Check the Culvery Home Assistant user can see the plugs' entities (it's a non-admin user; Plan 11 §3). Its registry read (§4.1) needs no admin: Home Assistant's `config/entity_registry/list_for_display` carries no `require_admin` (checked in Home Assistant's source, 2026-10-28; confirmed against the household's instance in §8.2).
- Note the real entity names for the switch and the P110's power sensor, for the fixtures. No names go into committed files.

## 4. The plugs

### 4.1 Modules

Names are the builder's to adjust, within `ModuleBoundaries` as Plan 11 §4.2 left them.

- **`:capability:plugs`**: `PlugSystem` — `plugs: Flow<List<PlugState>?>` (for each shown plug: its entity id, name, on / off / unreachable / gone / unknown, and watts when it reports power), the household's TP-Link plugs for Settings (§4.2), and `switch(entityId, on): PlugResult`. The Plugs tab (§4.3), its section of Settings › Other integrations (§4.2), the plug settings store (§4.5), and the `HousePlugs` implementation (§5).
- **`:provider:plugs-homeassistant`**: implements `PlugSystem` against Plan 11's shared `HomeAssistantClient`.
  - Switching: `switch.turn_on` / `switch.turn_off` with the entity.
  - State from the switch entity's state; `unavailable` is unreachable. Missing is gone only once the connection has been steady for 2 minutes (§6); until then it's unknown.
  - **Which entities are plugs, and their watts, come from the registry**: `config/entity_registry/list_for_display` over the socket, read on each connect. A plug is an entry with platform (`pl`) `tplink`, domain `switch` and no entity category (`ec`). Its watts are the `sensor` entry with platform `tplink` on the same device (`di`) whose translation key (`tk`) is `current_consumption`; none, no watts. *(Revised 2026-10-28: replaces finding the watts by name, and gives the plug filter decision 16 asks for. The builder confirms the keys against the household's instance, §8.2.)*
  - Updates as Plan 11 §4.3: the socket while the Plugs tab or Home shows, a full read on connect; commands over REST with a 5 s timeout; off screen (Holiday mode's steps), REST reads every 5 s for up to 30 s to see the change.

### 4.2 Which plugs Culvery shows: Settings › Other integrations

*(Revised 2026-10-28, decisions 2 and 16. Replaces Add a plug and Remove on the tab.)*

- A **Plugs** section on Settings › Other integrations, below the Home Assistant link, once the link is saved. It lists **every TP-Link plug Home Assistant has** (§4.1's registry rule), by its Home Assistant name, sorted by name. Each plug has two switches: **Show on the Plugs tab** (off for a plug Culvery hasn't seen before, so nothing appears until the household chooses; decision 1) and **Needs a PIN to switch** (off by default; usable only while the plug is shown).
- Help text under the heading: "Plugs from Home Assistant's TP-Link integration. Turn one on to show it on the Plugs tab." With none: "Home Assistant has no TP-Link plugs. Add them in Home Assistant first." If the registry can't be read: "Can't list the plugs. Check Home Assistant is up to date." *(My call on the words.)*
- **Turning Show off** takes the plug off the tab and out of every mode. Its mode choices and PIN setting are **kept** in the store, so turning it back on restores them; a running mode's note for it is dropped and the plug is left as it is (§5.4). Nothing is lost, so it needs no "Are you sure?". *(My call: walk-through fault 3, Remove with no confirmation, goes with Remove.)*
- A shown plug that Home Assistant no longer has, or that is no longer a TP-Link plug by §4.1's rule, stays in the list, marked "Home Assistant has no plug called that any more.", so it can be turned off; on the tab it counts as gone (§6). *(My call, after the builder check.)*
- Settings is admin-only (Plan 11), so showing a plug and its PIN setting need an Admin. *(Consequence of decision 2: Adults could add plugs on the tab before; they keep mode Edit, decision 18.)*
- The name shown is Home Assistant's friendly name, read live; Culvery stores only entity ids.

### 4.3 The Plugs tab

A capability tab (`hasTab`), rail order after Door *(my call: order 55)*, icon a plug (`power` or similar). It shows **once at least one plug is shown** (§4.2), since adding is no longer on the tab. *(Revised 2026-10-28; was: once Home Assistant is linked, with an empty state.)*

- **Header:** "Plugs", and "{on} of {n} on · Home Assistant" in muted, as the Lights tab's header, where {n} counts only plugs whose state is known; with none known, just "Home Assistant". **Edit** sits on the right.
- **A row per shown plug**, in the order they were turned on (decision 5):
  - the name (wrapping to two lines, never cut short; walk-through fault 2) and its state below it ("On · 182 W", "On", "Off", "Can't reach this plug", "Checking…", "Can't reach Home Assistant");
  - a padlock when it needs a PIN, then a large **Switch on / Switch off** button; an on plug's row uses the accent fill, as an on room does;
  - **one column per mode** (Plan 12: Holiday mode only), headed with the mode's name above the first row. At rest the column shows the chosen word (Ignore, On or Off); in **Edit**, three chips **Ignore / On / Off**.
- **No button** when the plug can't be switched: unreachable, gone, or its state unknown (before the first read, or while Home Assistant can't be reached). *(Walk-through faults 6 and 8: the button offered "Switch on" for a plug whose state wasn't known.)*
- **Edit** opens the mode columns for changes behind an Adult or Admin PIN; at rest they are read-only, so a stray tap changes nothing. Edit ends on Done, on leaving the tab, or after 2 idle minutes (decision 14).
- **Switching:** a plug that needs a PIN asks first; "Switching…" shows only once the PIN is accepted (walk-through fault 7), and stays until Home Assistant reports the change, as Plan 11's hoover row. A failure shows a toast with the §6 line and the row keeps its real state.

### 4.4 Who can do what

New permissions, as Plan 11 declared `hoover.start`:

- **`plugs.switch`**, "Switch plugs that need a PIN", default roles Admin and Adult, **a fresh PIN every time** (as Start the hoover). Plugs without the setting switch for anyone, with no PIN.
- **`plugs.manage`**, "Choose what modes do with plugs", default roles Admin and Adult, a fresh PIN: Edit on the mode columns. *(Revised 2026-10-28: showing plugs and their PIN setting moved to Settings, which is admin-only.)*
- A Child's PIN is refused for both.

### 4.5 The plug settings store

The capability's own store: the shown plugs in order (entity ids), each plug's PIN setting, and each mode's list (mode id → entity id → On or Off; a plug not in a mode's list is Ignore). A plug turned off in Settings leaves the shown list but keeps its PIN setting and mode choices. The notes (§5.2) and each running mode's snapshot live here too. Excluded from cloud backup and device transfer, as Plan 11's stores are. *(My call on the shape.)*

### 4.6 TalkBack

- Row: "Plug {name}, on, 182 watts. Switch off, button." / "Plug {name}, off. Switch on, button." / "Plug {name}, can't reach this plug." / "Plug {name}, checking." / "Plug {name}, can't reach Home Assistant." With the PIN setting: "… Switch on, needs a PIN, button."
- Mode chip: "Holiday mode, {name}, ignore, selected." and so on for On and Off; at rest the column reads "Holiday mode, ignore".
- Settings: each plug's switches read "{name}, show on the Plugs tab" and "{name}, needs a PIN to switch".

## 5. Modes

### 5.1 How a mode plugs in

`:core:house` (Plan 11 §5.1) gains:

- **`HouseModeInfo`**: a small contribution with the mode's id, its name as shown ("Holiday mode"), an order, and the permission that turns it on (`holiday.mode`). A mode's capability binds one into a Hilt set. Holiday binds `holiday`; Party mode will bind `party`. The Plugs tab shows one column per contribution, in order.
- **`HousePlugs`**, implemented by `:capability:plugs`:
  - `ready: Flow<Boolean>`: true once at least one added plug has On or Off in any list *(builder may narrow it to the given mode)*.
  - `preview(modeId): List<PlugChange>`: the plugs the mode will switch and to what, for a sheet's "will:" lines.
  - `begin(modeId, by)`: take the mode's snapshot (§5.2), with the grant from the mode's fresh PIN (decision 13); nothing is switched yet.
  - `enter(modeId): HouseStep?`: switch the plugs of a mode already begun (§5.3), with no grant. Safe to call again: a retry switches only plugs not yet reached. Null when the mode has no snapshot (never begun, or lost to a restart): nothing is switched.
  - `leave(modeId): HouseStep`: let it go (§5.4).
  - Optional (bound or not), like Plan 11's other seams.

A new mode needs only its contribution and calls to `begin`, `enter` and `leave`; the Plugs tab, the store and the put-back logic don't change. *(My call on the shape; decision 7.)*

**A grant on `begin`, none on `enter` or `leave`** (decisions 11, 13). The mode's own fresh PIN at turn-on is passed to `begin`, which refuses a grant without the mode's permission; turn-off's PIN is checked by the mode's capability (as Holiday already does) before calling `leave`. A retry after a restart, when Holiday has forgotten its grant (`HolidayHouse` keeps it in memory only), still switches plugs, including those set to need a PIN. This differs from the lights step, which Plan 11 marks missed after a restart.

### 5.2 The memory: one note per plug

Each plug has at most one **note**, kept in the plug store so it survives a restart:

- **before**: the plug's state just before the first mode that touched it (on or off);
- **holders**: the modes that currently hold it, in the order they took it, each with what it set the plug to;
- **expected**: what the plug should be now (the setting of the newest holder).

A mode's list is **snapshotted when the mode turns on** (`begin`, stored with the mode's place in the notes); edits to the list while it's on take effect next time. *(My call.)* Holders are ordered by when their mode began, so a mode that reaches a plug late doesn't jump ahead of one that began after it.

### 5.3 Turning a mode on

`enter(modeId)`, for each plug in the mode's snapshot that isn't Ignore:

1. **No note yet:** read the plug's state, write the note (before = that state, holders = this mode, expected = its setting), then switch it if it differs. A plug already as the mode wants is noted and not switched.
2. **Already noted by another mode** (decision 12): add this mode among the holders by when it began; expected is the newest holder's setting; switch only if this mode is the newest and the plug differs from expected. *before* doesn't change. If the plug already differs from expected (switched by hand before this mode came), the old note is dropped and a fresh one starts with before = its state now.
3. **Can't be reached:** retried every 5 minutes while the mode stays on (Plan 11 §5.3's rule for failed steps); its note is written when it's first reached. *(My call.)*
4. One plug failing doesn't stop the others or the mode; the step reports Failed with the §6 line.

### 5.4 Turning a mode off

`leave(modeId)`, for each plug this mode holds (the mode's snapshot is removed last, so a leave cut short by a restart can be run again):

- **Someone switched it by hand** while modes held it (its state differs from *expected*): drop the whole note and leave the plug as they set it. *(My call, 2026-10-27, played back to the user: a hand change wins over every mode.)* A change made by the plug's own Tapo schedule counts the same (decision 17).
- **Other modes still hold it:** remove this mode from the holders. If this mode was the newest, expected becomes the next newest holder's setting and the plug is switched to it; otherwise nothing is switched. *(My call: the mode still on keeps its say.)*
- **This was the last holder:** switch the plug back to *before*, and drop the note.
- **Unreachable now** (or gone, or its state unknown): it can't be switched; the step reports Failed. This mode is dropped from the holders, and the note goes when no holder is left, as if it had gone back. When a newer mode still holds it, the step reports nothing: that mode decides the plug. *(My call: the family are home and can switch it from the tab. The newer-holder case is from the final review, 2026-10-29.)*
- **Never reached** while the mode was on: there's no note to act on.

**Turning a plug's Show off** in Settings drops its note; the plug is left as it is. *(My call.)*

### 5.5 Holiday mode's part

- **Turning on** (Plan 11 §5.3): `HousePlugs.begin("holiday", by)` before Holiday's record is saved, then a new step after Heating: `HousePlugs.enter("holiday")`. A plugs step with no snapshot (`enter` null) is missed, as the lights step is after a restart. Holiday's record (Plan 11 §5.7) gains a plugs step, done or not, retried every 5 minutes like the others.
- **The sheet's "Holiday mode will:"** lists the plug changes in plain words, only for plugs not on Ignore: "Switch {name} off." / "Switch {name} on." When the plugs go back, it says "Plugs go back as they were when you turn it off." *(My call.)*
- **Turning off** (Plan 11 §5.4): `HousePlugs.leave("holiday")` after the heating. **Plugs go back at tap-off**, not at 00:01 on the back day as the heating does: decision 6 ties putting back to the mode ending. *(My call, played back to the user.)*
- Failure lines on the tile, §6. **At tap-off the tile shows every failure line, heating first, then plugs, then lights**, each on its own short line, not only the first. While Holiday mode is on, the tile keeps its one latest line as built. *(Revised 2026-10-28, walk-through fault 10: when the heating and a plug both failed at tap-off, the heating's line hid the plug's. The user, after the builder check: tap-off only; a status tab, in Feature ideas, would hold the fuller detail later.)*

## 6. When things go wrong

Plan 11 §6's lines cover Home Assistant itself (can't reach it, offline, token refused). New:

| What happens | How Culvery notices | What it says |
|---|---|---|
| A plug can't be reached | Its switch entity is `unavailable` | On the row: "Can't reach this plug." Toast on switching: "Can't reach {name}. Check it's plugged in and the Tapo app can see it." |
| A shown plug is gone from Home Assistant | Not in the states, after the connection has been steady for 2 minutes (until then, "Checking…") | On the row: "Home Assistant has no plug called that any more. Turn it off in Settings." *(Revised 2026-10-28, walk-through fault 5: while Home Assistant restarts its entities are missing for about 30 s.)* |
| A plug's state isn't known | Before the first read; Home Assistant can't be reached | On the row: "Checking…" before the first read; "Can't reach Home Assistant." while it can't be reached. No button; not counted in the header. |
| A switch didn't happen | No change within 30 s | Toast: "{name} didn't switch. Try again, or use the Tapo app." |
| Holiday mode couldn't switch a plug | §5.3 | On the tile: "{name} didn't switch {off}. It'll keep trying." (one plug) / "2 plugs didn't switch. They'll keep trying." |
| A plug couldn't be put back | §5.4 | On the tile: "{name} didn't switch back {on}. Switch it on the Plugs tab." |

**Privacy.** Logs carry the call and its outcome only: no entity ids, no plug names (a plug can be named after a person or a room), no watts. `LogHygieneTest` covers the new modules. The names show on screen only. **Captures** (Plan 11's debug-only capture) blank the state of any sensor whose `device_class` is `power` or `energy`, as they blank other numbers. *(Revised 2026-10-28, walk-through fault 9: Plan 11 keeps a kept sensor's state for temperatures, and the P110's watts came through with it.)*

## 7. Tests

- **JSON** (JVM): fixtures for switch entities on, off and `unavailable`, a P110 power sensor and a plug without one, extra non-plug switches; a `list_for_display` result with a TP-Link plug, its LED and auto-off switches (`ec` config), its power sensor, a P100 with no power sensor, and a Sonos switch: only the two main switches are plugs, and only the P110 has watts. From Home Assistant's documented shapes first, then redacted captures from the household's instance (§8.2).
- **Provider** (MockWebServer): `switch.turn_on` / `turn_off` paths and bodies, timeouts, the 30 s check, watts found by device or absent; a plug missing from the states is unknown for 2 minutes of steady connection, then gone; a reconnect restarts the 2 minutes.
- **Plugs tab** (Robolectric; Roborazzi for: four rows mixed on / off / unreachable / PIN, a long name wrapping, an unknown row with no button, the Holiday column at rest and in Edit): switching a plain plug with no PIN; a PIN plug asks for a fresh Adult or Admin PIN and refuses a Child's, and shows "Switching…" only after the PIN; Edit asks for a PIN; the header counts only known plugs; the tab shows once a plug is shown and goes when none is.
- **Settings** (Robolectric; Roborazzi for the Plugs section with plugs, with none, and unreadable): only registry plugs listed; Show on and off, with mode choices kept across off and on; Needs a PIN only while shown; a gone shown plug stays listed.
- **Modes** (with a test clock): enter notes then switches; a plug already in the mode's state is noted and not switched; Ignore plugs untouched; leave puts back to *before*; a hand-switched plug is left and its note dropped; a never-reached plug is left; unreachable at leave reports Failed; retries every 5 minutes switch only unreached plugs, including PIN plugs after a restart with no grant; notes survive a restart; editing the list mid-mode doesn't change what's put back; turning Show off drops the note. **Two modes** (a test mode contribution standing in for Party): A then B on the same plug, ending A-then-B and B-then-A, with the same and with opposite settings: the plug ends at the state from before A, and while one mode is still on it shows that mode's setting; a second contribution shows a second column with no other change.
- **Holiday** (extending Plan 11's tests): the plugs step in order, the sheet's plug lines, put back at tap-off, a failed plug doesn't stop Holiday mode; with the heating, a plug and the lights all failing at tap-off, the tile shows the three lines in that order; while on, it still shows one line.
- **Captures**: a power and an energy sensor's states are blanked; a temperature sensor's is kept.
- **TalkBack** words for the rows, the mode chips and the Settings switches.

## 8. Rollout

**The plugs are real devices: no plug is switched on or off, from any build or test, without the user's go each time.** Tests use MockWebServer and fakes.

### 8.1 Emulator

On the `culvery-5` AVD (debug build; it keeps Plan 11's Home Assistant token): Settings' Plugs section against the household's Home Assistant, the Plugs tab's rows, the states and watts, the PIN setting, the Holiday column; then a Home Assistant restart (fault 5) and the emulator offline (faults 6, 8, 10) once more. Switching a real plug, or running Holiday mode with a plug on On or Off, only on the user's go. Holiday mode with the plugs can also be walked with a fake `PlugSystem` in the debug build if the user prefers. *(Builder's choice how.)*

### 8.2 Checks against the real devices

1. The plugs added in Home Assistant (§3), including the router plug.
2. The Culvery user's `list_for_display` read on the household's instance: the plugs' main switches have platform `tplink` and no category, their LED and other switches have one, and the P110's power sensor shares the plug's device with translation key `current_consumption` (§4.1). Whether "Third-Party Compatibility" was needed. *(Revised 2026-10-28.)*
3. **Captures:** Plan 11's debug-only capture switch, names, ids and addresses replaced; fixtures from these after the names check.

### 8.3 On the tablet

A release `install -r`; never uninstall. Settings lists the plugs and the Plugs tab shows the ones turned on; switch one only on the user's go. Holiday mode with plugs is not turned on for real (as Plan 11b): its first real use is the check. The tablet's logs are read whenever it's plugged in while work is under way.

## 9. Not in this plan

- **Party mode's column and Party mode itself**: the next plan; it binds a `HouseModeInfo` and calls `enter` / `leave` (decision 7). Overlap with Holiday mode is already handled (decision 12).
- **Plugs joining Holiday lights** (decision 10): Feature ideas.
- **The rail redesign** (decision 9): Feature ideas.
- **Plug buttons in the One-tap tray**, energy history beyond the current watts, schedules or timers per plug: not asked for. *(My call.)*
