# Culvery — Plan 9c-2: The redesign, the setup wizard and the Settings pages Implementation Plan

> Refreshed against `main` 89a53c06 on 2026-10-23 (`.superpowers/sdd/2026-10-20-culvery-09c2-redesign-wizard-settings/refresh-findings.md` and the ledger's R-rulings).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–7 are subagent tasks; Task 8 is the controller on the `culvery-5` AVD with the user; Tasks 9–10 need the user and the son's SM-T510.
>
> **9c is split in two.** **9c-1** (`docs/superpowers/plans/2026-10-20-culvery-09c-redesign-forms.md`) covers the shared sheet and form pieces. 9c-1b and 9c-1c then made Add event the full-screen editor (`EventEditorScreen`). All three have merged. This plan, **9c-2**, covers the setup wizard and the Settings pages, on a branch cut from `main` at 89a53c06, after 9c-1, 9c-1b and 9c-1c.

**Goal:** The setup wizard and the Settings pages look as the October redesign draws them.

The wizard:
- step dots that show done, here and to go;
- one green button per screen, with "Add person" quiet;
- Back and Next as large buttons inside the content column, Next with an arrow;
- errors under their field, with a suggestion, and focus moved there;
- roles as radio rows;
- colour swatches with taken ones blocked, and a line saying who has them;
- the full-width "Save person".

Settings:
- the list styled as the handoff's panel;
- Settings › Calendars as fixed rows, with the inline Main badge, a person dropdown and Show ("Always shown" on the main calendar), and read problems as a strip at the card's foot;
- the other Settings pages on the same row pattern.

**Architecture:**
- **Shared pieces first, in `:core:ui`.** The list row and switch row take the handoff's row look. This is one `HhListRow`, so the wizard, Settings and the sheets that list rows move together. The switch becomes the handoff's 59 × 33 dp switch.
- **Each further piece lands in the task that first uses it:**
  - the page title, with the wizard frame;
  - the text field's error ring, with the You step;
  - the radio row and the swatch, with the person sheet;
  - the slider row, with the Settings pages that hold sliders.
- **`:core:setup`** then redraws the wizard frame, the people list, the person sheet and Settings' frame.
- **`:capability:calendar`** redraws Review calendars, which is both Settings › Calendars and the wizard's Your calendars step.
- **The other pages** take the rows, the title and errors under fields: `:app` Display, `:capability:door`, `:capability:lights`, `:capability:music`, and `:core:setup` Location, People and Tablet lock.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00, Material 3), Hilt 2.57.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-19-culvery-9-redesign-design.md`, sections:
- §4.11 (wizard);
- §4.12 (Settings pages);
- §4.13 (Settings' countdown ring, already built in 9a);
- §6.4;
- §7.

The drawings are the handoff `docs/design/2026-10-redesign/handoff.md` and its `notes.txt` (notes 40–46). The handoff sections to read:
- "Setup wizard (5a/5b)";
- "Settings › Calendars (5c)";
- "Settings › Home buttons (2c)", for the list panel only;
- "Not redesigned".

The full HTML export is in `C:\Users\si\code\siland\culvery-notes\design-handoff-2026-10\`. Where the spec and the handoff disagree, the spec wins. Read all three with this plan.

**Previous plans (format, shared parts, rulings):** 9c-1 (its shared pieces), `docs/superpowers/plans/2026-10-21-culvery-09c1b-calendar-update.md` (see its "Shared components 9c-2 reuses"), `docs/superpowers/plans/2026-10-22-culvery-09c1c-when-and-suggestions.md`, `docs/superpowers/plans/2026-10-20-culvery-09b-redesign-tabs.md`, and `docs/superpowers/plans/2026-10-19-culvery-09a-redesign-home.md`.

**Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md`.

**Builds on:** branch `plan-9c2-wizard-settings` in the one checkout `C:\Users\si\code\siland\culvery`. There are no worktrees; the branch is cut from `main` at 89a53c06, after 9c-1, 9c-1b and 9c-1c. Work only there. Never push.

| # | Task | Review |
|---|---|---|
| 1 | Shared: the icons, the row look, the switch | sonnet |
| 2 | Wizard: the frame, the dots, the page title, Back and Next, one green button | sonnet |
| 3 | Wizard: the people list, the You step, the field's error ring | sonnet |
| 4 | The person sheet: radio rows, swatches, errors where you can see them | **opus** (refusals routed and scrolled to; focus for TalkBack; the PIN kept out of strings) |
| 5 | Settings: the list panel, the page frame and titles | sonnet |
| 6 | Settings › Calendars | **opus** (a fixed row at large font; the strip; one switch for TalkBack that can't be toggled on the main calendar) |
| 7 | The other Settings pages: slider rows, the door volume, Location's errors | sonnet |
| 8 | The emulator walk-through on `culvery-5` | controller + user |
| 9 | On the tablet | controller + user, **needs the SM-T510** |
| 10 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:**
1. **The shared row look and switch first, alone.** They move almost every wizard and Settings image, plus Favourites, Lights' room card and the location results. Those moves should be one deliberate commit.
2. **The wizard frame** (2) before its steps (3, 4).
3. **Settings' frame** (5) before the pages (6, 7).

## Rulings against the code

These are the places where the spec leaves a choice open, or where it doesn't fit the code after 9c-1. Each is the plan's call unless it cites the spec or the user.

1. **Units, touch targets and the disabled look** are as in 9c-1 rulings 1–2 and 9a:
   - dp = px ÷ 1.5;
   - touch targets are 48 dp, and a 37 dp pill sits in a 48 dp tap height;
   - there is one disabled style.

   The disabled fade stays 38 % (the user, 2026-10-20).
2. **One row look** (spec §4.12: "take the 5c row pattern"). `HhListRow` and `HhSwitchRow` take the handoff's row:
   - radius 19;
   - the light theme's 1 dp `border` edge (`CardTokens.edge`, as on cards);
   - padding 0 16 0 20;
   - at least 69 dp high, with the content centred;
   - title 18 sp W600, subtitle 14 sp.

   Every list row in the app moves with it: people, towns, connections, Door's steps, Favourites, and Display's and Door's switch rows. It stays one row, as 4d-1 made it. `PersonRow` passes `minHeight = 75.dp` (5a's 112 px), and its name uses the row title like every row (18 sp W600), not 5a's 19. `HhSwitchRow` gains `enabled` and `minHeight`; the editor's All day row passes `EditorDimens.rowHeight` (77), so the editor's five rows stay level.
3. **The switch** (the user, 2026-10-20). `HhSwitch` becomes the handoff's 88 × 50 px switch, drawn here, replacing Material's 52 × 32 one:
   - 59 × 33 dp, knob 25, inset 4;
   - on: `accent`, with an `accentInk` knob;
   - off: `surf3`, with a `mute` knob;
   - disabled-on: the faded `accent` at 38 %.

   It is one switch app-wide, so the Lights room card's switch moves to it too.

   **Behaviour:**
   - With `onCheckedChange` set, it is `toggleable(enabled = enabled, role = Role.Switch)`. Disabled means it doesn't toggle by touch or by TalkBack. Material's `enabled` was behaviour as well as look, and the redraw must keep that.
   - With `onCheckedChange` null, it only draws; the row is the switch.
   - The knob animates only on a change, never on first composition.
