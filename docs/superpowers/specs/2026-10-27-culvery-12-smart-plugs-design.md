# Culvery 12: Tapo smart plugs

Brainstormed 2026-10-27. Follow-ups file: `docs/superpowers/plans/2026-09-23-plan1-followups.md` ("Feature ideas": Tapo smart plugs). Builds on Plan 11 (`docs/superpowers/specs/2026-10-26-culvery-11-hoover-heating-holiday-design.md`): the Home Assistant link, `:core:house` and Holiday mode.

Branch `plan-12-smart-plugs`, from `main` at 82b81db9, in the one `culvery` checkout (no worktree). One plan.

## 1. Purpose and outcome

The house has TP-Link Tapo smart plugs: a P110 (it reports power) on an appliance, a P100 on the router (not discoverable at the moment), and two more P100s coming for party lights. Party mode is the next plan and will switch chosen plugs; Holiday mode (Plan 11b) is already built.

The family want:

- **a Plugs tab on the rail**: every plug with its state, switched on and off straight from the wall;
- **modes that switch plugs**: for each mode, each plug is left alone, switched on or switched off, and **goes back to how it was when the mode ends**;
- **the plugs ready for Party mode**: the mode lists are built so that Party mode adds its own list and nothing else changes.

Done means: the household's plugs show on the Plugs tab through Home Assistant and switch from it; Holiday mode switches the plugs in its list on turning on and puts them back on turning off; the final review, a local merge and the release installed on the SM-T510 with `install -r`.

## 2. Decisions (the user, 2026-10-27)

1. **Culvery never assumes which plugs it may control.** Every plug is offered the same way, the router included ("maybe I'll use it for something else"). No plug is special-cased by its name, its model or what it powers. Whatever a plug does is the household's choice on the Plugs tab.
2. **Settings › Other integrations keeps only the Home Assistant link.** Everything about the plugs (which ones, their PIN setting, what each mode does) is on the Plugs tab.
3. **A new Plugs tab on the rail** shows each plug's state and **switches it on and off directly**.
4. **Per mode, each plug is Leave, On or Off.** Leave is the default for a newly added plug and for a newly added mode, so a plug is never switched by a mode until someone says so.
5. **Layout: by mode.** The tab has one section per mode, each listing every plug with Leave / On / Off. More modes will come; each adds one section. (Weighed against one row per plug with a choice per mode, which widens with every mode.)
6. **When a mode ends, each plug it switched goes back to how it was just before the mode started.**
7. **Plan 12 wires Holiday mode only.** The Party mode plan adds its own section. The lists are built so a new mode just plugs in (§5.1).
8. **A per-plug "Needs a PIN to switch" setting**, off by default, chosen by the household. *(Proposed in the brainstorm mockup and accepted, 2026-10-27.)*
9. **The rail is filling up**: a small redesign of it is likely soon (to Feature ideas; not in this plan).
10. **Holiday lights may use plugs later** (a lamp on a plug joining the mimic-presence lights). Not in this plan; to Feature ideas.
11. **The PIN that turns a mode on covers every plug in its list for as long as the mode is on**, restarts included. "Needs a PIN to switch" applies only to switching a plug by hand on the Plugs tab. *(The user, after the builder check.)*
12. **Two modes on at once:** each plug's state is remembered from before the **first** mode that touched it, and it is put back only when the **last** of those modes ends. *(The user, after the builder check.)*

The brainstorm's mockups (`.superpowers/brainstorm/`, not committed): the tab with plug cards on the left and the mode sections on the right, v2 with Leave / On / Off. Visual details are provisional until the end-of-v1 design review.

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
- Check the Culvery Home Assistant user can see the plugs' entities (it's a non-admin user; Plan 11 §3).
- Note the real entity names for the switch and the P110's power sensor, for §4.2 and the fixtures. No names go into committed files.

## 4. The plugs

### 4.1 Modules

Names are the builder's to adjust, within `ModuleBoundaries` as Plan 11 §4.2 left them.

