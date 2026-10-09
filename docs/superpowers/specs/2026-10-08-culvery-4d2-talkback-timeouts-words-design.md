# Culvery 4d-2: TalkBack, timeouts and words (design)

**Date:** 2026-10-08
**Status:** Approved design (user rulings 2026-10-08); amended at planning (2026-10-09: a sheet with unsaved changes outside Settings gets the 20-second warning too, Decision 4 and §6.3); amended at plan review (2026-10-09, user: "all as recommended", and decision A on the PIN pad refusal): in-page messages are always-present lines (§2, §4.4), the session ends by the wall clock after the tablet sleeps and keeps its hold through an authorise, "Time to take action" is capped at the edit hold, no overlay closes by itself inside Settings or while saving (§6.1–§6.3), a refused PIN names no one and counts as a wrong one (§7.1.2, C64), and §9's tasks 10, 11 and 13 are each split in two. Plan: `docs/superpowers/plans/2026-10-09-culvery-04d2-talkback-timeouts-words.md`.
**Parent spec:** `docs/superpowers/specs/2026-09-23-culvery-v1-design.md`
**Previous plan:** `docs/superpowers/specs/2026-10-06-culvery-4d1-shared-parts-contrast-design.md` (4d-1, merged)
**Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md`, sections "For Plan 4d-2 (TalkBack, timeouts, words)" and "From Plan 4d-1 (deferred)".
**Builds on:** `main` at 4d03211. File and line references below are as of that commit unless they say otherwise.

## 1. Goal

Make Culvery usable with TalkBack, make its timers fair to everyone, and make its words plain and consistent.

- **TalkBack.** Every control says what it is and what it does. Overlays hide what is behind them and announce themselves. Results that arrive later (toasts, errors, search results) are spoken. Lists, chips, pickers, the PIN pad and the week read as a person would describe them.
- **Timeouts.** Settings, overlays left open, the week view’s return to this week and toasts all count TalkBack activity as activity, give people with "Time to take action" set the time they asked for, and lose nobody’s typing without warning.
- **Words.** One name per thing, with no system words (master, kiosk, sync, read-only, assign, feed). Errors say what went wrong and what to do. Numbers are spelt in full words, sentences end with a full stop, and quotes and apostrophes are curly.

The 4d audits (2026-10-06) were triaged into D1–D41. 4d-1 took D1–D7 and D9–D14. This spec takes D15–D41. Item ids are the triage’s (D, W, E, X), the copy audit’s (C), the accessibility audit’s (A) and the screens audit’s (S). Those audits live in `.superpowers/audit-4d/`, which is not committed and goes after 4d-2, so everything this plan needs from them is copied here: the copy table in Appendix A, the accessibility mapping in Appendix B.

## 2. Scope

**In (4d-2, about 14 tasks):**
- D15–D19: TalkBack basics (roles, labels, tabs, modal overlays, live regions, headings).
- D20–D28: names and states (fields, swatches, review rows, chips, pickers, PIN pad, week and Home, wizard dots, Move up and Move down).
- D29–D32: timeouts (Settings, the week view, toasts, overlays left open).
- D33–D41: words (the vocabulary, errors by cause, abbreviations and full stops, people and PINs, "Out of date", dead ends, location search, Tablet lock, smaller copy).
- The 4d-1 follow-ups addressed to 4d-2: the Forecast row’s semantics test, the close button, the ‹ › buttons and the steppers taking the quiet fill and `Role.Button`, and the reconnect chip’s label.
- The 4c code minor T14 (time in the add/edit overlay counts as idle) and its host-wiring test.
- A TalkBack check on the emulator, and a curly-quote and vocabulary scan test.

**Out:**
- **Layout and look.** Everything visual waits for the user’s end-of-v1 walk-through (W1–W34): the Move arrows’ pill (W27), the week legend’s wrap (W33), the Tablet lock status row (W29), the review rows’ heights and the "Always shown" text (W31), the location hint’s place (W18), the failure card’s styling (W19), and so on. 4d-2 adds only what its items need in order to be seen or heard: the "Searching…" and "Try again" lines, the closing warning pill (Settings, and a sheet with unsaved changes), the "Out of date" icon, a disabled Move button at the list’s ends, the how-to line on Tablet lock, and the person sheet’s, You step’s and location pane’s message lines keeping one line while empty (§7.7, §6.2, §7.5, §5.8, §7.8, §4.4; the last amended at plan review).
- **4e (the SM-T510):** E1–E10, including E7, a TalkBack walk of every screen on the device, and E5, One UI’s name for the home-app menu.
- **Copy rows no D item took:** C25 (keep), C43 ("Pick date…" vs "Pick a date") and C49 ("Try again" → "Try saving again"). The triage assigned them nowhere (Appendix A.3).
- **The X list** (dropped in the triage); none is re-raised.

## 3. Decisions

| # | Decision |
|---|---|
| 1 | **Scope** (user, 2026-10-08). D15–D41 in one plan. Layout and look are out of scope and wait for the user’s end-of-v1 walk-through. |
| 2 | **Vocabulary** (user; triage Decision 1, all eight recommendations accepted). a. "Main" calendar badge; button "Use for new events"; "Choose another main calendar first". b. The Kiosk page becomes "Tablet lock"; its button and the PIN line say "Use other apps". c. "Updated … ago", "Saving to Google Calendar…" and "will be lost". The week subtitle drops "Family calendar" and reads "Updated from Google Calendar 5 minutes ago". d. "Google Calendar" in every visible string; `connectionLabel` becomes the service display name. e. Adding stays "Add event" everywhere (button, sheet, Save button), with the toast "Event added". Changing runs button "Change" → sheet "Change event" → "Save changes" → "Changes saved". f. "Choose who it’s for", including the permission and "Ask a grown-up to choose who this is for." g. "Can’t change here" replaces read-only, subscribed and feed, including the not-the-main-calendar case. h. "Free" becomes "Nothing on"; the Week chip becomes "See the week"; "Added from a phone" everywhere; "Keep" alone. |
| 3 | **Settings timeout** (user). Screen touches, TalkBack gestures (accessibility focus, click and scroll events) and scrolling all reset the timer. The base timeout is 2 minutes, or `AccessibilityManager.getRecommendedTimeoutMillis` when TalkBack is on. Unsaved changes in an open sheet stretch it to 10 minutes; after that Settings closes and the edits are lost, with no unlimited hold (a child is in the house). A spoken and shown warning, "Settings closes in 20 seconds. Touch the screen to stay.", appears 20 seconds before closing, once per countdown. When Settings closes, any open sheet closes too. |
| 4 | **Overlays left open** (user; triage Decision 4 A). Any sheet, picker or PIN pad with no unsaved changes closes after 2 minutes idle. A sheet with unsaved changes outside Settings is capped at 10 minutes, with the same spoken and shown warning 20 seconds before it closes: "This closes in 20 seconds. Touch the screen to keep your changes." *(Amended at planning, user 2026-10-09.)* The full-column add target in the week view stays (W28). |
| 5 | **Week snap-back** (user). Touches inside overlays count as activity. No snap-back while touch exploration is on. The recommended timeout applies. The week title gets a live region. This fixes the 4c code minor T14, with a host-wiring test. |
| 6 | **Toast durations** (user). Plain toasts about 4 s, errors and refusals about 6 s. Longer text scales up, capped at 8 s. With TalkBack on, the recommended timeout. |
| 7 | **TalkBack verification** (user). The controller drives the emulator with TalkBack on and reads the spoken output from the logs; the user types any PIN. Paths: Home, an event, adding an event, the PIN pad, Settings › People and the Settings warning. The user does a normal, non-TalkBack walk-through for the wording. Tests check role and label everywhere through a shared helper. The first task tries `enableAccessibilityChecks()` under Robolectric and records whether it works: adopted if it does, noted if it doesn’t. |
| 8 | **Curly quotes and apostrophes everywhere** (user; 4d-1 Decision 6). A source-scan test fails on master, kiosk, sync, read-only, assign or feed, or on a straight quote or apostrophe, in UI copy. How it tells copy from code is in §7.1.3. |
| 9 | **One activity signal** (this spec, §6.1). Every timer in the plan (the Settings session, the overlay idle close, the week snap-back, the PIN pad) reads one `UserActivity` signal from `:core:plugin`, fed by the shell’s pointer input and by TalkBack’s accessibility events. Today each timer has its own detector, and none sees TalkBack. |
| 10 | **Live region politeness** (this spec, §4.4). Assertive is used only for urgent toasts (errors and refusals) and the two closing warnings (Settings, and a sheet with unsaved changes, §6.3). Messages that appear in place, next to what the person just did (the editor’s failure card, the PIN pad’s error line, a sheet’s message), are Polite and set `error()` on the field concerned, so TalkBack finishes what it is saying first. |
| 11 | **Full stops** (this spec, from the copy audit’s punctuation rules and D35). Errors, toasts and body sentences end with a full stop, and so does any line that tells the reader to do something. Titles, labels, buttons, badges, chips and status lines don’t. So the toasts become "Event added." and "Changes saved."; "Updated 5 minutes ago" stays bare. |
| 12 | **Copy that names the service** (this spec). Visible copy names the service through `serviceNameOf` (the provider’s display name, "Google Calendar"), never `Connection.label` ("Google"). `EventUi.connectionLabel` goes, and `couldNotSave` and the drain toast take the service name. Stored connection labels are not migrated: they are no longer shown. |

## 4. TalkBack basics (D15–D19)

Path shorthand, as in the audits: `core/ui` = `core/ui/src/main/java/uk/co/siland/culvery/core/ui/`; `app/` = `app/src/main/java/uk/co/siland/culvery/`; `cal/` = `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/`; `wx/` = `capability/weather/…/capability/weather/`; `setup/`, `access/`, `plugin/` = the matching `core/*` package roots; `google/` and `fake/` = the two calendar providers.

Labels and descriptions written in this section already use the new vocabulary (Decision 2), so §7 doesn’t touch them again.

### 4.1 Roles and click labels (D15)

**Problem.** After 4d-1, `HhButton`, `HhListRow` rows, the reconnect chip, Today’s + and Move up and Move down have `Role.Button`; every other clickable has no role. Click labels say "Open" (TalkBack: "double-tap to Open") or repeat the visible text ("Close, double-tap to Close"). The theme toggle reads a bare "Auto" (A4, A6, A14, C5, C6, C34, C71, C72, C73c; Plan 2b-1 and 2b-2 follow-ups).

**Evidence (raw `clickable` calls at 4d03211).**
- core/ui: Controls.kt:134 (`HhChoiceChip`), :203 (`HhPersonChip`), :264 (`HhSwatch`); Shell.kt:111 (`HhCloseButton`, label "Close" equal to its icon’s description).
- cal/ui: TodayCard.kt:86 (Today’s +: "Add event" as both label and description), :101 (Today rows, "Open"); WeekView.kt:247 (the add hint), :261 (event chips, "Open"); Pickers.kt:105 (picker scrim), :221 (date cells), :238 (`RoundButton`, label equal to description), :264 (`StepButton`, the same).
- access/ui: PinPad.kt:179 (scrim), :242 (Cancel), :265 (backspace), :303 (digit keys).
- setup: PeopleUi.kt:93 (`MoveButton`, label equal to description); SettingsScreen.kt:104 (page items).
- app/shell/ui: NavRail.kt:67 (Settings), :84 (rail items); OverlayLayers.kt:78 (sheet scrim); StatusBar.kt:70 (Sign out, label equal to its text), :79 (theme toggle).

**Fix.**
- A helper in `core/ui` (`Clickable.kt`):

  ```kotlin
  fun Modifier.hhClickable(
      onClick: () -> Unit,
      label: String? = null,
      role: Role = Role.Button,
      enabled: Boolean = true,
  ): Modifier
  ```

  It is `clickable(enabled, onClickLabel = label, role = role, onClick = onClick)`. `label` is what TalkBack says after "double-tap to", so it names what happens, in lower case: "open event details", "find out why". It is left null when the control’s name already says it ("Add event", "Close", "Sign out").
- `HhButton` gains `spokenName: String? = null`, which replaces the label as the node’s name when a row of identical buttons needs a subject ("Disconnect Google Calendar, alex@example.com"). It must start with the visible text, so what a sighted helper reads out is what TalkBack users hear first; the test helper checks this. `HhListRow` gains `clickLabel: String? = null`.
- Every raw `clickable` above moves to `hhClickable` or to a role-specific modifier (§4.2, §5.2, §5.4), except the scrims, which lose their semantics (§4.3).
- Labels and names:

  | Control | Name | Click label |
  |---|---|---|
  | Today rows, Coming up rows, week chips | merged description (§5.6) | "open event details" |
  | Today’s + | "Add event" | none (was "Add event" twice) |
  | Week add hint | "Add event on Tuesday 7 October" (§5.6) | none |
  | Close (`HhCloseButton`) | "Close" (the icon’s description) | none (was "Close") |
  | Sign out | "Sign out" | none (was "Sign out") |
  | Theme toggle | "Theme: Auto" (or "Theme: Night", "Theme: Day") | "preview the night theme" or "preview the day theme" while on Auto; "go back to the automatic theme" while previewing |
  | ‹ › (week, date picker) | "Earlier week", "Later week", "Earlier weeks", "Later weeks" | none (was the same text again) |
  | Time steppers | §5.5 | none |
  | Move up, Move down | "Move Sam up", "Move Sam down" | none (was the same text again) |
  | People rows | "Sam" plus the subtitle (merged by `HhListRow`) | "change Sam’s details" |
  | Town results | the town | "use as home" |
  | PIN digit keys, backspace, Cancel | "1"…"0", "Delete last digit", "Cancel" | none |
  | Assign chips in the detail sheet (`HhPersonChip`) | "Sam" | "show as Sam’s event" |
  | The reconnect chip | its text, "Google Calendar needs reconnecting" | "reconnect" |
  | The rail’s Settings | "Settings" | none |

- The 4d-1 follow-up: `HhCloseButton`, the week’s ‹ ›, the date picker’s ‹ › and the time steppers draw `surf2` on `bg` or `surf` with no role (on light `bg` the close button’s fill is 1.04:1). They take `HhButton`’s quiet fill for their surface (`quietFill(on, colors)`) and `Role.Button`. This is the one look change in §4, agreed in 4d-1.
- The reconnect chip keeps its own composable (`dangerSoft`; 4d-1 follow-up), with the click label above.

**Tests.** A shared helper in `:core:ui`’s test fixtures (§8.2):

```kotlin
fun SemanticsNodeInteractionsProvider.assertClickablesHaveRoleAndLabel(useUnmergedTree: Boolean = false)
```

Over `onAllNodes(hasClickAction())`, each node must have a `Role`, a non-blank name (its text or content description), and no `OnClick` label equal to that name; where the name comes from `spokenName` it must start with the visible text. Each screen’s existing UI test calls it once per state it renders (Home, week, detail, editor, both pickers, the PIN pad, the person sheet, each wizard step, each Settings page, the status bar and the rail).

### 4.2 Tabs (D16)

**Problem.** The nav rail and the Settings page list have no Tab role; the rail has no selected state at all (A6, C73a, S19, Plan 4a follow-up).

**Evidence.** NavRail.kt:55-57, 75-99 (`RailItem` with a plain `clickable`); SettingsScreen.kt:66-68, 92-109 (`PageItem`: `semantics { selected }` and a plain `clickable`).

**Fix.** Each rail item and page item uses `Modifier.selectable(selected, role = Role.Tab, onClick = onClick)`, and the two parent Columns take `Modifier.selectableGroup()`. The rail’s Settings button stays outside the group with `Role.Button`. TalkBack reads "Home, tab, selected, 1 of 3" (Home plus one tab per capability).

**Tests.** `rail_home` is selected and has `Role.Tab`; `settings_page_location` the same when chosen; the helper in §4.1 passes.

### 4.3 Overlays hide what is behind them (D17)

**Problem.** When a sheet, Settings, a picker or the PIN pad opens, the shell stays in the semantics tree. A TalkBack double-tap is an accessibility action, not a pointer event, so the scrims don’t stop it: a user can swipe to the rail behind Settings and activate it unseen. Nothing announces that an overlay opened, and each scrim is a nameless focusable node ("double-tap to Close") (A1).

**Evidence.** OverlayLayers.kt:61-65 (shell, overlay, PIN pad and toasts are siblings), :73-84 (scrim `clickable` with only a click label); SetupWiring.kt:66-68 (`shell()` then `settings()` in the same Box); PinPad.kt:175-183 (scrim); Pickers.kt:98-111 (`PickerLayer` over the editor sheet); no `paneTitle` anywhere.

**Fix.**
- **Hide the background.** Whatever an overlay covers is wrapped in `Modifier.clearAndSetSemantics {}` while the overlay shows:
  - in `ShellLayers`, `shell()` while a sheet or the PIN pad shows, and the sheet layer while the PIN pad shows. `ShellLayers` gains `pinShowing: Boolean`, which MainActivity passes from `pinPrompt.request`;
  - in `AppContent`, `shell()` while Settings is open;
  - in `EventEditorSheet`, the sheet while a picker shows.
  The toast layer is never hidden, so live regions still speak.
- **Silent scrims.** The sheet, picker and PIN-pad scrims keep their pointer behaviour but carry no semantics (`clearAndSetSemantics {}`); every overlay has a labelled Close or Cancel.
- **Pane titles.** Each overlay’s root sets `paneTitle`, so TalkBack announces it when it appears:

  | Overlay | `paneTitle` |
  |---|---|
  | Settings | "Settings" |
  | Event detail sheet | "Event details" |
  | Add/change sheet | "Add event" or "Change event" (its title) |
  | Person sheet | "Add person", or the person’s name |
  | Date picker, time picker | "Pick a date", "Pick a time" |
  | PIN pad | "Enter your PIN" |
  | The You step’s PIN chooser | "Choose a 4-digit PIN" |
  | Connecting card | "Connecting to Google Calendar" |

**Tests.** With a sheet shown, `onNodeWithTag("rail_home").assertDoesNotExist()` and the sheet root has `SemanticsProperties.PaneTitle`; the same for Settings, the PIN pad over a sheet (the sheet’s nodes are gone) and a picker over the editor (the editor’s Save is gone). The scrims have no click action in the semantics tree.

### 4.4 Live regions (D18)

**Problem.** Nothing that appears later is spoken: toasts, the editor’s failure card, person, You and location messages, PIN errors, the Saving pill (A2, A3, C73b, A42 toast part, S20).

**Evidence.** Shell.kt:118-134 (`HhToast`, no live region); OverlayLayers.kt:93-109; EventEditorSheet.kt:358-378 (`FailureCard`); PersonEditorSheet.kt:219 (`person_message`); YouStep.kt:164 (`you_message`); LocationPane.kt:118-131 (results, no match, failed, save failed); PinPad.kt:218-227 (`pin_error`); EventDetailSheet.kt:197-212 (`SyncingPill`, given `e.connectionLabel` at :187).

**Fix (Decision 10).**
- `ToastMessage` gains `urgent: Boolean`; `Toaster.show(message, icon, urgent = false)`. `HhToast` merges its row and sets `liveRegion` to Assertive when urgent, else Polite. Urgent toasts are errors and refusals: every `couldNot…`, `COULD_NOT_SAVE`, the refusals from `Refusal.Toast`, `PeopleOutcome.Refused`, `refuseOtherWho`, the master-gone toast and the drain’s lost-changes toast. Everything else is plain.
- Polite live regions on: the editor failure card (merged, so title and body read as one), `person_message`, `you_message`, the location pane’s one status line (the "Searching…" line, no match, failed, save failed, and "3 towns found" or "1 town found" over a list, so the rows stay separately focusable; see the always-present lines below), `pin_error`, the detail sheet’s Saving pill, the week title (§6.4), the date picker’s range line and the time stepper values (§5.5), and the PIN dots (§5.5).
- `error(message)` on the field a message belongs to: the person sheet’s and You step’s name field for "Someone is already called Sam."; the PIN dots for "Wrong PIN — try again."

- **Always-present lines** *(amended at plan review)*. Compose announces a live region only when a node it already has changes, and leaves a node with no size out of the tree, so a zero-size announcer or a message composed only when it appears may never be spoken. The in-page messages are therefore one always-present line each (`HhMessageLine`, one line tall while empty, its text the message or nothing): `person_message`, `you_message`, and the location pane’s `location_status`, which holds "Searching…", no match, a failed search, a failed save, or nothing, and is described as "3 towns found" over a list. The toast, the two warnings, the editor’s failure card and the Saving pill appear new in their layers: an always-present node there would hide the shell under it from TalkBack, so the emulator check (§8.6) listens for each.

**Tests.** `toast` has `LiveRegion` Polite for a plain toast and Assertive for an urgent one; each tag above has `LiveRegion`; each message line is in the tree, with height, before it has a message; the name field has `Error` after a duplicate name. A unit test that each urgent call site passes `urgent = true` (a fake `Toaster` records it).

### 4.5 Headings (D19)

**Problem.** Apart from 4d-1’s `HhSectionLabel`, nothing is a heading, so TalkBack’s heading navigation finds almost nothing (A11).

**Evidence.** SetupUi.kt:19 (`StepTitle`); SettingsScreen.kt:64; TodayCard.kt:57; ComingUpCard.kt:59, 87 (day labels); ForecastCard.kt:62; ConnectCalendarCard.kt:46; WeekView.kt:119 (title), 193-202 (column headers: "Today" and "6" as two nodes); ReviewCalendars.kt:187; EventDetailSheet.kt:190 (sheet title); EventEditorSheet.kt:202; PersonEditorSheet.kt:166; Pickers.kt:128, 171; PinPad.kt:215 ("Who’s this?").

**Fix.** `Modifier.semantics { heading() }` on each. The week column header Row becomes one heading through `clearAndSetSemantics { heading(); contentDescription = … }`: "Today, Tuesday 6 October" for today, "Wednesday 7 October" otherwise (`EEEE d MMMM`, `Locale.ENGLISH` as the calendar’s other formatters). Coming up’s day labels read "Tomorrow, Wednesday 7 October" and "Thursday 8 October".

**Tests.** Per screen, `onAllNodes(isHeading())` is not empty; the week column header for today has the content description "Today, Tuesday 6 October" and is one node.

## 5. Names and states (D20–D28)

### 5.1 Text fields keep their name (D20)

**Problem.** `HhTextField` shows a placeholder only. Once there is text the field has no name: TalkBack reads "Alex, edit box". The event title has no label at all, only "What’s happening?" (A12, C41, C88, S7).

**Evidence.** Controls.kt:145-189 (`HhTextField`: `placeholder`, no semantics); callers PersonEditorSheet.kt:181, YouStep.kt:149, LocationPane.kt:117, EventEditorSheet.kt:220-228.

**Fix.** `HhTextField` gains `label: String` (required) and `error: String? = null`, set on the field as `semantics { contentDescription = label; if (error != null) error(error) }`. Nothing visible changes; the placeholder stays. Labels: the event title "Event name", the person sheet "Name", the You step "Your name", the location search "Town or city".

**Tests.** A filled field still has its label as content description; with an error it has `SemanticsProperties.Error`. TalkBack’s reading ("Name, Alex, edit box") is confirmed on the emulator (§8.6), as the audit asked.

### 5.2 Colour swatches (D21)

**Problem.** Swatches have no name, and "taken" shows only as a strike-through (A5, C89, S17 names).

**Evidence.** Controls.kt:252-295 (`HhSwatch`: `selected` and `clickable(enabled = !taken)`; `HhSwatchGrid` takes `taken: Set<Long>`); PersonPalette.kt:9-24 (no names).

**Fix.**
- `PersonPalette` gets names, in its order: Green, Blue, Pink, Violet, Lime, Red, Slate, Brown (`PersonPalette.nameOf(argb)`), and Family’s colour is "Amber". The colours don’t change.
- Each swatch uses `selectable(selected = chosen, enabled = !taken, role = Role.RadioButton)` with `contentDescription` = the colour’s name; a taken swatch has `stateDescription` "used by Sam". `HhSwatchGrid`’s FlowRow takes `selectableGroup()`.
- `HhSwatchGrid`’s `taken` becomes `Map<Long, String>` (colour to the name of who uses it); the person sheet builds it from `members`. The You step has nobody else, so its map is empty.

**Tests.** Every swatch has a name and `Role.RadioButton`; the chosen one is selected; a taken one is disabled with the state "used by Sam".

### 5.3 Calendars review rows (D22)

**Problem.** The Show switch is unnamed ("Switch, on"); the main calendar’s switch is disabled with no reason; the who-it’s-for chip reads as plain text; Disconnect, Make master and Hide this calendar repeat on every row with no subject (A13, A7 review chip, A14, C87a, C87b, S26 spoken reason).

**Evidence.** ReviewCalendars.kt:214-234 (`ConnectionHeader`), :236-298 (`SourceRow`: chip at :254, "Show" and `HhSwitch` at :255-257, Make master at :274, the person picker after it), :306-320 (`ReadProblem` with Hide); Controls.kt:298-319 (`HhSwitch`).

**Fix.**
- **Show.** The row’s Show text and switch become one `toggleable(value = visible, enabled = !isMaster && !busy, role = Role.Switch)` node with `contentDescription` "Show School terms"; `HhSwitch` gains a form with `onCheckedChange = null` for this, so the Material switch draws only. For the main calendar the node is disabled with `stateDescription` "always shown: it’s the main calendar".
- **Who it’s for.** The chip keeps `HhChoiceChip`’s look but has `Role.Button` (through §5.4’s `role` parameter), `onClickLabel` "change who School terms is for", and `stateDescription` "expanded" while its picker is open, "collapsed" otherwise. The picker’s chips are a radio group (§5.4).
- **A subject on each action** through `spokenName` (§4.1): "Reconnect Google Calendar, alex@example.com", "Disconnect Google Calendar, alex@example.com" (from `serviceAndAccount`), "Use School terms for new events", "Hide School terms".

**Tests.** A compose test on review rows: the Show node has `Role.Switch`, the name "Show School terms" and a toggle action; the main calendar’s is disabled with its state description; the chip has `Role.Button` and "collapsed", then "expanded" after a tap; Disconnect’s name contains the account.

### 5.4 Choice chips (D23)

**Problem.** Choice chips have no role or group; a disabled Who chip sounds like any other (A7, Plan 2b-2 follow-up).

**Evidence.** Controls.kt:106-143 (`HhChoiceChip`: `semantics { selected }`, plain `clickable`, alpha the only disabled cue); EventEditorSheet.kt:247-335 (the Who, Day, Time and Length sections); PersonEditorSheet.kt:198-206 (Role).

**Fix.**
- `HhChoiceChip` uses `selectable(selected, role = role, onClick = onClick)` with a new `role: Role = Role.RadioButton`. Each section’s FlowRow takes `selectableGroup()`.
- A disabled chip stays tappable (a tap explains why, 2b-2 design §4.2) and adds `stateDescription` "not available" and the click label "find out why". It doesn’t use `disabled()`, which would stop the tap.
- The two chips that open a picker, "Pick date…" and "Pick time…", have `Role.Button`, and `selected` when they hold the chosen value.

**Tests.** In the editor, Who chips have `Role.RadioButton` inside a selectable group; a disabled Who chip has the state "not available" and still runs its click; Pick date… has `Role.Button`.

### 5.5 Pickers and the PIN pad (D24, D25)

**Problem.** Date cells read "21", with no weekday, month, "today" or selected state; all four time steppers are "More" or "Less"; paging and stepping change nothing TalkBack says (A14, A15, A16, C55, C56). The PIN dots are silent, and the lockout countdown must not be spoken every second (A17, C67, A41).

**Evidence.** Pickers.kt:208-226 (`DateCell`), :129-137 (range line and ‹ ›), :244-251 (`Stepper`, "More" and "Less" at :248, :250), :253-268 (`StepButton`); PinPad.kt:86-101 (the countdown), :276-291 (`Dots`).

**Fix.**
- **Date cells.** `clearAndSetSemantics { contentDescription = "Tuesday 7 October" + (", today" if today); selected = isSelected; role = Role.Button; onClick { … } }`. TalkBack reads "Tuesday 7 October, today, selected". The range line ("Mon 21 Sep – Sun 25 Oct") is a Polite live region, so ‹ › are heard.
- **Steppers.** The buttons are "Hour up", "Hour down", "Minute up" and "Minute down". Each value is a Polite live region with `contentDescription` "Hour 7" or "Minute 30" (no leading zero, which TalkBack reads as "oh seven").
- **PIN dots.** The Row is `clearAndSetSemantics { contentDescription = "2 of 4 digits entered"; liveRegion = Polite }`, with `error("Wrong PIN — try again.")` while the dots are ringed in `danger`. "0 of 4 digits entered" when empty.
- **Lockout.** The visible line still counts down. Its semantics are fixed at the start of a lockout ("Too many tries — wait 30 seconds.", from the first value), so the live region speaks once and doesn’t tick.

**Tests.** A cell for today reads "Tuesday 7 October, today" and is selected when chosen; after ‹ the range line has a live region and new text; the stepper buttons have the four names; after a digit the dots read "1 of 4 digits entered"; during a lockout the error node’s content description doesn’t change while the visible seconds do.

### 5.6 Week and Home (D26)

**Problem.** Week chips say neither whose event it is nor which day; seven add hints are "Unlabelled"; the legend reads bare names; detail rows read label and value as separate items; the header weather is two items and never says the condition (A4 add hint, A8, A9, A10, A19, C7, C8, C23, C27, S34). The Forecast row clears its children, so its day label and icon are checked only by baselines (4d-1 follow-up).

**Evidence.** WeekView.kt:238-251 (`AddHint`: icon with no description), :253-279 (`EventChip`), :281-306 (`Legend`); TodayCard.kt:93-119 (`TodayRow`); ComingUpCard.kt:96-118 (`CompactRow`); EventDetailSheet.kt:242-256 (`InfoRow`); WeatherHeaderItem.kt:21-35; ForecastCard.kt:94 (`clearAndSetSemantics` with `rowDescription`); ForecastCardTest.kt:68.

**Fix.**
- **Spoken times.** `spokenTime(slice)` beside `sliceOn` (CalendarUi.kt:193-208): "09:00" for a start, "09:00 to 10:30" for a range, "from 22:00" for a first day of several, "until 01:00" for a last day, "all day". The en dash and trailing dash aren’t read well (C27).
- **Week chips** become one node: `clearAndSetSemantics { contentDescription = "Swimming, Sam, Tuesday 09:00"; role = Role.Button; onClick(label = "open event details") }`, with the badges’ words after it (", repeats", ", saving", ", can’t change here"). The day is the column’s (`EEEE`).
- **Today and Coming up rows** merge the same way: "Swimming, Sam, 09:00 to 10:30" (Today: the day is the card), "Swimming, Sam, Wednesday 09:00" (Coming up). Coming up rows aren’t clickable, so they have no role.
- **Add hints**: `contentDescription` "Add event on Tuesday 7 October", `Role.Button`.
- **The legend** is one node: "Colours: Alex, green; Sam, blue; Family, amber", using the palette names (§5.2). Whether to keep the legend at all now that chips carry names is walk-through W33.
- **Detail rows**: each `InfoRow` is `semantics(mergeDescendants = true)`, so "When, Today · 19:30–21:00" is one item.
- **Header weather**: the Row is `clearAndSetSemantics { contentDescription = "Now 17°, partly cloudy, high 19°, low 11°" }`, using `degrees` and `conditionWords` as `rowDescription` does. TalkBack reads ° as "degrees".

**Tests.** A week chip’s content description holds the title, the name and the day; an add hint has its date; the legend is one node; an `InfoRow` reads label and value together; in the Forecast card and the header, `onAllNodesWithText("°", substring = true).assertCountEquals(0)` and a forecast row has no children (`onChildren().assertCountEquals(0)`), closing the 4d-1 follow-up and A10.

### 5.7 Wizard dots (D27)

**Problem.** The dots are nameless "selected" nodes, and the total grows from 6 to 7 when Review calendars appears after a connection (A18, C105, S3 count).

**Evidence.** SetupWizard.kt:169 (`Dots(dotCount, dotIndex)`), :202-220; WizardRules.kt:35-38 (`dotCount` and `dotIndex` count only `shown` steps); CalendarSetupSteps.kt:35 (Review is `shown` once a connection exists).

**Fix.**
- The Row is `clearAndSetSemantics { contentDescription = "Step 3 of 7" }`.
- **Stable total.** `dotCount` counts every step, shown or not, and `dotIndex` is the step’s place among all of them. A hidden step keeps its outline dot and is passed over, so skipping Connect goes from "Step 5 of 7" to "Step 7 of 7". The alternative, counting Review only once a connection exists, is what makes the total jump today.

**Tests.** `WizardRules` unit tests: the total is the same before and after a connection exists; the index skips a hidden step. A compose test: the dots read "Step 1 of 7" on Welcome.

### 5.8 Move up and Move down (D28)

**Problem.** Rows aren’t keyed, so after "Move Alice up" TalkBack focus stays on the same position, which now shows Bob, and a second double-tap moves Bob. At the list’s ends the button is a `Spacer`, so focus is lost. The rows have no actions of their own (A42, S20 accessibility, Plan 4c follow-up).

**Evidence.** PeopleUi.kt:39-52 (`forEachIndexed`, no `key`), :80-97 (`MoveButton`: a `Spacer` when `onClick` is null).

**Fix.**
- Each row is wrapped in `key(member.person.id)`, so its node moves with the person and focus follows.
- At the ends the button stays, disabled, with `stateDescription` "already first" or "already last". It is drawn at the disabled alpha (`ControlTokens.DISABLED_ALPHA`), the only way a disabled button can show, so the arrow columns now show a faint arrow where there was a gap. Grouping the arrows in a pill stays W27.
- The row gets `customActions` "Move Sam up" and "Move Sam down", only where possible.
- The "Sam moved up." toast is Polite (§4.4).

**Tests.** After Move up on the second person, the node with Sam’s tag is the first row and still exists under the same key; the first row’s Move up is disabled with "already first"; the row has its custom actions.

## 6. Timeouts (D29–D32)

### 6.1 One activity signal (Decision 9)

**Problem.** Each timer detects activity on its own, and all of them only see pointer events. TalkBack’s swipes and double-taps are accessibility events, so a TalkBack user reading Settings is timed out 2 minutes after their last authorised action, and the week view snaps back under them (A38, A39, A41). The week view’s detector is on the week view only, so a touch in the add/edit sheet over it counts as idle (T14).

**Evidence.** OverlayLayers.kt:49-59 (`ShellLayers`: `awaitFirstDown` on the Initial pass, calling `onTouch`); MainActivity.kt:168-173 (`onTouch = touchTarget(…)`, only while Settings is open or the wizard shows); SetupWiring.kt:43-45; WeekView.kt:370-377 (`onEveryTouch`), CardHosts.kt:49, 58.

**Fix.** A `UserActivity` interface in `:core:plugin`, implemented once in `:app` (`ShellUserActivity`, a `@Singleton`) and provided as `LocalUserActivity` beside `LocalOverlayHost`:

```kotlin
interface UserActivity {
    /** Grows by one on each finger down anywhere, and on each TalkBack focus move, double-tap or scroll. */
    val touches: StateFlow<Long>

    /** TalkBack (or another touch-exploration service) is on. */
    val exploring: StateFlow<Boolean>

    /** [baseMillis] for most people; with touch exploration on, what Android’s "Time to take action" asks for, never less. */
    fun timeout(baseMillis: Long, hasControls: Boolean = true): Long
}
```

- **Pointer input.** `ShellLayers`’ existing Initial-pass `awaitFirstDown` calls `touched()` always, not only while Settings or the wizard shows. It covers the shell, sheets, pickers and the PIN pad. Scrolling starts with a finger down, so it counts.
- **TalkBack.** MainActivity sets an `AccessibilityDelegate` on `android.R.id.content` whose `onRequestSendAccessibilityEvent(host, child, event)` calls `touched()` for `TYPE_VIEW_ACCESSIBILITY_FOCUSED`, `TYPE_VIEW_CLICKED` and `TYPE_VIEW_SCROLLED`, then returns the default. Compose sends its accessibility events up the view tree this way, and they are only sent while an accessibility service is on.
- **`exploring`** follows `AccessibilityManager.isTouchExplorationEnabled` through a `TouchExplorationStateChangeListener`.
- **`timeout`** returns `baseMillis` unless `exploring`, then `max(baseMillis, getRecommendedTimeoutMillis(baseMillis.toInt(), FLAG_CONTENT_TEXT or FLAG_CONTENT_ICONS or (FLAG_CONTENT_CONTROLS if hasControls)))` (API 29, the app’s minSdk). Ruling 3 names TalkBack, so other people get the base. Never less than the base, so a short system setting can’t shorten anything; never more than `EDIT_HOLD_MS` unless the base is, so a value set outside Android’s own settings (which stop at 2 minutes) can’t hold anything open longer *(amended at plan review)*.
- MainActivity keeps feeding `access.touch(…)` (§6.2) from `touches` while Settings is open or the wizard shows, as `touchTarget` does today.

**Tests.** Robolectric: a pointer down in the sheet layer bumps `touches`; an `AccessibilityEvent` of each of the three types sent through the content view bumps it, and `TYPE_VIEW_HOVER_ENTER` doesn’t; `timeout` with exploring off is the base, and with it on is the recommended value from a shadowed `AccessibilityManager`.

### 6.2 Settings (D29)

**Problem.** Settings closes 2 minutes after the last pointer touch or authorised action. An open person sheet closes with it and its edits are lost, there is no warning, and TalkBack activity never counts (A38, A41).

**Evidence.** AccessControl.kt:9-13, :69-73 (`touch()`); DefaultAccessControl.kt:137-158 (`touch` and `restartExpiry`: a `delay` on the application scope); ShellViewModel.kt:148 (a null session closes Settings); SetupWiring.kt:43-45, 62 (Settings closing dismisses the overlay); OverlayLayers.kt:54-58.

**Fix (Decision 3).**
- **What counts.** Every `UserActivity` touch while Settings is open (§6.1): screen touches, scrolling, and TalkBack focus, click and scroll events.
- **How long.** The session’s timeouts come from `UserActivity.timeout`: `SESSION_TIMEOUT_MS` (2 minutes) and `SETUP_IDLE_MS` (10 minutes, the wizard). `DefaultAccessControl` takes the `UserActivity` interface from `:core:plugin`, which has no Android in it.
- **The tablet asleep** *(amended at plan review)*. The session’s timer is a coroutine delay, which stops while the tablet sleeps; `expiresAt` is wall-clock time. A touch, an authorise, and the app coming to the front each first end a session whose `expiresAt` has passed, so a touch after waking never revives it.
- **Unsaved changes.** `touch(holdForEdits: Boolean)`: while an open sheet has unsaved changes (§6.3), each touch restarts the session for `EDIT_HOLD_MS` = 10 minutes (or the recommended value, if longer) instead of 2. There is no longer hold. When it runs out, Settings closes, the sheet closes with it (`AppContent` already does this) and the edits are lost. *(Amended at plan review.)* The hold is kept through an authorise on the session (a Save the rules refuse keeps the sheet and its edits), and dropped by the next touch without it; the shell sends one whenever the edits go, so an undone edit or a closed sheet puts the session back on 2 minutes at once.
- **The warning.** `AccessControl` exposes `expiresAt: StateFlow<Long?>` (wall-clock millis; null with nobody signed in). While Settings is open, the shell shows a pill in the toast position, 20 seconds before `expiresAt`: "Settings closes in 20 seconds. Touch the screen to stay." It is an Assertive live region with the `schedule` icon (already in the subset font), tagged `settings_warning`, drawn over toasts. A touch restarts the countdown and hides the pill; the pill shows once per countdown (it isn’t re-shown by a recomposition inside the same countdown). The text is fixed: it doesn’t count down.
- **The PIN pad over Settings.** Touches on the pad count, so typing a PIN keeps Settings open. A session that ends while a fresh-PIN pad is up closes Settings and its sheet; the pad stays, since `authorise` is still waiting, and its own idle close (§6.3) cancels it.

**Tests (fake clock: `DefaultAccessControl` on a `TestScope`, the shell under the Compose test clock).**
- With nobody touching, the session ends at 2 minutes; a touch at 1:50 moves it to 3:50.
- With `holdForEdits`, it ends at 10 minutes; a touch at 9:59 moves it 10 minutes on; there is no hold beyond that.
- With `timeout` returning 5 minutes (TalkBack, a long "Time to take action"), the session lasts 5.
- The warning shows at 1:40 and not before, is Assertive, hides on a touch, shows again 20 seconds before the next expiry, and shows once per countdown.
- Robolectric: an accessibility focus event while Settings is open extends the session (A38’s test).
- Settings closing with an edited person sheet open closes the sheet.

### 6.3 Overlays left open (D32)

**Problem.** No shell overlay closes by itself; only Settings closing dismisses them. A stray tap on an empty week column leaves an "Add event" sheet, a detail sheet or the PIN pad over the wall display until someone closes it (L12, triage Decision 4).

**Evidence.** ShellOverlays.kt:18-31 (`OverlayState`: show and dismiss only); SetupWiring.kt:62; EventDetailHost.kt, EventEditorHost.kt, PeopleUi.kt:103-105, YouStep.kt:116-125 (the callers of `OverlayHost.show`); PinPad.kt:52-68 (`PinPadHost`).

**Fix (Decision 4).**
- `OverlayHost` gains:

  ```kotlin
  /** [closesWhenIdle] false for a card waiting on a screen outside the app, where no touch can reach Culvery. */
  fun show(content: @Composable () -> Unit, closesWhenIdle: Boolean = true)

  /** The open sheet has changes that closing it would lose; reset by each show and dismiss. */
  fun setEdited(edited: Boolean)
  ```

  (The plan puts `content` last, so `show { … }` callers keep compiling, and names the second `markEdited`: a `var edited` property’s setter is already `setEdited` on the JVM.)

- **Who reports edits.** The add/change sheet reports `EventForm.edited` (any field differs from where the sheet started, so a new sheet with its defaults is not edited); the person sheet reports its form’s `edited` the same way. The detail sheet, pickers on their own, the connecting card and the PIN chooser report nothing.
- **The idle close** lives in `ShellLayers`: keyed on the overlay’s content, its edited flag and `touches`, it waits `UserActivity.timeout(OVERLAY_IDLE_MS)` (2 minutes) with no edits, or `EDIT_HOLD_MS` (10 minutes) with edits, then dismisses. A picker belongs to its sheet and closes with it. The connecting card shows with `closesWhenIdle = false`: Google’s account chooser is outside the app, so no touch reaches Culvery while it is open.
- **Inside Settings** no overlay closes by itself: Settings’ own end (§6.2) takes its sheet, and Settings’ warning covers both. An overlay closing first would move TalkBack’s focus, which counts as a touch and would stretch the session. *(Amended at plan review; it was "the session closes first or at the same moment".)*
- **Saving** counts as edited, so the idle close never cuts a save off half done. *(Amended at plan review.)*
- **The PIN pad.** `PinPadHost` cancels its request (`controller.cancel()`, so `authorise` returns null as if Cancel were tapped) after `UserActivity.timeout(OVERLAY_IDLE_MS)` without a touch; over Settings a second later, so the session always ends first *(amended at plan review)*. Digits typed don’t count as edits. A lockout doesn’t stop the idle close.
- **Outside Settings with edits** the sheet closes at 10 minutes and the edits are lost. 20 seconds before, the shell shows the same pill as Settings’ warning (§6.2), with the text "This closes in 20 seconds. Touch the screen to keep your changes.", tagged `sheet_warning`: an Assertive live region, once per countdown, hidden by a touch, fixed text. Without edits there is no warning: nothing is lost. While Settings is open, only Settings’ warning shows. *(Amended at planning, user 2026-10-09; it was "without a warning".)*

**Tests (Compose test clock).** An untouched detail sheet closes at 2 minutes and not at 1:59; a touch inside the sheet restarts it; an add sheet with a typed title is still open at 9:59 and closed at 10:00; the connecting card is open at 30 minutes; the PIN pad cancels at 2 minutes and `authorise` returns null; with TalkBack’s timeout at 5 minutes, the detail sheet stays 5. The add sheet with a typed title shows `sheet_warning` at 9:40 and not at 9:39.999, Assertive, and a touch hides it; an untouched detail sheet never shows it; inside Settings an edited person sheet shows `settings_warning` and not `sheet_warning`.

### 6.4 The week view’s return to this week (D30, T14)

**Problem.** A later week goes back to this week 2 minutes after the last touch on the week view. Touches in the add/edit sheet don’t reach that detector (T14), TalkBack activity never does, and the title changes silently (A39).

**Evidence.** WeekView.kt:334-368 (`BACK_TO_THIS_WEEK_MS`, `WeekShown`, `rememberWeekShown`), :370-377 (`onEveryTouch`); CardHosts.kt:49, 58 (`Modifier.onEveryTouch(shown::touched)`; the host wiring is untested, T14).

**Fix (Decision 5).**
- `rememberWeekShown` keys its wait on `LocalUserActivity.current.touches` instead of its own counter, so touches anywhere (sheets and pickers included) and TalkBack events count. `onEveryTouch` and `WeekShown.touches` go.
- While `exploring` is true there is no snap-back at all.
- Otherwise the wait is `UserActivity.timeout(BACK_TO_THIS_WEEK_MS)`.
- The week title is a Polite live region, so "This week" is announced when it changes (by ‹ ›, This week, or the snap-back).

**Tests.** A host-level test of `WeekViewHost` with a fake `UserActivity`: a touch reported while an overlay is showing restarts the wait (closes T14’s untested wiring); with `exploring` true the week stays on a later week after 10 minutes; with it false it returns at 2 minutes; the title has a live region.

### 6.5 Toast durations (D31)

**Problem.** Every toast lasts 3.5 s, including errors and the 100–140-character home-app how-tos, which nobody can read in time (A40, S35).

**Evidence.** Shell.kt:47-55 (`TOAST_MILLIS = 3_500L`); OverlayLayers.kt:89-99 (`ToastLayer`’s `delay(ShellTokens.TOAST_MILLIS)`).

**Fix (Decision 6).** One pure function in `core/ui`, used by `ToastLayer`:

```kotlin
fun toastMillis(message: String, urgent: Boolean): Long {
    val base = if (urgent) 6_000L else 4_000L
    // 50 ms a character is a slow reader’s pace (screens audit S35); 40 characters fit in the base time.
    return minOf(base + 50L * (message.length - 40).coerceAtLeast(0), 8_000L)
}
```

`ToastLayer` waits `UserActivity.timeout(toastMillis(…), hasControls = false)`, so with TalkBack on the recommended time applies (and may pass 8 s). `TOAST_MILLIS` goes.

**Tests.** Unit: "Event added." is 4 s; an urgent 30-character message is 6 s; a 100-character plain one is 7 s; a 200-character one is 8 s. Compose: a toast is gone at its time and not 100 ms before; with the fake `UserActivity` at 20 s it stays 20.

## 7. Words (D33–D41)

Every changed string is in Appendix A, current → new, with its file. This section gives the rules and the code changes behind them. All new copy uses curly quotes and apostrophes (’ “ ”).

### 7.1 Vocabulary and quotes (D33)

**Problem.** System words reach the family: "Master", "Make master", "Kiosk", "Exit kiosk mode", "synced", "Syncing to Google…", "waiting to sync … dropped", "subscribed calendar", "Calendar feed", "Assign to…". The same thing has two names: "Google" and "Google Calendar"; "· read-only", "(read-only)" and "Read-only calendar"; "Add event", "New event", "Save event"; "Edit" and "Change"; "Added from phone" and "Added from a phone"; "Keep" and "Keep event". The PIN pad’s event line is built from the enum name (`reason.name.lowercase()`), so renaming an enum would change the copy. Quotes are straight except in one place (C2, C4, C17–C20, C24, C28, C30–C32, C35–C40, C60, C68, C70, C74, C79, C84, C85, C93, C99, hE1, hP1).

**Evidence.** SetupCopy.kt; CalendarUi.kt:14-17, 84-88, 100-122; ReviewCalendars.kt:58-71; EventDetailSheet.kt:53-55, 98-152, 211; EventEditorSheet.kt:66, 202, 224, 383-389; CalendarEditor.kt:36-46; CalendarPermissions.kt:20-24, 48; Permissions.kt:27-31; AccessControl.kt:16-26; CalendarReview.kt:21-35; SourceRefresher.kt:22-27; GoogleConnectFlow.kt:19 (`GOOGLE_LABEL = "Google"`); WeekView.kt:78-79.

**Fix.**

#### 7.1.1 The glossary

| Thing | Use | Not |
|---|---|---|
| The calendar new events go to | main calendar: the "Main" badge, "Use for new events", "Choose another main calendar first." | master |
| Two-way copying with the service | "Updated … ago", "Not updated yet", "Saving to Google Calendar…", "Saving…" | sync, synced, syncing |
| Changes not yet sent that are thrown away | "will be lost", "was lost" | dropped, waiting to sync |
| The service | "Google Calendar" in every visible string | "Google" alone |
| Creating an event | button "Add event" → sheet "Add event" → button "Add event" → toast "Event added." | New event, Save event |
| Changing an event | button "Change" → sheet "Change event" → "Save changes" → "Changes saved." | Edit |
| Who an event belongs to | "Choose who it’s for" | Assign, Assign to… |
| A calendar the tablet can’t write to, or that isn’t the main one | "Can’t change here" | read-only, subscribed, Calendar feed |
| Letting the tablet run other apps | page "Tablet lock"; button and PIN line "Use other apps" | Kiosk, Exit kiosk, kiosk mode |
| A day with nothing in it | "Nothing on" | Free |
| Going to the Calendar tab from Today | "See the week" | Week |
| An event made on a phone | "Added from a phone" | Added from phone |
| Closing a confirmation without doing it | "Keep" | Keep event |

#### 7.1.2 Code changes behind the words

- **Service names (Decision 12).** `EventUi.connectionLabel` goes; the Saving pill and every failure use `serviceName`. `couldNotSave`, `couldNotSaveAll` and the editor’s failure card take the service name (`serviceNameOf`), not `SourceCatalog.label`. The week subtitle names the service when every connection has the same one (`serviceNames.distinct().singleOrNull()`), and otherwise says "Updated 5 minutes ago". `GOOGLE_LABEL` stays as the stored label (it is the connection’s name inside the app, not shown).
- **PIN lines from explicit copy (C60).** `pinReasonText` maps each `PinReason` to its own string, not the enum name: Save → "add this event", Edit → "change this event", Delete → "delete this event", Assign → "choose who this event is for", each followed by "It also records who made the change." Generic uses the permission’s label, first letter lower-cased: "Enter your PIN to use other apps."
- **Permission labels.** `kiosk.exit` "Exit kiosk mode" → "Use other apps"; `calendar.event.assign` "Assign events" → "Choose who events are for"; `people.manage` "Manage people" → "Add and change people" (C70’s second proposal, so the PIN line reads "Enter your PIN to add and change people.").
- **The refusal in the PIN pad (C64, amended at plan review: user decision A).** A real PIN that may not do the thing gets "That PIN can’t do this. Ask an Admin.", or "That PIN can’t do this. Ask a grown-up." when Adults hold the permission. It names no one, so a PIN found by guessing doesn’t say whose it is, and it counts as a wrong attempt towards the lockout. `PinError.NotAllowed` carries only the message; `DefaultAccessControl` builds it from the registry’s roles.
- **Read-only notes (Decision 2g).** For both `OtherCalendar` and `NotMaster`, the detail sheet’s note title is "Can’t change here", the badge description is "Can’t change here", and the Calendar row reads "School terms (can’t change here)". The note bodies differ: `OtherCalendar` "This calendar can only be changed by whoever runs it."; `NotMaster` "Change events on this calendar in Google Calendar on your phone." The "Created by" value for a feed event (`CALENDAR_FEED`) becomes the calendar’s own name ("School terms"): "Can’t change here" would not answer "Created by".

#### 7.1.3 The copy-scan test (Decision 8)

`CopyScanTest` in `:core:ui`’s tests, beside `IconFontTest`, walks every module’s `src/main` Kotlin as `IconFontTest.mainSources()` does.

- **Reading the source.** A small tokenizer strips `//` and `/* */` comments (KDoc included), then collects string literals (`"…"` and `"""…"""`). Template expressions `${…}` are removed with their braces balanced, so code inside them (`padStart(2, '0')`) is never read as copy; simple templates (`$name`) stay as text.
- **Which literals are copy.** All of them, except:
  - **identifiers:** a literal with no whitespace that is all lower case or contains `.`, `_` or `/` (`"kiosk"`, `"calendar.event.assign"`, `"review_make_master_$id"`, URL paths);
  - **code contexts:** a literal on a line whose code calls `Log.*`, `testTag(`, `require(`, `requireNotNull(`, `check(`, `checkNotNull(`, `error(`, `DateTimeFormatter.ofPattern(`, or throws any exception other than `WriteRejectedException` (whose message is shown), or is a `TAG` constant or an annotation (`@Query(…)`);
  - **an allowlist** in `core/ui/src/test/resources/copy-scan-allow.txt`, one entry per line: `path suffix | literal | reason`. It starts empty; an entry needs a reason a reviewer accepts.
