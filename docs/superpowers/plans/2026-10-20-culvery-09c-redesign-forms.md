# Culvery — Plan 9c-1: The redesign, the shared form pieces and Add event (with multi-day and "After N times") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–10 are subagent tasks; Task 11 is the controller on the `culvery-5` AVD with the user; Tasks 12–13 need the user and the son's SM-T510.
>
> **9c is split in two.** This plan, **9c-1**, covers the shared sheet and form pieces and the Add event sheet, including creating multi-day all-day events and repeats that end after N times. **9c-2** (`docs/superpowers/plans/2026-10-20-culvery-09c2-redesign-wizard-settings.md`) covers the setup wizard and the Settings pages. It starts from 9c-1 merged and reuses its shared pieces.

**Goal:** The Add event sheet looks and behaves as the October redesign draws it:
- the wider sheet on the right;
- section labels;
- a child's locked Who chips, with the line saying why;
- "Pick a date" and "Pick a time";
- Time and Length side by side;
- Repeats with "More options", which opens Every day, Every month, Every year and an Ends row (Never, On a date, After N times);
- the full-width footer, which says "Add a title first" while the event can't be saved.

An all-day event can be made to last up to 60 days with an "Until" chip. A child's events are one-off.

**Architecture:**
- **Shared pieces first, in `:core:ui`:** two icons; the sheet's width, scrim and edge; the section label's tracking; the footer button's reason line; `HhStatusLine` widened to be the error under a field; the locked chip; a round button that repeats while held.
- **The repeat end** becomes a type in `:capability:calendar`. `RepeatEnd` is never, on a date, or after N times. `recurrenceRule` writes it as `UNTIL` or `COUNT`, and the fake provider's expander reads `COUNT`.
- **`EventForm`** gains the Until date for new all-day events and a rule that only people with `calendar.event.create` may repeat.
- **The sheet** (`EventEditorSheet`) is then redrawn section by section. All-day multi-day edits unlock last, together with the chip that edits them.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00, Material 3), Hilt 2.57.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-19-culvery-9-redesign-design.md`:
- §2.1 (multi-day, amended 2026-10-20: creating them is 9c's Add event);
- §4.8 (Add event);
- §5 (the pickers and the detail sheet stay as they are);
- §6.4 (one disabled style; the reason line optional);
- §7.

The drawings are the handoff `docs/design/2026-10-redesign/handoff.md` and `notes.txt` (notes 33–36). In the handoff, use the section "Add event sheet (4a)", plus "Not redesigned" for the pickers and the event detail sheet. When a value is unclear, the full HTML export is in `C:\Users\si\code\siland\culvery-notes\design-handoff-2026-10\`. Where the spec and the handoff disagree, the spec wins. Read all three with this plan.

**Previous plans (format, shared parts, rulings):** `docs/superpowers/plans/2026-10-20-culvery-09b-redesign-tabs.md` and `docs/superpowers/plans/2026-10-19-culvery-09a-redesign-home.md`. Their Units ruling, the 48 dp touch rule and the one disabled style carry over.

**Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md`: "Adding events, later" (the Until chip), and the multi-day entry under "Feature ideas".

**Builds on:** branch `plan-9c-forms` in the one checkout `C:\Users\si\code\siland\culvery`. There are no worktrees. The branch was cut from `main` at 05b1cbb, with Plan 9b merged. Work only there. Never push.

