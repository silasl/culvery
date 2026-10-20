# Culvery — Plan 9c-1: The redesign, the shared form pieces and Add event (with multi-day and "After N times") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–9 are subagent tasks; Task 10 is the controller on the `culvery-5` AVD with the user; Tasks 11–12 need the user and the son's SM-T510.
>
> **9c is split in two** (it came to 23 tasks): this plan, **9c-1**, is the shared sheet and form pieces and the Add event sheet, including creating multi-day all-day events and repeats that end after N times. **9c-2** — `docs/superpowers/plans/2026-10-20-culvery-09c2-redesign-wizard-settings.md` — is the setup wizard and the Settings pages; it starts from 9c-1 merged and reuses its shared pieces.

**Goal:** The Add event sheet looks and behaves as the October redesign draws it — the wider sheet from the right, section labels, a child's locked Who chips with the line saying why, "Pick a date" and "Pick a time", Time and Length side by side, Repeats with "More options" opening Every day, Every month, Every year and an Ends row (Never, On a date, After N times), the full-width footer that says "Add a title first" while it can't save — and an all-day event can be made to last several days with an "Until" chip.

**Architecture:** Shared pieces first in `:core:ui` (four icons, the sheet's width, scrim and edge, the section label's tracking, the footer button with its reason line, the error line under a field, the locked chip). The repeat end becomes a type in `:capability:calendar` (`RepeatEnd`: never, on a date, after N times) that `recurrenceRule` writes as `UNTIL` or `COUNT`, and the fake provider's expander reads `COUNT`. `EventForm` gains the Until date for all-day events and unlocks all-day multi-day edits. The sheet (`EventEditorSheet`) is then redrawn section by section.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00, Material 3), Hilt 2.57.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-19-culvery-9-redesign-design.md` — §2.1 (multi-day, amended 2026-10-20: creating them is 9c's Add event), §4.8 (Add event), §6.4 (one disabled style; the reason line optional), §7. The handoff `docs/design/2026-10-redesign/handoff.md` (section "Add event sheet (4a)", and "Not redesigned" for the pickers and the event detail sheet) and `notes.txt` (notes 33–36) are the drawings; the full HTML export is in `C:\Users\si\code\siland\culvery-notes\design-handoff-2026-10\` when a value is unclear. Where the spec and the handoff disagree, the spec wins. Read all three with this plan.
**Previous plans (format, shared parts, rulings):** `docs/superpowers/plans/2026-10-20-culvery-09b-redesign-tabs.md` and `docs/superpowers/plans/2026-10-19-culvery-09a-redesign-home.md` — their Units ruling, the 48 dp touch rule and the one disabled style carry over. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` ("Adding events, later": the Until chip; the multi-day entry under "Feature ideas").

**Builds on:** branch `plan-9c-forms` in the one checkout `C:\Users\si\code\siland\culvery` (no worktrees; cut from `main` at 05b1cbb, Plan 9b merged). Work only there. Never push.