- **What fails.** In a copy literal: a whole word, any case, from `master`, `kiosk`, `sync`, `synced`, `syncing`, `read-only`, `assign`, `assigned`, `feed`; a straight apostrophe `'`; a straight double quote (`\"` inside a literal). The failure lists `file:line: literal` for each.
- **Self-test.** A fixture source in the test resources holding one case of each rule (a banned word in a `Text(` literal fails; the same word in a test tag, a log line, a KDoc and a `${…}` template passes; an apostrophe in a `require` message passes; `WriteRejectedException("Can't …")` fails) proves the tokenizer, so a broken scanner can’t pass silently.
- It lands in the same task as the sweep (§9 task 11), so every commit stays green.

**Tests.** `CopyScanTest`; unit tests of `pinReasonText` per reason, `NotAllowed`’s message for an Admin-only and an Adult permission (no name in it, and a refused PIN counted towards the lockout), `weekSubtitle`, `reconnectLabel` ("2 accounts need reconnecting") and the badge descriptions.

### 7.2 Errors by cause (D34)

**Problem.** Errors give neither the cause nor the fix. A delete failure says "Couldn’t save". `EVENT_GONE` and the sample provider’s reasons are capitalised, so they read "— The event no longer exists" mid-sentence. The failure card says "check the connection" for refusals, where trying again can’t help. A connection in an unknown error state says "Something went wrong" (C47, C48, C51–C53, C64, C81, C82, C86, C87g, C87j, C96, C100, C107, S28 copy).

