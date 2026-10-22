# Culvery — Plan 9c-1c: When as Starts and Ends, title suggestions, and the walk-through fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–13 are subagent tasks; Task 14 is `simplify` over the branch; Task 15 is the controller on the `culvery-5` AVD with the user; Tasks 16–17 need the user and the son's SM-T510.
>
> **This plan finishes 9c-1 and 9c-1b.** 9c-1b's Tasks 1–16 and its whole-branch simplify are done (ledger `.superpowers/sdd/2026-10-21-culvery-09c1b-calendar-update/progress.md`). Its Task 17 (the emulator walk-through) stopped part-way, and **9c-1b's Tasks 17–19 are replaced by Tasks 15–17 here**, which walk through, review and merge 9c-1, 9c-1b and this plan together.

**Goal:** When is a Starts row and an Ends row with an All day switch, each picked with the calendar's existing steppers and month grid; a new event's title suggests remembered events and fills in the rest; the faults and notes from the paused walk-through are fixed.

**Architecture:**
- **The walk-through fixes first** (Tasks 1–5). They touch the week, the shell's toast, the editor's host and the detail sheet, not the When model, so each lands on today's editor.
- **When is a new value type, `EventWhen`** (Task 6), with the clash check widened to a span (Task 7) and the two shared pickers pulled out of today's panes (Task 8). **Task 9a builds the new panes and rows beside the old ones; Task 9b swaps the editor over** and deletes the Day and Time panes, so every commit leaves a working Add event.
- **Suggestions are a Room table filled by sync** (Task 11), read by `TitleSuggestions` (Task 12) and shown in the editor (Task 13). Nothing is fetched that sync doesn't already read.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00, Material 3), Hilt 2.57.1, Room (as in the module), Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-22-culvery-9c-when-and-suggestions-design.md` ("this spec": rulings §2, When §3, suggestions §4, fixes §5, calls §8, including the user's plan-review rulings in §3.2 and §8 calls 3 and 10). It adds to `docs/superpowers/specs/2026-10-21-culvery-9c-calendar-update-design.md` ("the 9c spec"). The drawings for suggestions are the handoff `docs/design/2026-10-calendar-update/handoff.md` 6a and 6h (dp = px ÷ 1.5). Where this spec, the 9c spec, the handoff or the code disagree, this spec wins. 9c-1b's rulings (`docs/superpowers/plans/2026-10-21-culvery-09c1b-calendar-update.md`) still hold except where a ruling below changes them.

**Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md`.

**Builds on:** branch `plan-9c-forms` in the one checkout `C:\Users\si\code\siland\culvery` (this plan's own commit on top of 57a34ebb). There are no worktrees. Work only there. Never push.

Paths are under `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/` (main) and `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/` (tests) unless they say otherwise.

| # | Task | Review |
|---|---|---|
| 1 | The week: header order, label and dates in one frame, chips shorten the who line | sonnet |
| 2 | The foot: toasts sit above it, its buttons keep their places | sonnet |
| 3 | F2: the editor draws in the frame it is shown | sonnet |
| 4 | The detail sheet's Who chips: busy at once, the last person explained | sonnet |
| 5 | The editor keeps its pane behind the PIN pad and its keyboard down | sonnet |
| 6 | `EventWhen`: Starts, Ends, All day, clock steps, the defaults, the cap | **opus** (the end never at or before the start; no date change from a step; DST) |
| 7 | The clash over a span, naming every busy person | **opus** (the held window over several days; who counts) |
| 8 | Shared pickers: `TimeSteppers` and `DateGrid` | sonnet |
| 9a | The Starts and Ends panes and rows, built beside the old ones | sonnet |
| 9b | The swap: the form, flow and screen on `EventWhen`; Day and Time panes removed | **opus** (the flow; locks; nothing of the old panes left) |
| 10 | Spans everywhere else: repeats, Check, the toast, the detail, the foot hint | sonnet |
| 11 | `remembered_titles`: the table, its migration, filled and forgotten by sync | **opus** (the migration; forgetting; hidden calendars; the 12 months) |
| 12 | `TitleSuggestions`: matching and its words | sonnet |
| 13 | From before: the list, Use this, Copied, the banner and quick picks | **opus** (the child rules; removed people; what is never copied) |
| 14 | `simplify` over the whole branch | controller |
| 15 | The emulator walk-through on `culvery-5` (resumed) | controller + user |
| 16 | On the tablet | controller + user, **needs the SM-T510** |
| 17 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:**
1. **The fixes first** (1–5): small, separate, and the walk-through has to re-check them anyway.
2. **The When model, the span clash and the shared pickers** (6–8) are new code beside the old, so nothing visible changes yet.
3. **New panes and rows beside the old** (9a), each driven by an `EventWhen` and tested alone; then **the swap** (9b) in one commit, then everything else that reads a span (10). 9b can't be split further and stay green: the old Day and Time panes drive range picking and hour/minute/length chips that have no `EventWhen` equivalent, so a green half-swap would need bridge code that the other half deletes.
4. **Suggestions** (11–13): storage, reading, then the screen, which needs the Starts date pane from 9b.

## Rulings against the code

Each is the plan's call unless it cites the spec or the user.

1. **Units** as 9c-1b ruling 1. New values in **Units** below.
2. **Week header order** (spec §2 ruling 4): in `ui/WeekHeader.kt` the actions row is "This week" (only when `weeksAhead > 0`, as now), then the ‹ › pill (`week_earlier`, `week_later`), then Add event. The arrows sit beside Add event whether or not This week shows.
3. **The week's label and dates change together** (spec §5). Today `WeekViewHost` (`ui/CardHosts.kt`) builds `WeekViewState(w, today, s, nowMillis, shown.weeks)`, so "Next week" comes from `shown.weeks` before `produceState` loads that week. The which-week part is worked out from the **loaded** week, `weeksTo(today, w.days.first())`; `shown.weeks` still drives the fetch.
4. **The week chip's who line shortens before it is cut** (spec §5). `fun whoLineForms(people: List<Person>): List<String>` in `Who.kt`: `whoLine(people)` ("Sam + Mia", "Alex, Sam +1"), then "{first} +{n − 1}" ("Sam +1", "Alex +2"); one person has one form. The chip (`ui/WeekView.kt:421`) measures "{start} · {form}" with a `TextMeasurer` and draws the first that fits; only the last may ellipsise. TalkBack keeps `spokenWho`.
5. **Toasts sit above a foot** (spec §5 F3; the plan's pick: shift the toast up, never hide the foot). core/ui `Shell.kt` gains `class ToastClearance` holding a state map from an owner key to a window-px top, `val LocalToastClearance = staticCompositionLocalOf<ToastClearance?> { null }`, and a composable wrapper `KeepsToastsAbove(modifier, content)` that reports its top with `onGloballyPositioned` under its own remembered key and removes **only that key** in a `DisposableEffect`. The clearance used is the highest reported top (the smallest y). `ShellLayers` (app `shell/ui/OverlayLayers.kt`) provides one; `ToastSlot` puts the toast's bottom at `max(ShellTokens.toastBottom, windowHeight − top + ShellTokens.toastAboveFoot)`, `toastAboveFoot = 12.dp`. It wraps the editor's foot and its discard card (`ui/EventEditorScreen.kt` `Foot`), and the detail sheet's foot and confirm card (`ui/EventDetailSheet.kt:167–173`). When the keyboard hides the foot nothing is reported, and the toast keeps its keyboard rule.
6. **Foot buttons keep their places while busy** (spec §5). `HhFooterButton` (`core/ui/.../Buttons.kt:247`) gains `busy: Boolean = false`. With `below = true` and `busy`, the line under the button keeps the hint or reason it showed before busy (held in the composable), or an empty slot of one line's height (`minHeight`, so a wrapped line at font scale 1.5 doesn't jump). Idle with no line still has no slot, so idle images don't move. The editor's foot and the detail sheet's Delete / Change pass `busy`, and keep their widths (busy content drawn inside).
7. **The editor is loaded before it is shown** (spec §5 F2). Cause, from the code: `EventEditorHost` (`ui/EventEditorHost.kt`) waits on `produceState` for the opened event and on `repo.members.collectAsState(null)` then `?: return`, while `OverlayLayer` already paints its full-screen `bg`. The fix:
   - `Opened` (today private, `EventEditorHost.kt:50–57`) becomes `internal` and gains `members`; it carries only what loads once: request, form, flow, members, service, calendar, the opened event, and the first `held`.
   - `internal suspend fun loadEditor(request, repo, editor, today): Opened?` does the reads (`open(...)`, `repo.members.first()`, `repo.held(today).first()`).
   - `suspend fun OverlayHost.openEditor(request, today, repo, editor, weekFocus, load: suspend () -> Opened? = { loadEditor(request, repo, editor, today) })` loads, then `showFullScreen`, **unless another overlay was shown since the tap** (an overlay generation count read before and after the load), so a sheet opened during the load isn't closed.
   - The host still derives the context line and `childOnly` / `childName` from `editor.session.collectAsState()` (a StateFlow, there on the first frame), and collects `repo.held(today)` live with `initial = opened.held`.
   - A gone event closes silently as today; only an exception toasts `couldNotOpen`.
   - The week's Add event, an empty column and Home's + call it through `rememberEventAdder` (a `rememberCoroutineScope`, ignoring a tap while one loads); the detail's Change and For row through the detail host's action runner.
8. **Detail chips show busy at once** (spec §5). `HhPersonChip(..., busy: Boolean = false)` (`core/ui/.../Controls.kt:405`) draws `Icons.SCHEDULE` in place of its dot and adds the state "Saving". `EventDetailHost` keeps a `pending: Set<PersonId>?` and the tapped id: the chips show the pending set at once and the tapped chip is busy until the result. **Every result except `Done` / `Queued` puts the set back** (a refused child PIN, a cancelled pad, a rejection); `NotEditable` closes as now. Other chips stay disabled while busy.
9. **The last person is explained** (spec §5): a tap that would clear the last person of the **shown (pending) set** writes nothing and toasts "An event needs at least one person." through a new `CalendarEditor.needsAPerson()` (the toaster lives there, `CalendarEditor.kt:152–155`).
10. **The pane behind the PIN pad stays put** (spec §5). The plan's reading of the cause: a pane pick queues a move-on (`EditorFlow.moveOnSoon`, 250 ms), and Add event tapped within it opens the PIN pad while the move-on fires. `EditorFlow.stay()` drops a pending move-on; Add event, Save changes, Delete and ✕ call it first. **Task 5's first test confirms the cause on the real path; if it can't reproduce it, the implementer reports back before changing anything.**
11. **The keyboard comes up once per opening** (spec §5). Suspected cause: `EventEditorScreen`'s `LaunchedEffect(Unit)` asks for title focus and `keyboardController?.show()` again. But the PIN pad doesn't remove the overlay from composition (`OverlayLayers.kt:89–90` keeps it and sets `hidden`), so that may not be the path. **Task 5's test drives the real path first** (`ShellLayers` with the PIN pad shown then hidden over the editor, title focus cleared before) and counts `show()`; if it doesn't reproduce, report back as ruling 10. The intended fix: the ask moves to a form flag, `EditorForm.titleFocusAsked`, set on the first ask, and after the title loses focus only a tap on the title shows the keyboard.
12. **`EventWhen` is the When** (spec §3), a new immutable `data class EventWhen(allDay, startDate: LocalDate?, startClock: LocalTime, endDays: Int, endClock: LocalTime)` in `EventWhen.kt`. `endDays` is the end's date minus the start's (all day: last day minus first). An offset rather than an end date keeps "Ends follows Starts" while no date is chosen and when the start date moves (spec §3.1, §3.3), and reads the cap directly. `EditorForm` holds `var eventWhen: EventWhen` and `timesTouched` in place of `day`, `multiDay`, `until`, `awaiting`, `time` and `length`.
13. **Defaults** (spec §3.4): All day off; the start is the next whole hour if the start date is today, not past 23:00, else 09:00 (with no date yet, today's rule); the end is the start + 1 hour (from 23:00: 00:00 the next day). While `timesTouched` is false, a Starts date pick re-applies the default for that date. Defaults don't count as edited: `edited` compares `eventWhen` with the value it opened with.
14. **Steppers work like a clock** (spec §3.2, the user at the plan review; replaces the no-carry rule). A step is ±15 minutes (an off-quarter minute snaps to the next quarter in the step's direction, so 10:37 up is 10:45, down 10:30) or ±1 hour, on the clock with the date fixed. **No step changes a date.**
    - **Starts:** a step that would leave the day wraps within it (23:45 up → 00:00, 00:00 down → 23:45, 23:30 hour up → 00:30), on the same start date. The end follows, keeping the length, and may cross into the next day.
    - **Ends:** a step that would leave the end's day, or reach or pass the start, is **disabled** (`canStepEnd(field, up)`).
    - `hourStep` and `minuteStep` (`ui/Pickers.kt`) keep `TimePickerCard`'s own behaviour until 9b deletes them with it.
15. **Ends date** (spec §3.3): the grid allows the start date to start + 59 days; others are dimmed and can't be tapped. A pick keeps the end time. Picking the start's own day with the end time at or before the start makes the end the start + 1 hour (which may land on the next day). All day: the end is the last day, written +1 as today.
16. **Starts date** keeps the length (`endDays`, `endClock` unchanged), unless the defaults re-apply (ruling 13).
17. **The cap** (spec §3.3): `endDays > 59` gives `UNTIL_TOO_LATE`, reworded "An event can last up to 60 days. Pick an earlier end." (moved from `EditorForm.kt:27` to `EventWhen.kt`).
18. **Locks** (spec §3.3; changes 9c-1b ruling 21): a timed span opens unlocked in Change. **Any opened span with `endDays ≥ 60`, timed or all day, stays locked**, since Save would refuse it: All day, Starts and Ends show `lockedDatesLabel` with `lock` and open no pane, and a title change still saves. A mixed timed/all-day event stays locked as today. An event read with its end at or before its start opens with the end as start + 1 hour, and `keepsOwnSpan` means an unchanged When writes nothing.
19. **Midnight ends** (as `daySpan`, `CalendarUi.kt:267`): a timed end at exactly 00:00 belongs to the day before. So 23:00–00:00 counts as one day for the clash (free starts, plain words), and toggled to All day it is one day.
20. **The span** is `fun EventWhen.span(zone: ZoneId): EventSpan?` (`Timed(start: ZonedDateTime, end: ZonedDateTime)` or `AllDay(first, endExclusive)`), null with no start date: the two wall times in the zone, no midnight cap. It replaces `draftSpan`.
21. **The clash over a span** (spec §3.5). `Held.check(people: List<PersonId>, from: LocalDateTime, to: LocalDateTime, zone, except, order)` replaces the day/start/length overload; `busyHoursAndCheck` and the private `checkOf` take `from`/`to` too until 9b deletes `busyHours`. The whole span must be held, else `None`. `FreeCheck.Busy(clashes: List<Clash>, more: Int, freeStarts: List<LocalTime>)`, `data class Clash(person: PersonId, event: HeldEvent)`: each chosen person's first overlapping timed event, in household order, up to two, the rest counted in `more`. Free starts only on one day (ruling 19), as today's rules. **Over several days** `clashes` holds only the first clash by start, `more` is 0 and there are no free starts. All day never checks.
22. **Clash words** (spec §3.5), `clashLine(check, single, overDays, names, zone)` keeping `names: (PersonId) -> String`: one person chosen, "Overlaps Piano, 15:30–16:15"; several, "Sam has Dentist, 12:30–13:00; Mia has Piano, 12:45–13:30", then " … and {n} more"; over several days the time reads with its day ("Overlaps Dentist, Sat 24 Oct 12:30–13:00"). The free line stays as 9c-1b ruling 32.
23. **Span words** (spec §3.7): `fun spanWords(span: EventSpan, zone: ZoneId): String` in `CalendarUi.kt`, built on `dayRange`: "Sat 26 Sep, 10:00–10:45"; "Fri 23 Oct 18:00 – Sun 25 Oct 16:00"; "Mon 19 – Fri 23 Oct, all day"; "Sat 26 Sep, all day". The detail sheet's `whenLabel` keeps its own "·" forms ("Mon 19 Oct – Fri 23 Oct · All day", "Today"); its timed span over several days already reads as `spanWords`.
24. **The rows** (spec §3.1): title, Who, All day, Starts, Ends, Repeats (Repeats only for an adult's new event). All day is `HhSwitchRow("All day", null, checked, "editor_all_day", onChange)` in the left column, with no pane. Starts and Ends are each one `EditorWhenRow` with two buttons: the date and, when All day is off and a date is set, the time. TalkBack: "Starts, Saturday 26 September", "Starts at 10:00", "Ends, Sunday 27 September", "Ends at 11:00"; unset, "Starts, Choose a day". **Ends with no start date** shows its label alone and isn't clickable ("Ends, choose a start day first"). Tags (Clash, Copied, Too long) sit on the row they name; the clash on Starts.
25. **Keyboard up** (9c-1b ruling 27): five rows at 61 dp don't fit 480 dp, so the All day row hides while the title is focused with the IME up (85 + 75 + 9 + 4 × 61 + 27 + 29 ≈ 469 dp).
26. **The flow** (spec §3.6). `enum class EditorStep { Who, StartDate, StartTime, EndDate, EndTime, Repeats, Check }`; `editorSteps` drops the time steps while All day is on and `Repeats` when the form can't repeat. Questions: "Who’s it for?", "Which day does it start?", "What time does it start?", "Which day does it end?", "What time does it end?", "Does it repeat?", "Check it". Next names: "Next: Who", "Next: Starts", "Next: Start time", "Next: Ends", "Next: End time", "Next: Repeats", "Next: Check". A Starts date pick moves on after `MOVE_ON_MS` to `StartTime`, or with All day on to `Repeats` (else `Check`). An Ends date pick moves on to `EndTime`, or with All day on to `Repeats` (else `Check`). Steppers never move on. `moveOnSoon(to: EditorStep)`. Skip shows on `EndDate`, `EndTime` and `Repeats`; on Ends it goes to Repeats (else Check) and changes nothing.
27. **What's missing** (spec §3.6): the `needsLine` forms unchanged; "a day" is the Starts date.
28. **What goes** (spec §2.2, §3.8), all in 9b: `TimePickerCard`, `PickerCard`, `PickerLayer`, `EditorPicker`, `hourStep`, `minuteStep` (only `EventEditorScreen` and `PickersTest` use them); `ui/TimePane.kt`; `ui/DayPane.kt` (its grid lives on in `DateGrid`); `TimeChoice`, `EDITOR_LENGTHS`, `lengthChip`, `lengthLabel` (its only caller is the Time pane; its `WriteCopyTest.kt:86` case goes), `END_BEFORE_START`, `draftSpan`, `busyHours`, `busyHoursAndCheck`, `pickerTime`, `endPickerTime`, `untilTimeLabel`, `setMultiDay`, `tapDay`, `chooseHour`, `chooseMinute`, `chooseLength`, `chooseEndTime`, `LASTS_MORE_THAN_A_DAY`, `TAP_FIRST_THEN_LAST`, `AT_A_TIME`, `OTHER_TIME`, `ALL_DAY_ONLY`, `HOUR_GROUPS`, and every test and image that draws them (re-homed as Task 8 and 9a say). 9c-1b fault F1 goes with them.
29. **Repeats and spans** (spec §3.3, §8 call 8): `fun RepeatChoice.fitsLength(length: Duration): Boolean`: Daily ≤ 1 day, Weekly ≤ 7, Fortnightly ≤ 14, MonthlyOnWeekday ≤ 28, Yearly and Never always. The length is end − start (timed) or the number of days (all day). A choice that doesn't fit is locked in the Repeats pane with "Too long to repeat that often." A When change that rules out the chosen repeat sets it to Never, and the Repeats row carries a "Too long" tag (`info`) until Repeats is opened or changed. *(Tag copy is the plan's: the spec says only "says so".)*
30. **Check** (spec §3.7): a one-off's `{when}, {time}` becomes one `spanWords` ("Dinner for Sam, Sat 26 Sep, 10:00–10:45."). A series keeps 9c-1b ruling 36's wording ("every Saturday, 10:00–10:45"). One date chip per occurrence; one over several days reads "Fri 23 – Sun 25 Oct". The "On the week" preview draws a span with the lane's bar: `LaneBar` (`ui/WeekLane.kt:80`) becomes `internal` with `onOpen: (() -> Unit)?`.
31. **The toast names the span's days** (spec §3.7) when it names a date (9c-1b ruling 38): "Added Camp, Fri 23 – Sun 25 Oct."
32. **The foot hint** (spec §3.7), first match wins: a clash → `CLASH_HINT`; New, Add event enabled, the form can repeat and Repeats unanswered → "Repeats are optional"; New and the active step is Check → "Everything’s set"; otherwise none. So a complete Change shows no hint. `OPTIONAL_HINT` and `TIME_OPTIONAL_HINT` go.
33. **The remembered titles table** (spec §4.1): `RememberedTitleEntity`, table `remembered_titles`, primary key `(connectionId, titleKey)`, holding the spec §4.1 columns plus `sourceId` (for the visibility join) and `sourceEventId = seriesId ?: remoteId`. Its people tag is the event's tag, else its calendar's person id. Version 8, `MIGRATION_7_8` (table and an index on `rememberedDate`) in `ALL_MIGRATIONS`. Commit the generated `schemas/.../8.json` only: `build.gradle.kts:12` already serves `schemas/` as debug assets.
34. **The key** (spec §4.1–§4.2): `titleKey(title)`: trim, collapse inner whitespace, NFD with combining marks dropped, `lowercase(Locale.ROOT)`. "Café" and "Cafe" share a key; the row keeps the title as last typed.
35. **The rule kept** (spec §4.1): when `choiceOf(rule, start)` is in `EDITOR_REPEATS` and not `Never`, stored as `recurrenceRule(choice, RepeatEnd.Never, start)`; else null.
36. **Filling** (spec §4.1). `CalendarStore.applySync` gains `today: LocalDate` (required; `CalendarSync` passes today in the household zone from its clock; the 55 test calls change mechanically, on purpose). In its transaction it folds the upserted events into one candidate per key, a series once:
    - **Only visible sources** are folded (`source.visible = 1`).
    - **The remembered date** of a candidate is its latest occurrence on or before today, else its earliest after; `better(a, b, today)` prefers on-or-before-today and the later of two such, else the earlier of two future dates.
    - **Same supplier** (same `sourceEventId`): the candidate's fields replace the row's, and the date is `better(old, new)`, so editing one future instance of a series keeps "last on".
    - **Another supplier** replaces the row only with a better date.
    - **A rename**: a row whose `sourceEventId` is an upserted event's but whose key differs is re-supplied from another stored event with that key, else deleted.
    - **Defensive:** an unreadable rule becomes null; an event whose candidate can't be read is skipped; no exception message names a title. A bad event never stops the mirror update.
    - **Prune:** rows before `today − 12 months` are deleted in the same transaction. A wall clock jumping a year ahead forgets everything once; that is accepted.
37. **Forgetting** (spec §4.1, §8 call 9). Before any delete — `applySync`'s `removedIds` or a full replace's dropped ids, `applyDeleted`, `applySeriesDeleted`, `removeSourceRows` — read the removed events' `(titleKey(title), seriesId ?: remoteId)`. Afterwards, for each such key whose row's supplier has no stored event left (by `seriesId ?: remoteId`, so a series removed instance by instance counts), the row is re-supplied from another stored, visible event with that key (ruling 36's rules), else deleted. One cancelled occurrence leaves the series' title. `removeSourceRows` (a calendar no longer listed) also deletes that source's rows first. `removeConnection` deletes the connection's rows in its transaction (the debug sample's removal included).
38. **Seeding** (spec §4.1): `CalendarStore.seedRememberedTitles(connectionId, today)` runs in one transaction that returns at once if the connection is gone, and otherwise fills from the stored events of visible sources when the connection has no rows, applying the 12-month cut. `CalendarSync` calls it at the start of each pass. Incremental syncs return only changes, so without it old events would never be remembered.
39. **Hidden calendars are never suggested** (spec §8 call 10): `CalendarStore.rememberedTitles()` reads `remembered_titles` joined to `source` on `(connectionId, sourceId)` with `visible = 1`, which also hides rows whose source or connection has gone.
40. **Suggestions show to anyone at the wall, with no PIN** (spec §8 call 10, the user). On the design-review list.
41. **Local only** (spec §4.1): the calendar database is already outside backup and transfer (`allowBackup="false"`, `data_extraction_rules`). No log line names a title.
42. **`TitleSuggestions`** (spec §4.1–§4.2): `class TitleSuggestions(store: CalendarStore, zone: HouseholdZone, clock: WallClock)`, built once by `CalendarRepository` (`val suggestions`), not a new Hilt binding, since the repository is built in 18 places and the editor host already has it; one per repository keeps the spec's singleton. `suspend fun matching(typed: String, max: Int = 3): List<Suggestion>` reads `store.rememberedTitles()`: nothing under 2 characters of `titleKey(typed)`; key-prefix matches, then word-prefix matches; latest date first within each; one per key across connections (the latest).
43. **Suggestion words** (spec §4.3–§4.4), in `SuggestionWords.kt`:
    - **Who:** `whoLine` of the people still in the household; none left, or Family, reads "Family" in the muted line and "Everyone" in the fill line. The row's colour bar is `StripedBar` for a set, the person's colour for one, `FAMILY_COLOR` for Family.
    - **Muted line:** parts joined with " · ". A weekly series "Saturdays 10:00–10:45", another series its wording then the time; then "last on Sat 12 Sep", or "next on Sat 3 Oct" when its date is after today. A one-off is its date and start ("Family · Sat 11 Jul, 10:30").
    - **Fill line:** "Fills in " then who, the time ("10:00–10:45", "all day", "all day for 3 days") and the repeat lowered, joined as a list ("A and B", "A, B and C"): "Fills in Mia, 10:00–10:45 and every Saturday", "Fills in Everyone and 10:30–12:00", "Fills in Sam and all day for 3 days". *(The spec's third example has a comma; one rule can't give all three. For the design review.)*
    - **Spoken:** "Guitar lesson. Fills in Mia, 10:00 to 10:45 and every Saturday".
    - **Banner:** "Copied from {title} ({when}): {copied}. Tap any row to change it. The date is never copied." `{copied}` lists what was filled ("who, time and repeats", "time").
44. **Use this** (spec §4.4): `fun EditorForm.use(s: Suggestion)`, through `edit {}` (so it does nothing while saving), fills the title, Who (people left, else Everyone; a child keeps themselves), All day, the start time and the end from the length (or the days), and the repeat (dropped for a child, or when it doesn't fit, ruling 29); `repeatEnd` stays `Never`; the Starts date is cleared. It **sets `timesTouched`**, so a later date pick keeps the copied time. It records `copied: Set<CopiedRow>` (`Who`, `Starts`, `Repeats`; not Who for a child), each cleared when that row changes (Starts: a time or All day change, not the date pick). It counts as an edit, clears focus and makes `StartDate` active.
45. **Quick picks after Use this** (spec §4.4, §8 call 7): `quickPicksFor(s, today)`: Weekly the next three of its weekday from today (today included); MonthlyOnWeekday the next three of its rule's nth (or last) weekday; Fortnightly the next three days on its fortnight; Yearly the next one (29 Feb only in a leap year); else none. The weekday, the fortnight and the yearly date are anchored on `seriesStart`, else `rememberedDate` (any occurrence keeps the fortnight).
46. **The From before pane** (spec §4.3): while the title is focused on a New event, the screen asks `matching` with `collectLatest` over `snapshotFlow { title }` (no debounce: a local query; a slower earlier answer never replaces a later one). With results, the right pane is "From before", up to three rows and "A suggestion never copies the date. Or keep typing; Next on the keyboard moves to Who."; without, the active row's question. Change never asks. A row is one button (`Role.Button`, ruling 43's spoken label, `onClickLabel = "Use this"`) with the pill drawn inside.
47. **Icons:** `content_copy` is added in Task 13. `schedule` and `info` are in the font.
48. **The form stays plain in-memory state** (9c-1 ruling 20). **`$LISTS`** is `C:/t9`.

## Notes for the end-of-v1 design review

From this plan's calls, added to 9c-1b's list:
- busy hours went with the hour chips; the clash line carries them (spec §3.5);
- the Next labels and the Ends row's label alone before a start day;
- the All day row hides while the keyboard is up;
- the "Too long" tag on Repeats;
- the fill line's joining and the suggestion pattern words;
- suggestions shown to anyone at the wall (spec §8 call 10);
- the toast 12 dp above a foot; the busy detail chip's clock icon.

## Units (converted once, px ÷ 1.5)

| Where | Handoff px | dp / sp |
|---|---|---|
| Suggestion row | `s2`, r 26; colour bar; title 28/600; fill line 19/600 accent + icon; pill 64 | `surf2`, r 17; `StripedBar` / `ColourBar` 4 wide; 19 sp W600; 13 sp W600 `accent`, icon 16; pill 48 tall (touch), `accentSoft` / `accent` |
| Muted line, under-list line | 21 | 14 sp `mute` |
| Banner | `accentSoft`, r 22, `content_copy` | `accentSoft`, r 15, icon 20, 14 sp |
| Copied tag | green | the Clash tag's shape, `accentSoft` fill, `accent` ink, `content_copy` 16 |
| Steppers in a pane | `TimePickerCard`'s | `CalendarDimens.stepperWidth` 88, `stepperHeight` 52, `stepperRadius` 18, `timeColumnGap` 14, `colonBottom` 10 (reused) |
| Toast above a foot | — | `ShellTokens.toastAboveFoot` 12 |

**Height budget** at 1280 × 800 dp, adult's new event: top bar 85 + title 75 + 9 + five rows 385 + four gaps 36 + foot 116 + bottom 29 ≈ 735 dp. Keyboard up: ruling 25.

## Global Constraints

**From the spec, verbatim:**
- §1 "The goal is still the 9c spec's: **an Add event that is obvious to use.**"
- §2.1 "One **Starts** row (date and time), one **Ends** row (date and time) and an **All day** switch replace the Day row and the Time row."
- §3.2 "**The steppers work like a clock** … Minutes carry into the hour both ways: 11:00 down gives 10:45, and 10:45 up gives 11:00. Hours step by 1. **No step ever changes a date**; the date changes only from a date pick."
- §3.3 "**The end can't be at or before the start.** Any Ends stepper button whose step would put the end at or before the start is disabled."
- §4.1 "**Local only:** it is never sent anywhere, and isn't in any export or log."
- §4.2 "**Only for New events.** Change never suggests."
- §4.4 "**Never copied:** the date, or when a repeat ends (Ends stays "Keeps going")."
- §8.10 "Hidden calendars' titles are never remembered or suggested."
- §5 "To be built with this, each with a failing test first".

**Copy** (verbatim, curly apostrophes in code):
- **Rows:** "All day", "Starts", "Ends", "Choose a day"; TalkBack "Starts, Saturday 26 September", "Starts at 10:00".
- **Panes:** "Which day does it start?", "What time does it start?", "Which day does it end?", "What time does it end?"; "Skip".
- **Cap:** "An event can last up to 60 days. Pick an earlier end."
- **Repeats:** "Too long to repeat that often."
- **Clash:** "Sam has Dentist, 12:30–13:00; Mia has Piano, 12:45–13:30", "… and 1 more".
- **Span words:** "Sat 26 Sep, 10:00–10:45", "Fri 23 Oct 18:00 – Sun 25 Oct 16:00", "Mon 19 – Fri 23 Oct, all day", "Sat 26 Sep, all day"; a series "every Saturday, 10:00–10:45".
- **Foot:** "Repeats are optional".
- **Suggestions:** "From before"; "Mia · Saturdays 10:00–10:45 · last on Sat 12 Sep", "next on Sat 3 Oct", "Family · Sat 11 Jul, 10:30"; "Fills in Mia, 10:00–10:45 and every Saturday", "Fills in Everyone and 10:30–12:00"; "Use this"; "A suggestion never copies the date. Or keep typing; Next on the keyboard moves to Who."; "Guitar lesson. Fills in Mia, 10:00 to 10:45 and every Saturday"; "Copied"; "Copied from Guitar lesson (last on Sat 12 Sep): who, time and repeats. Tap any row to change it. The date is never copied."
- **Fixes:** "An event needs at least one person."

**Privacy:** remembered titles stay in the calendar database (ruling 41); hidden calendars are never read for them (ruling 39). Log lines and exception messages name no title and no person.

**From the house rules** (as 9c-1b; binding):
- **Build:** package root `uk.co.siland.culvery`; `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only. **No new library; never change a version.** No deprecated API.
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug`. Never plain `test`, `check` or `build`. Never a Gradle `install…` task. No subagent touches a device. **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`** for Gradle. `$LISTS` is `C:/t9`, never exported.
- **Tests that can fail:** every new test fails before its change; one that passes first is labelled **pin**. Timer tests run on virtual time, under `compose.mainClock` for UI, one frame before and after the boundary. Colour checks use `pixelAt(node, dx, dy)`. Tokens aren't asserted against their literals.
- **Every UI task ends the same way:** `compareRoborazziDebug`; open each `*_compare.png` and each new image against the spec section the task names; record; the baseline check with the predictions in `$LISTS/expected-baselines.txt` / `$LISTS/expected-prefixes.txt` (anything outside stops the task); the gate; commit.
- **Copy and accessibility:** copy names what the user does; actions keep their name; errors say what went wrong and how to fix it. `CopyScanTest` passes. Every changed screen's test ends with `compose.assertClickablesHaveRoleAndLabel()`.
- **Code:** icons from `Icons`; a new one goes in `tools/fonts/icons.txt` and the subset is rebuilt from the full font at 608ce00 (`git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$LISTS/material-symbols-full.ttf"`, then `python tools/fonts/subset.py --source "$LISTS/material-symbols-full.ttf"`; `IconFontTest` passing; `NO_FILLED_FORM` if needed). Layout numbers in the module's dimens object. Comments say what the code can't, one line, spec refs as `(9c §3.3)`, never plan task or ruling numbers. Sample names only (Alex, Sam, Mia). UTF-8.
- **Commits and git:** a plain sentence, no trailer of any kind. Never push. **Never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **The end near the start, and the day's edges.**
   - **Cases:** start 10:00, end 11:00 same day: hour down, minute down ×4; start 10:00, end 23:00: hour up; end 23:45: minute up; start 22:00–23:00, Starts hour up twice; Starts 23:45 minute up and 00:00 minute down; an end on a later day at 00:00, minute down; start 23:30 (end 00:30 next day), Ends date moved back to the start's day.
   - **Expected:** hour down disabled (11:00 → 10:00 reaches the start); minute down gives 10:45, 10:30, 10:15, then is disabled; hour up and minute up past 23:xx are disabled (they'd leave the day); 22:00–23:00 → 23:00–00:00 (+1) → 00:00–01:00 on the same start date; Starts wraps to 00:00 and to 23:45 with the date unchanged; the later-day minute down is disabled (it would leave the end's day); the move back gives 00:30 the next day.
   - **Tests** (Task 6): `EventWhenTest.anEndsStepThatWouldReachTheStartIsDisabled`, `anEndsStepThatWouldLeaveItsDayIsDisabled`, `aStartsStepWrapsWithinTheDayAndTheEndFollows`, `movingTheEndBackToTheStartsDayMakesItAnHourAfter`.
