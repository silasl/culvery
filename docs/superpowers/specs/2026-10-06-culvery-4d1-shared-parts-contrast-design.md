# Culvery 4d-1: shared parts, contrast and layout bugs (design)

**Date:** 2026-10-06
**Status:** Approved (2026-10-06); amended at the plan review (§6.6's status bar, §7 and Decision 10's baselines). Plan: `docs/superpowers/plans/2026-10-06-culvery-04d1-shared-parts-contrast.md`.
**Parent spec:** `docs/superpowers/specs/2026-09-23-culvery-v1-design.md`
**Previous plan:** `docs/superpowers/specs/2026-10-02-culvery-4c-release-fixes-design.md` (4c, merged)
**Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (its "From the 4d triage" section holds 4d-2, the walk-through, the new 4e items and what was dropped)
**Builds on:** `main` at b7cb40d.

## 1. Goal

Make the app's controls one family with built-in roles and touch sizes, make every text and icon pass contrast in both themes, stop colour being the only way to tell whose event it is, and fix the layout and state bugs found by the 4d audits (the clock box, the header width, the weather that never fails, the detail sheet, the PIN pad, and fixed sizes that clip at larger font scales).

The audits ran on 2026-10-06 (copy, screens, hand-off, accessibility, live emulator) and were triaged into 41 items (D1–D41). This spec takes D1–D7 and D9–D14. Item ids below are the triage's; decisions are numbered 1–10.

## 2. Scope

**In (4d-1, about 10 tasks):**
- D1–D3: one shared button, list row and section label in `:core:ui`.
- D4–D7: contrast tokens, names on event chips and Coming up rows, faint affordances.
- D9–D14: the clock box, the header width budget, the weather failed state, the detail sheet scroll, the PIN pad at large font scales, fixed sizes made minimums.
- A docs change: device owner as the wall setup (Decision 5).
- Re-recording the Roborazzi baselines in each task that changes them, after a compare (amended at the plan review, §7).

**Out:**
- **4d-2, "TalkBack, timeouts, words"** (a later plan): D15–D41, including the overlay idle close (Decision 4) and the copy sweep with curly quotes (Decision 6). Listed in the follow-ups file.
- **D8, the person palette under colour-blindness:** not done (Decision 2); palette swaps are walk-through (W30).
- **The end-of-v1 design walk-through** (the user's own, after 4e and before the v1 release): W1–W34, every taste call, including those this plan's areas touch: the empty Home grid slot (W2), the Forecast card's size and span (W3), `opsz` (W1), the splash flash (W9), the outlined disabled button (W14).
- **4e (the SM-T510):** E1–E10, every device check.

## 3. Decisions

| # | Decision |
|---|---|
| 1 | **4d is split** (user, 2026-10-06). 4d-1 = "shared parts, contrast, layout bugs" (D1–D7, D9–D14). 4d-2 = "TalkBack, timeouts, words" (D15–D41), a later plan with its own spec. Taste and visual calls wait for the user's own design walk-through at the end, before the v1 release (after 4e). Device checks go to 4e. 4d-1 goes first because one button with `Role.Button` built in cuts the call sites 4d-2's role and label pass has to touch. |
| 2 | **Person colours** (user; triage Decision 2, option A). The person's name goes on week chips ("09:30 · Sam") and on Coming up rows' time line, as Today rows already show it. The line is drawn in `mute`; when space is short the name truncates first, then the time (one line, end ellipsis). The person colour stays as the chip tint and the row bar. No palette change: D8 is not done, and palette swaps are walk-through (W30). |
| 3 | **Light theme tokens** (user; triage Decision 3, option A). Light `accent` becomes `#23704F`; white `accentInk` on it stays. A new `weather` token replaces the `SunAmber` constant: light `#8A6400`, dark today's amber `#E0B85B`; it draws the header weather icon and the Forecast icons. A selected chip's secondary text is drawn at full alpha, not 72%. Dark is otherwise unchanged. |
| 4 | **Overlays left open after a stray tap** (user; triage Decision 4, option A): any overlay with no edits closes after 2 minutes idle. This is a timeout, so it belongs to 4d-2 (D32). The full-column add target in the week view stays (W28 at the walk-through). |
| 5 | **Unpinned windows without device owner** (user; triage Decision 5, option A; 4c ruling 10). Android's home-role dialog and Google's account chooser both leave Culvery unpinned briefly when it isn't device owner. `docs/setup/release.md` documents device owner as the wall setup and accepts the gap for plain screen pinning. No re-pin timer. Done as a docs task in 4d-1. |
| 6 | **House style** (user; triage Decision 6, option A). One shared uppercase section label, `HhSectionLabel`, with heading semantics, spoken in sentence case ("Who"). Curly quotes and apostrophes (’ “ ”) everywhere: the sweep of existing strings is 4d-2's copy work (D33); 4d-1 uses them in every string it writes or touches. |
| 7 | **Vocabulary** (triage Decision 1: master, kiosk, sync, Google, add/edit, assign, read-only): **still to decide**, before 4d-2's copy is written. Options and recommendations are in the follow-ups file. 4d-1 adds no new visible terms from that list. |
| 8 | **One disabled look** for every button tone: `mute` text on the tone's quiet fill (§4.1). It settles the Plan 4a follow-up: DeleteButton stops keeping its red text while disabled. |
| 9 | **The quiet button's fill depends on what it sits on** (this spec's call, §4.1): one step above its surface, so on light `bg` it is `surf`, not `surf2`. |
| 10 | **No layout changes beyond row and button heights**, except the fixes named in §6 (the clock box moves Home up 21 dp; the detail sheet scrolls; minimum sizes replace fixed ones). Visible changes besides those: the rulings' own (names on chips, the deeper light green, the weather colour, capital section labels in the person sheet) and button labels folding to two sizes (§4.1). The Roborazzi baselines are re-recorded in each task that changes them, after Roborazzi's compare and a look at every difference, and the user sees every changed baseline at the end (amended at the plan review, user 2026-10-06; was "once, at the end of the plan"). 4d-2 re-records again. |