**Evidence.** Writes.kt:33 (`EVENT_GONE`), :66 ("the calendar refused the change"), :126-132 (`couldNotSave`, `couldNotSaveAll`); CalendarEditor.kt:36 (`TRY_AGAIN`), :46, :133 (`couldNotDelete` reuses `couldNotSave`), :255-271 (a store failure becomes `Rejected(TRY_AGAIN)`), :435; EventEditorSheet.kt:66 (`SAVE_FAILED_BODY`); EventEditorHost.kt:96, 123; ReviewCalendars.kt:58-59, 69, 75-80; plugin/Setup.kt:11; CalendarSetup.kt:25, 95-105, 125-129; PinPromptController.kt:12; google/GoogleHttp.kt:27-28, 142; fake/FakeCalendarProvider.kt:146, 193.

**Fix.**
- **A typed cause.** `EditResult.Rejected(message: String)` becomes `Rejected(problem: WriteProblem)`, and the `TRY_AGAIN` sentinel goes:

  ```kotlin
  sealed interface WriteProblem {
      /** The tablet failed before the provider answered (a store failure): trying again may work. */
      data object Tablet : WriteProblem

      /** The provider refused for good. [reason] is lower case, to follow a dash; null when it gave none. */
      data class Refused(val reason: String?) : WriteProblem
  }
  ```

  The editor never meets an unreachable provider: a write it can’t send in time is queued and shows as saving. "Couldn’t reach" is for connecting (below) and the review rows.
