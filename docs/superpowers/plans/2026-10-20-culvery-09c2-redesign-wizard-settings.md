# Culvery — Plan 9c-2: The redesign, the setup wizard and the Settings pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–8 are subagent tasks; Task 9 is the controller on the `culvery-5` AVD with the user; Tasks 10–11 need the user and the son's SM-T510.
>
> **9c is split in two** (it came to 23 tasks). **9c-1** — `docs/superpowers/plans/2026-10-20-culvery-09c-redesign-forms.md` — is the shared sheet and form pieces and Add event; it merges first. This plan, **9c-2**, is the setup wizard and the Settings pages, on a branch cut from `main` after 9c-1's merge.

**Goal:** The setup wizard and the Settings pages look as the October redesign draws them — step dots that show done, here and to go; one green button per screen with "Add person" quiet; Back and Next as 61 dp buttons inside the content column, Next with an arrow; errors under their field with a suggestion; roles as radio rows; colour swatches with taken ones blocked and a line saying who has them; the full-width "Save person"; Settings' list styled as the handoff's panel; Settings › Calendars as fixed rows with the inline Main badge, a person dropdown and Show ("Always shown" on the main calendar), read problems as a strip at the card's foot; and the other Settings pages on the same row pattern.

**Architecture:** Shared pieces first in `:core:ui`: the list row and switch row take the handoff's row look (one `HhListRow`, so the wizard, Settings and the sheets that list rows move together), the switch becomes the handoff's 59 × 33 dp switch, a page title, a slider row, a radio row, the swatch with its block icon, and the text field's error drawn under it. `:core:setup` then redraws the wizard frame, the people list, the person sheet and Settings' frame; `:capability:calendar` redraws Review calendars (Settings › Calendars and the wizard's Your calendars step); the other pages (`:app` Display, `:capability:door`, `:capability:lights`, `:capability:music`, `:core:setup` Location, People, Tablet lock) take the rows, the title and errors under fields.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00, Material 3), Hilt 2.57.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-19-culvery-9-redesign-design.md` — §4.11 (wizard), §4.12 (Settings pages), §4.13 (Settings' countdown ring, already built in 9a), §6.4, §7. The handoff `docs/design/2026-10-redesign/handoff.md` (sections "Setup wizard (5a/5b)", "Settings › Calendars (5c)", "Settings › Home buttons (2c)" for the list panel only, and "Not redesigned") and `notes.txt` (notes 40–46) are the drawings; the full HTML export is in `C:\Users\si\code\siland\culvery-notes\design-handoff-2026-10\`. Where the spec and the handoff disagree, the spec wins. Read all three with this plan.
**Previous plans (format, shared parts, rulings):** 9c-1 (its shared pieces), `docs/superpowers/plans/2026-10-20-culvery-09b-redesign-tabs.md`, `docs/superpowers/plans/2026-10-19-culvery-09a-redesign-home.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md`.

**Builds on:** branch `plan-9c2-wizard-settings` in the one checkout `C:\Users\si\code\siland\culvery` (no worktrees; cut from `main` once 9c-1 is merged). Work only there. Never push.

| # | Task | Review |
|---|---|---|
| 1 | Shared: the row look, the switch, the page title, the slider row | sonnet |
| 2 | Shared: the radio row, the swatch, the field's error under it | sonnet |
| 3 | Wizard: the frame, the dots, Back and Next, one green button | sonnet |
| 4 | Wizard: the people list and the You step | sonnet |
| 5 | The person sheet | **opus** (refusals routed to their field; TalkBack hearing each once) |
| 6 | Settings: the list panel, the page frame and titles | sonnet |
| 7 | Settings › Calendars | **opus** (a fixed row at large font; the strip; one switch for TalkBack) |
| 8 | The other Settings pages: slider rows, the door volume, Location's errors | sonnet |
| 9 | The emulator walk-through on `culvery-5` | controller + user |
| 10 | On the tablet | controller + user, **needs the SM-T510** |
| 11 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:** the two shared tasks first, alone, because they move almost every wizard and Settings image (and Favourites, Lights' room card, the location results) and those moves should be deliberate. The wizard frame (3) before its steps (4, 5). Settings' frame (6) before the pages (7, 8).

## Rulings against the code

Where the spec leaves a choice open or doesn't fit the code after 9c-1. Each is the plan's call unless it cites the spec or the user.

1. **Units, touch targets, the disabled look** as 9c-1 rulings 1–2 and 9a: dp = px ÷ 1.5; 48 dp touch targets (a 37 dp pill sits in a 48 dp tap height); one disabled style.
2. **One row look** (spec §4.12: "take the 5c row pattern"): `HhListRow` and `HhSwitchRow` take the handoff's row — radius 19, the light theme's 1 dp `border` edge (`CardTokens.edge`, as cards), padding 0 16 0 20, at least 69 dp high, content centred, title 18 sp W600, subtitle 14 sp. Every list row in the app moves with it (people, towns, connections, Door's steps, Favourites, Display's and Door's switch rows): one row, as 4d-1 made it. `PersonRow` passes `minHeight = 75.dp` (5a's 112 px).
3. **The switch** (the user's brief; handoff 5c): `HhSwitch` becomes the handoff's 88 × 50 px switch, drawn here (59 × 33 dp, knob 25, inset 4; on `accent` with an `accentInk` knob, off `surf3` with a `mute` knob; disabled-on the faded `accent` at 38 %), in place of Material's 52 × 32 one. It stays one switch app-wide (9b ruling 5's point), so the Lights room card's switch moves to it too; the handoff's 96 × 54 px Lights switch is left for the end-of-v1 review. With `onCheckedChange` null it only draws (rows are the switch for TalkBack), as today.
4. **The main calendar's switch** reads "Always shown" beside it in place of "Show" (note 46) and is drawn disabled-on at the app's 38 %, not the handoff's 45 %, so the app keeps one disabled alpha.
5. **One page title** for wizard steps and Settings pages: `HhPageTitle(title, line)`, 37 sp W700 (Settings' 56 px), its line 16 sp `mute` 8 below. The wizard's 60 px (40 sp) is drawn at 37 too: the Connect and Your calendars steps are the same composables as Settings › Calendars, and a step shouldn't change size between the two. Every page that draws its own title (`StepTitle`, `ReviewCalendars`, Display, Door, Lights, Music) uses it.
6. **The wizard column is 640 dp** (960 px); **Settings' pane keeps 720 dp** as its own `SetupDimens.settingsPane` (today both read `wizardColumn`): the 5c rows fill a wide pane, and narrowing Settings to 640 would cut every page's rows for no drawn reason. Flagged to the user.
7. **The step dots** (note 40): 11 dp dots 9 apart; done steps `accent`, the current one a 29 × 11 `accent` pill, the rest `surf3` — the pill's width now tells the current step without colour, which the 1.5 dp rings did. Spoken "Step 3 of 7" as today.
8. **Back and Next** (note 42) are `ButtonSize.Footer` (61 dp) at the foot of the 640 dp column, Back left (quiet), Next right (primary, `arrow_forward` after the label); Skip for now is the same size, quiet. **One green button per screen** (note 41): "Add person" quiet with `person_add`; the Done step's "Choose home app" quiet (Open Culvery is the green one) — `HomeAppPrompt(tone)`, primary on Settings › Tablet lock where nothing else is. Review calendars' Reconnect stays primary even beside Next: it is the fix, and shows only while a connection needs signing in again. The Connect step's card keeps its primary Connect, because Skip for now is quiet there.
9. **Errors under their field** (note 43): `HhTextField(error)` draws a 2 dp `danger` ring (3 px, in place of the focus ring while there is an error) and, 8 under it, `HhFieldError(error, "${tag}_error", announce = false)` — the field already carries the error for TalkBack, so it is said once. The name taken reads "`Someone is already called {name}. Try “{name} B” or a nickname.`" (handoff; `someoneCalled`), in the person sheet and the You step. The person sheet's other refusals go under the section they are about: "That colour is taken — choose another." under Colour, "That PIN is taken — choose another." and "An Admin needs a PIN." under PIN, "Culvery needs at least one Admin with a PIN." under Role, each an announced `HhFieldError`; only "Couldn’t save …" (no field) stays above the footer. `PeopleOutcome.Refused` carries `field: PersonField?` in place of `nameTaken`.
10. **Role radio rows** (note 44): `HhRadioRow(title, line, selected, …)` per role, in a `selectableGroup`; titles `roleName` ("Admin"), lines "Can change settings and people", "Can add and change any event", "Can add their own events" (`roleWhat`; `roleLine` goes if unused).
11. **Swatches** (note 45): 51 dp, 12 apart; a taken one at the app's 38 % (not the handoff's 30 %, one disabled alpha) with the `block` icon in `PersonChipInk` in place of the strike; the chosen one a 3 dp `bg` gap, a 3 dp `ink` ring and the `check` icon. Under them, when anyone else has a colour: "`{Colours} are taken by {names}.`" in palette order — "Green is taken by Alex.", "Green and blue are taken by Alex and Sam.", "Green, blue and pink are taken by Alex, Sam and Mia." (`takenLine`), 13 sp `mute`. The You step has none taken, so no line.
12. **The PIN row** in the person sheet: "Set PIN" (quiet, 48) with "Without a PIN, {name} can only look." beside it, 13 dp apart (5b), in place of under it. The handoff's `pin` icon isn't added.
13. **The people list's lines** take the handoff's words (5a): "Admin · PIN set", "Adult · no PIN yet, so Sam can only look", "Child · PIN set"; an Admin without a PIN keeps "Admin · No PIN". The Household step's line is the handoff's "Everyone gets a colour on the calendar. A PIN lets them add events." Flagged (not in the brief).
14. **The Settings list** (2c's panel): items 52 dp, 7 apart, padding 0 19; the panel's padding 35 top, 28 sides and foot; the title 35 sp, 25 above the list. The chosen item keeps its 700 weight as well as the tint (4d-1 §5.2), which the handoff doesn't draw.
15. **A Settings › Calendars row** (note 46) is its own layout, no longer `HhListRow`: a card (radius 19, `surf`, `border` edge) whose top line is 69 dp — the name (18 sp W600, one line, "…"), on the main calendar the inline badge "Main · new events go here" (24 high, `accentSoft`/`accent`, 12 sp W700), the person dropdown pill (37 drawn in a 48 dp tap, `surf2`, dot, name, `expand_more`) and Show. Under the line, only when there: the person chips while the dropdown is open, "Use for new events" (quiet, 48, non-main writable calendars), and at the very foot the read problem strip (full width, `dangerSoft`, `sync_problem`, the message in `ink`, and "Hide this calendar" on `surf` inside it for a non-main calendar). At 1.0 font the line is exactly 69 dp; at larger fonts it grows rather than clipping (Review Focus 2).
16. **The read problem's words** take the handoff's: "`Can’t read this calendar. Check it’s still shared with {account}.`" ({account} the connection's `CONFIG_ACCOUNT`, else "this account"), and on the main calendar " To hide it, choose another main calendar first." added — `cantRead(account, main)` replaces `CANT_READ_CALENDAR` and `CHOOSE_ANOTHER_MAIN`. The account is already shown in the connection row above. Flagged (not in the brief).
17. **"Main · new events go here"** is one badge (`MAIN_NEW_EVENTS_GO_HERE`), replacing the `Main` badge and the separate "New events go here" line.
18. **Sliders on Settings pages** are `HhSliderRow` (the row look; title left, `HhPercentSlider` in the middle, the value right): Display's brightness rows, Music's "Lower music to" and Door's chime volume. Door's volume moves from Material's `Slider` (Float) to it: percent `Int` 0..100, `steps = 9` (10 % steps, as `VOLUME_STEPS`), saved on let go as `percent / 100f`, tag `door_volume`, spoken "Chime volume, 30 percent".
19. **Location's errors under the field:** the status line moves directly under the town field (above the results); its two errors ("Couldn’t search for towns — …", "Couldn’t save the home location — try again.") are `HhFieldError(announce = true)`; "Searching…" and "No towns match …" stay a `mute` `HhMessageLine`. The field gets no ring: neither error is the text's fault.
20. **Touches before `countsTouches`:** nothing in Settings consumes a touch before the shell's counting box sees it (it listens on the `Initial` pass). Task 6 pins it with a tap on a Settings row's switch.

## Units (converted once, px ÷ 1.5)

| Where | Handoff px | dp / sp |
|---|---|---|
| Rows (5c) | r 28, padding 0 24 0 30, 1 px `border`; account 112; calendar line 104; name 27/600; sub 21 | r 19, padding 0 16 0 20, 1 `border`; min 69 (people 75); 18 sp W600; 14 sp |
| Switch | 88 × 50, knob 38, inset 6 | 59 × 33, knob 25, inset 4 |
| Page title | Settings 56/700, wizard 60/700; line 24 muted | 37 sp W700 (ruling 5); 16 sp `mute`, 8 below |
| Wizard | column 960; dots 16, pill 44 × 16, gap 14; padding top 56; dots to step 64 | 640; 11, 29 × 11, 9; 37; 43 |
| Back · Next | 92, Next arrow 34 | `ButtonSize.Footer` 61, icon 24 |
| Person rows | 112, r 28, dot 22, gap 22, name 28/600, sub 21, › 36; 18 apart | 75, r 19, dot 15, gap 15, 19 sp W600, 14 sp, 24; 12 apart |
| Add person | 72 quiet, `person_add` 32 | 48 quiet, icon 24 |
| Field error | ring 3 px inset; message 21/600 + `error` 26 | ring 2; `HhFieldError` (9c-1) |
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

**Copy** (verbatim, curly quotes and apostrophes in code): "Back", "Next", "Skip for now", "Add person", "Save person", "Save changes", "Remove person", "Set PIN", "`Without a PIN, {name} can only look.`", "`Someone is already called {name}. Try “{name} B” or a nickname.`", "`{Colours} are taken by {names}.`" (e.g. "Green and blue are taken by Alex and Sam."), "Admin", "Adult", "Child", "Can change settings and people", "Can add and change any event", "Can add their own events", "`{role} · PIN set`", "`{role} · no PIN yet, so {name} can only look`", "Everyone gets a colour on the calendar. A PIN lets them add events.", "Settings", "Close", "Calendars", "Main · new events go here", "Show", "Always shown", "Use for new events", "Hide this calendar", "`Can’t read this calendar. Check it’s still shared with {account}.`", "To hide it, choose another main calendar first.", "Disconnect", "Chime volume".

**Privacy:** the read problem names the connected account, which the connection row already shows; nothing new is stored or logged; names in copy are the household's own.

**From the house rules** (as Plans 9a and 9b; binding):

- Package root `uk.co.siland.culvery`. `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only. **No new library; never change a version.** No deprecated API; the one opt-in is `ExperimentalMaterial3Api` in `HhPercentSlider`.
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug`. Never plain `test`, `check` or `build`; never a Gradle `install…` task; no subagent touches a device. **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`** for Gradle: a long temp path pushes Robolectric's database paths past Windows' MAX_PATH (SQLITE_CANTOPEN). `$TMP` below means `C:/t9` (lists only).
- **Tests that can fail:** every new test fails before its change; a test that passes before it is labelled **pin**. Timer tests on virtual time; UI timers under `compose.mainClock` one frame before the boundary and at the boundary plus one frame.
- **Every UI task ends the same way:** `compareRoborazziDebug`; open each `*_compare.png`, and each new image against the handoff section the task names; record; the baseline check (predicted names or prefixes in `$TMP/expected-baselines.txt` / `$TMP/expected-prefixes.txt`, checked as in Plan 9a's Global Constraints — anything outside the prediction stops the task); the gate; the commit. Baselines live in `<module>/src/test/screenshots/`. Each task lists only its predictions and its commit line.
- **Copy** names what the user does; curly quotes; "·", "›", "→", "…" as written; `CopyScanTest` passes. **Accessibility:** every changed screen's test ends with `compose.assertClickablesHaveRoleAndLabel()`; TalkBack names follow the copy.
- **Icons** from `Icons`; a new one in `tools/fonts/icons.txt` and the subset rebuilt from the full font at 608ce00. **Layout numbers are named** in the module's dimens object. **Comments say what the code can't**, one line, spec refs as `(9 §4.12)`; no plan task or ruling numbers. **No personal data** — sample names only (Alex, Sam, Mia, Lounge, Kitchen, Hall, Garden, Front Door, Back Garden). **UTF-8.** **Commits** a plain sentence, no trailer. Never push. **Never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **A name taken, then corrected; a refusal for another field** — "Sam" saved while Sam exists, then a letter typed; the colour taken meanwhile on another tablet; the last Admin's PIN removed. Expected: the name's ring and line show, TalkBack says the error once (on the field, not again from a live line); typing clears both; the colour refusal shows under Colour, the PIN one under PIN; nothing waits above the footer except "Couldn’t save …".
   - Task 2 `TextFieldTest.anErrorRingsTheFieldAndSitsUnderIt`, `theErrorIsSaidOnce`; Task 4 `YouFormTest.aTakenNameIsAnErrorOnTheNamedFieldAndSaidOnce` (extended); Task 5 `PeopleUiTest.aTakenNameShowsUnderTheNameAndTypingClearsIt`, `aTakenColourShowsUnderColour`, `aPinRefusalShowsUnderPin`.
2. **Settings › Calendars at large font with long names** — a 40-character calendar name on the main calendar (badge inline) with a person "Family", at font scale 1.0 and 1.3, on the 720 dp pane. Expected: at 1.0 the line is 69 dp ±1; at 1.3 it grows, nothing overlaps, the name ends in "…", the badge, pill and Show stay whole.
   - Task 7 `ReviewCalendarsTest.aCalendarsLineIsSixtyNineHigh`, `aLongNameWithTheBadgeFitsAtLargeFont`.
3. **The main calendar can't be read while its people are being picked** — the main calendar with a read problem, the dropdown open; a hidden calendar with a read problem. Expected: the chips under the line, the strip at the very foot with no Hide button and "To hide it, choose another main calendar first."; the hidden one shows no strip.
   - Task 7 `ReviewCalendarsTest.theStripSitsAtTheFootUnderTheChips`, `theMainCalendarsProblemSaysChooseAnotherFirst`, `aHiddenCalendarShowsNoStrip` (**pin** of today's rule).
4. **The wizard with the keyboard up and at the steps with no green button** — the You step's name field focused (a 320 dp keyboard) at font 1.3; the Household step with only you (Skip for now quiet, Add person quiet); the Done step. Expected: Back and Next stay above the keyboard inside the 640 dp column; at most one primary button on each step; Done's only primary is Open Culvery.
   - Task 3 `SetupButtonsTest.theWizardsButtonsStayAboveTheKeyboard` (extended to 1.3), `backAndNextSitInsideTheColumn`; `StepsUiTest.eachStepHasAtMostOneGreenButton`.
5. **Settings' idle close and the door volume's steps** — a tap on a Settings switch while Settings is open; the door volume dragged to 33 % and let go. Expected: the tap counts as a touch (the session's idle wait restarts); the volume lands on 30 % and saves 0.3.
   - Task 6 `AppContentTest.aTapOnASettingsSwitchCountsAsATouch` (**pin**); Task 8 `DoorSettingsPageTest.theVolumeStepsInTensAndSavesOnLetGo`.

The spec's own tests (§7): CopyScanTest and TalkBack names (every task); images looked at against 5a, 5b, 5c and 2c (Tasks 1–8).

---

## File Structure

```
core/ui/.../Components.kt (HhListRow, ListRowTokens, HhPageTitle), Rows.kt (HhSwitchRow, HhSliderRow), Controls.kt (HhSwitch) (Task 1)
core/ui/.../Controls.kt (HhRadioRow, HhSwatch, HhSwatchGrid, HhTextField's error) (Task 2)
core/setup/.../SetupWizard.kt, SetupDimens.kt, SetupUi.kt (StepTitle, HomeAppPrompt), steps/DoneStep.kt (Task 3)
core/setup/.../PeopleUi.kt, SetupCopy.kt, steps/HouseholdStep.kt, steps/YouStep.kt (Task 4)
core/setup/.../PersonEditorSheet.kt, PeopleEditor.kt (PersonField), SetupCopy.kt (Task 5)
core/setup/.../SettingsScreen.kt, pages/*.kt; app/.../display/ui/DisplaySettingsPage.kt; capability/{door,lights,music}/.../ui/*SettingsPage.kt (titles) (Task 6)
capability/calendar/.../ui/ReviewCalendars.kt, ui/CalendarType.kt (Task 7)
capability/door/.../ui/DoorSettingsPage.kt; app/.../DisplaySettingsPage.kt; capability/music/.../MusicSettingsPage.kt; core/setup/.../LocationPane.kt (Task 8)
docs/superpowers/plans/2026-09-23-plan1-followups.md (Tasks 9–11)
```

---

### Task 1: Shared: the row look, the switch, the page title, the slider row (rulings 2, 3, 5, 18)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Components.kt` (`ListRowTokens`, `HhListRow(minHeight)`; create `HhPageTitle` there), `core/ui/.../Rows.kt` (`HhSwitchRow`; create `HhSliderRow`), `core/ui/.../Controls.kt` (`HhSwitch`, `SwitchTokens`)
- Test: create `core/ui/src/test/.../RowLookTest.kt`, `SwitchTest.kt`; `RowStylesTest` in `core/setup` (**pin**, still passes); a new `rows_dark`/`rows_light` screenshot in core/ui

**Interfaces:**
- Produces:
  - `ListRowTokens`: `radius = 19.dp`, `paddingStart = 20.dp`, `paddingEnd = 16.dp`, `paddingV = 12.dp`, `minHeight = 69.dp`, `titleStyle = HhType.rowTitle.copy(fontSize = 18.sp)`; `HhListRow(…, minHeight: Dp = ListRowTokens.minHeight)` with the `border` edge.
  - `HhSwitchRow` in the same container.
  - `HhSwitch(checked, onCheckedChange, tag, enabled)` drawn per ruling 3 (`SwitchTokens.width = 59.dp`, `height = 33.dp`, `knob = 25.dp`, `inset = 4.dp`; the knob slides with `animateDpAsState`); with a non-null `onCheckedChange` it is `toggleable(role = Role.Switch)`.
  - `@Composable fun HhPageTitle(title: String, line: String? = null, modifier: Modifier = Modifier)` (37 sp W700 heading; line 16 sp `mute`, 8 below).
  - `@Composable fun HhSliderRow(title: String, percent: Int, tag: String, onLetGo: (Int) -> Unit, modifier: Modifier = Modifier, range: IntRange = 0..100, steps: Int = 0, spokenName: String = title, spokenState: (Int) -> String = { "$it percent" }, valueLabel: (Int) -> String = { "$it %" })` — the row container, the title at its natural width, the slider `weight(1f)`, the value right-aligned.

- [ ] **Step 1: The failing tests** (each ends with `assertClickablesHaveRoleAndLabel()`)
- `aListRowIsAtLeastSixtyNineHigh`: a one-line `HhListRow` → 69 dp ±1; the title 18 sp; a `minHeight = 75.dp` row → 75.
- `aListRowHasTheCardEdgeInLight`: light theme → the edge pixel `border` (0xFFD5DCD8); dark → none drawn.
- `aSwitchRowIsTheSameRow` (**pin**-like, fails on height): `HhSwitchRow` one line → 69 dp; still one `Role.Switch` node.
- `SwitchTest.theSwitchIsFiftyNineByThirtyThree`: 59 × 33 dp; on → the knob's centre pixel `accentInk` right of centre, track `accent`; off → `mute` knob left, `surf3` track; disabled on → track `accent` at 38 % over the background.
- `SwitchTest.aSwitchThatOnlyDrawsHasNoAction`: `onCheckedChange = null` → no clickable node.
- `aPageTitleIsAHeadingWithItsLine`: "Calendars" heading at 37 sp; the line 8 dp below in `mute`.
- `aSliderRowSaysItsValueAndLetsGo`: `HhSliderRow("Chime volume", 30, "x", steps = 9)` → spoken "Chime volume, 30 percent"; "30 %" shown right; `SetProgress(50f)` → `onLetGo(50)`.
Run: `./gradlew :core:ui:testDebugUnitTest --tests '*RowLook*' --tests '*SwitchTest*' --tests '*PageTitle*'` — Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces and Units.
- [ ] **Step 3: Images** — predicted prefixes (every list row, switch row or switch): `people_list_`, `settings_`, `review_`, `location_`, `you_`, `done_`, `connected_step_`, `favourites_sheet_`, `lights_tab_`, `lights_grid_`, `lights_room_`, `tab_lights_`, `home_full_`, `home_lights_` (if Home's card shows a room switch — refine); new `rows_dark`, `rows_light`. Refine from the compare report before re-recording; stop on anything outside it. Against 5c's rows.
```bash
git add core/ui core/setup capability app
git commit -m "Give list and switch rows the redesign's row, make the switch the redesign's size, and add a page title and a slider row"
```

---

### Task 2: Shared: the radio row, the swatch, the field's error under it (rulings 9–11)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Controls.kt` (create `HhRadioRow`, `RadioTokens`; `HhSwatch`, `HhSwatchGrid`, `ControlTokens.swatch*`; `HhTextField`'s error ring and line)
- Test: create `core/ui/src/test/.../RadioRowTest.kt`; `TextFieldTest.kt` (or where `HhTextField` is tested; create if none); the swatch's test; `form_pieces_` screenshot (9c-1)

**Interfaces:**
- Consumes: `HhFieldError` (9c-1 Task 3); `Icons.BLOCK` (9c-1 Task 1).
- Produces: `@Composable fun HhRadioRow(title: String, line: String, selected: Boolean, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier)` (`Modifier.selectable(role = Role.RadioButton)`, one node "Adult, Can add and change any event"); `HhSwatch` and `HhSwatchGrid` keep their signatures (51 dp, block, check); `HhTextField(error)` draws the ring and the line `${tag}_error` under the field (ruling 9), and its `modifier` still applies to the field.

- [ ] **Step 1: The failing tests** (each ends with `assertClickablesHaveRoleAndLabel()`)
- `RadioRowTest.aSelectedRowIsSoftGreenWithARing`: selected → the centre `accentSoft`, the edge pixel `accent`, `assertIsSelected`; unselected → `surf`, no ring; min height 59 dp; the title 17 sp W700, the line 13 sp `mute`.
- `RadioRowTest.aRowIsOneSpokenRadio`: `Role.RadioButton`, text "Adult" and "Can add and change any event" merged into one node.
- `aTakenSwatchIsBlocked` (replaces the strike's test): taken by Alex → the `block` icon shown, stateDescription "used by Alex", not selectable; 51 dp.
- `theChosenSwatchHasARingAndATick`: chosen → `check` shown, the pixel 4 dp outside the colour is `ink`.
- `TextFieldTest.anErrorRingsTheFieldAndSitsUnderIt` (Review Focus 1): `error = "Someone is already called Sam. Try “Sam B” or a nickname."` → the field's edge pixel `danger`, `x_error` displayed 8 dp under the field; null → no `x_error`, no ring.
- `TextFieldTest.theErrorIsSaidOnce` (Review Focus 1): the field's semantics carry the error; `x_error` is hidden from accessibility and not a live region.
Run: `./gradlew :core:ui:testDebugUnitTest --tests '*RadioRow*' --tests '*TextField*' --tests '*Swatch*'` — Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces and Units.
- [ ] **Step 3: Images** — predicted prefixes `person_`, `you_`, `form_pieces_`; new `radio_rows_dark`, `radio_rows_light`. Against 5b.
```bash
git add core/ui core/setup
git commit -m "Add radio rows, block taken colours instead of striking them, and draw a field's error as a red ring with the message under it"
```

---

### Task 3: Wizard: the frame, the dots, Back and Next, one green button (spec §4.11; rulings 5–8; Review Focus 4)

**Review:** sonnet.

**Files:**
- Modify: `core/setup/.../SetupWizard.kt` (`WizardFrame`, `Dots`), `SetupDimens.kt` (`wizardColumn = 640.dp`, `settingsPane = 720.dp`, the dots, `wizardPaddingTop`, `dotsBottom`), `SetupUi.kt` (`StepTitle` → `HhPageTitle`; `HomeAppPrompt(onChoose, tone)`), `steps/DoneStep.kt` (quiet), `pages/KioskPage.kt` (primary), `SettingsScreen.kt` (reads `settingsPane`)
- Test: `WizardDotsTest.kt`, `SetupButtonsTest.kt` (`wizard_back`, `wizard_next`, `wizard_skip` 61 dp), `StepsUiTest.kt`, `SetupScreenshotTest.kt`

**Interfaces:**
- Consumes: `ButtonSize.Footer` (9c-1 Task 3); `HhPageTitle` (Task 1); `Icons.ARROW_FORWARD`.
- Produces: `SetupDimens.settingsPane`; `HomeAppPrompt(onChoose: () -> Unit, tone: ButtonTone)`.

- [ ] **Step 1: The failing tests** (each ends with `assertClickablesHaveRoleAndLabel()`)
- `WizardDotsTest.doneHereAndToGo` (replaces `theCurrentStepIsAFilledDotAndTheOthersAreRings`): 7 dots at index 3 → dots 0–2 `accent` 11 dp, dot 3 a 29 × 11 `accent` pill, 4–6 `surf3` 11 dp; 9 dp gaps. `theDotsAreOneSpokenStep` keeps passing.
- `SetupButtonsTest.theWizardsButtonsAreButtons` (changed): `wizard_back`, `wizard_next`, `wizard_skip` 61 dp; `welcome_sample`, `people_add` 48; `wizard_next` has `arrow_forward` after its label.
- `SetupButtonsTest.backAndNextSitInsideTheColumn` (Review Focus 4): at 1280 × 800 dp, `wizard_back`'s left = `wizard_step`'s left and `wizard_next`'s right = its right (±1 dp); `wizard_step` 640 dp wide.
- `SetupButtonsTest.theWizardsButtonsStayAboveTheKeyboard` (extended, Review Focus 4): at font scale 1.0 and 1.3.
- `StepsUiTest.eachStepHasAtMostOneGreenButton` (Review Focus 4): Welcome, Household with only you, Done with the home-app prompt showing → at most one node whose fill is `accent` (read through a test tag on each primary, or the button's colours, as `SetupButtonsTest` reads them); Done's prompt button quiet.
- `KioskPageTest.chooseHomeAppIsTheGreenButtonThere` (**pin** of ruling 8: still primary in Settings).
- `SettingsScreenTest.thePaneStaysSevenTwentyWide` (**pin** of ruling 6): `settings_pane` at most 720 dp on a 1280 dp screen.
Run: `./gradlew :core:setup:testDebugUnitTest --tests '*WizardDots*' --tests '*SetupButtons*' --tests '*StepsUi*' --tests '*KioskPage*' --tests '*SettingsScreen*'` — Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 5–8 and Units: the buttons' `Row` moves inside the column's `Box` at its foot, `width(wizardColumn)`, centred.
- [ ] **Step 3: Images** — predicted prefixes `wizard_frame_`, `welcome_`, `location_`, `you_`, `done_`, `people_list_` (if drawn in the frame — refine), `connect_step_`, `connected_step_`, `review_` (titles), `settings_kiosk_`, `settings_location_`, `settings_people_`, `settings_all_pages_`; `wizard_pin_gate_` must not change. Against 5a.
```bash
git add core/setup capability/calendar
git commit -m "Lay the wizard in a 640 dp column with done, here and to-go dots, and Back and Next as big buttons at its foot, with one green button per step"
```

---

### Task 4: Wizard: the people list and the You step (spec §4.11; rulings 2, 8, 9, 13; Review Focus 1)

**Review:** sonnet.

**Files:**
- Modify: `core/setup/.../PeopleUi.kt` (`PeopleList` Add person quiet with `PERSON_ADD`; `PersonRow(minHeight = 75.dp)`, the dot 15; `pinState`), `SetupCopy.kt` (`someoneCalled`, `WHO_ELSE_LINE`, the pin lines), `steps/HouseholdStep.kt` (the line), `steps/YouStep.kt` (the name error under the field; `HhMessageLine` keeps only the non-field messages)
- Test: `PeopleUiTest.kt` (`theListShowsEachPersonsRoleAndWhetherTheyHaveAPin`), `YouFormTest.kt`, `SetupCopyTest.kt`, `RowStylesTest.kt` (**pin**), `SetupScreenshotTest.kt`

**Interfaces:**
- Consumes: `HhTextField(error)` (Task 2); `HhListRow(minHeight)` (Task 1).
- Produces: `internal fun someoneCalled(name: String): String = "Someone is already called $name. Try “$name B” or a nickname."`; `internal const val WHO_ELSE_LINE = "Everyone gets a colour on the calendar. A PIN lets them add events."`; `internal fun noPinYet(name: String): String = "no PIN yet, so $name can only look"`.

- [ ] **Step 1: The failing tests** (each ends with `assertClickablesHaveRoleAndLabel()`)
- `theListShowsEachPersonsRoleAndWhetherTheyHaveAPin` (changed, ruling 13): Alex "Admin · PIN set"; Sam without a PIN "Adult · no PIN yet, so Sam can only look"; an Admin without a PIN "Admin · No PIN".
- `addPersonIsQuietWithItsIcon` (note 41): `people_add` "Add person" 48 dp, the quiet fill, `person_add` before the label.
- `aPersonRowIsSeventyFiveHigh`: `person_Sam` 75 dp ±1, the dot 15 dp.
- `theHouseholdStepSaysWhyColoursAndPins`: the step shows "Who else lives here?" and `WHO_ELSE_LINE`.
- `YouFormTest.aTakenNameIsAnErrorOnTheNamedFieldAndSaidOnce` (extended, Review Focus 1): a taken "Sam" → `you_name_error` "Someone is already called Sam. Try “Sam B” or a nickname." under the field; `you_message` empty; the field's error said once; typing clears both.
- `SetupCopyTest.someoneCalledSuggestsAnotherName`.
Run: `./gradlew :core:setup:testDebugUnitTest --tests '*PeopleUi*' --tests '*YouForm*' --tests '*SetupCopy*' --tests '*RowStyles*'` — Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces and Units.
- [ ] **Step 3: Images** — predicted `people_list_`, `you_`, `settings_people_`; new `you_name_taken_dark`. Against 5a and 5b's name error.
```bash
git add core/setup
git commit -m "Make Add person a quiet button, give each person's row the redesign's size and words, and show a taken name under its field with a suggestion"
```

---

### Task 5: The person sheet (spec §4.11; rulings 9–12; Review Focus 1)

**Review:** opus.

**Files:**
- Modify: `core/setup/.../PersonEditorSheet.kt` (`PersonForm` refusals by field; the sheet's title and padding as 9c-1's Add event; the sections; role radio rows; the PIN row; the footer), `PeopleEditor.kt` (`PeopleOutcome.Refused(message, field)`, `enum class PersonField { NAME, COLOUR, ROLE, PIN }`), `SetupCopy.kt` (`takenLine`, `roleWhat`), `SetupDimens.kt` (sheet padding 29/32/27; `SetupType.sheetTitle` 35 sp W700)
- Test: `PeopleUiTest.kt`, `PeopleEditorTest.kt`, `PersonFormTest.kt`, `SetupCopyTest.kt`, `SetupButtonsTest.kt` (`person_save` 61 dp, `person_remove` 61), `SetupScreenshotTest.kt`

**Interfaces:**
- Consumes: `HhRadioRow`, `HhSwatchGrid`, `HhTextField(error)` (Task 2); `HhFooterButton`, `HhFieldError` (9c-1 Task 3).
- Produces: `PeopleOutcome.Refused(val message: String, val field: PersonField? = null)` (`nameTaken` goes: `someoneCalled` → `NAME`, `COLOUR_TAKEN` → `COLOUR`, `PIN_TAKEN` and `ADMIN_NEEDS_PIN` → `PIN`, `NEEDS_AN_ADMIN` → `ROLE`, the rest null); in `PersonForm`, `var refusal: PeopleOutcome.Refused?` in place of `message` and `nameError`, cleared by any change; `internal fun takenLine(taken: Map<Long, String>): String?` (ruling 11; null when empty); `internal fun roleWhat(role: Role): String`.

- [ ] **Step 1: The failing tests** (`PeopleUiTest` unless named; each ends with `assertClickablesHaveRoleAndLabel()`)
- `aTakenNameShowsUnderTheNameAndTypingClearsIt` (Review Focus 1; replaces part of `aRefusalShowsInTheSheetAndKeepsWhatWasTyped`): add "Sam" while Sam exists → `person_name_error` with the suggestion, the ring; `person_message` empty; type "Sam B" → both gone; what was typed kept.
- `aTakenColourShowsUnderColour` (Review Focus 1): the editor refuses with `COLOUR_TAKEN` → `person_colour_error` under the swatches, announced; nothing under Name.
- `aPinRefusalShowsUnderPin` (Review Focus 1): `ADMIN_NEEDS_PIN` → `person_pin_error`.
- `aSaveFailureStaysAboveTheFooter`: `couldNotSaveDetails` → `person_message`, no field error.
- `rolesAreRadioRows` (note 44): three `role_*` rows, one selected (Adult for a new person), each one node "Adult, Can add and change any event"; a tap selects Admin.
- `takenColoursAreBlockedAndNamed` (replaces `aNewPersonStartsOnTheFirstFreeColourWithTakenOnesStruck`): Alex green, Sam blue taken → those swatches blocked; "Green and blue are taken by Alex and Sam." under them; the first free (pink) chosen with its tick.
- `thePinHintSitsBesideSetPin`: a new Adult named Sam → "Without a PIN, Sam can only look." right of `person_set_pin`, level with it.
- `saveFillsTheFooter` (note 45): `person_save` "Save person" 61 dp across the body; an existing person → Remove person on its left (61), Save changes filling the rest.
- `PeopleEditorTest.eachRefusalNamesItsField`: the five outcomes carry the fields above.
- `SetupCopyTest.takenLineNamesColoursAndPeopleInPaletteOrder`: one, two, three; empty → null.
Run: `./gradlew :core:setup:testDebugUnitTest --tests '*PeopleUi*' --tests '*PeopleEditor*' --tests '*PersonForm*' --tests '*SetupCopy*' --tests '*SetupButtons*'` — Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 9–12 and Units; the header as Add event's (35 sp, close 48); sections 16 apart, label to control 8.
- [ ] **Step 3: Images** — predicted `person_`; new `person_name_taken_dark`, `person_name_taken_light` (5b's state: "Sam" taken, green and blue taken, Adult chosen). Against 5b.
```bash
git add core/setup
git commit -m "Redraw the person sheet: errors under the field they're about, roles as radio rows, taken colours blocked and named, and a full-width Save person"
```

---

### Task 6: Settings: the list panel, the page frame and titles (spec §4.12; rulings 5, 14, 20; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify: `core/setup/.../SettingsScreen.kt` (`PageItem`, the panel, the page padding), `SetupDimens.kt` (the settings values of Units), `pages/LocationPage.kt`, `pages/PeoplePage.kt`, `pages/KioskPage.kt` (`StepTitle` already `HhPageTitle`), `app/.../display/ui/DisplaySettingsPage.kt`, `capability/door/.../ui/DoorSettingsPage.kt`, `capability/lights/.../ui/LightsSettingsPage.kt`, `capability/music/.../ui/MusicSettingsPage.kt` (their `Text(…, HhType.screenTitle)` → `HhPageTitle`)
- Test: `SettingsScreenTest.kt`, `SettingsScreenshotTest.kt`; `app/src/test/.../AppContentTest.kt`; each page's test where it checks its title (unchanged text)

**Interfaces:**
- Consumes: `HhPageTitle` (Task 1).

- [ ] **Step 1: The failing tests** (each ends with `assertClickablesHaveRoleAndLabel()`)
- `SettingsScreenTest.theListIsTheHandoffsPanel`: `settings_page_*` 52 dp high, 7 dp apart; the "Settings" heading 35 sp; Close 48 dp at the panel's foot.
- `SettingsScreenTest.theChosenPageIsBoldAsWellAsTinted` keeps passing (ruling 14).
- `SettingsScreenTest.thePageIsPaddedAsDrawn`: `settings_pane`'s left is 320 + 48 dp, its top 43 dp.
- `eachPagesTitleIsThePageTitle`: on Display, Door, Lights, Music, Home location, People, Tablet lock, the title node is a heading at 37 sp (one test per module's page test, or one in `app`'s `SettingsWiring`/`SetupWiringTest` over all pages if they're wired there).
- `AppContentTest.aTapOnASettingsSwitchCountsAsATouch` (**pin**, Review Focus 5): Settings open on a page with an `HhSwitchRow`; tap the row → the session's touch is recorded (as `aUserActivityTouchWhileSettingsIsOpenReachesTheSession` reads it).
Run: `./gradlew :core:setup:testDebugUnitTest --tests '*SettingsScreen*' :app:testDebugUnitTest --tests '*AppContentTest*'` plus each page module's settings test — Expected: FAIL.
- [ ] **Step 2: Implement** per Units and ruling 5.
- [ ] **Step 3: Images** — predicted prefixes `settings_` (every module), `review_` (the page title), `settings_calendars_`. Against 2c's panel and 5c's page header.
```bash
git add core/setup app capability
git commit -m "Style the Settings list as the redesign's panel and give every Settings page the same title"
```

---

### Task 7: Settings › Calendars (spec §4.12; rulings 3, 4, 15–17; Review Focus 2, 3)

**Review:** opus.

**Files:**
- Modify: `capability/calendar/.../ui/ReviewCalendars.kt` (`ConnectionHeader` on the row look; `SourceRow` → `CalendarRowCard`; `ReadProblem` → the strip; the copy), `ui/CalendarType.kt` (the 5c values of Units)
- Test: `ui/ReviewCalendarsTest.kt`, `ui/ReviewScreenshotTest.kt`, `ui/HealthWordsTest.kt` if it reads the problem copy

**Interfaces:**
- Consumes: `HhSwitch` (Task 1); `HhPageTitle` (Task 1); `Icons.SYNC_PROBLEM`, `EXPAND_MORE`.
- Produces: `internal const val MAIN_NEW_EVENTS_GO_HERE = "Main · new events go here"`; `internal fun cantRead(account: String?, main: Boolean): String` (ruling 16); tags kept: `review_source_{id}`, `review_person_{id}`, `review_show_{id}`, `review_show_switch_{id}`, `review_make_master_{id}`, `review_read_problem_{id}`, `review_hide_{id}`; new `review_main_{id}` (the badge).

- [ ] **Step 1: The failing tests** (`ReviewCalendarsTest`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `aCalendarsLineIsSixtyNineHigh` (Review Focus 2): every source row's top line 69 dp ±1 at font 1.0, with and without the badge; the account row ≥ 75 dp.
- `theMainBadgeIsInline` (note 46): the main calendar's `review_main_{id}` "Main · new events go here" sits right of the name on the same line; no separate "New events go here" text.
- `aLongNameWithTheBadgeFitsAtLargeFont` (Review Focus 2): a 40-character name on the main calendar at font 1.3 on a 720 dp pane → no two of name, badge, pill and Show overlap; the name's layout has an ellipsis; the badge's text isn't cut.
- `theMainCalendarSaysAlwaysShown` (note 46): "Always shown" shown beside its switch (no "Show" there), the switch disabled-on; spoken "Show Family, Always shown" (as today's state).
- `thePersonPillOpensTheChipsUnderTheLine`: tap `review_person_{id}` → the chips below the 69 dp line, `stateDescription` "expanded"; the pill 48 dp tall to the touch.
- `theStripSitsAtTheFootUnderTheChips` (Review Focus 3): a read problem with the dropdown open → `review_read_problem_{id}`'s bottom = the card's bottom, its left and right = the card's (full width), below the chips.
- `theMainCalendarsProblemSaysChooseAnotherFirst` (Review Focus 3): main with a problem → "Can’t read this calendar. Check it’s still shared with family@example.com. To hide it, choose another main calendar first.", no `review_hide_{id}`.
- `aCalendarsProblemOffersHideInsideTheStrip`: non-main → `review_hide_{id}` inside the strip; a tap → `onShown(source, false)`.
- `aHiddenCalendarShowsNoStrip` (**pin**, Review Focus 3).
- `useForNewEventsSitsUnderTheLine`: a non-main writable calendar → `review_make_master_{id}` under the line, 48 dp; the main has none.
- `HealthWordsTest` (or where `cantRead` is tested): with no account → "…shared with this account."
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*ReviewCalendarsTest*' --tests '*HealthWords*'` — Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 15–17 and Units: the card a `Column` (clip, `surf`, `border` edge) of the line (`Row`, `heightIn(min = 69.dp)`), then the optional chips, the Use button, the strip; Show stays one toggleable node around its label and the drawing-only `HhSwitch`.
- [ ] **Step 3: Images** — predicted `review_`, `settings_calendars_`, `connected_step_` (if it draws a source row — refine); new `review_strip_open_dark` (the main calendar's problem with the chips open). Against 5c.
```bash
git add capability/calendar
git commit -m "Redraw Settings › Calendars as fixed rows with the Main badge inline, a person dropdown and Show, and read problems in a strip at the card's foot"
```

---

### Task 8: The other Settings pages: slider rows, the door volume, Location's errors (spec §4.12; rulings 18, 19; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify: `capability/door/.../ui/DoorSettingsPage.kt` (`VolumeRow` → `HhSliderRow`), `app/.../display/ui/DisplaySettingsPage.kt` (`BrightnessRow` → `HhSliderRow`), `capability/music/.../ui/MusicSettingsPage.kt` (`LowerToRow` → `HhSliderRow`), `core/setup/.../LocationPane.kt` (the status line under the field), the modules' dimens (unused slider widths go)
- Test: `capability/door/src/test/.../ui/DoorSettingsPageTest.kt` (:118–127), the Display page's test in `app`, `MusicSettingsPageTest.kt`, `core/setup/.../LocationPaneTest.kt`; their screenshot tests

**Interfaces:**
- Consumes: `HhSliderRow` (Task 1); `HhFieldError` (9c-1 Task 3).

- [ ] **Step 1: The failing tests** (each ends with `assertClickablesHaveRoleAndLabel()`)
- `DoorSettingsPageTest.theVolumeStepsInTensAndSavesOnLetGo` (Review Focus 5; replaces the `SetProgress(0.3f)` lines): `door_volume` spoken "Chime volume, 30 percent" at 0.3; `SetProgress(30f)` → `onVolume(0.3f)` once, on let go; the row is the 69 dp row.
- Display: `theBrightnessRowsAreSliderRows`: each brightness row 69 dp, its value "40 %" on the right, `onLetGo` as today.
- `MusicSettingsPageTest.lowerMusicToIsASliderRow`: `music_lower_to` keeps its 5 % steps and words.
- `LocationPaneTest.aFailedSearchSaysSoUnderTheField`: `TownResults.Failed` → `location_status` an announced `HhFieldError` directly under `location_query` (above any row), the Try again button kept; `Searching` → the `mute` line in the same place; a failed save → "Couldn’t save the home location — try again." there.
- `LocationPaneTest.theFieldHasNoRingForASearchFailure` (**pin** of ruling 19): no `danger` edge on the field.
Run: `./gradlew :capability:door:testDebugUnitTest --tests '*DoorSettingsPage*' :capability:music:testDebugUnitTest --tests '*MusicSettings*' :core:setup:testDebugUnitTest --tests '*LocationPane*' :app:testDebugUnitTest --tests '*Display*'` — Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 18–19.
- [ ] **Step 3: Images** — predicted `settings_door_`, `settings_display_`, `settings_music_`, `settings_location_`, `location_`. Against 5c's row pattern and note 43.
```bash
git add capability/door capability/music app core/setup
git commit -m "Put Settings' sliders in rows, step the chime volume in tens, and show Home location's errors under its field"
```

---

### Task 9: The emulator walk-through on `culvery-5`

**Review:** the controller, with the user at the emulator. (The workflow runs `simplify` over the branch first, then the gate.)

**Rules.** As Plan 9a's Task 15: only the `culvery-5` AVD, started with `-no-snapshot-save -camera-front emulated` (check `adb -s "$S" emu avd name`); it holds a debug build, so `install -r` of the debug APK; never uninstall; never a Gradle install task while a device is connected; the user types every PIN; put back every setting changed. Walking the wizard needs an empty household: ask the user first, and they clear the AVD's app data themselves; otherwise the wizard is checked through its screenshots and Settings › People's sheet.

- [ ] **Step 1: The AVD and the build** — `./gradlew :app:assembleDebug`; `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2: Walk the checklist** (one line each)
1. The wizard: the dots read done, here and to go; Back and Next at the column's foot, Next with its arrow; each step has at most one green button.
2. Who else lives here: the rows, Add person quiet; add "Sam" twice → the error under the name with the suggestion; taken colours blocked with the line; roles as radio rows; Save person across the foot.
3. Settings: the list panel; every page's title the same size; switches the new size.
4. Settings › Calendars: the fixed rows, the Main badge inline, the person dropdown, "Always shown"; a calendar unshared in Google (or the fake's read problem) shows the strip with Hide inside it.
5. Settings › Door: the chime volume in tens; Display and Music sliders in rows; Home location's failed search (Wi-Fi off) says so under the field.
6. Leave Settings untouched: the closing ring and the close at the usual time; a tap on a switch restarts it.
- [ ] **Step 3: The fault loop** — each fault a failing test, the fix, the gate, a commit naming it, `install -r`, re-checked.
- [ ] **Step 4: Put back** every setting and the household; `adb -s "$S" emu kill`.
- [ ] **Step 5: Record** under "Plan 9c-2 (Redesign: the wizard and Settings): emulator walk-through".
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9c-2's emulator walk-through"
```

---

### Task 10: On the tablet

**Review:** the controller with the user. **Needs the SM-T510 on the cable.** 4e's tablet rules (save and restore every setting changed, signed releases with `adb install -r`, never uninstall, the names check before any commit). The wizard is not re-run on the household's tablet; Settings › People's sheet stands in for the person sheet.

- [ ] **Step 1: Install the release** — signed per `docs/setup/release.md`; keep `mapping.txt` in `culvery-notes/mappings/`.
- [ ] **Step 2: At the wall** (the user): Settings at arm's length; the Calendars rows with the household's calendars; a person's sheet opened and closed without saving; the switch size.
- [ ] **Step 3: Record** — "Plan 9c-2 (Redesign: the wizard and Settings): on the tablet": what read and what didn't; no names.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9c-2 on the tablet"
```

---

### Task 11: Final review, merge, install

- [ ] **Step 1: The review** — opus, the whole branch against spec §4.11, §4.12, §6.4 and this plan; one fix wave, each fix with its failing test first and the gate.
- [ ] **Step 2: Merge** — on the user's say: `git merge --ff-only plan-9c2-wizard-settings` on `main`; the gate on main; never push. Plan 9 is then complete: the end-of-v1 design review's list (chip size and weight, the Lights switch, the wizard title size) goes to the follow-ups.
- [ ] **Step 3: Install** — the release from the merged `main` on the tablet, `install -r`.

---

## Spec coverage (9 design → tasks)

| Spec | Task |
|---|---|
| §4.11 step dots (note 40) | 3 |
| §4.11 one green button; Add person quiet (note 41) | 3, 4 |
| §4.11 Back and Next in the column, 92 px, Next's arrow (note 42) | 3 |
| §4.11 errors under their field with a suggestion (note 43) | 2, 4, 5 |
| §4.11 role radio rows (note 44) | 2, 5 |
| §4.11 the full-width Save footer; taken swatches (note 45) | 2, 5 |
| §4.11 the other wizard steps take the patterns | 3 (titles, buttons, one green), 4 (You), 7 (Your calendars), 8 (Where's home) |
| §4.12 Calendars: fixed rows, inline Main, person dropdown, Show, Always shown, the strip (note 46) | 1, 7 |
| §4.12 the other pages take the 5c rows and errors under fields | 1, 6, 8 |
| §4.12 the Settings list's styling (2c) | 6 |
| §6.4 one disabled style | 1 (the switch), every task |
| §7 images looked at; CopyScanTest; TalkBack | every UI task |