## 4. Shared parts (D1–D3)

### 4.1 `HhButton` (D1)

**Problem.** Seven button implementations, 44–60 dp tall, none with `Role.Button`, and two disabled styles (DeleteButton keeps its red text; HhSheetButton greys out). In light, a plain button on `bg` is `surf2` on `bg`, 1.04:1, so Back, a disabled Next, Exit kiosk and Set your PIN have no visible edge, and a disabled Next looks like a plain one (screens S1).

**Evidence.** `core/ui` Components.kt:51-75 (`HhPillButton`, about 46 dp: 15 sp plus 2 × 13 padding), Controls.kt:224-260 (`HhSheetButton`, 60 dp, five tones); calendar ui/Components.kt:25-128 (`HeaderChip` 44 dp, `DeleteButton` 60 dp, `PrimaryButton` 60 dp 18 sp, `AddButton` 48 dp); Pickers.kt:275-296 (`PickerButton`, 52 and 56 dp); ReviewCalendars.kt:232 (Reconnect is `AddButton` at 48, Disconnect beside it is `HhPillButton` at about 46). `CalendarDimens.touchTarget` 44 (Week pill, Today +, Reconnect, This week) and `assignButtonHeight` 44 (CalendarType.kt:117, 237); `addHintMinHeight` 40 (CalendarType.kt:321).

**Fix.** One `HhButton` in `:core:ui`:

```kotlin
@Composable
fun HhButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Quiet,
    size: ButtonSize = ButtonSize.Regular,
    icon: String? = null,
    enabled: Boolean = true,
    on: ButtonSurface = ButtonSurface.Surf,
)
```

- **Sizes.** `Regular`: 48 dp tall, radius 24, 15 sp / 700 label, padding as `HhPillButton` and `AddButton` today (0 20, or 0 20 0 14 with a 24 dp icon 6 from the label). `Large`: 60 dp, radius 30, 17 sp / 700, padding and icon gap as `HhSheetButton` (hand-off §7).
- **Tones.** The approved families are primary, quiet and danger; `ButtonTone` keeps the shapes the screens draw today:
  - `Primary`: `accent` fill, `accentInk` label.
  - `Quiet`: the quiet fill (below), `ink` label. Today's `Plain` and `Quiet` become this one tone.
  - `Danger`: the quiet fill, `danger` label and icon (Delete).
  - `Destroy`: `danger` fill, `dangerInk` label. Only the confirm card's confirming button uses it.