- **Copy by cause** (`Writes.kt`), with `service` = "Google Calendar":

  | Cause | Save | Delete |
  |---|---|---|
  | `Tablet` | "Couldn’t save — try again." | "Couldn’t delete — try again." |
  | `Refused(null)` or the provider’s generic refusal | "Google Calendar wouldn’t accept this change." | the same |
  | `Refused(reason)` | "Couldn’t save to Google Calendar — {reason}." | "Couldn’t delete — {reason}." |

  The provider’s generic refusal becomes one shared constant beside `EVENT_GONE`, `WRITE_REFUSED`, which Google uses instead of its own `REFUSED`, and `callWriter`’s fallback (Writes.kt:66) gives `Refused(null)`. `report` (CalendarEditor.kt:435) picks the Delete column for a delete, so a failed delete never says "save" (C52); `COULD_NOT_DELETE` and `couldNotDelete` use it.
- **Reasons in lower case**, with no end punctuation since they follow a dash: `EVENT_GONE` "the event has been deleted"; Google’s `READ_ONLY_HERE` "this calendar can’t be changed from the tablet"; the sample provider’s "repeating events can’t be changed here" and "{calendar} can’t be changed here".
- **The failure card’s body by cause** (C48): `Tablet` "Nothing was saved. Your details are still here — try again."; `Refused` "Nothing was saved. Your details are still here to add in Google Calendar on your phone." The `SAVE_FAILED_BODY` constant becomes a function of the cause.
- **Lost changes** (C53, the drain): one change "A change couldn’t be saved to Google Calendar and was lost — {reason}." (or "… and was lost." for a generic refusal); several "3 changes couldn’t be saved to Google Calendar and were lost."
- **Connecting** (C87g): when the failure is an `UnreachableException`, "Couldn’t reach Google Calendar — check the tablet’s Wi-Fi, then try again."; otherwise "Couldn’t connect to Google Calendar — try again." `CalendarSetup.connect` and `reconnect` pass which.
- **Review health lines** (C81, C82, S28): `Unreachable` "Can’t reach Google Calendar — check the tablet’s Wi-Fi."; `Error` "Stopped updating — disconnect and connect again."; `NeedsSignIn` "Needs reconnecting" (a status, so no full stop); `Ok` "Updated 5 minutes ago". The unreadable calendar: "Can’t read this calendar — check it’s still shared with this account." (C86).
- **Saves outside the calendar say what wasn’t saved** (C87j, C96): `COULD_NOT_SAVE` (plugin/Setup.kt:11) becomes "Couldn’t save that change — try again." for the calendar review; the location pane "Couldn’t save the home location — try again."; the person sheet "Couldn’t save Sam’s details — try again." ("Couldn’t save this person — try again." before a name); the You step "Couldn’t save your details — try again."; a failed move "Couldn’t move Sam — try again."
- **Smaller ones.** "Couldn’t open the event — try again." (C51); "That colour is taken — choose another." (C100); the PIN pad refusal (§7.1.2, C64).

