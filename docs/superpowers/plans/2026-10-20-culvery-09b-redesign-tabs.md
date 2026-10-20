# Culvery — Plan 9b: The redesign, the tabs and multi-day events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–12 are subagent tasks; Task 13 is the controller on the `culvery-5` AVD with the user; Tasks 14–15 need the user and the son's SM-T510.

**Goal:** The Lights, Music, Door and Calendar tabs look as the October redesign draws them — one title block, the Lights tray and growing grid, the Music room cards with labelled volumes and Play last, the Door rows with NEW tags, the week with one nav pill, tinted chips and tap-anywhere adding — and events lasting more than a day show as one bar across the week and as "Day 2 of 3" on Home's Today.

**Architecture:** Shared pieces land first in `:core:ui` (the page title block, the outlined tray, a chip that is always filled, the two icons, the chip and bar tints). Each capability then redraws its own tab: `:capability:lights`, `:capability:music`, `:capability:door`, `:capability:calendar`. Multi-day events get a pure model in `:capability:calendar` (which events span, which lane row each takes, which day of the span today is), fed from `CalendarRepository.week` and `toUi`, then drawn by the week's lane and Today's rows.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00, Material 3), Hilt 2.57.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-19-culvery-9-redesign-design.md` — §2 (rulings; §2.1 folds multi-day events into 9b/9c), §4.4–4.7, §6.8–6.9, §7. The handoff `docs/design/2026-10-redesign/handoff.md` (sections "Lights tab", "Music tab", "Door tab", "Calendar week", "Home" for "Day 2 of 3") and `notes.txt` (notes 15, 18, 20, 22–29, 31) are the drawings. Where the spec and the handoff disagree, the spec wins. Read all three with this plan.
**Previous plan (format, shared parts, rulings):** `docs/superpowers/plans/2026-10-19-culvery-09a-redesign-home.md` — its Units, rulings 1, 2, 17 and its shared components carry over. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md`.

**Builds on:** branch `plan-9b-tabs` in the one checkout `C:\Users\si\code\siland\culvery` (no worktrees; cut from `main` at 7bd925e, Plan 9a merged). Work only there. Never push.