2. **A timed span over the clock change.**
   - **Cases:** Fri 23 Oct 18:00 – Sun 25 Oct 16:00 in Europe/London (clocks go back Sun 25 Oct 2026); an all-day Sat 24 – Sun 25 Oct; a start at 01:30 on 25 Oct (it happens twice).
   - **Expected:** the wall times are exactly 18:00 and 16:00; the all-day end is written Mon 26 Oct exclusive; 01:30 takes the earlier offset (+01:00, `ZonedDateTime.of`'s default).
   - **Tests** (Task 6): `EventWhenTest.aTimedSpanOverTheClockChangeKeepsItsWallTimes`, `anAllDaySpanIsWrittenWithTheDayAfter`, `anAmbiguousClockTimeTakesTheEarlierOffset`.
3. **A remembered title forgotten, or not.**
   - **Cases:** two events "Swimming", one deleted on the phone; the only "Swimmnig" deleted; a series deleted on the phone (every instance in `removedIds`); one cancelled occurrence; a series undone; a full replace that drops an event; a renamed event; a calendar no longer listed; a hidden calendar; a connection removed, and a seed racing that removal; a row 12 months and a day old, over two passes; the same title on two connections.
   - **Expected:** "Swimming" stays, supplied by the other; "Swimmnig" goes; the deleted series goes; the cancelled occurrence leaves it; the undone series goes; the dropped event goes; the old name goes; the unlisted calendar's titles go; the hidden calendar's titles are never suggested; the connection's rows go and no seed brings them back; the old row stays gone; the title shows once, from the latest.
   - **Tests:** Task 11 `CalendarStoreRememberedTest` (named there); Task 12 `TitleSuggestionsTest.theSameTitleOnTwoConnectionsShowsOnce`, `aHiddenCalendarsTitlesAreNotSuggested`.
4. **Use this with people or rules that don't fit.**
   - **Cases:** a remembered "sam,mia" after Sam is removed; after both are; Mia (child) uses an Alex + Sam weekly suggestion; a remembered 9-day all-day event with a Weekly rule; a monthly-by-date rule; Use this then a date pick.
   - **Expected:** Who is Mia; Who is Everyone; Who stays Mia with no Copied tag on Who, the repeat dropped and "time" alone in the banner; the repeat dropped, the banner without "repeats"; the rule was never stored, so Repeats stays "Doesn’t repeat"; the copied time survives the date pick.
   - **Tests:** Task 13 `EditorFormSuggestionTest.aRemovedPersonIsLeftOut`, `nobodyLeftIsEveryone`, `aChildKeepsThemselvesAndLosesTheRepeat`, `aRepeatThatDoesNotFitTheSpanIsDropped`, `pickingTheDateAfterUseThisKeepsTheCopiedTime`; Task 11 `RememberedTitlesTest.aRuleTheEditorDoesNotOfferIsNotKept`.
5. **Changing an event made elsewhere.**
   - **Cases:** a timed Fri 18:00 – Sun 16:00 made on a phone, saved with only the title changed; a timed span of 70 days; an all-day 70-day span; an all-day event with All day toggled off then on.
   - **Expected:** no start or end is written (`keepsOwnSpan`); both 70-day spans are locked and a title change saves; toggling back leaves `edited` false.
   - **Tests** (Task 9b): `EditorFormTest.aTimedSpanMadeElsewhereSavesWithoutTouchingItsTimes`, `aTimedSpanOverSixtyDaysSavesATitleChange`, `anAllDaySpanOverSixtyDaysIsLocked`, `togglingAllDayBackIsNotAnEdit`.

---

## File Structure

```
Who.kt (whoLineForms), ui/WeekHeader.kt, ui/CardHosts.kt, ui/WeekView.kt (Task 1)
core/ui/.../Shell.kt (ToastClearance, KeepsToastsAbove, toastAboveFoot), core/ui/.../Buttons.kt (busy), app/.../shell/ui/OverlayLayers.kt, ui/EventEditorScreen.kt, ui/EventDetailSheet.kt (Task 2)
ui/EventEditorHost.kt (Opened, loadEditor), ui/Sheets.kt (openEditor), ui/EventDetailHost.kt, ui/CardHosts.kt, app overlay generation (Task 3)
core/ui/.../Controls.kt (HhPersonChip busy), ui/EventDetailHost.kt, ui/EventDetailSheet.kt, CalendarEditor.kt (needsAPerson) (Task 4)
ui/EditorFlow.kt (stay), ui/EventEditorScreen.kt, EditorForm.kt (titleFocusAsked) (Task 5)
EventWhen.kt (new), EditorForm.kt (UNTIL_TOO_LATE moves out) (Task 6)
Held.kt, EditorWords.kt, CalendarUi.kt (spanWords), ui/TimePane.kt (Task 7)
ui/TimeSteppers.kt (new), ui/DateGrid.kt (new), ui/Pickers.kt, ui/DayPane.kt (Task 8)
ui/StartsEndsPanes.kt (new), ui/EditorRows.kt (EditorWhenRow) (Task 9a)
EditorForm.kt, ui/EditorFlow.kt, ui/EditorPane.kt, ui/EditorRows.kt, ui/EventEditorScreen.kt, ui/EventEditorHost.kt, EditorWords.kt, ui/CheckPane.kt, ui/RepeatsPane.kt; deletes ui/TimePane.kt, ui/DayPane.kt, the picker card (Task 9b)
Recurrence.kt, ui/RepeatsPane.kt, ui/CheckPane.kt, ui/WeekLane.kt, EditorWords.kt, CalendarEditor.kt, ui/EventEditorScreen.kt (Task 10)
db/CalendarDatabase.kt, db/Migrations.kt, RememberedTitles.kt (new), CalendarStore.kt, CalendarSync.kt, schemas/.../8.json (Task 11)
TitleSuggestions.kt (new), SuggestionWords.kt (new), CalendarRepository.kt (Task 12)
ui/FromBeforePane.kt (new), EditorForm.kt, ui/EditorRows.kt, ui/StartsEndsPanes.kt, ui/EventEditorScreen.kt, ui/EventEditorHost.kt, tools/fonts/icons.txt, the font, Icons (Task 13)
docs/superpowers/plans/2026-09-23-plan1-followups.md (Tasks 15–17)
```

---

### Task 1: The week: header order, label and dates in one frame, chips shorten the who line (spec §2 ruling 4, §5; rulings 2–4)

**Review:** sonnet.

**Files:** Modify `ui/WeekHeader.kt`, `ui/CardHosts.kt`, `Who.kt`, `ui/WeekView.kt:421`. Test: `ui/WeekViewTest.kt`, `WhoTest.kt`, new `ui/WeekViewHostTest.kt`.

**Interfaces:** Produces `fun whoLineForms(people: List<Person>): List<String>`.

- [ ] **Step 1: The failing tests.**
  - `WeekViewTest.theHeaderReadsThisWeekThenTheArrowsThenAddEvent`: on next week, `week_this_week.right < week_earlier.left`, `week_later.right < week_add_event.left`; on this week and next week, `week_add_event.left − week_later.right` is the same number.
  - `WeekViewHostTest.theWeeksLabelAndDatesChangeInTheSameFrame`: the store is built over a test DAO that wraps the real one (as `CountingDao` does) and holds `eventsBetween` on a `CompletableDeferred`. Tap `week_later`. While held, the subtitle still reads "This week · Wed 23 – Tue 29 Sep"; once released, "Next week · Wed 30 Sep – Tue 6 Oct". Never "Next week" with the old dates (checked under `mainClock.autoAdvance = false` at each step). It fails before the fix.
  - `WhoTest.theWhoLineShortensBeforeItIsCut`: `[Sam, Mia]` → `["Sam + Mia", "Sam +1"]`; three → `["Alex, Sam +1", "Alex +2"]`; one → `["Sam"]`.
  - `WeekViewTest.aNarrowChipShowsTheShorterWhoLine`: the column width is computed in the test with a `TextMeasurer` to fit "17:00 · Sam +1" but not "17:00 · Sam + Mia"; the chip shows the former with no ellipsis, and its description still says "Sam and Mia".
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 2–4.
- [ ] **Step 3: Images.** Predicted prefix: `week_`.
```bash
git add capability/calendar
git commit -m "Put This week before the week's arrows, change its label with its dates, and shorten a chip's who line before cutting it"
```

---

### Task 2: The foot: toasts sit above it, its buttons keep their places (spec §5 F3 and the busy note; rulings 5, 6)

**Review:** sonnet.

**Files:** Modify `core/ui/.../Shell.kt`, `core/ui/.../Buttons.kt:247`, `app/.../shell/ui/OverlayLayers.kt`, `ui/EventEditorScreen.kt`, `ui/EventDetailSheet.kt:167–173`. Test: `app/src/test/.../OverlayLayersTest.kt`, core/ui's footer button test, `ui/EventEditorScreenTest.kt`, `ui/EventDetailSheetTest.kt`.

**Interfaces:** Produces `class ToastClearance`, `LocalToastClearance`, `@Composable fun KeepsToastsAbove(modifier: Modifier = Modifier, content: @Composable () -> Unit)`, `HhFooterButton(..., busy: Boolean = false)`.

- [ ] **Step 1: The failing tests.**
  - `OverlayLayersTest.aToastSitsAboveAFootThatKeepsToastsAbove`: an overlay whose bottom 100 dp box is in `KeepsToastsAbove`; a toast with an action; the toast's bottom is at least 12 dp above the box's top, and the box's button takes a click.
  - `OverlayLayersTest.whenTheFootGoesTheToastReturnsToItsPlace`.
  - `OverlayLayersTest.aFootReplacedByItsCardKeepsTheToastAbove`: swap the foot for a card in one frame; the toast stays above the card, whichever leaves composition last.
  - `ButtonsTest.aBusyFooterButtonKeepsItsLineAndItsPlace`: with a hint, then `busy`: the button's top and the line's height are unchanged. `anIdleFooterButtonWithNoLineIsAsTallAsBefore` (**pin**).
  - `EventEditorScreenTest.theFootKeepsToastsAbove`, `EventDetailSheetTest.theFootKeepsToastsAbove`: with a clearance provided, its top equals the foot row's window top, and the confirm card's after ✕ / Delete.
  - Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 5–6.
- [ ] **Step 3: Images.** Predicted: none (idle feet don't change). Anything else stops the task.
```bash
git add core/ui app capability/calendar
git commit -m "Keep the toast above a sheet's or the editor's foot, and keep the foot buttons in place while they are busy"
```

---

### Task 3: F2: the editor draws in the frame it is shown (spec §5 F2; ruling 7)

**Review:** sonnet.

**Files:** Modify `ui/EventEditorHost.kt`, `ui/Sheets.kt`, `ui/EventDetailHost.kt`, `ui/CardHosts.kt`; the overlay generation count in `core/plugin`'s `OverlayHost` (a default member) and `app/.../shell/ShellOverlays.kt`. Test: `ui/EventEditorHostTest.kt` (its 25 `showEditor`/`EventEditorHost(` calls re-pointed to `openEditor`), `ui/RecordingOverlay.kt`.

**Interfaces:** Produces `internal suspend fun loadEditor(...): Opened?`; `suspend fun OverlayHost.openEditor(request, today, repo, editor, weekFocus, load = …)`; `OverlayHost.generation: Int` (default 0).

- [ ] **Step 1: The failing tests** (order, not frame counts):
  - `EventEditorHostTest.theEditorIsComposedWhenItIsShown`: `RecordingOverlay` records, at the moment `showFullScreen` is called, whether the load has finished; then in the same `runOnIdle` as `fullScreen` turns true, `editor_title` exists. Fails today (the show comes first).
  - `aSecondTapWhileLoadingOpensOneEditor`: `load` waits on a `CompletableDeferred`; two taps; release; one `showFullScreen`.
  - `aSheetOpenedDuringTheLoadIsNotClosed`: with the load held, show another overlay; release; the editor isn't shown.
  - `aGoneEventClosesSilently` (**pin** on today's behaviour); `aLoadThatThrowsSaysItCouldNotOpen`.
  - `theContextLineFollowsTheSessionAfterItExpires`: "From Calendar · Alex signed in", then the session ends: "From Calendar".
  - Run `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventEditorHostTest*"`. Expected: FAIL.
- [ ] **Step 2: Implement** per ruling 7.
- [ ] **Step 3: Images.** Predicted: none.
```bash
git add core/plugin app capability/calendar
git commit -m "Load the editor before showing it, so it draws in its first frame"
```

---

### Task 4: The detail sheet's Who chips: busy at once, the last person explained (spec §5; rulings 8, 9)

**Review:** sonnet.

**Files:** Modify `core/ui/.../Controls.kt:405`, `ui/EventDetailHost.kt`, `ui/EventDetailSheet.kt`, `CalendarEditor.kt` (`needsAPerson`). Test: core/ui `ControlsTest`, `ui/EventDetailHostTest.kt`, the module's detail image test.

**Interfaces:** Produces `HhPersonChip(..., busy: Boolean = false)`; `fun CalendarEditor.needsAPerson()`.

- [ ] **Step 1: The failing tests.**
  - `ControlsTest.aBusyPersonChipSaysSaving`: state "Saving"; the clock glyph checked with `pixelAt` where the dot was.
  - `EventDetailHostTest.aTappedChipIsBusyAndChosenAtOnce`: with the write held, tap `assign_Alex` on Sam + Mia: next frame Alex is selected and busy; after release, "Alex, Sam +1".
  - `aRefusedOrCancelledPinPutsTheChipsBack`: a child's PIN refused, and the pad cancelled: the chips read as before.
  - `clearingTheLastPersonSaysWhy`: "An event needs at least one person.", nothing written; and after a quick second tap judged on the pending set.
  - Run `./gradlew :core:ui:testDebugUnitTest :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 8–9.
- [ ] **Step 3: Images.** New: `detail_who_busy_dark`. Predicted: that alone.
```bash
git add core/ui capability/calendar
git commit -m "Show a detail chip as busy the moment it is tapped, and say why the last person can't be cleared"
```

---

### Task 5: The editor keeps its pane behind the PIN pad and its keyboard down (spec §5; rulings 10, 11)

**Review:** sonnet.

**Files:** Modify `ui/EditorFlow.kt`, `ui/EventEditorScreen.kt`, `EditorForm.kt`. Test: `ui/EditorFlowTest.kt`, `ui/EventEditorScreenTest.kt`, `ui/EventEditorHostTest.kt`, `app/src/test/.../OverlayLayersTest.kt` if the shell is needed for the real path.

**Interfaces:** Produces `fun EditorFlow.stay()`; `var EditorForm.titleFocusAsked: Boolean`.

- [ ] **Step 1: Confirm the causes on the real path.** Both tests must be red before any change; if one is green, stop and report.
  - `EventEditorHostTest.thePaneBehindThePinPadIsTheOneItWasOn`: nobody signed in, title and Who set, pick a day, tap Add event within 100 ms; advance 300 ms; still `Day`, and still `Day` after the PIN.
  - `theKeyboardIsAskedForOncePerOpening`: a recording `SoftwareKeyboardController` through `LocalSoftwareKeyboardController`; open New (one `show()`); move focus to Who; show and hide the PIN pad over the editor through `ShellLayers`; still one; tap the title: two.
- [ ] **Step 2:** `EditorFlowTest.stayDropsAPendingMoveOn`. Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement** per rulings 10–11 (the keyboard fix is the one Step 1 points to).
- [ ] **Step 4: Images.** Predicted: none.
```bash
git add capability/calendar app
git commit -m "Keep the editor on its pane when the PIN pad opens, and bring the keyboard up only once per opening"
```

---

### Task 6: `EventWhen`: Starts, Ends, All day, clock steps, the defaults, the cap (spec §3.1–§3.4; rulings 12–20; Review Focus 1, 2)

**Review:** opus.

**Files:** Create `EventWhen.kt`. Modify `EditorForm.kt` (drop `UNTIL_TOO_LATE`, now in `EventWhen.kt`). Test: new `EventWhenTest.kt` (today Wed 23 Sep 2026, now 10:20, `Europe/London`).

**Interfaces:** Produces `EventWhen` (ruling 12) with `defaults(startDate, today, now)`, `of(span, zone)`, `endDate`, `length`, `pickStartDate(d, defaults, today, now)`, `pickEndDate(d): EventWhen?`, `enum class ClockField { Hour, Minute }`, `stepStart(field, up)`, `stepEnd(field, up): EventWhen?`, `canStepEnd(field, up)`, `withAllDay(on)`, `withStart(clock)`, `capError: String?`; `sealed interface EventSpan { Timed(start: ZonedDateTime, end: ZonedDateTime); AllDay(first: LocalDate, endExclusive: LocalDate) }`; `span(zone)`.

- [ ] **Step 1: The failing tests** (`EventWhenTest`):
  - Defaults: `theDefaultIsTheNextHourTodayForAnHour` (11:00–12:00); `anotherDayDefaultsToNine`; `lateTodayCapsAtElevenAndEndsAtMidnight` (23:10 → 23:00–00:00, `endDays == 1`); `pickingAStartsDateWithUntouchedTimesReappliesTheDefault`.
  - Moving: `movingTheStartDateKeepsTheLength` (Fri 23 Oct 18:00 – Sun 16:00 → Sat 24 18:00 – Mon 26 16:00); `aStartsStepMovesTheEndAndKeepsTheLength` (10:00–11:00 hour up → 11:00–12:00; 11:00 minute down → 10:45–11:45; 10:45 minute up → 11:00); `anOffQuarterMinuteSnapsToTheNextQuarter` (10:37 up 10:45, down 10:30).
  - Review Focus 1: `aStartsStepWrapsWithinTheDayAndTheEndFollows` (22:00–23:00 hour up → 23:00–00:00 (+1); again → 00:00–01:00, same date; 23:45 minute up → 00:00, 00:00 minute down → 23:45, date unchanged); `anEndsStepThatWouldReachTheStartIsDisabled` (10:00–11:00: hour down disabled; minute down 10:45, 10:30, 10:15, then disabled); `anEndsStepThatWouldLeaveItsDayIsDisabled` (end 23:00 hour up, end 23:45 minute up, a later-day end 00:00 minute down: all disabled); `onALaterDayTheEndClockIsFree`; `movingTheEndBackToTheStartsDayMakesItAnHourAfter` (Fri 10:00 / Sat 09:00 → Fri 11:00; 23:30 / Sat 00:15 → Sat 00:30).
  - Dates and cap: `theEndsDateAllowsTheStartToFiftyNineDaysAfter`; `pickingALaterEndDayKeepsTheEndTime`; `aSixtyOneDaySpanHasTheCapsMessage`.
  - All day: `allDayKeepsBothDatesAndTogglingBackRestoresTheTimes`; `aMidnightEndToggledToAllDayIsOneDay` (ruling 19).
  - Review Focus 2: `aTimedSpanOverTheClockChangeKeepsItsWallTimes` (+01:00 / +00:00); `anAllDaySpanIsWrittenWithTheDayAfter`; `anAmbiguousClockTimeTakesTheEarlierOffset` (25 Oct 01:30 → +01:00).
  - Change: `aChangeOpensOnTheEventsOwnValues`; `anEndAtOrBeforeTheStartOpensAnHourAfter` (ruling 18).
  - Run `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventWhenTest*"`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 12–20.
- [ ] **Step 3:** PASS, then the gate.
```bash
git add capability/calendar
git commit -m "Add EventWhen: a start and an end with All day, stepped like a clock and never ending at or before the start"
```

---

### Task 7: The clash over a span, naming every busy person (spec §3.5; rulings 19, 21–23)

**Review:** opus.

**Files:** Modify `Held.kt` (`check`, `busyHoursAndCheck`, `checkOf` over `from`/`to`; `Clash`; `FreeCheck.Busy`), `EditorWords.kt` (`clashLine`), `CalendarUi.kt` (`spanWords`), `ui/TimePane.kt` (`rememberTimeCheck` passes `day.atTime(start)` and `+ length`). Test: `ClashTest.kt`, `ui/TimePaneTest.kt` (its direct `check` call at `:299` re-pointed), `EditorWordsTest.kt`, `WhenLabelTest.kt`.

**Interfaces:** Consumes `EventSpan`. Produces `Held.check(people, from, to, zone, except, order)`, `data class Clash(val person: PersonId, val event: HeldEvent)`, `FreeCheck.Busy(clashes, more, freeStarts)`, `clashLine(check, single, overDays, names, zone)`, `spanWords(span, zone)`.

- [ ] **Step 1: The tests.** `ClashTest`'s helper (`:51`) now calls `check(people, day.atTime(start), day.atTime(start) + length, …)`. Per case:
  - Re-pointed, unchanged in meaning (**pins**): `allDayAndFamilyEventsMakeNobodyBusy`, `aSharedEventMakesEachOfItsPeopleBusy`, `aCalendarsPersonIsBusyWithItsUntaggedEvents`, `theFreeStartsAreTheNearestBeforeAndAfter`, `noFreeStartBeforeSevenOrPastEleven`, `everyoneChecksNobody`, `aDayPastTheHeldWindowHasNoLine`, `aDayBeforeTheHeldWindowHasNoLine`, `theEventBeingChangedDoesNotClashWithItself`, `aPersonWhoseCalendarNeedsSignInGetsNoLine`.
  - Rewritten for `clashes`: `theClashNamesTheFirstOverlap`, `severalPeopleNameTheBusyOneInHouseholdOrder` (now names both busy people).
  - Rewritten: `theCheckStopsAtMidnight` becomes `aTimedSpanPastMidnightChecksTheNextDay`.
  - Left on `busyHours` until 9b deletes them: `anOverlapIsABusyHour`, `aMultiDayTimedEventMakesTheDaysBetweenBusy`, `theClocksChangeKeepsLocalHours`.
  - New: `everyBusyPersonIsNamedUpToTwo` ("Sam has Dentist, 12:30–13:00; Mia has Piano, 12:45–13:30"); `aThirdBusyPersonIsCounted` ("… and 1 more"); `aSpanOverSeveralDaysNamesTheFirstClashWithItsDay` ("Overlaps Dentist, Sat 24 Oct 12:30–13:00", no free starts); `aSpanReachingPastTheHeldWindowHasNoLine`; `anElevenToMidnightCheckIsOneDay` (ruling 19).
  - `EditorWordsTest.spanWordsReadsEachForm` (ruling 23's four); `WhenLabelTest.theDetailReadsATimedSpanInSpanWords` (**pin**) and `theDetailKeepsItsOwnAllDayForm` ("Mon 19 Oct – Fri 23 Oct · All day").
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 19, 21–23.
- [ ] **Step 3: Images.** Predicted: none (one-person words unchanged). Gate.
```bash
git add capability/calendar
git commit -m "Check clashes over the whole span, name up to two busy people, and word spans the same everywhere"
```

---

### Task 8: Shared pickers: `TimeSteppers` and `DateGrid` (spec §3.2; ruling 14)

**Review:** sonnet.

**Files:** Create `ui/TimeSteppers.kt` (from `ui/Pickers.kt`'s private `Stepper` / `StepButton`, with `enabled` per button), `ui/DateGrid.kt` (from `ui/DayPane.kt`'s `MonthHeader`, `WeekdayLetters`, `DayCell`, `monthCells`). Modify `ui/Pickers.kt` (`TimePickerCard` uses `TimeSteppers` with its own `hourStep`/`minuteStep`), `ui/DayPane.kt` (uses `DateGrid`). Test: `ui/PickersTest.kt` (unchanged), new `ui/TimeSteppersTest.kt`, new `ui/DateGridTest.kt`, `ui/DayPaneTest.kt`.

**Interfaces:** Produces:
- `@Composable fun TimeSteppers(time: LocalTime, tagPrefix: String = "", onStep: (ClockField, Boolean) -> Unit, enabled: (ClockField, Boolean) -> Boolean = { _, _ -> true }, modifier: Modifier = Modifier)`. Tags `{tagPrefix}hour_up`, `{tagPrefix}hour_down`, `{tagPrefix}hour_value`, `{tagPrefix}minute_up`, `{tagPrefix}minute_down`, `{tagPrefix}minute_value`, so an empty prefix keeps today's tags; 9a uses `start_` and `end_`. TalkBack as now ("Hour up", "Hour 7"). The caller does the arithmetic.
- `@Composable fun DateGrid(month, onMonth, today, chosen, band, allowed: (LocalDate) -> Boolean, dots, spoken, onPick)`; tags `day_{date}` as now; a disallowed date is dimmed (`HhAlpha.PAST_DAY`), not clickable, and says "not available".

- [ ] **Step 1: The tests.**
  - `TimeSteppersTest.aDisabledStepButtonIsNotClickableAndSaysSo` (new, fails).
  - `DateGridTest.aDateThatIsNotAllowedIsDimmedAndCannotBeTapped` (new, fails). Moved here from `DayPaneTest`, re-pointed to `DateGrid`, not rewritten: `theMonthStartsOnMonday`, `pastAndOutsideDaysFade`, `todayIsRingedAndTheChosenDayFilled`, `upToThreeDotsFromHeldEvents`, `theMonthPagesBothWays`, `atFontScale13TheLastWeekCanStillBeReached`.
  - `PickersTest` stays green unchanged (**pins** for the move).
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL (new names).
- [ ] **Step 2: Implement.** Move, don't copy; reuse `CalendarDimens.stepper*`.
- [ ] **Step 3: Images.** Predicted: none.
```bash
git add capability/calendar
git commit -m "Pull the time steppers and the month grid out into shared pickers"
```

---

### Task 9a: The Starts and Ends panes and rows, built beside the old ones (spec §3.1, §3.2, §3.5; rulings 14, 15, 21, 24)

**Review:** sonnet.

**Files:** Create `ui/StartsEndsPanes.kt` (`StartDatePane`, `StartTimePane`, `EndDatePane`, `EndTimePane`, each taking `eventWhen: EventWhen` and `onChange: (EventWhen) -> Unit` with `held`, `people`, `order`, `zone`, `today`; the clash line under the steppers, free-start chips on one day). Modify `ui/EditorRows.kt` (`EditorWhenRow`). Nothing calls them yet. Test: new `ui/StartsEndsPanesTest.kt`, `ui/EditorRowsTest.kt`.

**Interfaces:** Consumes Tasks 6–8. Produces the four panes; `EditorWhenRow(testTag, icon, label, date: String?, time: String?, spokenDate: String?, active, set, typing, onDate, onTime, tag)`; pane tags `starts_date_pane`, `starts_time_pane`, `ends_date_pane`, `ends_time_pane`.

- [ ] **Step 1: The failing tests** (each ends with `assertClickablesHaveRoleAndLabel()`):
  - `StartsEndsPanesTest`: `eachPaneAsksItsQuestion` (spoken as pane titles); `theEndsGridDimsDaysBeforeTheStartAndPastFiftyNine`; `anEndsHourDownThatWouldReachTheStartIsDisabled`; `aFreeStartMovesTheStartAndTheEndFollows`; `overSeveralDaysThereAreNoFreeStarts`; `allDayHasNoClashLine`. Re-homed from `TimePaneTest` and rewritten for the steppers pane: `theResultSaysFree`, `aClashWarnsAndNamesTheFreeStarts`, `noLineBeyondTheHeldWindow` (was `pastTheHeldWindowThereIsNoResultLineAndNoTag`), `everyoneHasNoCheck`, `anUnhealthyCalendarHasNoCheck` (was `aPersonWhoseCalendarNeedsSignInHasNoCheck`), `theEventBeingChangedDoesNotClashWithItself`, `theStepsPaneFitsAtFontScales13And15` (was `theHoursFitAtLargerFontScales`). Re-homed from `DayPaneTest`: `theStartsDatePaneFitsWithoutScrollingForASixWeekMonth`.
  - `EditorRowsTest`: `eachTargetIsItsOwnButton` ("Starts, Saturday 26 September", "Starts at 10:00"); `endsWaitsForAStartDay`; `atFontScale15TheStartsRowKeepsDateAndTimeOnOneLine` (with "Wed 30 Sep").
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Images.** Predicted: none (recorded in 9b, on the screen).
```bash
git add capability/calendar
git commit -m "Add the Starts and Ends panes and rows beside the old Day and Time panes"
```

---

### Task 9b: The swap: the form, flow and screen on `EventWhen`; Day and Time panes removed (spec §3.1–§3.6, §3.8; rulings 12–18, 24–28; Review Focus 5)

**Review:** opus.

**Files:**
- Modify:
  - `EditorForm.kt`: `eventWhen`, `timesTouched`, `chooseAllDay`, `pickStartDate`, `pickEndDate`, `stepStart`, `stepEnd`, `useFreeStart`; `draft()` from `span`; `whenTouched` / `keepsOwnSpan` from the opened value; locks by ruling 18; `missing`; `stepperLabel` (`:329`) and `repeatsValue` (`:337`) read `eventWhen.startDate`.
  - `ui/EditorFlow.kt`, `ui/EditorPane.kt` (ruling 26).
  - `ui/EditorRows.kt`: `LOCKED_SPAN` reads "dates locked, as it runs over more than 60 days".
  - `ui/EventEditorScreen.kt`: the rows of ruling 24 with the 9a panes; All day hidden with the keyboard up; the picker layer gone.
  - `ui/EventEditorHost.kt`: `EditorPicker` and `onPicker` go.
  - `EditorWords.kt` (`checkSentence`, `firstDates`, `datesOf`, `timesOf`), `ui/CheckPane.kt` (`form.day` ×2), `ui/RepeatsPane.kt` (×2): read `eventWhen.startDate` / `span`; Task 10 changes the words.
- Delete: ruling 28's list, with `ui/TimePane.kt`, `ui/DayPane.kt`, `ui/TimePaneTest.kt`, `ui/DayPaneTest.kt` (cases not re-homed in Tasks 8 and 9a go: quick picks, the switch, the range, the band, the cap tap, the hour groups, minutes and length, Other time and Until, the end-before-start line, busy hours, multi-day lock, Skip on Time); `PickersTest`'s card and step cases; `ClashTest`'s three `busyHours` cases; `WriteCopyTest.kt:86`'s `lengthLabel` case; the `EditorFormTest` range, hour, minute, length and Until cases; images `add_event_day_*`, `add_event_days_*`, `add_event_time_*`.
- Test: `EditorFormTest.kt`, `ui/EditorFlowTest.kt`, `ui/EventEditorScreenTest.kt`, `ui/RepeatsPaneTest.kt` (its `EditorStep.Day`/`Time` re-pointed), `ui/AddEventScreenshotTest.kt`, `ui/EditForms.kt` (`editorLockedVisit` becomes an all-day 70-day span; new `editorTimedSpan` Mon 28 Sep 18:00 – Wed 30 Sep 16:00), `ui/FontScaleTest.kt`, `ui/EventEditorHostTest.kt` (re-pointed tags).

**Interfaces:** Consumes Tasks 6–9a. Produces `EditorStep` (ruling 26); `EditorForm.eventWhen`; row tags `editor_all_day`, `editor_starts_date`, `editor_starts_time`, `editor_ends_date`, `editor_ends_time`.

- [ ] **Step 1: The failing tests.**
  - `EditorFormTest`: `aNewEventStartsTimedWithTheDefaults` (not edited); `pickingAStartsDateIsAnEditButTheDefaultsAreNot`; `aTimedSpanMadeElsewhereOpensUnlocked`; Review Focus 5's four; `theDraftOfATimedSpanHasItsOwnEnd`; `onlyTheStartsDateIsMissing`.
  - `EditorFlowTest`: `theStepsFollowAllDay`; `aStartsDatePickMovesOnToTheStartTime`; `withAllDayAStartsDatePickMovesOnToRepeats` (Check for a child); `anEndsDatePickMovesOnToTheEndTime`; `theSteppersNeverMoveOn`; `nextNamesTheNextStep` (all seven names of ruling 26, "Next: Starts" from Who included); `skipOnEndsKeepsTheEndAndGoesToRepeats`.
  - `EventEditorScreenTest`: `theRowsReadTitleWhoAllDayStartsEndsRepeats`; `allDayHidesBothTimes`; `withTheKeyboardUpTheAllDayRowHidesAndNothingIsCut` (font scales 1.0, 1.3 and 1.5, 320 dp keyboard); `aLockedSpanShowsLockedRows`.
  - `CopyScanTest` passes with the old copy gone.
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement.** Then `grep -rn "TimePane\|DayPane\|TimePickerCard\|PickerLayer\|EditorPicker\|TimeChoice\|draftSpan\|EDITOR_LENGTHS\|lengthChip\|lengthLabel\|hourStep\|minuteStep\|LASTS_MORE_THAN_A_DAY\|busyHours" --include=*.kt .` outside `build/` finds nothing.
- [ ] **Step 3: Images.** Deleted: `add_event_day_*`, `add_event_days_*`, `add_event_time_*`. New (dark): `add_event_starts_date_dark`, `add_event_starts_time_dark`, `add_event_ends_date_dark`, `add_event_ends_time_dark`, `add_event_all_day_dark`, `add_event_span_dark`. Re-recorded: the other `add_event_` and `change_event_`. Predicted prefixes: `add_event_`, `change_event_`.
```bash
git add -A capability/calendar
git commit -m "Replace the Day and Time rows with Starts, Ends and All day, picked with the month grid and the time steppers"
```

---

### Task 10: Spans everywhere else: repeats, Check, the toast, the detail, the foot hint (spec §3.3 repeats, §3.7; rulings 29–32)

**Review:** sonnet.

**Files:** Modify `Recurrence.kt`, `EditorForm.kt` (`repeatTooLong`), `ui/RepeatsPane.kt`, `ui/EditorRows.kt`, `EditorWords.kt`, `ui/CheckPane.kt`, `ui/WeekLane.kt:80`, `CalendarEditor.kt:143`, `ui/EventEditorScreen.kt`. Test: `RecurrenceTest.kt`, `EditorFormTest.kt`, `ui/RepeatsPaneTest.kt`, `EditorWordsTest.kt`, `ui/CheckPaneTest.kt`, `CalendarEditorTest.kt`, `ui/EventEditorScreenTest.kt`, `ui/AddEventScreenshotTest.kt`.

**Interfaces:** Produces `fun RepeatChoice.fitsLength(length: Duration): Boolean`; `EditorForm.repeatTooLong`.

- [ ] **Step 1: The failing tests.**
  - `RecurrenceTest.eachRepeatFitsOnlySpansThatEndBeforeTheNextStarts`: Daily 24 h yes / 25 h no; Weekly 7 / 8 days; Fortnightly 14 / 15; MonthlyOnWeekday 28 / 29; Yearly 60 yes.
  - `RepeatsPaneTest.aRepeatTheSpanRulesOutIsLockedWithItsLine`.
  - `EditorFormTest.changingTheSpanPastTheRepeatFallsBackToDoesntRepeat` (and opening Repeats clears the tag).
  - `EditorWordsTest.theCheckSentenceUsesTheSpanWords`: "Dinner for Sam, Sat 26 Sep, 10:00–10:45."; "Camp for Mia, Fri 23 Oct 18:00 – Sun 25 Oct 16:00."; a weekly series "every Saturday, 10:00–10:45".
  - `CheckPaneTest.aSpanHasOneDateChipPerOccurrence`: four chips "Fri 23 – Sun 25 Oct", …; the preview is one lane bar.
  - `CalendarEditorTest.theAddedToastNamesTheSpansDays`: "Added Camp, Fri 23 – Sun 25 Oct."
  - `EventEditorScreenTest.theFootHintFollowsItsOrder` (ruling 32): New complete, Repeats unanswered: "Repeats are optional"; after Doesn't repeat on Check: "Everything’s set"; a clash: the clash hint; Change complete: none.
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 29–32.
- [ ] **Step 3: Images.** New: `add_event_repeats_too_long_dark`, `add_event_check_span_dark`. Predicted prefixes: `add_event_`, `change_event_`.
```bash
git add capability/calendar
git commit -m "Offer only the repeats a span allows, and read spans in Check, the added toast and the foot hint"
```

---

### Task 11: `remembered_titles`: the table, its migration, filled and forgotten by sync (spec §4.1, §8 call 10; rulings 33–39, 41; Review Focus 3)

**Review:** opus.

**Files:** Create `RememberedTitles.kt` (`titleKey`, `better`, the fold). Modify `db/CalendarDatabase.kt` (entity, DAO, version 8), `db/Migrations.kt`, `CalendarStore.kt` (`applySync(..., today)`, `applyDeleted`, `applySeriesDeleted`, `removeSourceRows`, `removeConnection`, `seedRememberedTitles`, `rememberedTitles`), `CalendarSync.kt`. Add `schemas/.../8.json`. Test: `CalendarMigrationTest.kt`, new `RememberedTitlesTest.kt`, new `CalendarStoreRememberedTest.kt`, `CalendarSyncTest.kt`, and the `applySync` callers.

**Interfaces:** Produces `RememberedTitleEntity` (ruling 33); `fun titleKey(title: String): String`; `fun better(a: LocalDate, b: LocalDate, today: LocalDate): LocalDate`; `suspend fun CalendarStore.rememberedTitles(): List<RememberedTitleEntity>` (visible only, ruling 39); `suspend fun CalendarStore.seedRememberedTitles(connectionId: String, today: LocalDate)`.

- [ ] **Step 1: The failing tests.**
  - `CalendarMigrationTest.migrationFromV7AddsTheRememberedTitlesTable`: `runMigrationsAndValidate(8, true, MIGRATION_7_8)` (imported as the file imports `MIGRATION_6_7`); the old rows read back; an inserted row reads back, and a second with the same `(connectionId, titleKey)` is rejected.
  - `RememberedTitlesTest`: `theKeyTrimsCollapsesLowersAndDropsAccents` ("  Café  Lunch " → "cafe lunch"); `theRememberedDateIsTheLatestPastElseTheEarliestFuture`; `aSeriesCountsOnce`; `aRuleTheEditorDoesNotOfferIsNotKept`; `aKeptRuleHasNoCountUntilOrDtstart`; `anAllDayEventKeepsItsDays`; `aTimedEventKeepsItsStartAndLength`; `anUntaggedEventKeepsItsCalendarsPerson`; `aBlankTitleIsNotRemembered`.
  - `CalendarStoreRememberedTest`:
    - filling: `aSyncRemembersWhatItRead`; `theSameEventReplacesItsOwnFields`; `aChangedFutureInstanceKeepsTheSeriesLastDate`; `aLaterPastDateWins`; `aHiddenCalendarIsNotRemembered`; `anUnreadableRuleIsRememberedWithoutItAndTheSyncLands`;
    - forgetting: `aDeletedEventsTitleStaysWhileAnotherEventHasIt`; `aTitleWithNoEventLeftIsForgotten`; `aSeriesDeletedOnThePhoneIsForgotten`; `oneCancelledOccurrenceKeepsTheSeriesTitle`; `undoingASeriesForgetsItsTitle`; `aFullReplaceForgetsWhatItDrops`; `aRenamedEventForgetsItsOldTitle`; `aCalendarNoLongerListedForgetsItsTitles`; `removingAConnectionForgetsItsTitles`;
    - age: `aTitleOlderThanTwelveMonthsIsForgottenOnTheNextSync` (two passes, still gone);
    - seeding: `theFirstPassSeedsFromStoredEvents`; `theSeedSkipsHiddenCalendars`; `seedingDoesNotResurrectATitleOlderThanTwelveMonths`; `aSeedAfterTheConnectionWentWritesNothing`.
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 33–39, 41.
- [ ] **Step 3:** The gate. `8.json` is committed and the migration test reads it.
```bash
git add capability/calendar
git commit -m "Remember event titles from visible calendars in a new table, forget them with their events, and keep them twelve months"
```

---

### Task 12: `TitleSuggestions`: matching and its words (spec §4.2–§4.4; rulings 42, 43, 45)

**Review:** sonnet.

**Files:** Create `TitleSuggestions.kt`, `SuggestionWords.kt`. Modify `CalendarRepository.kt`. Test: new `TitleSuggestionsTest.kt`, new `SuggestionWordsTest.kt`.

**Interfaces:** Consumes `CalendarStore.rememberedTitles()`, `titleKey`. Produces:
- `data class Suggestion(title, forPerson: String?, allDay, start: LocalTime?, length: Duration?, days: Int?, rule: String?, seriesStart: LocalDate?, rememberedDate: LocalDate)`;
- `class TitleSuggestions(store: CalendarStore, zone: HouseholdZone, clock: WallClock) { suspend fun matching(typed: String, max: Int = 3): List<Suggestion> }`; `CalendarRepository.suggestions`;
- `patternLine(s, people, today)`, `fillLine(s, people, childOnly)`, `spokenSuggestion(s, people, childOnly)`, `enum class CopiedRow { Who, Starts, Repeats }`, `bannerLine(s, copied, today)`, `quickPicksFor(s, today): List<LocalDate>`.

- [ ] **Step 1: The failing tests.**
  - `TitleSuggestionsTest`: `nothingUnderTwoCharacters`; `aWordInsideMatches` ("less" finds "Guitar lesson"); `caseAndAccentsAreIgnored`; `titleStartsComeBeforeWordMatchesThenLatestFirst`; `atMostThree`; `theSameTitleOnTwoConnectionsShowsOnce`; `aHiddenCalendarsTitlesAreNotSuggested`.
  - `SuggestionWordsTest`: the muted lines, fill lines, spoken form and banners of ruling 43 (spec §4.3 values); `aChildsFillLineLeavesOutWhoAndTheRepeat`.
  - `SuggestionWordsTest.quickPicks` (from Wed 23 Sep 2026): Weekly Saturday → 26 Sep, 3 Oct, 10 Oct; 4th Saturday → 26 Sep, 24 Oct, 28 Nov; last Thursday → 24 Sep, 29 Oct, 26 Nov; Fortnightly from Sat 19 Sep → 3 Oct, 17 Oct, 31 Oct; the same with no `seriesStart` and `rememberedDate` Sat 19 Sep → the same; Yearly 29 Feb → 29 Feb 2028; Daily, Never → none.
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 42, 43, 45.
- [ ] **Step 3:** The gate.
```bash
git add capability/calendar
git commit -m "Match remembered titles as the title is typed, and word what each suggestion fills in"
```

---

### Task 13: From before: the list, Use this, Copied, the banner and quick picks (spec §4.3, §4.4; rulings 44–47; Review Focus 4)

**Review:** opus.

**Files:** Create `ui/FromBeforePane.kt`. Modify `EditorForm.kt` (`use`, `copied`), `ui/EventEditorScreen.kt` (ruling 46), `ui/EventEditorHost.kt` (`suggestions` on New only), `ui/EditorRows.kt` (`COPIED`), `ui/StartsEndsPanes.kt` (banner, quick picks), `tools/fonts/icons.txt`, the font subset, `Icons.CONTENT_COPY`. Test: new `EditorFormSuggestionTest.kt`, new `ui/FromBeforePaneTest.kt`, `ui/EventEditorScreenTest.kt`, `ui/StartsEndsPanesTest.kt`, `ui/AddEventScreenshotTest.kt`, `IconFontTest`.

**Interfaces:** Consumes Tasks 9b, 12. Produces `fun EditorForm.use(s: Suggestion)`, `EditorForm.copied`, `EditorForm.usedSuggestion`.

- [ ] **Step 1: The failing tests** (UI tests end with `assertClickablesHaveRoleAndLabel()`):
  - `EditorFormSuggestionTest`: `useFillsEverythingButTheDateAndTheEnd`; Review Focus 4's five (`pickingTheDateAfterUseThisKeepsTheCopiedTime` covers a grid pick and a quick pick); `aCopiedTagGoesWhenItsRowChanges`; `typingAgainKeepsWhatWasFilled`; `useDoesNothingWhileSaving`.
  - `FromBeforePaneTest`: `theListShowsWhileTheTitleIsFocused`; `withNoMatchesThePaneAsksTheActiveQuestion`; `changeNeverSuggests`; `eachRowIsOneItemWithUseThis`; `threeRowsAtFontScale13ScrollAndNoneIsCutMidRow` (320 dp keyboard); `aSlowerEarlierAnswerNeverReplacesALaterOne` (a fake `matching` that answers "sw" after "swim").
  - `EventEditorScreenTest.useThisClosesTheKeyboardAndOpensTheStartsDateWithTheBanner`: focus cleared, `StartDate` active, the banner, quick picks for a weekly suggestion; a quick pick moves on to Start time after 250 ms.
  - Run `./gradlew :capability:calendar:testDebugUnitTest :core:ui:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Add `content_copy`** to the font (Global Constraints' commands); `IconFontTest` passes.
- [ ] **Step 3: Implement** per rulings 44–47.
- [ ] **Step 4: Images.** New: `add_event_from_before_dark` (6a), `add_event_from_before_keyboard_dark`, `add_event_copied_dark` (6h). Predicted: those three, and `icon_` images if the icon test draws the font.
```bash
git add capability/calendar core/ui tools/fonts
git commit -m "Suggest titles from before while typing, fill in the rest with Use this, and show what was copied"
```

---

### Task 14: `simplify` over the whole branch

**Review:** the controller.

- [ ] **Step 1:** Run the `simplify` skill over this plan's commits (from 57a34ebb to HEAD), once for the branch.
- [ ] **Step 2:** The gate. Commit each change set as its own plain sentence. Record partials in the ledger.

---

### Task 15: The emulator walk-through on `culvery-5` (resumed: 9c-1, 9c-1b and this plan together)

**Review:** the controller, with the user at the emulator.

**Rules** (as 9c-1b Task 17):
- Only the `culvery-5` AVD (`emulator-5554`), started with `-no-snapshot-save -camera-front emulated` (check `adb -s "$S" emu avd name`).
- A debug build, so `install -r` the debug APK. Never uninstall; never a Gradle install task while a device is connected.
- The controller enters the debug sample PINs (`DebugSeed.kt`).
- Offline is `adb -s "$S" shell svc wifi disable` and `svc data disable`, put back after.
- **Faults are collected, not fixed on the spot.** At the end the user sees the list and is asked before any fix.
- Put back every setting changed.

- [ ] **Step 0: Before starting**, check the follow-ups have "Choosing the destination calendar per event" and "A year-long fetch of past events (the remembered list builds up instead)"; add any missing and commit "Note the cuts from Plan 9c-1c in the follow-ups". Then tell the user first, **"Not in this build, on purpose":** the destination calendar per event; a year-long fetch; Home's speed (measured on the tablet).
- [ ] **Step 1:** `./gradlew :app:assembleDebug`, then `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2: Re-check what changed** (one line each):
  1. **The week:** This week, ‹ ›, Add event; the label and dates change together; a narrow shared chip reads "Sam +1".
  2. **Toasts:** above the editor's foot and the detail sheet's Delete; foot buttons don't move while busy.
  3. **F2:** no grey frame from any entry point.
  4. **Detail chips:** busy at once; a cancelled PIN puts them back; the last person says why.
  5. **PIN pad:** pick a date then Add event at once: the same pane after the PIN.
  6. **Keyboard:** after leaving the title, it stays down until the title is tapped.
  7. **Starts and Ends:** defaults; steps carry like a clock and never change a date; an Ends step that would reach the start or leave the day is greyed; an end on a later day by the Ends date; the Ends grid greys days outside the 60; All day hides the times and keeps the dates; a Fri 18:00 – Sun 16:00 event saves and shows as one lane bar.
  8. **Clash:** two busy people named; over several days the first clash only; none on All day.
  9. **Repeats with a span:** "Too long to repeat that often."; "Too long" on Repeats after lengthening.
  10. **Check and the toast:** one chip per occurrence; span words; the foot hint only while it matters.
  11. **Suggestions:** "sw" lists Swimming under "From before"; Use this fills who, time and repeat with Copied tags, the banner and quick picks, and a date pick keeps the time; a child's Use this keeps them; a hidden calendar's titles never show; removing and re-adding the sample shows only its titles.
- [ ] **Step 3: Never reached before** (one line each): idle close at 2 minutes untouched and at 10 edited (warning at 9:40); "4th Saturday" and "last Thursday" land right in the fake; a counted series reads "Every Saturday · 12 times · last on Sat 12 Dec"; Undo offline on a just-queued series leaves nothing to send; Undo by another person signed in is refused; "Day 2 of 3" on Home (set the AVD's date and put it back); a timed span over midnight; the person and Favourites sheets; every other new thing here.
- [ ] **Step 4: The faults.** Show the list and ask which to fix. Each agreed fix: a failing test, the fix, the gate, a commit naming it, `install -r`, a re-check.
- [ ] **Step 5:** Put back every setting, then `adb -s "$S" emu kill`.
- [ ] **Step 6: Record** under "Plans 9c-1, 9c-1b and 9c-1c: emulator walk-through".
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record the emulator walk-through of Plans 9c-1, 9c-1b and 9c-1c"
```

---

### Task 16: On the tablet

**Review:** the controller with the user. **Needs the SM-T510 (`R52N60KXWJF`) on the cable.** 4e's rules: save and restore every setting changed, a signed release installed with `adb -s R52N60KXWJF install -r`, **never uninstall**, and the names check before any commit.

- [ ] **Step 1: Install the release**, signed per `docs/setup/release.md`. Keep `mapping.txt` in `culvery-notes/mappings/`.
- [ ] **Step 2: To check when the tablet is next plugged in** (from the follow-ups): the doorbell logcat around 2026-10-21 13:10 (missed), 16:20 (fine) and 18:03 (unexplained); Home's speed, launch to Home filled, three runs.
- [ ] **Step 3: At the wall** (the user): the editor reads at arm's length, and Starts and Ends with the steppers are obvious; a timed weekend event and an all-day span show right in Google Calendar on a phone; a shared event keeps the calendar's colour and its people after a sync; "4th Saturday" and "last Thursday" series land right in Google; Undo of a series removes it from Google; a known title is suggested after a sync and Use this fills it; a child's locked tiles read as locked, not broken.
- [ ] **Step 4: Record** "Plans 9c-1, 9c-1b and 9c-1c: on the tablet", with the doorbell and speed results. No names.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plans 9c-1, 9c-1b and 9c-1c on the tablet"
```

---

### Task 17: Final review, merge, install

- [ ] **Step 1: The review.** Opus reviews the whole branch (9c-1, 9c-1b and this plan) against Plan 9's spec §2.1, §4.8 and §6.4, the 9c spec, this spec, and the three plans. **One fix wave** follows, each fix with its failing test first and the gate.
- [ ] **Step 2: The follow-ups.** Add under "From Plans 9c-1b and 9c-1c":
  - choosing the destination calendar per event;
  - skipping one date of a series from the event's details;
  - an undone create whose drain delete is lost on restart (`UndoneKeys` is in memory); if it later lands, its title is remembered too;
  - Home is slow to fill, with Task 16's numbers;
  - the end-of-v1 design-review notes (9c-1b's and this plan's).
  Remove the "From before" suggestions entry: built here. Commit "Record what Plans 9c-1b and 9c-1c left for later".
- [ ] **Step 3: Merge**, on the user's say: `git merge --ff-only plan-9c-forms` on `main`, then the gate on main. Never push. 9c-2 then branches from it.
- [ ] **Step 4: Install** the release from the merged `main` with `adb -s R52N60KXWJF install -r`.

---

## Spec coverage (this spec → tasks)

| Spec | Task |
|---|---|
| §2.1 Starts, Ends, All day replace Day and Time | 6, 9a, 9b |
| §2.2 the existing steppers, one time control | 8, 9a, 9b |
| §2.3 suggestions on this branch | 11–13 |
| §2.4 week header order | 1 |
| §3.1 rows, two targets, All day, Ends follows | 9a, 9b |
| §3.2 panes, live steppers, clock steps, quick picks only after a suggestion | 6, 8, 9a, 13 |
| §3.3 moving together, end after start, later day, back to the start's day, all-day inclusive, locks, cap | 6, 9b |
| §3.3 repeats and spans | 10 |
| §3.4 defaults | 6, 9b |
| §3.5 clash over the span, every busy person | 7, 9a |
| §3.6 order, moving on, Skip, missing | 9b |
| §3.7 Check, chips per occurrence, toast, detail, foot hint | 7, 10 |
| §3.8 what goes | 9b |
| §4.1 table, fill, seed, forget, 12 months, removal, local, reader | 11, 12 |
| §4.2 matching | 12 |
| §4.3 the list | 12, 13 |
| §4.4 Use this | 12, 13 |
| §5 F2; F3; header; notes | 3; 2; 1; 1, 2, 4, 5 |
| §6 not in scope; §7 Home's speed | 15, 17; 16, 17 |
| §8 calls 1–10 | rulings 13, 14, 21–22, 27–29, 36–40, 45 |