**Tests.** Unit tests of each copy function per cause, including `couldNotDelete` with `Tablet` ("Couldn’t delete — try again."), a refused delete, the drain’s one and several, and connect with and without `UnreachableException`; `CalendarEditorTest`’s existing refusal cases updated to `WriteProblem`.

### 7.3 Full words and full stops (D35)

**Problem.** "5 min ago", "3 h ago", "30s", "1 h 30": abbreviations that children and TalkBack stumble on. End punctuation is random (C12, C19, C27, C45, C51, C61–C63, C86).

**Evidence.** WeatherWords.kt:29-37 (`updatedAgo`); CalendarUi.kt:100-113 (`syncedLabel`); EventForm.kt:40-49 (`lengthLabel`); PinPad.kt:97-98, 113; AccessControl.kt:26.

**Fix (Decision 11).**
- Ages: "just now", "1 minute ago", "5 minutes ago", "1 hour ago", "3 hours ago", "2 days ago" (`syncedLabel` becomes `updatedLabel`); the weather card "Updated 3 hours ago", "Updated 1 day ago", "Updated 2 days ago".
- Lengths: "30 minutes", "1 hour", "2 hours", "1 hour 30 minutes", "2 hours 15 minutes"; "1 minute" in the singular. The chip may take two lines (4d-1).
- The PIN pad: "Too many tries — wait 30 seconds." ("1 second"); "Wrong PIN — try again."; "Those PINs didn’t match — try again."; "Enter your PIN to carry on setting up."
- Toasts end with a full stop ("Event added.", "Sam removed.", "Google Calendar connected."), as do errors and body sentences. Titles, labels, buttons, chips, badges and status lines don’t.
- TalkBack hears times through `spokenTime` (§5.6): "from 22:00", not "22:00 dash".

**Tests.** Unit tests of `updatedLabel` (0, 1, 59 minutes; 1, 47 hours; 2 days; a clock that went backwards), `updatedAgo`, `lengthLabel` (30, 60, 90, 135 minutes, 1 minute) and the PIN pad’s lockout text for 1 and 30 seconds.

### 7.4 People and PINs (D36)

**Problem.** The role lines promise what a person can do, but without a PIN an Adult or Child can’t be identified and can do none of it. The You step’s button still says "Set your PIN" after a PIN is set. Nobody tells the first person they are the Admin (C91, C97, S7, S8).

**Evidence.** SetupCopy.kt:17-18, 71-75; PeopleUi.kt:68 (the row’s "Adult · No PIN"); PersonEditorSheet.kt:208-216 (the PIN section); YouStep.kt:145-166.

**Fix.**
- **The person sheet.** Under the PIN section, while the person is an Adult or Child with no PIN: "Without a PIN, Sam can only look." ("Without a PIN, they can only look." before a name), `SetupType.secondary` in `mute`. The role lines stay as they are.
- **The people row.** "Adult · No PIN — can’t add events" for an Adult or Child with no PIN; "Admin · PIN set" and "Adult · PIN set" as today. The colour stays `mute` (S8’s `danger` "No PIN" is a look change, so walk-through).
- **The You step.** Its title line becomes "You’ll be the Admin — you can change settings and people. Admins need a PIN." (`StepTitle(WHOS_SETTING_UP, YOU_LINE)`), which also says why Next waits for a PIN. Its PIN button reads "Set your PIN" until a PIN is chosen, then "Change PIN".

**Tests.** The You step shows "Change PIN" after a PIN is chosen and its title line; a person row for an Adult with no PIN reads "Adult · No PIN — can’t add events"; the person sheet shows the look-only line for a Child with no PIN and not for an Admin.

### 7.5 "Out of date" (D37)

**Problem.** The week subtitle turns `danger` when the data is stale, and colour is the only cue (A37, L6, hC2, C17, C18). The triage found the cause on the emulator is expected: a pass with any failing calendar, or unreachable Play services, never stamps `lastSyncMillis` (CalendarSync.kt:305-309), so a connection that never had a clean pass reads "not synced yet" in red (E10 checks it on the device).

**Evidence.** WeekView.kt:133-140 (the subtitle’s colour); CalendarUi.kt:100-122 (`syncedLabel`, `isStaleAt`, `weekSubtitle`).