| # | Task | Review |
|---|---|---|
| 1 | Icons, the chip tint, the bar tint | sonnet |
| 2 | Shared: the page title block, the tray, the always-filled chip | sonnet |
| 3 | Lights: the title block, the tray, the room card | sonnet |
| 4 | Lights: the growing grid, on-first order once, the "more rooms" pill | **opus** (sort-once against live data; focus) |
| 5 | Music: the title block, the room card, one volume pill | sonnet |
| 6 | Music: the group chip and per-room grid; nothing playing and Play last | sonnet |
| 7 | Door: the title block, the rows, the copy, NEW for the visit | **opus** (seen ordering against the store) |
| 8 | Week: the title block, one nav pill, columns and chips, no legend | sonnet |
| 9 | Week: tap empty space to add, the pressed ghost, TalkBack's add | sonnet |
| 10 | Multi-day: the model (spans, lanes, day of span) | **opus** (zones, exclusive ends, overlap packing) |
| 11 | Multi-day: the week's lane | sonnet |
| 12 | Multi-day: "Day 2 of 3" on Home's Today | sonnet |
| 13 | The emulator walk-through on `culvery-5` | controller + user |
| 14 | On the tablet | controller + user, **needs the SM-T510** |
| 15 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:** icons and tints first (the font can stop for a download — here both icons are already in the full font at 608ce00, so it shouldn't). The shared title block, tray and chip next, because all four tabs use them. Each tab then stands alone (3–4, 5–6, 7, 8–9). The multi-day model (10) before the lane (11) and Today's rows (12), which both read it.

## Shared components 9c reuses

`HhPageHeader` (Task 2: 9c's Settings pages and the wizard's title), `HhTray` (Task 2: Home's One-tap tray later), `HhChoiceChip(unselectedFill = …)` (Task 2: 9c's Add event chips), `VolumePill(name, volume, …)` (Task 5), `DaySpan` and `isMultiDay` (Task 10: 9c's Until… chip creates what these read).

## Rulings against the code

Where the spec leaves a choice open or doesn't fit the code at 7bd925e. Each is the plan's call unless it cites the spec.

1. **Units** as Plan 9a: dp = px ÷ 1.5, rounded to whole dp/sp; every 2 px edge is 1 dp; the SM-T510 read 240 dpi (9a). Values are in **Units** below.
2. **Touch targets stay 48 dp** (9a ruling 2). Chips drawn 40 dp (60 px) sit in a 48 dp tap height; the handoff's 68–76 px buttons are 48 dp; the 72 px Music buttons are exactly 48.
3. **One title block** for the four tabs: `HhPageHeader(title, subtitle, trailing)` — the handoff's 68/700 title and 23 muted subtitle, the tray or buttons on the right (note 28: "the tabs line up when you switch between them"). The block is the first item of each tab's scrolling list, so it scrolls away with the content, as Lights' and Door's top blocks do today.
4. **The Lights tray** is `HhTray` (an outlined container, the handoff's One-tap pattern) holding the existing `AllOffButton` and `HolidayButton` (spec §2.3, §4.4). **The button keeps the name "Holiday mode"** on the tab, as on Home's card and in the PIN prompt: the handoff's "Holiday lights" names the lights-only mode as distinct from a house Holiday mode that doesn't exist yet (note 15); when the house mode arrives, both buttons become "Holiday lights" together. (A call for the user to check.)
5. **The switch keeps `HhSwitch`'s size** (Material's 52 × 32 dp); the handoff's 96 × 54 px switch (64 × 36 dp) is left for the end-of-v1 design review, so `:core:ui`'s one switch stays one size.
6. **Scene chips are always filled** (note 18): the active one `accent`/`onAccent`, the others `surf2`/`ink`. They keep `Role.RadioButton` and their selected state for TalkBack; only the unselected fill changes (`HhChoiceChip(unselectedFill = c.surf2)`). A chip on an off room turns it on with that scene: the Hue provider already does (`HueSystem.recallScene` sets the group on); Task 3 pins it at the tab.
7. **The Lights grid** (note 20): 1–4 rooms 2 columns, 5–6 rooms 3 columns, the rows sharing the height in both; 7 or more 3 columns at a fixed row height, scrolling, a fade to `bg` at the foot and a pinned pill "`{k} more rooms`" with `keyboard_arrow_down`, k being the rooms below the last fully visible row; the pill shows while k > 0 and a tap scrolls one screen of rows down. The handoff's "n − 6" is k at its sizes.
8. **On-first order, once** (spec §6.9): the order is taken from the first non-empty room list after the tab opens — on rooms first, each group in Hue's order — and kept while the tab stays open; live data is looked up by id; a room that appears later goes at the end; one that goes is dropped. Reopening the tab takes a new order. Home's › focus (`view.focus`) scrolls to the room's place in that order.
9. **Music's subtitle** is "`{r} rooms · {g} group(s) · Sonos`": r every room, g the groups of more than one room, "· 0 groups" left out, "1 room", "1 group" singular. The tray with Pause everywhere is not built (spec §4.5).
10. **The group chip** reads "`{n} rooms`" in `accentSoft`/`accent` with `expand_more`/`expand_less`; it replaces today's "Rooms" chip and keeps its show/hide state and click label. Opening it shows a 2-column grid of per-room rows: the room's name and a `VolumePill`. A group that vanishes while open closes.
11. **Nothing playing on a Music card** uses Home's notion (9a fault 4): a group is quiet when `!isSounding && !pausedWithSomethingLoaded()`. Then Next hides, the round Play becomes a labelled Primary `HhButton` "Play last" with `play_arrow` (the existing Play, relabelled — spec §4.5, handoff open decision 4), the art is the empty tile and the first line "Nothing playing" in `mute`. Otherwise Play/Pause follows `isPlaying` as today.
12. **`VolumePill` takes a name and a volume** — `VolumePill(name, volume, tag, onLetGo, modifier)` — so a room and a group use the same pill; Home's card passes `group.name`, `group.volume`.
13. **The Door subtitle** is "`{devices} · {n} new ring(s)`": devices the distinct non-empty `event.device` values of the events shown, joined by " · ", in first-seen order; "· 0 new rings" left out; with no devices and no new rings, no subtitle.
14. **NEW for the visit** (spec §6.8): each time the tab is resumed or reselected, the host reads the unseen ids, *then* marks all seen (Plan 5 §4.7 unchanged). A row is NEW when its id was in that set **or** it is still unseen — a ring arriving while the tab stays open is unseen and keeps its badge (Plan 5), so it shows NEW too. The set clears at the next open.
15. **Door copy** (note 29): a ring "Someone rang", motion "Movement", an unrecognised notice "Notice from Ring" with the sub-line "Culvery couldn’t tell what this was" (after the device and " · " when there is one). The time stays first: "14:02 · Someone rang". The words change everywhere `kindLabel`/`eventLine` are used (the snapshot sheet too). `RING_ALERT` stays where it means the Ring app's own alert (`cantFullyRead`).
16. **The Door row** is its own layout (no longer `HhListRow`): the 64 px icon circle (doorbell on `accentSoft`/`accent`, directions_walk and help on `surf2`/`mute`), the title and sub-line, the NEW tag, the 160 × 90 px thumbnail, which still opens the snapshot sheet (the panel is §8's).
17. **The week's nav pill**: one `surf2` pill holding ‹ and ›; "This week" (Quiet) appears between the pill and "+ Add event" (Primary) past the current week (note 22). The arrows are round icon buttons on the pill's fill. A disabled arrow is the shared disabled style (9a ruling 17: `mute` icon, the 1 dp `surf3` outline) — not the handoff's 35 % — so the app keeps one disabled look (spec §6.4). The calendar's own `RoundButton` leaves the week (the date picker's stays for 9c).
18. **The chip tint** becomes 0x30 in dark (was 0x2E; light stays 0x26), as the handoff's tokens say; only the week's chips use it. **The bar tint** is new: 0x4D dark, 0x40 light.
19. **Adding on a day** keeps today's tap rules (below a column's last chip or anywhere in an empty column; not the header, not the gap between chips) and loses the always-shown add hint (note 24). While pressed, the empty space shows the dashed `accent` ghost "+ Add on Sun 27" (`EEE d`, the date as the column shows it). For TalkBack, each column's empty space is one node: `Role.Button`, named `addEventOn(date)`, its click opening Add event on that day.
20. **"+ Add event" in the header** keeps today's behaviour: this week → today; another week → that week's first day.
21. **What counts as multi-day** (note 31: "events lasting more than a day"): an all-day event covering two or more days (all-day ends are exclusive), or a timed event lasting 24 hours or more. An overnight timed event (22:00–01:00) is not multi-day: it stays a chip on each day with "22:00–" / "until 01:00", as today. Single-day all-day events stay chips (Bin day).
22. **The week's lane**: multi-day events leave the columns' chips and become bars in a lane under the day headers. Bars pack into rows greedily (earliest first day, then longest, then title); a bar takes the first row it doesn't overlap. Every column gets a spacer the lane's height so the chips start level. A bar starting before the shown week has a square left end; one ending after it has a square right end and reads "until {EEE} →" (its last day's weekday) instead of the range. The range reads "`{EEE d} – {EEE d}`" ("Mon 21 – Thu 24"). A bar opens its event. Spoken: "`{title}, {person}, {from} to {to}`" with full weekday and date.
23. **`DayUi` keeps every slice** — the repository's per-day lists still hold multi-day events (its tests stand); `WeekUi` gains the spans, and the week view drops the chips whose `ref` has a span.
24. **"Day 2 of 3"** (note 31): on Home's Today a multi-day event's row reads "Day {i} of {n}" where the time would be (i = today's day of the event, n its days); spoken "day 2 of 3". `EventUi` gains `span: DaySpan?`, set by `toUi` for multi-day events only. Overnight events keep "20:00–"/"until 10:00". The tomorrow top-up already shows such an event once.

## Units (converted once, px ÷ 1.5)

| Where | Handoff px | dp / sp |
|---|---|---|
| Page title | 68/700, ls −1; subtitle 23 muted | 45 sp W700, ls −0.7 sp; 15 sp `mute`; 4 between; 15 below the block |
| Tray | tile radius 26; outlined | radius 17, edge 1 `surf3`, padding 8, gap 8 |
| Lights card | r 36, padding 30/32; bulb 52; name 34/700; status 23; track 34; % 23/600 | r 24, padding 20 × 21; bulb 35; 23 sp W700; 15 sp; track 23; 15 sp W600 |
| Scene chips | 60, r 30, 12 apart | 40 (48 tap), r 20, 8 apart |
| Lights grid | gap 22; fixed row 420; fade 220; pill 68 | gap 15; row 280; fade 147; pill 48 |
| Music card | r 36, padding 26/30; art 120; room 32/700; track 25; artist 21 | r 24, padding 17 × 20; art 80 (r 13); 21 sp W700; 17 sp; 14 sp `mute` |
| Music controls | buttons 72; volume pill 420; chips 60; room rows 72 | 48; pill 280 wide, 48 high; 40 (48 tap); 48 |
| Door row | r 28; icon circle 64; title 27/600; sub 21; thumb 160 × 90; NEW 30 | r 19, padding 15 × 20; circle 43 (icon 24); 18 sp W600; 14 sp `mute`; 107 × 60 r 10; 20 high, 11 sp W700 `dangerInk` on `danger`, padding 0 × 8 |
| Week header | nav pill with 68 buttons; "+ Add event" 76 | pill 48 high (buttons 48); 48 |
| Week columns | 14 apart, r 30, padding 18/14/14; ring 3 inset; label 21/600; date 34/700 | 9 apart, r 20, padding top 12, sides and foot 9; ring 2 inset; 14 sp W600; 23 sp W700 |
| Week chips | r 18, padding 12/14; meta 17/600 + 10 dot; title 21/600, 2 lines | r 12, padding 8 × 9; 11 sp W600 + dot 7; 14 sp W600, 2 lines; 8 apart |
| Ghost | 2 px dashed accent | 1 dp dashed `accent`, r 12; label 14 sp W600 `accent` |
| Lane | row 62; inset 10; r 18; edge 6; title 21/700; range 17/600 | row 41, 4 between rows; inset 7; r 12; edge 4; icon 18; 14 sp W700; 11 sp W600 `mute` |

## Global Constraints

**From the spec, verbatim:**

- §2.1 "**Multi-day events are folded into 9b and 9c** (the user, 2026-10-20): the week's lane in 9b, creating them in 9c's Add event, "Day 2 of 3" on Home's Today with them".
- §2.3 "The Lights tab also gets the design's top tray with All lights off and Holiday lights (note 15)."
- §2.5 "**An off room looks off** (note 18). Its slider is disabled, keeping the remembered level and reading "Off"; the switch or a scene chip turns the room on (a scene chip turns it on with that scene)."
- §4.4 "Page header with the count; a top tray … holding **All lights off** and **Holiday lights** (Holiday mode, shown ON while on) — the same Holiday mode as Home's, with its fresh PIN; room cards with the off look … and always-filled scene chips …; the grid grows 2 → 3 columns then scrolls with a fade and "N more rooms" pill …; rooms that are on sort first."
- §4.5 "chips always filled, the group chip "2 rooms" expanding to per-room volumes (note 25); nothing playing hides Next and shows **Play last** — the existing Play that resumes the queue, relabelled (note 26). **Not** "Pause everywhere"".
- §4.6 "the row design with thumbnails and a red NEW tag on new rings, and the copy: "Someone rang", "Movement", "Notice from Ring" / "Culvery couldn't tell what this was" … the list takes the full width".
- §4.7 "Header with one ‹ › pill and "+ Add event" (note 22); no legend (note 23); columns with today's ring; chips tinted with the person's colour, 2-line titles; empty column space as the add target with the pressed ghost "+ Add on Sun 27" (note 24)."
- §6.8 "**"New" rings in the Door tab** are the rings that were unseen when the tab opened: they keep their NEW tag and count for that visit, while opening the tab still marks them seen (Plan 5), so the rail badge clears as before."
- §6.9 "**Lights rooms sort once,** when the tab opens (on first), not live, so a card never jumps from under a finger."
- §7 "the Lights grid at 4, 6 and 9 rooms with on-first ordering"; "Copy changes go through CopyScanTest; TalkBack labels follow the new copy."

**Copy** (verbatim, curly apostrophes in code): "Lights", "`{on} of {n} rooms on · Hue Bridge`", "All lights off", "Holiday mode" (ruling 4), "Off", "`{k} more rooms`", "Music", "`{r} rooms · {g} group · Sonos`", "`{n} rooms`", "Play last", "Nothing playing", "Favourites", "Shuffle everything", "Door", "`{n} new rings`", "Today", "Yesterday", "Someone rang", "Movement", "Notice from Ring", "Culvery couldn’t tell what this was", "NEW" (spoken "new"), "This week", "Add event", "Add on {EEE d}", "until {EEE} →", "Day {i} of {n}".

**From the house rules** (as Plan 9a; binding):

- Package root `uk.co.siland.culvery`. `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only. **No new library; never change a version.** No deprecated API; the one opt-in is `ExperimentalMaterial3Api` in `HhPercentSlider`.
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug`. Never plain `test`, `check` or `build`; never a Gradle `install…` task; no subagent touches a device. **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`** for Gradle: a long temp path pushes Robolectric's database paths past Windows' MAX_PATH (SQLITE_CANTOPEN). `$TMP` below means `C:/t9` (lists only).
- **Tests that can fail:** every new test fails before its change; a test that passes before it is labelled **pin**. Timer tests on virtual time; UI timers under `compose.mainClock` one frame before the boundary and at the boundary plus one frame.
- **Screenshots:** baselines in `<module>/src/test/screenshots/`. Compare first (`compareRoborazziDebug`), open each `*_compare.png`, then record; predicted names or prefixes in `$TMP/expected-baselines.txt` / `$TMP/expected-prefixes.txt`, checked as in Plan 9a's Global Constraints; anything outside the prediction stops the task. **Open every new image** and look at it against the handoff's section for that screen.
- **Copy** names what the user does; curly quotes; "·", "›", "→", "…" as written; `CopyScanTest` passes. **Accessibility:** every changed screen's test ends with `compose.assertClickablesHaveRoleAndLabel()`; TalkBack names follow the copy.
- **Icons** from `Icons`; a new one in `tools/fonts/icons.txt` and the subset rebuilt from the full font at 608ce00. **Layout numbers are named** in the module's dimens object. **Comments say what the code can't**, one line, spec refs as `(9 §4.4)`; no plan task or ruling numbers. **No personal data** — sample names only (Alex, Sam, Mia, Lounge, Kitchen, Hall, Garden, Front Door, Back Garden). **UTF-8.** **Commits** a plain sentence, no trailer. Never push. **Never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **A multi-day event that starts before the shown week, ends after it, or crosses the clocks changing** (an all-day half term from last Thursday to next Tuesday; a timed 48 h event over the October change). Expected: one bar clamped to the week with square ends and "until Tue →"; Today reads the right "Day i of n"; nothing is a chip as well.
   - Task 10 `MultiDayTest.aSpanStartingBeforeTheWeekIsClampedAndMarked`, `aTimedSpanOverTheClockChangeCountsItsDays`; Task 11 `WeekLaneTest.aBarRunningPastTheWeekSaysUntil`.
2. **Several spans overlapping** (three at once, one inside another). Expected: the lane grows to as many rows as needed, every column's spacer matches, chips start level under it, no bar hidden.
   - Task 10 `LaneRowsTest.threeOverlappingSpansTakeThreeRows`, `aSpanInsideAnotherSharesNoRow`; Task 11 `WeekLaneTest.theColumnsSpacerMatchesTheLane`.
3. **A Lights room changing while the tab is open** (switched in the Hue app, a new room paired). Expected: no card moves; its state updates in place; a new room appears at the end; Home's › still lands on the right card.
   - Task 4 `LightsGridTest.aRoomTurningOnDoesNotMoveIt`, `aNewRoomGoesAtTheEnd`, `theFocusedRoomIsScrolledToInTheOpenedOrder`.
4. **A ring arriving while the Door tab is open, then the tab reopened.** Expected: the new ring shows NEW and keeps the rail badge; on reopening it is seen, its NEW stays for that visit, and the next visit has none.
   - Task 7 `DoorTabTest.aRingArrivingWhileOpenIsNewAndKeepsItsBadge`, `newLastsOneVisit`.
5. **A Music group splitting while its rooms are open, and Play last refused.** Expected: the open grid closes for the vanished group without a crash; a refused Play last shows the row's line "Sonos didn’t do that. Try again."
   - Task 6 `MusicTabTest.aGroupThatGoesClosesItsRooms`, `aRefusedPlayLastShowsOnItsRow`.

The spec's own tests (§7) each have their task: the Lights grid at 4, 6 and 9 rooms with on-first order (4); an off room's slider disabled and a scene chip turning it on (3); CopyScanTest and TalkBack names (every task).

---

## File Structure

```
core/ui/.../Icons.kt, tools/fonts/icons.txt, res/font/material_symbols_rounded.ttf, Colors.kt (HhAlpha) (Task 1)
core/ui/.../PageHeader.kt (create: HhPageHeader, HhTray), Controls.kt (HhChoiceChip unselectedFill) (Task 2)
capability/lights/.../ui/LightsTab.kt, LightsRoomCard.kt (create), LightsGrid.kt (create), LightsParts.kt, LightsDimens.kt, LightsWords.kt (Tasks 3, 4)
capability/music/.../ui/MusicTab.kt, MusicRoomCard.kt (create), MusicParts.kt (VolumePill), MusicDimens.kt, MusicWords.kt, ui/MusicCard.kt (VolumePill call) (Tasks 5, 6)
capability/door/.../ui/DoorTab.kt, DoorRow.kt (create), DoorDimens.kt, DoorWords.kt, DoorStore.kt (unseenIds) (Task 7)
capability/calendar/.../ui/WeekView.kt, WeekHeader.kt (create), CalendarType.kt (Tasks 8, 9)
capability/calendar/.../MultiDay.kt (create), CalendarUi.kt (EventUi.span, toUi), CalendarRepository.kt (week spans) (Task 10)
capability/calendar/.../ui/WeekLane.kt (create) (Task 11)   ui/TodayCard.kt, Spoken.kt (Task 12)
app/.../shell/ui/ShellScreenshotTest.kt (tab call sites: Tasks 3, 5, 7, 8)
docs/superpowers/plans/2026-09-23-plan1-followups.md (Tasks 13–15)
```

---

### Task 1: Icons, the chip tint, the bar tint (handoff tokens; rulings 18)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Icons.kt`, `tools/fonts/icons.txt`, `core/ui/src/main/res/font/material_symbols_rounded.ttf` (regenerated), `core/ui/.../Colors.kt` (`HhAlpha`)
- Test: `core/ui/src/test/.../IconFontTest.kt` (`NO_FILLED_FORM` if needed), `ThemeTest.kt` or the test that pins `HhAlpha`, `ContrastTest.kt`

**Interfaces:**
- Produces: `Icons.HELP = "help"`, `Icons.KEYBOARD_ARROW_DOWN = "keyboard_arrow_down"`; `HhAlpha.tint(colors)` = 0x30/255 dark, 0x26/255 light; `HhAlpha.bar(colors: HhColors): Float` = 0x4D/255 dark, 0x40/255 light.

- [ ] **Step 1: The failing tests** — the two constants in `Icons` and `icons.txt` (alphabetical); `HhAlpha` tests: `chipTintMatchesTheHandoff` (`tint(DarkColors) == 0x30 / 255f`, light 0x26), `barTintMatchesTheHandoff` (0x4D, 0x40). Run: `./gradlew :core:ui:testDebugUnitTest --tests '*IconFontTest*' --tests '*Theme*' --tests '*Contrast*'` — Expected: FAIL (the font lacks the glyphs; `bar` doesn't exist).
- [ ] **Step 2: The subset** — `tools/fonts/README.md` steps 3–4 from the blob at 608ce00 (`git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$TMP/material-symbols-full.ttf"`; `python tools/fonts/subset.py --source "$TMP/material-symbols-full.ttf"`). Both glyphs are in that font. If a filled form is missing, add it to `NO_FILLED_FORM`. Set `TINT_DARK` to 0x30; add the bar alphas.
- [ ] **Step 3: Compare, check, re-record** — predicted: the week's images (`week_`) for the chip tint only. Look at one `week_` compare; re-record; the baseline check by prefix.
- [ ] **Step 4: The gate and commit**
```bash
git add core/ui tools/fonts/icons.txt capability/calendar
git commit -m "Add the help and arrow-down icons and take the handoff's chip and bar tints"
```

---

### Task 2: Shared: the page title block, the tray, the always-filled chip (rulings 3, 4, 6)

**Review:** sonnet.

**Files:**
- Create: `core/ui/.../PageHeader.kt`
- Modify: `core/ui/.../Controls.kt` (`HhChoiceChip`)
- Test: create `core/ui/src/test/.../PageHeaderTest.kt`; `ControlsTest.kt` or the chip's test; `PageHeaderScreenshotTest.kt` (create)

**Interfaces:**
- Produces: `@Composable fun HhPageHeader(title: String, subtitle: String?, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {})` — the title a heading (`semantics { heading() }`), the subtitle below it, `trailing` right-aligned on the title's row, `PageHeaderTokens` (title 45 sp W700 ls −0.7 sp, subtitle 15 sp `mute`, gap 4, below 15); `@Composable fun HhTray(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit)` — a `Row` with a 1 dp `surf3` edge, radius 17, padding 8, gap 8, no fill; `HhChoiceChip(…, unselectedFill: Color = Color.Transparent)` — the unselected background (today's look when transparent).

- [ ] **Step 1: The failing tests** (`PageHeaderTest`, each ends with `assertClickablesHaveRoleAndLabel()`)
- `theTitleIsAHeadingAndTheSubtitleSitsUnderIt`: "Lights" is a heading; "2 of 4 rooms on · Hue Bridge" is below it; the title's font size 45 sp.
- `noSubtitleLeavesNoGap`: with `subtitle = null` the block's height is the title's line height.
- `theTrailingContentSitsOnTheRight`: a 48 dp button in `trailing` has its right edge at the block's right edge, level with the title.
- `theTrayIsAnOutlinedRow`: two buttons in `HhTray` sit 8 dp apart inside 8 dp padding.
- `anUnselectedChipCanBeFilled` (the chip's test): `HhChoiceChip(selected = false, unselectedFill = c.surf2)` draws `surf2` (pixel at its centre); default still transparent (**pin** for the default).
Run: `./gradlew :core:ui:testDebugUnitTest --tests '*PageHeader*' --tests '*Chip*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** per the Interfaces and Units.
- [ ] **Step 3: Screenshots** — new `page_header_dark`, `page_header_light` (title, subtitle, a tray with two quiet buttons). Open both against the handoff's "Lights tab" header; the baseline check by name.
- [ ] **Step 4: The gate and commit**
```bash
git add core/ui
git commit -m "Add the tabs' shared title block, an outlined tray, and a chip that can stay filled"
```

---

### Task 3: Lights: the title block, the tray, the room card (spec §2.3, §2.5, §4.4; rulings 4–6)

**Review:** sonnet.

**Files:**
- Create: `capability/lights/.../ui/LightsRoomCard.kt`
- Modify: `ui/LightsTab.kt`, `ui/LightsParts.kt` (`AllOffButton`, `HolidayButton` gain `tag`), `ui/LightsDimens.kt`, `LightsWords.kt`
- Test: `capability/lights/src/test/.../ui/LightsTabTest.kt`, `LightsTabScreenshotTest.kt`, `LightsWordsTest.kt`; `app/.../ShellScreenshotTest.kt` (`tab_lights_light`)

**Interfaces:**
- Consumes: `HhPageHeader`, `HhTray`, `HhChoiceChip(unselectedFill)` (Task 2).
- Produces: `fun roomsOn(rooms: List<LightRoom>): String` = "`{on} of {n} rooms on · Hue Bridge`" (`HUE_BRIDGE`); `@Composable internal fun LightsRoomCard(room: LightRoom, line: String?, actions: RoomActions, modifier: Modifier = Modifier)` (tag `lights_room_{id}`; the switch row tag `lights_room_{id}_switch`, slider `lights_room_{id}_brightness`, chips `lights_room_{id}_scene_{sceneId}`); `AllOffButton(…, tag: String = "lights_card_all_off")`, `HolidayButton(…, tag: String = "lights_card_holiday")` — the tab passes `lights_tray_all_off`, `lights_tray_holiday`.

- [ ] **Step 1: The failing tests** (`LightsTabTest`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `theHeaderCountsTheRoomsOn` (§4.4): Lounge on, Kitchen off, Hall on → "Lights" heading, "2 of 3 rooms on · Hue Bridge".
- `theTrayHoldsAllLightsOffAndHolidayMode` (§2.3): `lights_tray_all_off` and `lights_tray_holiday` on the header's row, right of the title; Holiday reads its state as on Home ("On" while on).
- `anOffRoomsSliderIsDisabledAndReadsOff` (§2.5): Kitchen off at 40 → `lights_room_kitchen-id_brightness` not enabled, spoken "Kitchen brightness, Off", level 40.
- `aSceneChipOnAnOffRoomTurnsItOnWithThatScene` (§2.5): the host over the fake bridge; tap Kitchen's "Relax" → the fake records the scene recalled and the room reads "On · … · Relax" (**pin** — the provider already does this; it guards the tab's wiring).
- `everySceneChipIsFilled` (ruling 6): the inactive chip's centre pixel is `surf2`; the active one `accent`; the active chip `assertIsSelected`, the others `assertIsNotSelected` (replaces `scenesAsChipsWithTheActiveOneSelected`).
- `LightsWordsTest.roomsOnCounts`: 2 of 3, 0 of 1, 1 of 1.
`oneRowPerRoomInHuesOrder` moves to Task 4 (the order changes there); `allLightsOffAndHolidayModeAtTheTop` becomes `theTrayHolds…`. The tab's two positional `LightsTab(…)` calls in tests follow the new signature.
Run: `./gradlew :capability:lights:testDebugUnitTest --tests '*LightsTabTest*' --tests '*LightsWordsTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — the tab's first list item is `HhPageHeader("Lights", roomsOn(rooms), trailing = { HhTray { AllOffButton(…); HolidayButton(…) } })` (subtitle null while looking); the status line and the house line stay under it. The card per Units: bulb (filled `accent` on, outline `mute` off), name, `cardStatus`, the switch; the slider row as Home's `BrightnessRow` (enabled while on, "Off", `holdCeilingMs = LIGHTS_HOLD_MS`) with the % label on the right; the chips in a `FlowRow`. The rooms still lay out as a single column here — the grid is Task 4.
- [ ] **Step 3: Compare, check, re-record** — predicted prefixes: `lights_tab_`, `lights_row_no_scenes_` (renamed `lights_room_no_scenes_dark`), `tab_lights_`. Open each new image against the handoff's "Lights tab" (2b); re-record; the baseline check by prefix.
- [ ] **Step 4: The gate and commit**
```bash
git add capability/lights app
git commit -m "Give the Lights tab its title, the tray of All lights off and Holiday mode, and the redesigned room card"
```

---

### Task 4: Lights: the growing grid, on-first order once, the "more rooms" pill (spec §4.4, §6.9; rulings 7, 8; Review Focus 3)

**Review:** opus.

**Files:**
- Create: `capability/lights/.../ui/LightsGrid.kt`
- Modify: `ui/LightsTab.kt`, `ui/LightsDimens.kt`, `LightsWords.kt`
- Test: create `capability/lights/src/test/.../ui/LightsGridTest.kt`, `RoomOrderTest.kt`; `LightsTabTest.kt` (the focus tests), `LightsTabScreenshotTest.kt`

**Interfaces:**
- Consumes: `LightsRoomCard` (Task 3), `HhBottomFade`, `Icons.KEYBOARD_ARROW_DOWN` (Task 1).
- Produces: `internal fun openedOrder(previous: List<String>?, rooms: List<LightRoom>): List<String>` (null previous → on rooms then off, each in Hue's order; otherwise previous ∩ current, then new ids in Hue's order); `internal fun gridColumns(count: Int): Int` (≤ 4 → 2, else 3); `internal fun scrolls(count: Int): Boolean` (count ≥ 7); `fun moreRooms(count: Int): String = "$count more rooms"`; `@Composable internal fun LightsGrid(rooms: List<LightRoom>, lines: Map<String, String>, actions: RoomActions, focus: String?, onFocused: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: The failing tests**
- `RoomOrderTest` (plain JVM): `onRoomsComeFirstInHuesOrder` (Lounge on, Kitchen off, Hall on → Lounge, Hall, Kitchen); `theOrderStaysWhenARoomTurnsOn` (previous [Lounge, Hall, Kitchen], Kitchen now on → same); `aNewRoomGoesAtTheEnd`; `aRoomThatGoesIsDropped`.
- `LightsGridTest` (Robolectric, the tab at 1116 × 690 dp; each ends with `assertClickablesHaveRoleAndLabel()`): `fourRoomsAreTwoByTwo` (two columns, the rows sharing the height within 1 dp); `sixRoomsAreThreeByTwo`; `nineRoomsScrollWithAPill` ("3 more rooms" with `keyboard_arrow_down` while the top is shown; a tap scrolls; at the end no pill) (§7's 4, 6 and 9); `aRoomTurningOnDoesNotMoveIt` (Review Focus 3: Kitchen turns on through the fake → its card's top-left unchanged); `aNewRoomGoesAtTheEnd`; `theFocusedRoomIsScrolledToInTheOpenedOrder` (nine rooms, focus the eighth in Hue's order → it is displayed, `onFocused` called once); `aFocusOnAnUnknownRoomIsDropped` (moved from `LightsTabTest`).
Run: `./gradlew :capability:lights:testDebugUnitTest --tests '*LightsGridTest*' --tests '*RoomOrderTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — `LightsTab` keeps the header as before and puts the grid under it. Fewer than 7 rooms: rows of `gridColumns` cards in a `Column`, each row `weight(1f)`. Seven or more: a `LazyVerticalGrid(GridCells.Fixed(3))` with rows at `LightsDimens.fixedRow` (280 dp), `contentPadding(bottom = pill + foot)`, `HhBottomFade(c.bg, 147.dp)` and the pinned `HhButton(moreRooms(k), icon = KEYBOARD_ARROW_DOWN, …)` (quiet on `bg`, `Modifier.shadow` as Today's pill), k from `layoutInfo` (rooms after the last fully visible row); the tap `animateScrollBy` one viewport. The order: `var order by remember { mutableStateOf<List<String>?>(null) }`, updated with `openedOrder(order, rooms)` whenever rooms change (first non-empty list sets it), the cards looked up by id. Focus scrolls to the room's index in `order`.
- [ ] **Step 3: Compare, check, re-record** — predicted: `lights_tab_`, `tab_lights_`; new `lights_grid_4_dark`, `lights_grid_6_dark`, `lights_grid_9_dark`, `lights_grid_9_light`. Open each against the handoff's 2b and 2d; re-record; the baseline check by prefix.
- [ ] **Step 4: The gate and commit**
```bash
git add capability/lights app
git commit -m "Lay the Lights tab's rooms in a grid that grows to three columns and then scrolls, on rooms first in the order they opened"
```

---

### Task 5: Music: the title block, the room card, one volume pill (spec §4.5; rulings 9, 12)

**Review:** sonnet.

**Files:**
- Create: `capability/music/.../ui/MusicRoomCard.kt`
- Modify: `ui/MusicTab.kt`, `ui/MusicParts.kt` (`VolumePill`, `PlayPauseButton`/`NextButton` sizes), `ui/MusicCard.kt` (the pill call), `ui/MusicDimens.kt`, `MusicWords.kt`
- Test: `capability/music/src/test/.../ui/MusicTabTest.kt`, `MusicTabScreenshotTest.kt`, `MusicWordsTest.kt`, `MusicCardTest.kt` (the pill call, **pin**: its tests pass unchanged); `app/.../ShellScreenshotTest.kt` (`tab_music_light`)

**Interfaces:**
- Consumes: `HhPageHeader` (Task 2).
- Produces: `fun musicSummary(groups: List<SpeakerGroup>): String` (ruling 9); `VolumePill(name: String, volume: Int, tag: String, onLetGo: (Int) -> Unit, modifier: Modifier = Modifier)` (spoken `volumeName(name)`, `percentWords`); `@Composable internal fun MusicRoomCard(group: SpeakerGroup, line: String?, roomsShown: Boolean, onRooms: () -> Unit, actions: GroupActions, modifier: Modifier = Modifier)` (tag `music_row_{id}`, keeping today's `_play`, `_pause`, `_next`, `_shuffle`, `_favourites`, `_line` suffixes; the pill `music_row_{id}_volume`).

- [ ] **Step 1: The failing tests** (`MusicTabTest`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `theHeaderCountsRoomsAndGroups`: Lounge + Dining room, Kitchen, Bedroom → "Music", "4 rooms · 1 group · Sonos"; one room alone → "1 room · Sonos".
- `eachCardHasTheControlRowAndALabelledVolume`: Lounge playing → `_pause` and `_next` 48 dp, the pill 280 dp wide showing "40", spoken "Lounge + Dining room volume, 40 percent"; the art 80 dp.
- `MusicWordsTest.musicSummaryCountsRoomsAndMultiRoomGroups` (ruling 9's four cases).
`eachGroupIsARowWithItsControlsAndWords` keeps its tags and spoken names.
Run: `./gradlew :capability:music:testDebugUnitTest --tests '*MusicTabTest*' --tests '*MusicWordsTest*' --tests '*MusicCardTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — the tab's first item `HhPageHeader("Music", musicSummary(groups))` (subtitle null while looking), the status line under it; each group a `MusicRoomCard` per Units: left `NowPlayingBlock` at 80 dp art (name 21 sp W700, track 17 sp, artist 14 sp `mute`), right the round Play/Pause and Next at 48 dp and the 280 dp `VolumePill`; under them the chips row (the existing Rooms chip, Favourites, Shuffle everything — Task 6 redraws the chip). `VolumePill` takes name and volume; Home's card passes the group's.
- [ ] **Step 3: Compare, check, re-record** — predicted: `music_tab_`, `tab_music_`; Home's `music_card_` must not change (the pill's look is the same). Open each new image against the handoff's 3b; re-record; the baseline check by prefix.
- [ ] **Step 4: The gate and commit**
```bash
git add capability/music app
git commit -m "Give the Music tab its title and counts, and room cards with the control row and a labelled volume"
```

---

### Task 6: Music: the group chip and per-room grid; nothing playing and Play last (spec §4.5; rulings 10, 11; Review Focus 5)

**Review:** sonnet.

**Files:**
- Modify: `capability/music/.../ui/MusicRoomCard.kt`, `ui/MusicParts.kt`, `MusicWords.kt`, `ui/MusicDimens.kt`, `ui/MusicTab.kt` (the host's `roomsShown` pruning)
- Test: `MusicTabTest.kt`, `MusicTabScreenshotTest.kt`, `MusicWordsTest.kt`

**Interfaces:**
- Consumes: `VolumePill(name, volume, …)` (Task 5); `HhChoiceChip(unselectedFill)` (Task 2); `pausedWithSomethingLoaded()` — today a local function inside `homePick` (`HomePick.kt:19`); lift it to an `internal fun SpeakerGroup.pausedWithSomethingLoaded()` in the same file so both use one definition.
- Produces: `fun roomsChip(count: Int): String = "$count rooms"`; `const val PLAY_LAST = "Play last"`; `fun playLastSpoken(name: String): String = "Play last in $name"`; `internal fun SpeakerGroup.isQuiet(): Boolean = !isSounding && !pausedWithSomethingLoaded()`.

- [ ] **Step 1: The failing tests**
- `theGroupChipSaysHowManyRoomsAndOpensTheirVolumes` (replaces `theRoomsChipShowsEachRoomsVolume`): "2 rooms" chip (`accentSoft` fill) with `expand_more`; a tap shows a 2-column grid of `music_row_l_room_{roomId}_volume` pills, spoken "Dining room volume, 35 percent"; the icon becomes `expand_less`; a second tap hides it.
- `aGroupThatGoesClosesItsRooms` (Review Focus 5): open Lounge's rooms; the groups change so Lounge splits → no crash, no room grid, `roomsShown` no longer holds "l".
- `aQuietRoomOffersPlayLastAndNoNext` (§4.5): Bedroom stopped, nothing loaded → "Play last" (`music_row_b_play`, spoken "Play last in Bedroom"), no `_next`, "Nothing playing" in `mute`, the empty art tile; a tap calls `onPlay`.
- `aPausedRoomWithATrackStillHasPlayAndNext`: Kitchen paused with a song → round Play, `_next`, the track's words.
- `aRefusedPlayLastShowsOnItsRow` (Review Focus 5): the host over a fake system refusing Play → `music_row_b_line` "Sonos didn’t do that. Try again."
- `MusicWordsTest.isQuietFollowsHome` (stopped/empty → quiet; idle Input → quiet; paused with a track → not; playing → not).
Run: `./gradlew :capability:music:testDebugUnitTest --tests '*MusicTabTest*' --tests '*MusicWordsTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — the chip with `unselectedFill = c.accentSoft` and `accent` ink, the expand icon trailing, the existing click label; the per-room grid two columns, gap 15, rows 48 high (name over the pill, or beside it — the handoff's 72 px row holds both on one line: name left, pill filling the rest). The quiet card: `HhButton(PLAY_LAST, icon = PLAY_ARROW, tone = Primary, …)` in place of the round Play, Next hidden. The host drops ids from `roomsShown` that no longer name a multi-room group.
- [ ] **Step 3: Compare, check, re-record** — predicted: `music_tab_`, `tab_music_`; new `music_tab_quiet_dark`. Open each against 3b and note 26; re-record; the baseline check by prefix.
- [ ] **Step 4: The gate and commit**
```bash
git add capability/music app
git commit -m "Show a Music group's rooms behind a filled chip, and offer Play last without Next when a room is quiet"
```

---

### Task 7: Door: the title block, the rows, the copy, NEW for the visit (spec §4.6, §6.8; rulings 13–16; Review Focus 4)

**Review:** opus.

**Files:**
- Create: `capability/door/.../ui/DoorRow.kt`
- Modify: `ui/DoorTab.kt` (`DoorTab`, `DoorTabHost`), `DoorWords.kt`, `DoorStore.kt` (`unseenIds`), `db/DoorDatabase.kt` (the query), `ui/DoorDimens.kt`, `ui/Snapshots.kt` or the sheet if it shows `eventLine`
- Test: `capability/door/src/test/.../ui/DoorTabTest.kt`, `DoorTabScreenshotTest.kt`, `DoorStoreTest.kt`, `DoorWordsTest.kt` (or where the words are tested); `core/ui` `CopyScanTest` (passes); `app/.../ShellScreenshotTest.kt` if it draws the Door tab

**Interfaces:**
- Consumes: `HhPageHeader` (Task 2); `Icons.HELP` (Task 1).
- Produces: `suspend fun DoorStore.unseenIds(): Set<Long>`; `fun doorSubtitle(events: List<DoorEvent>, newCount: Int): String?` (ruling 13); `fun newRings(count: Int): String` ("1 new ring", "2 new rings"); `const val SOMEONE_RANG = "Someone rang"`, `MOVEMENT = "Movement"`, `NOTICE_FROM_RING = "Notice from Ring"`, `COULDNT_TELL = "Culvery couldn’t tell what this was"`, `NEW = "NEW"`; `fun kindLabel(event: DoorEvent): String` returns the three new words; `fun subLine(event: DoorEvent): String?` (device; for an unrecognised notice "`{device} · {COULDNT_TELL}`" or `COULDNT_TELL` alone); `DoorTab(days: DoorDays, zone: ZoneId, status: String?, newIds: Set<Long>, onOpenSnapshot: (DoorEvent) -> Unit, modifier)`; `DoorRow(event, zone, isNew: Boolean, onOpenSnapshot)` (tag `door_row_{id}`, the tag `door_row_{id}_new`, the thumbnail `door_thumb_{id}` as today).

- [ ] **Step 1: The failing tests** (`DoorTabTest`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `theHeaderCountsNewRings` (§4.6): two unseen rings from Front Door, motion from Back Garden → "Door", "Front Door · Back Garden · 2 new rings".
- `eachRowSaysTheTimeAndWhatHappened` (replaces `eachRowSaysTheTimeWhatAndWhere`): "14:01 · Someone rang" / "Front Door"; "14:00 · Movement"; "13:59 · Notice from Ring" / "Front Door · Culvery couldn’t tell what this was".
- `newRingsCarryATag` (§6.8): the host opens with two unseen rings → both rows show `door_row_{id}_new` "NEW" (spoken "new"); the store has marked them seen (`unseen` empty) — the badge clears.
- `aRingArrivingWhileOpenIsNewAndKeepsItsBadge` (Review Focus 4): with the tab open, insert a ring → its row is NEW and `unseen` counts it (extends `aRingArrivingWhileTheTabIsOpenStaysUnseen`).
- `newLastsOneVisit` (Review Focus 4): open (NEW shown) → leave (ON_PAUSE) → come back (ON_RESUME) → no NEW on those rows.
- `theThumbnailStillOpensTheSnapshot`: `door_thumb_1` is 107 × 60 dp inside a ≥ 48 dp tap height; a click opens the sheet (keeps `aThumbnailOpensTheSnapshot`).
- `DoorStoreTest.unseenIdsAreReadBeforeMarking`: two unseen, one seen → `unseenIds() == {1, 2}`.
- The kind words in their own test: ring, motion, unrecognised with and without a device.
Run: `./gradlew :capability:door:testDebugUnitTest --tests '*DoorTabTest*' --tests '*DoorStoreTest*' --tests '*Words*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — `DoorTabHost`: on `ON_RESUME` and on reselect, in one coroutine, `newIds = store.unseenIds()` then `store.markAllSeen()` (the order matters; one launch, so a slow read can't race the mark); a row is NEW when `id in newIds || !event.seen`. The tab's first item is `HhPageHeader("Door", doorSubtitle(...))`, then the status line, the day labels and `DoorRow`s per Units (ruling 16). `kindLabel`/`eventLine` take the new words; `cantFullyRead` keeps `RING_ALERT`.
- [ ] **Step 3: Compare, check, re-record** — predicted: `door_tab_`, and any `door_snapshot_` / sheet image showing `eventLine`; new `door_tab_new_dark`. Open each against the handoff's 3c (without the panel) and note 29; re-record; the baseline check by prefix.
- [ ] **Step 4: The gate and commit**
```bash
git add capability/door app core/ui
git commit -m "Give the Door tab its title and new-ring count, rows with an icon circle and a NEW tag, and plainer words for each kind"
```

---

### Task 8: Week: the title block, one nav pill, columns and chips, no legend (spec §4.7; rulings 17, 18, 20)

**Review:** sonnet.

**Files:**
- Create: `capability/calendar/.../ui/WeekHeader.kt`
- Modify: `ui/WeekView.kt` (the header, `DayColumn`, `EventChip`, the legend goes), `ui/CalendarType.kt` (the week rows of Units), `Spoken.kt` (`legendName` goes if unused)
- Test: `capability/calendar/src/test/.../ui/WeekViewTest.kt`, `WeekScreenshotTest.kt`; `app/.../ShellScreenshotTest.kt` if it draws the week

**Interfaces:**
- Consumes: `HhPageHeader` (Task 2); `HhRoundButton`; `HhAlpha.tint` (Task 1).
- Produces: `@Composable internal fun WeekHeader(state: WeekViewState, onWeeksAhead: (Int) -> Unit, onAdd: ((LocalDate) -> Unit)?, onReconnect: () -> Unit, onUpdateNow: () -> Unit)` — `HhPageHeader(weekTitle(weeksAhead), subtitle = null, trailing = { nav pill; "This week"; "+ Add event" })` with today's subtitle row (sync icon, `weekLine`, Update now, the reconnect chip) under it; the arrows keep tags `week_earlier`, `week_later`, "This week" `week_this_week`, Add `week_add_event`.

- [ ] **Step 1: The failing tests** (`WeekViewTest`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `theArrowsShareOnePillAndThisWeekSitsBetween` (note 22): `week_earlier` and `week_later` 48 dp inside one pill; at `weeksAhead = 1` `week_this_week` lies between the pill's right edge and `week_add_event`'s left.
- `aDisabledArrowIsTheSharedDisabledLook` (ruling 17): `week_earlier` at week 0 is not enabled (keeps `theArrowsStepAWeekAndStopAtEachEnd`'s stepping).
- `thereIsNoLegend` (note 23; replaces `legendNamesEveryoneAndFamily` and `legendStaysLevel…`): no `week_legend` node.
- `todaysColumnHasAnInsetRing`: the ring pixel lies inside the column's bounds, 2 dp in.
- `aChipReadsTimeAndPersonWithADotAndTwoLines` (extends `aChipSaysWhatWhoWhenAndItsBadgesAsOneItem`): "19:30 · Alex", the title's `maxLines == 2`, the spoken name unchanged.
- `aChipIsTintedWithItsPersonsColour`: the chip's centre pixel is Alex's colour at `HhAlpha.tint` over `surf`.
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*WeekViewTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — per Units; the arrows `HhRoundButton` (quiet, on the pill's `surf2`) in a `surf2` pill; `RoundButton` leaves `WeekView.kt` (the picker keeps it); `addEventInTheHeaderAddsOnToday` and `aLaterWeekAddsOnItsOwnDays` keep passing (ruling 20).
- [ ] **Step 3: Compare, check, re-record** — predicted: `week_`; open each against the handoff's 3a (without the lane); re-record; the baseline check by prefix.
- [ ] **Step 4: The gate and commit**
```bash
git add capability/calendar app
git commit -m "Redraw the week: one title block, both arrows in one pill, tinted chips with two-line titles, and no legend"
```

---

### Task 9: Week: tap empty space to add, the pressed ghost, TalkBack's add (spec §4.7; ruling 19)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/.../ui/WeekView.kt` (`DayColumn`'s tap area, `AddHint` goes), `ui/CalendarType.kt`, `Spoken.kt` (`addOn(date)`)
- Test: `WeekViewTest.kt`, `WeekScreenshotTest.kt`

**Interfaces:**
- Produces: `fun addOn(date: LocalDate): String = "Add on ${date.format(EEE_D)}"` ("Add on Sun 27"); each column's empty-space node tag `week_add_{date}`, `Role.Button`, named `addEventOn(date)`.

- [ ] **Step 1: The failing tests**
- `pressingEmptySpaceShowsTheGhost` (`mainClock.autoAdvance = false`): press (down, no up) below Wednesday's last chip → "+ Add on Wed 22" shown (dashed); release → gone and `onAdd(Wednesday)`.
- `theEmptySpaceIsOneButtonForTalkBack`: `week_add_{date}` exists per column, `Role.Button`, its click → `onAdd(date)`; no add-hint icon is drawn.
- Keep: `aTapOnTheSpaceBelowAColumnsLastChipAddsOnThatDay`, `aTapAnywhereInAnEmptyColumnAddsOnThatDay`, `aTapInTheGapBetweenTwoChipsAddsNothing`, `aTapOnAColumnsHeaderAddsNothing`; remove `eachColumnEndsWithAnAddHintAtLeast48DpTall` and `aTapOnAColumnsAddHintAddsOnThatDay` (the hint goes) — replaced by the two above.
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*WeekViewTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — the column's empty area below the chips is a `Box` filling the rest of the column (`weight(1f)`), semantics as above, `pointerInput` with `detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onAdd(day) })`; the gap and header rules stay where they are today. The ghost per Units while `pressed`.
- [ ] **Step 3: Compare, check, re-record** — predicted: `week_`; new `week_ghost_dark` (a held press). Open it against note 24; re-record; the baseline check by prefix.
- [ ] **Step 4: The gate and commit**
```bash
git add capability/calendar
git commit -m "Let any empty space in a week column add an event on that day, with a ghost while pressed and one button for TalkBack"
```

---

### Task 10: Multi-day: the model — spans, lanes, day of span (spec §2.1; rulings 21–24; Review Focus 1, 2)

**Review:** opus.

**Files:**
- Create: `capability/calendar/.../MultiDay.kt`
- Modify: `CalendarUi.kt` (`EventUi.span`, `WeekUi.spans`, `toUi`), `CalendarRepository.kt` (`week`), `ui/SampleUi.kt` (multi-day samples for later tasks)
- Test: create `MultiDayTest.kt`, `LaneRowsTest.kt`; `CalendarRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `fun StoredEvent.isMultiDay(zone: ZoneId): Boolean` (ruling 21: all-day with `end.date − start.date ≥ 2` days; timed with `end − start ≥ 24 h`).
  - `data class DaySpan(val day: Int, val of: Int)`; `fun StoredEvent.daySpan(date: LocalDate, zone: ZoneId): DaySpan?` (null unless multi-day and on that date; days counted in the household's dates — all-day by its dates, timed from its first to its last local date).
  - `data class SpanUi(val ref: EventRef, val title: String, val person: Person, val first: LocalDate, val last: LocalDate, val startsBefore: Boolean, val endsAfter: Boolean, val label: String, val spoken: String)` — `first`/`last` clamped to the week, `label` "Mon 21 – Thu 24" or "until Thu →" when `endsAfter` (ruling 22).
  - `fun weekSpans(events: List<StoredEvent>, start: LocalDate, zone: ZoneId, people: …): List<SpanUi>`.
  - `fun laneRows(spans: List<SpanUi>): List<List<SpanUi>>` (ruling 22's greedy packing).
  - `EventUi.span: DaySpan? = null` (set by `toUi` for multi-day events); `WeekUi.spans: List<List<SpanUi>> = emptyList()` (the lane rows).

- [ ] **Step 1: The failing tests**
`MultiDayTest` (plain JVM, Europe/London):
- `anAllDayEventOfTwoDaysIsMultiDay` (Mon–Wed exclusive → yes); `oneAllDayDayIsNot` (Bin day); `anOvernightTimedEventIsNot` (22:00–01:00); `twentyFourHoursIsMultiDay`.
- `theDayOfASpan`: half term Mon 21–Fri 25 Oct (end exclusive Sat 26) → Wed 23 is `DaySpan(3, 5)`; Sat 26 → null.
- `aSpanStartingBeforeTheWeekIsClampedAndMarked` (Review Focus 1): Thu 17 – Tue 22 (exclusive Wed 23) with the week from Mon 20 → `first` Mon 20, `last` Tue 22, `startsBefore`, not `endsAfter`; a span to Mon 3 Nov → `endsAfter`, label "until Mon →".
- `aTimedSpanOverTheClockChangeCountsItsDays` (Review Focus 1): Sat 24 Oct 12:00 to Mon 26 Oct 12:00 BST→GMT → three dates, `daySpan` Sun 25 = `DaySpan(2, 3)`.
- `theLabelAndSpokenName`: "Mon 21 – Thu 24"; spoken "Half term, Family, Monday 21 October to Thursday 24 October".
`LaneRowsTest` (plain JVM):
- `separateSpansShareARow`; `threeOverlappingSpansTakeThreeRows` (Review Focus 2); `aSpanInsideAnotherSharesNoRow`; `packingIsStable` (same input in another order → same rows).
`CalendarRepositoryTest`:
- `theWeekCarriesItsSpans`: half term in the week → `week.spans` one row with it; the days still hold its slices (**pin** for `multiDayAllDayEventAppearsOnEveryDayItCovers`).
- `aMultiDayEventsSlicesKnowTheirDay`: the day slices' `span` = `DaySpan(1, 4)` … `DaySpan(4, 4)`; an overnight event's slices have `span == null`.
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*MultiDayTest*' --tests '*LaneRowsTest*' --tests '*CalendarRepositoryTest*'` — Expected: FAIL to compile.
- [ ] **Step 2: Implement** — `weekSpans` over the same stored events `days()` reads (with the pending overlay), filtered by `isMultiDay`; `week()` sets `spans = laneRows(weekSpans(...))`. Dates in the household zone as `days()` does (all-day dates are zone-free).
- [ ] **Step 3: The gate and commit** — Expected: PASS, **no image changed**.
```bash
git add capability/calendar
git commit -m "Work out which events span several days, which lane row each takes in a week, and which day of the span a date is"
```

---

### Task 11: Multi-day: the week's lane (spec §2.1; note 31; ruling 22; Review Focus 1, 2)

**Review:** sonnet.

**Files:**
- Create: `capability/calendar/.../ui/WeekLane.kt`
- Modify: `ui/WeekView.kt` (the lane under the headers, the spacer, chips without spans), `ui/CalendarType.kt` (lane rows of Units)
- Test: create `capability/calendar/src/test/.../ui/WeekLaneTest.kt`; `WeekScreenshotTest.kt`

**Interfaces:**
- Consumes: `WeekUi.spans`, `SpanUi` (Task 10); `HhAlpha.bar` (Task 1); `Icons.DATE_RANGE`.
- Produces: `@Composable internal fun WeekLane(rows: List<List<SpanUi>>, start: LocalDate, columnGap: Dp, onOpen: (EventRef) -> Unit, modifier: Modifier = Modifier)`; bar tag `week_span_{ref.listKey}`.

- [ ] **Step 1: The failing tests** (`WeekLaneTest`, the week at 1116 × 690 dp; each ends with `assertClickablesHaveRoleAndLabel()`)
- `aSpanIsOneBarAcrossItsDays`: half term Mon–Thu → one `week_span_…` whose left is 7 dp inside Monday's column and right 7 dp inside Thursday's; no "Half term" chip in those columns.
- `aBarRunningPastTheWeekSaysUntil` (Review Focus 1): "until Mon →" shown; its right end square (the corner pixel filled).
- `theColumnsSpacerMatchesTheLane` (Review Focus 2): with two lane rows, every column's first chip top equals the lane's bottom plus the gap, within 1 dp; with no spans, no lane and no spacer.
- `aBarOpensItsEvent`: click → `onOpen(ref)`; spoken name from `SpanUi.spoken`.
- `singleDayAllDayStaysAChip`: Bin day is a chip, not a bar.
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*WeekLaneTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — the lane is a `Layout` placing each bar from its first to last column using the same column widths and gap as the columns (pass both, or measure the columns' row); rows 41 dp, 4 apart; each `DayColumn` gets a top spacer of the lane's height; chips filter out refs with spans. Bars per Units: `HhAlpha.bar` tint, the 4 dp colour edge inset at the start, `date_range`, title, label; square ends where `startsBefore`/`endsAfter`.
- [ ] **Step 3: Compare, check, re-record** — predicted: `week_` (unchanged without spans — expect none); new `week_multiday_dark`, `week_multiday_light`, `week_multiday_overlap_dark`. Open each against the handoff's 3a and note 31; re-record; the baseline check by name.
- [ ] **Step 4: The gate and commit**
```bash
git add capability/calendar
git commit -m "Draw events lasting several days as one bar across the week, stacking overlapping ones"
```

---

### Task 12: Multi-day: "Day 2 of 3" on Home's Today (spec §2.1; note 31; ruling 24)

**Review:** sonnet.

**Files:**
- Modify: `capability/calendar/.../ui/TodayCard.kt` (the row's second line), `Spoken.kt` (`todayRowName`), `CalendarUi.kt` (`dayOf(span)`)
- Test: `ui/CardsTest.kt`, `ui/CardScreenshotTest.kt`, `SpokenTimeTest.kt` or where spoken names are tested

**Interfaces:**
- Consumes: `EventUi.span` (Task 10).
- Produces: `fun dayOf(span: DaySpan): String = "Day ${span.day} of ${span.of}"`; spoken `"day ${span.day} of ${span.of}"`.

- [ ] **Step 1: The failing tests** (`CardsTest`; each ends with `assertClickablesHaveRoleAndLabel()`)
- `aMultiDayEventReadsWhichDayItIs`: half term today as day 2 of 3 → the row reads "Day 2 of 3 · Family", spoken "Half term, Family, day 2 of 3".
- `anOvernightEventKeepsItsTimes` (**pin**: `anEventSpanningMidnightShowsOnceAsToday` keeps "20:00–").
- `aMultiDayEventTomorrowShowsOnce`: half term today and tomorrow → one today row, not a tomorrow top-up as well.
Run: `./gradlew :capability:calendar:testDebugUnitTest --tests '*CardsTest*'` — Expected: FAIL.
- [ ] **Step 2: Implement** — where the row shows `timeLabel`, show `dayOf(span)` when `span != null`; the spoken name likewise.
- [ ] **Step 3: Screenshots** — new `today_multiday_dark`. Open it against note 31; the baseline check by name.
- [ ] **Step 4: The gate and commit**
```bash
git add capability/calendar
git commit -m "Show which day of a multi-day event today is on Home's Today"
```

---

### Task 13: The emulator walk-through on `culvery-5`

**Review:** the controller, with the user at the emulator. (The workflow runs `simplify` over the branch first, then the gate.)

**Rules.** As Plan 9a's Task 15: only the `culvery-5` AVD, started with `-no-snapshot-save -camera-front emulated` (check `adb -s "$S" emu avd name`); it holds a debug build, so `install -r` of the debug APK; never uninstall; never a Gradle install task while a device is connected; the user types every PIN; put back every setting changed.

- [ ] **Step 1: The AVD and the build** — `./gradlew :app:assembleDebug`; `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2: Walk the checklist** (one line each)
1. The four tabs line up: the same title block and inset when switching (note 28).
2. Lights: the header count; the tray; an off room's grey slider reading Off; a scene chip on an off room turns it on with that scene; the grid with the AVD's rooms; turning a room on in the Hue app doesn't move its card; Home's › lands on the room.
3. Music: the header counts; a card's controls and labelled volume; the "2 rooms" chip opens the per-room volumes (if a group exists); a quiet room shows Play last and no Next.
4. Door: the header with new rings; a fake ring (`cmd notification post -t 'Front Door' culvery-fake-ring 'Someone is at your Front Door'`) while the tab is open shows NEW and keeps the badge; leaving and coming back clears the NEW; the words for a ring, motion and an unrecognised notice.
5. The week: one nav pill; This week past the current week; tinted chips with two lines; pressing empty space shows the ghost and adds on that day; no legend.
6. Multi-day: add an all-day event over three days in the Google calendar the AVD reads (or the fake's half term, if seeded) → one bar across its days, "Day 2 of 3" on Home on its second day (set the AVD's date if needed, and put it back).
- [ ] **Step 3: The fault loop** — each fault a failing test, the fix, the gate, a commit naming it, `install -r`, re-checked.
- [ ] **Step 4: Put back** every setting; `adb -s "$S" emu kill`.
- [ ] **Step 5: Record** under "Plan 9b (Redesign: the tabs): emulator walk-through".
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9b's emulator walk-through"
```

---

### Task 14: On the tablet

**Review:** the controller with the user. **Needs the SM-T510 on the cable.** 4e's tablet rules (save and restore every setting changed, signed releases with `adb install -r`, never uninstall, the names check before any commit).

- [ ] **Step 1: Install the release** — signed per `docs/setup/release.md`; keep `mapping.txt` in `culvery-notes/mappings/`.
- [ ] **Step 2: At the wall** (the user): each tab at arm's length; the Lights grid with the household's rooms; a real multi-day event on the week and on Home.
- [ ] **Step 3: Record** — "Plan 9b (Redesign: the tabs): on the tablet": what read and what didn't; no names.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9b on the tablet"
```

---

### Task 15: Final review, merge, install

- [ ] **Step 1: The review** — opus, the whole branch against spec §2.1, §2.3, §2.5, §4.4–4.7, §6.8, §6.9 and this plan; one fix wave, each fix with its failing test first and the gate.
- [ ] **Step 2: Merge** — on the user's say: `git merge --ff-only plan-9b-tabs` on `main`; the gate on main; never push.
- [ ] **Step 3: Install** — the release from the merged `main` on the tablet, `install -r`.

---

## Spec coverage (9 design → tasks)

| Spec | Task |
|---|---|
| §2.1 multi-day: the week's lane, "Day 2 of 3" | 10, 11, 12 (creating them: 9c) |
| §2.3 the Lights tray | 2, 3 |
| §2.5 an off room looks off; a scene chip turns it on | 3 |
| §4.4 Lights tab | 2, 3, 4 |
| §4.5 Music tab | 2, 5, 6 |
| §4.6 Door tab | 2, 7 |
| §4.7 Calendar week | 1, 8, 9 |
| §6.8 NEW for the visit | 7 |
| §6.9 sort once | 4 |
| §7 the grid at 4, 6, 9; CopyScanTest; TalkBack | 4; every task |
| §4.8, §4.11, §4.12 | 9c |
