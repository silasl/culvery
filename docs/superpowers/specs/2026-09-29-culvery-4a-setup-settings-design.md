# Culvery 4a: household setup and Settings (design)

**Date:** 2026-09-29
**Status:** Draft, for the user's review
**Parent spec:** `docs/superpowers/specs/2026-09-23-culvery-v1-design.md` (§7, §8, §9.5, §9.6, §10)
**Previous plan:** `docs/superpowers/specs/2026-09-28-culvery-3a-google-calendar-design.md` (3a, Google Calendar)
**Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (§8 says which items 4a takes)
**Builds on:** Plan 3a, merged to `main`.

## 1. Scope

Plan 4 is split into 4a (this spec), 4b (weather) and 4c (release and the on-device pass). 4a adds:
- `:core:setup`: the first-run wizard frame, the two-pane Settings frame, and the core pages (Welcome, Home location, You, Household, Kiosk);
- capability hooks so the calendar contributes its own wizard steps and Settings page;
- the People editor (add, edit, remove, roles, PINs) and a shared person-colour palette;
- home location by town search through Open-Meteo geocoding, in a new `:provider:weather-openmeteo` (geocoding only; 4b adds the forecast);
- the calendar's Connect step and the **Review calendars** editor (person, show/hide, master, disconnect);
- `calendar.db` v5 (the service's last-seen tick per calendar);
- Settings that stays open while in use;
- the debug seed reworked so a fresh debug install runs the real wizard.

## 2. Decisions (agreed with the user)

| # | Decision |
|---|---|
| D1 | **Split.** Plan 4 → 4a household setup and Settings, 4b weather, 4c release and on-device. 4a first. |
| D2 | **Location in 4a, by town search** through Open-Meteo geocoding (no key). Stores name, coordinates and time zone. **Skip for now** uses the tablet's time zone. |
| D3 | **The wizard reviews calendars after connecting**: a list with each calendar's person, show/hide and master, the same editor as Settings › Calendars. |
| D4 | **Settings is two panes**: sections on the left (Home location, People, Calendars, Kiosk), the chosen section on the right. Each right-hand pane is the wizard's matching step. |
| D5 | **Settings closes after 2 minutes without a touch**; every touch inside it keeps it open (follow-up m5). |
| D6 | **Fresh PIN** for removing a person, changing a role and setting, changing or removing a PIN (`people.manage`). Renaming and recolouring need only the open Settings session (`settings.manage`). |
| D7 | **Capabilities contribute pages** (approach A): `:core:setup` owns the frames and core pages; `Capability` gains `setupSteps()` and `settingsPages()`. |
| D8 | **When the wizard runs:** while `setupComplete` is false. Each step saves as it goes and the wizard resumes at the first unfinished step. An install that already has an active Admin is marked complete on first start of this version. |
| D9 | **No PIN before an Admin exists**; after the You step the new Admin stays signed in, with no timeout, until Done. |
| D10 | **Lock-task starts only after setup is complete**, so the first connect always happens outside the kiosk. |
| D11 | **Debug builds:** the seed no longer creates people or sets a master. The Welcome step has a debug-only **Use a sample household** button. |
| D12 | **Colours:** 8 person colours in `:core:ui`; a colour in use can't be picked again; Family keeps its amber. At most 8 people. |
| D13 | **Mapping changes are tablet-only** and apply at once. The daily refresh changes a calendar's visibility only when its tick in the service changes. |
| D14 | **Disconnect** removes the connection with its calendars, events and queued changes, after a confirmation that counts the queued changes. It doesn't revoke the grant in the Google account; the setup doc says where to. |
| D15 | **Deferred:** per-calendar health (M7) and the repeat-series cap (to 4c); reordering people. |

## 3. Architecture

### 3.1 Module `:core:setup`

Depends on `:core:plugin`, `:core:household`, `:core:access`, `:core:ui`. Holds:
- `SetupState`: `setupComplete: Flow<Boolean>` and `markComplete()`, in a small DataStore file of its own (not Room: it is one flag). On first start, if `setupComplete` is unset and `HouseholdRepository.credentials()` has an active Admin, it is set to true (D8).
- `LocationSearch` (interface): `suspend fun search(query: String): List<PlaceMatch>`; `PlaceMatch(name, region, country, latitude, longitude, timeZoneId)`. Throws `LocationSearchException` on network or HTTP failure. The implementation lives in `:provider:weather-openmeteo`.
- The wizard frame, the Settings frame and the core pages (§4).
- `SetupStep` and `SettingsPage` (below), with the core ones registered through Hilt `@IntoSet`, as capabilities are.

### 3.2 Contract additions (`:core:plugin`)

```kotlin
interface SetupStep {
    val id: String
    val order: Int                         // core steps 0–399; capabilities 400+ in rail order; Done 1000
    /** False hides the step (e.g. Review calendars before a connection exists). */
    val shown: Flow<Boolean> get() = flowOf(true)
    /** True once the step's required input is saved; drives Next and where the wizard resumes. */
    val done: Flow<Boolean>
    val skippable: Boolean get() = false
    @Composable fun Content(onNext: () -> Unit)
}

interface SettingsPage {
    val id: String
    val title: String                      // the left-hand list: "Home location", "People", "Calendars", "Kiosk"
    val order: Int
    @Composable fun Content()
}
```
`Capability` gains `fun setupSteps(): List<SetupStep> = emptyList()` and `fun settingsPages(): List<SettingsPage> = emptyList()`. `Capability.SettingsSection()` is removed; the calendar's current section becomes its Calendars page.

### 3.3 Wizard flow (D8–D11)

- `MainActivity` shows `SetupWizard` in place of the shell while `setupComplete` is false. The rail, Home and tabs aren't composed.
- Steps, in `order`: **Welcome** (0), **Home location** (100), **You** (200), **Household** (300, skippable), the calendar's **Connect** (400, skippable) and **Review calendars** (410, shown once a connection exists), **Done** (1000).
- Progress dots count shown steps. **Back** goes to the previous shown step. **Next** is enabled when the step's `done` is true or it is `skippable`; a skippable step not done shows **Skip for now** in place of Next.
- On start and after a kill, the wizard opens at the first shown step whose `done` is false (Welcome counts as done once passed; it keeps that in `SetupState`).
- **Done** calls `markComplete()`, ends the setup session and opens Home.

### 3.4 Access during setup (D9)

- `AccessControl` gains `beginSetupSession(person: Identified)` and `endSetupSession()`. A setup session doesn't time out and every `authorise` for that person passes without a PIN (including fresh-PIN permissions: the PIN was just set).
- Before the You step no Admin exists and the wizard's pages call the repositories directly, with no `authorise`. From the You step on, pages call `authorise` as they would in Settings, and the setup session satisfies it.
- The You step, on Next, creates the Admin (`addPerson` + `PinManager.setPin`) and calls `beginSetupSession`. Done calls `endSetupSession` and `lock()`.
- The setup session is in memory only. A wizard that resumes after a kill past the You step asks for an Admin's PIN once ("Enter your PIN to carry on setting up"), then begins the setup session again.

### 3.5 Settings session (D5, D6)

- `openSettings()` is unchanged (`authorise(settings.manage)`).
- While Settings is open, each touch inside it (a pointer-input observer on the Settings root) calls `AccessControl.touch()`, which restarts the session's 2-minute timer. `settingsOpen` still closes when the session ends.
- People actions: rename and recolour call `authorise(settings.manage)` (already satisfied); remove, role change and PIN set/change/remove call `authorise(people.manage)`, which asks for a fresh PIN.

### 3.6 Kiosk (D10)

`pinToScreen()` runs only when `setupComplete` is true. When setup completes, `MainActivity` pins on the next resume. Settings › Kiosk holds **Exit kiosk** (moved from the Settings footer).

### 3.7 People (`:core:household`, `:core:access`)

- `PersonPalette` in `:core:ui`: 8 colours: the hand-off's `#4CB387`, `#5B9BE0`, `#E07BA8` and five extras chosen in the plan to be distinct from each other and from Family's `#E0A85B` in light and dark themes.
- `HouseholdRepository`:
  - `addPerson` rejects a blank or duplicate name (case-insensitive, trimmed) with `DuplicateNameException`, and a colour already in use with `ColourInUseException`; reads `sortOrder` inside the transaction (follow-up).
  - `updatePerson` applies the same name and colour rules.
  - `setRole`, `setPinHash`, `clearPin` reject Family (tests, follow-up).
- After a remove, role change or PIN change of the person in the session, `AccessControl.lock()` (follow-up).

### 3.8 Location (D2)

- `:provider:weather-openmeteo` binds `LocationSearch`: `GET https://geocoding-api.open-meteo.com/v1/search?name={q}&count=5&language=en&format=json` with the app's OkHttp client, cancellable as in 3a. Results with no `timezone` are dropped.
- The page searches after 2 or more characters and 400 ms without typing; a new query cancels the old one.
- Saving calls `HouseholdRepository.setLocation`. A change of time zone asks the calendar to sync (`CalendarSetup.syncSoon()`), through a `HouseholdZone` change listener in the calendar capability.
- Nothing logs the query, the place name or coordinates.

### 3.9 Calendar pages (D3, D13, D14)

- **Connect step:** the existing connect card (`ConnectCardHost` with its connector), plus **Skip for now**. `done` is true once any connection exists.
- **Review calendars** (wizard step 410 and the Calendars Settings page): §4.5.
- `CalendarStore` gains `setMapping(connectionId, sourceId, person: PersonId, visible: Boolean)` and `remapMissingPeople(existing: Set<PersonId>)` (any source mapped to a person not in the set → Family). `setMaster` and `removeConnection` exist.
- The master is always visible: `setMapping(visible = false)` on the master is refused; setting a hidden calendar as master makes it visible.
- The calendar capability collects `household.people` on the app scope and calls `remapMissingPeople` on each emission.
- **Disconnect** counts the connection's outbox rows, confirms, then calls `removeConnection`.

### 3.10 Storage (`calendar.db` v5, D13)

- `source` gains `shownInService INTEGER` (nullable; null = not yet seen). `MIGRATION_4_5` adds it and copies `visible` into it.
- `refreshSources`: for a source already stored, when the service's `shown` differs from `shownInService`, set `visible = shown` (the master stays visible) and store the new `shownInService`; otherwise leave `visible` as the user set it. New sources: as today.

### 3.11 Debug builds (D11)

- `DebugSeed` no longer creates people, PINs or a master. It still registers the fake provider (debug-only, `userConnectable = false`).
- **Use a sample household** (debug Welcome only): creates Alex (Admin, 1234), Sam (Adult, 2468), Mia (Child, 1357) with the palette's first three colours, London (51.5074, −0.1278, Europe/London), connects the sample calendar with its per-person mapping and the Family master, and marks setup complete.
- `DebugSeedTest` and the Roborazzi tests that relied on the seed build their household in the test.

## 4. Screens

All in the hand-off's visual language, light and dark, using `ShellTokens` and `HhType`. Layout numbers live in a `SetupDimens` object; none inline.

### 4.1 Wizard frame
Full screen. Progress dots at the top centre; the step's content in a centred column 720 dp wide; **Back** (left) and **Next** / **Skip for now** (right) as pill buttons at the bottom. Welcome has no Back.

### 4.2 Welcome, You, Household, Done
- **Welcome:** "Welcome to Culvery", one line on what setup covers ("Your home's location, the people who live here, and your calendars."), **Start**.
- **You:** "Who's setting this up?" Name, colour swatches, then **Set your PIN** (PIN pad twice: "Choose a 4-digit PIN", "Enter it again"). Mismatch: "Those PINs didn't match — try again." Next creates the Admin.
- **Household:** "Who else lives here?" The people list (§4.4) with **Add person**. Next is always enabled ("Skip for now" when only you exist).
- **Done:** "Culvery is ready", **Open Culvery**.

### 4.3 Home location
"Where's home?" A search field; up to 5 result rows ("Canterbury, England, United Kingdom"); the selected one ticked. Below: "Used for the time zone, and for weather." Errors: "Couldn't search for towns — check the tablet's Wi-Fi and try again." No match: "No towns match "{query}"." Settings shows the current location above the field.

### 4.4 People
- **List:** colour dot, name, role, "PIN set" / "No PIN"; tap to edit; **Add person**.
- **Editor sheet** (right side, 600 dp, HhSheet): **Name**; **Colour** swatches (taken ones shown with a line through and disabled); **Role** chips with a line each ("Admin — can change settings and people", "Adult — can add and change any event", "Child — can add their own events"); **PIN**: **Set PIN** / **Change PIN** / **Remove PIN**; **Save person** (new) or **Save changes**; **Remove person** (existing, not yourself while you're the last Admin).
- **Messages:** "Someone is already called {name}.", "That colour is taken.", "That PIN is taken — choose another.", "An Admin needs a PIN.", "Culvery needs at least one Admin with a PIN.", remove confirmation "Remove {name}? {name}'s events and calendars show as Family." with **Keep** / **Remove person**, toast "{name} removed".
- The shared chip, text field and swatch components are promoted from `:capability:calendar` to `:core:ui` where the calendar already has them (`ChoiceChip`, the text field, `PersonChip`).

### 4.5 Review calendars
- **Title:** "Your calendars" (wizard), "Calendars" (Settings).
- **Per connection:** "{Service} · {account}", its health line ("Synced 2 min ago" / "Needs reconnecting" / "Can't reach {Service}"), **Reconnect** when needed, **Disconnect**.
- **Per calendar row:** name; person chip (tap → a row of Family + people chips); **Show** switch; "Master" badge on the master, with "New events go here" and its switch disabled; **Make master** on other writable calendars.
- **Toasts:** "{calendar} now shows as {person}", "{calendar} hidden" / "{calendar} shown", "New events now go to {calendar}".
- **Disconnect confirmation:** "Disconnect {Service}? Its calendars leave the tablet." plus ", and {n} changes still waiting to sync are dropped." when n > 0; **Keep** / **Disconnect**; toast "{Service} disconnected".
- **Master cleared** toast (3a D7) becomes "{Service}: can't find the master calendar — choose a new one in Settings › Calendars."

### 4.6 Settings frame
Full screen over the shell (as today). Left column 320 dp: "Settings", the page titles in order (Home location 0, People 100, Calendars 400, Kiosk 900), the selected one highlighted; **Close** at the bottom. Right: the page. The first page is selected on open.

### 4.7 Kiosk page
"Kiosk" with **Exit kiosk** (fresh PIN, as today) and a line: "Culvery keeps the tablet on this app. Exit to use other apps; it locks again next time Culvery opens."

## 5. Errors and offline

- Location search offline or failing → the message in §4.3; the wizard's **Skip for now** stays available.
- Connect failing → 3a's messages; the step stays.
- A store failure while saving a person or a mapping → "Couldn't save — try again." and nothing changes. Every save goes through a `SingleAction`-style guard so a double tap saves once.
- `LastAdminException`, `PinInUseException`, `DuplicateNameException`, `ColourInUseException` → their messages in §4.4, shown in the sheet, input kept.

## 6. Where the decisions are ambiguous, and what was chosen

- **A person removed while mapped to a calendar and tagged on events:** calendars go to Family (§3.9); tagged events already fall back to their calendar's person.
- **Removing yourself:** allowed only if another active Admin exists; the session then ends.
- **The Welcome "done" state:** kept in `SetupState` so a kill after Welcome resumes at Home location.
- **Two Google connections:** unchanged from 3a (one connection per account); the review page lists each.
- **Time zone with no location:** `HouseholdZone` falls back to the device zone, as today.

## 7. Testing

- **Unit (Robolectric):** `SetupState` (migration to complete with an active Admin, resume point); wizard navigation (shown/done/skippable, Back, resume after kill); setup session (no PIN, no timeout, ended at Done); Settings touch keeps the session; people rules (duplicate name, colour in use, last Admin, fresh PIN on the sensitive actions only, lock after changing the signed-in person, Family rejected); `LocationSearch` against `MockWebServer` (parse, no-timezone dropped, error → exception, cancellation cancels the call, no logging of the query); `setMapping`, master always visible, `remapMissingPeople`, `refreshSources` keeping a user's hide until the service's tick changes; `MIGRATION_4_5` with the driver-based `MigrationTestHelper`; disconnect counts queued changes; debug **Use a sample household** builds the same household the old seed did.
- **Roborazzi**, light and dark: each wizard step, the people list and editor (with errors), location search (results, error), Review calendars (OK, needs reconnecting, master), the Settings frame on each page.
- **Emulator walkthrough** with the user's Google account after clearing the app's data: the whole wizard with real names, then Settings edits (rename, recolour, role change with fresh PIN, remove a person mapped to a calendar, hide a calendar and check it stays hidden after a restart, change the master, disconnect and reconnect).

## 8. Follow-ups: what 4a does

Takes: m5 (Settings session); `AccessControl.lock()` from the people editor; `addPerson`'s `sortOrder` in a transaction and the Family rejection tests; the debug seed's Admin check (replaced by D11); H2's master re-selection (§4.5); the zone-change resync (§3.8); the "Connect-a-calendar card only ever checked by tests" item (a fresh debug install now shows it).
Leaves for 4c: the lockout clock guard, `LockoutStore.commit()`, `kioskExited` on recreate, insets after Exit kiosk, the font subset, every on-device item, L3 (fresh PIN for connecting; D10 makes the first connect happen outside kiosk), M4, M7, the repeat-series cap, `TodayCardHost` recomposition, the accessibility pass.

## 9. Review focus

- A kill at every wizard step resumes at the right step with nothing duplicated (no second Admin, no second connection).
- The setup session can't outlive setup: after Done, or after a kill before Done, the next start has no silent Admin session.
- An existing install (Admin present) never sees the wizard; a fresh one always does.
- The daily refresh never re-shows a calendar the user hid unless its tick in the service changed.
- No PIN, place name, coordinates or account email reaches a log.

## 10. Out of scope

Weather and the forecast (4b); release signing, the release OAuth client, lock-task on the device (4c); reordering people; per-calendar health; revoking the Google grant from the app; editing role → permission mappings; more than 8 people.