**Fix.**
- When `isStaleAt` is true, the subtitle starts with a 16 dp `sync_problem` icon in `danger` (already in the subset font) and the words "Out of date — ", then the usual line: "Out of date — updated from Google Calendar 3 hours ago", "Out of date — not updated yet". The text stays `danger`. Fresh, it reads "Updated from Google Calendar 5 minutes ago", or "Not updated yet", in `mute` with no icon.
- **Plan step 1** of this task reads the emulator’s `connection` rows (`health`, `healthMessage`, `lastSyncMillis`) with `adb shell run-as` and `sqlite3`, and records the cause in the task’s notes, as the follow-up asks.

**Tests.** A stale subtitle contains "Out of date" and has the icon; a fresh one has neither; `weekSubtitle` unit tests for one service, two services and never updated.

### 7.6 Dead ends (D38)

**Problem.** The Forecast card’s "Open settings" opens Settings at whatever page was first, and names where it goes rather than what you do. The Connect card’s fallback (no service can be connected) opens Settings, where there is nothing to connect either (C11, C14, S34).

**Evidence.** plugin/ShellNavigator.kt:10-11 (`openSettings()`, no page); ShellViewModel.kt:157-161; SettingsScreen.kt:46-49 (always the first page); wx/ui/ForecastCard.kt:75-78; cal/ui/ConnectCalendarCard.kt:50-54.

**Fix.**
- `ShellNavigator.openSettings(page: String? = null)`. The page ids become constants in `:core:plugin` (`SettingsPageIds.LOCATION = "location"`, `CALENDARS = "calendars"`, `PEOPLE`, `TABLET_LOCK`), so capabilities can name a page without depending on `:core:setup`; the pages use them. `ShellViewModel` keeps the requested page with `settingsOpen`, and `SettingsScreen` takes `initialPage`, falling back to the first page for an unknown id. The Tablet lock page’s id stays `"kiosk"`: it is stored nowhere, but renaming it gains nothing.
- The Forecast card’s button reads "Add home location" and calls `openSettings(SettingsPageIds.LOCATION)`.
- The Connect card’s fallback has no button and says "No calendar services are available on this tablet." under its title.

**Tests.** Tapping "Add home location" opens Settings on the Home location page (`settings_page_location` selected); the Connect card with no service has no button and shows the line; `SettingsScreen` with an unknown `initialPage` shows the first page.

### 7.7 Location search (D40)

**Problem.** Nothing shows while a search runs (a 400 ms pause, then a network call). A failed search can be retried only by editing the text. The `location_results_*` baselines show "Skip for now" beside a ticked town, a state the app never reaches (S5, S6).

**Evidence.** LocationPane.kt:38-46 (`TownResults`: no searching state), :64-82 (`produceState` keyed on the query only), :118-131; SetupCopy.kt:27; SetupScreenshotTest.kt:140-143 (`location()` passes `Forward.Skip` with `current = brighton`).

**Fix.**
- `TownResults.Searching`, set as the pause starts, shows "Searching…" (`SetupType.line`, `mute`, a Polite live region) under the field.
- A failed search shows "Couldn’t search for towns — check the tablet’s Wi-Fi, then try again." and a plain "Try again" button (`HhButton`, Quiet, on `Bg`) that re-runs the same query: `produceState` also keys on a retry counter the button bumps.
- The no-match line: "No towns match “Brigh” — check the spelling or try a nearby town." (C93).
- The screenshot fixture renders the forward button the real step would show: a ticked town (a saved home) has Next, enabled; a search with no saved home has Skip for now. A `location_searching` baseline is added.

**Tests.** Unit or compose: the states run Idle → Searching → Found; Failed shows Try again, and a tap runs the search again with the same query (a fake `LocationSearch` counts calls); the fixture’s forward state matches `LocationStep`’s rule.

### 7.8 Tablet lock (D41)

**Problem.** Exit kiosk gives no feedback: as the home app Culvery stays in front with the page unchanged. The two home-app how-tos are 100–140-character toasts in a 3.5 s pill, and they say "exit kiosk" (C74–C77, S22, S35).

**Evidence.** KioskLifecycle.kt:41-48 (`HOME_APP_NOT_SET`, `HOME_APP_SCREEN_MISSING`), :129-137 (`exitKiosk`); ShellViewModel.kt:165-172; pages/KioskPage.kt:25-44; SetupCopy.kt:85-87.

**Fix.**
- The page is "Tablet lock" (Decision 2b); its line: "Culvery keeps the tablet on this app. Tap Use other apps to leave it. It locks again when Culvery next opens." (C75); the button "Use other apps".
- After Use other apps, a plain toast: "You can use other apps now. Culvery locks again when it next opens." It doesn’t say how to reach them, as that depends on the tablet’s navigation mode.
- The home-app toasts become short: "Culvery isn’t the home app yet." and "Culvery can’t open the home app setting."
- The how-to moves to a `mute` line on the Tablet lock page, under the home-app button, always shown: "If that doesn’t work: tap Use other apps, then choose the home app in Android’s Settings › Apps › Default apps." One UI’s name for that menu is checked on the SM-T510 (E5) and the line changed there if needed.

**Tests.** `KioskLifecycle` message tests for the two short toasts; `ShellViewModel.exitKiosk` toasts after `leaveKiosk`; the Tablet lock page shows the how-to line in both home-app states.

### 7.9 Smaller copy and empty states (D39)

As the copy audit proposed, adjusted to Decision 2; each row is in Appendix A.

- C1: Today, empty, when adding is allowed: "Nothing on today. Tap + to add something." Without add: "Nothing on today".
- C3: Coming up’s overflow: "+2 more in Calendar", naming the rail tab where they are.
- C10: "No forecast — check the tablet’s Wi-Fi. Culvery will try again."; the failed first fetch the same way: "No forecast yet — check the tablet’s Wi-Fi. Culvery will try again."
- C46: "Mon 22 – Wed 24 · change dates in Google Calendar on your phone" (`EventForm` takes the service name).
- C50: "Event added on Tue 7 October."
- C59: the Generic PIN line through the new labels (§7.1.2).
- C75: the Tablet lock line (§7.8).
- C85: the master-gone toast, shorter and urgent: "Google Calendar’s main calendar has gone. An Admin can choose another in Settings › Calendars." plus " 1 unsaved change was lost." or " 3 unsaved changes were lost." A banner on the week view (C85’s second idea) is layout, so not done.
- C87e: the connecting card’s second line comes from the provider: `ProviderDescriptor.connectHint: String? = null`; Google’s is "Choose the family’s Google account and allow access."; the sample provider has none, so the card shows no line.
- C90: Welcome’s line: "We’ll set up your home’s location, the people who live here, and your calendars."
- C94: "Used for the clock’s time zone and the weather."
- C98: "Remove Sam? Sam’s events and calendars will show as Family."
- S29: the wizard’s Your calendars step gets a line: "Choose who each calendar is for, and which to show. New events go to the main calendar." (`ReviewCalendars` takes an optional `line`; Settings › Calendars passes none.)
- Settings › Calendars with nothing connected: "Connect your family’s Google Calendar to see it on Home." above the Connect button (`ReviewCalendars` shows it when `connections` is empty).

**Tests.** Copy unit tests where a function builds the string (`eventAddedFor`, `masterGone`, `removeQuestion`); compose tests for Today’s empty line with and without add, the Calendars empty state and the wizard line; re-recorded baselines.

## 8. Testing strategy

### 8.1 Robolectric semantics tests

Each item’s tests are listed with it. They run under Robolectric with the Compose test rule, as the existing UI tests do, and read the semantics tree: `Role`, `ContentDescription`, `StateDescription`, `Selected`, `LiveRegion`, `PaneTitle`, `Heading`, `Error`, `CustomActions`, `OnClick` labels and `ToggleableState`. Tests that check something is hidden behind an overlay assert the node does not exist in the merged tree.

### 8.2 The shared helper and automatic checks

- `assertClickablesHaveRoleAndLabel()` (§4.1) lives in `:core:ui`’s test fixtures (`android { testFixtures { enable = true } }`, with `android.experimental.enableTestFixturesKotlinSupport=true`), so every module’s tests can use it. If AGP’s Kotlin test fixtures don’t work with the Compose convention plugin, the first task puts it in a small `:core:ui-testing` module instead and says so.
- **`enableAccessibilityChecks()`** (Compose BOM 2025.09 ships `ui-test-junit4-accessibility`, which runs Google’s Accessibility Test Framework on each action). The first task adds it to one Robolectric test and records in the plan whether it runs under Robolectric and what it reports. If it works, it is enabled in every screen test from task 2 on, and its findings are fixed or listed with a reason. If it doesn’t (ATF reads real views and may not see Robolectric’s), the plan notes that and the helper is the guard.

### 8.3 Fake-clock timeout tests

- `DefaultAccessControl` runs on a `TestScope`, with `advanceTimeBy` for the session, the edit hold and `expiresAt`.
- The overlay idle close, the PIN pad’s idle cancel, the week snap-back, the Settings warning and toast durations run under the Compose test clock (`mainClock.autoAdvance = false`, `advanceTimeBy`).
- A `FakeUserActivity` (in `:core:plugin`’s test fixtures, or beside the helper) sets `touches`, `exploring` and `timeout` directly, so each timer is tested with TalkBack off, on, and with a long "Time to take action".
- Each timer is tested at its boundary: still open 1 ms before, closed at it.

### 8.4 The copy-scan test

`CopyScanTest` (§7.1.3) with its self-test fixture. It lands with the vocabulary sweep (task 11), so it is green from its first commit. Copy unit tests cover every function that builds a string.

### 8.5 Baselines

As in 4d-1: each task that changes baselines runs Roborazzi’s compare first, looks at every difference against its list of expected changes, then re-records. An unexpected difference is a stop. Nearly every baseline changes in the words tasks; the TalkBack tasks change few (the quiet fill on Close, ‹ › and the steppers; the disabled Move buttons). New baselines: `location_searching`, `settings_warning`, `sheet_warning`, a Tablet lock page with the how-to line, the Calendars page with nothing connected, and the stale week subtitle. The final task shows the user every baseline the plan changed, light and dark, with a line on why.

### 8.6 TalkBack on the emulator (Decision 7)

In the final task, on the debug build (API 35 emulator):
- The controller turns TalkBack on through `adb shell settings put secure enabled_accessibility_services …TalkBackService` and `accessibility_enabled 1`, with TalkBack’s developer settings logging its speech (log output level verbose), and reads what TalkBack says from `adb logcat`. It moves with injected gestures (`input swipe` right and left for next and previous, two taps for a double-tap) or TalkBack’s keyboard shortcuts, whichever the first try shows TalkBack accepts. If the emulator image has no TalkBack, the controller stops and asks the user.
- Paths: Home (header weather, Today rows, Coming up, Forecast rows); an event (detail sheet, pane title, merged rows, Change, Delete and Keep); adding an event (title field name, chips as radio buttons, both pickers, Add event, the toast); the PIN pad (dots, wrong PIN, lockout spoken once; the user types the PIN); Settings › People (tabs, rows, Move up with focus following the person, a custom action, the person sheet’s swatches); the Settings warning (left idle until it speaks, then a TalkBack swipe to stay).
- What TalkBack said is kept as a short transcript in the task’s notes, with anything that reads badly fixed or listed.
- The user then does a normal, non-TalkBack walk-through of the same screens for the wording.

### 8.7 Gate

`./gradlew testDebugUnitTest verifyRoborazziDebug` after each re-record.

## 9. Plan shape

Fourteen tasks, the last a wrap-up. Labels written in tasks 1–8 already use the new vocabulary; the sweep of existing copy is tasks 11–13. *(Amended at plan review, user: tasks 10, 11 and 13 are each split in two, session then overlay timing, the sweep then the scanner, routing and search then the copy, so the plan has seventeen.)*

1. **Groundwork.** Try `enableAccessibilityChecks()` under Robolectric and record the result; the test fixtures and `assertClickablesHaveRoleAndLabel()`; `Modifier.hhClickable`; `HhButton.spokenName`; `HhListRow.clickLabel`.
2. **Roles and labels** on every clickable (D15); the quiet fill and `Role.Button` for Close, ‹ › and the steppers (4d-1 follow-up); the helper called from each screen test.
3. **Tabs and headings** (D16, D19).
4. **Modal overlays and pane titles** (D17).
5. **Live regions, urgent toasts, field labels and errors** (D18, D20).
6. **Swatches, choice chips and review rows** (D21, D23, D22).
7. **Pickers, the PIN pad, wizard dots, Move up and Move down** (D24, D25, D27, D28).
8. **Week and Home semantics** (D26), with the Forecast test follow-up.
9. **`UserActivity`**, the TalkBack hook and the recommended timeout; the week snap-back with T14’s host test (D30); toast durations (D31).
10. **Settings timeout, the edit hold and the warning; the overlay and PIN pad idle close** (D29, D32).
11. **The vocabulary and quotes sweep** (D33) and `CopyScanTest`.
12. **Errors by cause; full words and full stops** (D34, D35).
13. **People and PINs, "Out of date" (with the emulator health read), dead ends, location search, Tablet lock, smaller copy** (D36–D41).
14. **Wrap-up.** The TalkBack check on the emulator and its transcript; the user’s wording walk-through; every changed baseline shown; the follow-ups file updated (D15–D41 closed, T14 closed, the 4d-1 follow-ups for 4d-2 closed, anything left over listed); the audit folder may then go.