| # | Task | Review |
|---|---|---|
| 1 | Icons | sonnet |
| 2 | Shared: the sheet's width, scrim and edge; section labels' tracking | sonnet |
| 3 | Shared: the footer's reason, the error under a field, the locked chip, the held round button | sonnet |
| 4 | Repeats: the end as a type, `COUNT`, the fake's expander | **opus** (the rule written to Google; `UNTIL` and `COUNT` exclusive; the expander's window) |
| 5 | Multi-day: Until in the form for new all-day events, the 60-day cap | **opus** (exclusive ends; the day moving under Until; the cap) |
| 6 | The sheet: header, title, sections, Time beside Length, the chips' words | sonnet |
| 7 | The sheet: the footer and its reason; the pickers' round buttons | sonnet |
| 8 | A child: locked Who chips, the line, and no repeats | **opus** (the click path is the gate in the UI; the editor's gate behind it) |
| 9 | Repeats: More options, the Ends row, After N times | sonnet |
| 10 | The Until chip, and all-day spans unlocked on edit | **opus** (the unlock; restoring Until; edits keeping their span) |
| 11 | The emulator walk-through on `culvery-5` | controller + user |
| 12 | On the tablet | controller + user, **needs the SM-T510** |
| 13 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:**
1. **The icons first.** A font rebuild can stop for a download. Both icons are in the full font at 608ce00, so it shouldn't.
2. **The shared sheet next, alone.** It moves the images of the event detail, person, PIN-over-sheet, connect, Favourites, pairing and address sheets, plus every section label. Those moves should be one deliberate commit.
3. **The form pieces before any sheet task uses them.**
4. **The two models.** They land before the sheet is redrawn:
   - Task 4 is plain logic, apart from Robolectric where the test class already uses it.
   - Task 5 is for new events only. It doesn't touch `datesLocked`, so no existing test or screen changes meaning yet.
5. **The sheet's frame, then its footer** (6, 7).
6. **The sections that read the models** (8–10). The all-day unlock comes last, in Task 10, together with the chip that edits the span. No commit shows Day chips on an all-day span without its Until.

## Shared components 9c-2 reuses

- **`HhFooterButton`** (Task 3): the person sheet's Save person.
- **`HhStatusLine(icon, style, spoken)`** (Task 3): errors under their field in the person sheet, the You step and Settings › Home location.
- **`ShellTokens.sheetWidth`/`sheetScrim`** (Task 2): the person sheet.
- **`Icons.ERROR`** (Task 1).

9c-2 adds its own `person_add` and `block` icons.

## Rulings against the code

These are where the spec leaves a choice open or doesn't fit the code at 05b1cbb. Each is the plan's call unless it cites the spec or the user.

1. **Units** are as in Plan 9a:
   - dp = handoff px ÷ 1.5, rounded to whole dp/sp;
   - every 2 px edge is 1 dp;
   - the SM-T510 reads 240 dpi.
   The values are in **Units** below.
2. **Touch targets stay 48 dp** (9a ruling 2). The handoff draws 64 px (43 dp) chips; they stay the shared 48 dp `HhChoiceChip`, with its padding and 16 sp label unchanged (the user, 2026-10-20). The selected chip's weight 700 and the 23 px (15 sp) label go to the end-of-v1 design review (see **Notes**).
3. **The shared sheet** (the user's brief, 2026-10-20) is one task (Task 2), because every right-hand sheet moves. It changes three things:
   - `ShellTokens.sheetWidth` goes from 600 to 613 dp (920 px);
   - `sheetScrim` goes from 0x8C to 0x99 (.6);
   - the left edge goes from `line` to `surf3`.
   The sheets that move are Add event, the event detail sheet, the person sheet, the PIN pad drawn over a sheet, the connect sheet's scrim, Favourites, Hue pairing and both address sheets. Their contents are "not redesigned" (handoff); only the frame changes. The editor's and the person sheet's own `.width(ShellTokens.sheetWidth)` on their outer `Box` stays, because it sizes the picker and PIN layers drawn over the sheet. "From the right" is the sheet's position. The handoff's slide-in motion isn't built; no sheet animates today.
4. **Section labels** take the handoff's tracking: `HhType.sectionLabel` letter-spacing goes from 0.5 to 1 sp (1.5 px). Size and weight are unchanged (13 sp W700). The style is shared, so it changes in Task 2 with the sheet. The tray label, Display's and Door's settings, and the Door tab's day labels use it too.
5. **The footer button is `ButtonSize.Large` (60 dp), not a new size.**
   - The handoff's 92 px is 61 dp, and one dp doesn't earn a third button size. Its 28 px label (19 sp) against Large's 17 sp goes to the design review.
   - `HhFooterButton` is `HhButton` at Large, filling the width. It adds a reason line 8 dp above the button while it is disabled and has a reason.
   - Spec §6.4 makes the reason optional. Only **Add event**'s "Add a title first" has one, shown while a new event's title is blank. "Save changes" on an edit shows no reason.
   - The disabled look stays the shared 1 dp `surf3` outline with a `mute` label (9a).
   - TalkBack hears the reason as the button's state: "Add event, Add a title first, disabled".
   - The editor's Delete stays `DeleteButton` at Large, beside it, so the event detail sheet's Delete doesn't move.
6. **The error under a field is the widened `HhStatusLine`, not a new composable.** Today it is a danger line with the `info` icon and a polite live region. It gains:
   - an `icon` parameter (`Icons.ERROR`, filled, for field errors);
   - a `style` parameter (`ControlType.fieldError`, 14 sp W600, for field errors);
   - `spoken: Boolean = true`.
   With `spoken = false` it is hidden from accessibility and isn't a live region. A text field passes `spoken = false` for its own error, which the field already carries in `semantics { error(…) }`, so TalkBack hears it once (9c-2 uses this).
   An error under a field is composed only while it shows, 8 dp under its field, so an empty error takes no height. `HhMessageLine` keeps its one job, the always-composed live line, and stays as the Ends row's line.
7. **The locked chip** (note 33) is `HhChoiceChip(locked = true)`. It draws:
   - no fill;
   - a 1 dp `surf3` edge;
   - the `lock` icon before `leading` (the colour dot stays);
   - the label in `mute`;
   - no 38 % fade.
   It keeps the disabled chip's semantics ("not available", "find out why") and stays tappable, so a tap still explains. A locked chip that is selected is drawn selected, with the lock. That happens when an adult tagged the child's event and the child is editing it.
8. **One `locked` flag for both paint and click** (security review). The flag is `locked = childOnly != null && person.id != childOnly`.
   - The click does this: `busy` → nothing; `locked && !selected` → `onRefusedWho()`; `locked && selected` → nothing (as today; no toast); otherwise `chooseWho`.
   - `enabled` stays at its default, so a save in flight doesn't fade every chip or call them "not available".
   - The UI isn't the gate: `CalendarEditor`'s `mayCreateFor`/`mayRetag` still are. If the child's session ends mid-sheet, the save asks for a PIN and the editor refuses.
9. **The child's line** (note 33) sits under a child's Who chips: the `lock` icon at 15 dp, then "`{name} can add events for {name} only.`" in 13 sp `mute`.
   - The name is the signed-in person's own; with no name there is no line.
   - The refusal toast takes the same sentence. `cannotAddForOthers(name)` becomes "`{name} can add events for {name} only.`" (handoff wording), replacing "…can only add events for themselves.". This applies everywhere it is used: the editor's tap on a locked chip and `CalendarEditor`'s create and retag refusals.
10. **A child's events are one-off** (the user, 2026-10-20).
    - With `childOnly` set, the sheet shows no Repeats, no More options and no Ends. On the wall a series can't be deleted (recurring events are read-only, `Editability.kt:16`), so a child can't make one that nobody can remove from the wall.
    - **The UI isn't the gate:** `CalendarEditor.create` refuses a draft with a `recurrence` unless `calendar.event.create` is granted (`mayRepeat`). Admin and Adult hold that permission; a Child holds only `create.self`. The refusal is the toast "Ask a grown-up to add an event that repeats." and nothing is written or queued.
    - `EventForm` takes `repeats: Boolean` (false for a child) and `canRepeat = mode == New && repeats`.
11. **No ellipses on the chips** (note 34):
    - "Pick date…" becomes "Pick a date";
    - "Pick time…" becomes "Pick a time";
    - Ends' "On a date…" becomes "On a date";
    - the follow-ups' "Until…" becomes "Until" (the user, 2026-10-20).
    A picked value still replaces the label ("Sat 26 Sep", "18:00").
12. **Time and Length side by side** (handoff 4a) share one `Row`. Time's section is at its natural width; Length's is `weight(1f)`, so its chips wrap within it. They are 27 dp apart. Length shows only for a timed event, as today. Until (ruling 15) sits in Time's section after "Pick a time", only while All day is chosen.
13. **Repeats** (note 35; the user's brief: Every day moves under More options, it isn't removed).
    - **The first row** is Never, Every week, Every 2 weeks and "More options" (`Role.Button`, with `expand_more` after the label).
    - **A tap opens a second row in place:** Every day, Every month, Every year. While the event repeats, the Ends row (Never, On a date, After N times) follows it.
    - **It opens and never closes.** The chip goes once tapped, and accessibility focus moves to `repeat_Daily`, so TalkBack isn't left on a node that went.
    - **The open area also shows by itself** whenever the form holds a choice that lives in it:
      `moreRepeats = opened || repeat in RepeatChoice.MORE || repeatEnd != RepeatEnd.Never`.
      So a Daily repeat opens it, and so does an end set from code or a test.
    - **The body scrolls only once More options is open** (Review Focus 5).
14. **After N times** (the user, 2026-10-20) is `RepeatEnd.After(times)`, written as `;COUNT=n`.
    - `UNTIL` and `COUNT` never both appear, because they are one `RepeatEnd` value.
    - **The count** runs from 2 to 52 and starts at 10. `After`'s constructor requires `times in MIN_TIMES..MAX_TIMES`, and `chooseAfter(times)` clamps a value before building it.
    - **The chip** reads "After 10 times". A tap while it isn't chosen gives `After(DEFAULT_TIMES)`; a tap while it is chosen does nothing; leaving it and coming back starts at 10 again.
    - **While chosen, two round quiet buttons follow it**, `remove` ("Fewer times") and `add` ("More times"). They step by 1, are each disabled at their end, and **repeat while held**: one step for a tap, then held from 500 ms, a step every 100 ms until let go.
    - **`chooseEnd` is ignored** unless the event repeats (`canRepeat && repeat != Never`). Changing between two repeat choices other than Never keeps the end.
    - **Google needs no change.** It reads instances with `singleEvents=true`, and `repeatsLabel` already treats `COUNT` as plain, so there are pins only.
    - **The fake's `RecurrenceExpander`** applies the count from the series' first occurrence, before the read window's last day. A phone-written `COUNT=1` gives one occurrence. `COUNT` that is 0, negative or not a number is ignored. With both `UNTIL` and `COUNT` (phone data), whichever ends first wins.
15. **Multi-day creation** (the user, 2026-10-20; spec §2.1).
    - **The chip and picker:** while All day is chosen, an "Until" chip (`date_range`) opens the date picker as a new `EditorPicker.Until`. The end date of a series stays `EndDate`.
    - **The draft** becomes `AllDay(day)` → `AllDay(until + 1)`, exclusive.
    - **One day:** picking the start day itself clears Until.
    - **Changing Day** moves Until by the same number of days, so the event keeps its length.
    - **Choosing a time** sets Until aside, and choosing All day again with the same Day puts it back (security review I5). Multi-day timed events can't be created on the wall; they stay on the phone.
    - **The guards:** `chooseUntil` does nothing unless `offersUntil`. `untilBeforeStart` and `untilTooLate` are false unless `offersUntil`, so a hidden Until can't hold Save back.
    - **The chip's label** reads "Until Thu 22 Oct" once picked. The cap keeps the label inside a year's ambiguity.
    - **Repeats stay offered** for an adult's multi-day event.
16. **Until's limits** (the user, 2026-10-20 for the cap):
    - A picked Until before the day shows "The last day is before the first day. Pick a later one." under the Time row, and Save waits.
    - An event lasts at most 60 days. Until = day + 59 is allowed (60 days); day + 60 shows "An event can last up to 60 days. Pick an earlier last day." and Save waits.
    The line is composed only while it shows (ruling 6). It isn't a live region, so while it shows, the Until chip's state description carries the same sentence; TalkBack hears it on the chip it just used.
17. **All-day spans unlock on edit** (the user's brief), in Task 10.
    - `datesLocked` becomes `!(start is AllDay && end is AllDay) && spansDays`, exactly. So a timed span, or a mixed all-day/timed record from a provider, stays locked.
    - An all-day multi-day edit opens with Day on its first date and Until on its last (exclusive end − 1), editable.
    - `lockedDatesLabel` is unchanged for the spans that stay locked.
    - An untouched all-day edit still keeps its own start and end exactly (`keepsOwnSpan`).
18. **The calendar's own `RoundButton`** (Pickers.kt) goes. The date picker's ‹ › become `HhRoundButton(on = ButtonSurface.Surf, iconSize = ButtonTokens.icon)`: the shared disabled look (spec §6.4) at today's 24 dp icon. `CalendarDimens.DISABLED_ALPHA`, `pageButton` and `pageButtonIcon` go; `pageButtonGap` stays (`Pickers.kt:135`). The pickers are otherwise not redesigned.
19. **Length chips keep full words** ("30 minutes", "1 hour", "2 hours") although the handoff draws "30 min". `lengthLabel` is also the spoken and summary wording ("1 hour 30 minutes"), and the app spells units out. This is the plan's call, for the design review.
20. **The form stays plain in-memory state.** That covers `until` and the stashed Until, `repeatEnd` and `moreRepeats`. None of it goes in `rememberSaveable`. A system font-size change recreates the activity and drops the sheet, as it does today; the user retypes. `moreRepeats` stays out of `Answers` and `edited`.
21. **The shell variable for prediction lists is `$LISTS`** (`C:/t9`). It is a plain shell variable for list files and is never exported.

## Notes for the end-of-v1 design review

These stand as the plan has them (the user, 2026-10-20) and go on the end-of-v1 review list:
- chips stay 48 dp (16 sp, weight 600 when selected);
- one 59 × 33 dp switch everywhere, the Lights room card included (9c-2);
- the disabled fade stays 38 %;
- one 37 sp page title for wizard steps and Settings pages (9c-2);
- the Settings pane stays 720 dp and the wizard column is 640 dp (9c-2);
- the Until chip reads "Until" with no ellipsis.

Also for that review (this plan's calls):
- the footer at Large's 60 dp with a 17 sp label (the handoff draws 61 dp with 19 sp);
- full-word Length chips;
- no slide-in motion on sheets.

## Units (converted once, px ÷ 1.5)

| Where | Handoff px | dp / sp |
|---|---|---|
| Sheet | 920 wide; scrim .6; 1 px `s3` left edge; padding 44/48/40; gap 24 | 613; `0x99000000`; 1 `surf3`; top 29, sides 32 (`editorPaddingH`, new; the detail sheet keeps `sheetPaddingH` 30), bottom 27; 16 between blocks (`sheetGap`) |
| Sheet header | title 52/700; summary 23 muted, 6 under; close 72 | 35 sp W700; 15 sp `mute`, 4 under; 48 (`HhCloseButton`) |
| Title field | 96, r 26, 31/600 | `HhTextField` as is: 64, r 18, 22 sp W600 |
| Section label | 19/700, ls 1.5, 12 above chips | 13 sp W700, ls 1 sp; 8 |
| Chips | 64, r 32, 23 px, gap 12 | 48 (ruling 2), r 24, 16 sp W600, gap 8 |
| Locked chip | 2 px inset `s3`, lock 28, dot 14 | 1 `surf3` edge, lock 20 (`chipIcon`), dot 12 (`chipDot`) |
| Child's line | 20 muted, lock 22, gap 8 | 13 sp `mute`, lock 15, gap 5; 8 under the chips |
| Time · Length | 40 apart | 27 |
| Footer | 92, r 46, 28/700, check 36, gap 12; reason above | `ButtonSize.Large` 60, pill, 17 sp W700, icon 24; reason 16 sp `mute`, centred, 8 above |
| Field error | 21/600 danger, `error` 26 filled, gap 8 | `HhStatusLine(icon = ERROR, style = fieldError)`: 14 sp W600 `danger`, 17 filled, gap 5; 8 under the field |
| Count stepper | — | `HhRoundButton` 48, icon 24, 8 from the chip and each other; hold 500 ms, then every 100 ms |

**Height budget** at 613 × 800 dp with More options closed. The tallest closed sheet is an adult's: padding 56 + header 71 + title 64 + Who 69 + Day on two rows 130 + Time 74 + Repeats 74 + footer with its reason 84 + six gaps of 16 (96) ≈ 718 dp. A child's sheet has the 31 dp line but no Repeats (ruling 10), so it comes to about 659 dp. Task 6 asserts at least 24 dp to spare in both. A shown Until line (19 dp, inside the Time column, no extra gap) still fits.

## Global Constraints

**From the spec, verbatim:**

- §2.1 "**Multi-day events are folded into 9b and 9c** (the user, 2026-10-20): the week's lane in 9b, creating them in 9c's Add event, "Day 2 of 3" on Home's Today with them".
- §4.8 "The sheet from the right; the title field; section labels; chips; a child's Who chips locked with the explanation line (note 33); "Pick a date" and "Pick a time" without ellipses (note 34); Time and Length side by side; Repeats with "More options ▾" expanding to Every month, Every year and the Ends row, scrolling only once open (note 35); the footer primary, and **disabled as a 2 px outline with a muted label and the reason above it** ("Add a title first") (note 36)."
- §6.4 "**One disabled button style** (the outline) everywhere … The reason line above it is **optional**: shown where the handoff gives one (Add event's "Add a title first"), not invented for every disabled button."
- §5 "Event detail sheets, the date and time pickers … unchanged except the shared tokens and button styles they already use."
- §7 "Every changed screen's Roborazzi baselines are re-recorded and each new image looked at against the handoff"; "Copy changes go through CopyScanTest; TalkBack labels follow the new copy."

**Copy** (verbatim, curly apostrophes in code):
- **Sheet:** "Add event", "Change event".
- **Section labels:** "Who", "Day", "Time", "Length", "Repeats", "Ends".
- **Day and Time chips:** "Pick a date", "Pick a time", "All day", "Until", "Until {EEE d MMM}".
- **Repeat and end chips:** "Never", "Every week", "Every 2 weeks", "More options", "Every day", "Every month", "Every year", "On a date", "After {n} times"; "Fewer times" and "More times" (TalkBack).
- **A child:** "`{name} can add events for {name} only.`", "Ask a grown-up to add an event that repeats."
- **Footer and errors:** "Add a title first", "The last day is before the first day. Pick a later one.", "An event can last up to 60 days. Pick an earlier last day.", "The end date is before the event’s day. Pick a later one." (kept), "Save changes", "Try again".

**Privacy:** nothing new is stored or logged. A refusal toast names only the signed-in person, as today.

**From the house rules** (as Plans 9a and 9b; binding):

- **Build:**
  - The package root is `uk.co.siland.culvery`. `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only.
  - **No new library; never change a version.**
  - No deprecated API. The one opt-in is `ExperimentalMaterial3Api` in `HhPercentSlider`.
- **Test gate:**
  - Run `./gradlew testDebugUnitTest verifyRoborazziDebug`. Never plain `test`, `check` or `build`. Never a Gradle `install…` task. No subagent touches a device.
  - **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`** for Gradle: a long temp path pushes Robolectric's database paths past Windows' MAX_PATH (SQLITE_CANTOPEN).
  - `$LISTS` below is `C:/t9`, a shell variable for prediction lists only, never exported.
- **Tests that can fail:**
  - Every new test fails before its change. A test that passes before its change is labelled **pin**.
  - Timer tests run on virtual time. UI timers run under `compose.mainClock`, checked one frame before the boundary and at the boundary plus one frame.
  - Colour checks read a pixel inside a fill but clear of its content, through a shared `pixelAt(node, dx, dy)` test helper in core/ui's test sources (created in Task 3), never the centre of something with a label.
  - Tokens aren't asserted against the literals that set them.
- **Every UI task ends the same way:**
  1. Run `compareRoborazziDebug`.
  2. Open each `*_compare.png`, and each new image, against the handoff section the task names.
  3. Record.
  4. Run the baseline check, with the predicted names or prefixes in `$LISTS/expected-baselines.txt` / `$LISTS/expected-prefixes.txt`, checked as in Plan 9a's Global Constraints. Anything outside the prediction stops the task.
  5. Run the gate.
  6. Commit.

  Baselines live in `<module>/src/test/screenshots/`. Each task lists only its predictions and its commit line.
- **Copy and accessibility:**
  - Copy names what the user does. Use curly quotes, and "·", "›", "→", "…" as written. `CopyScanTest` passes.
  - Every changed screen's test ends with `compose.assertClickablesHaveRoleAndLabel()`. TalkBack names follow the copy.
- **Code:**
  - Icons come from `Icons`. A new one goes in `tools/fonts/icons.txt` and the subset is rebuilt from the full font at 608ce00.
  - Layout numbers are named in the module's dimens object.
  - Comments say what the code can't, in one line, with spec refs as `(9 §4.8)`. No plan task or ruling numbers.
  - No personal data: sample names only (Alex, Sam, Mia, Lounge, Kitchen, Hall, Garden, Front Door, Back Garden).
  - Files are UTF-8.
- **Commits and git:**
  - Commits are a plain sentence, no trailer. Never push.
  - **Never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **Until against the day it ends on.**
   - **Cases:**
     - Until picked, then Day moved later or earlier;
     - Until equal to the day;
     - Until before the day;
     - Until at day + 59 and at day + 60;
     - Until, then a time, then All day again.
   - **Expected:**
     - the span keeps its length when Day moves;
     - equal to the day is one day (the chip reads "Until" again);
     - before the day, and past 60 days, show their line and Save waits;
     - a time sets Until aside and All day puts it back;
     - the draft's end is always the day after the last day.
   - **Tests:**
     - Task 5: `EventFormTest.movingTheDayMovesUntilWithIt`, `untilOnTheDayItselfIsOneDay`, `untilBeforeTheDayWaitsToSave`, `untilOnTheSixtiethDayIsAllowed`, `untilOnTheSixtyFirstDayWaitsToSave`, `reChoosingAllDayRestoresUntil`, `aMultiDayDraftEndsTheDayAfterItsLastDay`;
     - Task 10: `EventEditorSheetTest.anUntilBeforeTheDaySaysSoAndSaveWaits`, `anUntilPastSixtyDaysSaysSoAndSaveWaits`.
2. **Editing a multi-day event.**
   - **Cases:**
     - an all-day half term Mon 19 – Fri 23 Oct (exclusive Sat 24) opened, nothing changed;
     - then Until moved;
     - then Day moved;
     - a one-day all-day event given an Until;
     - a timed span, and a mixed all-day/timed record, opened.
   - **Expected:**
     - the all-day one opens with Day Mon 19 and Until Fri 23;
     - unchanged, it is `unchanged` and saves nothing;
     - moving Until or Day sends TIMES only;
     - the timed and mixed ones stay locked with their one line.
   - **Tests** (Task 10): `EventFormTest.anAllDaySpanOpensWithItsFirstAndLastDays`, `anUntouchedAllDaySpanIsUnchanged`, `movingUntilOnAnEditTouchesOnlyTheTimes`, `movingTheDayOnASpanEditTouchesOnlyTheTimes`, `aOneDayEditGivenAnUntilTouchesOnlyTheTimes`, `aTimedSpanStaysLocked`, `aMixedAllDayAndTimedEventStaysLocked`; `EventEditorSheetTest.anAllDaySpanEditShowsItsUntil`.
3. **After N times at its ends, and the expander's window.**
   - **Cases:**
     - After at 2, then Fewer;
     - After at 52, then More;
     - a held More button;
     - After 5, then On a date, then Never;
     - a `COUNT=5` series read with the window ending on its 5th occurrence and on the day before;
     - a weekly `COUNT=5` series crossing the 25 Oct 2026 clock change.
   - **Expected:**
     - the count stays in 2–52 with the end button disabled;
     - holding steps once, then every 100 ms after 500 ms;
     - the rule has `COUNT=5` alone, then `UNTIL=…` alone, then neither;
     - the fake gives 5 occurrences, then 4, every one at 18:00 local.
   - **Tests:**
     - Task 4: `RecurrenceTest.aCountedSeriesWritesCountAndNoUntil`, `neverBothUntilAndCountForAnyRepeatEnd`; `EventFormTest.theCountStaysBetweenTwoAndFiftyTwo`, `changingTheEndReplacesIt`; `RecurrenceExpanderTest.theWindowEndingOnTheLastOccurrenceKeepsAllOfThem`, `theWindowEndingTheDayBeforeKeepsOneFewer`, `aCountedSeriesKeepsItsLocalTimeOverTheClockChange`;
     - Task 3: `RoundButtonHoldTest.holdingRepeatsAfterHalfASecond`;
     - Task 9: `EventEditorSheetTest.theCountButtonsStopAtTheirEnds`.
4. **A signed-in child.**
   - **Cases:**
     - Mia opens Alex's event (Alex's chip chosen) while a save is in flight;
     - Mia's session ends with the sheet open;
     - a repeating draft sent past the UI.
   - **Expected:**
     - Alex's chip is drawn selected with the lock and a tap on it does nothing;
     - the others are locked, and a tap toasts "Mia can add events for Mia only." and changes nothing;
     - busy fades nothing;
     - after the session ends, the save asks for a PIN and is refused for someone else;
     - no Repeats section, and `CalendarEditor` refuses a recurrence without `calendar.event.create`.
   - **Tests** (Task 8): `EventEditorSheetTest.aChosenLockedChipIsDrawnChosenWithItsLock`, `aTapOnALockedChipExplainsAndChangesNothing`, `busyFadesNoChip`, `aChildSeesNoRepeats`; `EventEditorHostTest.theLockedChipsFollowTheSessionRole`; `CalendarEditorTest.aChildWhoseSessionEndedCannotSaveForSomeoneElse`, `aChildCannotSaveARepeatingEvent`.
5. **The sheet's height.**
   - **Cases:**
     - More options closed at 1280 × 800 dp, for an adult with the reason line, and for a child with the line;
     - More options opened with an end chosen;
     - the keyboard up;
     - font scale 1.3.
   - **Expected:**
     - closed, nothing scrolls, with at least 24 dp to spare;
     - open, the Ends row is scrolled into view;
     - the footer and its reason stay above the keyboard at both scales.
   - **Tests:**
     - Task 6: `EventEditorSheetTest.theClosedSheetFitsWithRoomToSpare`;
     - Task 7: `theFooterAndItsReasonStayAboveTheKeyboard` (at 1.0 and 1.3);
     - Task 9: `theSheetScrollsOnlyOnceMoreOptionsOpens`, `openingMoreOptionsBringsEndsIntoView`.

The spec's own tests (§7) each have their task: CopyScanTest and TalkBack names (every task), and the images looked at against 4a (Tasks 2, 6–10).

---

## File Structure

```
core/ui/.../Icons.kt, tools/fonts/icons.txt, res/font/material_symbols_rounded.ttf (Task 1)
core/ui/.../Shell.kt (sheetWidth, sheetScrim, the edge), Type.kt (sectionLabel) (Task 2)
core/ui/.../Buttons.kt (HhFooterButton, HhRoundButton(repeatWhileHeld)), Controls.kt (HhStatusLine widened, ControlType.fieldError, HhChoiceChip(locked)) (Task 3)
capability/calendar/.../Recurrence.kt (RepeatEnd, COUNT), EventForm.kt (repeatEnd, the count) (Task 4)
provider/calendar-fake/.../RecurrenceExpander.kt (COUNT) (Task 4)
capability/calendar/.../EventForm.kt (until, the cap), ui/Pickers.kt (EditorPicker.Until) (Task 5)
capability/calendar/.../ui/EventEditorSheet.kt, ui/CalendarType.kt (Tasks 6–10)
capability/calendar/.../ui/Pickers.kt (HhRoundButton) (Task 7)
capability/calendar/.../CalendarPermissions.kt (cannotAddForOthers, mayRepeat), CalendarEditor.kt (the repeat gate), EventForm.kt (repeats) (Task 8)
capability/calendar/.../EventForm.kt (datesLocked) (Task 10)
docs/superpowers/plans/2026-09-23-plan1-followups.md (Tasks 11–13)
```

---

### Task 1: Icons

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Icons.kt`, `tools/fonts/icons.txt`, `core/ui/src/main/res/font/material_symbols_rounded.ttf` (regenerated)
- Test: `core/ui/src/test/.../IconFontTest.kt` (`NO_FILLED_FORM` if needed)

**Interfaces:**
- Produces: `Icons.ERROR = "error"` and `Icons.REMOVE = "remove"`, alphabetical in both files.
- Already there: `lock`, `expand_more`, `date_range`, `calendar_month`, `schedule`, `check`, `add`, `info`.

- [ ] **Step 1: The failing test.** Add the two constants to `Icons` and `icons.txt`. Run `./gradlew :core:ui:testDebugUnitTest --tests '*IconFontTest*'`. Expected: FAIL (the font lacks the glyphs).
- [ ] **Step 2: The subset.** Follow `tools/fonts/README.md` steps 3–4 from the blob at 608ce00:
  - `git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$LISTS/material-symbols-full.ttf"`
  - `python tools/fonts/subset.py --source "$LISTS/material-symbols-full.ttf"`

  `error` is drawn filled (ruling 6); if its filled form is missing, stop and report. Put `remove` in `NO_FILLED_FORM` if it has none.
- [ ] **Step 3: The gate and commit.** Expected: PASS, **no image changed**.
```bash
git add core/ui tools/fonts/icons.txt
git commit -m "Add the error and remove icons"
```

---

### Task 2: Shared: the sheet's width, scrim and edge; section labels' tracking (rulings 3, 4)

**Review:** sonnet.

**Files:**
- Modify:
  - `core/ui/.../Shell.kt`: `ShellTokens.sheetWidth`, `sheetScrim`, `HhSheet`'s edge colour and its KDoc.
  - `core/ui/.../Type.kt`: `HhType.sectionLabel`.
- Test:
  - `core/ui/src/test/.../ShellPartsTest.kt`.
  - `core/ui/.../KeyboardTest.kt` (**pin**, unchanged).
  - `core/access/src/test/.../ui/PinPadTest.kt`: `overASheetTheScrimCoversOnlyTheSheetAndTheCardIsCentredInIt`, `:333–336`.

**Interfaces:**
- Produces:
  - `ShellTokens.sheetWidth = 613.dp`.
  - `ShellTokens.sheetScrim = Color(0x99000000)`.
  - `HhSheet` draws its 1 dp left edge in `surf3`.
  - `HhType.sectionLabel` has letter-spacing `1.sp`.

- [ ] **Step 1: The failing tests.**
  - `ShellPartsTest.theSheetIsTheHandoffsWidth`: `HhSheet` in a 1280 × 800 box is 613 dp wide.
  - `ShellPartsTest.theSheetsEdgeIsSurf3`: the pixel 0 dp in from the sheet's left at mid-height is `surf3` (dark 0xFF303537, light 0xFFDDE3E0).
  - `ShellPartsTest.theScrimDarkensBgToTheHandoffs`: an `OverlayLayer`-style `Box(background(ShellTokens.sheetScrim))` over `bg` reads `bg` blended at 60 % black, within 2 per channel. Hard-code dark 0xFF060606 and light 0xFF5F6060; don't use the formula.
  - `PinPadTest.overASheetTheScrimCoversOnlyTheSheetAndTheCardIsCentredInIt`: compute the expected values from `ShellTokens.sheetWidth` (left = 1280 − width, centre = 1280 − width / 2) instead of 680, 600 and 980.

  Section labels' tracking is shown by the images; there's no token test.

  Run `./gradlew :core:ui:testDebugUnitTest --tests '*ShellParts*' --tests '*Keyboard*' :core:access:testDebugUnitTest --tests '*PinPadTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: Images.** The predicted prefixes cover every right-hand sheet, its scrim, and every section label or tray label:
  - `editor_`, `detail_`, `person_`, `connect`, `pin_pad_` (over a sheet, if drawn so);
  - `favourites_sheet_`, `lights_pairing_sheet_`, `lights_address_sheet_`, `music_address_sheet_`, `sheet_warning_`;
  - `settings_door_`, `settings_display_`, `settings_lights_`, `settings_music_`;
  - `door_tab_`, `lights_tab_`, `lights_grid_`, `tab_lights_`, `page_header_`.

  Refine from the compare report before re-recording (a sheet whose screenshot draws no scrim or edge drops out), and stop on anything outside it. Look at one of each kind against 4a: the width, the `surf3` edge, the darker scrim.
```bash
git add core/ui core/access capability core/setup app
git commit -m "Widen the right-hand sheet to the redesign's 613 dp with a darker scrim and a surf3 edge, and track section labels as drawn"
```

---

### Task 3: Shared: the footer's reason, the error under a field, the locked chip, the held round button (rulings 5–7, 14)

**Review:** sonnet.

**Files:**
- Modify:
  - `core/ui/.../Buttons.kt`: `HhFooterButton`, and `HhRoundButton(repeatWhileHeld)`.
  - `core/ui/.../Controls.kt`: `HhStatusLine(icon, style, spoken)`, `ControlType.fieldError`, `HhChoiceChip(locked)`, `ControlTokens.fieldError*`.
- Test:
  - `core/ui/src/test/.../ButtonsTest.kt`, and create `RoundButtonHoldTest.kt`.
  - Create `StatusLineTest.kt`.
  - The chip's test.
  - `ButtonScreenshotTest.kt`, and create `FormPiecesScreenshotTest.kt`.
  - Create the `pixelAt(node, dx, dy)` helper in core/ui's test sources.

**Interfaces:**
- Produces:
  - **`HhFooterButton`.** Signature: `@Composable fun HhFooterButton(text: String, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier, icon: String? = null, enabled: Boolean = true, reason: String? = null)`. It is a `Column(modifier)`:
    - the reason line comes first, tagged `${tag}_reason`, 16 sp `mute`, centred, `shownNotSpoken`, 8 above the button, only while `!enabled && reason != null`;
    - then a Primary `HhButton` at `ButtonSize.Large` filling the width on `ButtonSurface.Bg`, tagged `tag`, with `stateDescription = reason` while disabled.
  - **`HhStatusLine`.** Signature: `@Composable fun HhStatusLine(text: String, tag: String, icon: String = Icons.INFO, style: TextStyle = HhType.body, spoken: Boolean = true)`.
    - `spoken = false` hides it from accessibility and makes it not live.
    - The `icon` is drawn filled when it is `ERROR`.
    - `ControlType.fieldError` is 14 sp W600.
  - **`HhChoiceChip(…, locked: Boolean = false)`** (ruling 7). `enabled = false` keeps today's 38 %.
  - **`HhRoundButton(…, repeatWhileHeld: Boolean = false)`.**
    - A tap gives one `onClick`.
    - A press held 500 ms gives one `onClick` then, and one every 100 ms until let go. The release after a hold adds none.
    - TalkBack's click is one step.
    - A disabled button stops repeating.

- [ ] **Step 1: The failing tests.** Each UI test ends with `assertClickablesHaveRoleAndLabel()`.
  - `ButtonsTest.aFooterButtonIsLargeAndFillsTheWidth`: in a 549 dp column, `HhFooterButton("Add event", …)` is 60 dp high and 549 wide.
  - `ButtonsTest.aDisabledFooterButtonShowsItsReasonAbove`: with `enabled = false, reason = "Add a title first"`:
    - `x_reason` is displayed, its bottom 8 dp above the button's top;
    - the button's state description is "Add a title first".
    Enabled, there is no `x_reason` node.
  - `ButtonsTest.aFooterButtonWithoutAReasonShowsNone` (**pin** of spec §6.4: optional): disabled with `reason = null` gives no reason node and the outline look.
  - `StatusLineTest.aFieldErrorIsDangerWithTheErrorIcon`: `HhStatusLine("Someone is already called Sam.", "x", icon = Icons.ERROR, style = ControlType.fieldError)` draws:
    - the `error` icon (filled) left of the text;
    - the text in `danger`;
    - a polite live region.
  - `StatusLineTest.aFieldsOwnErrorIsHiddenFromTalkBack`: with `spoken = false` the line is displayed, `onNodeWithTag("x").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))`, and there is no `LiveRegion`.
  - `StatusLineTest.theStatusLineIsUnchangedByDefault` (**pin**): `HhStatusLine(text, tag)` keeps the `info` icon and the 16 sp body.
  - `aLockedChipHasNoFillALockAndAMutedLabel`: with `locked = true`, unselected:
    - `pixelAt(chip, 4.dp, 4.dp)` is the background (`bg`); the edge pixel is `surf3`;
    - a `lock` icon sits before the dot; the label is `mute`;
    - the state is "not available" and the click label "find out why";
    - a click still calls `onClick`.
  - `aLockedChipThatIsChosenIsFilled`: with `locked = true, selected = true`, `pixelAt(chip, 4.dp, 4.dp)` is `selectedColor` and the lock shows.
  - `RoundButtonHoldTest.aTapStepsOnce`: one click, `onClick` once.
  - `RoundButtonHoldTest.holdingRepeatsAfterHalfASecond` (Review Focus 3): `mainClock.autoAdvance = false`, `down`:
    - at 484 ms (one frame before 500), 0 steps;
    - at 516 ms, 1;
    - at 816 ms, 4;
    - `up`, then still 4.
  - `RoundButtonHoldTest.aDisabledButtonStopsRepeating`: while held, `enabled` turns false, and no more steps follow.

  Run `./gradlew :core:ui:testDebugUnitTest --tests '*Buttons*' --tests '*StatusLine*' --tests '*RoundButtonHold*' --tests '*Chip*'`. Expected: FAIL to compile.
- [ ] **Step 2: Implement** per the Interfaces and Units.
- [ ] **Step 3: Images.** Expect no existing image to change: the defaults are today's look. Add new images `form_pieces_dark` and `form_pieces_light`, each showing:
  - a footer enabled, and disabled with its reason;
  - a field error;
  - a locked chip, unselected and chosen.

  Check them against 4a and 5b, by name.
```bash
git add core/ui
git commit -m "Add the footer button's reason line, let the status line be a field's error, and add a locked chip and a round button that repeats while held"
```

---

### Task 4: Repeats: the end as a type, `COUNT`, the fake's expander (spec §4.8; rulings 13, 14; Review Focus 3)

**Review:** opus.

**Files:**
- Modify:
  - `capability/calendar/.../Recurrence.kt`: `RepeatEnd`, `recurrenceRule`, `RepeatChoice.COMMON`/`MORE`.
  - `EventForm.kt`: `endsOn` → `repeatEnd`, the count steps, `Answers`.
  - `ui/EventEditorSheet.kt`: only the calls (`EndsSection` reads `repeatEnd`), so it compiles. Task 9 redraws Ends.
  - `provider/calendar-fake/.../RecurrenceExpander.kt`.
- Test (every call site the signature change breaks):
  - `RecurrenceTest.kt:15, :60`: `end = RepeatEnd.Never`; the round-trip loop gains `After`.
  - `EventFormTest.kt:465–510`.
  - `ui/EventEditorSheetTest.kt:286, :301`: `chooseEnd(RepeatEnd.OnDate(…))`.
  - `ui/EditorScreenshotTest.kt`: `repeats()`.
  - `provider/calendar-fake/src/test/.../FakeCalendarProviderTest.kt:365, :373`: `RepeatEnd.OnDate`.
  - `RepeatsTest.kt`.
  - Create `provider/calendar-fake/src/test/.../RecurrenceExpanderTest.kt`.
  - `provider/calendar-google/src/test/.../GoogleWriteTest.kt`.

**Interfaces:**
- Produces:
  - **`RepeatEnd`.** `sealed interface RepeatEnd { data object Never : RepeatEnd; data class OnDate(val date: LocalDate) : RepeatEnd; data class After(val times: Int) : RepeatEnd { init { require(times in MIN_TIMES..MAX_TIMES) } } }`, with `const val MIN_TIMES = 2`, `MAX_TIMES = 52`, `DEFAULT_TIMES = 10`.
  - **`recurrenceRule`.** `fun recurrenceRule(choice: RepeatChoice, end: RepeatEnd, allDay: Boolean, zone: ZoneId): String?`:
    - `OnDate` writes `UNTIL` exactly as today;
    - `After(n)` writes `;COUNT=n`;
    - `Never` writes nothing.
  - **`RepeatChoice.COMMON`** is `listOf(Never, Weekly, Fortnightly)` and **`RepeatChoice.MORE`** is `listOf(Daily, Monthly, Yearly)`, both companion vals; the labels are unchanged.
  - **In `EventForm`:**
    - `var repeatEnd: RepeatEnd` (private set, starts `Never`);
    - `fun chooseEnd(end: RepeatEnd)`, ignored unless `canRepeat && repeat != Never`;
    - `fun chooseAfter(times: Int = DEFAULT_TIMES)`, which clamps `times` to `MIN_TIMES..MAX_TIMES` and then calls `chooseEnd(After(…))`; the chip uses it;
    - `fun fewerTimes()` and `fun moreTimes()`, stepping by 1, clamped, and ignored unless the end is `After`;
    - `val endsBeforeStart` reads `OnDate`;
    - choosing `Never` as the repeat sets `repeatEnd = Never`;
    - `endsOn` goes.
  - **Copy:** `fun afterTimes(times: Int): String = "After $times times"`.

- [ ] **Step 1: The failing tests.**
  - `RecurrenceTest.aCountedSeriesWritesCountAndNoUntil` (Review Focus 3): `Weekly, After(5)` gives `"RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=5"`. The existing `UNTIL` tests move to `OnDate` and pass unchanged.
  - `RecurrenceTest.neverBothUntilAndCountForAnyRepeatEnd`: for every `RepeatChoice` except Never × {`Never`, `OnDate`, `After(2)`, `After(52)`} × all-day/timed, the rule never holds both `UNTIL=` and `COUNT=`.
  - `RecurrenceTest.aCountOutsideTheRangeCannotBeBuilt`: `After(1)`, `After(53)` and `After(0)` throw.
  - `RepeatsTest.aCountedSeriesReadsAsItsChip` (**pin**: `COUNT` is in `PLAIN_PARTS`): `"RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=6"` reads "Every 2 weeks".
  - `EventFormTest.theCountStaysBetweenTwoAndFiftyTwo` (Review Focus 3):
    - `After(2)` then `fewerTimes()` stays `After(2)`;
    - `After(52)` then `moreTimes()` stays `After(52)`;
    - `After(10)` then `moreTimes()` gives `After(11)`.
  - `EventFormTest.chooseAfterClampsItsCount`: `chooseAfter(1)` gives `After(2)` and `chooseAfter(99)` gives `After(52)`.
  - `EventFormTest.anEndChosenWhileNotRepeatingIsIgnored`: with repeat Never, `chooseEnd(After(10))` leaves `repeatEnd == Never` and the form not `edited`.
  - `EventFormTest.changingTheEndReplacesIt` (Review Focus 3):
    - Weekly with `After(5)`, then `chooseEnd(OnDate(day + 30))`: the draft's rule has `UNTIL` and no `COUNT`;
    - `chooseRepeat(Never)` gives `repeatEnd == Never` and no rule.
  - `EventFormTest.changingBetweenRepeatsKeepsTheEnd`: Weekly with `After(5)`, then Monthly, keeps `After(5)`.
  - `EventFormTest.aCountMakesTheSheetEdited`: `After(10)` makes the form `edited`; back to `Never` it isn't.
  - `RecurrenceExpanderTest` (plain JVM, Europe/London; `expand` directly):
    - `theWindowEndingOnTheLastOccurrenceKeepsAllOfThem`: a weekly timed series from Fri 2 Oct 2026, `COUNT=5`, with `lastDay` on its 5th date (Fri 30 Oct), gives 5.
    - `theWindowEndingTheDayBeforeKeepsOneFewer`: `lastDay` Thu 29 Oct gives 4.
    - `aCountedSeriesStopsAtItsCount`: over a year, 5.
    - `aCountOfOneIsOneOccurrence`: `COUNT=1` gives 1, `seriesId` set.
    - `aBadCountIsIgnored`: `COUNT=0`, `COUNT=-3`, `COUNT=abc` and `COUNT=` each expand as with no `COUNT`, bounded by the window.
    - `untilAndCountTogetherEndAtTheFirst`: `UNTIL` before the 5th occurrence with `COUNT=5` stops at `UNTIL`; `UNTIL` after the 3rd with `COUNT=3` gives 3.
    - `aCountedSeriesKeepsItsLocalTimeOverTheClockChange` (Review Focus 3): from Fri 16 Oct 2026 18:00, weekly, `COUNT=5`, every occurrence is 18:00 local (16, 23, 30 Oct, 6, 13 Nov).
    - `aCountedAllDaySpanKeepsItsDays`: all-day Fri 23 – Sun 25 Oct (exclusive Mon 26), weekly, `COUNT=3`, gives 3 occurrences, each 3 dates long.
  - `GoogleWriteTest.aCountedSeriesInsertSendsItsRule` (**pin**: the writer sends the rule as given): an insert with `"RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=5"` puts that line in the request's `recurrence`.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*RecurrenceTest*' --tests '*RepeatsTest*' --tests '*EventFormTest*' --tests '*EventEditorSheetTest*' :provider:calendar-fake:testDebugUnitTest :provider:calendar-google:testDebugUnitTest --tests '*GoogleWriteTest*'`. Expected: FAIL to compile.
- [ ] **Step 2: Implement** per the Interfaces and ruling 14:
  - the expander reads `COUNT` with `toIntOrNull`, ignores it when absent or < 1, and takes that many occurrences after its `UNTIL` and window checks;
  - its KDoc lists `COUNT`.
- [ ] **Step 3: The gate and commit.** Expected: PASS, **no image changed**. The sheet still shows the old Ends chips, with "On a date…" setting `OnDate`.
```bash
git add capability/calendar provider/calendar-fake provider/calendar-google
git commit -m "Let a new series end after a number of times as well as on a date, and have the sample calendar repeat it that many times"
```

---

### Task 5: Multi-day: Until in the form for new all-day events, the 60-day cap (spec §2.1; rulings 15, 16; Review Focus 1)

**Review:** opus.

**Files:**
- Modify:
  - `capability/calendar/.../EventForm.kt`: `until`, the stashed Until, `chooseDay`, `chooseTime`, `draft`, `Answers`, `canSave`. **`datesLocked` is not touched here** (Task 10).
  - `ui/Pickers.kt`: `EditorPicker.Until`.
  - `ui/EventEditorSheet.kt`: only the `when (picker)` branch, drawing the date picker for `Until`, so it compiles. Task 10 draws the chip.
- Test:
  - `EventFormTest.kt`. Use `new(on = LocalDate.of(2026, 10, 19))`; the class's `today` (Wed 23 Sep) and its Robolectric runner stay.
  - `CalendarEditorTest.kt`.

**Interfaces:**
- Consumes: `UNTIL` from `uk.co.siland.culvery.core.ui` (`Rows.kt:22`), reused, not redeclared.
- Produces, in `EventForm`:
  - `var until: LocalDate?` (private set; null means one day);
  - `val offersUntil: Boolean get() = time == TimeChoice.AllDay && !datesLocked`;
  - `fun chooseUntil(date: LocalDate)`, which does nothing unless `offersUntil`, sets null when `date == day`, and marks when touched;
  - `val untilBeforeStart` and `val untilTooLate`, both false unless `offersUntil`. Too late means more than 60 days: `until > day.plusDays(MAX_SPAN_DAYS - 1)`;
  - `const val MAX_SPAN_DAYS = 60`;
  - `val untilLabel: String`, which is `UNTIL` or "Until Thu 22 Oct" (`SHORT_DAY`);
  - `val untilError: String?`, which is `UNTIL_TOO_EARLY`, `UNTIL_TOO_LATE` or null;
  - `canSave` adds `untilError == null`;
  - `enum class EditorPicker { None, Date, Time, EndDate, Until }`;
  - `internal const val UNTIL_TOO_EARLY = "The last day is before the first day. Pick a later one."` and `internal const val UNTIL_TOO_LATE = "An event can last up to 60 days. Pick an earlier last day."`.

- [ ] **Step 1: The failing tests.** All are in `EventFormTest`, Europe/London, new events on Mon 19 Oct 2026.
  - `aMultiDayDraftEndsTheDayAfterItsLastDay` (Review Focus 1): day Mon 19, then `chooseUntil(Thu 22)`. The draft is `AllDay(Mon 19)` → `AllDay(Fri 23)`, and the summary equals `whenLabel(draft.start, draft.end, zone, today)` plus " · " and the person, read from `whenLabel` rather than retyped.
  - `untilOnTheDayItselfIsOneDay`: `chooseUntil(Mon 19)` gives `until == null`, a draft ending Tue 20, and `untilLabel == "Until"`.
  - `untilBeforeTheDayWaitsToSave`: day Wed 21, then `chooseUntil(Tue 20)`. `untilError == UNTIL_TOO_EARLY`, and with a title set, `!canSave`.
  - `untilOnTheSixtiethDayIsAllowed` (the user's boundary): `chooseUntil(Mon 19 + 59)` (Thu 17 Dec) gives `untilError == null`, `canSave`, and a draft ending Fri 18 Dec.
  - `untilOnTheSixtyFirstDayWaitsToSave`: `chooseUntil(Mon 19 + 60)` (Fri 18 Dec) gives `untilError == UNTIL_TOO_LATE` and `!canSave`.
  - `movingTheDayMovesUntilWithIt` (Review Focus 1): Mon 19 – Wed 21, then `chooseDay(Fri 23)`, gives `until == Sun 25`; then `chooseDay(Tue 20)` gives `until == Thu 22`.
  - `choosingATimeSetsUntilAside`: Until Wed 21, then `chooseTime(Custom(18:00))`. `until == null`, `offersUntil == false`, and the draft is timed on one day.
  - `reChoosingAllDayRestoresUntil` (Review Focus 1): then `chooseTime(AllDay)` gives `until == Wed 21`. A time, then `chooseDay(Tue 20)`, then All day gives `until == null` (the Day changed, so the old Until isn't restored).
  - `chooseUntilIsIgnoredForATimedEvent`: timed 18:00, then `chooseUntil(Wed 21)` leaves `until == null` and the form not `edited`.
  - `aHiddenUntilNeverHoldsSaveBack`: Until Tue 20 before day Wed 21, then a time. `untilError == null` and `canSave`.
  - `untilMakesTheSheetEdited`: `chooseUntil(Wed 21)` makes the form `edited`; `chooseUntil(Mon 19)` makes it not.
  - `aMultiDaySeriesCarriesItsRule` (rulings 14–15 together; not a pin): Until Wed 21, Weekly, `After(4)` gives a draft all-day Mon 19 → Thu 22 with `COUNT=4`.
  - `anUntilAcrossTheYearEndIsLabelled`: day Wed 30 Dec, then Until Sat 2 Jan, gives "Until Sat 2 Jan" and a draft ending Sun 3 Jan 2027.
  - `CalendarEditorTest.aMultiDayAllDayEventIsWrittenWithItsExclusiveEnd`: a new event with no repeat, Mon 19, Until Wed 21, saved by an adult. The writer receives `AllDay(Mon 19)`/`AllDay(Thu 22)`.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventFormTest*' --tests '*CalendarEditorTest*'`. Expected: FAIL to compile.
- [ ] **Step 2: Implement** per the Interfaces and rulings 15–16. `draft`'s KDoc keeps "timed events end at the next midnight at the latest" and adds the all-day Until.
- [ ] **Step 3: The gate and commit.** Expected: PASS, **no image changed**. Edits still lock all-day spans until Task 10.
```bash
git add capability/calendar
git commit -m "Let a new all-day event run until a later day, up to 60 days"
```

---

### Task 6: The sheet: header, title, sections, Time beside Length, the chips' words (spec §4.8; rulings 2, 11, 12; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify:
  - `capability/calendar/.../ui/EventEditorSheet.kt`: `Header`, `Section`, `DaySection`, and `TimeSection` with `LengthSection` merged into one `WhenRow`. The body column is tagged `editor_body`.
  - `ui/CalendarType.kt`:
    - `editorTitle` 35 sp W700;
    - `editorSummary` 15 sp;
    - `editorPaddingTop` 29, `editorPaddingH` 32 (new), `editorPaddingBottom` 27;
    - `sectionGap` 8, `editorBodyGap` 16, `timeLengthGap` 27.
- Test: `ui/EventEditorSheetTest.kt`, `ui/EditorScreenshotTest.kt`.

**Interfaces:**
- Produces:
  - `internal const val PICK_A_DATE = "Pick a date"` and `PICK_A_TIME = "Pick a time"`;
  - `editor_time_length` on the shared row;
  - `editor_body` on the scrolling body.

- [ ] **Step 1: The failing tests.** These are in `EventEditorSheetTest`; each ends with `assertClickablesHaveRoleAndLabel()`.
  - `theHeaderSitsAsDrawn`:
    - the summary's top is 4 dp below the title's bottom;
    - the close button's top is level with the title's top (±1 dp);
    - the sheet's first content is 29 dp from its top and 32 dp from its left.
  - `theChipsSayPickADateAndPickATime` (note 34):
    - `day_pick` reads "Pick a date" with `calendar_month`;
    - `time_pick` reads "Pick a time" with `schedule`;
    - no node in the sheet has text containing "…".
  - `timeAndLengthSitSideBySide`: for a timed event, the "Length" label's left is at least Time's last chip's right + 27 dp, and the two labels are level (±1 dp). For all day there is no Length section.
  - `timeAndLengthWrapAtLargeFont`: at font scale 1.3, a timed event's Length chips sit within the sheet's body and don't overlap Time's.
  - `theClosedSheetFitsWithRoomToSpare` (Review Focus 5): the sheet is 613 × 800 dp, the title is empty, and More options is closed. Run it twice, with Alex signed in (Repeats shown) and with Mia as a child (the line, no Repeats; until Task 8 this case is just Mia with every chip open). In both, the last block's bottom is at least 24 dp above the footer's reason line, and `editor_body`'s `VerticalScrollAxisRange.maxValue` is 0.
  - `pickDateOpensTheDatePickerAndAPickSetsTheDay` and `pickTimeSetsACustomTime` keep passing with the new labels.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventEditorSheetTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per Units and rulings 11–12:
  - `EventEditorSheet.kt:129–130` reads `editorPaddingH`;
  - the detail sheet keeps `sheetPaddingH`.
- [ ] **Step 3: Images.**
  - Predicted: the `editor_` prefix, all of it.
  - `detail_` must not change (stop if it does).
  - Check against 4a.
```bash
git add capability/calendar
git commit -m "Redraw Add event's header and sections, put Time beside Length, and say Pick a date and Pick a time"
```

---

### Task 7: The sheet: the footer and its reason; the pickers' round buttons (spec §4.8, §6.4; rulings 5, 18; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify:
  - `capability/calendar/.../ui/EventEditorSheet.kt`: `Footer`. The row is `verticalAlignment = Alignment.Bottom`, so Delete lines up with Save under a reason.
  - `ui/Pickers.kt`: the date picker's ‹ › become `HhRoundButton`; `RoundButton` goes.
  - `ui/CalendarType.kt`: `DISABLED_ALPHA`, `pageButton` and `pageButtonIcon` go.
- Test: `ui/EventEditorSheetTest.kt`, `ui/PickersTest.kt`, `ui/EventEditorHostTest.kt`, `ui/EditorScreenshotTest.kt`.

**Interfaces:**
- Consumes: `HhFooterButton` (Task 3); `HhRoundButton`.
- Produces: `internal const val ADD_A_TITLE_FIRST = "Add a title first"`. Save keeps the tag `editor_save`, and its reason is `editor_save_reason`. The reason is passed only while `form.mode == New && form.title.isBlank()`.

- [ ] **Step 1: The failing tests.** Each UI test ends with `assertClickablesHaveRoleAndLabel()`.
  - `saveIsDisabledUntilThereIsATitle` (extended, note 36):
    - with an empty title, `editor_save` is not enabled and `editor_save_reason` "Add a title first" is above it;
    - with a title typed, Save is enabled and there is no reason node.
  - `anEditWithItsTitleClearedShowsNoReason` (ruling 5): on an edit, clearing the title disables Save, and there is no `editor_save_reason`.
  - `anEndBeforeTheDayHasNoFooterReason` (**pin** of spec §6.4): with a title set and Ends before the day, Save is disabled and there is no `editor_save_reason`.
  - `theFooterIsFullWidthAndLarge`:
    - `editor_save` is 60 dp high and as wide as the body;
    - on an edit, Delete sits on its left and Save fills the rest, their bottoms level (±1 dp).
  - `theFooterAndItsReasonStayAboveTheKeyboard` (Review Focus 5; extends `saveStaysAboveTheKeyboard`): with a 320 dp keyboard and an empty title, at font scale 1.0 and 1.3, `editor_save` and `editor_save_reason` are both above the keyboard's top.
  - `EventEditorHostTest.saveDoesNothingWhileTheTitleIsBlankEvenIfOnSaveIsCalled` (**pin**): the host's `canSave` guard stays the gate.
  - `EventEditorHostTest.deleteStillAsksForAPinBeforeConfirming` (**pin**).
  - `PickersTest.thePickersPagesButtonsAreTheSharedRoundButton`: `date_prev` and `date_next` are 48 dp, `Role.Button`, named "Earlier weeks"/"Later weeks", with a 24 dp icon.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventEditorSheetTest*' --tests '*PickersTest*' --tests '*EventEditorHostTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement.**
  - The footer is `HhFooterButton(…, tag = "editor_save", icon = CHECK, or REFRESH after a failure, reason = …)`, with `DeleteButton` unchanged (Large) beside it on an edit.
  - The pickers use `HhRoundButton(…, on = ButtonSurface.Surf, iconSize = ButtonTokens.icon)`.
- [ ] **Step 3: Images.**
  - Predicted: `editor_` (the footer), `editor_date_picker_` (the page buttons' disabled look, if shown).
  - `detail_` must not change.
  - Check against 4a and note 36.
```bash
git add capability/calendar
git commit -m "Put Add event's Save across the footer with Add a title first above it while there's no title, and give the date picker the shared round buttons"
```

---

### Task 8: A child: locked Who chips, the line, and no repeats (spec §4.8; rulings 7–10; Review Focus 4)

**Review:** opus.

**Files:**
- Modify:
  - `capability/calendar/.../ui/EventEditorSheet.kt`: `WhoSection`; `RepeatsSection` hidden for a child.
  - `CalendarPermissions.kt`: `cannotAddForOthers`, `mayRepeat`, `CANT_REPEAT_FOR_CHILD`.
  - `CalendarEditor.kt`: `create` refuses a recurrence without `CREATE`.
  - `EventForm.kt`: the `repeats` parameter.
  - `ui/EventEditorHost.kt`: passes `repeats = childOnly == null`.
  - `ui/CalendarType.kt`: the line's style and gaps.
- Test:
  - `ui/EventEditorSheetTest.kt`: `aSignedInChildsOtherWhoChipsAreDisabledAndATapExplains`, `whoChipsAreARadioGroupAndAChildsOthersSayWhyNot`.
  - `CalendarPermissionsTest.kt:68`.
  - `CalendarEditorTest.kt:561, 574, 722`.
  - `ui/EventEditorHostTest.kt:298, 312, 324`.
  - `EventFormTest.kt`.
  - `ui/EditorScreenshotTest.kt`.
  - `app/src/test/.../OverlayLayersTest.kt:147` (its sample toast text, updated so it doesn't go stale).

**Interfaces:**
- Consumes: `HhChoiceChip(locked)` (Task 3).
- Produces:
  - `fun cannotAddForOthers(name: String): String = "$name can add events for $name only."`;
  - `internal fun mayRepeat(granted: Set<String>): Boolean = CalendarPermissions.CREATE in granted`;
  - `const val CANT_REPEAT_FOR_CHILD = "Ask a grown-up to add an event that repeats."`;
  - `EventForm(…, repeats: Boolean = true)`, with `canRepeat = mode == New && repeats`;
  - the line tagged `editor_who_only`.

- [ ] **Step 1: The failing tests.** Each UI test ends with `assertClickablesHaveRoleAndLabel()`.
  - `aChildsOtherChipsAreLockedWithTheLine` replaces `aSignedInChildsOtherWhoChipsAreDisabledAndATapExplains` and keeps its tap check. With Mia signed in as a child:
    - Family, Alex and Sam are locked, with no fill (`pixelAt`) and the `lock` showing;
    - Mia's chip is filled in her colour with the check;
    - `editor_who_only` "Mia can add events for Mia only." sits under the chips with the `lock` icon.
  - `aTapOnALockedChipExplainsAndChangesNothing` (Review Focus 4): a tap on Alex calls `onRefusedWho` once, and `form.who` is still Mia.
  - `aChosenLockedChipIsDrawnChosenWithItsLock` (Review Focus 4): an edit of an event for Alex, with Mia signed in. Alex's chip `assertIsSelected`, is filled in Alex's colour and shows the lock; a tap on it calls no `onRefusedWho` and changes nothing; Mia's chip is unlocked.
  - `busyFadesNoChip` (Review Focus 4): with `busy = true` and an adult signed in, every Who chip's fill is at full alpha and none says "not available"; a tap changes nothing.
  - `lockedChipsSayWhyNot` keeps `whoChipsAreARadioGroupAndAChildsOthersSayWhyNot`: the locked chips' state is "not available" and their click label "find out why", and the group is still a radio group.
  - `anAdultSeesNoLine`: with Alex signed in, there is no `editor_who_only` and no locked chip.
  - `aChildWithALongNameKeepsTheLineWhole`: a 30-character child name at font scale 1.3 wraps the line, nothing is clipped, and the footer is still on screen.
  - `aChildSeesNoRepeats` (the user, 2026-10-20): with `childOnly` set and a new event, there is no `repeat_*` node, no `repeat_more` and no Ends.
  - `EventFormTest.aChildsFormNeverRepeats`: `EventForm(…, repeats = false)` gives `canRepeat == false`; `chooseRepeat(Weekly)` leaves Never; the draft has no recurrence.
  - `CalendarEditorTest.aChildCannotSaveARepeatingEvent` (the user, 2026-10-20): a draft with a weekly `recurrence` for Mia, authorised with `create.self` only. The toast says `CANT_REPEAT_FOR_CHILD`; nothing is written and nothing is queued.
  - `CalendarEditorTest.anAdultMaySaveARepeatingEvent` (**pin**): the same draft with `create` is written.
  - `CalendarEditorTest.aChildWhoseSessionEndedCannotSaveForSomeoneElse` (Review Focus 4): the form is tagged Alex, Mia's PIN is entered at Save, and the result is `cannotAddForOthers("Mia")`, no write and no pending change.
  - `EventEditorHostTest.theLockedChipsFollowTheSessionRole`: the session changes from Mia (child) to none while the sheet is open, and the chips unlock. The save still goes through `CalendarEditor` (covered above).
  - `CalendarPermissionsTest`: `cannotAddForOthers("Mia") == "Mia can add events for Mia only."`. The `CalendarEditorTest` and `EventEditorHostTest` toasts expect it.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventEditorSheetTest*' --tests '*CalendarPermissionsTest*' --tests '*CalendarEditorTest*' --tests '*EventEditorHostTest*' --tests '*EventFormTest*' :app:testDebugUnitTest --tests '*OverlayLayersTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 8–10.
  - `WhoSection` derives one `locked` flag and uses it for both `HhChoiceChip(locked = …)` and the click `when`; `enabled` isn't passed.
  - The line comes from the signed-in child's own name, and is absent without one.
  - `RepeatsSection` is hidden when `childOnly != null`.
  - `CalendarEditor.create` refuses a recurrence unless `mayRepeat`, before the write and before any queueing.
- [ ] **Step 3: Images.**
  - Predicted: `editor_child_` (the locked chips, the line, no Repeats).
  - Check against 4a and note 33.
```bash
git add capability/calendar app
git commit -m "Lock a child's other Who chips with a padlock, say under them who the child can add events for, and keep a child's events from repeating"
```

---

### Task 9: Repeats: More options, the Ends row, After N times (spec §4.8; rulings 13, 14; Review Focus 3, 5)

**Review:** sonnet.

**Files:**
- Modify:
  - `capability/calendar/.../ui/EventEditorSheet.kt`: `RepeatsSection`, `EndsSection`.
  - `EventForm.kt`: `moreRepeats`, `showMoreRepeats()`.
  - `ui/CalendarType.kt`.
- Test:
  - `ui/EventEditorSheetTest.kt`: `aNewEventOffersRepeatsAndEndsOnlyWhenItRepeats`, `choosingARepeatScrollsEndsIntoView`, `anEndBeforeTheDayScrollsItsLineIntoView`, `anEndBeforeTheDaySaysSoAndSaveWaits`.
  - `EventFormTest.kt`.
  - `ui/EditorScreenshotTest.kt`: `repeats()` calls `showMoreRepeats()`, Weekly, `After(10)`.

**Interfaces:**
- Consumes:
  - `RepeatEnd`, `RepeatChoice.COMMON`/`MORE`, `fewerTimes`/`moreTimes`, `afterTimes`, `MIN_TIMES`/`MAX_TIMES` (Task 4);
  - `HhRoundButton(repeatWhileHeld)` (Task 3);
  - `Icons.REMOVE`, `Icons.ADD`.
- Produces:
  - in `EventForm`, `val moreRepeats: Boolean`, equal to `opened || repeat in RepeatChoice.MORE || repeatEnd != RepeatEnd.Never`, and `fun showMoreRepeats()` (sets `opened`; ignored unless `canRepeat`). Neither is in `Answers`;
  - `internal const val MORE_OPTIONS = "More options"`, `ON_A_DATE = "On a date"`, `FEWER_TIMES = "Fewer times"`, `MORE_TIMES = "More times"`;
  - tags `repeat_more`, `ends_never`, `ends_pick`, `ends_after`, `ends_fewer`, `ends_more`, `ends_error` (kept).

- [ ] **Step 1: The failing tests.** These are in `EventEditorSheetTest`; each ends with `assertClickablesHaveRoleAndLabel()`. Tests that drive the form directly call `form.showMoreRepeats()` or choose a `MORE` repeat first.
  - `repeatsShowsTheCommonThreeAndMoreOptions` (note 35): `repeat_Never`, `repeat_Weekly`, `repeat_Fortnightly` and `repeat_more` ("More options", `Role.Button`, `expand_more`) show. There is no `repeat_Daily`, `repeat_Monthly`, `repeat_Yearly`, and no Ends.
  - `moreOptionsOpensInPlaceAndStaysOpen`:
    - a tap on `repeat_more` shows `repeat_Daily`, `repeat_Monthly` and `repeat_Yearly` under the first row;
    - `repeat_more` is gone, and `repeat_Daily` is focused;
    - choosing Never keeps them shown.
  - `EventFormTest.aDailyRepeatOpensMoreOptionsByItself`: `chooseRepeat(Daily)` without `showMoreRepeats()` gives `moreRepeats`.
  - `theEndsRowShowsOnlyWhileItRepeats` replaces `aNewEventOffersRepeatsAndEndsOnlyWhenItRepeats`. Opened with Weekly, `ends_never`, `ends_pick` ("On a date") and `ends_after` ("After 10 times") show; with Never, there is no Ends.
  - `afterNTimesShowsItsCountButtons`:
    - a tap on `ends_after` selects it and shows `ends_fewer` and `ends_more`, named "Fewer times" and "More times";
    - `ends_more` gives "After 11 times";
    - another tap on `ends_after` leaves 11;
    - On a date, then After again, gives "After 10 times";
    - another end chosen leaves no count buttons.
  - `theCountButtonsStopAtTheirEnds` (Review Focus 3): at 2, `ends_fewer` is not enabled; at 52, `ends_more` is not enabled. These are driven through the form.
  - `holdingMoreStepsTheCount`: hold `ends_more` 816 ms under `mainClock` and the chip reads "After 14 times".
  - `theSheetScrollsOnlyOnceMoreOptionsOpens` (Review Focus 5): at 613 × 800 dp with Alex signed in and an empty title (the reason line showing), `editor_body`'s `VerticalScrollAxisRange.maxValue` is 0. After `repeat_more` and Weekly it is > 0.
  - `openingMoreOptionsBringsEndsIntoView` (Review Focus 5; replaces `choosingARepeatScrollsEndsIntoView`): at 613 × 520 dp, `repeat_more`, then Weekly, then `compose.waitForIdle()`, and `ends_after` is displayed.
  - `anEndBeforeTheDaySaysSoAndSaveWaits` and `anEndBeforeTheDayScrollsItsLineIntoView` keep their assertions, opening More options first.
  - `theEndsRowFitsAtLargeFont`: at font scale 1.3, "After 10 times" and its two buttons wrap within the body and nothing is clipped.
  - `EventFormTest.moreOptionsIsOnlyForANewEvent`: on an edit, `showMoreRepeats()` leaves `moreRepeats` false.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventEditorSheetTest*' --tests '*EventFormTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 13–14:
  - `COMMON` and the More options chip, or `COMMON` then `MORE` as a second `FlowRow`;
  - the Ends row inside that area while `repeat != Never`;
  - the count buttons, which are `HhRoundButton(…, repeatWhileHeld = true, iconSize = ButtonTokens.icon)`;
  - Ends' `bringIntoView` as today;
  - focus on `repeat_Daily` after opening.
- [ ] **Step 3: Images.**
  - Predicted: `editor_repeats_` (now open, Weekly, After 10 times).
  - New: `editor_more_options_dark` (open, Never chosen).
  - Check against 4a and note 35.
```bash
git add capability/calendar
git commit -m "Keep Repeats to three choices with More options for the rest, and let a series end after a number of times"
```

---

### Task 10: The Until chip, and all-day spans unlocked on edit (spec §2.1; rulings 15–17; Review Focus 1, 2)

**Review:** opus.

**Files:**
- Modify:
  - `capability/calendar/.../EventForm.kt`: `datesLocked` per ruling 17; an all-day span opens with `day`/`until`.
  - `ui/EventEditorSheet.kt`:
    - `TimeSection` gets the Until chip, with its state description carrying `untilError`;
    - the Until error line is an `HhStatusLine(icon = ERROR, style = fieldError)`, composed only while `untilError != null`, inside the Time section's column;
    - `LockedDates` is shown only while `datesLocked`.
- Test (all the existing tests the unlock changes, moved to spans that stay locked, in this task):
  - `EventFormTest.kt`:
    - `anEventOverSeveralDaysLocksItsDates` (:148) becomes `aTimedSpanStaysLocked`;
    - `aLockedSpanCrossingAMonthOrYearNamesTheMonthOnBothEnds` (:161) moves to timed spans: Wed 30 Sep 22:00 → Fri 2 Oct 09:00, and Thu 31 Dec 22:00 → Sat 2 Jan 09:00, with the same expected labels;
    - `aLockedEditKeepsItsDatesAndChangesOnlyTitleAndWho` (:178) moves to a timed span.
  - `ui/EventEditorSheetTest.kt`: `aLockedMultiDayEditShowsOneLineInPlaceOfDayTimeAndLength` (:189) becomes `aTimedSpanEditStillShowsOneLine`, at Mon 28 Sep 18:00 → Wed 30 Sep 10:00.
  - `ui/EditorScreenshotTest.kt`: `locked()` moves to a timed span, Mon 28 Sep 18:00 → Wed 30 Sep 10:00, so `editor_locked_` still shows the locked line.

**Interfaces:**
- Consumes (Task 5): `until`, `offersUntil`, `chooseUntil`, `untilError`, `untilLabel`, `EditorPicker.Until`.
- Consumes (Task 3): `HhStatusLine(icon, style)`.
- Produces: the tags `time_until` and `until_error`.

- [ ] **Step 1: The failing tests.** Each UI test ends with `assertClickablesHaveRoleAndLabel()`.
  - `EventFormTest.anAllDaySpanOpensWithItsFirstAndLastDays` (Review Focus 2): an edit of the half term `AllDay(Mon 19)` → `AllDay(Sat 24)` gives `!datesLocked`, `day == Mon 19`, `until == Fri 23` and `offersUntil`.
  - `EventFormTest.anUntouchedAllDaySpanIsUnchanged` (Review Focus 2): that edit with the title unchanged is `unchanged`, and the draft's start and end are the original's.
  - `EventFormTest.movingUntilOnAnEditTouchesOnlyTheTimes`: `chooseUntil(Wed 21)` gives `touched == {TIMES}` and a draft ending Thu 22.
  - `EventFormTest.movingTheDayOnASpanEditTouchesOnlyTheTimes`: `chooseDay(Tue 20)` gives `until == Sat 24`, `touched == {TIMES}`, and a draft of Tue 20 → Sun 25.
  - `EventFormTest.aOneDayEditGivenAnUntilTouchesOnlyTheTimes`: an all-day Mon 19 edit, then `chooseUntil(Wed 21)`, gives `touched == {TIMES}`.
  - `EventFormTest.anEditWhoseTimeWasChosenThenAllDayAgainIsUnchanged`: the half term edit, then a time, then All day, is `unchanged`, with Until restored.
  - `EventFormTest.aTimedSpanStaysLocked`:
    - Mon 19 22:00 → Wed 21 09:00 gives `datesLocked`, `lockedDatesLabel` as today, and `offersUntil == false`;
    - 22:00 → 01:00 is still locked, and 22:00 → 00:00 still not.
  - `EventFormTest.aMixedAllDayAndTimedEventStaysLocked`: `AllDay(Mon 19)` → `Timed(Wed 21 09:00)` gives `datesLocked`.
  - `EventEditorSheetTest.untilShowsOnlyForAllDay`: with all day, `time_until` "Until" shows with `date_range` after `time_pick`; with a time picked there is no `time_until`.
  - `EventEditorSheetTest.untilOpensTheDatePickerAndAPickSetsTheLastDay`:
    - a tap on `time_until` calls `onPicker(EditorPicker.Until)`;
    - with the picker shown, picking Thu 22 gives `form.until == Thu 22`, the chip "Until Thu 22 Oct", and the range in the summary;
    - the picker opens on Until's date, or on the day when there is none.
  - `EventEditorSheetTest.anUntilBeforeTheDaySaysSoAndSaveWaits` (Review Focus 1): day Wed 21 and Until Tue 20 give:
    - `until_error` "The last day is before the first day. Pick a later one.";
    - `time_until`'s state description equal to the same sentence;
    - Save disabled, and no footer reason (a title is set).
  - `EventEditorSheetTest.anUntilPastSixtyDaysSaysSoAndSaveWaits` (Review Focus 1): Until at day + 60 gives `until_error` "An event can last up to 60 days. Pick an earlier last day." and Save disabled. At day + 59 there is no `until_error`.
  - `EventEditorSheetTest.noUntilErrorTakesRoomWhenThereIsNone`: with an all-day valid Until, there is no `until_error` node, and `theClosedSheetFitsWithRoomToSpare` (Task 6) still passes (re-run here).
  - `EventEditorSheetTest.anAllDaySpanEditShowsItsUntil` (Review Focus 2): the half term edit shows Day, Time and `time_until` "Until Fri 23 Oct", and no `editor_locked_dates`.
  - `EventEditorSheetTest.aTimedSpanEditStillShowsOneLine`: Mon 28 Sep 18:00 → Wed 30 Sep 10:00 shows `editor_locked_dates` and no Day chips.
  - `EventEditorSheetTest.openingUntilClosesTheKeyboard` (extends `openingAPickerClosesTheKeyboardButAChipDoesNot`).
  - `EventEditorHostTest.saveDoesNothingWhileUntilIsBeforeTheDay` (**pin** of the host's `canSave` guard).

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventFormTest*' --tests '*EventEditorSheetTest*' --tests '*EventEditorHostTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 15–17.
  - The chip shows while `form.offersUntil`: `Role.Button`, selected while `until != null`, with `stateDescription = untilError` when set.
  - The `EditorPicker.Until` branch is a `DatePickerCard(selected = form.until ?: form.day, …)`.
- [ ] **Step 3: Images.**
  - Predicted:
    - `editor_empty_` (the only all-day sample) gains the Until chip;
    - `editor_more_options_` (if its sample is all day);
    - `editor_locked_` (its sample is now timed).
  - The timed samples (`editor_keyboard_`, `editor_child_`, `editor_failed_`, `editor_repeats_`) must not change.
  - New: `editor_until_dark` and `editor_until_light` (Mon 19 – Thu 22, the picker closed), `editor_until_error_dark` (past 60 days), `editor_span_edit_dark` (the half term edit).
  - Check against 4a, §2.1 and note 31.
```bash
git add capability/calendar
git commit -m "Add the Until chip so an all-day event can run over several days, and let such an event's dates be changed on the wall"
```

---

### Task 11: The emulator walk-through on `culvery-5`

**Review:** the controller, with the user at the emulator. (The workflow runs `simplify` over the branch first, then the gate.)

**Rules.** As Plan 9a's Task 15:
- Use only the `culvery-5` AVD, started with `-no-snapshot-save -camera-front emulated` (check `adb -s "$S" emu avd name`).
- It holds a debug build, so `install -r` the debug APK.
- Never uninstall, and never run a Gradle install task while a device is connected.
- The user types every PIN.
- Put back every setting changed.

- [ ] **Step 1: The AVD and the build.** Run `./gradlew :app:assembleDebug`, then `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2: Walk the checklist** (one line each).
  1. Every right-hand sheet (event detail, Add event, a person, Favourites) is the wider sheet with the darker scrim and the edge.
  2. Add event shows the new header, Pick a date and Pick a time with no "…", and Time beside Length. The footer is greyed with "Add a title first" until a title is typed.
  3. Signed in as the sample child:
     - the other Who chips are locked with the padlock;
     - the line sits under them;
     - a tap on one says "Mia can add events for Mia only." (the sample's child name);
     - no Repeats.
  4. Repeats, as an adult:
     - More options opens Every day, Every month, Every year and Ends;
     - After 10 times comes with Fewer and More, and holding More runs the count up to 52 and stops;
     - the sheet scrolls only once More options is open;
     - a weekly event ending after 3 times shows 3 times on the week.
  5. Until:
     - All day with Until in three days shows one bar across the week (9b's lane);
     - "Day 2 of 3" shows on Home on its second day (set the AVD's date if needed, and put it back);
     - Until past 60 days says so and Save waits;
     - opened again, Until is editable, and moving the day moves Until with it.
  6. A timed event over midnight opened from the phone's calendar (or the fake's) still shows the locked dates line.
- [ ] **Step 3: The fault loop.** Each fault gets a failing test, the fix, the gate, a commit naming it, `install -r`, and a re-check.
- [ ] **Step 4: Put back** every setting, then `adb -s "$S" emu kill`.
- [ ] **Step 5: Record** under "Plan 9c-1 (Redesign: Add event): emulator walk-through".
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9c-1's emulator walk-through"
```

---

### Task 12: On the tablet

**Review:** the controller with the user. **Needs the SM-T510 on the cable.** 4e's tablet rules apply: save and restore every setting changed, signed releases with `adb install -r`, never uninstall, and the names check before any commit.

- [ ] **Step 1: Install the release**, signed per `docs/setup/release.md`. Keep `mapping.txt` in `culvery-notes/mappings/`.
- [ ] **Step 2: At the wall** (the user):
  - Add event reads at arm's length;
  - a real multi-day all-day event made on the wall shows across its days in Google Calendar on a phone;
  - a series ending after N times shows N times there;
  - the child's locked chips read as locked, not broken.
- [ ] **Step 3: Record** "Plan 9c-1 (Redesign: Add event): on the tablet": what read and what didn't. No names.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9c-1 on the tablet"
```

---

### Task 13: Final review, merge, install

- [ ] **Step 1: The review.** Opus reviews the whole branch against spec §2.1, §4.8 and §6.4 and this plan. One fix wave follows, each fix with its failing test first and the gate.
- [ ] **Step 2: Merge**, on the user's say: `git merge --ff-only plan-9c-forms` on `main`, then the gate on main. Never push. 9c-2 then branches from it.
- [ ] **Step 3: Install** the release from the merged `main` on the tablet with `install -r`.

---

## Spec coverage (9 design → tasks)

| Spec | Task |
|---|---|
| §2.1 multi-day: creating them in Add event | 5, 10 |
| §4.8 the sheet from the right | 2, 6 |
| §4.8 title field, section labels, chips | 2, 6 (ruling 2: chips stay 48 dp) |
| §4.8 a child's locked Who chips and the line (note 33) | 3, 8 |
| §4.8 "Pick a date", "Pick a time" (note 34) | 6 |
| §4.8 Time and Length side by side | 6 |
| §4.8 Repeats with More options and the Ends row, scrolling once open (note 35) | 4, 9 |
| §4.8 the footer primary; disabled outline with the reason above (note 36) | 3, 7 |
| §6.4 one disabled style; reason optional | 3, 7 |
| §5 pickers and detail sheet unchanged but for shared tokens | 2, 7 (ruling 18) |
| §7 images looked at; CopyScanTest; TalkBack | every UI task |
| §4.11, §4.12 | 9c-2 |