- **The quiet fill (Decision 9).** One step above the surface the button sits on, given by `on`:
  - `Bg` (wizard frame, Settings pages): light `surf` (#FFFFFF on #EDF0EE, 1.15:1), dark `surf2` (1.28:1). Light `surf2` would be 1.04:1, which is the S1 problem.
  - `Surf` (sheets, cards, list rows; the default): `surf2` in both themes (light 1.11:1, dark 1.14:1), as today.
  - `DangerSoft` (the confirm card's Keep): `surf` in both themes (light 1.27:1, dark 1.14:1), as today's `Quiet`.
  - Why `surf` on light `bg`: it is the only existing light token visibly apart from `bg` that is lighter than it, so the button reads as raised like a card does (cards are `surf` on `bg` too). No new token. The rule lives in one function, `quietFill(on, colors)`, which the contrast test checks (§5.5). Button fills carry no WCAG ratio (the label identifies the button); the test holds each quiet fill at ≥ 1.1:1 against its surface so none vanishes again.
- **Disabled (Decision 8).** Every tone: the quiet fill for its surface and a `mute` label and icon; does nothing. Telling disabled from quiet by more than the label colour (an outlined style, S1) is walk-through W14.
- **Semantics.** `clickable(role = Role.Button, enabled = enabled)`; the touch target is the 48 or 60 dp box, so never under 48.
- **Callers.** `HhPillButton`, `HhSheetButton`, `PrimaryButton`, `DeleteButton`, `AddButton`, `HeaderChip` and `PickerButton` go; callers call `HhButton`. `DeleteButton` may stay as a one-line wrapper (`HhButton("Delete", tone = Danger, size = Large, icon = Icons.DELETE)`) if more than one sheet uses it. Mapping:
  - Regular: Close, Back, Next, Skip, Disconnect, Make master, Hide this calendar, Set your PIN, Exit kiosk, Open settings (was `HhPillButton`, about 46 → 48); Add event, Connect, Reconnect (`AddButton`, 48); the Week pill, This week, the Reconnect chip (`HeaderChip` and the 44 dp chips, 44 → 48).
  - Large: sheet footers (Save, Save changes, Edit, Delete, Keep, Delete event, Try again) and the pickers' and Connecting card's buttons (`PickerButton`, 52 and 56 → 60).
  - Label sizes fold to two: `PrimaryButton` 18 → 17 sp, `PickerButton` 16 → 17 sp, `HeaderChip` 14 sp / 600 → 15 sp / 700.
- **Not buttons with text, raised to 48 dp:** `CalendarDimens.touchTarget` 44 → 48 (Today's round +), `assignButtonHeight` 44 → 48 (Assign to…), the week column's add hint `addHintMinHeight` 40 → 48.

**Tests.**
- Compose (Robolectric), over tone × size × enabled × surface: the node has `Role.Button`, `assertTouchHeightIsAtLeast(48.dp)`, a disabled one has no click effect and the `mute` label.
- Roborazzi: one button matrix baseline per theme (tones down, Regular and Large across, enabled and disabled, on `bg` and on `surf`).
- The Settings, wizard and sheet baselines are re-recorded (heights move by 2–16 dp).

### 4.2 `HhListRow` (D2)

**Problem.** Two list-row systems at different sizes on one Settings screen: setup rows are 17 / 14 sp with padding 16 × 20; Settings › Calendars rows are 18 / 15 sp with padding 16 × 20 (connections) or 14 × 20 (calendars), gaps 12. "Alex" and "Google Calendar" sit at different sizes on the same page (screens S40).

**Evidence.** SetupDimens.kt:30-37 (rows: `surf`, radius 18, padding 16 × 20, 10 apart, 16 dp dot 16 from the text, second line 2 below, 24 dp end icon); CalendarType.kt:27, 105 (`settingsRowTitle` 18 sp, `subtitle` 15 sp); CalendarDimens.kt:336-350; PeopleUi.kt; ReviewCalendars.kt (`ConnectionHeader`, `SourceRow`); LocationPane.kt (`PlaceRow`).

**Fix.** `HhListRow` in `:core:ui` on setup's values:

```kotlin
@Composable
fun HhListRow(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    subtitleColor: Color = Culvery.colors.mute,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
)
```

- `surf`, radius 18, padding 16 × 20; leading 16 from the text; title `HhType.rowTitle` (17 sp / 600, one line, ellipsis); subtitle `HhType.secondary` (14 sp / 400) 2 below; trailing items 12 apart. A row with `onClick` is `clickable(role = Role.Button)`. Lists keep their own 10 dp gap.
- Two additions to the approved parameters, both needed by Settings › Calendars: `subtitleColor` (a connection's health line is `danger` when it needs a reconnect) and `below` (a calendar row's Master badge line or Make master button, its read-problem line and its person picker sit under the row's top line). The plan may make `subtitle` a slot instead of adding `below` if the Master line fits there better; the sizes are what matter.
- Used by: people (`PersonRow`), towns (`PlaceRow`), connection headers and calendar rows. The calendar rows go from 18 / 15 sp to 17 / 14 sp and from 14 × 20 to 16 × 20 padding.

**Tests.** A compose test that a row's title and subtitle use `rowTitle` and `secondary` (one row from each caller, by tag); re-recorded `settings_people`, `settings_calendars` and location baselines.

### 4.3 `HhSectionLabel` (D3)

**Problem.** Two section-label styles: the person sheet's "Name / Colour / Role / PIN" (13 sp / 700, title case) and the event editor's "WHO / DAY / TIME / LENGTH" (13 sp / 700, 0.5 tracking, typed in capitals). Some TTS engines spell "WHO" out or say "W.H.O." (copy C42, screens S41).

**Evidence.** SetupDimens.kt:81 (`SetupType.label`); CalendarType.kt:90 (`sectionLabel`); EventEditorSheet.kt:245-329; PersonEditorSheet.kt:248.

**Fix (Decision 6).** `HhSectionLabel(text: String, modifier)` in `:core:ui`, with the style moved to `HhType.sectionLabel` (13 sp / 700, 0.5 tracking). Callers pass sentence case ("Who", "Day", "Name"); the component draws `text.uppercase(Locale.UK)` and sets `semantics { heading(); contentDescription = text }`. Both sheets use it, so the person sheet's labels become capitals as the hand-off draws them. These are the only headings 4d-1 adds; the rest are 4d-2's D19.

**Tests.** A compose test: the label shows "WHO", is a heading, and its content description is "Who".

## 5. Contrast, and colour not the only cue (D4–D7)

Normal text needs 4.5:1; large text (≥ 18.66 sp bold or 24 sp) and icons or other non-text parts need 3:1. Ratios are WCAG 2.x, with any alpha blended over the surface the code draws on. The figures before and after are in Appendix A.

### 5.1 Week chips and Coming up rows (D4)

**Problem.** A week chip draws its time in the person's colour on that colour's own tint: 1.83–3.32:1 in light for all nine colours, and 3.47–4.37:1 in dark for blue, violet, red, slate and brown (accessibility A21, the audit's one blocker; live L8 measured 2.28 and 1.91). Whose event it is shows only by colour on chips and on Coming up rows, and under deuteranopia lime ≈ Family amber (ΔE 2.6) and blue ≈ violet (ΔE 4.4) (A36).

**Evidence.** WeekView.kt:238-257 (`EventChip`: `startLabel` in `colour`, chip `colour.copy(alpha = tint)`); ComingUpCard.kt:97-113 (`CompactRow`: time only); TodayCard.kt:107 (Today rows already show "{time} · {name}" in `mute`); CalendarType.kt:357-359 (tints 0x2E dark, 0x26 light).

**Fix (Decision 2).**
- Week chip: the first line becomes one `Text("${event.startLabel} · ${event.person.name}")` in `mute`, `CalendarType.chipTime` (12 sp / 700), `maxLines = 1`, end ellipsis, so the name truncates first and then the time. All-day chips read "All day · Sam". Badges stay after it. The chip keeps its tint; the title stays `ink`.
- Coming up row: the time line becomes "${event.timeLabel} · ${event.person.name}", `mute`, one line, end ellipsis, as Today rows. The colour bar stays.
- `mute` on every tint: 4.55–5.26 dark, 5.18–5.54 light. Lowest: dark lime 4.55 and Family 4.60, pinned in the test.
- The chip tint alphas move from `CalendarDimens` to `:core:ui` beside `PersonPalette`, so the contrast test reads the values the code draws.

**Tests.** The contrast test (§5.5) covers `mute` and `ink` on every palette tint and Family, both themes. A compose test: a chip and a Coming up row contain the person's name. Re-recorded week and Home baselines.

### 5.2 Light accent, chip secondary text, the chosen Settings page (D5)

**Problem.** Light `accent` (#2E8A64) fails as text: 3.71 on `bg` (Sign out, 12 sp), 4.25 on `surf` (the week column's "Today"), 3.44 on `accentSoft` (the chosen Settings page, the Master badge); and white `accentInk` on it is 4.25 on every primary button (15–18 sp bold, below the large-text threshold) (A22). A selected chip's secondary text is `accentInk` at 72%: 2.99 light, 4.28 dark (A22). The chosen Settings page differs from the others by tint alone, `accentSoft` vs `surf` at 1.24–1.26 (A37).

**Evidence.** core/ui Colors.kt:47 (light `accent`); Controls.kt:205 (72% secondary); StatusBar.kt:63; WeekView.kt:178-182; ReviewCalendars.kt:266-274; SettingsScreen.kt:102-106.

**Fix (Decision 3).**
- Light `accent` = `#23704F`: 5.23 on `bg`, 6.00 on `surf`, 5.41 on `surf2`, 4.85 on `accentSoft`; white on it 6.00. `accentSoft` stays #D3EDE1. The light green gets visibly deeper; dark is unchanged.
- Chip secondary text at full alpha: 6.00 light, 7.11 dark.
- The chosen Settings page's label is W700 as well as tinted (others stay W600).
- Borderline pairs that pass today are pinned so a later tweak can't slip: light `danger` on `dangerSoft` 4.50, light `mute` on `surf3` 4.70 (A25).

**Tests.** The contrast test; a compose test that the chosen Settings page's label is W700. Re-recorded light baselines everywhere `accent` shows.

### 5.3 The weather colour (D6)

**Problem.** Weather icons are `SunAmber` (#E0B85B) in both themes: 1.88 on light `surf` and 1.64 on light `bg`, and the icon is the only visual cue for the condition (A23).

**Evidence.** Colors.kt:55-56 (`SunAmber`, "the same in both themes, so not an HhColors token"); ForecastCard.kt:91; WeatherHeaderItem.kt:29.

**Fix (Decision 3).** `HhColors.weather`: light `#8A6400` (4.69 on `bg`, 5.38 on `surf`, 4.86 on `surf2`), dark `#E0B85B` (10.15 on `bg`, 9.03 on `surf`). `SunAmber` goes; both icons use the token. The user's ruling also names "High": in the code the header's "High 19° · Low 11°" line is `mute` and nothing draws "High" in amber, so nothing else changes; if the walk-through wants an amber "High", it uses this token, which passes as text in both themes. Condition-tinted icons are walk-through W7.

**Tests.** The token's pairs are in the contrast test (as icons: ≥ 3:1; as text, should it be used for "High": ≥ 4.5:1). Re-recorded weather baselines.

### 5.4 Faint affordances (D7)

**Problem (A24, A37, S3).**
- The week column's add hint is `mute` at 50%: 2.64 dark, 2.15 light.
- Past date cells are drawn at 30%: 2.49 dark, 1.91 light. They are still selectable, so the "inactive" exemption doesn't apply.
- Inactive wizard dots are `surf3` on `bg`: 1.54 dark, 1.13 light; the current step is shown by colour alone.

**Evidence.** WeekView.kt:233 (`ADD_HINT_ALPHA` 0.5); Pickers.kt:220 (`PAST_DAY_ALPHA` 0.3, the whole cell layer: `ink` on `surf2` over the picker's `surf`); SetupWizard.kt:212.

**Fix.**
- The add hint at full `mute`: 6.56 dark, 6.12 light. `ADD_HINT_ALPHA` goes.
- Past date cells at **62%**: 5.80 dark, 4.83 light. The approved 60% blends to #707372 on #F7F8F7 in light, 4.499:1, a hair under 4.5 (the cell text is 16 sp / 600, normal size), so the plan uses 0.62. `PAST_DAY_ALPHA` moves to `:core:ui` with the chip tints so the test reads it.
- Wizard dots: the current step a filled `accent` dot (7.36 dark, 5.23 light on `bg`); the others 1.5 dp `mute` outlines (7.38 dark, 5.33 light). Outline vs fill is the non-colour cue. Dot size (12 dp, W16) and a visible "Step 2 of 5" text are not changed here; the spoken "Step n of m" is 4d-2's D27.

**Tests.** The pairs in the contrast test; re-recorded wizard frame and date picker baselines.

### 5.5 The contrast test

One JVM test in `:core:ui` (`ContrastTest`), beside `PersonPaletteTest`. It lists, as data, every text and background pair the code draws and every icon or non-text pair that carries meaning, in both themes, and asserts ≥ 4.5:1 for normal text and ≥ 3:1 for large text and icons. It includes:
- `ink` and `mute` on `bg`, `surf`, `surf2`, `surf3`, `accentSoft`, `dangerSoft`; `accent` as text on `bg`, `surf`, `surf2`, `accentSoft`; `accentInk` on `accent`; `danger` on `bg`, `surf`, `surf2`, `dangerSoft`; `dangerInk` on `danger`; `bg` on `ink` (toasts).
- `mute` and `ink` on every person tint and Family, both themes.
- `weather` on `bg`, `surf`, `surf2` (≥ 3:1 as an icon; also ≥ 4.5:1, so it is safe as text).
- The add hint, past date cells (at the moved alpha), wizard dots (`accent` and `mute` vs `bg`).
- Each `quietFill(on, colors)` vs its surface ≥ 1.1:1.
- Pinned edges: light `danger` on `dangerSoft` (4.50), light `mute` on `surf3` (4.70), dark `mute` on the lime and Family tints (4.55, 4.60), light past cells (4.83).

A helper (`contrast(fg, bg)` and `over(alpha, surface)`) computes WCAG relative luminance with 8-bit blending, as the screen does.

## 6. Bugs and font scale (D9–D14)

### 6.1 The clock box (D9)

**Problem.** `HhType.clock` has `lineHeight = 93.6.sp` with `LineHeightStyle(Alignment.Center, Trim.Both)`. In Compose 1.9.1, `Trim` removes the line-height adjustment from the first line's top and the last line's bottom; here the adjustment is negative (93.6 < 135), so `Trim.Both` on one line restores the font's full ascent and descent and the 93.6 sp has no effect. The clock box is 135 dp tall, not 94; the clock's cap-top is at 84 dp, not 61; the date, the header items and the grid sit 20–23 dp low (grid top 227 vs 207 dp; each grid row about 9 dp shorter, 267 vs 276). The clock-to-date ink gap already matches the hand-off (28 dp; baselines 44 apart). The Plan 1 follow-up ("~55 dp vs ~12 dp") is stale: it predates 2a placing the date by baseline.

**Evidence.** core/ui Type.kt:38-42; HomeScreen.kt:34-35, 52 (the KDoc "the clock's line-height trim doesn't apply reliably"), 70; live `home_clock` bounds 135 dp tall at every font scale; Compose `LineHeightStyleSpan.calculateTargetMetrics` (hand-off audit H1).

**Fix.** A new `core/ui` `Modifier.lineBox(height)` (`LineBox.kt`) on the clock. It measures the text, reports the box as the style's 93.6 sp line height, and places the text centred in it, so glyphs may draw outside the box and the baselines move with the offset: the baseline is 82 dp from the box top, as CSS's half-leading puts it (82.3). `HhType.clock` is unchanged. *(Amended in Task 7: the first plan was `LineHeightStyle(Alignment.Center, Trim.None)`, but Compose won't shrink one line below the font's ascent plus descent through the style, so `Trim.None` left the box at 135 dp.)* The 44 dp baseline-to-baseline placement stays. The date, header items and grid move up 21 dp, onto the hand-off. Correct the KDoc at HomeScreen.kt:52 and close the Plan 1 follow-up.

**Tests.** A compose test: `home_clock` is 94 dp ± 1 tall. Re-recorded Home baselines, one of them with `onAdd` set so Today's + is covered (the live audit found the baseline stale on this, hand-off H6).

### 6.2 The header width budget (D10)

**Problem.** `HomeHeader` measures the right-hand items with `loose` constraints (the full width) and places them at `width − right.width`, reserving nothing for the clock or date. Today the weather item (about 180 dp) fits beside a 217–277 dp clock in 1116 dp, but a Climate item, a long string or a larger font scale can push it onto the date (H2, L3, S34, Plan 4b follow-up).

**Evidence.** HomeScreen.kt:66-77.

**Fix.** Measure the items with `maxWidth = constraints.maxWidth − max(clock.width, date.width) − 28.dp` (the hand-off's gap). Each item's text is one line with an end ellipsis (`WeatherHeaderItem`'s temperature and High · Low lines). The bottom alignment to the date is unchanged.

**Tests.** A compose test at font scale 1.3 with a wide fake item: its bounds don't intersect `home_date`, and its text is one line.

### 6.3 The weather's failed state (D11)

**Problem.** `Waiting` means "a location is set and nothing stored matches it". If the first fetch fails (offline at setup, a bad network), the view stays `Waiting` and the card says "Getting the forecast…" all day; `WeatherSyncLoop` only logs and retries (S31).

**Evidence.** WeatherView.kt:52 (`?: return WeatherView.Waiting`); WeatherSyncLoop.kt:80-99; ForecastCard.kt:67; WeatherWords.kt.

**Fix.**
- `WeatherSyncLoop` exposes the place whose last fetch failed (`lastFailure: StateFlow<WeatherPlace?>`, null at start and after a success; plan ruling 27).
- `weatherView` returns a new `WeatherView.Failed` when a location is set, nothing stored matches it and the last attempt failed. With stored data the view is as today, whatever the last attempt did.
- The Forecast card in `Failed` reads "No forecast yet — check the tablet’s Wi-Fi." under its usual title. The header item stays hidden, as for `Waiting`.
- The next successful fetch stores data, so the view becomes `Ready` and replaces the message. A retry in progress keeps `Failed` until it finishes.

**Tests.** Unit: `weatherView` with a location, nothing stored and a failed attempt is `Failed`; with nothing stored and no attempt yet is `Waiting`; with stored data and a failed attempt is the stored view. Roborazzi `forecast_waiting` and `forecast_failed` baselines (neither state has one today).

### 6.4 The detail sheet scrolls (D12)

**Problem.** The event detail sheet is a plain Column: a long title (34 sp, no line limit), five 58 dp info rows, a note and the confirm card can push Delete and Edit below the screen at font scale 1.3, where nothing reaches them (A28, L10, hand-off D2, Plan 2b-1 follow-up).

**Evidence.** EventDetailSheet.kt:75-152 (`HhSheet` with `Header`, `InfoCard`, the note, then `Spacer(weight(1f))` and the footer); the editor already scrolls its body with the footer pinned (EventEditorSheet.kt:125-131).

**Fix.** The header, info card and note (with the Assign chips) go in `Column(Modifier.weight(1f).verticalScroll(rememberScrollState()))`; the footer (Delete and Edit, or the confirm card) stays pinned below it; the `Spacer(weight)` goes. The header scrolls with the body: the title has no line limit, so pinning it could fill the sheet; the scrim and Back still close the sheet.

**Tests.** Robolectric at font scale 1.5 with a long title and notes: `detail_edit` is displayed, and the body scrolls to the last info row.

### 6.5 The PIN pad at large font scales (D13)

**Problem.** The Cancel key's label wraps to "Cance / l" and clips at 1.5 (it fits at 1.3 by about 9 dp in a 76 dp key). The error line is a fixed 18 dp, while 14 sp DM Sans is already about 18 dp tall at 1.0, so "Wrong PIN — try again" clips from about 1.15 and is mostly gone at 1.3; `maxLines = 1` also cuts "{name} can’t do that" for long names (L11, A29).

**Evidence.** PinPad.kt:217-240; PinPadDimens.kt:28 (`errorLine` 18 dp).

**Fix.**
- Cancel: `maxLines = 1, softWrap = false`, drawn without clipping to the key's circle, so a label wider than the key overflows it rather than wrapping.
- The error line: `heightIn(min = …)` reserving two lines of `PinPadType.error` at the current font scale (so the keypad doesn't jump when an error appears), `maxLines = 2`.

**Tests.** Robolectric at 1.5, reading each text's layout through `SemanticsActions.GetTextLayoutResult`: the Cancel label has one line; `pin_error`, showing a refusal with a long name, has no visual overflow.

### 6.6 Fixed sizes become minimums (D14)

**Problem.** Fixed sizes clip from font scale 1.3 (A26, A30–A33, A35, H5):
- The status bar is a fixed 30 dp; Sign out (12 sp plus 6 dp padding) is visibly about 28 dp and the theme toggle about 24 dp (live: 56 × 39 dp touch at 1.0); from 1.4 the text overflows the bar.
- Forecast rows are 44 dp tall and each temperature 44 dp wide: at 1.3 "-12°" wraps and clips; at 1.5 the high and low run together ("22° 14°").
- The detail sheet's label column is a fixed 104 dp, one line with no ellipsis: "Created by" and "Calendar" clip at about 1.3.
- The week title is one line with no ellipsis; `HhChoiceChip`'s label, the editor summary and the Review health line are one line with no ellipsis, or cut the wrong part.

**Evidence.** StatusBar.kt:48, 60-79; WeatherDimens.kt:14, 17; ForecastCard.kt:86-94; EventDetailSheet.kt:232; WeekView.kt:115; Controls.kt:203; EventEditorSheet.kt:207; ReviewCalendars.kt:230.

**Fix.**
- Status bar: `heightIn(min = 30.dp)`. Sign out and the theme toggle keep their visible size and rely on Compose's minimum touch target, which already takes a touch up to 48 dp around a smaller clickable. *(Amended at the plan review, user 2026-10-06: `minimumInteractiveComponentSize()` reserves 48 dp of layout, which would make the bar 48 dp and push Home down 18 dp.)*
- Forecast rows `heightIn(min = 44.dp)`; temperatures `widthIn(min = 44.dp)`, `maxLines = 1, softWrap = false`, with an 8 dp gap between high and low so they never run together.
- The detail label column `widthIn(min = 104.dp)`, up to two lines, with an 18 dp gap before the value (`CalendarDimens.infoLabelGap`, added in Task 10 so the value column is the hand-off's 204 dp).
- The week title, `HhChoiceChip`'s label and the Review health line: an end ellipsis; the chip `heightIn(min = 48.dp)` with up to two lines; the editor summary up to two lines, so the time isn't cut first.

**Tests.** Robolectric at font scale 1.3 and 1.5 for Home, Week, Detail and the PIN pad: named nodes lie inside their parents; the status bar stays 30 dp at 1.0 and a touch just outside Sign out's text still signs out (`assertTouchHeightIsAtLeast` can't fail for any clickable, as Compose pads every clickable's touch bounds to 48 dp). Roborazzi baselines at 1.5 for Home, Week, Detail and the PIN pad (`*_fs15`).

**Left out (walk-through):** the empty Home grid slot (W2), the Forecast card's size and span (W3), `opsz` for large text (W1), the splash's dark flash in the day theme (W9; also checked on the device, E3). Capping the clock's font scale waits for the device (E1): Robolectric runs at SDK 35, whose non-linear scaling already caps the 104 sp clock, while the SM-T510's Android 11 scales it linearly to 156 sp.

### 6.7 Device owner as the wall setup (Decision 5)

`docs/setup/release.md`:
- §6's title drops "(optional)" and becomes "Device owner (the wall setup)"; its first line says this is how the wall tablet should be set up.
- A short paragraph after it: without device owner, Culvery runs in screen pinning, and two moments leave it unpinned: Android's "Choose home app" dialog, and Google's account chooser during Connect or Reconnect. Both need an Admin PIN to reach and both are modal; a Home press there leaves Culvery unpinned until it next comes to the front. This is accepted for screen pinning; there is no re-pin timer.
- The Plan 4c code-minor follow-up on the unpinned window is closed by this.

## 7. Testing strategy

- **Order.** Components first (§4), so later tasks use them; tokens and the contrast test next (§5); bugs and font scale last (§6). Each task adds its tests. Each task that changes baselines runs Roborazzi's compare, looks at every difference against its list of expected changes, then re-records them; an unexpected difference is a stop. The final task shows the user every baseline changed by the plan, light and dark, with a line on why. *(Amended at the plan review, user 2026-10-06; was "re-recorded once, in the final task".)*
- **New tests.** The `HhButton` matrix (compose and Roborazzi); `HhListRow` styles; `HhSectionLabel` heading and spoken text; `ContrastTest`; chip and Coming up names; the chosen Settings page weight; the clock height; the header budget at 1.3; `weatherView`'s `Failed`; the detail sheet, PIN pad and status bar at 1.3 and 1.5; `forecast_waiting`, `forecast_failed` and the `*_fs15` baselines.
- **Font scale.** Robolectric's `RuntimeEnvironment.setFontScale` at 1.3 and 1.5. At SDK 35 large sizes scale non-linearly, so these tests cover body text, not the clock (E1).
- **Gate.** `./gradlew testDebugUnitTest verifyRoborazziDebug` after the re-record.
- **Emulator walk-through** (debug build, light theme, as the live audit): Home at 1.0, 1.3, 1.5 (clock position, header, Forecast rows, status bar); a week with events (names on chips, contrast); the detail sheet with a long title at 1.5; the PIN pad at 1.5; the wizard dots; Settings people and calendars rows; a forced weather failure (Wi-Fi and data off with `svc wifi/data disable`, never airplane mode, with a fresh location) showing the failed line, then recovering.

## 8. Plan shape

Eleven tasks, the last a wrap-up:
1. `HhButton` and its tests.
2. Move every caller to `HhButton`; delete the old buttons; raise `touchTarget`, `assignButtonHeight` and the add hint to 48.
3. `HhListRow` and `HhSectionLabel`; move people, towns, connections, calendars and both sheets' labels.
4. Tokens: light `accent`, `weather`, chip secondary alpha; move the alphas to `:core:ui`; `ContrastTest`.
5. Names on week chips and Coming up rows (D4).
6. Faint affordances and the chosen Settings page (D5 part, D7).
7. The clock box and the header width budget (D9, D10).
8. The weather's failed state (D11).
9. The detail sheet scroll and the PIN pad (D12, D13).
10. Fixed sizes to minimums and the status bar (D14); font-scale tests.
11. The `release.md` change, follow-ups closed, every changed baseline shown to the user, emulator walk-through. *(The plan merges 5 and 6 and splits 2, so it has 11 tasks too.)*

## 9. Risks

- **Baseline churn.** Nearly every baseline changes (the accent, the clock moving 21 dp, button heights, row sizes). A re-record hides regressions if nobody looks, so each task compares every changed image before accepting it, and the user gets a list of what changed and why at the end. 4d-2 re-records again.
- **`HhButton` migration breadth.** Seven implementations with callers in `:core:ui`, `:core:setup`, `:capability:calendar`, `:capability:weather` and `:provider:calendar-fake` (about 20 files). Test tags must survive the move, as many UI tests find buttons by tag; heights change by 2–16 dp and may push a sheet or a wizard step past its height. Task 2 runs the whole suite before deleting the old functions.
- **The quiet fill's `on` parameter** can be passed wrongly (a button on `bg` left at the default `Surf`). The Roborazzi matrix shows both; the wizard and Settings baselines show the real callers.
- **Contrast edges.** Several pairs sit within 0.4 of the threshold (light `danger` on `dangerSoft` 4.50, past cells 4.83, dark `mute` on the lime tint 4.55). The test pins them; any token change has to re-run it.
- **The clock fix depends on Compose 1.9.1's `LineHeightStyle` behaviour.** The height test catches a change on a Compose upgrade.
- **Moving Home up 21 dp** gives each grid row about 10 dp more; cards that were tuned to the shorter rows (Forecast) get more empty space until the walk-through (W3).

## Appendix A: audit numbers (2026-10-06)

From the accessibility, hand-off and live audits, kept here because the audit folder is not committed. Emulator API 35, 1280 × 800 dp.

### A.1 Contrast, before and after

Text pairs (4.5:1 needed unless large):

| Pair | Dark before | Light before | Dark after | Light after |
|---|---|---|---|---|
| `accent` text on `bg` / `surf` / `surf2` / `surf3` | 7.36 / 6.54 / 5.74 / 4.80 | 3.71 / 4.25 / 3.84 / 3.27 | unchanged | 5.23 / 6.00 / 5.41 / 4.61 |
| `accentInk` on `accent` (primary buttons) | 7.11 | 4.25 | unchanged | 6.00 |
| `accent` on `accentSoft` (chosen page, Master badge) | 5.21 | 3.44 | unchanged | 4.85 |
| Chip secondary on `accent` (72% → 100%) | 4.28 | 2.99 | 7.11 | 6.00 |
| `ink` on `bg` / `surf` / `surf2` / `surf3` | 17.22 / 15.31 / 13.43 / 11.22 | 16.04 / 18.40 / 16.62 / 14.14 | unchanged | unchanged |
| `mute` on `bg` / `surf` / `surf2` / `surf3` | 7.38 / 6.56 / 5.75 / 4.80 | 5.33 / 6.12 / 5.53 / 4.70 | unchanged | unchanged |
| `danger` on `dangerSoft` | 5.42 | 4.50 (edge) | unchanged | unchanged |
| Past date cell (30% → 62%) | 2.49 | 1.91 | 5.80 | 4.83 |

Non-text (3:1 needed):

| Pair | Dark before | Light before | Dark after | Light after |
|---|---|---|---|---|
| Weather icon on `surf` / `bg` | 9.03 / 10.15 | 1.88 / 1.64 | unchanged | 5.38 / 4.69 |
| Add hint (`mute` 50% → 100%) on `surf` | 2.64 | 2.15 | 6.56 | 6.12 |
| Wizard dot, inactive (`surf3` fill → `mute` outline) on `bg` | 1.54 | 1.13 | 7.38 | 5.33 |
| Wizard dot, current (`accent`) on `bg` | 7.36 | 3.71 | 7.36 | 5.23 |
| Today ring (`accent`) on `surf` | 6.54 | 4.25 | unchanged | 6.00 |

Week chips (12 sp / 700 on the person's tint, 0x2E dark, 0x26 light, over `surf`). Before: the time in the person colour. After: the time and name in `mute`. The title stays `ink` (10.62–12.29 dark, 15.57–16.67 light).

| Colour | Time in colour (D / L), before | `mute` (D / L), after |
|---|---|---|
| green #4CB387 | 4.82 / 2.28 | 4.83 / 5.38 |
| blue #5B9BE0 | 4.37 / 2.53 | 4.92 / 5.32 |
| pink #E07BA8 | 4.58 / 2.41 | 4.92 / 5.31 |
| violet #9C7CE3 | 4.00 / 2.81 | 5.08 / 5.24 |
| lime #9CC44E | 5.84 / 1.83 | 4.55 / 5.54 |
| red #E06666 | 3.99 / 2.84 | 5.17 / 5.18 |
| slate #8B96A8 | 4.27 / 2.60 | 4.94 / 5.32 |
| brown #A1785A | 3.47 / 3.32 | 5.26 / 5.18 |
| Family #E0A85B | 5.62 / 1.91 | 4.60 / 5.52 |

Quiet button fills against their surface (visibility, no WCAG ratio):

| Fill on surface | Dark | Light |
|---|---|---|
| `surf2` on `bg` (today's plain button on `bg`) | 1.28 | 1.04 |
| `surf` on `bg` (light after) | 1.13 | 1.15 |
| `surf2` on `surf` (sheets, cards) | 1.14 | 1.11 |
| `surf` on `dangerSoft` (Keep) | 1.14 | 1.27 |

Not changed by 4d-1, recorded for the walk-through: light person dots and bars are 1.82–3.93 against `surf`/`surf2`, most below 3:1 (A23; names now carry identity); simulated deuteranopia ΔE lime vs Family 2.6, blue vs violet 4.4, red vs brown 8.1; protanopia pink vs slate 4.8 (A36, W30).

### A.2 The clock and Home header

| Measure | Hand-off | Built (Trim.Both) | After the fix |
|---|---|---|---|
| Clock box height | 93.6 dp | 135 dp (ascent 0.992 + descent 0.31 em × 104 sp) | ≈ 94 dp |
| Clock baseline from box top | 82.3 dp | — | 82 dp |
| Clock cap-top from screen top | 61 dp | 84 dp | ≈ 63 dp |
| Grid top | 207 dp | 227 dp | ≈ 206 dp |
| Grid row height | 276 dp | 267 dp | ≈ 277 dp |
| Clock-to-date ink gap | 28 dp | 28 dp (26 at 1.3, 24.7 at 1.5) | 28 dp |
| Clock-to-date baselines | 44 dp | 44 dp | 44 dp |

The clock is 104 sp / 600, −4 tracking, tabular; it doesn't grow with font scale on API 35 (live `home_clock` 135 dp tall at 1.0, 1.3 and 1.5), while the date grows (27, 31, 34 dp tall). Built "11:54" is 213.3 dp wide, matching DM Sans at `opsz` 9 (213.6 computed); the hand-off's "16:37" matches `opsz` 40 (W1). The weather header item spans x 1075–1252 dp at 1.5; the clock ends at 409 dp and the date at 367 dp.

### A.3 Touch and fixed sizes

| Control | Before | After |
|---|---|---|
| `HhPillButton` (Close, Back, Next, Skip, Disconnect, Make master, Set PIN, Open settings) | ≈ 46 dp | 48 dp |
| `CalendarDimens.touchTarget` (Week pill, Today +, Reconnect, This week) | 44 dp | 48 dp |
| Assign to… (`assignButtonHeight`) | 44 dp | 48 dp |
| Week column add hint (`addHintMinHeight`) | 40 dp min (live 131 × 48) | 48 dp min |
| Sign out, theme toggle (status bar) | visibly ≈ 28 and ≈ 24 dp; theme toggle touch 56 × 39 dp | 48 dp touch |
| Status bar | 30 dp fixed | 30 dp minimum |
| Picker and Connecting buttons (`PickerButton`) | 52 and 56 dp | 60 dp |
| PIN error line | 18 dp fixed | two lines minimum |
| Forecast row / temperature | 44 dp fixed / 44 dp fixed | 44 dp minimum / 44 dp minimum |
| Detail label column | 104 dp fixed | 104 dp minimum |

Already ≥ 48 and unchanged: chips 48, Close 48, ‹ › 48, Move 48, swatches 56, steppers 88 × 52, date cells 56, PIN keys 76, rail items about 77–86, Settings items 56, sheet buttons 60, `AddButton` 48, Material Switch 48. The PIN Cancel key is 76 × 76 dp; its label is 67 dp wide at 1.3 and wraps at 1.5.