## 10. Risks

- **Live-region noise.** Toasts, the week title, the PIN dots, the stepper values, the date range and TalkBack’s own echo of each PIN digit can talk over each other. Mitigations: Assertive only for urgent toasts and the warning (Decision 10); no live region on anything that changes without the person’s own action, except toasts, the warning and the snap-back’s title; the lockout spoken once. The emulator check (§8.6) listens for overlap, and any region that adds noise without news is removed there.
- **The timeout and the PIN pad.** Three timers meet at the pad: its own idle cancel, the Settings session and the overlay under it. Touches on the pad count for all three, so typing keeps everything open. A session that ends while a fresh-PIN pad is up closes Settings under it; the pad’s cancel must go through `PinPromptController.cancel()` so `authorise` returns null rather than hanging. A lockout longer than 2 minutes ends with the pad cancelled; the lockout itself is kept by `LockoutStore`, so the next pad still shows it. Each of these has a test (§6.2, §6.3).
- **Screenshot churn from copy changes.** Most baselines change in tasks 11–13, where a real regression can hide among expected text changes. Each task compares before re-recording and lists what it expected to change; the user sees every changed baseline at the end.
- **Test churn.** Many UI tests find nodes by text ("Save event", "Edit", "Master", "Exit kiosk"). Test tags don’t change; the task that changes a string updates the tests that use it, and the full suite runs before each commit.
- **Hidden backgrounds hide nodes tests use.** Once overlays clear what is behind them, a test that taps the shell while a sheet is open fails. That is the intended behaviour: `clearAndSetSemantics` removes those nodes from both trees, so such a test closes the overlay first, or taps by position when it tests pointer behaviour.
- **The accessibility hook.** It relies on Compose sending accessibility events up the view tree through `requestSendAccessibilityEvent`. A Robolectric test checks the hook; the emulator check (§8.6) confirms TalkBack activity keeps Settings open. If a Compose upgrade stops this, the test fails.
- **Waiting outside the app.** The connecting card is exempt from the idle close because nothing in Culvery is touched while Google’s chooser is open. The Settings session has the same blind spot during a Reconnect from Settings › Calendars; this plan doesn’t change that (4c tested it), and it is noted for E6 on the device.
- **`enableAccessibilityChecks()` may not run under Robolectric.** Then the helper and the emulator check are the guard, and the plan says so.

## Appendix A: copy table

Built from the copy audit (C1–C108), adjusted to the rulings. Every visible string that changes, current → new, with its file and line at 4d03211 (short paths as in §4). "(new)" means there is no string today. A string that changes only its apostrophe or quotes is listed in A.1 under its file, so the sweep misses none. `{service}` is "Google Calendar" wherever the provider is Google.

### A.1 Visible copy

**core/setup: SetupCopy.kt**

| Id | Line | Current | New |
|---|---|---|---|
| C90 | 15 | Your home's location, the people who live here, and your calendars. | We’ll set up your home’s location, the people who live here, and your calendars. |
| quotes | 17 | Who's setting this up? | Who’s setting this up? |
| C91, D36 | (new) | | You’ll be the Admin — you can change settings and people. Admins need a PIN. |
| quotes | 24 | Where's home? | Where’s home? |
| C94 | 26 | Used for the time zone, and for weather. | Used for the clock’s time zone and the weather. |
| C95, D40 | 27 | Couldn't search for towns — check the tablet's Wi-Fi and try again. | Couldn’t search for towns — check the tablet’s Wi-Fi, then try again. |
| C93 | 29 | No towns match "$query". | No towns match “$query” — check the spelling or try a nearby town. |
| D40 | (new) | | Searching… |
| D40 | (new) | | Try again |
| D40 | (new, spoken) | | 3 towns found / 1 town found |
| C100 | 45 | That colour is taken. | That colour is taken — choose another. |
| C97, D36 | (new) | | Without a PIN, Sam can only look. / Without a PIN, they can only look. |
| C98 | 52 | Remove $name? $name's events and calendars show as Family. | Remove Sam? Sam’s events and calendars will show as Family. |
| D35 | 54 | $name removed | Sam removed. |
| D35 | 61, 63 | $name moved up / $name moved down | Sam moved up. / Sam moved down. |
| C96 | (new) | | Couldn’t move Sam — try again. |
| C74 | 85 | Kiosk | Tablet lock |
| C74 | 86 | Exit kiosk | Use other apps |
| C75 | 87 | Culvery keeps the tablet on this app. Exit to use other apps; it locks again next time Culvery opens. | Culvery keeps the tablet on this app. Tap Use other apps to leave it. It locks again when Culvery next opens. |
| C76, C77, D41 | (new) | | If that doesn’t work: tap Use other apps, then choose the home app in Android’s Settings › Apps › Default apps. |

**core/setup: other files**

| Id | File:line | Current | New |
|---|---|---|---|
| S8, D36 | PeopleUi.kt:68 | Adult · No PIN | Adult · No PIN — can’t add events (Adult or Child with no PIN) |
| S7, D36 | YouStep.kt:161 | Set your PIN (always) | Set your PIN, then Change PIN once a PIN is chosen |
| C96 | YouStep.kt:105 | Couldn't save — try again. | Couldn’t save your details — try again. |
| C96 | PersonEditorSheet.kt:98 | Couldn't save — try again. | Couldn’t save Sam’s details — try again. / Couldn’t save this person — try again. |
| C96 | LocationPane.kt:130 | Couldn't save — try again. | Couldn’t save the home location — try again. |
| C96 | PeopleEditor.kt:97-98 | Couldn't save — try again. (a failed move) | Couldn’t move Sam — try again. |

**core/plugin: Setup.kt**

| Id | Line | Current | New |
|---|---|---|---|
| C87j | 11 | Couldn't save — try again. | Couldn’t save that change — try again. |

**core/access**

| Id | File:line | Current | New |
|---|---|---|---|
| C60 | AccessControl.kt:22 | Enter your PIN to save this event. It also records who made the change. | Enter your PIN to add this event. It also records who made the change. |
| C60 | AccessControl.kt:22 | Enter your PIN to edit this event. … | Enter your PIN to change this event. … |
| C60 | AccessControl.kt:22 | Enter your PIN to delete this event. … | (the same words, now from explicit copy) |
| C60 | AccessControl.kt:22 | Enter your PIN to assign this event. … | Enter your PIN to choose who this event is for. … |
| C59, C74 | AccessControl.kt:20 with Permissions.kt:29 | Enter your PIN to exit kiosk mode. | Enter your PIN to use other apps. |
| C70 | AccessControl.kt:20 with Permissions.kt:28 | Enter your PIN to manage people. | Enter your PIN to add and change people. |
| C61 | AccessControl.kt:26 | Enter your PIN to carry on setting up | Enter your PIN to carry on setting up. |
| C70 | Permissions.kt:28 | Manage people | Add and change people |
| C74 | Permissions.kt:29 | Exit kiosk mode | Use other apps |
| C64 (amended) | PinPromptController.kt:12 | $name can't do that | That PIN can’t do this. Ask an Admin. (or "That PIN can’t do this. Ask a grown-up." when Adults hold the permission; names no one, user decision A) |
| quotes | PinPad.kt:94 | Who's this? | Who’s this? |
| C62 | PinPad.kt:97 | Too many tries — wait 30s | Too many tries — wait 30 seconds. ("1 second") |
| C63 | PinPad.kt:98 | Wrong PIN — try again | Wrong PIN — try again. |
| quotes | PinPad.kt:113 | Those PINs didn't match — try again. | Those PINs didn’t match — try again. |

**app**

| Id | File:line | Current | New |
|---|---|---|---|
| C76 | KioskLifecycle.kt:42-43 | Culvery isn't the home app yet — exit kiosk, then set it in Android's Settings › Apps › Default apps. | Culvery isn’t the home app yet. |
| C77 | KioskLifecycle.kt:46-48 | Culvery can't open this tablet's home app setting — exit kiosk, then change the home app in Android's Settings › Apps › Default apps. | Culvery can’t open the home app setting. |
| S22, D41 | ShellViewModel.kt:165-172 (new toast) | | You can use other apps now. Culvery locks again when it next opens. |
| D29 | (new) | | Settings closes in 20 seconds. Touch the screen to stay. |
| D32 (amended) | (new) | | This closes in 20 seconds. Touch the screen to keep your changes. |

**capability/calendar: CalendarUi.kt**

| Id | Line | Current | New |
|---|---|---|---|
| C36 | 16 | Added from phone | Added from a phone |
| C37 | 17 | Calendar feed | the calendar’s own name, e.g. School terms |
| C24 | 85 | Syncing (badge) | Saving… |
| C24, Decision 2g | 86 | Read-only calendar (badge) | Can’t change here |
| C18 | 101 | not synced yet | Not updated yet |
| C17 | 102 | synced / synced with Google | Updated / Updated from Google Calendar |
| C19 | 108 | 5 min ago | 5 minutes ago ("1 minute ago") |
| C19 | 109 | 3 h ago | 3 hours ago ("1 hour ago") |
| C17 | 122 | Family calendar · synced with Google 5 min ago | Updated from Google Calendar 5 minutes ago |
| D37 | 122 (stale) | (the same line in `danger`) | Out of date — updated from Google Calendar 3 hours ago / Out of date — not updated yet, with the `sync_problem` icon |
| C50 | 148 | Event added for Tue 7 October | Event added on Tue 7 October. |

**capability/calendar: other logic files**

| Id | File:line | Current | New |
|---|---|---|---|
| D35 | CalendarEditor.kt:39 | Event deleted | Event deleted. |
| C40 | CalendarEditor.kt:42 | Event added | Event added. |
| C40 | CalendarEditor.kt:43 | Changes saved | Changes saved. |
| C51 | CalendarEditor.kt:46 | Couldn't open the event — try again | Couldn’t open the event — try again. |
| C52 | CalendarEditor.kt:133 | Couldn't save to Google — try again | Couldn’t delete — try again. |
| C47 | Writes.kt:33 | The event no longer exists | the event has been deleted |
| C47 | Writes.kt:66 | the calendar refused the change | (none: `Refused(null)`) |
| C47 | Writes.kt:128 | Couldn't save to Google — {reason} | Google Calendar wouldn’t accept this change. (generic refusal) / Couldn’t save to Google Calendar — {reason}. / Couldn’t save — try again. (tablet failure) |
| C52 | Writes.kt:128 (deletes) | Couldn't save to Google — {reason} | Couldn’t delete — {reason}. / Google Calendar wouldn’t accept this change. |
| C53 | Writes.kt:132 | Couldn't save 3 changes to Google | 3 changes couldn’t be saved to Google Calendar and were lost. / A change couldn’t be saved to Google Calendar and was lost — {reason}. |
| C46 | EventForm.kt:180 | Mon 22 – Wed 24 · change dates on your phone | Mon 22 – Wed 24 · change dates in Google Calendar on your phone |
| C45 | EventForm.kt:45 | 30 min | 30 minutes ("1 minute") |
| C45 | EventForm.kt:46 | 1 h | 1 hour / 2 hours |
| C45 | EventForm.kt:47 | 1 h 30 | 1 hour 30 minutes |
| C70, C32 | CalendarPermissions.kt:24 | Assign events | Choose who events are for |
| C68 | CalendarPermissions.kt:48 | Ask an adult to assign this event. | Ask a grown-up to choose who this is for. |
| D35 | CalendarReview.kt:21 | School terms now shows as Sam | School terms now shows as Sam. |
| D35 | CalendarReview.kt:23, 25 | School terms hidden / School terms shown | School terms hidden. / School terms shown. |
| D35 | CalendarReview.kt:27 | New events now go to School terms | New events now go to School terms. |
| D35 | CalendarReview.kt:29 | Google Calendar disconnected | Google Calendar disconnected. |
| C84 | CalendarReview.kt:31-35 | Disconnect Google Calendar? Its calendars leave the tablet, and 1 change still waiting to sync is dropped. | Disconnect Google Calendar? Its calendars stop showing here, and 1 change not yet saved to Google Calendar will be lost. (no changes: "… Its calendars stop showing here.") |
| D35 | CalendarSetup.kt:21, 23 | Google Calendar connected / reconnected | Google Calendar connected. / Google Calendar reconnected. |
| C87g | CalendarSetup.kt:25 | Couldn't connect to Google Calendar — try again | Couldn’t reach Google Calendar — check the tablet’s Wi-Fi, then try again. (unreachable) / Couldn’t connect to Google Calendar — try again. |
| C85 | SourceRefresher.kt:22-27 | Google Calendar: can't find the master calendar — choose a new one in Settings › Calendars. 1 change waiting to sync was dropped. | Google Calendar’s main calendar has gone. An Admin can choose another in Settings › Calendars. 1 unsaved change was lost. ("3 unsaved changes were lost.") |
| S29 | CalendarSetupSteps.kt:39 (new line) | | Choose who each calendar is for, and which to show. New events go to the main calendar. |