4. **The main calendar's switch** reads "Always shown" beside it, in place of "Show" (note 46). It is drawn disabled-on at the app's 38 %, not the handoff's 45 %. The row's `toggleable` is `enabled = false` there, as today. Its spoken state stays `ALWAYS_SHOWN` ("always shown: it’s the main calendar"), which says why; the visible text is "Always shown".
5. **One page title** for wizard steps and Settings pages (the user, 2026-10-20).
   - **What it is:** `HhPageTitle(title, line)` calls the slot overload of `HhPageHeader` (`title: @Composable () -> Unit`, 9c-1b) with `Text(title, PageHeaderTokens.pageTitleStyle, heading())`. The slot overload gains `subtitleStyle` and `subtitleGap` (defaults unchanged, 4 dp), and `HhPageTitle` passes 8. There is no `titleStyle` parameter. It draws 37 sp W700 (Settings' 56 px), with its line in 16 sp `mute` 8 below. The heading semantics and gaps exist once.
   - **Why 37 for the wizard too:** the wizard's 60 px (40 sp) is drawn at 37, because the Connect and Your calendars steps are the same composables as Settings › Calendars.
   - **Where it goes:** it is built in Task 2 for `StepTitle`. Task 5 moves every Settings page to it, including `ReviewCalendars`' title; only Task 5 changes that title.
6. **The wizard column is 640 dp** (960 px). **Settings' pane keeps 720 dp**, as its own `SetupDimens.settingsPane` (today both read `wizardColumn`) (the user, 2026-10-20).
7. **The step dots** (note 40) are 11 dp, 9 apart:
   - done steps: `accent`;
   - the current step: a 29 × 11 `accent` pill;
   - the rest: `surf3`.

   The pill's width tells the current step without colour, which the 1.5 dp rings used to do. They are spoken "Step 3 of 7", as today.
8. **Back and Next** (note 42) are `ButtonSize.Large` (60 dp; the handoff's 61, per 9c-1 ruling 5) at the foot of the 640 dp column:
   - Back on the left, quiet;
   - Next on the right, primary: `HhButton(…, size = ButtonSize.Large, trailingIcon = Icons.ARROW_FORWARD, busy = busy)`, which keeps its width while busy;
   - Skip for now is the same size, quiet.
9. **One green button per screen** (note 41).
   - **Made quiet:**
     - "Add person", which now carries `person_add`;
     - the Done step's "Choose home app", because Open Culvery is the green one.
   - **Stays primary:**
     - "Choose home app" on Settings › Tablet lock, where nothing else is green;
     - the Connect step's card's Connect, because Skip for now is quiet there.
   - **`HomeAppPrompt(onChoose, tone)`** takes the tone from its screen.
   - **Accepted exception:** Review calendars' Reconnect stays primary even beside Next. It is the fix, and it shows only while a connection needs signing in again, so that step can have two green buttons.
   - **Tests:** count primaries through a semantics property `ButtonToneKey` that `HhButton` sets (as `SpokenNameKey` is set today), on fixed tags.
10. **Errors under their field, where you can see them** (note 43; security review).
    - **The text field:** `HhTextField(error)` draws a 2 dp `danger` ring (3 px), in place of the focus ring while there is an error. Under it, 8 dp down, it draws `HhStatusLine(error, "${tag}_error", icon = ERROR, field = true, spoken = false)`, composed only while shown. `HhStatusLine` takes an explicit `field: Boolean = false` in place of comparing `style` with `ControlType.fieldError`, so the text field's caller can't drift. The field carries the error in its semantics, so TalkBack says it once.
    - **The address sheets:** `AddressSheet` (Lights and Music) stops passing `error = line` and keeps its `_line` message, because a failed connect isn't the text's fault (ruling 20). A mistyped address gets no ring.
    - **Focus:** on a refusal, the sheet or step moves focus to the errored field, so TalkBack reads the error there, and scrolls it into view.
    - **The name taken** reads "`Someone is already called {name}. Try “{name} B” or a nickname.`" (handoff; `someoneCalled`).
    - **Person sheet, save refusals:** each goes under the section it is about, as an announced `HhStatusLine` composed only while shown. The section is scrolled into view, and focus moves to that line (made focusable):
      - "That colour is taken — choose another." goes under Colour, and that colour joins the sheet's `taken` set, so the grid blocks it from then on;
      - "That PIN is taken — choose another." and "An Admin needs a PIN." go under PIN;
      - "Culvery needs at least one Admin with a PIN." from Save goes under Role.
    - **Above the footer:** refusals from **Remove** and **Move** (`remove()`, `move()`), and "Couldn’t save …" (no field), show in `person_message` above the footer. A Remove refused for the last Admin closes the confirm card and shows the reason there, so it is never out of sight.
    - **The type:** `PeopleOutcome.Refused` carries `field: PersonField?` in place of `nameTaken`.
11. **Role radio rows** (note 44) are `HhRadioRow(title, line: String? = null, selected, tag, locked = false, onClick)` in `core/ui`, with `RadioRowTokens`. It is `RepeatRow` (`capability/calendar/.../ui/RepeatsPane.kt`, 9c-1b) moved there; `RepeatsPane` and the person sheet are its two callers. It has one set of sizes, handoff 5b's (Units: min 59, r16, dot 6, 8 apart), so the editor's Repeats rows take them too and `EditorDimens.repeatRow*`, `radio` and `radioDot` go. The rows sit in a `selectableGroup`.
    - **Titles:** `Role.label` (`:core:household`), as `PersonRow` already uses ("Admin").
    - **Lines:** "Can change settings and people", "Can add and change any event", "Can add their own events" (`roleWhat`). `roleLine` goes if it is unused.
12. **Swatches** (note 45) are 51 dp, 12 apart.
    - **Taken:** at the app's 38 %, with the `block` icon in `PersonChipInk` in place of the strike. It stays not selectable (`selectable(enabled = false)`), as today.
    - **Chosen:** a 3 dp `bg` gap, a 3 dp `ink` ring and the `check` icon.
    - **The line under them,** shown when anyone else has a colour: "`{Colours} are taken by {names}.`" in palette order (`takenLine`), 13 sp `mute`. For example:
      - "Green is taken by Alex."
      - "Green and blue are taken by Alex and Sam."
      - "Green, blue and pink are taken by Alex, Sam and Mia."

      Four or more follow the same form. The person being edited is never in it. The You step has no colours taken, so it shows no line.
13. **The PIN row** in the person sheet is "Set PIN" (quiet, 48 dp) with "Without a PIN, {name} can only look." beside it, 13 dp apart (5b), instead of under it. With a long name, the hint wraps beside the button and doesn't push it. The handoff's `pin` icon isn't added.
14. **The people list's lines** take the handoff's words (5a). This is the plan's call, accepted with the handoff wording:
    - "Admin · PIN set"
    - "Adult · no PIN yet, so Sam can only look"
    - "Child · PIN set"
    - an Admin without a PIN keeps "Admin · No PIN".

    The Household step's line is the handoff's "Everyone gets a colour on the calendar. A PIN lets them add events."
15. **The Settings list** (2c's panel):
    - items 52 dp high, 7 apart, padding 0 19;
    - the panel's padding 35 at the top, 28 at the sides and foot;
    - the title 35 sp, 25 above the list.

    The chosen item keeps its 700 weight as well as the tint (4d-1 §5.2), which the handoff doesn't draw.
16. **A Settings › Calendars row** (note 46) is its own layout, no longer `HhListRow`: a card in the row look.

    **The top line** is 69 dp. Left to right:
    - the name, 18 sp W600, one line, ending in "…";
    - on the main calendar, the inline badge "Main · new events go here";
    - the person dropdown pill: 37 dp drawn in a 48 dp tap, `surf2`, with a dot, the name and `expand_more`;
    - Show.

    **Under the line,** each only when it applies:
    - the person chips, while the dropdown is open: `HhChoiceChip`s in a `selectableGroup` (`review_pick_{name}`), one person per calendar. They are not `HhPersonChip`, which is a multi-select for an event's set;
    - "Use for new events" (quiet, 48 dp), on writable calendars that aren't the main one;
    - at the very foot, the read problem strip: full width, `dangerSoft`, with `sync_problem`, the message in `ink`, and "Hide this calendar" on `surf` inside it for a calendar that isn't the main one.

    **At larger fonts** the line grows rather than clipping (Review Focus 2).
17. **The read problem's words** take the handoff's. This is the plan's call, accepted with the handoff wording.
    - **The sentence:** "`Can’t read this calendar. Check it’s still shared with {account}.`". {account} is the connection's `CONFIG_ACCOUNT`, else "this account".
    - **On the main calendar,** this is added: " To hide it, choose another main calendar first."
    - **The function:** `cantRead(account, main)` replaces `CANT_READ_CALENDAR` and `CHOOSE_ANOTHER_MAIN`. The connection's row is passed down to the source rows to get the account, which the connection row above already shows.
    - **Where it may appear:** `cantRead` is UI only. It is never passed to `Toaster`, a log, or a description a toast shares.
18. **"Main · new events go here"** is one badge (`MAIN_NEW_EVENTS_GO_HERE`), replacing the `Main` badge and the separate "New events go here" line.
19. **Sliders on Settings pages** are `HhSliderRow`, built in Task 7, which has its three callers:
    - Display's brightness rows;
    - Music's "Lower music to";
    - Door's chime volume.

    **The row:** the row look, with the title on the left, `HhPercentSlider` in the middle and the value on the right. It takes `enabled` and passes it on. It holds the value let go for at most `SETTINGS_HOLD_MS = 2_000` (`holdCeilingMs`), so a save that fails shows the stored value again.

    **Door's volume** moves from Material's `Slider` (Float) to it:
    - percent `Int` 0..100;
    - `steps = 9` (10 % steps, as `VOLUME_STEPS`);
    - saved on let go as `percent / 100f`;
    - tag `door_volume`;
    - spoken "Chime volume, 30 percent".
20. **Location's status line under the field.**
    - **Placement:** it moves directly under the town field, above the results.
    - **One composable:** it stays one always-composed `HhMessageLine` under the tag `location_status`, in `danger` for its two errors ("Couldn’t search for towns — …", "Couldn’t save the home location — try again.") and in `mute` otherwise. Swapping composables would lose the live region's node.
    - **No ring on the field:** neither error is the text's fault.
21. **Touches before `countsTouches`.** Nothing in Settings consumes a touch before the shell's counting box sees it; it listens on the `Initial` pass. Task 5 pins this for a switch, a radio row, a slider drag and a tap in a person sheet over Settings.
22. **State and secrets.**
    - **No saved state:** the new form state (`refusal`, the field focus) stays plain in-memory state, never `rememberSaveable`, so no name or PIN goes into a Bundle.
    - **The PIN never prints:** `PersonDraft.toString()` leaves out the PIN, because `PersonDraft` is a data class holding `newPin`.
    - **Pins kept as they are:** the PIN pad over the person sheet keeps hiding the sheet from TalkBack, and `form.edited` keeps its fields.
23. **The shell variable for prediction lists is `$LISTS`** (`C:/t9`). It is a plain shell variable for list files only, never exported. Each task writes both `expected-baselines.txt` and `expected-prefixes.txt` afresh (an empty file for the one it doesn't use), because `C:/t9` still holds older lists.

## Notes for the end-of-v1 design review

**These stand as the plan has them** (the user, 2026-10-20) and go on the end-of-v1 review list:
- chips stay 48 dp (9c-1);
- one 59 × 33 dp switch everywhere, the Lights room card included;
- the disabled fade stays 38 %;
- one 37 sp page title for wizard steps and Settings pages;
- the Settings pane stays 720 dp, and the wizard column is 640 dp.

**Also for that review** (this plan's calls):
- Back, Next and Save person are at Large's 60 dp with a 17 sp label;
- people rows use the one row title, 18 sp, against the handoff's 19;
- Settings › Calendars says "Family" for a calendar's household-wide tag, where New event's Who says "Everyone" (Family vs Everyone);
- the chosen Settings item stays bold;
- the 45 % "Always shown" switch is drawn at 38 %.

## Units (converted once, px ÷ 1.5)

| Where | Handoff px | dp / sp |
|---|---|---|
| Rows (5c) | r 28, padding 0 24 0 30, 1 px `border`; account 112; calendar line 104; name 27/600; sub 21 | r 19, padding 0 16 0 20, 1 `border`; min 69 (people 75); 18 sp W600; 14 sp |
| Switch | 88 × 50, knob 38, inset 6 | 59 × 33, knob 25, inset 4 |
| Page title | Settings 56/700, wizard 60/700; line 24 muted | 37 sp W700 (ruling 5); 16 sp `mute`, 8 below |
| Wizard | column 960; dots 16, pill 44 × 16, gap 14; padding top 56; dots to step 64 | 640; 11, 29 × 11, 9; 37; 43 |
| Back · Next | 92, Next arrow 34 | `ButtonSize.Large` 60, icon 24 |
| Person rows | 112, r 28, dot 22, gap 22, name 28/600, sub 21, › 36; 18 apart | 75, r 19, dot 15, gap 15, 18 sp W600, 14 sp, 24; 12 apart |
| Add person | 72 quiet, `person_add` 32 | 48 quiet, icon 24 |
| Field error | ring 3 px inset; message 21/600 + `error` 26 | ring 2; `HhStatusLine(icon = ERROR, field = true)` |
| Swatches | 76, gap 18, taken .3 + block 34, chosen 5 gap + 4 ring + check 38 | 51, 12, 38 % + block 23, 3 gap + 3 ring + check 25 |
| Taken line | 20 muted | 13 sp `mute`, 9 under |
| Radio rows | min 88, r 24, padding 14/26, radio 30 (ring 3, selected 9), gap 20, name 25/700, line 20; 12 apart; selected `accentSoft` + 3 ring | min 59, r 16, padding 9 × 17, radio 20 (ring 2, selected 6), gap 13, 17 sp W700, 13 sp; 8 apart; `accentSoft` + 2 `accent` |
| Settings list | panel 480, padding 52/42/42, title 52/700 + 28; items 78, r 24, padding 28, gap 10; Close 72 | 320, 35/28/28, 35 sp + 25; 52, r 16, 19, 7; 48 |
| Settings page | padding 64/72; rows 20 apart | 43 × 48; rows 12 apart (`rowGap`) |
| Main badge | 36, r 18, padding 0 14, 18/700; 14 from the name | 24, r 12, 0 × 9, 12 sp W700; 9 |
| Person pill | 56, padding 0 20, dot 14, 22/600, `expand_more` 26 | 37 drawn (48 tap), 0 × 13, dot 9, 15 sp W600, 17 |
| Show | label 21 muted, 12 to the switch; group 190 | 14 sp `mute`, 8; 127 |
| Read problem strip | padding 16/24/16/30; `sync_problem` 30; gap 16; text 21; button 60 on `s1` | 11 16 11 20; 20; 11; 14 sp `ink`; 48 on `surf` |
| Use for new events | 60, padding 0 30 24 | 48, 0 20 16 |

## Global Constraints

**From the spec, verbatim:**

- §4.11 "Step dots (done / here / to go) (note 40); one green button per screen, "Add person" quiet (note 41); Back and Next inside the 960 px column, 92 px, Next with an arrow (note 42); errors under their field with a suggestion (note 43); role radio rows (note 44); the full-width Save footer and the colour swatches with taken colours faded (note 45). The other wizard steps get the same patterns (handoff "Not redesigned")."
- §4.12 "Fixed 104 px rows with the inline Main badge, the person dropdown and Show; the read problem as a strip at the card's foot; "Always shown" on the main calendar (note 46). The other Settings pages (Display, Music, Lights, Door, Home location, People, Tablet lock) take the 5c row pattern and errors under fields; the Settings list takes the handoff's list styling (2c's left panel)."
- §6.4 "**One disabled button style** (the outline) everywhere … The reason line above it is **optional**".
- §7 "Every changed screen's Roborazzi baselines are re-recorded and each new image looked at against the handoff"; "Copy changes go through CopyScanTest; TalkBack labels follow the new copy."

**Copy** (verbatim, curly quotes and apostrophes in code):
- **Wizard buttons:** "Back", "Next", "Skip for now", "Add person".
- **The person sheet:**
  - buttons: "Save person", "Save changes", "Remove person", "Set PIN";
  - the PIN hint: "`Without a PIN, {name} can only look.`";
  - the name taken: "`Someone is already called {name}. Try “{name} B” or a nickname.`";
  - the taken line: "`{Colours} are taken by {names}.`" (e.g. "Green and blue are taken by Alex and Sam.").
- **Roles:**
  - names: "Admin", "Adult", "Child";
  - lines: "Can change settings and people", "Can add and change any event", "Can add their own events".
- **The people list's lines:** "`{role} · PIN set`", "`{role} · no PIN yet, so {name} can only look`".
- **The Household step's line:** "Everyone gets a colour on the calendar. A PIN lets them add events."
- **Settings:** "Settings", "Close", "Calendars".
- **Settings › Calendars:**
  - the badge and labels: "Main · new events go here", "Show", "Always shown";
  - buttons: "Use for new events", "Hide this calendar", "Disconnect";
  - the read problem: "`Can’t read this calendar. Check it’s still shared with {account}.`", "To hide it, choose another main calendar first."
- **Settings › Door:** "Chime volume".

**Privacy:**
- The read problem names the connected account, which the connection row already shows. It appears only on that page, never in a toast or log.
- The PIN never reaches a string (`PersonDraft.toString`).
- Nothing new is stored or logged.

**From the house rules** (as Plans 9a and 9b; binding):

- **Platform:**
  - the package root is `uk.co.siland.culvery`;
  - `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only;
  - **no new library; never change a version**;
  - no deprecated API; the one opt-in is `ExperimentalMaterial3Api` in `HhPercentSlider`.
- **The test gate:**
  - run `./gradlew testDebugUnitTest verifyRoborazziDebug`;
  - never plain `test`, `check` or `build`, and never a Gradle `install…` task;
  - no subagent touches a device;
  - **never set TMP, TEMP, TMPDIR or `java.io.tmpdir`** for Gradle. A long temp path pushes Robolectric's database paths past Windows' MAX_PATH (SQLITE_CANTOPEN);
  - `$LISTS` below is `C:/t9`, a shell variable for prediction lists only, never exported.
- **Tests that can fail:**
  - every new test fails before its change; a test that passes before it is labelled **pin**;
  - timer tests run on virtual time; UI timers run under `compose.mainClock`, one frame before the boundary and at the boundary plus one frame. A test that toggles the switch then reads pixels advances `mainClock` past the knob's animation;
  - colour checks read a pixel inside a fill but clear of its content, through `pixelAt(node, dx, dy)` (`core/ui/src/testFixtures/.../test/PixelAt.kt`), never the centre of something with a label. Every test that reads pixels is `@GraphicsMode(GraphicsMode.Mode.NATIVE)`;
  - tokens aren't asserted against the literals that set them.
- **Every UI task ends the same way:**
  1. Run `compareRoborazziDebug`.
  2. Open each `*_compare.png`, and each new image, against the handoff section the task names.
  3. Record.
  4. Run the baseline check. Write both `$LISTS/expected-baselines.txt` (exact names, one per line) and `$LISTS/expected-prefixes.txt` (name prefixes) afresh, with an empty file for the one the task doesn't use. Then:
```bash
git status --porcelain -- "*.png" | sed 's#.*/##; s#\.png$##' | sort -u > "$LISTS/changed.txt"
echo "changed but not listed:"; comm -23 "$LISTS/changed.txt" <(sort -u "$LISTS/expected-baselines.txt")
echo "listed but unchanged:"; comm -13 "$LISTS/changed.txt" <(sort -u "$LISTS/expected-baselines.txt")
echo "changed but not predicted:"; grep -v -f <(sed 's/^/^/' "$LISTS/expected-prefixes.txt") "$LISTS/changed.txt"
echo "predicted but unchanged:"; while read -r p; do grep -q "^$p" "$LISTS/changed.txt" || echo "$p"; done < "$LISTS/expected-prefixes.txt"
```
     Both lists of each pair must be empty, or stop. Anything outside the prediction stops the task. (An empty expected file makes its pair print everything changed; read only the pair that applies.)
  5. Run the gate.
  6. Commit.

  Baselines live in `<module>/src/test/screenshots/`. Each task lists only its predictions and its commit line.
- **Copy and accessibility:**
  - copy names what the user does; use curly quotes, and "·", "›", "→", "…" as written; `CopyScanTest` passes;
  - every changed screen's test ends with `compose.assertClickablesHaveRoleAndLabel()`;
  - TalkBack names follow the copy.
- **Code:**
  - icons come from `Icons`; a new one goes in `tools/fonts/icons.txt`, and the subset is rebuilt from the full font at 608ce00 (`git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$LISTS/material-symbols-full.ttf"`, then `python tools/fonts/subset.py --source "$LISTS/material-symbols-full.ttf"`; the full font is already at `C:/t9/material-symbols-full.ttf`);
  - layout numbers are named in the module's dimens object;
  - comments say what the code can't, in one line, with spec refs as `(9 §4.12)`; no plan task or ruling numbers;
  - no personal data: sample names only (Alex, Sam, Mia, Lounge, Kitchen, Hall, Garden, Front Door, Back Garden);
  - files are UTF-8.
- **Git:**
  - commits are a plain sentence, with no trailer;
  - never push;
  - **never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **A name taken, then corrected; refusals for other fields and from Remove.**
   - **Cases:**
     - "Sam" is saved while Sam exists, then a letter is typed;
     - the colour is taken meanwhile on another tablet;
     - the last Admin's PIN is removed;
     - the last Admin is removed;
     - with the keyboard up at font scale 1.3.
   - **Expected:**
     - the name's ring and line show;
     - focus moves to the name field, and TalkBack says the error once;
     - typing clears both;
     - the colour refusal shows under Colour, scrolled into view, and that colour is now blocked;
     - the PIN refusal shows under PIN;
     - the Remove refusal shows above the footer.
   - **Tests:**
     - Task 3:
       - `ControlsTest.anErrorRingsTheFieldAndSitsUnderIt`, `theErrorIsSaidOnce`;
       - `YouFormTest.aTakenNameIsAnErrorOnTheNamedFieldAndSaidOnce` (extended), `aTakenNameMovesFocusToTheNameField`.
     - Task 4, in `PeopleUiTest`:
       - `aTakenNameShowsUnderTheNameAndTypingClearsIt`, `aTakenNameMovesFocusToTheNameField`;
       - `aTakenColourShowsUnderColourAndIsBlocked`, `aPinRefusalShowsUnderPin`;
       - `aFieldRefusalIsScrolledIntoViewWithTheKeyboardUp`, `aLastAdminRemoveRefusalShowsAboveTheFooter`.
2. **Settings › Calendars at large font with long names.**
   - **Cases:**
     - a 40-character calendar name on the main calendar (badge inline), with the person "Family";
     - at font scale 1.0 and 1.3;
     - on the 720 dp pane and in the wizard's 640 dp column.
   - **Expected:**
     - at 1.0, the line is 69 dp ±1;
     - at 1.3, it grows and nothing overlaps;
     - the name ends in "…";
     - the badge, the pill and Show stay whole.
   - **Tests** (Task 6): `ReviewCalendarsTest.aCalendarsLineIsSixtyNineHigh`, `aLongNameWithTheBadgeFitsAtLargeFont`.
3. **The main calendar can't be read while its people are being picked.**
   - **Cases:**
     - the main calendar with a read problem, with the dropdown open;
     - a hidden calendar with a read problem;
     - a tap on the main calendar's Show.
   - **Expected:**
     - the chips sit under the line, and the strip at the very foot, with no Hide button and "To hide it, choose another main calendar first.";
     - the hidden calendar shows no strip;
     - Show doesn't toggle.
   - **Tests** (Task 6): `ReviewCalendarsTest.theStripSitsAtTheFootUnderTheChips`, `theMainCalendarsProblemSaysChooseAnotherFirst`, `aHiddenCalendarShowsNoReadProblem` (existing **pin**), `theMainCalendarsSwitchCannotBeToggled`.
4. **The wizard with the keyboard up, and the steps' green buttons.**
   - **Cases:**
     - the You step's name field focused, with a 320 dp keyboard, at font scale 1.3;
     - the Household step with only you;
     - the Done step;
     - Review calendars with a connection needing sign-in.
   - **Expected:**
     - Back and Next stay above the keyboard, inside the 640 dp column;
     - at most one primary on each step, except Review's accepted Reconnect beside Next.
   - **Tests:**
     - Task 2: `SetupButtonsTest.theWizardsButtonsStayAboveTheKeyboard` (extended to 1.3), `backAndNextSitInsideTheColumn`, `aToastSitsAboveTheWizardsNext`; `StepsUiTest.eachStepHasAtMostOneGreenButton` (Welcome, Household, Done).
     - Task 6: `ReviewCalendarsTest.aConnectionNeedingSignInHasOneGreenButton`.
5. **Settings' idle close, and sliders that save.**
   - **Cases:**
     - a tap on a Settings switch, a radio row or a slider drag while Settings is open;
     - the door volume dragged to 33 % and let go;
     - a save that fails.
   - **Expected:**
     - each touch counts (the session's idle wait restarts);
     - the volume lands on 30 % and saves 0.3;
     - after a failed save, the slider shows the stored value within 2 s.
   - **Tests:**
     - Task 5: `AppContentTest.aTapOnASettingsSwitchCountsAsATouch` (**pin**) and its siblings.
     - Task 7: `DoorSettingsPageTest.theVolumeStepsInTensAndSavesOnLetGo`, `aFailedSaveReturnsTheSliderToTheStoredVolume`.

The spec's own tests (§7):
- CopyScanTest and TalkBack names, in every task;
- images looked at against 5a, 5b, 5c and 2c, in Tasks 1–7.

---

## File Structure

```
core/ui/.../Icons.kt, tools/fonts/icons.txt, the font; Components.kt (HhListRow, ListRowTokens), Rows.kt (HhSwitchRow), Controls.kt (HhSwitch); capability/calendar/.../CalendarType.kt (EditorDimens), EventEditorScreen.kt (All day row) (Task 1)
core/ui/.../PageHeader.kt (the slot overload's subtitle styles, HhPageTitle), Buttons.kt (ButtonToneKey); core/setup/.../SetupWizard.kt, SetupDimens.kt, SetupUi.kt, steps/DoneStep.kt, pages/KioskPage.kt (Task 2)
core/ui/.../Controls.kt (HhTextField's error, HhStatusLine's `field`), AddressSheet.kt; core/setup/.../PeopleUi.kt, SetupCopy.kt, steps/HouseholdStep.kt, steps/YouStep.kt (Task 3)
core/ui/.../Controls.kt (HhSwatch, HhSwatchGrid), Rows.kt (HhRadioRow); capability/calendar/.../RepeatsPane.kt, CalendarType.kt; core/setup/.../PersonEditorSheet.kt, PeopleEditor.kt (PersonField, PersonDraft), SetupCopy.kt (Task 4)
core/setup/.../SettingsScreen.kt, pages/*.kt; app/.../display/ui/DisplaySettingsPage.kt; capability/{door,lights,music}/.../ui/*SettingsPage.kt; capability/calendar/.../ui/ReviewCalendars.kt (title) (Task 5)
capability/calendar/.../ui/ReviewCalendars.kt (incl. ReviewCalendarsHost), ui/CalendarType.kt (Task 6)
core/ui/.../Rows.kt (HhSliderRow); capability/door/.../ui/DoorSettingsPage.kt; app/.../DisplaySettingsPage.kt; capability/music/.../MusicSettingsPage.kt; core/setup/.../LocationPane.kt (Task 7)
docs/superpowers/plans/2026-09-23-plan1-followups.md (Tasks 8–10)
```

---

### Task 1: Shared: the icons, the row look, the switch (rulings 2, 3)

**Review:** sonnet.

**Files:**
- Modify:
  - `core/ui/.../Icons.kt`, `tools/fonts/icons.txt`, and the font (regenerated from the full font as in Global Constraints). Only `block` and `person_add` are added.
  - `core/ui/.../Components.kt`: `ListRowTokens`, and `HhListRow(minHeight)`.
  - `core/ui/.../Rows.kt`: `HhSwitchRow`. Its `ListRowTokens.paddingH` at :43 becomes `paddingStart`/`paddingEnd`. It gains `enabled` and `minHeight`.
  - `capability/calendar/.../ui/CalendarType.kt`: `EditorDimens.rowRadius`, `rowPaddingStart` and `rowPaddingEnd` point at `ListRowTokens` (no pixel change), so it stays one look.
  - `capability/calendar/.../ui/EventEditorScreen.kt:439`: the All day row passes `minHeight = EditorDimens.rowHeight`.
  - `core/ui/.../Controls.kt`: `HhSwitch`, `SwitchTokens`.
- Test:
  - `core/ui/src/test/.../IconFontTest.kt`.
  - Create `RowLookTest.kt` and `SwitchTest.kt`.
  - Change to 18 sp (not pins): `RowStylesTest.assertRowTitle` in `core/setup`, `ListRowAndLabelTest.aRowsTitleAndSubtitleAreOneLineInTheSharedStyles` in `core/ui`, and `ReviewCalendarsTest.connectionAndCalendarRowsUseTheSharedRowStyles` in `capability/calendar`.
  - Pins that stay green: `RowsTest.aSwitchRowIsOneSwitch` and `EventEditorScreenTest`.
  - A new `rows_dark`/`rows_light` screenshot in core/ui.

**Interfaces:**
- Produces:
  - **Icons:** `Icons.BLOCK = "block"`, `Icons.PERSON_ADD = "person_add"`.
  - **`ListRowTokens`:**
    - `radius = 19.dp`;
    - `paddingStart = 20.dp`, `paddingEnd = 16.dp`, `paddingV = 12.dp`;
    - `minHeight = 69.dp`;
    - `titleStyle = HhType.rowTitle.copy(fontSize = 18.sp)`.
  - **`HhListRow(…, minHeight: Dp = ListRowTokens.minHeight)`**, with the `border` edge.
  - **`HhSwitchRow(title, line, checked, tag, onChange, enabled: Boolean = true, minHeight: Dp = ListRowTokens.minHeight)`**, in the same container. Its `toggleable` passes `enabled`. The editor's All day row passes `minHeight = EditorDimens.rowHeight` (77), so its five rows stay level.
  - **`HhSwitch(checked, onCheckedChange, tag, enabled)`**, drawn per ruling 3:
    - `SwitchTokens.width = 59.dp`, `height = 33.dp`, `knob = 25.dp`, `inset = 4.dp`;
    - the knob slides with `animateDpAsState`, but not on first composition.

- [ ] **Step 1: The failing tests.** Each ends with `assertClickablesHaveRoleAndLabel()`.
  - **Icons:** `IconFontTest` covers the two new glyphs.
  - **`RowLookTest`:**
    - `aListRowIsAtLeastSixtyNineHigh`: a one-line `HhListRow` is 69 dp ±1; a `minHeight = 75.dp` row is 75 dp. The title's text is 3 dp below the row's top padding (a layout relationship, not a token).
    - `aListRowHasTheCardEdgeInLight`: in the light theme, `pixelAt(row, 0.dp, 30.dp)` is `border` (0xFFD5DCD8); in dark it is the row's `surf`.
    - `aSwitchRowIsTheSameRow`: a one-line `HhSwitchRow` is 69 dp and is still one `Role.Switch` node.
    - `aSwitchRowTakesAMinHeight`: with `minHeight = 77.dp` it is 77 dp.
  - **`SwitchTest`:** toggles run with `mainClock.autoAdvance = false`, advancing 300 ms before reading.
    - `theSwitchIsFiftyNineByThirtyThree`: the switch is 59 × 33 dp.
      - On: `pixelAt(switch, 50.dp, 16.dp)` is `accentInk` and `pixelAt(switch, 6.dp, 16.dp)` is `accent`.
      - Off: the knob side is `mute`, and the track is `surf3`.
      - Disabled-on: the track is `accent` at 38 % over the background.
    - `aSwitchThatOnlyDrawsHasNoAction`: with `onCheckedChange = null`, there is no clickable node.
    - `aDisabledSwitchDoesNotToggle`: with `enabled = false` and a callback set, a tap and TalkBack's toggle action both leave `onCheckedChange` uncalled.
    - `aDisabledOnSwitchStaysOnAndIsNamedAsDisabled`: the switch reads `ToggleableState.On` and `isNotEnabled`.
    - `theKnobDoesNotAnimateOnFirstShow`: at frame 0, an on switch's knob is already on the right.

  Run `./gradlew :core:ui:testDebugUnitTest --tests '*RowLook*' --tests '*SwitchTest*' --tests '*IconFont*' --tests '*ListRowAndLabel*' --tests '*RowsTest*' :core:setup:testDebugUnitTest --tests '*RowStyles*' :capability:calendar:testDebugUnitTest --tests '*ReviewCalendarsTest*' --tests '*EventEditorScreenTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces and Units.
- [ ] **Step 3: Images.**
  - **Predicted prefixes:** every list row, switch row or switch:
    - wizard: `people_list_`, `location_`, `you_`, `done_`;
    - Settings: `settings_`, `review_`;
    - other screens: `favourites_sheet_`, `lights_tab_`, `lights_grid_`, `lights_room_`, `tab_lights_`;
    - calendar editor (the All day row): `add_event_`, `change_event_`.
  - **New images:** `rows_dark`, `rows_light`.
  - **Refine** from the compare report before re-recording, and stop on anything outside it.
  - **Check against** 5c's rows, and the editor's rows.
```bash
git add core/ui core/setup capability app tools/fonts/icons.txt
git commit -m "Give list and switch rows the redesign's row, and make the switch the redesign's size"
```

---

### Task 2: Wizard: the frame, the dots, the page title, Back and Next, one green button (spec §4.11; rulings 5–9; Review Focus 4)

**Review:** sonnet.

**Files:**
- Modify, in `core/ui`:
  - `.../PageHeader.kt`: the slot overload of `HhPageHeader` gains `subtitleStyle` and `subtitleGap`; create `HhPageTitle` and `PageHeaderTokens.pageTitleStyle`.
  - `.../Buttons.kt`: `ButtonToneKey`, set by `HhButton` and `HhRoundButton`.
- Modify, in `core/setup`:
  - `SetupWizard.kt`: `WizardFrame`, `Dots`, and the Back / Skip / Next row inside `KeepsToastsAbove`.
  - `SetupDimens.kt`:
    - `wizardColumn = 640.dp`;
    - `settingsPane = 720.dp`;
    - the dots;
    - `wizardPaddingTop`;
    - `dotsBottom`.
  - `SetupUi.kt`: `StepTitle` → `HhPageTitle`; `HomeAppPrompt(onChoose, tone)`.
  - `steps/DoneStep.kt`: quiet.
  - `pages/KioskPage.kt`: primary.
  - `SettingsScreen.kt`: reads `settingsPane`.
- Test:
  - `WizardDotsTest.kt`.
  - `SetupButtonsTest.kt`: `wizard_back`, `wizard_next` and `wizard_skip` at 60 dp.
  - `StepsUiTest.kt`, `KioskPageTest.kt`, `SettingsScreenTest.kt`, `SetupScreenshotTest.kt`.
  - `core/ui` `PageHeaderTest.kt`.

**Interfaces:**
- Consumes: `ButtonSize.Large`; `Icons.ARROW_FORWARD`; `HhButton(trailingIcon, busy)`; `KeepsToastsAbove`.
- Produces:
  - The slot overload `HhPageHeader(…, subtitleStyle: TextStyle = PageHeaderTokens.subtitleStyle, subtitleGap: Dp = PageHeaderTokens.subtitleGap)`.
  - `@Composable fun HhPageTitle(title: String, line: String? = null, modifier: Modifier = Modifier)`.
  - `val ButtonToneKey: SemanticsPropertyKey<ButtonTone>`.
  - `SetupDimens.settingsPane`.
  - `HomeAppPrompt(onChoose: () -> Unit, tone: ButtonTone)`.

- [ ] **Step 1: The failing tests.** Each ends with `assertClickablesHaveRoleAndLabel()`.
  - **`PageHeaderTest`:**
    - `aPageTitleIsAHeadingWithItsLineUnderIt`: "Calendars" is a heading; its line's top is 8 dp below the title's bottom (±1).
    - `theStringTitleIsUnchanged` and `aComposableTitleSitsWhereTheStringOneDoes` already pin the header (**pins**). Both stay green.
  - **`WizardDotsTest`:**
    - `doneHereAndToGo` replaces `theCurrentStepIsAFilledDotAndTheOthersAreRings`. With 7 dots at index 3:
      - dots 0–2 are `accent` and 11 dp (`pixelAt` at their centre; the dots carry no text);
      - dot 3 is 29 × 11 dp;
      - dots 4–6 are `surf3`;
      - the gaps are 9 dp.
    - `theDotsAreOneSpokenStep` keeps passing.
  - **`SetupButtonsTest`:**
    - `theWizardsButtonsAreButtons` (changed):
      - `wizard_back`, `wizard_next` and `wizard_skip` are 60 dp; `welcome_sample` and `people_add` are 48 dp;
      - `wizard_next` has `arrow_forward` after its label.
    - `backAndNextSitInsideTheColumn` (Review Focus 4): at 1280 × 800 dp, `wizard_back`'s left equals `wizard_step`'s left, and `wizard_next`'s right equals its right (±1 dp). `wizard_step` is 640 dp wide.
    - `theWizardsButtonsStayAboveTheKeyboard` (extended, Review Focus 4): at font scale 1.0 and 1.3.
    - `aToastSitsAboveTheWizardsNext`: a toast's bottom is at least `ShellTokens.toastAboveFoot` above `wizard_next`.
  - **`StepsUiTest.eachStepHasAtMostOneGreenButton`** (Review Focus 4). Count the nodes whose `ButtonToneKey` is `Primary`:
    - **Welcome:** 1 (`wizard_next`).
    - **Household, with only you:** 0. `people_add` and `wizard_skip` are `Quiet`.
    - **Done, with the home-app prompt showing:** 1 (`wizard_next`). `choose_home_app` is `Quiet`.

    Review calendars' count is checked in Task 6, because `:core:setup` doesn't depend on `:capability:calendar` and `WizardFrame` is `internal`.
  - **`KioskPageTest.chooseHomeAppIsTheGreenButtonThere`:** `choose_home_app`'s `ButtonToneKey` is `Primary`.
  - **`SettingsScreenTest.thePaneStaysSevenTwentyWide`** (a **pin** of ruling 6): `settings_pane` is at most 720 dp on a 1280 dp screen.

  Run `./gradlew :core:ui:testDebugUnitTest --tests '*PageHeader*' :core:setup:testDebugUnitTest --tests '*WizardDots*' --tests '*SetupButtons*' --tests '*StepsUi*' --tests '*KioskPage*' --tests '*SettingsScreen*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 5–9 and Units. The buttons' row moves inside the column's `Box`, at its foot, `width(wizardColumn)`, centred. `enabled = forward.enabled && !busy` and the saved `current` step stay as they are. Next also passes `busy = busy`, so it keeps its width.
- [ ] **Step 3: Images.**
  - **Predicted prefixes:**
    - wizard steps: `wizard_frame_`, `welcome_`, `location_`, `you_`, `done_`;
    - also `people_list_`, if it is drawn in the frame (refine);
    - Settings pages: `settings_kiosk_`, `settings_location_`, `settings_people_`, `settings_all_pages_` (each through `StepTitle`).
  - **Must not change:** `wizard_pin_gate_`, `review_` (its title waits for Task 5), `connect_step_`, `connected_step_` (they draw only a card), and 9b's `page_header_`, `week_`, `door_tab_`, `lights_tab_` (Task 2 only) and `music_tab_`.
  - **Check against** 5a.
```bash
git add core/ui core/setup
git commit -m "Lay the wizard in a 640 dp column with done, here and to-go dots, a shared page title, and Back and Next as big buttons at its foot, with one green button per step"
```

---

### Task 3: Wizard: the people list, the You step, the field's error ring (spec §4.11; rulings 2, 9, 10, 14; Review Focus 1)

**Review:** sonnet.

**Files:**
- Modify:
  - `core/ui/.../Controls.kt`: `HhTextField`'s error ring and the line under it (ruling 10); `HhStatusLine` takes `field: Boolean = false` in place of its `style == ControlType.fieldError` test.
  - `core/ui/.../AddressSheet.kt`: drops `error = line`; its `_line` message stays (ruling 10). Its tests live in `capability/lights/.../ui/LightsSettingsPageTest.kt`, `capability/music/.../ui/MusicSettingsPageTest.kt` and their `*SettingsScreenshotTest.kt`: check what they assert about the address sheet's error and keep them green.
  - `core/setup/.../PeopleUi.kt`:
    - `PeopleList`: Add person goes quiet, with `PERSON_ADD`;
    - `PersonRow(minHeight = 75.dp)`, with the dot at 15;
    - `pinState`.
  - `SetupCopy.kt`: `someoneCalled` (changed), `WHO_ELSE_LINE`, `noPinYet`.
  - `steps/HouseholdStep.kt`: the line.
  - `steps/YouStep.kt`:
    - the name error now sits under the field;
    - focus moves to `you_name` on a taken name;
    - `HhMessageLine` keeps only the messages that aren't about a field.
  - `PersonEditorSheet.kt`: `person_message` shows nothing while it equals the name error, so the error isn't shown twice before Task 4.
- Test:
  - `core/ui` `ControlsTest.kt`, beside `aFilledTextFieldKeepsItsNameAndCarriesItsError` (:201), and `StatusLineTest.kt` (`aFieldsOwnErrorIsHiddenFromTalkBack`, :57). No new file.
  - The Lights and Music address sheets' tests.
  - `PeopleUiTest.kt`: `theListShowsEachPersonsRoleAndWhetherTheyHaveAPin`.
  - `YouFormTest.kt:26, 32`, `SetupCopyTest.kt`, `PeopleEditorTest.kt:114`, `StepsTest.kt:211–212`, `PeopleUiTest.kt:195, 199`.
  - `RowStylesTest.kt` (**pin**).
  - `SetupScreenshotTest.kt:119`.

**Interfaces:**
- Consumes: `HhStatusLine(icon, field, spoken)`; `HhListRow(minHeight)`, `Icons.PERSON_ADD` (Task 1).
- Produces:
  - `HhStatusLine(…, field: Boolean = false)`.
  - `HhTextField(error)` keeps its signature. While `error != null`, it draws the ring and `${tag}_error` under the field.
  - `internal fun someoneCalled(name: String): String = "Someone is already called $name. Try “$name B” or a nickname."`.
  - `internal const val WHO_ELSE_LINE`.
  - `internal fun noPinYet(name: String): String = "no PIN yet, so $name can only look"`.

- [ ] **Step 1: The failing tests.** Each ends with `assertClickablesHaveRoleAndLabel()`.
  - **`ControlsTest` (text field):**
    - `anErrorRingsTheFieldAndSitsUnderIt` (Review Focus 1). With `error = someoneCalled("Sam")`:
      - `pixelAt(field, 1.dp, 30.dp)` is `danger`;
      - `x_error` is displayed, its top 8 dp under the field's bottom.
      With `null`, there is no `x_error` and no ring.
    - `theErrorIsSaidOnce` (Review Focus 1):
      - the field's semantics carry the error;
      - `x_error` has `HideFromAccessibility` and no live region;
      - the only node whose text or error contains the sentence is the field.
  - **Address sheets:** `aFailedConnectShowsItsLineOnceAndDoesNotRingTheField` (Lights and Music): the sheet's `_line` shows the message once, and the field has no `danger` edge.
  - **`PeopleUiTest`:**
    - `theListShowsEachPersonsRoleAndWhetherTheyHaveAPin` (changed, ruling 14):
      - Alex reads "Admin · PIN set";
      - Sam, without a PIN, reads "Adult · no PIN yet, so Sam can only look";
      - an Admin without a PIN reads "Admin · No PIN".
    - `addPersonIsQuietWithItsIcon` (note 41):
      - `people_add` "Add person" is 48 dp;
      - its `ButtonToneKey` is `Quiet`;
      - `person_add` comes before the label.
    - `aPersonRowIsSeventyFiveHigh`: `person_Sam` is 75 dp ±1. A 30-character name at font scale 1.3 grows the row, and the name ends in "…".
    - `theHouseholdStepSaysWhyColoursAndPins`: the step shows "Who else lives here?" and `WHO_ELSE_LINE`.
  - **`YouFormTest`:**
    - `aTakenNameIsAnErrorOnTheNamedFieldAndSaidOnce` (extended, Review Focus 1). A taken "Sam" shows `you_name_error` with the suggestion, `you_message` is empty, and typing clears both.
    - `aTakenNameMovesFocusToTheNameField` (Review Focus 1): after Next is refused, `you_name` `assertIsFocused`.
  - **Copy tests:**
    - These expect the new `someoneCalled`: `PeopleEditorTest:114`, `StepsTest:211–212`, `YouFormTest:26, 32` and `PeopleUiTest:195, 199`. `SetupScreenshotTest:119` is a literal; it changes to `someoneCalled("Sam")` so `person_error_` shows the suggestion.
    - `SetupCopyTest.someoneCalledSuggestsAnotherName`.

  Run `./gradlew :core:ui:testDebugUnitTest --tests '*ControlsTest*' --tests '*StatusLine*' (and the address sheets' tests) :core:setup:testDebugUnitTest --tests '*PeopleUi*' --tests '*YouForm*' --tests '*SetupCopy*' --tests '*PeopleEditor*' --tests '*StepsTest*' --tests '*RowStyles*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces and Units.
- [ ] **Step 3: Images.**
  - Predicted: `people_list_`, `you_`, `settings_people_`, `person_error_` (the ring and the line; it moves again in Task 4).
  - **Must not change:** `lights_address_sheet_`, `music_address_sheet_` (dropping `error = line` keeps them as they are).
  - New: `you_name_taken_dark`.
  - Check against 5a and 5b's name error.
```bash
git add core/ui core/setup
git commit -m "Make Add person a quiet button, give each person's row the redesign's size and words, and show a taken name under its field with a suggestion"
```

---

### Task 4: The person sheet: radio rows, swatches, errors where you can see them (spec §4.11; rulings 10–13, 22; Review Focus 1)

**Review:** opus.

**Files:**
- Modify:
  - `core/ui/.../Controls.kt`: `HhSwatch`, `HhSwatchGrid`, `ControlTokens.swatch*`.
  - `core/ui/.../Rows.kt`: create `HhRadioRow` and `RadioRowTokens` from `RepeatRow`.
  - `capability/calendar/.../ui/RepeatsPane.kt`: `RepeatRow` goes and it calls `HhRadioRow`. `ui/CalendarType.kt`: `EditorDimens.repeatRow*`, `radio` and `radioDot` go.
  - `core/setup/.../PersonEditorSheet.kt`:
    - `PersonForm`'s refusals by field;
    - the role rows as `HhRadioRow`s;
    - the header and padding as handoff 5b: title 35 sp W700 (`SetupType.sheetTitle` → 35 sp; fix its stale comment "(the calendar editor's title)"), padding 29 top, 32 sides, 27 bottom (`SetupDimens.sheetPadding*`);
    - the sections, 16 apart, with the label 8 from its control;
    - the PIN row and the footer: Save person / Save changes is `HhFooterButton(…, tag = "person_save", busy = busy)` in a Row after Remove person (Large, `ButtonTone.Danger`), as the editor's `Foot`;
    - the foot and the confirm card inside `KeepsToastsAbove`;
    - `BringIntoViewRequester` and a `FocusRequester` per section.
  - `PeopleEditor.kt`:
    - `PeopleOutcome.Refused(message, field)`;
    - `enum class PersonField { NAME, COLOUR, ROLE, PIN }`;
    - `PersonDraft.toString`.
  - `SetupCopy.kt`: `takenLine`, `roleWhat`.
  - `SetupDimens.kt`: the sheet is `ShellTokens.sheetWidth` (613 dp), so the comment "(§4.4: HhSheet, 600 dp)" changes.
- Test:
  - `PeopleUiTest.kt`. The kept halves of the replaced tests stay:
    - from `aRefusalShowsInTheSheetAndKeepsWhatWasTyped`, that what was typed is kept;
    - from `aNewPersonStartsOnTheFirstFreeColourWithTakenOnesStruck`, that a new person starts on the first free colour.
  - `PeopleEditorTest.kt`, `PersonFormTest.kt`, `SetupCopyTest.kt`.
  - `SetupButtonsTest.kt`: `person_save` and `person_remove` at 60 dp.
  - `core/ui` `ControlsTest.kt`: the swatch tests (`aTakenSwatchIsNamedDisabledAndSaysWhoHasIt` :57, `theSwatchesAreARadioGroupWithTheChosenOneSelected` :70) and the radio-row test.
  - `capability/calendar` `RepeatsPaneTest.kt`.
  - `SetupScreenshotTest.kt`.

**Interfaces:**
- Consumes: `HhTextField(error)` (Task 3); `HhFooterButton`, `HhStatusLine`, `KeepsToastsAbove` (on main); `Icons.BLOCK` (Task 1).
- Produces:
  - **`HhRadioRow(title, line: String? = null, selected: Boolean, tag: String, locked: Boolean = false, onClick: () -> Unit)`** and `RadioRowTokens` (Units: min 59, r16, dot 6, 8 apart).
  - **`PeopleOutcome.Refused(val message: String, val field: PersonField? = null)`.** `nameTaken` goes. From `add`/`save`, the messages map to fields as follows; everything else, including every outcome of `remove()` and `move()`, gets `null`:

    | Message | Field |
    |---|---|
    | `someoneCalled` | `NAME` |
    | `COLOUR_TAKEN` | `COLOUR` |
    | `PIN_TAKEN`, `ADMIN_NEEDS_PIN` | `PIN` |
    | `NEEDS_AN_ADMIN` | `ROLE` |

  - **In `PersonForm`:** `var refusal: PeopleOutcome.Refused?` replaces `message` and `nameError`. Any change clears it. A refusal is not an edit, and `taken` grows by the refused colour on a `COLOUR` refusal.
  - **`internal fun takenLine(taken: Map<Long, String>): String?`** (ruling 12): null when nothing is taken.
  - **`internal fun roleWhat(role: Role): String`**.

- [ ] **Step 1: The failing tests.** These are in `PeopleUiTest` unless named; each ends with `assertClickablesHaveRoleAndLabel()`.
  - **The name** (Review Focus 1):
    - `aTakenNameShowsUnderTheNameAndTypingClearsIt`: adding "Sam" while Sam exists shows `person_name_error` with the suggestion and the ring. `person_message` stays empty. Typing "Sam B" clears both, and what was typed is kept.
    - `aTakenNameMovesFocusToTheNameField`: `person_name` `assertIsFocused`.
  - **Colour and PIN refusals:**
    - `aTakenColourShowsUnderColourAndIsBlocked` (Review Focus 1):
      - the editor refuses with `COLOUR_TAKEN`;
      - `person_colour_error` shows under the swatches, announced and focused;
      - that swatch is now blocked;
      - nothing shows under Name.
    - `aPinRefusalShowsUnderPin` (Review Focus 1): `ADMIN_NEEDS_PIN` shows `person_pin_error`.
    - `aFieldRefusalIsScrolledIntoViewWithTheKeyboardUp` (Review Focus 1): with a 320 dp keyboard, at font scale 1.3, a `PIN` refusal leaves `person_pin_error` displayed.
  - **Refusals above the footer:**
    - `aLastAdminRemoveRefusalShowsAboveTheFooter` (Review Focus 1): Remove is confirmed for the last Admin and refused with `NEEDS_AN_ADMIN`. The confirm card closes, `person_message` shows the reason above the footer, and no field error appears.
    - `aSaveFailureStaysAboveTheFooter`: `couldNotSaveDetails` shows in `person_message`, with no field error.
  - **Role and colour:**
    - `rolesAreRadioRows` (note 44):
      - there are three `role_*` rows, one selected (Adult for a new person);
      - each is one node, "Adult, Can add and change any event";
      - in the selected row, `pixelAt(row, 4.dp, 4.dp)` is `accentSoft`;
      - a tap selects Admin.
    - `takenColoursAreBlockedAndNamed`. Alex has green and Sam blue:
      - their swatches show `block` and can't be clicked (`onPick` is not called);
      - "Green and blue are taken by Alex and Sam." sits under them;
      - the first free colour (pink) is chosen, with its tick.
    - `editingYourselfNeverListsYourOwnColour`: Sam's own sheet shows his blue unblocked and chosen, and the line names only Alex.
  - **The PIN row:**
    - `thePinHintSitsBesideSetPin`: a new Adult named Sam shows "Without a PIN, Sam can only look." to the right of `person_set_pin`, level with it.
    - With a 30-character name at font scale 1.3, the hint wraps and `person_set_pin` keeps its 48 dp. The sheet is 613 dp wide (`ShellTokens.sheetWidth`).
  - **The footer:** `saveFillsTheFooter` (note 45):
    - `person_save` "Save person" is 60 dp and spans the body;
    - for an existing person, Remove person is on its left and Save changes fills the rest;
    - `theFootDoesNotMoveWhileSaving`: with `busy`, `person_save` and `person_remove` keep their bounds.
  - **Toasts:** `aToastSitsAboveThePersonSheetsFoot`: a toast's bottom is at least `ShellTokens.toastAboveFoot` above `person_save`.
  - **Radio row** (`ControlsTest`): `aRadioRowIsOneRadioNodeAndALockedOneDoesNotSelect`: one node with `Role.RadioButton` whose `selected` follows the argument; with `locked = true` a tap doesn't call `onClick`. `RepeatsPaneTest` stays green on `HhRadioRow`.
  - **The PIN pad:** `thePinPadHidesTheSheetFromTalkBack` is a **pin**. While `choosingPin`, the sheet's nodes are gone from the tree.
  - **`PersonFormTest`:**
    - `refusalsDoNotCountAsEdits`: a refusal leaves `edited` unchanged.
    - `aDraftNeverPrintsItsPin`: `PersonDraft(…, newPin = "4821", …).toString()` doesn't contain "4821".
  - **Editor and copy:**
    - `PeopleEditorTest.eachRefusalNamesItsField`: the outcomes carry the fields above, and `remove()`'s `NEEDS_AN_ADMIN` carries `null`.
    - `SetupCopyTest.takenLineNamesColoursAndPeopleInPaletteOrder`: one, two, three and five taken; empty gives null. Sam holding green and Alex blue gives "Green and blue are taken by Sam and Alex."
    - `aNewPersonWithNoFreeColourStartsUnchosen`: with all six colours taken, no swatch is chosen and Save waits.

  Run `./gradlew :core:ui:testDebugUnitTest --tests '*ControlsTest*' :core:setup:testDebugUnitTest --tests '*PeopleUi*' --tests '*PeopleEditor*' --tests '*PersonForm*' --tests '*SetupCopy*' --tests '*SetupButtons*' :capability:calendar:testDebugUnitTest --tests '*RepeatsPane*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 10–13 and 22, and Units.
- [ ] **Step 3: Images.**
  - Predicted: `person_`, `you_` (the swatches), `add_event_repeats_dark`, `add_event_repeats_too_long_dark` (the Repeats rows take `HhRadioRow`'s sizes).
  - New: `person_name_taken_dark`, `person_name_taken_light`. This is 5b's state: "Sam" is taken, green and blue are taken, and Adult is chosen.
  - Check against 5b.
```bash
git add core/ui core/setup capability/calendar
git commit -m "Redraw the person sheet: errors under the field they're about and moved into view, roles as radio rows, taken colours blocked and named, and a full-width Save person"
```

---

### Task 5: Settings: the list panel, the page frame and titles (spec §4.12; rulings 5, 15, 21; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify:
  - `core/setup/.../SettingsScreen.kt`: `PageItem`, the panel, the page padding.
  - `SetupDimens.kt`: the settings values from Units.
  - `pages/LocationPage.kt`, `pages/PeoplePage.kt`, `pages/KioskPage.kt`: their titles come through `StepTitle`, already done in Task 2.
  - Each `Text(…, HhType.screenTitle)` and `CalendarType.reviewTitle` becomes `HhPageTitle` in:
    - `app/.../display/ui/DisplaySettingsPage.kt`;
    - `capability/door/.../ui/DoorSettingsPage.kt`;
    - `capability/lights/.../ui/LightsSettingsPage.kt`;
    - `capability/music/.../ui/MusicSettingsPage.kt`;
    - `capability/calendar/.../ui/ReviewCalendars.kt` (only its title).
- Test:
  - `SettingsScreenTest.kt`, `SettingsScreenshotTest.kt`.
  - `app/src/test/.../AppContentTest.kt`.
  - `app/src/test/.../SetupWiringTest.kt`, for the title check across every wired page.

**Interfaces:**
- Consumes: `HhPageTitle` (Task 2).

- [ ] **Step 1: The failing tests.** Each ends with `assertClickablesHaveRoleAndLabel()`.
  - `SettingsScreenTest.theListIsTheHandoffsPanel`: each `settings_page_*` is 52 dp high, 7 dp apart, and Close is 48 dp at the panel's foot.
  - `SettingsScreenTest.theChosenPageIsBoldAsWellAsTinted` keeps passing (ruling 15).
  - `SettingsScreenTest.thePageIsPaddedAsDrawn`: `settings_pane`'s left is 320 + 48 dp and its top 43 dp.
  - `SetupWiringTest.everySettingsPageTitleIsTheSharedTitle`: for each wired page (Display, Door, Lights, Music, Home location, People, Calendars, Tablet lock), its first heading node has the same text size as an `HhPageTitle` drawn beside it in the test. That compares two layouts, not a token.
  - `AppContentTest`, all **pins** (Review Focus 5; ruling 21). In each, Settings is open; the session's touch is recorded, read as `aUserActivityTouchWhileSettingsIsOpenReachesTheSession` reads it.
    - `aTapOnASettingsSwitchCountsAsATouch`: a tap on an `HhSwitchRow`.
    - `aTapOnASettingsRadioRowCountsAsATouch`: through Settings › People, a person's sheet, a role row.
    - `aDragOnASettingsSliderCountsAsATouch`.
    - `aTapInsideAPersonSheetOverSettingsCountsAsATouch`.

  Run `./gradlew :core:setup:testDebugUnitTest --tests '*SettingsScreen*' :app:testDebugUnitTest --tests '*AppContentTest*' --tests '*SetupWiringTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per Units and ruling 5.
- [ ] **Step 3: Images.**
  - Predicted prefixes: `settings_` (every module), `review_` (the title).
  - Check against 2c's panel and 5c's page header.
```bash
git add core/setup app capability
git commit -m "Style the Settings list as the redesign's panel and give every Settings page the same title"
```

---

### Task 6: Settings › Calendars (spec §4.12; rulings 3, 4, 16–18; Review Focus 2, 3)

**Review:** opus.

**Files:**
- Modify:
  - `capability/calendar/.../ui/ReviewCalendars.kt`:
    - `ConnectionHeader` takes the row look;
    - `SourceRow` becomes `CalendarRowCard`, and it gets the connection's row;
    - `ReadProblem` becomes the strip;
    - the copy changes.
  - `ReviewCalendarsHost` collects `connections` from null, as `ConnectStepHost` does, and draws nothing until they answer. This removes the "Connect…" flash on opening.
  - `ui/CalendarType.kt`: the 5c values from Units.
- Test: `ui/ReviewCalendarsTest.kt`, `ui/ReviewScreenshotTest.kt`. Build `CalendarReview` as `CalendarReviewTest` does: it takes `zone: HouseholdZone` and `clock: WallClock`.

**Interfaces:**
- Consumes: `HhSwitch` (Task 1); `Icons.SYNC_PROBLEM`, `EXPAND_MORE`.
- Produces:
  - `internal const val MAIN_NEW_EVENTS_GO_HERE = "Main · new events go here"`.
  - `internal fun cantRead(account: String?, main: Boolean): String` (ruling 17).
  - Tags kept: `review_source_{id}`, `review_person_{id}`, `review_show_{id}`, `review_show_switch_{id}`, `review_make_master_{id}`, `review_read_problem_{id}`, `review_hide_{id}`.
  - New tag: `review_main_{id}` (the badge).

- [ ] **Step 1: The failing tests.** These are in `ReviewCalendarsTest`; each ends with `assertClickablesHaveRoleAndLabel()`.
  - **Row size:**
    - `aCalendarsLineIsSixtyNineHigh` (Review Focus 2): at font 1.0, every source row's top line is 69 dp ±1, with and without the badge. The account row is at least 75 dp.
    - `aLongNameWithTheBadgeFitsAtLargeFont` (Review Focus 2): a 40-character name on the main calendar at font 1.3, on a 720 dp pane and then in a 640 dp column:
      - none of the name, badge, pill and Show overlap;
      - the name's layout has an ellipsis;
      - the badge's text isn't cut.
  - **The badge and Show:**
    - `theMainBadgeIsInline` (note 46): the main calendar's `review_main_{id}` "Main · new events go here" sits right of the name, on the same line. There is no separate "New events go here" text.
    - `theMainCalendarSaysAlwaysShown` (note 46):
      - "Always shown" shows beside its switch, and there is no "Show" there;
      - the switch is disabled-on;
      - it is spoken "Show Family, always shown: it’s the main calendar" (`ALWAYS_SHOWN`, kept: it says why), and the visible text is "Always shown".
    - `theMainCalendarsSwitchCannotBeToggled` (Review Focus 3): taps on `review_show_{id}` and TalkBack's toggle never call `onShown`.
  - **The person dropdown:** `thePersonPillOpensTheChipsUnderTheLine`:
    - a tap on `review_person_{id}` shows the chips below the 69 dp line;
    - its `stateDescription` is "expanded";
    - the pill is 48 dp tall to the touch.
  - **The read problem strip:**
    - `theStripSitsAtTheFootUnderTheChips` (Review Focus 3): with a read problem and the dropdown open, `review_read_problem_{id}` sits below the chips. Its bottom equals the card's bottom, and its left and right equal the card's (full width).
    - `theMainCalendarsProblemSaysChooseAnotherFirst` (Review Focus 3): the main calendar with a problem shows "Can’t read this calendar. Check it’s still shared with family@example.com. To hide it, choose another main calendar first.", and there is no `review_hide_{id}`.
    - `aCalendarsProblemOffersHideInsideTheStrip`: for a calendar that isn't the main one, `review_hide_{id}` is inside the strip, and a tap calls `onShown(source, false)`.
    - `aHiddenCalendarShowsNoReadProblem` (:126; the existing **pin**, Review Focus 3). No new test.
    - `aProblemWithNoAccountSaysThisAccount`: with no `CONFIG_ACCOUNT`, the text reads "…shared with this account."
    - `theReadProblemIsNotToasted`: a recording toaster receives nothing while the strip shows.
  - **Use for new events:** `useForNewEventsSitsUnderTheLine`. A writable calendar that isn't the main one has `review_make_master_{id}` under the line, 48 dp. The main calendar has none.
  - **Green buttons:** `aConnectionNeedingSignInHasOneGreenButton` (Review Focus 4): with a `NeedsSignIn` connection, only `settings_reconnect` has `ButtonToneKey = Primary`. With the wizard's own Next that is the accepted two.
  - **Before the connections answer:** `nothingShowsUntilTheConnectionsAnswer`: while `connections` is null, neither `review_none` nor any `review_source_*` row is displayed. Once it answers with an empty list, `review_none` shows.
  - **Existing tests to keep or rewrite:**
    - `aHiddenCalendarShowsNoReadProblem` (:126) is the pin above. Keep it.
    - Rewrite `aCalendarThatCantBeReadSaysSoAndOffersToHideIt` (:117), `theMasterThatCantBeReadAsksForAnotherMasterFirst` (:133) and `theMasterIsMarkedAndAlwaysShown` (:157) for `cantRead`, the strip and the inline badge.
    - Keep `showIsOneNamedSwitchAndTheMainCalendarsSaysWhyItIsOff` (:254): `ALWAYS_SHOWN`'s spoken words stay.
    - `connectionAndCalendarRowsUseTheSharedRowStyles` (:233) moves to 18 sp in Task 1.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*ReviewCalendarsTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 16–18 and Units. Show stays one toggleable node around its label and the draw-only `HhSwitch`, with `enabled = !main && !busy`.
- [ ] **Step 3: Images.**
  - Predicted: `review_`, `settings_calendars_`.
  - New: `review_strip_open_dark` (the main calendar's problem with the chips open).
  - Check against 5c.
```bash
git add capability/calendar
git commit -m "Redraw Settings › Calendars as fixed rows with the Main badge inline, a person dropdown and Show, and read problems in a strip at the card's foot"
```

---

### Task 7: The other Settings pages: slider rows, the door volume, Location's errors (spec §4.12; rulings 19, 20; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify:
  - `core/ui/.../Rows.kt`: create `HhSliderRow`.
  - `capability/door/.../ui/DoorSettingsPage.kt`: `VolumeRow` → `HhSliderRow`.
  - `app/.../display/ui/DisplaySettingsPage.kt`: `BrightnessRow` → `HhSliderRow`.
  - `capability/music/.../ui/MusicSettingsPage.kt`: `LowerToRow` → `HhSliderRow`.
  - `core/setup/.../LocationPane.kt`: the status line moves under the field.
  - Each module's dimens: the unused slider widths go.
- Test:
  - `core/ui` `RowLookTest.kt`.
  - `capability/door/src/test/.../ui/DoorSettingsPageTest.kt`: `theVolumeIsSavedWhenTheSliderIsLetGo` (:122–128). `playChimePlaysAtTheChosenVolume` (:114–120) stays.
  - `app/src/test/.../display/ui/DisplaySettingsPageTest.kt`.
  - `capability/music/src/test/.../ui/MusicSettingsPageTest.kt`.
  - `core/setup/.../LocationPaneTest.kt`.
  - Their screenshot tests.

**Interfaces:**
- Consumes: `HhPercentSlider`.
- Produces: `@Composable fun HhSliderRow(title: String, percent: Int, tag: String, onLetGo: (Int) -> Unit, modifier: Modifier = Modifier, range: IntRange = 0..100, steps: Int = 0, enabled: Boolean = true, spokenState: (Int) -> String = { "$it percent" }, valueLabel: (Int) -> String = { "$it %" })`.
  - It uses the row look.
  - `spokenName` is the title.
  - Music passes `lowerToWords` for both `spokenState` and `valueLabel`.
  - It holds a let-go value for `SETTINGS_HOLD_MS = 2_000`.

- [ ] **Step 1: The failing tests.** Each ends with `assertClickablesHaveRoleAndLabel()`.
  - **`RowLookTest`:**
    - `aSliderRowSaysItsValueAndLetsGo`: `HhSliderRow("Chime volume", 30, "x", steps = 9)` is spoken "Chime volume, 30 percent" and shows "30 %" on the right. `SetProgress(50f)` calls `onLetGo(50)`.
    - `aSliderRowCanBeDisabled`: with `enabled = false`, there is no `SetProgress` action and touch does nothing.
  - **`DoorSettingsPageTest`:**
    - `theVolumeStepsInTensAndSavesOnLetGo` (Review Focus 5) replaces `theVolumeIsSavedWhenTheSliderIsLetGo` (:122–128), whose `SetProgress(0.3f)` is at :125:
      - `door_volume` is spoken "Chime volume, 30 percent" at 0.3;
      - `SetProgress(30f)` calls `onVolume(0.3f)` once, on let go;
      - the row is the 69 dp row.
    - `aFailedSaveReturnsTheSliderToTheStoredVolume` (Review Focus 5): the store refuses the save; at `SETTINGS_HOLD_MS + 16` the slider shows the stored volume.
  - **Display:** `DisplaySettingsPageTest.theBrightnessRowsAreSliderRows`. Each brightness row is 69 dp, with its value "40 %" on the right; `onLetGo` works as today.
  - **Music:** `MusicSettingsPageTest.lowerMusicToIsASliderRow`. `music_lower_to` keeps its 5 % steps and its words.
  - **Location:**
    - `LocationPaneTest.aFailedSearchSaysSoUnderTheField`:
      - with `TownResults.Failed`, `location_status` shows directly under `location_query` (above any row), in `danger`, and the Try again button stays;
      - with `Searching`, the `mute` line shows in the same place, the same node;
      - after a failed save, "Couldn’t save the home location — try again." shows there.
    - `LocationPaneTest.theFieldHasNoRingForASearchFailure` (**pin** of ruling 20): the field has no `danger` edge.

  Run `./gradlew :core:ui:testDebugUnitTest --tests '*RowLook*' :capability:door:testDebugUnitTest --tests '*DoorSettingsPage*' :capability:music:testDebugUnitTest --tests '*MusicSettingsPage*' :core:setup:testDebugUnitTest --tests '*LocationPane*' :app:testDebugUnitTest --tests '*DisplaySettingsPage*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 19–20.
- [ ] **Step 3: Images.**
  - Predicted: `settings_door_`, `settings_display_`, `settings_music_`, `settings_location_`, `location_`.
  - Check against 5c's row pattern and note 43.
```bash
git add core/ui capability/door capability/music app core/setup
git commit -m "Put Settings' sliders in rows, step the chime volume in tens, and show Home location's errors under its field"
```

---

### Task 8: The emulator walk-through on `culvery-5`

**Review:** the controller, with the user at the emulator. (The workflow runs `simplify` over the branch first, then the gate.)

**Rules.** As in Plan 9a's Task 15:
- Use only the `culvery-5` AVD, started with `-no-snapshot-save -camera-front emulated` (check `adb -s "$S" emu avd name`).
- It holds a debug build, so `install -r` the debug APK.
- Never uninstall, and never run a Gradle install task while a device is connected.
- The user types every PIN.
- Put back every setting changed.
- Walking the wizard needs an empty household. Ask the user first; they clear the AVD's app data themselves. Otherwise, check the wizard through its screenshots and Settings › People's sheet.

- [ ] **Step 1: The AVD and the build.** Run `./gradlew :app:assembleDebug`, then `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2: Walk the checklist** (one line each).
  1. The wizard:
     - the dots read done, here and to go;
     - Back and Next sit at the column's foot, Next with its arrow;
     - each step has at most one green button.
  2. Who else lives here:
     - the rows, with Add person quiet;
     - add "Sam" twice: the error shows under the name with the suggestion, and focus lands there;
     - taken colours are blocked, with the line;
     - roles are radio rows;
     - Save person spans the foot;
     - removing the last Admin says why above the footer.
  3. Settings:
     - the list panel;
     - every page's title is the same size;
     - switches are the new size;
     - move Sam below Mia in Settings › People: a Sam + Mia event reads "Mia + Sam" and its stripes swap (put it back).
  4. Settings › Calendars:
     - the fixed rows, with the Main badge inline;
     - the person dropdown;
     - "Always shown", and that switch can't be moved;
     - a calendar unshared in Google (or the fake's read problem) shows the strip, with Hide inside it;
     - hide a calendar, then check New event's From before no longer offers its titles; Show brings them back;
     - "Use for new events": New event's foot then names the new calendar.
  5. Other Settings pages:
     - Settings › Door: the chime volume steps in tens;
     - Display and Music: the sliders sit in rows;
     - Home location: a failed search (Wi-Fi off) says so under the field.
  6. Leave Settings untouched:
     - the closing ring shows and Settings closes at the usual time;
     - a tap on a switch restarts it.
- [ ] **Step 3: The fault loop.** For each fault: a failing test, the fix, the gate, a commit naming it, `install -r`, then a re-check.
- [ ] **Step 4: Put back** every setting and the household, then run `adb -s "$S" emu kill`.
- [ ] **Step 5: Record** the walk-through under "Plan 9c-2 (Redesign: the wizard and Settings): emulator walk-through".
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9c-2's emulator walk-through"
```

---

### Task 9: On the tablet

**Review:** the controller with the user. **Needs the SM-T510 on the cable.**

4e's tablet rules apply:
- save and restore every setting changed;
- install signed releases with `adb install -r`;
- never uninstall;
- run the names check before any commit.

The wizard is not re-run on the household's tablet; Settings › People's sheet stands in for the person sheet.

- [ ] **Step 1: Install the release.** Sign it per `docs/setup/release.md`, and keep `mapping.txt` in `culvery-notes/mappings/`.
- [ ] **Step 2: At the wall** (the user), check:
  - Settings, at arm's length;
  - the Calendars rows, with the household's calendars;
  - a person's sheet, opened and closed without saving;
  - the switch size.
- [ ] **Step 3: Record** under "Plan 9c-2 (Redesign: the wizard and Settings): on the tablet": what read and what didn't. No names.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9c-2 on the tablet"
```

---

### Task 10: Final review, merge, install

- [ ] **Step 1: The review.** Opus reviews the whole branch against spec §4.11, §4.12 and §6.4, and against this plan. Then one fix wave, each fix with its failing test first and the gate.
- [ ] **Step 2: Merge**, on the user's say:
  1. Run `git merge --ff-only plan-9c2-wizard-settings` on `main`.
  2. Run the gate on main.
  3. Never push.
  4. Plan 9 is then complete. Move both plans' "Notes for the end-of-v1 design review" to the follow-ups.
- [ ] **Step 3: Install** the release from the merged `main` on the tablet with `install -r`.

---

## Spec coverage (9 design → tasks)

| Spec | Task |
|---|---|
| §4.11 step dots (note 40) | 2 |
| §4.11 one green button; Add person quiet (note 41) | 2, 3 |
| §4.11 Back and Next in the column, 92 px, Next's arrow (note 42) | 2 |
| §4.11 errors under their field with a suggestion (note 43) | 3, 4 |
| §4.11 role radio rows (note 44) | 4 |
| §4.11 the full-width Save footer; taken swatches (note 45) | 4 |
| §4.11 the other wizard steps take the patterns | 2 (titles, buttons, one green), 3 (You), 6 (Your calendars), 7 (Where's home) |
| §4.12 Calendars: fixed rows, inline Main, person dropdown, Show, Always shown, the strip (note 46) | 1, 6 |
| §4.12 the other pages take the 5c rows and errors under fields | 1, 5, 7 |
| §4.12 the Settings list's styling (2c) | 5 |
| §6.4 one disabled style | 1 (the switch), every task |
| §7 images looked at; CopyScanTest; TalkBack | every UI task |