- **`:capability:plugs`**: `PlugSystem` — `plugs: Flow<List<PlugState>?>` (for each added plug: its entity id, name, on / off / unreachable, and watts when it reports power) and `switch(entityId, on): PlugResult`. The Plugs tab (§4.3), the plug settings store (§4.5), and the `HousePlugs` implementation (§5).
- **`:provider:plugs-homeassistant`**: implements `PlugSystem` against Plan 11's shared `HomeAssistantClient`.
  - Switching: `switch.turn_on` / `switch.turn_off` with the entity.
  - State from the switch entity's state; `unavailable` (or missing) is unreachable.
  - **Watts: found by name** in `/api/states`, the way Plan 11 finds the hoover's battery: the P110's current-consumption sensor named after the switch's object id. No such sensor, no watts. No entity-registry read. *(My call; the builder confirms the naming against the household's instance, §8.2.)*
  - Updates as Plan 11 §4.3: the socket while the Plugs tab or Home shows, a full read on connect; commands over REST with a 5 s timeout; off screen (Holiday mode's steps), REST reads every 5 s for up to 30 s to see the change.

### 4.2 Which plugs Culvery shows

- **Add a plug** on the Plugs tab lists the `switch.*` entities Home Assistant has that aren't added yet, by their Home Assistant name. The person picks one. *(My call: the integration's extra switches, such as each plug's LED, would clutter the tab if every switch showed; Culvery can't tell a plug from them without the registry, so the household chooses. This keeps decision 1: Culvery doesn't filter by name.)*
- **Remove** takes a plug off the tab and out of every mode's list.
- Adding and removing need an Adult or Admin PIN (§4.4).
- The name shown is Home Assistant's friendly name, read live; Culvery stores only the entity id.

### 4.3 The Plugs tab

A capability tab (`hasTab`), rail order after Door *(my call: order 55)*, icon a plug (`power` or similar). It shows **once Home Assistant is linked**, so that Add a plug has somewhere to live; with no plugs yet it says "No plugs yet. Add one from Home Assistant." with **Add a plug**. *(My call.)*

- **Header:** "Plugs", and "{on} of {n} on · Home Assistant" in muted, as the Lights tab's header. **Add a plug** sits on the right.
- **Left: a card per plug** in a grid that grows like the Lights tab's rooms (note 20): the name, its state ("On · 182 W", "On", "Off", "Can't reach this plug"), and a large On / Off button. An on plug's card uses the accent fill, as an on room does. A plug set to need a PIN shows a padlock beside its button. An unreachable plug's button is inactive.
- **Right: a section per mode** (Plan 12: Holiday mode only), titled with the mode's name. Each section lists every added plug with a three-way **Leave / On / Off** choice. **Edit** opens the sections for changes behind an Adult or Admin PIN; at rest they are read-only, so a stray tap changes nothing. *(My call on Edit.)*
- **A plug's own settings** (a long press or a › on its card, the builder's choice): **Needs a PIN to switch** (off by default) and **Remove**, behind an Adult or Admin PIN.
- Switching shows "Switching…" on the card until Home Assistant reports the change, not before, as Plan 11's hoover row. A failure shows a toast with the §6 line and the card keeps its real state.

### 4.4 Who can do what

New permissions, as Plan 11 declared `hoover.start`:

- **`plugs.switch`**, "Switch plugs that need a PIN", default roles Admin and Adult, **a fresh PIN every time** (as Start the hoover). Plugs without the setting switch for anyone, with no PIN.
- **`plugs.manage`**, "Add, remove and set up plugs", default roles Admin and Adult, a fresh PIN: Add a plug, Remove, Needs a PIN to switch, and Edit on the mode sections. *(My call: one permission for all set-up on the tab.)*
- A Child's PIN is refused for both.

### 4.5 The plug settings store

The capability's own store: the added plugs in order (entity ids), each plug's PIN setting, and each mode's list (mode id → entity id → On or Off; a plug not in a mode's list is Leave). The notes (§5.2) and each running mode's snapshot live here too. Excluded from cloud backup and device transfer, as Plan 11's stores are. *(My call on the shape.)*

### 4.6 TalkBack

- Card: "Plug {name}, on, 182 watts. Switch off, button." / "Plug {name}, off. Switch on, button." / "Plug {name}, can't reach this plug." With the PIN setting: "… Switch on, needs a PIN, button."
- Mode row: "Holiday mode, {name}, leave alone, selected." and so on for On and Off.

## 5. Modes

### 5.1 How a mode plugs in

`:core:house` (Plan 11 §5.1) gains:

- **`HouseModeInfo`**: a small contribution with the mode's id, its name as shown ("Holiday mode") and an order. A mode's capability binds one into a Hilt set. Holiday binds `holiday`; Party mode will bind `party`. The Plugs tab shows one section per contribution, in order.
- **`HousePlugs`**, implemented by `:capability:plugs`:
  - `ready: Flow<Boolean>`: true once at least one added plug has On or Off in any list *(builder may narrow it to the given mode)*.
  - `preview(modeId): List<PlugChange>`: the plugs the mode will switch and to what, for a sheet's "will:" lines.
  - `enter(modeId): HouseStep`: take the mode on (§5.3). Safe to call again: a retry switches only plugs not yet reached.
  - `leave(modeId): HouseStep`: let it go (§5.4).
  - Optional (bound or not), like Plan 11's other seams.

A new mode needs only its contribution and calls to `enter` and `leave`; the Plugs tab, the store and the put-back logic don't change. *(My call on the shape; decision 7.)*

**No grant on `enter` or `leave`** (decision 11). The mode's own fresh PIN at turn-on and turn-off is the authority; the mode's capability checks it (as Holiday already does) before calling. A retry after a restart, when Holiday has forgotten its grant (`HolidayHouse` keeps it in memory only), still switches plugs, including those set to need a PIN. This differs from the lights step, which Plan 11 marks missed after a restart.

### 5.2 The memory: one note per plug

Each plug has at most one **note**, kept in the plug store so it survives a restart:

- **before**: the plug's state just before the first mode that touched it (on or off);
- **holders**: the modes that currently hold it, in the order they took it, each with what it set the plug to;
- **expected**: what the plug should be now (the setting of the newest holder).

A mode's list is **snapshotted when the mode turns on** (stored with the mode's place in the notes); edits to the list while it's on take effect next time. *(My call.)*

### 5.3 Turning a mode on

`enter(modeId)`, for each plug in the mode's snapshot that isn't Leave:

1. **No note yet:** read the plug's state, write the note (before = that state, holders = this mode, expected = its setting), then switch it if it differs. A plug already as the mode wants is noted and not switched.
2. **Already noted by another mode** (decision 12): add this mode as the newest holder; expected becomes this mode's setting; switch if the plug differs. *before* doesn't change.
3. **Can't be reached:** retried every 5 minutes while the mode stays on (Plan 11 §5.3's rule for failed steps); its note is written when it's first reached. *(My call.)*
4. One plug failing doesn't stop the others or the mode; the step reports Failed with the §6 line.

### 5.4 Turning a mode off

`leave(modeId)`, for each plug this mode holds:

- **Someone switched it by hand** while modes held it (its state differs from *expected*): drop the whole note and leave the plug as they set it. *(My call, 2026-10-27, played back to the user: a hand change wins over every mode.)*
- **Other modes still hold it:** remove this mode from the holders. If this mode was the newest, expected becomes the next newest holder's setting and the plug is switched to it; otherwise nothing is switched. *(My call: the mode still on keeps its say.)*
- **This was the last holder:** switch the plug back to *before*, and drop the note.
- **Unreachable now:** it can't be switched; the step reports Failed and the note is dropped as if it had gone back. *(My call: the family are home and can switch it from the tab.)*
- **Never reached** while the mode was on: there's no note to act on.

**Removing a plug** from the tab drops its note; the plug is left as it is. *(My call.)*

### 5.5 Holiday mode's part

- **Turning on** (Plan 11 §5.3): a new step after Heating: `HousePlugs.enter("holiday")`. Holiday's record (Plan 11 §5.7) gains a plugs step, done or not, retried every 5 minutes like the others.
- **The sheet's "Holiday mode will:"** lists the plug changes in plain words, only for plugs not on Leave: "Switch {name} off." / "Switch {name} on." When the plugs go back, it says "Plugs go back as they were when you turn it off." *(My call.)*
- **Turning off** (Plan 11 §5.4): `HousePlugs.leave("holiday")` after the heating. **Plugs go back at tap-off**, not at 00:01 on the back day as the heating does: decision 6 ties putting back to the mode ending. *(My call, played back to the user.)*
- Failure lines on the tile, §6.

## 6. When things go wrong

Plan 11 §6's lines cover Home Assistant itself (can't reach it, offline, token refused). New:

| What happens | How Culvery notices | What it says |
|---|---|---|
| A plug can't be reached | Its switch entity is `unavailable` | On the card: "Can't reach this plug." Toast on switching: "Can't reach {name}. Check it's plugged in and the Tapo app can see it." |
| An added plug is gone from Home Assistant | Not in the states | On the card: "Home Assistant has no plug called that any more. Remove it or add it again." |
| A switch didn't happen | No change within 30 s | Toast: "{name} didn't switch. Try again, or use the Tapo app." |
| Holiday mode couldn't switch a plug | §5.3 | On the tile: "{name} didn't switch {off}. It'll keep trying." (one plug) / "2 plugs didn't switch. They'll keep trying." |
| A plug couldn't be put back | §5.4 | On the tile: "{name} didn't switch back {on}. Switch it on the Plugs tab." |

**Privacy.** Logs carry the call and its outcome only: no entity ids, no plug names (a plug can be named after a person or a room), no watts. `LogHygieneTest` covers the new modules. The names show on screen only.

## 7. Tests

- **JSON** (JVM): fixtures for switch entities on, off and `unavailable`, a P110 power sensor and a plug without one, extra non-plug switches. From Home Assistant's documented shapes first, then redacted captures from the household's instance (§8.2).
- **Provider** (MockWebServer): `switch.turn_on` / `turn_off` paths and bodies, timeouts, the 30 s check, watts found by name or absent.
- **Plugs tab** (Robolectric; Roborazzi for: no plugs, four plugs mixed on / off / unreachable / PIN, the Holiday section at rest and in Edit): switching a plain plug with no PIN; a PIN plug asks for a fresh Adult or Admin PIN and refuses a Child's; Add a plug lists only switches not yet added; Remove takes the plug out of every list; Edit asks for a PIN; the tab shows once Home Assistant is linked.
- **Modes** (with a test clock): enter notes then switches; a plug already in the mode's state is noted and not switched; Leave plugs untouched; leave puts back to *before*; a hand-switched plug is left and its note dropped; a never-reached plug is left; unreachable at leave reports Failed; retries every 5 minutes switch only unreached plugs, including PIN plugs after a restart with no grant; notes survive a restart; editing the list mid-mode doesn't change what's put back; Remove drops the note. **Two modes** (a test mode contribution standing in for Party): A then B on the same plug, ending A-then-B and B-then-A, with the same and with opposite settings: the plug ends at the state from before A, and while one mode is still on it shows that mode's setting; a second contribution shows a second section with no other change.
- **Holiday** (extending Plan 11's tests): the plugs step in order, the sheet's plug lines, put back at tap-off, a failed plug doesn't stop Holiday mode.
- **TalkBack** words for the cards and mode rows.

## 8. Rollout

**The plugs are real devices: no plug is switched on or off, from any build or test, without the user's go each time.** Tests use MockWebServer and fakes.

### 8.1 Emulator

On the `culvery-5` AVD (debug build; it keeps Plan 11's Home Assistant token): the Plugs tab against the household's Home Assistant, Add a plug, the states and watts, the PIN setting, the Holiday section. Switching a real plug, or running Holiday mode with a plug on On or Off, only on the user's go. Holiday mode with the plugs can also be walked with a fake `PlugSystem` in the debug build if the user prefers. *(Builder's choice how.)*

### 8.2 Checks against the real devices

1. The plugs added in Home Assistant (§3), including the router plug.
2. The real entity names for the switch and the P110's power sensor, so §4.1's naming holds; whether "Third-Party Compatibility" was needed.
3. **Captures:** Plan 11's debug-only capture switch, names, ids and addresses replaced; fixtures from these after the names check.

### 8.3 On the tablet

A release `install -r`; never uninstall. The Plugs tab shows the plugs; switch one only on the user's go. Holiday mode with plugs is not turned on for real (as Plan 11b): its first real use is the check. The tablet's logs are read whenever it's plugged in while work is under way.

## 9. Not in this plan

- **Party mode's section and Party mode itself**: the next plan; it binds a `HouseModeInfo` and calls `enter` / `leave` (decision 7). Overlap with Holiday mode is already handled (decision 12).
- **Plugs joining Holiday lights** (decision 10): Feature ideas.
- **The rail redesign** (decision 9): Feature ideas.
- **Plug buttons in the One-tap tray**, energy history beyond the current watts, schedules or timers per plug: not asked for. *(My call.)*