**capability/calendar: ui**

| Id | File:line | Current | New |
|---|---|---|---|
| C4 | TodayCard.kt:59 | Week | See the week |
| C1 | TodayCard.kt:64 | Nothing on today | Nothing on today. Tap + to add something. (only when adding is allowed) |
| C2 | ComingUpCard.kt:88 | Free | Nothing on |
| C3 | ComingUpCard.kt:91 | +2 more | +2 more in Calendar |
| C20 | WeekView.kt:79 | Google needs reconnecting / 2 calendars need reconnecting | Google Calendar needs reconnecting / 2 accounts need reconnecting |
| quotes | ConnectCalendarCard.kt:48 | Connect your family's calendar to see it here. | Connect your family’s calendar to see it here. |
| C14 | ConnectCalendarCard.kt:53 | Open settings (button) | no button; the line "No calendar services are available on this tablet." |
| C87e | CalendarConnectHost.kt:33 | Choose the family's Google account and allow access. | from the provider: Choose the family’s Google account and allow access. (Google); no line (sample) |
| C38 | EventDetailSheet.kt:55 | School terms · read-only | School terms (can’t change here) (also for not-the-main-calendar) |
| C28 | EventDetailSheet.kt:101 | From School terms (read-only) / From Family | Can’t change here |
| C28 | EventDetailSheet.kt:103 | This is a subscribed calendar, so it can't be changed here. | This calendar can only be changed by whoever runs it. |
| C30 | EventDetailSheet.kt:112 | Edit repeating events in Google Calendar on your phone. | Change repeating events in Google Calendar on your phone. |
| C32 | EventDetailSheet.kt:117 | Showing as Family until someone assigns it. | Showing as Family until someone chooses who it’s for. |
| C32 | EventDetailSheet.kt:131 | Assign to… | Choose who it’s for |
| C99 | EventDetailSheet.kt:142 | Keep event | Keep |
| C35 | EventDetailSheet.kt:165 | Edit | Change |
| C39 | EventDetailSheet.kt:211 | Syncing to Google… | Saving to Google Calendar… |
| C48 | EventEditorSheet.kt:66 | Nothing was changed. Your details are still here — check the connection and try again. | Nothing was saved. Your details are still here — try again. (tablet failure) / Nothing was saved. Your details are still here to add in Google Calendar on your phone. (refused) |
| C40, C35 | EventEditorSheet.kt:202 | New event / Edit event | Add event / Change event |
| quotes | EventEditorSheet.kt:224 | What's happening? | What’s happening? |
| C40 | EventEditorSheet.kt:387 | Save event | Add event |
| C79 | ReviewCalendars.kt:64 | Master | Main |
| C79 | ReviewCalendars.kt:66 | Make master | Use for new events |
| C81 | ReviewCalendars.kt:59 | Something went wrong | Stopped updating — disconnect and connect again. |
| C86 | ReviewCalendars.kt:69 | Can't read this calendar — check it's still shared with this account | Can’t read this calendar — check it’s still shared with this account. |
| C79 | ReviewCalendars.kt:71 | Choose another master calendar first | Choose another main calendar first. |
| C17 | ReviewCalendars.kt:76 | Synced 5 min ago | Updated 5 minutes ago |
| C82 | ReviewCalendars.kt:78 | Can't reach Google Calendar | Can’t reach Google Calendar — check the tablet’s Wi-Fi. |
| D39 | ReviewCalendars.kt (new, nothing connected) | | Connect your family’s Google Calendar to see it on Home. |

**capability/weather: WeatherWords.kt**

| Id | Line | Current | New |
|---|---|---|---|
| C10 | 11 | No forecast — check the tablet's Wi-Fi. | No forecast — check the tablet’s Wi-Fi. Culvery will try again. |
| C10 | 12 | No forecast yet — check the tablet’s Wi-Fi. | No forecast yet — check the tablet’s Wi-Fi. Culvery will try again. |
| C11 | 14 | Open settings | Add home location |
| C12 | 34 | Updated 3 h ago | Updated 3 hours ago ("Updated 1 hour ago") |

**Providers**

| Id | File:line | Current | New |
|---|---|---|---|
| C47 | google/GoogleHttp.kt:27 | the change was refused | (the shared `WRITE_REFUSED`, shown as "Google Calendar wouldn’t accept this change.") |
| quotes | google/GoogleHttp.kt:28 | this calendar can't be changed from the tablet | this calendar can’t be changed from the tablet |
| quotes | google/GoogleConnectFlow.kt:22 | That's a different Google account. Reconnect with alex@example.com. | That’s a different Google account. Reconnect with alex@example.com. |
| C107 | fake/FakeCalendarProvider.kt:146 | Repeating events can't be changed here | repeating events can’t be changed here |
| C107 | fake/FakeCalendarProvider.kt:193 | School terms can't be changed here | School terms can’t be changed here (the calendar’s name, then lower case) |
| quotes | fake/SampleEvents.kt:61 | Sunday lunch at Gran's | Sunday lunch at Gran’s |

### A.2 Spoken only (TalkBack)

New or changed descriptions, state descriptions, click labels and pane titles. They are in §4 and §5; listed here so the copy is in one place.

| Where | Spoken |
|---|---|
| Event rows and chips (click label) | open event details |
| Week chip | Swimming, Sam, Tuesday 09:00 (then ", repeats", ", saving", ", can’t change here") |
| Today row / Coming up row | Swimming, Sam, 09:00 to 10:30 / Swimming, Sam, Wednesday 09:00 |
| Spoken times | 09:00 to 10:30; from 22:00; until 01:00; all day |
| Week add hint | Add event on Tuesday 7 October |
| Week column header | Today, Tuesday 6 October / Wednesday 7 October |
| Coming up day label | Tomorrow, Wednesday 7 October / Thursday 8 October |
| Legend | Colours: Alex, green; Sam, blue; Family, amber |
| Header weather | Now 17°, partly cloudy, high 19°, low 11° |
| Theme toggle | Theme: Auto; click label "preview the night theme", "preview the day theme" or "go back to the automatic theme" |
| Text field names | Event name; Name; Your name; Town or city |
| Swatches | Green, Blue, Pink, Violet, Lime, Red, Slate, Brown; state "used by Sam" |
| Review: Show | Show School terms; main calendar state "always shown: it’s the main calendar" |
| Review: who-it’s-for chip | click label "change who School terms is for"; state "expanded" / "collapsed" |
| Review: actions | Reconnect Google Calendar, alex@example.com; Disconnect Google Calendar, alex@example.com; Use School terms for new events; Hide School terms |
| Disabled Who chip | state "not available"; click label "find out why" |
| Date cell | Tuesday 7 October, today (selected state from the tree) |
| Steppers | Hour up; Hour down; Minute up; Minute down; values "Hour 7", "Minute 30" |
| PIN dots | 2 of 4 digits entered |
| Wizard dots | Step 3 of 7 |
| Move at the ends | state "already first" / "already last"; row actions "Move Sam up", "Move Sam down" |
| People row (click label) | change Sam’s details |
| Town result (click label) | use as home |
| Assign chip (click label) | show as Sam’s event |
| Reconnect chip (click label) | reconnect |
| Pane titles | Settings; Event details; Add event; Change event; Add person or the person’s name; Pick a date; Pick a time; Enter your PIN; Choose a 4-digit PIN; Connecting to Google Calendar |

### A.3 Copy audit rows not changed by 4d-2

- **Kept, as the triage said (X1):** C9, C13, C15, C16, C21, C22, C26, C29, C33, C39a, C44, C54, C57, C58, C65, C66, C69, C73, C78, C80, C83, C87, C87c, C87d, C87f, C87h, C87i, C92, C95, C101–C103, C106, C108. Their only change, where they have one, is curly apostrophes (A.1).
- **Done in 4d-1:** C42 (section labels).
- **Dropped:** C104 ("Use a sample household", debug only; X13).
- **Assigned nowhere in the triage, so not done:** C25 (a "keep" row missing from X1, harmless), C43 ("Pick date…" vs "Pick a date"), C49 ("Try again" → "Try saving again"). They can go to the walk-through’s wording pass (§8.6).
- **Taken in part:** C20’s "— tap to fix" (not ruled; the chip is a button, so TalkBack already says so); C85’s week-view banner (layout); C72’s "Lock" or "Done" for Sign out (not ruled; Sign out stays).

## Appendix B: accessibility findings resolved

Which item resolves each accessibility audit finding (A1–A42; A20 was unused). "4d-1" means it was done there.

| Finding | Severity | Resolved by |
|---|---|---|
| A1 Overlays aren’t modal; no pane titles | major | D17 (§4.3) |
| A2 Toasts aren’t announced | major | D18 (§4.4) |
| A3 In-place errors and results aren’t announced | major | D18 (§4.4), D40 (§7.7) |
| A4 Icon-only controls unlabelled (add hint, theme toggle) | major | D15 (§4.1), D26 (§5.6) |
| A5 Colour swatches have no name | major | D21 (§5.2) |
| A6 No roles on clickables | major | D15 (§4.1), D16 (§4.2) |
| A7 Choice chips: no role or group; disabled Who chips | major | D23 (§5.4); the review chip D22 (§5.3) |
| A8 Event chips and rows don’t say whose event | major | D26 (§5.6); the visible name 4d-1 D4 |
| A9 Weather header: two items, no condition | major | D26 (§5.6) |
| A10 Forecast row test probes only the day label | minor | D26 tests (§5.6) |
| A11 No headings | minor | D19 (§4.5); section labels 4d-1 D3 |
| A12 Text fields lose their name | major | D20 (§5.1); checked on the device in E7 |
| A13 Show switch unlabelled | major | D22 (§5.3) |
| A14 Ambiguous or doubled labels | minor | D15 (§4.1), D22 (§5.3), D24 (§5.5) |
| A15 Date cells read only the day number | major | D24 (§5.5) |
| A16 Time stepper changes aren’t announced | minor | D24 (§5.5) |
| A17 PIN pad progress is silent | major | D25 (§5.5); digits spoken by TalkBack is platform behaviour (X14) |
| A18 Wizard dots nameless | minor | D27 (§5.7); a visible "Step 2 of 5" is not added (look) |
| A19 Grouping and traversal | minor | D26 (§5.6); the pointer-only area below the chips is X16 |
| A21 Chip time contrast (blocker) | blocker | 4d-1 D4 |
| A22 Light accent fails | major | 4d-1 D5 |
| A23 Non-text colour below 3:1 | major | 4d-1 D6 (weather); light person dots are W30 |
| A24 Faint affordances | minor | 4d-1 D7 |
| A25 Borderline passes | minor | 4d-1 D5 (pinned in `ContrastTest`) |
| A26 Status-bar controls small | major | 4d-1 D14; the touch check is 4e |
| A27 40–46 dp targets | minor | 4d-1 D1 |
| A28 Detail sheet footer off-screen | major | 4d-1 D12 |
| A29 PIN error line fixed height | major | 4d-1 D13 |
| A30 Status bar fixed height | major | 4d-1 D14 |
| A31 Forecast rows fixed sizes | minor | 4d-1 D14 |
| A32 Detail labels clipped | minor | 4d-1 D14 |
| A33 Week header one row | minor | 4d-1 D14 (title); the legend’s wrap is W33 |
| A34 Home grid and clock scale | minor | E1 (device) |
| A35 One-line text hides meaning | minor | 4d-1 D14 |
| A36 Whose event is colour-only | major | 4d-1 D4; palette changes W30 |
| A37 Smaller colour-only states | minor | the stale subtitle D37 (§7.5); the chosen Settings page 4d-1 D5; the wizard’s step shown by more than colour 4d-1 D7, spoken by D27 |
| A38 Settings closes silently; TalkBack never counts | major | D29 (§6.1, §6.2) |
| A39 Week view snaps back without warning | major | D30 (§6.4) |
| A40 Toasts last 3.5 s | major | D31 (§6.5) |
| A41 Setup idle and PIN lockout | minor | D29 (the setup session counts TalkBack, §6.2); D25 (lockout spoken once, §5.5); motion is X15 |
| A42 Move up and Move down: focus follows position | major | D28 (§5.8); the toast’s announcement D18 (§4.4) |