| # | Task | Review |
|---|---|---|
| 1 | Icons | sonnet |
| 2 | Shared: the sheet's width, scrim and edge; section labels' tracking | sonnet |
| 3 | Shared: the footer button and its reason, the error under a field, the locked chip | sonnet |
| 4 | Repeats: the end as a type, `COUNT`, the fake's expander | **opus** (the rule written to Google; `UNTIL` and `COUNT` exclusive) |
| 5 | Multi-day: Until in the form, all-day spans unlocked | **opus** (exclusive ends; the day moving under Until; edits keeping their span) |
| 6 | The sheet: header, title, sections, Time beside Length, the chips' words, the footer | sonnet |
| 7 | Who for a child: locked chips and the line | sonnet |
| 8 | Repeats: More options, the Ends row, After N times | sonnet |
| 9 | The Until chip and the unlocked all-day edit | sonnet |
| 10 | The emulator walk-through on `culvery-5` | controller + user |
| 11 | On the tablet | controller + user, **needs the SM-T510** |
| 12 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:** the icons first (a font rebuild can stop for a download; all four are in the full font at 608ce00, so it shouldn't). The shared sheet next and alone, because it moves the event detail, person, PIN-over-sheet, connect, Favourites, pairing and address sheets' images, and those moves should be one deliberate commit. The form pieces (3) before any sheet task uses them. The two models (4, 5) are plain JVM and land before the sheet is redrawn (6), then the sheet's sections that read them (7–9).

## Shared components 9c-2 reuses

`ButtonSize.Footer` and `HhFooterButton` (Task 3: the wizard's Back and Next, the person sheet's Save person), `HhFieldError` (Task 3: errors under their field in the person sheet, the You step and Settings › Home location), `ShellTokens.sheetWidth`/`sheetScrim` (Task 2: the person sheet), `Icons.PERSON_ADD`, `ERROR`, `BLOCK` (Task 1).

## Rulings against the code

Where the spec leaves a choice open or doesn't fit the code at 05b1cbb. Each is the plan's call unless it cites the spec or the user.

1. **Units** as Plan 9a: dp = handoff px ÷ 1.5, rounded to whole dp/sp; every 2 px edge is 1 dp; the SM-T510 reads 240 dpi. Values are in **Units** below.
2. **Touch targets stay 48 dp** (9a ruling 2). The handoff's 64 px chips (43 dp) stay the shared 48 dp `HhChoiceChip`, its padding and 16 sp label unchanged; the selected chip's weight 700 and the 23 px (15 sp) label are left for the end-of-v1 design review, so the app keeps one chip.
3. **The shared sheet** (the user's brief, 2026-10-20): `ShellTokens.sheetWidth` 600 → 613 dp (920 px), `sheetScrim` 0x8C → 0x99 (.6), the left edge `line` → `surf3`. It is one task (2) because every right-hand sheet moves: Add event, the event detail sheet, the person sheet, the PIN pad drawn over a sheet, the connect sheet's scrim, Favourites, Hue pairing and both address sheets. Their contents are "not redesigned" (handoff); only the frame changes. The editor's and the person sheet's own `.width(ShellTokens.sheetWidth)` on their outer `Box` stays (it sizes the picker and PIN layers drawn over the sheet) — both read the one token.
4. **Section labels** take the handoff's tracking: `HhType.sectionLabel` letter-spacing 0.5 → 1 sp (1.5 px); size and weight unchanged (13 sp W700). It is shared, so it changes in Task 2 with the sheet (the tray label uses it too).
5. **The footer button** is `ButtonSize.Footer` (92 px: 61 dp, label 19 sp W700, icon 24, padding 0 × 32, gap 8). `HhFooterButton` puts the reason line 8 dp above a disabled button when given (spec §6.4: optional; only Add event's "Add a title first" has one, shown while the title is blank); the disabled look stays the shared 1 dp `surf3` outline with a `mute` label (9a). TalkBack hears the reason as the button's state ("Add event, Add a title first, disabled").
6. **The error under a field** is `HhFieldError`: the `error` icon (filled, 17 dp) and the message 14 sp W600 in `danger`, 5 apart, 8 under its field. With `announce = true` it is a polite live region kept one line tall while empty (as `HhMessageLine`, so TalkBack hears it appear); a text field's own error passes `announce = false` and is composed only while shown, since the field already carries it (9c-2). In this plan the Ends row's "end before the day" line and the Until line use it.
7. **The locked chip** (note 33): `HhChoiceChip(locked = true)` draws no fill, a 1 dp `surf3` edge, the `lock` icon before `leading` (the colour dot stays), the label in `mute`, and no 38 % fade; it keeps the disabled chip's semantics ("not available", "find out why") and stays tappable so a tap still explains. A locked chip that is selected (an adult tagged the child's event, which the child is editing) is drawn selected, with the lock.
8. **The child's line** (note 33): under a child's Who chips, `lock` 15 dp and "`{name} can add events for {name} only.`" in 13 sp `mute`. The refusal toast takes the same sentence: `cannotAddForOthers(name)` becomes "`{name} can add events for {name} only.`" (handoff wording; replaces "…can only add events for themselves.") everywhere it is used — the editor's tap on a locked chip and `CalendarEditor`'s create and retag refusals.
9. **No ellipses on the chips** (note 34): "Pick date…" → "Pick a date", "Pick time…" → "Pick a time", Ends' "On a date…" → "On a date", and the follow-ups' "Until…" → "Until". A picked value still replaces the label ("Sat 26 Sep", "18:00").
10. **Time and Length side by side** (handoff 4a): one `Row`, Time's section at its natural width, Length's `weight(1f)` so its chips wrap within it, 27 dp apart. Length shows only for a timed event (as today); Until (ruling 13) sits in Time's section after "Pick a time", only while All day is chosen.
11. **Repeats** (note 35; the user's brief: Every day moves under More options, not removed): the first row is Never, Every week, Every 2 weeks and "More options" (`Role.Button`, `expand_more` after the label). A tap opens, in place and for the rest of the sheet's life, a second row Every day, Every month, Every year, and — while the event repeats — the Ends row: Never, On a date, After N times. The chip goes once opened (it opens, it doesn't toggle: nothing chosen inside can be hidden). Only a new event repeats (as today). The body scrolls only once More options is open (Review Focus 5).
12. **After N times** (the user's brief): `RepeatEnd.After(times)`, written as `;COUNT=n`; `UNTIL` and `COUNT` never both (one `RepeatEnd` value). The chip reads "After 10 times" (default 10) and, while chosen, two round quiet buttons follow it, `remove` ("Fewer times") and `add` ("More times"), stepping by 1 between 2 and 99, each disabled at its end. Google reads instances with `singleEvents=true` and `repeatsLabel` already treats `COUNT` as plain, so the Google provider changes nothing (pins); the fake's `RecurrenceExpander` takes the first `COUNT` occurrences. The Ends row keeps "On a date" and its error line.
13. **Multi-day creation** (the user, 2026-10-20; spec §2.1): while All day is chosen, an "Until" chip (`date_range`) opens the date picker as a new `EditorPicker.Until` (the end date of a series stays `EndDate`). The draft becomes `AllDay(day)` → `AllDay(until + 1)`, exclusive. Picking the start day itself clears Until (one day). A picked Until before the day shows `HhFieldError` "The last day is before the first day. Pick a later one." and Save waits, as Ends does. Changing Day moves Until by the same number of days, so the event keeps its length. Choosing a time clears Until: **multi-day timed events can't be created on the wall** (they stay on the phone). The chip reads "Until Thu 22 Oct" once picked. Repeats stay offered for a multi-day event (the fake expands a span's days; Google repeats it as written).
14. **All-day spans unlock** (the user's brief): `datesLocked` now means a **timed** event over several days; an all-day multi-day edit opens with Day on its first date and Until on its last (exclusive end − 1), editable. `lockedDatesLabel` is unchanged for timed spans. An untouched all-day edit still keeps its own start and end exactly (`keepsOwnSpan`).
15. **The calendar's own `RoundButton`** (Pickers.kt) goes: the date picker's ‹ › become `HhRoundButton(on = ButtonSurface.Surf)` (the shared disabled look; spec §6.4), and `CalendarDimens.DISABLED_ALPHA` and `pageButton*` go if nothing else reads them. The pickers are otherwise not redesigned.
16. **Length chips keep full words** ("30 minutes", "1 hour", "2 hours"): the handoff draws "30 min", but `lengthLabel` is also the spoken and summary wording ("1 hour 30 minutes"), and the app spells units out. Flagged to the user.

## Units (converted once, px ÷ 1.5)

| Where | Handoff px | dp / sp |
|---|---|---|
| Sheet | 920 wide; scrim .6; 1 px `s3` left edge; padding 44/48/40; gap 24 | 613; `0x99000000`; 1 `surf3`; top 29, sides 32, bottom 27; 16 between blocks (`sheetGap`) |
| Sheet header | title 52/700; summary 23 muted, 6 under; close 72 | 35 sp W700; 15 sp `mute`, 4 under; 48 (`HhCloseButton`) |
| Title field | 96, r 26, 31/600 | `HhTextField` as is: 64, r 18, 22 sp W600 |
| Section label | 19/700, ls 1.5, 12 above chips | 13 sp W700, ls 1 sp; 8 |
| Chips | 64, r 32, 23 px, gap 12 | 48 (ruling 2), r 24, 16 sp W600, gap 8 |
| Locked chip | 2 px inset `s3`, lock 28, dot 14 | 1 `surf3` edge, lock 20 (`chipIcon`), dot 12 (`chipDot`) |
| Child's line | 20 muted, lock 22, gap 8 | 13 sp `mute`, lock 15, gap 5; 8 under the chips |
| Time · Length | 40 apart | 27 |
| Footer | 92, r 46, 28/700, check 36, gap 12; reason above | 61, pill, 19 sp W700, icon 24, gap 8, padding 0 × 32; reason 16 sp `mute`, centred, 8 above |
| Field error | 21/600 danger, `error` 26 filled, gap 8 | 14 sp W600 `danger`, 17 filled, gap 5; 8 under the field |
| Count stepper | — | `HhRoundButton` 48, icon 24, 8 from the chip and each other |

## Global Constraints

**From the spec, verbatim:**

- §2.1 "**Multi-day events are folded into 9b and 9c** (the user, 2026-10-20): the week's lane in 9b, creating them in 9c's Add event, "Day 2 of 3" on Home's Today with them".
- §4.8 "The sheet from the right; the title field; section labels; chips; a child's Who chips locked with the explanation line (note 33); "Pick a date" and "Pick a time" without ellipses (note 34); Time and Length side by side; Repeats with "More options ▾" expanding to Every month, Every year and the Ends row, scrolling only once open (note 35); the footer primary, and **disabled as a 2 px outline with a muted label and the reason above it** ("Add a title first") (note 36)."
- §6.4 "**One disabled button style** (the outline) everywhere … The reason line above it is **optional**: shown where the handoff gives one (Add event's "Add a title first"), not invented for every disabled button."
- §5 "Event detail sheets, the date and time pickers … unchanged except the shared tokens and button styles they already use."
- §7 "Every changed screen's Roborazzi baselines are re-recorded and each new image looked at against the handoff"; "Copy changes go through CopyScanTest; TalkBack labels follow the new copy."

**Copy** (verbatim, curly apostrophes in code): "Add event", "Change event", "Who", "Day", "Time", "Length", "Repeats", "Ends", "Pick a date", "Pick a time", "All day", "Until", "Until {EEE d MMM}", "Never", "Every week", "Every 2 weeks", "More options", "Every day", "Every month", "Every year", "On a date", "After {n} times", "Fewer times", "More times" (TalkBack), "`{name} can add events for {name} only.`", "Add a title first", "The last day is before the first day. Pick a later one.", "The end date is before the event’s day. Pick a later one." (kept), "Save changes", "Try again".

**Privacy:** nothing new is stored or logged; a refusal toast names only the signed-in person, as today.

**From the house rules** (as Plans 9a and 9b; binding):

- Package root `uk.co.siland.culvery`. `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only. **No new library; never change a version.** No deprecated API; the one opt-in is `ExperimentalMaterial3Api` in `HhPercentSlider`.
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug`. Never plain `test`, `check` or `build`; never a Gradle `install…` task; no subagent touches a device. **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`** for Gradle: a long temp path pushes Robolectric's database paths past Windows' MAX_PATH (SQLITE_CANTOPEN). `$TMP` below means `C:/t9` (lists only).
- **Tests that can fail:** every new test fails before its change; a test that passes before it is labelled **pin**. Timer tests on virtual time; UI timers under `compose.mainClock` one frame before the boundary and at the boundary plus one frame.
- **Every UI task ends the same way:** `compareRoborazziDebug`; open each `*_compare.png`, and each new image against the handoff section the task names; record; the baseline check (predicted names or prefixes in `$TMP/expected-baselines.txt` / `$TMP/expected-prefixes.txt`, checked as in Plan 9a's Global Constraints — anything outside the prediction stops the task); the gate; the commit. Baselines live in `<module>/src/test/screenshots/`. Each task lists only its predictions and its commit line.
- **Copy** names what the user does; curly quotes; "·", "›", "→", "…" as written; `CopyScanTest` passes. **Accessibility:** every changed screen's test ends with `compose.assertClickablesHaveRoleAndLabel()`; TalkBack names follow the copy.
- **Icons** from `Icons`; a new one in `tools/fonts/icons.txt` and the subset rebuilt from the full font at 608ce00. **Layout numbers are named** in the module's dimens object. **Comments say what the code can't**, one line, spec refs as `(9 §4.8)`; no plan task or ruling numbers. **No personal data** — sample names only (Alex, Sam, Mia, Lounge, Kitchen, Hall, Garden, Front Door, Back Garden). **UTF-8.** **Commits** a plain sentence, no trailer. Never push. **Never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **Until against the day it ends on** — Until picked, then Day moved later or earlier; Until picked equal to the day; Until before the day; Until then a time chosen, then All day again. Expected: the span keeps its length when Day moves; equal is one day (the chip reads "Until" again); before the day shows the line and Save waits; a time clears Until; the draft's end is always the day after the last day.
   - Task 5 `EventFormTest.movingTheDayMovesUntilWithIt`, `untilOnTheDayItselfIsOneDay`, `untilBeforeTheDayWaitsToSave`, `choosingATimeClearsUntil`, `aMultiDayDraftEndsTheDayAfterItsLastDay`; Task 9 `EventEditorSheetTest.anUntilBeforeTheDaySaysSoAndSaveWaits`.
2. **Editing a multi-day event** — an all-day half term Mon 19 – Fri 23 Oct (exclusive Sat 24) opened, nothing changed, then Until moved; a timed span opened. Expected: the all-day one opens with Day Mon 19 and Until Fri 23; unchanged it is `unchanged` and saves nothing; Until moved sends TIMES only; the timed one stays locked with its one line.
   - Task 5 `EventFormTest.anAllDaySpanOpensWithItsFirstAndLastDays`, `anUntouchedAllDaySpanIsUnchanged`, `movingUntilOnAnEditTouchesOnlyTheTimes`, `aTimedSpanStaysLocked` (replaces `anEventOverSeveralDaysLocksItsDates`); Task 9 `EventEditorSheetTest.anAllDaySpanEditShowsItsUntil`.
3. **After N times at its ends, and changing the end** — After at 2 then Fewer, at 99 then More; After 5 then On a date then Never; a repeat back to Never. Expected: the count stays in 2–99 with the end button disabled; the rule has `COUNT=5` alone, then `UNTIL=…` alone, then neither; Never drops the end; the fake expands exactly 5 occurrences, an all-day 3-day series keeping each occurrence's 3 days.
   - Task 4 `RecurrenceTest.aCountedSeriesWritesCountAndNoUntil`, `EventFormTest.theCountStaysBetweenTwoAndNinetyNine`, `changingTheEndReplacesIt`; `FakeCalendarProviderTest.aCountedSeriesStopsAfterItsCount`, `aCountedAllDaySpanKeepsItsDays`; Task 8 `EventEditorSheetTest.theCountButtonsStopAtTheirEnds`.
4. **A signed-in child on an event an adult tagged for someone else** — Mia opens Alex's event (Alex's chip chosen). Expected: Alex's chip is drawn selected with the lock, the others locked, Mia's own unlocked; a tap on a locked chip toasts "Mia can add events for Mia only." and changes nothing; TalkBack hears "not available" on the locked ones.
   - Task 7 `EventEditorSheetTest.aChosenLockedChipIsDrawnChosenWithItsLock`, `aTapOnALockedChipExplainsAndChangesNothing`, `lockedChipsSayWhyNot`.
5. **The sheet's height** — More options closed at 1280 × 800 dp with a child's line and the reason line; opened with an end chosen; the keyboard up; font scale 1.3. Expected: closed, nothing scrolls; open, the Ends row is scrolled into view; the footer and its reason stay above the keyboard at both scales.
   - Task 6 `EventEditorSheetTest.theFooterAndItsReasonStayAboveTheKeyboard` (extends `saveStaysAboveTheKeyboard`, at 1.0 and 1.3); Task 8 `EventEditorSheetTest.theSheetScrollsOnlyOnceMoreOptionsOpens`, `openingMoreOptionsBringsEndsIntoView`.

The spec's own tests (§7) each have their task: CopyScanTest and TalkBack names (every task); the images looked at against 4a (Tasks 2, 6–9).

---

## File Structure

```
core/ui/.../Icons.kt, tools/fonts/icons.txt, res/font/material_symbols_rounded.ttf (Task 1)
core/ui/.../Shell.kt (sheetWidth, sheetScrim, the edge), Type.kt (sectionLabel) (Task 2)
core/ui/.../Buttons.kt (ButtonSize.Footer, HhFooterButton), Controls.kt (HhFieldError, HhChoiceChip(locked)) (Task 3)
capability/calendar/.../Recurrence.kt (RepeatEnd, COUNT), EventForm.kt (repeatEnd, the count) (Task 4)
provider/calendar-fake/.../RecurrenceExpander.kt (COUNT) (Task 4)
capability/calendar/.../EventForm.kt (until, datesLocked), ui/Pickers.kt (EditorPicker.Until) (Task 5)
capability/calendar/.../ui/EventEditorSheet.kt, ui/Pickers.kt (HhRoundButton), ui/CalendarType.kt (Tasks 6–9)
capability/calendar/.../CalendarPermissions.kt (cannotAddForOthers) (Task 7)
docs/superpowers/plans/2026-09-23-plan1-followups.md (Tasks 10–12)
```

---

### Task 1: Icons

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Icons.kt`, `tools/fonts/icons.txt`, `core/ui/src/main/res/font/material_symbols_rounded.ttf` (regenerated)
- Test: `core/ui/src/test/.../IconFontTest.kt` (`NO_FILLED_FORM` if needed)

**Interfaces:**
- Produces: `Icons.BLOCK = "block"`, `Icons.ERROR = "error"`, `Icons.PERSON_ADD = "person_add"`, `Icons.REMOVE = "remove"` (alphabetical in both files). `lock`, `expand_more`, `date_range`, `calendar_month`, `schedule`, `check`, `add`, `arrow_forward`, `sync_problem` are already there.

- [ ] **Step 1: The failing test** — the four constants in `Icons` and `icons.txt`. Run: `./gradlew :core:ui:testDebugUnitTest --tests '*IconFontTest*'` — Expected: FAIL (the font lacks the glyphs).
- [ ] **Step 2: The subset** — `tools/fonts/README.md` steps 3–4 from the blob at 608ce00 (`git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$TMP/material-symbols-full.ttf"`; `python tools/fonts/subset.py --source "$TMP/material-symbols-full.ttf"`). `error` is drawn filled (ruling 6): if its filled form is missing, stop and report. Others missing a filled form go in `NO_FILLED_FORM`.
- [ ] **Step 3: The gate and commit** — Expected: PASS, **no image changed**.
```bash
git add core/ui tools/fonts/icons.txt
git commit -m "Add the block, error, person-add and remove icons"
```

---

### Task 2: Shared: the sheet's width, scrim and edge; section labels' tracking (rulings 3, 4)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Shell.kt` (`ShellTokens.sheetWidth`, `sheetScrim`, `HhSheet`'s edge colour and its KDoc), `core/ui/.../Type.kt` (`HhType.sectionLabel`)
- Test: `core/ui/src/test/.../ShellTest.kt` or the test that pins `ShellTokens` (create `SheetTest.kt` if none), `KeyboardTest.kt` (**pin**, unchanged)

**Interfaces:**
- Produces: `ShellTokens.sheetWidth = 613.dp`; `ShellTokens.sheetScrim = Color(0x99000000)`; `HhSheet` draws its 1 dp left edge in `surf3`; `HhType.sectionLabel` letter-spacing `1.sp`.

- [ ] **Step 1: The failing tests** (`SheetTest`)
- `theSheetIsTheHandoffsWidth`: `HhSheet` in a 1280 × 800 box → 613 dp wide.
- `theSheetsEdgeIsSurf3`: the pixel 0 dp in from the sheet's left at mid-height is `surf3` (dark 0xFF303537, light 0xFFDDE3E0).
- `theScrimIsSixtyPercent`: `ShellTokens.sheetScrim.alpha` = 0x99/255 (**pin** of the handoff's value; it fails first).
- `sectionLabelsAreTrackedOneSp`: `HhSectionLabel("Who")`'s text layout letter-spacing = 1 sp.
Run: `./gradlew :core:ui:testDebugUnitTest --tests '*SheetTest*' --tests '*Keyboard*'` — Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: Images** — predicted prefixes (every right-hand sheet, its scrim, and every section label or tray label): `editor_`, `detail_`, `person_`, `connect`, `favourites_sheet_`, `lights_pairing_sheet_`, `lights_address_sheet_`, `music_address_sheet_`, `sheet_warning_`, `settings_door_`, `settings_lights_`, `settings_music_`, `lights_tab_`, `lights_grid_`, `tab_lights_`, `page_header_`; refine from the compare report before re-recording (a sheet whose screenshot draws no scrim or edge drops out), and stop on anything outside it. Look at one of each kind against 4a: the width, the `surf3` edge, the darker scrim.
```bash
git add core/ui capability core/setup app
git commit -m "Widen the right-hand sheet to the redesign's 613 dp with a darker scrim and a surf3 edge, and track section labels as drawn"
```

---

### Task 3: Shared: the footer button and its reason, the error under a field, the locked chip (rulings 5–7)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Buttons.kt` (`ButtonSize.Footer`, `ButtonTokens.footer*`, `HhFooterButton`), `core/ui/.../Controls.kt` (`ControlType.footerButton`, `HhFieldError`, `HhChoiceChip(locked)`, `ControlTokens.fieldError*`)
- Test: `core/ui/src/test/.../ButtonsTest.kt` (or where `HhButton` is tested), create `FieldErrorTest.kt`; the chip's test; `ButtonMatrixScreenshotTest` or create `FormPiecesScreenshotTest.kt`

**Interfaces:**
- Produces:
  - `ButtonSize.Footer` (61 dp, `ControlType.footerButton` = 19 sp W700, padding 0 × 32, icon gap 8). `HhButton` handles it as it does `Large`.
  - `@Composable fun HhFooterButton(text: String, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier, tone: ButtonTone = ButtonTone.Primary, icon: String? = null, trailingIcon: String? = null, enabled: Boolean = true, reason: String? = null, on: ButtonSurface = ButtonSurface.Bg)` — a `Column(modifier)`: the reason (`${tag}_reason`, 16 sp `mute`, centred, `shownNotSpoken`) 8 above when `!enabled && reason != null`, then the `HhButton` filling the width, tagged `tag`, with `stateDescription = reason` while disabled.
  - `@Composable fun HhFieldError(message: String?, tag: String, modifier: Modifier = Modifier, announce: Boolean = true)` (ruling 6).
  - `HhChoiceChip(…, locked: Boolean = false)` (ruling 7); `enabled = false` keeps today's 38 % (busy states use it).

- [ ] **Step 1: The failing tests** (each UI test ends with `assertClickablesHaveRoleAndLabel()`)
- `aFooterButtonIsSixtyOneHigh`: `HhFooterButton("Add event", …)` → 61 dp, the label 19 sp.
- `aDisabledFooterButtonShowsItsReasonAbove`: `enabled = false, reason = "Add a title first"` → `x_reason` displayed, its bottom 8 dp above the button's top; the button's state description "Add a title first"; enabled → no `x_reason` node.
- `aFooterButtonWithoutAReasonShowsNone` (**pin** of spec §6.4: optional): disabled, `reason = null` → no reason node, the outline look.
- `aFieldErrorSaysWhatWentWrongInDanger`: `HhFieldError("Someone is already called Sam. Try “Sam B” or a nickname.", "x")` → the text in `danger` at 14 sp W600 with the `error` icon left of it; `announce = true` → a polite live region; `announce = false` and null → no node.
- `anEmptyAnnouncedErrorKeepsItsLine` (**pin** of `HhMessageLine`'s rule): `announce = true`, null → a node one line tall.
- `aLockedChipHasNoFillALockAndAMutedLabel`: `locked = true`, unselected → the centre pixel is the background (`bg`), the edge pixel `surf3`, a `lock` icon before the dot, the label `mute`; state "not available", click label "find out why"; a click still calls `onClick`.
- `aLockedChipThatIsChosenIsFilled`: `locked = true, selected = true` → `selectedColor` at the centre, the lock shown.
Run: `./gradlew :core:ui:testDebugUnitTest --tests '*Buttons*' --tests '*FieldError*' --tests '*Chip*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** per the Interfaces and Units.
- [ ] **Step 3: Images** — `button_matrix_` gains the Footer row (predicted); new `form_pieces_dark`, `form_pieces_light` (a footer enabled and disabled with its reason, a field error, a locked chip unselected and chosen), against 4a and 5b; checked by name.
```bash
git add core/ui
git commit -m "Add the sheet footer button with its reason line, an error line for under a field, and a locked chip"
```

---

### Task 4: Repeats: the end as a type, `COUNT`, the fake's expander (spec §4.8; rulings 11, 12; Review Focus 3)

**Review:** opus.

**Files:**
- Modify: `capability/calendar/.../Recurrence.kt` (`RepeatEnd`, `recurrenceRule`, `RepeatChoice.COMMON`/`MORE`), `EventForm.kt` (`endsOn` → `repeatEnd`, the count steps, `Answers`), `ui/EventEditorSheet.kt` (only the calls, so it compiles; Task 8 redraws Ends), `provider/calendar-fake/.../RecurrenceExpander.kt`
- Test: `RecurrenceTest.kt`, `EventFormTest.kt` (:465–510 move to `repeatEnd`), `RepeatsTest.kt`, `provider/calendar-fake/src/test/.../FakeCalendarProviderTest.kt`, `provider/calendar-google/src/test/.../GoogleWriteTest.kt`; `ui/EditorScreenshotTest.kt` (`repeats()` calls)

**Interfaces:**
- Produces:
  - `sealed interface RepeatEnd { data object Never : RepeatEnd; data class OnDate(val date: LocalDate) : RepeatEnd; data class After(val times: Int) : RepeatEnd }`; `const val MIN_TIMES = 2`, `MAX_TIMES = 99`, `DEFAULT_TIMES = 10`.
  - `fun recurrenceRule(choice: RepeatChoice, end: RepeatEnd, allDay: Boolean, zone: ZoneId): String?` — `OnDate` writes `UNTIL` exactly as today; `After(n)` writes `;COUNT=n`; `Never` nothing.
  - `RepeatChoice.COMMON = listOf(Never, Weekly, Fortnightly)`, `RepeatChoice.MORE = listOf(Daily, Monthly, Yearly)` (companion vals; labels unchanged).
  - In `EventForm`: `var repeatEnd: RepeatEnd` (private set, starts `Never`); `fun chooseEnd(end: RepeatEnd)` (ignored unless `canRepeat`); `fun fewerTimes()`, `fun moreTimes()` (step 1, clamped to `MIN_TIMES..MAX_TIMES`, ignored unless `After`); `val endsBeforeStart` reads `OnDate`; choosing `Never` as the repeat sets `repeatEnd = Never`. `endsOn` goes.
  - `fun afterTimes(times: Int): String = "After $times times"`.

- [ ] **Step 1: The failing tests**
- `RecurrenceTest.aCountedSeriesWritesCountAndNoUntil` (Review Focus 3): `Weekly, After(5)` → `"RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=5"`; `Monthly, OnDate(2026-12-31), allDay` → `"…;UNTIL=20261231"` and no `COUNT`. Existing `UNTIL` tests move to `OnDate` and pass unchanged.
- `RepeatsTest.aCountedSeriesReadsAsItsChip` (**pin**: `COUNT` is in `PLAIN_PARTS`): `"RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=6"` → "Every 2 weeks".
- `EventFormTest.theCountStaysBetweenTwoAndNinetyNine` (Review Focus 3): `chooseEnd(After(2))`, `fewerTimes()` → `After(2)`; `After(99)`, `moreTimes()` → `After(99)`; `After(10)`, `moreTimes()` → `After(11)`.
- `EventFormTest.changingTheEndReplacesIt` (Review Focus 3): Weekly, `After(5)` → `chooseEnd(OnDate(day + 30))` → the draft's rule has `UNTIL` and no `COUNT`; `chooseRepeat(Never)` → `repeatEnd == Never`, no rule.
- `EventFormTest.aCountMakesTheSheetEdited`: `After(10)` → `edited`; back to `Never` → not.
- `FakeCalendarProviderTest.aCountedSeriesStopsAfterItsCount` (Review Focus 3): a timed weekly series `COUNT=5` read over a year → 5 occurrences, the 5th four weeks after the first.
- `FakeCalendarProviderTest.aCountedAllDaySpanKeepsItsDays`: all-day Fri 23 – Sun 25 Oct (exclusive Mon 26), weekly, `COUNT=3` → 3 occurrences, each 3 dates long.
- `GoogleWriteTest.aCountedSeriesInsertSendsItsRule` (**pin**: the writer sends the rule as given): an insert with `"RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=5"` → the request's `recurrence` is that line.
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*RecurrenceTest*' --tests '*RepeatsTest*' --tests '*EventFormTest*' :provider:calendar-fake:testDebugUnitTest :provider:calendar-google:testDebugUnitTest --tests '*GoogleWriteTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** per the Interfaces. The expander reads `COUNT` as an `Int` (`toIntOrNull`, ignored when absent or < 1) and takes that many occurrences after the `UNTIL` checks; its KDoc lists `COUNT`.
- [ ] **Step 3: The gate and commit** — Expected: PASS, **no image changed** (the sheet still shows the old Ends chips).
```bash
git add capability/calendar provider/calendar-fake provider/calendar-google
git commit -m "Let a new series end after a number of times as well as on a date, and have the sample calendar repeat it that many times"
```

---

### Task 5: Multi-day: Until in the form, all-day spans unlocked (spec §2.1; rulings 13, 14; Review Focus 1, 2)

**Review:** opus.

**Files:**
- Modify: `capability/calendar/.../EventForm.kt` (`until`, `datesLocked`, `chooseDay`, `chooseTime`, `draft`, `Answers`, `canSave`), `ui/Pickers.kt` (`EditorPicker.Until`), `ui/EventEditorSheet.kt` (only the `when (picker)` branch, so it compiles; Task 9 draws the chip)
- Test: `EventFormTest.kt`; `CalendarEditorTest.kt` (an all-day span written)

**Interfaces:**
- Produces: in `EventForm`, `var until: LocalDate?` (private set; null = one day); `val offersUntil: Boolean get() = time == TimeChoice.AllDay && !datesLocked`; `fun chooseUntil(date: LocalDate)` (date == day → null; else set, marks when touched); `val untilBeforeStart: Boolean`; `val untilLabel: String` ("Until" or "Until Thu 22 Oct", `SHORT_DAY`); `canSave` adds `!untilBeforeStart`; `datesLocked` = the original is timed and spans days; `enum class EditorPicker { None, Date, Time, EndDate, Until }`; `internal const val UNTIL = "Until"`, `internal const val UNTIL_TOO_EARLY = "The last day is before the first day. Pick a later one."`.

- [ ] **Step 1: The failing tests** (`EventFormTest`, plain JVM, Europe/London, today Mon 19 Oct 2026)
- `aMultiDayDraftEndsTheDayAfterItsLastDay` (Review Focus 1): day Mon 19, `chooseUntil(Thu 22)` → draft `AllDay(Mon 19)` → `AllDay(Fri 23)`; summary "Today – Thu 22 Oct · All day" (whatever `whenLabel` gives, asserted from it, not retyped).
- `untilOnTheDayItselfIsOneDay`: `chooseUntil(Mon 19)` → `until == null`, draft ends Tue 20, `untilLabel == "Until"`.
- `untilBeforeTheDayWaitsToSave`: day Wed 21, `chooseUntil(Tue 20)` → `untilBeforeStart`, title set → `!canSave`.
- `movingTheDayMovesUntilWithIt` (Review Focus 1): Mon 19 – Wed 21, `chooseDay(Fri 23)` → `until == Sun 25`; `chooseDay(Tue 20)` → `until == Thu 22`.
- `choosingATimeClearsUntil`: Until Wed 21, `chooseTime(Custom(18:00))` → `until == null`, `offersUntil == false`, the draft timed on one day; `chooseTime(AllDay)` → `until` still null.
- `anAllDaySpanOpensWithItsFirstAndLastDays` (Review Focus 2): edit of half term `AllDay(Mon 19)` → `AllDay(Sat 24)` → `!datesLocked`, `day == Mon 19`, `until == Fri 23`, `offersUntil`.
- `anUntouchedAllDaySpanIsUnchanged` (Review Focus 2): that edit, title unchanged → `unchanged`, the draft's start and end the original's.
- `movingUntilOnAnEditTouchesOnlyTheTimes`: that edit, `chooseUntil(Wed 21)` → `touched == {TIMES}`, draft end Thu 22.
- `aTimedSpanStaysLocked` (replaces `anEventOverSeveralDaysLocksItsDates`): Mon 19 22:00 → Wed 21 09:00 → `datesLocked`, `lockedDatesLabel` as today, `offersUntil == false`; 22:00 → 01:00 still locked and 22:00 → 00:00 still not (that test's timed lines carry over); the all-day half term there is now unlocked.
- `untilMakesTheSheetEdited`: `chooseUntil(Wed 21)` → `edited`; `chooseUntil(Mon 19)` → not.
- `aMultiDaySeriesCarriesItsRule` (**pin** of rulings 12–13 together): Until Wed 21, Weekly, `After(4)` → draft all-day Mon 19 → Thu 22 with `COUNT=4`.
- `CalendarEditorTest.aMultiDayAllDayEventIsWrittenWithItsExclusiveEnd`: save that draft → the writer receives `AllDay(Mon 19)`/`AllDay(Thu 22)`.
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventFormTest*' --tests '*CalendarEditorTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** per the Interfaces; `draft`'s KDoc loses "new events can't span days" for all-day and keeps it for timed.
- [ ] **Step 3: The gate and commit** — `EditorScreenshotTest.locked()` is an all-day span (Grandma visiting, 28 Sep – 1 Oct), which now unlocks: move its sample to a timed span (Mon 28 Sep 18:00 → Wed 30 Sep 10:00) so `editor_locked_` still shows the locked line. Predicted: `editor_locked_` only (its line's dates and times); look at it; the gate.
```bash
git add capability/calendar
git commit -m "Let an all-day event run until a later day, and let an all-day event over several days have its dates changed"
```

---

### Task 6: The sheet: header, title, sections, Time beside Length, the chips' words, the footer (spec §4.8, §6.4; rulings 2, 5, 9, 10, 15; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/.../ui/EventEditorSheet.kt` (`Header`, `Section`, `DaySection`, `TimeSection`, `LengthSection` into one `WhenRow`, `Footer`), `ui/Pickers.kt` (the date picker's ‹ › to `HhRoundButton`; `RoundButton` goes), `ui/CalendarType.kt` (`editorTitle` 35 sp W700, `editorSummary` 15 sp, `editorPadding*`, `sectionGap` 8, `editorBodyGap` 16, `timeLengthGap` 27; `DISABLED_ALPHA`/`pageButton*` go if unused)
- Test: `ui/EventEditorSheetTest.kt`, `ui/PickersTest.kt`, `ui/EditorScreenshotTest.kt`

**Interfaces:**
- Consumes: `HhFooterButton`, `ButtonSize.Footer` (Task 3); `HhRoundButton`.
- Produces: `internal const val PICK_A_DATE = "Pick a date"`, `PICK_A_TIME = "Pick a time"`, `ADD_A_TITLE_FIRST = "Add a title first"`; the footer's Save tagged `editor_save` (its reason `editor_save_reason`); `editor_time_length` on the shared row.

- [ ] **Step 1: The failing tests** (`EventEditorSheetTest`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `theHeaderIsTheHandoffs`: "Add event" heading at 35 sp; the summary 15 sp, 4 dp below it; the close button 48 dp, top-aligned with the title.
- `theChipsSayPickADateAndPickATime` (note 34): `day_pick` reads "Pick a date" with `calendar_month`; `time_pick` "Pick a time" with `schedule`; no "…" anywhere in the sheet's text.
- `timeAndLengthSitSideBySide`: a timed event → the "Length" label's left ≥ Time's last chip's right + 27 dp, the two labels level (±1 dp); all day → no Length section.
- `saveIsDisabledUntilThereIsATitle` (extended, note 36): empty title → `editor_save` not enabled, `editor_save_reason` "Add a title first" above it; a title typed → enabled, no reason node.
- `anEndBeforeTheDayHasNoFooterReason` (**pin** of spec §6.4): a title, Ends before the day → Save disabled, no `editor_save_reason`.
- `theFooterIsFullWidthAndSixtyOneHigh`: `editor_save` 61 dp, as wide as the body; an edit → Delete on its left, Save filling the rest.
- `theFooterAndItsReasonStayAboveTheKeyboard` (Review Focus 5; extends `saveStaysAboveTheKeyboard`): a 320 dp keyboard, empty title, at font scale 1.0 and 1.3 → `editor_save` and `editor_save_reason` both above the keyboard's top.
- `PickersTest.thePickersPagesButtonsAreTheSharedRoundButton`: `date_prev`, `date_next` 48 dp, `Role.Button`, named "Earlier weeks"/"Later weeks"; `pickDateOpensTheDatePickerAndAPickSetsTheDay` keeps passing.
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventEditorSheetTest*' --tests '*PickersTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** per Units: the header, the body gaps, `Section`'s label-to-chips 8; one `Row` for Time and Length (ruling 10); the footer `HhFooterButton(…, tag = "editor_save", icon = CHECK or REFRESH, reason = ADD_A_TITLE_FIRST.takeIf { form.title.isBlank() })` with Delete (`ButtonSize.Footer`) beside it on an edit. The pickers' page buttons `HhRoundButton(Icons.CHEVRON_LEFT, "Earlier weeks", …, Modifier.testTag("date_prev"), on = ButtonSurface.Surf)`.
- [ ] **Step 3: Images** — predicted prefixes `editor_` (all), and `detail_` only if the detail sheet shares a changed dimen (it shouldn't: stop if it moves). Against 4a and note 36.
```bash
git add capability/calendar
git commit -m "Redraw Add event's header, sections and footer, put Time beside Length, and say Pick a date and Pick a time"
```

---

### Task 7: Who for a child: locked chips and the line (spec §4.8; rulings 7, 8; Review Focus 4)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/.../ui/EventEditorSheet.kt` (`WhoSection`), `CalendarPermissions.kt` (`cannotAddForOthers`), `ui/CalendarType.kt` (the line's style), `ui/CalendarDimens` in `CalendarType.kt` (the line's gaps)
- Test: `ui/EventEditorSheetTest.kt` (`aSignedInChildsOtherWhoChipsAreDisabledAndATapExplains`, `whoChipsAreARadioGroupAndAChildsOthersSayWhyNot`), `CalendarPermissionsTest.kt:68`, `CalendarEditorTest.kt:561, 574, 722`, `ui/EventEditorHostTest.kt:298`, `ui/EditorScreenshotTest.kt`

**Interfaces:**
- Consumes: `HhChoiceChip(locked)` (Task 3).
- Produces: `fun cannotAddForOthers(name: String): String = "$name can add events for $name only."`; the line tagged `editor_who_only`.

- [ ] **Step 1: The failing tests** (each UI test ends with `assertClickablesHaveRoleAndLabel()`)
- `aChildsOtherChipsAreLockedWithTheLine` (replaces `aSignedInChildsOtherWhoChipsAreDisabledAndATapExplains`): Mia signed in as a child → Family, Alex, Sam locked (no fill, `lock` shown), Mia's chip filled in her colour with the check; `editor_who_only` "Mia can add events for Mia only." under the chips with the `lock` icon.
- `aTapOnALockedChipExplainsAndChangesNothing` (Review Focus 4): tap Alex → `onRefusedWho` once, `form.who` still Mia.
- `aChosenLockedChipIsDrawnChosenWithItsLock` (Review Focus 4): an edit of an event for Alex with Mia signed in → Alex's chip `assertIsSelected`, filled in Alex's colour, the lock shown; Mia's chip unlocked.
- `lockedChipsSayWhyNot` (keeps `whoChipsAreARadioGroupAndAChildsOthersSayWhyNot`): locked chips' state "not available", click label "find out why"; the group still a radio group.
- `anAdultSeesNoLine`: Alex signed in → no `editor_who_only`, no locked chip.
- `CalendarPermissionsTest`: `cannotAddForOthers("Mia") == "Mia can add events for Mia only."`; the `CalendarEditorTest` and `EventEditorHostTest` toasts expect it.
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventEditorSheetTest*' --tests '*CalendarPermissionsTest*' --tests '*CalendarEditorTest*' --tests '*EventEditorHostTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — `WhoSection` passes `locked = childOnly != null && person.id != childOnly` in place of `enabled`; `busy` keeps `enabled = !busy`. The line per Units under the `FlowRow` while `childOnly != null`.
- [ ] **Step 3: Images** — predicted `editor_child_`; against 4a and note 33.
```bash
git add capability/calendar
git commit -m "Lock a child's other Who chips with a padlock and say under them who the child can add events for"
```

---

### Task 8: Repeats: More options, the Ends row, After N times (spec §4.8; rulings 11, 12; Review Focus 3, 5)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/.../ui/EventEditorSheet.kt` (`RepeatsSection`, `EndsSection`), `EventForm.kt` (`moreRepeats`, `showMoreRepeats()`), `ui/CalendarType.kt`
- Test: `ui/EventEditorSheetTest.kt` (`aNewEventOffersRepeatsAndEndsOnlyWhenItRepeats`, `choosingARepeatScrollsEndsIntoView`, `anEndBeforeTheDayScrollsItsLineIntoView`, `anEndBeforeTheDaySaysSoAndSaveWaits`), `EventFormTest.kt`, `ui/EditorScreenshotTest.kt`

**Interfaces:**
- Consumes: `RepeatEnd`, `RepeatChoice.COMMON`/`MORE`, `fewerTimes`/`moreTimes`, `afterTimes` (Task 4); `HhFieldError` (Task 3); `Icons.REMOVE`, `Icons.ADD`.
- Produces: in `EventForm`, `var moreRepeats: Boolean` (private set, false) and `fun showMoreRepeats()`; `internal const val MORE_OPTIONS = "More options"`, `ON_A_DATE = "On a date"`, `FEWER_TIMES = "Fewer times"`, `MORE_TIMES = "More times"`; tags `repeat_more`, `ends_never`, `ends_pick`, `ends_after`, `ends_fewer`, `ends_more`, `ends_error`.

- [ ] **Step 1: The failing tests** (`EventEditorSheetTest`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `repeatsShowsTheCommonThreeAndMoreOptions` (note 35): `repeat_Never`, `repeat_Weekly`, `repeat_Fortnightly`, `repeat_more` ("More options", `Role.Button`, `expand_more`); no `repeat_Daily`, `repeat_Monthly`, `repeat_Yearly`, no Ends.
- `moreOptionsOpensInPlaceAndStaysOpen`: tap `repeat_more` → `repeat_Daily`, `repeat_Monthly`, `repeat_Yearly` shown under the first row, `repeat_more` gone; choosing Never keeps them shown.
- `theEndsRowShowsOnlyWhileItRepeats` (replaces `aNewEventOffersRepeatsAndEndsOnlyWhenItRepeats`): open, Weekly → `ends_never`, `ends_pick` ("On a date"), `ends_after` ("After 10 times"); Never → no Ends.
- `afterNTimesShowsItsCountButtons`: tap `ends_after` → selected, `ends_fewer` and `ends_more` shown (named "Fewer times", "More times"); `ends_more` → "After 11 times"; another end chosen → no count buttons.
- `theCountButtonsStopAtTheirEnds` (Review Focus 3): at 2 `ends_fewer` not enabled; at 99 `ends_more` not enabled (driven through the form).
- `theSheetScrollsOnlyOnceMoreOptionsOpens` (Review Focus 5): the sheet at 613 × 800 dp, Mia signed in (the line), empty title (the reason) → the body's `ScrollState.maxValue == 0`; after `repeat_more` and Weekly → `> 0`.
- `openingMoreOptionsBringsEndsIntoView` (Review Focus 5; replaces `choosingARepeatScrollsEndsIntoView`): at 613 × 520 dp, `repeat_more` then Weekly → `ends_after` displayed.
- `anEndBeforeTheDaySaysSoAndSaveWaits` and `anEndBeforeTheDayScrollsItsLineIntoView` keep passing, `ends_error` now an `HhFieldError`.
- `EventFormTest.moreOptionsIsOnlyForANewEvent`: an edit → `showMoreRepeats()` leaves `moreRepeats` false.
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventEditorSheetTest*' --tests '*EventFormTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 11–12: `RepeatsSection` draws `COMMON` and the More options chip, or `COMMON` then `MORE` as a second `FlowRow` once `form.moreRepeats`; the Ends row inside that open area while `repeat != Never`; the count buttons `HhRoundButton(Icons.REMOVE, FEWER_TIMES, form::fewerTimes, …, enabled = times > MIN_TIMES, on = ButtonSurface.Bg)` and the `ADD` one; the `bringIntoView` on Ends as today.
- [ ] **Step 3: Images** — predicted `editor_repeats_` (its sample opens More options and chooses Weekly, After 10 times); new `editor_more_options_dark` (open, Never chosen). Against 4a and note 35.
```bash
git add capability/calendar
git commit -m "Keep Repeats to three choices with More options for the rest, and let a series end after a number of times"
```

---

### Task 9: The Until chip and the unlocked all-day edit (spec §2.1; rulings 13, 14; Review Focus 1, 2)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/.../ui/EventEditorSheet.kt` (`TimeSection`'s Until chip, the `EditorPicker.Until` layer, the Until error line; `LockedDates` only for timed spans)
- Test: `ui/EventEditorSheetTest.kt` (`aLockedMultiDayEditShowsOneLineInPlaceOfDayTimeAndLength` → timed), `ui/EditorScreenshotTest.kt`

**Interfaces:**
- Consumes: `until`, `offersUntil`, `chooseUntil`, `untilBeforeStart`, `untilLabel`, `UNTIL_TOO_EARLY`, `EditorPicker.Until` (Task 5); `HhFieldError` (Task 3).
- Produces: tags `time_until`, `until_error`.

- [ ] **Step 1: The failing tests** (`EventEditorSheetTest`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `untilShowsOnlyForAllDay`: all day → `time_until` "Until" with `date_range`, after `time_pick`; a time picked → no `time_until`.
- `untilOpensTheDatePickerAndAPickSetsTheLastDay`: tap `time_until` → `onPicker(EditorPicker.Until)`; with the picker shown, pick Thu 22 → `form.until == Thu 22`, the chip "Until Thu 22 Oct", the summary shows the range; the date picker opens on Until's date, or the day when none.
- `anUntilBeforeTheDaySaysSoAndSaveWaits` (Review Focus 1): day Wed 21, Until Tue 20 → `until_error` "The last day is before the first day. Pick a later one.", Save disabled, no footer reason (a title is set).
- `anAllDaySpanEditShowsItsUntil` (Review Focus 2): the half term edit → Day, Time and `time_until` "Until Fri 23 Oct" shown; no `editor_locked_dates`.
- `aTimedSpanEditStillShowsOneLine` (renamed from `aLockedMultiDayEditShowsOneLineInPlaceOfDayTimeAndLength`): Mon 22:00 → Wed 09:00 → `editor_locked_dates`, no Day chips.
- `openingUntilClosesTheKeyboard` (extends `openingAPickerClosesTheKeyboardButAChipDoesNot`).
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventEditorSheetTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — the chip in `TimeSection` while `form.offersUntil` (`Role.Button`, selected while `until != null`); the `EditorPicker.Until` branch a `DatePickerCard(selected = form.until ?: form.day, onPick = { form.chooseUntil(it); … })`; `HhFieldError(UNTIL_TOO_EARLY.takeIf { form.untilBeforeStart }, "until_error")` under the Time row.
- [ ] **Step 3: Images** — predicted: the all-day samples gain the Until chip — refine from the compare report among `editor_empty_`, `editor_keyboard_`, `editor_child_`, `editor_failed_`, `editor_repeats_`, `editor_more_options_`; new `editor_until_dark`, `editor_until_light` (Mon 19 – Thu 22, the picker closed), `editor_span_edit_dark` (the half term edit). Against 4a, §2.1 and note 31.
```bash
git add capability/calendar
git commit -m "Add the Until chip so an all-day event can run over several days, and let such an event's dates be changed on the wall"
```

---

### Task 10: The emulator walk-through on `culvery-5`

**Review:** the controller, with the user at the emulator. (The workflow runs `simplify` over the branch first, then the gate.)

**Rules.** As Plan 9a's Task 15: only the `culvery-5` AVD, started with `-no-snapshot-save -camera-front emulated` (check `adb -s "$S" emu avd name`); it holds a debug build, so `install -r` of the debug APK; never uninstall; never a Gradle install task while a device is connected; the user types every PIN; put back every setting changed.

- [ ] **Step 1: The AVD and the build** — `./gradlew :app:assembleDebug`; `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2: Walk the checklist** (one line each)
1. Every right-hand sheet (event detail, Add event, a person, Favourites) is the wider sheet with the darker scrim and the edge.
2. Add event: the header, Pick a date and Pick a time without "…", Time beside Length, the footer greyed with "Add a title first" until a title is typed.
3. Signed in as the sample child: the other Who chips locked with the padlock, the line under them, a tap on one says "Mia can add events for Mia only." (the sample's child name).
4. Repeats: More options opens Every day, Every month, Every year and Ends; After 10 times with Fewer and More; the sheet scrolls only once open; a weekly event ending after 3 times shows 3 times on the week.
5. Until: All day, Until in three days → one bar across the week (9b's lane), "Day 2 of 3" on Home on its second day (set the AVD's date if needed, and put it back); open it again → Until editable; move the day → Until moves with it.
6. A timed event over midnight opened from the phone's calendar (or the fake's) still shows the locked dates line.
- [ ] **Step 3: The fault loop** — each fault a failing test, the fix, the gate, a commit naming it, `install -r`, re-checked.
- [ ] **Step 4: Put back** every setting; `adb -s "$S" emu kill`.
- [ ] **Step 5: Record** under "Plan 9c-1 (Redesign: Add event): emulator walk-through".
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9c-1's emulator walk-through"
```

---

### Task 11: On the tablet

**Review:** the controller with the user. **Needs the SM-T510 on the cable.** 4e's tablet rules (save and restore every setting changed, signed releases with `adb install -r`, never uninstall, the names check before any commit).

- [ ] **Step 1: Install the release** — signed per `docs/setup/release.md`; keep `mapping.txt` in `culvery-notes/mappings/`.
- [ ] **Step 2: At the wall** (the user): Add event at arm's length; a real multi-day all-day event made on the wall shows in Google Calendar on a phone across its days; a series ending after N times shows N times there; the child's locked chips read as locked, not broken.
- [ ] **Step 3: Record** — "Plan 9c-1 (Redesign: Add event): on the tablet": what read and what didn't; no names.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9c-1 on the tablet"
```

---

### Task 12: Final review, merge, install

- [ ] **Step 1: The review** — opus, the whole branch against spec §2.1, §4.8, §6.4 and this plan; one fix wave, each fix with its failing test first and the gate.
- [ ] **Step 2: Merge** — on the user's say: `git merge --ff-only plan-9c-forms` on `main`; the gate on main; never push. 9c-2 then branches from it.
- [ ] **Step 3: Install** — the release from the merged `main` on the tablet, `install -r`.

---

## Spec coverage (9 design → tasks)

| Spec | Task |
|---|---|
| §2.1 multi-day: creating them in Add event | 5, 9 |
| §4.8 the sheet from the right | 2, 6 |
| §4.8 title field, section labels, chips | 2, 6 (ruling 2: chips stay 48 dp) |
| §4.8 a child's locked Who chips and the line (note 33) | 3, 7 |
| §4.8 "Pick a date", "Pick a time" (note 34) | 6 |
| §4.8 Time and Length side by side | 6 |
| §4.8 Repeats with More options and the Ends row, scrolling once open (note 35) | 4, 8 |
| §4.8 the footer primary; disabled outline with the reason above (note 36) | 3, 6 |
| §6.4 one disabled style; reason optional | 3, 6 |
| §5 pickers and detail sheet unchanged but for shared tokens | 2, 6 (ruling 15) |
| §7 images looked at; CopyScanTest; TalkBack | every UI task |
| §4.11, §4.12 | 9c-2 |
