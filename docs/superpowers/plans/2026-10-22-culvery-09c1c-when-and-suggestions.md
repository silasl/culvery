# Culvery — Plan 9c-1c: When as Starts and Ends, title suggestions, and the walk-through fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–13 are subagent tasks; Task 14 is `simplify` over the branch; Task 15 is the controller on the `culvery-5` AVD with the user; Tasks 16–17 need the user and the son's SM-T510.
>
> **This plan finishes 9c-1 and 9c-1b.** 9c-1b's Tasks 1–16 and its whole-branch simplify are done (ledger `.superpowers/sdd/2026-10-21-culvery-09c1b-calendar-update/progress.md`). Its Task 17 (the emulator walk-through) stopped part-way, and **9c-1b's Tasks 17–19 are replaced by Tasks 15–17 here**, which walk through, review and merge 9c-1, 9c-1b and this plan together.

**Goal:** When is a Starts row and an Ends row with an All day switch, each picked with the calendar's existing steppers and month grid; a new event's title suggests remembered events and fills in the rest; the faults and notes from the paused walk-through are fixed.

**Architecture:**
- **The walk-through fixes first** (Tasks 1–5). They touch the week, the shell's toast, the editor's host and the detail sheet, not the When model, so each lands on today's editor.
- **When is a new value type, `EventWhen`** (Task 6), beside `EditorForm`, with the clash check widened to a span (Task 7) and the two shared pickers pulled out of today's panes (Task 8). **Task 9 swaps the editor over** in one commit and deletes the Day and Time panes, so every commit leaves a working Add event.
- **Suggestions are a Room table filled by sync** (Task 11), read by `TitleSuggestions` (Task 12) and shown in the editor (Task 13). Nothing is fetched that sync doesn't already read.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00, Material 3), Hilt 2.57.1, Room (as in the module), Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-22-culvery-9c-when-and-suggestions-design.md` ("this spec": rulings §2, When §3, suggestions §4, fixes §5, calls §8). It adds to `docs/superpowers/specs/2026-10-21-culvery-9c-calendar-update-design.md` ("the 9c spec") and changes it where it says so. The drawings for suggestions are the handoff `docs/design/2026-10-calendar-update/handoff.md` 6a and 6h (dp = px ÷ 1.5). Where this spec, the 9c spec, the handoff or the code disagree, this spec wins. 9c-1b's rulings (`docs/superpowers/plans/2026-10-21-culvery-09c1b-calendar-update.md`) still hold except where a ruling below changes them.

**Previous plans (format, shared parts, rulings):** `2026-10-21-culvery-09c1b-calendar-update.md` (9c-1b), `2026-10-20-culvery-09c-redesign-forms.md` (9c-1).

**Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md`.

**Builds on:** branch `plan-9c-forms` (HEAD 57a34ebb) in the one checkout `C:\Users\si\code\siland\culvery`. There are no worktrees. Work only there. Never push.

| # | Task | Review |
|---|---|---|
| 1 | The week: header order, label and dates in one frame, chips shorten the who line | sonnet |
| 2 | The foot: toasts sit above it, its buttons keep their places | sonnet |
| 3 | F2: the editor draws in the frame it is shown | sonnet |
| 4 | The detail sheet's Who chips: busy at once, the last person explained | sonnet |
| 5 | The editor keeps its pane behind the PIN pad and its keyboard down | sonnet |
| 6 | `EventWhen`: Starts, Ends, All day, the defaults, the cap | **opus** (the end never at or before the start; no carry; DST) |
| 7 | The clash over a span, naming every busy person | **opus** (the held window over several days; who counts) |
| 8 | Shared pickers: `TimeSteppers` and `DateGrid` | sonnet |
| 9 | The swap: Starts, Ends and All day rows and panes; Day and Time panes removed | **opus** (the flow; locks; nothing of the old panes left) |
| 10 | Spans everywhere else: repeats, Check, the toast, the detail, the foot hint | sonnet |
| 11 | `remembered_titles`: the table, its migration, filled and forgotten by sync | **opus** (the migration; forgetting; the 12 months) |
| 12 | `TitleSuggestions`: matching and its words | sonnet |
| 13 | From before: the list, Use this, Copied, the banner and quick picks | **opus** (the child rules; removed people; what is never copied) |
| 14 | `simplify` over the whole branch | controller |
| 15 | The emulator walk-through on `culvery-5` (resumed) | controller + user |
| 16 | On the tablet | controller + user, **needs the SM-T510** |
| 17 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:**
1. **The fixes first** (1–5): small, separate, and the walk-through has to re-check them anyway.
2. **The When model, the span clash and the shared pickers** (6–8) are new code beside the old, so nothing visible changes yet.
3. **The swap** (9) in one commit, then everything else that reads a span (10).
4. **Suggestions** (11–13): storage, reading, then the screen, which needs the Starts date pane from Task 9.

## Rulings against the code

Each is the plan's call unless it cites the spec or the user.

1. **Units** as 9c-1b ruling 1. New values in **Units** below.
2. **Week header order** (spec §2 ruling 4): in `ui/WeekHeader.kt` the actions row is "This week" (only when `weeksAhead > 0`, as now), then the ‹ › `NavPill`, then Add event. The arrows sit beside Add event whether or not This week shows.
3. **The week's label and dates change together** (spec §5). Today `WeekViewHost` (`ui/CardHosts.kt`) builds `WeekViewState(w, today, s, nowMillis, shown.weeks)`, so "Next week" comes from `shown.weeks` one frame before `produceState` loads that week. The subtitle's which-week part is worked out from the **loaded** week: `weeksTo(today, w.days.first())`. `shown.weeks` still drives the fetch.
4. **The week chip's who line shortens before it is cut** (spec §5). `fun whoLineForms(people: List<Person>): List<String>` in `Who.kt` gives the forms from longest: `whoLine(people)` ("Sam + Mia", "Alex, Sam +1"), then "{first} +{n − 1}" ("Sam +1", "Alex +2"); one person has one form. The chip (`ui/WeekView.kt:421`) measures "{start} · {form}" with a `TextMeasurer` and draws the first that fits on one line. Only the last form may ellipsise. TalkBack keeps `spokenWho` (all names).
5. **Toasts sit above a foot** (spec §5 F3; the plan's pick: shift the toast up, never hide the foot). core/ui gains `class ToastClearance { var footTop: Float? }` (Compose state, window px), `val LocalToastClearance = staticCompositionLocalOf<ToastClearance?> { null }` and `fun Modifier.keepsToastsAbove(): Modifier`, a `Modifier.Node` that reports the node's window top in `onGloballyPositioned` and clears it in `onDetach`. `ShellLayers` (app `shell/ui/OverlayLayers.kt`) provides one. `ToastSlot` puts the toast's bottom at `max(ShellTokens.toastBottom, windowHeight − footTop + ShellTokens.toastAboveFoot)`, `toastAboveFoot = 12.dp`. Used by the editor's foot row (`ui/EventEditorScreen.kt` `Foot`, and the discard card in its place) and the detail sheet's foot (`ui/EventDetailSheet.kt:167–173`, and its confirm card). When the keyboard hides the foot, nothing is reported, and the toast keeps its keyboard rule.
6. **Foot buttons keep their places while busy** (spec §5). `HhFooterButton` (`core/ui/.../Buttons.kt:247`) with `below = true` always lays out its line under the button: busy keeps the last hint or reason (the button shows its busy state as now), and with no line the slot keeps its height, empty. The editor's foot and the detail sheet's Delete / Change keep fixed widths while busy (`ButtonSize.Large` minimum widths, busy content drawn inside).
7. **The editor is loaded before it is shown** (spec §5 F2). Cause, from the code: `EventEditorHost` (`ui/EventEditorHost.kt`) waits on `produceState` for the opened event and on `repo.members.collectAsState(null)` then `?: return`, while `OverlayLayer` already paints its full-screen `bg` box, so the first frames are a blank `bg`. The fix: `internal suspend fun loadEditor(request: EditorRequest, repo: CalendarRepository, editor: CalendarEditor, today: LocalDate): LoadedEditor?` does the reads (`open(...)` today plus `repo.members.first()` and `repo.held(today).first()`), and `suspend fun OverlayHost.openEditor(request, today, repo, editor, weekFocus)` calls it, then `showFullScreen` with a host that has every value from its first composition. A load that fails toasts `couldNotOpen` as today and shows nothing. The week's Add event, an empty column and Home's + call it through `rememberEventAdder` (a `rememberCoroutineScope`, ignoring a second tap while one loads); the detail's Change and For row through the detail host's existing action runner.
8. **Detail chips show busy at once** (spec §5). `HhPersonChip(name, color, tag, enabled, selected, onClick, busy: Boolean = false)` (`core/ui/.../Controls.kt:405`): while busy it draws `Icons.SCHEDULE` in place of its dot and adds the state "Saving". `EventDetailHost` keeps a `pending: Set<PersonId>?` and the tapped id: the chips show the pending set at once and the tapped chip is busy until the write returns. On failure the set goes back and the existing error toast shows. Other chips stay disabled while busy, as now.
9. **The last person is explained** (spec §5): a tap that would clear the last person toasts "An event needs at least one person." (`NEEDS_A_PERSON` in `EventDetailHost.kt`) and writes nothing.
10. **The pane behind the PIN pad stays put** (spec §5). The plan's reading of the cause: a pane pick queues a move-on (`EditorFlow.moveOnSoon`, `MOVE_ON_MS = 250`), and Add event tapped within 250 ms opens the PIN pad while the move-on still fires. `EditorFlow` gains `fun stay()`, which drops a pending move-on; Add event, Save changes, Delete and ✕ call it first. Task 5's test confirms the cause before the fix; if the test can't reproduce it, the implementer reports back before changing anything.
11. **The keyboard comes up once per opening** (spec §5). `EventEditorScreen`'s `LaunchedEffect(Unit)` asks for title focus and `keyboardController?.show()` each time it enters composition. The flag moves to the form: `EditorForm.titleFocusAsked: Boolean`, set on the first ask, so the PIN pad or a pane change doesn't ask again. After the title loses focus the keyboard is shown only by a tap on the title.
12. **`EventWhen` is the When** (spec §3). An immutable `data class EventWhen(allDay: Boolean, startDate: LocalDate?, startClock: LocalTime, endDays: Int, endClock: LocalTime)` in a new `EventWhen.kt`. `endDays` is the end's date minus the start's: 0 on the same day; for all day it is the last day (inclusive) minus the first. Keeping the end as an offset is what makes "the end follows the start" hold while no date is chosen and when the start date moves (spec §3.1, §3.3). `EditorForm` holds `var eventWhen: EventWhen` in place of `day`, `multiDay`, `until`, `awaiting`, `time` and `length`.
13. **Defaults** (spec §3.4): All day off. The start is `pickerTime`'s rule kept: the next whole hour if the start date is today, not past 23:00, else 09:00; with no date yet, today's rule. The end is the start + 1 hour (from 23:00 that is 00:00 the next day, `endDays = 1`). While neither time has been changed (`timesTouched == false`), picking a Starts date re-applies the default for that date. Defaults don't count as edited: `edited` compares `eventWhen` with the value it opened with, plus the existing answers.
14. **Steppers change only their own field** (spec §3.2, §8 call 3), with `hourStep` and `minuteStep` (`ui/Pickers.kt`; 23 ↔ 00, 45 ↔ 00, no carry). A Starts step moves the end with it and keeps the length: the new end is the new start plus the old length, worked on `LocalDateTime`, so it may cross into the next day. The start's date never changes from a stepper. An Ends step changes only the end's hour or minute; it is **disabled** when its result would be at or before the start (`canStepEnd(field, up): Boolean`). Steps are 1 hour and 15 minutes as `TimePickerCard`'s.
15. **Ends date** (spec §3.3): the grid allows the start date to start + 59 days; others are dimmed and can't be tapped. A pick keeps the end time. A pick of the start's own day with the end time at or before the start makes the end the start + 1 hour (which may land on the next day). All day: any allowed date; the end is the last day, written +1 as today.
16. **Starts date** keeps the length: `endDays` and `endClock` don't change, unless the defaults re-apply (ruling 13).
17. **The cap** (spec §3.3): `endDays > 59` is the cap's error, `UNTIL_TOO_LATE = "An event can last up to 60 days. Pick an earlier end."` It can only arise from a Starts step on a span of 59 days and late hours, or an all-day span over 60 days opened by Change, which is locked.
18. **Locks** (spec §3.3, overriding 9c-1b ruling 21 for timed spans): a timed span opens unlocked in Change. An all-day span over 60 days stays locked: the All day, Starts and Ends rows show `lockedDatesLabel` with `lock` and open no pane. `keepsOwnSpan` stays: an unchanged When writes nothing.
19. **The span** is `fun EventWhen.span(zone: ZoneId): EventSpan?` (`Timed(start: ZonedDateTime, end: ZonedDateTime)` or `AllDay(first: LocalDate, endExclusive: LocalDate)`), null with no start date. A timed span is the two wall times in the zone, with no midnight cap. It replaces `draftSpan` (`EditorForm.kt`).
20. **The clash over a span** (spec §3.5). `Held.check(people: List<PersonId>, from: LocalDateTime, to: LocalDateTime, zone: ZoneId, except: EventRef?, order: List<PersonId>): FreeCheck` replaces the day/start/length overload. The whole span must be held (`holds` for each date from `from` to `to`), else `None`. `FreeCheck.Busy(clashes: List<Clash>, more: Int, freeStarts: List<LocalTime>)` with `data class Clash(person: Person, event: HeldEvent)`: for each chosen person in household order, their first overlapping timed event by start; up to two in `clashes`, the rest counted in `more`. Free starts only when `from` and `to` are on one date (as today's rules, 07:00–23:00, quarter hours, the whole length free). **Over several days** (spec §3.5) `clashes` holds only the first clash by start across the chosen people, `more` is 0 and there are no free starts. All day never checks.
21. **Clash words** (spec §3.5): one busy person alone is still "Overlaps Piano, 15:30–16:15". Several chosen: "Sam has Dentist, 12:30–13:00", two busy "Sam has Dentist, 12:30–13:00; Mia has Piano, 12:45–13:30", more " … and 1 more" ("… and {n} more"). Over several days the event's time reads with its day: "Sam has Dentist, Sat 24 Oct 12:30–13:00". The free line under it stays as 9c-1b ruling 32.
22. **Span words** (spec §3.7): `fun spanWords(span: EventSpan, zone: ZoneId): String` in `CalendarUi.kt`: "Sat 26 Sep, 10:00–10:45"; "Fri 23 Oct 18:00 – Sun 25 Oct 16:00"; "Mon 19 – Fri 23 Oct, all day"; "Sat 26 Sep, all day". The detail sheet's `whenLabel` keeps its own forms where it differs today ("Today", "Tomorrow"), and uses `spanWords` for a span over several days, which it already reads in this form (a **pin**).
23. **The rows** (spec §3.1): title, Who, All day, Starts, Ends, Repeats (Repeats only for an adult's new event, as now). All day is `HhSwitchRow(title = "All day", line = null, checked, tag = "editor_all_day", onChange)` inside the left column, with no pane and no Active state. Starts and Ends are `EditorWhenRow(testTag, icon, label, date: String?, time: String?, ...)`, one row with two buttons: the date ("Sat 26 Sep") and, when All day is off and a date is set, the time ("10:00"). TalkBack: "Starts, Saturday 26 September", "Starts at 10:00", "Ends, Sunday 27 September", "Ends at 11:00"; unset, "Starts, Choose a day". **Ends with no start date** shows its label alone and isn't clickable (TalkBack "Ends, choose a start day first"). Row tags (Clash, Copied, Too long) sit on the Starts row for the clash and on the row they name otherwise.
24. **Keyboard up** (9c-1b ruling 27, re-budgeted): five rows at 61 dp no longer fit 480 dp, so the All day row is hidden while the title is focused with the IME up. 85 + 75 + 9 + 4 × 61 + 27 + 29 ≈ 469 dp, as before.
25. **The flow** (spec §3.6). `enum class EditorStep { Who, StartDate, StartTime, EndDate, EndTime, Repeats, Check }`. `editorSteps(form, childOnly)` drops `StartTime` and `EndTime` while All day is on and `Repeats` when the form can't repeat. Questions: "Who’s it for?", "Which day does it start?", "What time does it start?", "Which day does it end?", "What time does it end?", "Does it repeat?", "Check it". Next reads "Next: {name}" with names "Who", "Start time", "Ends", "End time", "Repeats", "Check" (the step after `StartDate` when All day is on is "Ends"). A Starts date pick moves on after `MOVE_ON_MS` to `StartTime`, or with All day on to `Repeats` (else `Check`), skipping Ends, which already matches. An Ends date pick moves on to `EndTime`, or with All day on to `Repeats` (else `Check`). Steppers never move on. `moveOnSoon(to: EditorStep)` takes its target. Skip shows on `EndDate`, `EndTime` and `Repeats`; on the Ends steps it goes to Repeats (else Check) and changes nothing. A tap on a row's date or time opens that step.
26. **What's missing** (spec §3.6): unchanged `needsLine` forms; "a day" means the Starts date. Nothing else in When is required.
27. **Steppers in the pane** (spec §3.2, §8 call 2): `TimeSteppers` (Task 8) under the question, live, with no Cancel / Set. `TimePickerCard`, `PickerCard`, `PickerLayer` and `EditorPicker` have no other caller (checked: only `EventEditorScreen` and `PickersTest`), so they go in Task 9. `hourStep` and `minuteStep` stay.
28. **What goes** (spec §3.8), in Task 9: `ui/DayPane.kt`'s switch, range, band and `quickPicks` (the grid moves to `DateGrid`); `ui/TimePane.kt` whole except `rememberTimeCheck`'s clash, which moves to the time panes; `TimeChoice`, `EDITOR_LENGTHS`, `lengthChip`, `lengthLabel` (unless another caller remains: check with grep), `END_BEFORE_START`, `draftSpan`, `busyHours`, `busyHoursAndCheck`, `pickerTime`/`endPickerTime`/`untilTimeLabel`, `setMultiDay`/`tapDay`/`chooseHour`/`chooseMinute`/`chooseLength`/`chooseEndTime`; `LASTS_MORE_THAN_A_DAY`, `TAP_FIRST_THEN_LAST`, `AT_A_TIME`, `OTHER_TIME`, `ALL_DAY_ONLY`, `HOUR_GROUPS`; every test and image that draws them. 9c-1b fault F1 goes with them.
29. **Repeats and spans** (spec §3.3, §8 call 8): `fun RepeatChoice.fitsLength(length: Duration): Boolean` in `Recurrence.kt`: Daily ≤ 1 day, Weekly ≤ 7, Fortnightly ≤ 14, MonthlyOnWeekday ≤ 28, Yearly always, Never always. The length is end − start (timed) or the number of days (all day). A choice that doesn't fit shows locked in the Repeats pane with the line "Too long to repeat that often." A When change that rules out the chosen repeat sets it to Never, and the Repeats row carries a "Too long" tag (`info` icon) until Repeats is opened or changed. *(Tag copy is the plan's: the spec says only "says so".)*
30. **Check** (spec §3.7): the sentence's `{when}` and `{time}` become one `spanWords` part for a one-off ("Dinner for Sam, Sat 26 Sep, 10:00–10:45."). A series keeps 9c-1b ruling 36's wording ("every Saturday, 10:00–10:45"; over several days "every Saturday, 18:00 to Sun 16:00"). The date chips are one per occurrence; an occurrence over several days reads "Fri 23 – Sun 25 Oct" (`dayRange`). The "On the week" preview draws a span with the lane's bar: `LaneBar` (`ui/WeekLane.kt`) becomes `internal` with `onOpen: (() -> Unit)?`.
31. **The toast names the span's days** (spec §3.7) when it names a date (9c-1b ruling 38): "Added Camp, Fri 23 – Sun 25 Oct." A one-day event reads as today.
32. **The foot hint** (spec §3.7): "Repeats are optional" while Add event is enabled, the form can repeat and Repeats hasn't been answered; then the clash hint and "Everything’s set" as now; no hint otherwise (a complete Change shows none). `OPTIONAL_HINT` and `TIME_OPTIONAL_HINT` go.
33. **The remembered titles table** (spec §4.1): `RememberedTitleEntity` in `db/CalendarDatabase.kt`, table `remembered_titles`, primary key `(connectionId, titleKey)`, columns `title`, `sourceId`, `forPerson: String?` (the event's tag, else its calendar's person id, else null), `allDay: Boolean`, `startMinute: Int?` and `lengthMinutes: Int?` (timed), `days: Int?` (all day), `rule: String?`, `seriesStart: String?` (ISO date), `rememberedDate: String` (ISO date), `sourceEventId: String` (`seriesId ?: remoteId`). Version 8, `MIGRATION_7_8` creates the table and its index on `rememberedDate`, and is added to `ALL_MIGRATIONS`. The export `schemas/.../8.json` is committed (and the debug-assets copy, as the module does for 7).
34. **The key** (spec §4.1, §4.2): `fun titleKey(title: String): String`: trim, collapse inner whitespace to one space, NFD then drop combining marks, `lowercase(Locale.ROOT)`. "Café" and "Cafe" share a key; the row keeps the title as last typed.
35. **The rule kept** (spec §4.1): `choiceOf(rule, start)` in `EDITOR_REPEATS` and not `Never`, stored as `recurrenceRule(choice, RepeatEnd.Never, start)` (no COUNT, UNTIL or DTSTART; the BYDAY says "4th" or "last"); otherwise null.
36. **Filling** (spec §4.1): `CalendarStore.applySync(connectionId, sourceId, range, result, today: LocalDate)` (the new parameter; `CalendarSync` passes today in the household zone from its `clock`) folds each upserted event with a non-blank title into a candidate per key, a series once (by `seriesId`). The remembered date of a series or event is its latest occurrence read on or before today, else its earliest after today. A candidate replaces the stored row when it comes from the same `sourceEventId`, or when its date is better: `better(a, b, today)` prefers a date on or before today, the later of two such, else the earlier of two future ones. Rows older than `today − 12 months` are deleted in the same transaction (spec §4.1 "same pass").
37. **Forgetting** (spec §4.1, §8 call 9): when `applySync` removes events (`removedIds`, or the ids a full replace drops), and in `applyDeleted` / `applySeriesDeleted` (the Undo path), each row whose `sourceEventId` was removed is deleted, unless a stored event with the same key is left, which then supplies the row by ruling 36's rule. `removeConnection` deletes the connection's rows in its transaction (the debug sample's removal included).
38. **Seeding** (spec §4.1): `CalendarStore.seedRememberedTitles(connectionId, today)` fills from the stored events when the connection has no rows, and `CalendarSync` calls it at the start of each pass. That is once after the upgrade; afterwards it only runs again if every row has been forgotten, which rebuilds the same rows. Incremental syncs return only changes, so without the seed the old events would never be remembered.
39. **Local only** (spec §4.1): the table is in the calendar database, which `allowBackup="false"` and `data_extraction_rules` already exclude. No log line names a title.
40. **`TitleSuggestions`** (spec §4.1, §4.2): a class built once by `CalendarRepository` (`val suggestions = TitleSuggestions(store, zone, clock)`), not a new Hilt binding: the repository is constructed in 18 places and the editor host already has it. *(The spec says "a `@Singleton` (e.g. `TitleSuggestions`)"; one instance per repository, which is a singleton, keeps that.)* `suspend fun matching(typed: String, max: Int = 3): List<Suggestion>`: nothing under 2 characters of `titleKey(typed)`; key prefix matches first, then any word of the key (split on spaces) starting with it; latest `rememberedDate` first within each; one per key across connections (the latest).
41. **`Suggestion`** is `data class Suggestion(title: String, forPerson: String?, allDay: Boolean, start: LocalTime?, length: Duration?, days: Int?, rule: String?, seriesStart: LocalDate?, rememberedDate: LocalDate)`.
42. **Suggestion words** (spec §4.3, §4.4) in a new `SuggestionWords.kt`:
    - **Who:** `whoLine` of the people still in the household (resolved as 9c-1b ruling 4); none left, or Family, is "Family" in the muted line and "Everyone" in the fill line.
    - **Pattern** (the muted line's middle): a weekly series "Saturdays 10:00–10:45"; any other series its wording then the time ("Every 2 weeks, on Saturday, 10:00–10:45"); all day "all day", or "all day for 3 days". A one-off has no pattern: its date and start ("Sat 11 Jul, 10:30", or "Sat 11 Jul, all day").
    - **When:** a series "last on Sat 12 Sep", or "next on Sat 3 Oct" when its date is after today. A one-off's when is its date, already in the pattern.
    - **The muted line** is the parts joined with " · ": "Mia · Saturdays 10:00–10:45 · last on Sat 12 Sep", "Family · Sat 11 Jul, 10:30".
    - **The fill line** is "Fills in " and the filled parts joined as a list ("A and B", "A, B and C"): who, then the time ("10:00–10:45", "all day", "all day for 3 days"), then the repeat lowered ("every Saturday"). So "Fills in Mia, 10:00–10:45 and every Saturday", "Fills in Everyone and 10:30–12:00", and "Fills in Sam and all day for 3 days". *(The spec's third example has a comma; one joining rule can't give all three. The plan keeps the list rule; for the design review.)*
    - **Spoken:** "Guitar lesson. Fills in Mia, 10:00 to 10:45 and every Saturday" (times through the existing spoken-time helper).
    - **The banner:** "Copied from {title} ({when}): {copied}. Tap any row to change it. The date is never copied." `{when}` is "last on Sat 12 Sep", "next on Sat 3 Oct" or a one-off's "Sat 11 Jul"; `{copied}` lists what was filled: "who", "time", "repeats" ("who, time and repeats", "time and repeats", "who and time").
43. **Use this** (spec §4.4): `fun EditorForm.use(s: Suggestion)` fills the title, Who (people left, else Everyone; a child keeps themselves), All day, the start time and the end from the length (or `days − 1` for all day), and the repeat (dropped for a child and when it doesn't fit, ruling 29); `repeatEnd` stays `Never`; the Starts date is set to null. It records `copied: Set<CopiedRow>` (`Who`, `Starts`, `Repeats`; not Who for a child), each cleared when that row's value changes (for Starts: a time or All day change, not the date pick). It counts as an edit. It closes the keyboard (clears focus) and makes `StartDate` active.
44. **Quick picks after Use this** (spec §4.4, §8 call 7): `fun quickPicksFor(s: Suggestion, today: LocalDate): List<LocalDate>` in `SuggestionWords.kt`: Weekly the next three of its weekday from today (today included); MonthlyOnWeekday the next three months' nth (or last) weekday from its rule; Fortnightly the next three days `d >= today` with `(d − seriesStart) % 14 == 0` days; Yearly the next one (29 Feb only in a leap year); Never, Daily and no rule none. Shown as chips "Sat 26 Sep" over the grid in the Starts date pane while a copied repeat stands; a tap is a Starts date pick.
45. **The From before pane** (spec §4.3): while the title is focused on a New event and `matching` has results, the right pane is "From before" with up to three rows and the line "A suggestion never copies the date. Or keep typing; Next on the keyboard moves to Who." Without results, the active row's question as now. Change never asks. A row is one button (`Role.Button`, merged label ruling 42's spoken form, `onClickLabel = "Use this"`); the pill is drawn inside it. Typing again keeps what was filled and asks again.
46. **Icons:** `content_copy` is added to the font (spec §4.3) in Task 13. Nothing else is new: `schedule` and `info` are in the font.
47. **The form stays plain in-memory state** (9c-1 ruling 20).
48. **The shell variable for prediction lists is `$LISTS`** (`C:/t9`), as before.

## Notes for the end-of-v1 design review

From this plan's calls, added to 9c-1b's list:
- busy hours went with the hour chips; the clash line carries them (spec §3.5);
- the Next labels "Next: Start time", "Next: Ends", "Next: End time";
- Ends shows its label alone until a start day is chosen;
- the All day row hides while the keyboard is up;
- the "Too long" tag on Repeats;
- the fill line's joining ("Fills in Sam and all day for 3 days") and the suggestion pattern words;
- the toast's place above a foot (12 dp clear);
- the busy detail chip's clock icon.

## Units (converted once, px ÷ 1.5)

| Where | Handoff px | dp / sp |
|---|---|---|
| Suggestion row | `s2`, r 26; title 28/600; fill line 19/600 accent + icon; pill 64 | `surf2`, r 17; 19 sp W600; 13 sp W600 `accent`, icon 16; pill 48 tall (touch), `accentSoft` / `accent` |
| Muted line, under-list line | (not drawn; as the context line) 21 | 14 sp `mute` |
| Banner | `accentSoft`, r 22, `content_copy` | `accentSoft`, r 15, icon 20, 14 sp |
| Copied tag | green | as the Clash tag's shape, `accentSoft` fill, `accent` ink, `content_copy` 16 |
| Steppers in a pane | `TimePickerCard`'s | `CalendarDimens.stepperWidth` 88, `stepperHeight` 52, `stepperRadius` 18, `timeColumnGap` 14, `colonBottom` 10 (reused, not copied) |
| Toast above a foot | — | `ShellTokens.toastAboveFoot` 12 |

**Height budget** at 1280 × 800 dp, adult's new event: top bar 85 + title 75 + 9 + five rows (Who, All day, Starts, Ends, Repeats) 385 + four gaps 36 + foot 116 + bottom 29 ≈ 735 dp. Keyboard up: ruling 24.

## Global Constraints

**From the spec, verbatim:**
- §1 "The goal is still the 9c spec's: **an Add event that is obvious to use.**"
- §2.1 "One **Starts** row (date and time), one **Ends** row (date and time) and an **All day** switch replace the Day row and the Time row."
- §2.2 "A time is picked with the calendar's existing hour and minute steppers (`TimePickerCard`'s: hours step by 1, minutes by 15), so the app keeps one time control."
- §3.3 "**The end can't be at or before the start.** Any Ends stepper button whose step would put the end at or before the start is disabled."
- §3.3 "**An all-day span over 60 days still stays locked** on Change, because Save would refuse it."
- §4.1 "**Local only:** it is never sent anywhere, and isn't in any export or log."
- §4.2 "**Only for New events.** Change never suggests."
- §4.4 "**Never copied:** the date, or when a repeat ends (Ends stays "Keeps going")."
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

**Privacy:** remembered titles stay in the calendar database (ruling 39). Log lines name no title and no person. Toasts name only the event's title.

**From the house rules** (as 9c-1b; binding):
- **Build:** package root `uk.co.siland.culvery`; `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only. **No new library; never change a version.** No deprecated API.
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug`. Never plain `test`, `check` or `build`. Never a Gradle `install…` task. No subagent touches a device. **Never set TMP, TEMP, TMPDIR or `java.io.tmpdir`** for Gradle (Robolectric's database paths pass MAX_PATH). `$LISTS` is `C:/t9`, never exported.
- **Tests that can fail:** every new test fails before its change; one that passes first is labelled **pin**. Timer tests run on virtual time, under `compose.mainClock` for UI, checked one frame before and one frame after the boundary. Colour checks use `pixelAt(node, dx, dy)`. Tokens aren't asserted against their literals.
- **Every UI task ends the same way:** `compareRoborazziDebug`; open each `*_compare.png` and each new image against the spec section the task names; record; the baseline check with the predictions in `$LISTS/expected-baselines.txt` / `$LISTS/expected-prefixes.txt` (anything outside the prediction stops the task); the gate; commit. Baselines live in `<module>/src/test/screenshots/`.
- **Copy and accessibility:** copy names what the user does; actions keep their name through the flow; errors say what went wrong and how to fix it. `CopyScanTest` passes. Every changed screen's test ends with `compose.assertClickablesHaveRoleAndLabel()`.
- **Code:** icons from `Icons`; a new one goes in `tools/fonts/icons.txt` and the subset is rebuilt from the full font at 608ce00 (`git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$LISTS/material-symbols-full.ttf"`, then `python tools/fonts/subset.py --source "$LISTS/material-symbols-full.ttf"`, `IconFontTest` passing, `NO_FILLED_FORM` if it has no filled form). Layout numbers named in the module's dimens object. Comments say what the code can't, one line, spec refs as `(9c §3.3)`, never plan task or ruling numbers. Sample names only (Alex, Sam, Mia). UTF-8.
- **Commits and git:** a plain sentence, no trailer of any kind. Never push. **Never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **The end near the start, and near midnight.**
   - **Cases:** start 10:00, end 11:00 same day: hour-down, minute-down ×4; start 23:30 (default end 00:30 next day), Ends date moved back to the start's day; start 22:00–23:00 then Starts hour up twice (23 → 00 wraps the start back 23 hours); end on a later day, then hour-down past the start's clock.
   - **Expected:** hour-down disabled; minute-down gives 10:45, 10:30, 10:15, then is disabled; the move back gives 00:30 the next day (start + 1 hour), never an end before the start; the end follows the start to 00:00–01:00 the same day; on a later day any end clock is allowed.
   - **Tests** (Task 6): `EventWhenTest.anEndsStepThatWouldReachTheStartIsDisabled`, `movingTheEndBackToTheStartsDayMakesItAnHourAfter`, `theStartWrapsWithoutCarryAndTheEndFollows`, `onALaterDayTheEndClockIsFree`.
2. **A timed span over the clock change.**
   - **Cases:** Fri 23 Oct 18:00 – Sun 25 Oct 16:00 in Europe/London (clocks go back Sun 25 Oct 2026, 02:00); an all-day Sat 24 – Sun 25 Oct; 01:30 on 25 Oct (it happens twice).
   - **Expected:** the draft's wall times are exactly 18:00 and 16:00; the all-day end is written as Mon 26 Oct exclusive; 01:30 takes the earlier offset (`ZonedDateTime.of` default), as the code does today.
   - **Tests** (Task 6): `EventWhenTest.aTimedSpanOverTheClockChangeKeepsItsWallTimes`, `anAllDaySpanIsWrittenWithTheDayAfter`.
3. **A remembered title forgotten, or not.**
   - **Cases:** two events "Swimming", one deleted on the phone (incremental sync's `removedIds`); the only "Swimmnig" deleted; a series undone from the toast; a full replace that drops an event; a connection removed; a row 12 months and a day old; the same title on two connections.
   - **Expected:** "Swimming" stays, now supplied by the other event; "Swimmnig" goes; the undone series' row goes; the dropped event's row goes; the connection's rows go and the other's stay; the old row goes on the next sync; `matching` shows the title once, from the latest.
   - **Tests** (Task 11): `CalendarStoreRememberedTest.aDeletedEventsTitleStaysWhileAnotherEventHasIt`, `aTitleWithNoEventLeftIsForgotten`, `undoingASeriesForgetsItsTitle`, `aFullReplaceForgetsWhatItDrops`, `removingAConnectionForgetsItsTitles`, `aTitleOlderThanTwelveMonthsIsForgottenOnTheNextSync`; Task 12: `TitleSuggestionsTest.theSameTitleOnTwoConnectionsShowsOnce`.
4. **Use this with people or rules that don't fit.**
   - **Cases:** a remembered "sam,mia" after Sam is removed; one after both are removed; Mia (child) uses an Alex + Sam weekly suggestion; a remembered 9-day all-day event with a Weekly rule (made elsewhere); a monthly-by-date rule (not offered).
   - **Expected:** Who is Mia; Who is Everyone; Who stays Mia with no Copied tag on Who, the repeat dropped and "time" alone in the banner; the repeat is dropped (doesn't fit) and the banner omits "repeats"; the rule was never stored (null), so Repeats stays "Doesn’t repeat".
   - **Tests:** Task 13: `EditorFormSuggestionTest.aRemovedPersonIsLeftOut`, `nobodyLeftIsEveryone`, `aChildKeepsThemselvesAndLosesTheRepeat`, `aRepeatThatDoesNotFitTheSpanIsDropped`; Task 11: `RememberedTitlesTest.aRuleTheEditorDoesNotOfferIsNotKept`.
5. **Changing an event made elsewhere, unchanged.**
   - **Cases:** a timed Fri 18:00 – Sun 16:00 made on a phone, opened by Change and saved with only the title changed; an all-day 70-day span opened by Change; an all-day event opened and All day toggled off then on.
   - **Expected:** the write sends no start or end (`keepsOwnSpan`); the 70-day span's When rows are locked and Save keeps its dates; toggling back leaves the When unchanged and `edited` false.
   - **Tests** (Task 9): `EditorFormTest.aTimedSpanMadeElsewhereSavesWithoutTouchingItsTimes`, `anAllDaySpanOverSixtyDaysIsLocked`, `togglingAllDayBackIsNotAnEdit`.

---

## File Structure

```
capability/calendar/.../Who.kt (whoLineForms), ui/WeekHeader.kt, ui/CardHosts.kt, ui/WeekView.kt (Task 1)
core/ui/.../Shell.kt (ToastClearance, keepsToastsAbove, toastAboveFoot), core/ui/.../Buttons.kt, app/.../shell/ui/OverlayLayers.kt, capability/calendar/.../ui/EventEditorScreen.kt, ui/EventDetailSheet.kt (Task 2)
capability/calendar/.../ui/EventEditorHost.kt (loadEditor, LoadedEditor), ui/Sheets.kt (openEditor), ui/EventDetailHost.kt, ui/CardHosts.kt (Task 3)
core/ui/.../Controls.kt (HhPersonChip busy), capability/calendar/.../ui/EventDetailHost.kt, ui/EventDetailSheet.kt (Task 4)
capability/calendar/.../ui/EditorFlow.kt (stay), ui/EventEditorScreen.kt, EditorForm.kt (titleFocusAsked) (Task 5)
capability/calendar/.../EventWhen.kt (new) (Task 6)
capability/calendar/.../Held.kt (check over a span, Clash), EditorWords.kt (clashLine), CalendarUi.kt (spanWords), ui/TimePane.kt (caller) (Task 7)
capability/calendar/.../ui/TimeSteppers.kt (new), ui/DateGrid.kt (new), ui/Pickers.kt, ui/DayPane.kt (Task 8)
capability/calendar/.../EditorForm.kt, ui/EditorFlow.kt, ui/EditorPane.kt, ui/EditorRows.kt, ui/EventEditorScreen.kt, ui/StartsEndsPanes.kt (new), ui/EventEditorHost.kt; deletes ui/TimePane.kt, ui/DayPane.kt's range and quick picks, the picker card (Task 9)
capability/calendar/.../Recurrence.kt (fitsLength), ui/RepeatsPane.kt, ui/CheckPane.kt, ui/WeekLane.kt, EditorWords.kt, CalendarEditor.kt (addedLineFor), ui/EventEditorScreen.kt (Task 10)
capability/calendar/.../db/CalendarDatabase.kt, db/Migrations.kt, RememberedTitles.kt (new: titleKey, better, the fold), CalendarStore.kt, CalendarSync.kt, schemas/.../8.json (Task 11)
capability/calendar/.../TitleSuggestions.kt (new), SuggestionWords.kt (new), CalendarRepository.kt (Task 12)
capability/calendar/.../ui/FromBeforePane.kt (new), EditorForm.kt (use, copied), ui/EditorRows.kt (Copied tag), ui/StartsEndsPanes.kt (banner, quick picks), tools/fonts/icons.txt, core/ui font, Icons (Task 13)
docs/superpowers/plans/2026-09-23-plan1-followups.md (Tasks 15–17)
```

---

### Task 1: The week: header order, label and dates in one frame, chips shorten the who line (spec §2 ruling 4, §5; rulings 2–4)

**Review:** sonnet.

**Files:**
- Modify: `ui/WeekHeader.kt` (actions order), `ui/CardHosts.kt` (`WeekViewHost`'s weeks-ahead from the loaded week), `Who.kt` (`whoLineForms`), `ui/WeekView.kt:421` (the chip's meta line).
- Test: `ui/WeekViewTest.kt`, `WhoTest.kt`, `ui/CardsTest.kt` (or wherever `WeekViewHost` is driven).

**Interfaces:**
- Produces: `fun whoLineForms(people: List<Person>): List<String>`.

- [ ] **Step 1: The failing tests.**
  - `WeekViewTest.theHeaderReadsThisWeekThenTheArrowsThenAddEvent`: on next week, the left edges of `week_this_week`, `week_prev`, `week_next`, `week_add_event` increase in that order; on this week, `week_prev`'s left is unchanged relative to `week_add_event`.
  - `CardsTest.theWeeksLabelAndDatesChangeInTheSameFrame`: with a repository whose week load is held on a gate, tap ‹ ›'s next; while the load is held the subtitle still reads "This week · Wed 23 – Tue 29 Sep"; after release it reads "Next week · Wed 30 Sep – Tue 6 Oct" in one frame (check every frame under `mainClock.autoAdvance = false`).
  - `WhoTest.theWhoLineShortensBeforeItIsCut`: `whoLineForms([Sam, Mia]) == ["Sam + Mia", "Sam +1"]`; three people `["Alex, Sam +1", "Alex +2"]`; one `["Sam"]`.
  - `WeekViewTest.aNarrowChipShowsTheShorterWhoLine`: Cinema (Sam + Mia) at 17:00 in a column whose chip fits "17:00 · Sam +1" but not "17:00 · Sam + Mia" shows the former with no ellipsis; its content description still says "Sam and Mia".
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 2–4.
- [ ] **Step 3: Images.** Predicted prefix: `week_` (header order; any chip with a shared event in a narrow column). Look at one against spec §2 ruling 4.
```bash
git add capability/calendar
git commit -m "Put This week before the week's arrows, change its label with its dates, and shorten a chip's who line before cutting it"
```

---

### Task 2: The foot: toasts sit above it, its buttons keep their places (spec §5 F3 and the busy note; rulings 5, 6)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Shell.kt` (`ToastClearance`, `LocalToastClearance`, `Modifier.keepsToastsAbove()`, `ShellTokens.toastAboveFoot`), `core/ui/.../Buttons.kt:247` (`HhFooterButton`'s below line), `app/.../shell/ui/OverlayLayers.kt` (`ShellLayers` provides it; `ToastSlot` reads it), `ui/EventEditorScreen.kt` (`Foot` and the discard card), `ui/EventDetailSheet.kt:167–173` (foot row and confirm card).
- Test: `app/src/test/.../OverlayLayersTest.kt`, `core/ui` `ButtonsTest` (or its existing footer test file), `ui/EventEditorScreenTest.kt`, `ui/EventDetailSheetTest.kt`.

**Interfaces:**
- Produces: `class ToastClearance`, `val LocalToastClearance: ProvidableCompositionLocal<ToastClearance?>`, `fun Modifier.keepsToastsAbove(): Modifier`.

- [ ] **Step 1: The failing tests.**
  - `OverlayLayersTest.aToastSitsAboveAFootThatKeepsToastsAbove`: an overlay whose bottom 100 dp box uses `keepsToastsAbove()`; show a toast with an action; the `toast` node's bottom is at least 12 dp above the box's top; the box's `Delete` button is clickable at its centre (`onNodeWithTag(...).performClick()` reaches it).
  - `OverlayLayersTest.whenTheFootGoesTheToastReturnsToItsPlace`: remove the box; the toast's bottom is `toastBottom` from the window's bottom.
  - `ButtonsTest.aBusyFooterButtonKeepsItsLineAndItsPlace`: `HhFooterButton(below = true, hint = "Everything’s set")` then busy (`enabled = false`, no reason): the button's top and the line slot's height are unchanged.
  - `EventEditorScreenTest.theFootReportsItsTop` and `EventDetailSheetTest.theFootReportsItsTop`: with a `ToastClearance` provided, `footTop` equals the foot row's window top; after the confirm card replaces the foot, it equals the card's top.
  - Run `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 5–6.
- [ ] **Step 3: Images.** Predicted: none (toasts aren't in the editor's images; the busy line slot keeps the same height). If a `footer_` image moves, look and record.
```bash
git add core/ui app capability/calendar
git commit -m "Keep the toast above a sheet's or the editor's foot, and keep the foot buttons in place while they are busy"
```

---

### Task 3: F2: the editor draws in the frame it is shown (spec §5 F2; ruling 7)

**Review:** sonnet.

**Files:**
- Modify: `ui/EventEditorHost.kt` (`loadEditor`, `LoadedEditor`; the host takes a `LoadedEditor`; `rememberEventAdder`), `ui/Sheets.kt` (`OverlayHost.openEditor` replaces `showEditor`), `ui/EventDetailHost.kt` (Change and For row), `ui/CardHosts.kt` (Home's adder).
- Test: `ui/EventEditorHostTest.kt`.

**Interfaces:**
- Produces:
  - `internal data class LoadedEditor(val form: EditorForm, val flow: EditorFlow, val members: List<Member>, val context: String, val savesTo: String, val event: EditableEvent?)` (the host's existing inputs, gathered);
  - `internal suspend fun loadEditor(request: EditorRequest, repo: CalendarRepository, editor: CalendarEditor, today: LocalDate): LoadedEditor?`;
  - `suspend fun OverlayHost.openEditor(request: EditorRequest, today: LocalDate, repo: CalendarRepository, editor: CalendarEditor, weekFocus: WeekFocus)`.

- [ ] **Step 1: The failing tests.**
  - `EventEditorHostTest.theEditorDrawsInTheFrameItIsShown`: with `mainClock.autoAdvance = false`, tap `week_add_event`, advance one frame at a time; on the first frame where `RecordingOverlay.fullScreen` is true, `editor_screen` and `editor_title` exist.
  - `EventEditorHostTest.aChangeThatCannotLoadShowsNothingAndSaysSo`: a missing event: the overlay never shows, and the toast is `couldNotOpen`'s.
  - `EventEditorHostTest.aSecondTapWhileLoadingOpensOneEditor`.
  - Run `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventEditorHostTest*"`. Expected: FAIL.
- [ ] **Step 2: Implement** per ruling 7.
- [ ] **Step 3: Images.** Predicted: none.
```bash
git add capability/calendar
git commit -m "Load the editor before showing it, so it draws in its first frame"
```

---

### Task 4: The detail sheet's Who chips: busy at once, the last person explained (spec §5; rulings 8, 9)

**Review:** sonnet.

**Files:**
- Modify: `core/ui/.../Controls.kt:405` (`HhPersonChip(busy)`), `ui/EventDetailHost.kt` (the pending set, the tapped chip, `NEEDS_A_PERSON`), `ui/EventDetailSheet.kt` (`ChooseWho` passes `busy`).
- Test: `core/ui` `ControlsTest`, `ui/EventDetailHostTest.kt`, screenshot `ui/DetailScreenshotTest.kt` (or the module's detail image test).

**Interfaces:**
- Produces: `HhPersonChip(..., busy: Boolean = false)`.

- [ ] **Step 1: The failing tests.**
  - `ControlsTest.aBusyPersonChipSaysSaving`: state description contains "Saving"; the `schedule` glyph is drawn.
  - `EventDetailHostTest.aTappedChipIsBusyAndChosenAtOnce`: with the write held on a gate, tap `assign_Alex` on a Sam + Mia event: in the next frame Alex is selected and busy; after release, not busy, and the detail reads "Alex, Sam +1".
  - `EventDetailHostTest.aFailedAssignPutsTheChipsBack`.
  - `EventDetailHostTest.clearingTheLastPersonSaysWhy`: on a Sam event, tap `assign_Sam`: the toast is "An event needs at least one person." and nothing is written.
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

**Files:**
- Modify: `ui/EditorFlow.kt` (`stay()`), `ui/EventEditorScreen.kt` (Add event / Save / Delete / ✕ call it; focus asked once), `EditorForm.kt` (`titleFocusAsked`).
- Test: `ui/EditorFlowTest.kt`, `ui/EventEditorScreenTest.kt`, `ui/EventEditorHostTest.kt`.

**Interfaces:**
- Produces: `fun EditorFlow.stay()`; `var EditorForm.titleFocusAsked: Boolean`.

- [ ] **Step 1: The failing tests.**
  - `EventEditorHostTest.thePaneBehindThePinPadIsTheOneItWasOn` (confirms the cause first): with nobody signed in, title and Who set, pick a day, tap Add event within 100 ms; advance 300 ms; the active step is still `Day` and `editor_pane` shows "Which day?" behind the PIN pad; after the PIN, still `Day`. If this test passes before the change, stop and report: the cause is elsewhere.
  - `EditorFlowTest.stayDropsAPendingMoveOn`.
  - `EventEditorScreenTest.theKeyboardIsAskedForOncePerOpening`: with a recording `SoftwareKeyboardController` provided through `LocalSoftwareKeyboardController`, open New: one `show()`; move focus to Who, remove and re-add the screen to composition (as the PIN pad does): still one `show()`; tap the title: two.
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 10–11.
- [ ] **Step 3: Images.** Predicted: none.
```bash
git add capability/calendar
git commit -m "Keep the editor on its pane when the PIN pad opens, and bring the keyboard up only once per opening"
```

---

### Task 6: `EventWhen`: Starts, Ends, All day, the defaults, the cap (spec §3.1, §3.3, §3.4; rulings 12–19; Review Focus 1, 2)

**Review:** opus.

**Files:**
- Create: `capability/calendar/.../EventWhen.kt`.
- Test: `capability/calendar/src/test/.../EventWhenTest.kt` (today Wed 23 Sep 2026, now 10:20, `Europe/London`, as `EditorFormTest`).

**Interfaces:**
- Produces:
  - `data class EventWhen(val allDay: Boolean, val startDate: LocalDate?, val startClock: LocalTime, val endDays: Int, val endClock: LocalTime)`;
  - `fun EventWhen.Companion.defaults(startDate: LocalDate?, today: LocalDate, now: LocalTime): EventWhen`;
  - `fun EventWhen.Companion.of(span: EventSpan, zone: ZoneId): EventWhen` (Change);
  - `val EventWhen.endDate: LocalDate?`; `val EventWhen.length: Duration`;
  - `fun EventWhen.pickStartDate(d: LocalDate, defaults: Boolean, today: LocalDate, now: LocalTime): EventWhen` (`defaults` = times untouched, ruling 13);
  - `fun EventWhen.pickEndDate(d: LocalDate): EventWhen?` (null outside start..start + 59);
  - `enum class ClockField { Hour, Minute }`; `fun EventWhen.stepStart(field: ClockField, up: Boolean): EventWhen`; `fun EventWhen.stepEnd(field: ClockField, up: Boolean): EventWhen?` (null when disabled); `fun EventWhen.canStepEnd(field: ClockField, up: Boolean): Boolean`;
  - `fun EventWhen.withAllDay(on: Boolean): EventWhen` (the clocks are kept while All day is on, so turning it off brings them back);
  - `fun EventWhen.withStart(clock: LocalTime): EventWhen` (a free-start chip; the end follows);
  - `val EventWhen.capError: String?` (`UNTIL_TOO_LATE` when `endDays > MAX_SPAN_DAYS − 1`);
  - `sealed interface EventSpan { data class Timed(val start: ZonedDateTime, val end: ZonedDateTime); data class AllDay(val first: LocalDate, val endExclusive: LocalDate) }`; `fun EventWhen.span(zone: ZoneId): EventSpan?`.
  - `UNTIL_TOO_LATE` moves here, reworded: "An event can last up to 60 days. Pick an earlier end."

- [ ] **Step 1: The failing tests** (`EventWhenTest`, values from spec §3.3–§3.4):
  - `theDefaultIsTheNextHourTodayForAnHour`: today, 10:20 → 11:00–12:00, All day off.
  - `anotherDayDefaultsToNine`: Sat 26 Sep → 09:00–10:00.
  - `lateTodayCapsAtElevenAndEndsAtMidnight`: 23:10 → 23:00, end 00:00, `endDays == 1`.
  - `pickingAStartDateWithUntouchedTimesReappliesTheDefault`: today → Sat 26 Sep moves 11:00 to 09:00.
  - `movingTheStartDateKeepsTheLength`: Fri 23 Oct 18:00 – Sun 25 Oct 16:00, start moved to Sat 24 Oct: Sat 18:00 – Mon 26 Oct 16:00.
  - `aStartsStepMovesTheEndAndKeepsTheLength`: 10:00–11:00, hour up → 11:00–12:00; 22:30–23:30, hour up → 23:30–00:30 next day.
  - `theStartWrapsWithoutCarryAndTheEndFollows` (Review Focus 1): 23:00–00:00 (+1), hour up → 00:00–01:00, same start date, `endDays == 0`.
  - `anEndsStepThatWouldReachTheStartIsDisabled` (Review Focus 1, spec §3.3's example): 10:00–11:00 same day: `canStepEnd(Hour, false) == false`, `stepEnd(Hour, false) == null`; minute down → 10:45, 10:30, 10:15, then disabled.
  - `anEndsStepWrapsWithoutCarry`: end 23:45 minute up → 23:00 (wraps 45 → 00 and keeps the hour), allowed when after the start.
  - `onALaterDayTheEndClockIsFree` (Review Focus 1): start Fri 18:00, end Sat 09:00: hour down to 08:00 and on to 00:00 are all allowed.
  - `theEndsDateAllowsTheStartToFiftyNineDaysAfter`: `pickEndDate(start − 1)` and `(start + 60)` null; `(start + 59)` set.
  - `pickingALaterEndDayKeepsTheEndTime`.
  - `movingTheEndBackToTheStartsDayMakesItAnHourAfter` (Review Focus 1): start Fri 10:00, end Sat 09:00, pick Fri → Fri 11:00; start 23:30, end Sat 00:15, pick Fri → Sat 00:30.
  - `allDayKeepsBothDatesAndTogglingBackRestoresTheTimes`.
  - `aTimedSpanOverTheClockChangeKeepsItsWallTimes` (Review Focus 2): Fri 23 Oct 18:00 – Sun 25 Oct 16:00 → `Timed` with those local times and offsets +01:00 / +00:00.
  - `anAllDaySpanIsWrittenWithTheDayAfter` (Review Focus 2): Sat 24 – Sun 25 Oct → `AllDay(24 Oct, 26 Oct)`.
  - `aSixtyOneDaySpanHasTheCapsMessage`: `endDays = 60` → "An event can last up to 60 days. Pick an earlier end."
  - `aChangeOpensOnTheEventsOwnValues`: `of(Timed(...))` and `of(AllDay(...))`.
  - Run `./gradlew :capability:calendar:testDebugUnitTest --tests "*EventWhenTest*"`. Expected: FAIL (no `EventWhen`).
- [ ] **Step 2: Implement** per rulings 12–19, using `hourStep` / `minuteStep` from `ui/Pickers.kt` (move them to `EventWhen.kt` if `ui` can't be imported from the model; `PickersTest` follows them).
- [ ] **Step 3: Run** the test: PASS. Then the gate.
```bash
git add capability/calendar
git commit -m "Add EventWhen: a start and an end with All day, moved together, never ending at or before the start"
```

---

### Task 7: The clash over a span, naming every busy person (spec §3.5; rulings 20–22)

**Review:** opus.

**Files:**
- Modify: `Held.kt` (`check` over `from`/`to`, `Clash`, `FreeCheck.Busy`), `EditorWords.kt` (`clashLine`), `CalendarUi.kt` (`spanWords`), `ui/TimePane.kt` (`rememberTimeCheck` calls the new `check` with `day.atTime(start)` and `+ length`, until Task 9 deletes it).
- Test: `ClashTest.kt`, `EditorWordsTest.kt`, `CalendarUiTest.kt`.

**Interfaces:**
- Consumes: `EventSpan` (Task 6).
- Produces:
  - `fun Held.check(people: List<PersonId>, from: LocalDateTime, to: LocalDateTime, zone: ZoneId, except: EventRef?, order: List<PersonId>): FreeCheck`;
  - `data class Clash(val person: Person, val event: HeldEvent)`; `FreeCheck.Busy(val clashes: List<Clash>, val more: Int, val freeStarts: List<LocalTime>)`;
  - `fun clashLine(check: FreeCheck.Busy, single: Boolean, overDays: Boolean, zone: ZoneId): String`;
  - `fun spanWords(span: EventSpan, zone: ZoneId): String`.

- [ ] **Step 1: The failing tests.**
  - `ClashTest.everyBusyPersonIsNamedUpToTwo`: Sam's Dentist 12:30–13:00 and Mia's Piano 12:45–13:30, Sam + Mia chosen 12:30–13:30 → "Sam has Dentist, 12:30–13:00; Mia has Piano, 12:45–13:30".
  - `ClashTest.aThirdBusyPersonIsCounted`: Alex too → "…; Mia has Piano, 12:45–13:30 … and 1 more".
  - `ClashTest.oneBusyPersonAloneStillReadsOverlaps` (**pin** on the words): "Overlaps Piano, 15:30–16:15".
  - `ClashTest.aSpanOverSeveralDaysNamesTheFirstClashWithItsDay`: Fri 18:00 – Sun 16:00, Sam has Dentist Sat 24 Oct 12:30–13:00 → "Overlaps Dentist, Sat 24 Oct 12:30–13:00", and `freeStarts` empty.
  - `ClashTest.aSpanReachingPastTheHeldWindowHasNoLine`: from `lastDay` 18:00 to `lastDay + 1` 10:00 → `None`.
  - `ClashTest.allDayAndFamilyEventsMakeNobodyBusy`, `theEventBeingChangedDoesNotClashWithItself` (re-pointed **pins**).
  - `CalendarUiTest.spanWordsReadsEachForm`: the four forms of ruling 22.
  - `CalendarUiTest.theDetailReadsATimedSpanInTheSameWords` (**pin**): `whenLabel` for Fri 23 Oct 18:00 – Sun 25 Oct 16:00 equals `spanWords`.
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 20–22.
- [ ] **Step 3: Images.** Predicted: `add_event_clash_` only if its words change (they shouldn't for one person). Gate.
```bash
git add capability/calendar
git commit -m "Check clashes over the whole span, name up to two busy people, and word spans the same everywhere"
```

---

### Task 8: Shared pickers: `TimeSteppers` and `DateGrid` (spec §3.2; ruling 27)

**Review:** sonnet.

**Files:**
- Create: `ui/TimeSteppers.kt` (from `ui/Pickers.kt`'s private `Stepper` and `StepButton`, with `enabled` per button), `ui/DateGrid.kt` (from `ui/DayPane.kt`'s `MonthHeader`, `WeekdayLetters`, `DayCell`, `monthCells`).
- Modify: `ui/Pickers.kt` (`TimePickerCard` uses `TimeSteppers`), `ui/DayPane.kt` (uses `DateGrid`).
- Test: `ui/PickersTest.kt`, `ui/DayPaneTest.kt`, new `ui/TimeSteppersTest.kt`, `ui/DateGridTest.kt`.

**Interfaces:**
- Produces:
  - `@Composable fun TimeSteppers(time: LocalTime, tagPrefix: String, onStep: (ClockField, Boolean) -> Unit, enabled: (ClockField, Boolean) -> Boolean = { _, _ -> true }, modifier: Modifier = Modifier)`; tags `{tagPrefix}_hour_up`, `_hour_down`, `_minute_up`, `_minute_down`, `_hour`, `_minute`; TalkBack "Hour up", "Hour 7" as now.
  - `@Composable fun DateGrid(month: YearMonth, onMonth: (YearMonth) -> Unit, today: LocalDate, chosen: LocalDate?, band: ClosedRange<LocalDate>?, allowed: (LocalDate) -> Boolean, dots: (LocalDate) -> List<Color>, spoken: (LocalDate) -> String, onPick: (LocalDate) -> Unit)`; tags `day_{date}` as now; a disallowed date is dimmed (`HhAlpha.PAST_DAY`), not clickable, and says "not available".

- [ ] **Step 1: The failing tests.** `TimeSteppersTest.aDisabledStepButtonIsNotClickableAndSaysSo`; `DateGridTest.aDateThatIsNotAllowedIsDimmedAndCannotBeTapped`. Existing `PickersTest` and `DayPaneTest` stay green unchanged (**pins** for the move).
  Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL (new names).
- [ ] **Step 2: Implement.** Move, don't copy; reuse `CalendarDimens.stepper*`.
- [ ] **Step 3: Images.** Predicted: none changed (`add_event_day_`, picker images identical).
```bash
git add capability/calendar
git commit -m "Pull the time steppers and the month grid out into shared pickers"
```

---

### Task 9: The swap: Starts, Ends and All day rows and panes; Day and Time panes removed (spec §3.1–§3.6, §3.8; rulings 12–18, 23–28; Review Focus 5)

**Review:** opus.

**Files:**
- Create: `ui/StartsEndsPanes.kt` (`StartDatePane`, `StartTimePane`, `EndDatePane`, `EndTimePane`; the clash line from Task 7 under the steppers, free-start chips on one day).
- Modify:
  - `EditorForm.kt`: holds `eventWhen: EventWhen` and `timesTouched`; `chooseAllDay(on)`, `pickStartDate`, `pickEndDate`, `stepStart`, `stepEnd`, `useFreeStart`; `draft()` from `eventWhen.span(zone)`; `whenTouched` / `keepsOwnSpan` from the opened value; datesLocked = all day and `endDays ≥ 60`; `missing` (Starts date); remove the names in ruling 28.
  - `ui/EditorFlow.kt`: `EditorStep` (ruling 25), `editorSteps`, `moveOnSoon(to)`, `nextLabel`.
  - `ui/EditorPane.kt`: `questionFor` (ruling 25), Skip on the Ends steps and Repeats.
  - `ui/EditorRows.kt`: `EditorWhenRow`; `LOCKED_SPAN` reads "dates locked, as it runs over more than 60 days".
  - `ui/EventEditorScreen.kt`: the rows of ruling 23, the All day row hidden with the keyboard up (ruling 24), the picker overlay removed.
  - `ui/EventEditorHost.kt`: `EditorPicker` and `onPicker` go.
  - `EditorWords.kt`: `dayValue`/`timeValue` give way to the row's date and time strings; `timesOf` uses `span`.
- Delete: `ui/TimePane.kt`; `ui/DayPane.kt` (its grid now in `DateGrid`); from `ui/Pickers.kt` `TimePickerCard`, `PickerCard`, `PickerLayer`, `EditorPicker`; tests `ui/TimePaneTest.kt`, `ui/DayPaneTest.kt` (range and quick-pick cases), the `PickersTest` cases for the card (the `hourStep`/`minuteStep` cases stay or follow Task 6); the `EditorFormTest` range, hour, minute, length and Until cases; images `add_event_day_`, `add_event_days_`, `add_event_time_` and any `picker_time_` baseline.
- Test: `EditorFormTest.kt`, `ui/EditorFlowTest.kt`, `ui/EventEditorScreenTest.kt`, new `ui/StartsEndsPanesTest.kt`, `ui/AddEventScreenshotTest.kt`, `ui/EditForms.kt` (`editorLockedVisit` becomes an all-day 70-day span; a new `editorTimedSpan` is Mon 28 Sep 18:00 – Wed 30 Sep 16:00), `ui/FontScaleTest.kt`, `ui/EventEditorHostTest.kt` (re-pointed tags).

**Interfaces:**
- Consumes: Tasks 6–8.
- Produces: `EditorStep { Who, StartDate, StartTime, EndDate, EndTime, Repeats, Check }`; `EditorForm.eventWhen`; row tags `editor_all_day`, `editor_starts_date`, `editor_starts_time`, `editor_ends_date`, `editor_ends_time`; pane tags `starts_date_pane`, `starts_time_pane`, `ends_date_pane`, `ends_time_pane`; stepper prefixes `start`, `end`.

- [ ] **Step 1: The failing tests.** Each UI test ends with `assertClickablesHaveRoleAndLabel()`.
  - `EditorFormTest`: `aNewEventStartsTimedWithTheDefaults` (ruling 13; not edited); `pickingAStartsDateIsAnEditButTheDefaultsAreNot`; `aTimedSpanMadeElsewhereOpensUnlocked` (spec §3.3); `aTimedSpanMadeElsewhereSavesWithoutTouchingItsTimes` (Review Focus 5); `anAllDaySpanOverSixtyDaysIsLocked` (Review Focus 5); `togglingAllDayBackIsNotAnEdit` (Review Focus 5); `theDraftOfATimedSpanHasItsOwnEnd` (Fri 18:00 – Sun 16:00, no midnight cap); `onlyTheStartsDateIsMissing`.
  - `EditorFlowTest`: `theStepsFollowAllDay`; `aStartsDatePickMovesOnToTheStartTime`; `withAllDayAStartsDatePickMovesOnToRepeats` (and to Check for a child); `anEndsDatePickMovesOnToTheEndTime`; `theSteppersNeverMoveOn`; `nextNamesTheNextStep` ("Next: Start time", "Next: Ends", "Next: End time", "Next: Repeats", "Next: Check").
  - `StartsEndsPanesTest`: `eachPaneAsksItsQuestion` (the four questions, spoken as pane titles); `theEndsGridDimsDaysBeforeTheStartAndPastFiftyNine`; `anEndsHourDownThatWouldReachTheStartIsDisabled` (start 10:00, end 11:00); `aFreeStartMovesTheStartAndTheEndFollows`; `overSeveralDaysThereAreNoFreeStarts`; `allDayHasNoClashLine`; `skipOnEndsKeepsTheEndAndGoesToRepeats`.
  - `EventEditorScreenTest`: `theRowsReadTitleWhoAllDayStartsEndsRepeats`; `eachTargetIsItsOwnButton` ("Starts, Saturday 26 September", "Starts at 10:00"); `endsWaitsForAStartDay`; `allDayHidesBothTimes`; `withTheKeyboardUpTheAllDayRowHidesAndNothingIsCut` (font scale 1.0 and 1.3, 320 dp keyboard); `aLockedAllDaySpanShowsLockedRows`.
  - `CopyScanTest` passes with the old pane copy gone.
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per the rulings. Then `grep -rn "TimePane\|DayPane\|TimePickerCard\|PickerLayer\|EditorPicker\|TimeChoice\|draftSpan\|EDITOR_LENGTHS\|lengthChip\|LASTS_MORE_THAN_A_DAY\|busyHours" --include=*.kt .` outside `build/` finds nothing.
- [ ] **Step 3: Images.** Deleted: `add_event_day_*`, `add_event_days_*`, `add_event_time_*`, picker time-card images. New (dark): `add_event_starts_date_dark`, `add_event_starts_time_dark`, `add_event_ends_date_dark`, `add_event_ends_time_dark`, `add_event_all_day_dark`, `add_event_span_dark` (Fri 18:00 – Sun 16:00). Re-recorded: every other `add_event_` (the rows changed), `add_event_clash_dark` on the Starts time pane. Predicted prefix: `add_event_`. Look at each new one against spec §3.1–§3.2.
```bash
git add -A capability/calendar
git commit -m "Replace the Day and Time rows with Starts, Ends and All day, picked with the month grid and the time steppers"
```

---

### Task 10: Spans everywhere else: repeats, Check, the toast, the detail, the foot hint (spec §3.3 repeats, §3.7; rulings 29–32)

**Review:** sonnet.

**Files:**
- Modify: `Recurrence.kt` (`fitsLength`), `EditorForm.kt` (falls back to Never and sets `repeatTooLong`), `ui/RepeatsPane.kt` (locked rows and the line), `ui/EditorRows.kt` ("Too long" tag), `EditorWords.kt` (`checkSentence`, `firstDates` per occurrence), `ui/CheckPane.kt` (chips, `LaneBar` preview), `ui/WeekLane.kt` (`LaneBar` internal, nullable `onOpen`), `CalendarEditor.kt:143` (`addedLineFor`), `ui/EventEditorScreen.kt` (hints).
- Test: `RecurrenceTest.kt`, `EditorFormTest.kt`, `ui/RepeatsPaneTest.kt`, `EditorWordsTest.kt`, `ui/CheckPaneTest.kt`, `CalendarEditorTest.kt`, `ui/EventEditorScreenTest.kt`, `ui/AddEventScreenshotTest.kt`.

**Interfaces:**
- Consumes: `EventWhen.length`, `spanWords` (Tasks 6–7).
- Produces: `fun RepeatChoice.fitsLength(length: Duration): Boolean`; `EditorForm.repeatTooLong: Boolean`.

- [ ] **Step 1: The failing tests.**
  - `RecurrenceTest.eachRepeatFitsOnlySpansThatEndBeforeTheNextStarts`: Daily 1 day yes / 25 h no; Weekly 7 days yes / 8 no; Fortnightly 14 / 15; MonthlyOnWeekday 28 / 29; Yearly 60 yes.
  - `RepeatsPaneTest.aRepeatTheSpanRulesOutIsLockedWithItsLine`: a 3-day all-day event: Every day locked, "Too long to repeat that often." shown; Every Saturday open.
  - `EditorFormTest.changingTheSpanPastTheRepeatFallsBackToDoesntRepeat`: Every day chosen, end moved a day later → Never and `repeatTooLong`; the Repeats row shows "Too long"; opening Repeats clears it.
  - `EditorWordsTest.theCheckSentenceUsesTheSpanWords`: "Dinner for Sam, Sat 26 Sep, 10:00–10:45."; a weekend span "Camp for Mia, Fri 23 Oct 18:00 – Sun 25 Oct 16:00."; a weekly series keeps "every Saturday, 10:00–10:45".
  - `CheckPaneTest.aSpanHasOneDateChipPerOccurrence` (spec §3.7's note): a weekly 3-day series shows four chips "Fri 23 – Sun 25 Oct", …, and the preview is one lane bar, not a chip.
  - `CalendarEditorTest.theAddedToastNamesTheSpansDays`: past the week's reach, "Added Camp, Fri 23 – Sun 25 Oct."
  - `EventEditorScreenTest.theFootHintIsRepeatsAreOptionalOnlyWhileItMatters`: New, complete, Repeats unanswered: "Repeats are optional"; after Doesn't repeat: none; Change complete: none; a clash: the clash hint.
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 29–32.
- [ ] **Step 3: Images.** New: `add_event_repeats_too_long_dark`, `add_event_check_span_dark`. Re-recorded: `add_event_check_` (sentence), any `add_event_` with the foot hint. Predicted prefix: `add_event_`.
```bash
git add capability/calendar
git commit -m "Offer only the repeats a span allows, and read spans in Check, the added toast and the foot hint"
```

---

### Task 11: `remembered_titles`: the table, its migration, filled and forgotten by sync (spec §4.1; rulings 33–39; Review Focus 3)

**Review:** opus.

**Files:**
- Create: `RememberedTitles.kt` (`titleKey`, `better`, `rememberedFrom(events, sources, today): Map<String, RememberedTitleEntity>`, the rule normalisation).
- Modify: `db/CalendarDatabase.kt` (entity, DAO methods, version 8), `db/Migrations.kt` (`MIGRATION_7_8`, `ALL_MIGRATIONS`), `CalendarStore.kt` (`applySync(..., today)`, `applyDeleted`, `applySeriesDeleted`, `removeConnection`, `seedRememberedTitles`), `CalendarSync.kt` (passes today; seeds at the start of a pass).
- Add: `capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/8.json` (generated by the build) and its debug-assets copy as for 7.
- Test: `db/CalendarMigrationTest.kt`, new `RememberedTitlesTest.kt`, new `CalendarStoreRememberedTest.kt`, `CalendarSyncTest.kt`.

**Interfaces:**
- Produces:
  - `@Entity(tableName = "remembered_titles", primaryKeys = ["connectionId", "titleKey"]) data class RememberedTitleEntity(...)` (ruling 33);
  - DAO: `suspend fun rememberedFor(connectionId: String): List<RememberedTitleEntity>`, `suspend fun allRemembered(): List<RememberedTitleEntity>`, `suspend fun upsertRemembered(rows: List<RememberedTitleEntity>)`, `suspend fun deleteRemembered(connectionId: String, keys: List<String>)`, `suspend fun deleteRememberedOf(connectionId: String)`, `suspend fun deleteRememberedBefore(date: String)`;
  - `fun titleKey(title: String): String`; `fun better(a: LocalDate, b: LocalDate, today: LocalDate): LocalDate`.

- [ ] **Step 1: The failing tests.**
  - `CalendarMigrationTest.migrationFromV7AddsAnEmptyRememberedTitlesTableKeepingEverythingElse`.
  - `RememberedTitlesTest`: `theKeyTrimsCollapsesLowersAndDropsAccents` ("  Café  Lunch " → "cafe lunch"); `theRememberedDateIsTheLatestPastElseTheEarliestFuture`; `aSeriesCountsOnce`; `aRuleTheEditorDoesNotOfferIsNotKept` (Review Focus 4: monthly by date → null); `aKeptRuleHasNoCountUntilOrDtstart`; `anAllDayEventKeepsItsDays`; `aTimedEventKeepsItsStartAndLength`; `anUntaggedEventKeepsItsCalendarsPerson`; `aBlankTitleIsNotRemembered`.
  - `CalendarStoreRememberedTest` (Review Focus 3): `aSyncRemembersWhatItRead`; `theSameEventReplacesItsOwnRow` (time changed on the phone); `aLaterPastDateWins`; `aDeletedEventsTitleStaysWhileAnotherEventHasIt`; `aTitleWithNoEventLeftIsForgotten`; `undoingASeriesForgetsItsTitle` (through `applySeriesDeleted`); `aFullReplaceForgetsWhatItDrops`; `removingAConnectionForgetsItsTitles`; `aTitleOlderThanTwelveMonthsIsForgottenOnTheNextSync`; `theFirstPassSeedsFromStoredEvents`.
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 33–39. Every existing `applySync` caller passes `today` (tests through a fixed clock).
- [ ] **Step 3: Run** the gate. Check the generated `8.json` is committed and the migration test reads it.
```bash
git add capability/calendar
git commit -m "Remember event titles from sync in a new table, forget them with their events, and keep them twelve months"
```

---

### Task 12: `TitleSuggestions`: matching and its words (spec §4.2, §4.3 words, §4.4 quick picks; rulings 40–42, 44)

**Review:** sonnet.

**Files:**
- Create: `TitleSuggestions.kt` (`Suggestion`, `TitleSuggestions`), `SuggestionWords.kt` (`patternLine`, `fillLine`, `spokenSuggestion`, `bannerLine`, `quickPicksFor`).
- Modify: `CalendarRepository.kt` (`val suggestions`).
- Test: `TitleSuggestionsTest.kt`, `SuggestionWordsTest.kt`.

**Interfaces:**
- Consumes: `titleKey`, the DAO (Task 11).
- Produces: `class TitleSuggestions(store: CalendarStore, zone: ZoneId, clock: WallClock) { suspend fun matching(typed: String, max: Int = 3): List<Suggestion> }`; `CalendarRepository.suggestions`; `fun patternLine(s: Suggestion, people: List<Person>, today: LocalDate): String`; `fun fillLine(s: Suggestion, people: List<Person>, childOnly: Boolean): String`; `fun spokenSuggestion(s: Suggestion, people: List<Person>, childOnly: Boolean): String`; `enum class CopiedRow { Who, Starts, Repeats }`; `fun bannerLine(s: Suggestion, copied: Set<CopiedRow>, today: LocalDate): String`; `fun quickPicksFor(s: Suggestion, today: LocalDate): List<LocalDate>`.

- [ ] **Step 1: The failing tests.**
  - `TitleSuggestionsTest`: `nothingUnderTwoCharacters`; `aWordInsideMatches` ("less" finds "Guitar lesson"); `caseAndAccentsAreIgnored` ("cafe" finds "Café"); `titleStartsComeBeforeWordMatchesThenLatestFirst`; `atMostThree`; `theSameTitleOnTwoConnectionsShowsOnce` (Review Focus 3).
  - `SuggestionWordsTest` (spec §4.3 values): "Mia · Saturdays 10:00–10:45 · last on Sat 12 Sep"; "next on Sat 3 Oct" when the date is after today; "Family · Sat 11 Jul, 10:30"; "Fills in Mia, 10:00–10:45 and every Saturday"; "Fills in Everyone and 10:30–12:00"; "Fills in Sam and all day for 3 days"; spoken "Guitar lesson. Fills in Mia, 10:00 to 10:45 and every Saturday"; banner "Copied from Guitar lesson (last on Sat 12 Sep): who, time and repeats. Tap any row to change it. The date is never copied." and with `{Starts}` alone "…: time. …"; `aChildsFillLineLeavesOutWhoAndTheRepeat`.
  - `SuggestionWordsTest.quickPicks`: Weekly Saturday from Wed 23 Sep → 26 Sep, 3 Oct, 10 Oct; MonthlyOnWeekday 4th Saturday → 26 Sep, 24 Oct, 28 Nov; last Thursday → 24 Sep, 29 Oct, 26 Nov; Fortnightly with `seriesStart` Sat 19 Sep → 3 Oct, 17 Oct, 31 Oct (never 26 Sep); Yearly 29 Feb from 2026 → 29 Feb 2028; Daily, Never → empty.
  - Run `./gradlew :capability:calendar:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 40–42, 44.
- [ ] **Step 3: Run** the gate.
```bash
git add capability/calendar
git commit -m "Match remembered titles as the title is typed, and word what each suggestion fills in"
```

---

### Task 13: From before: the list, Use this, Copied, the banner and quick picks (spec §4.3, §4.4; rulings 43–46; Review Focus 4)

**Review:** opus.

**Files:**
- Create: `ui/FromBeforePane.kt`.
- Modify: `EditorForm.kt` (`use`, `copied`, cleared on change), `ui/EventEditorScreen.kt` (the pane while the title is focused, asking `repo.suggestions.matching` per text change, debounced on the text with `snapshotFlow`), `ui/EventEditorHost.kt` (passes `suggestions` on New only), `ui/EditorRows.kt` (`COPIED` tag), `ui/StartsEndsPanes.kt` (banner and quick picks), `tools/fonts/icons.txt`, the font subset, `Icons` (`CONTENT_COPY`).
- Test: new `EditorFormSuggestionTest.kt`, new `ui/FromBeforePaneTest.kt`, `ui/EventEditorScreenTest.kt`, `ui/StartsEndsPanesTest.kt`, `ui/AddEventScreenshotTest.kt`, `IconFontTest`.

**Interfaces:**
- Consumes: Tasks 9, 12.
- Produces: `fun EditorForm.use(s: Suggestion)`; `val EditorForm.copied: Set<CopiedRow>`; `val EditorForm.usedSuggestion: Suggestion?`.

- [ ] **Step 1: The failing tests.** Each UI test ends with `assertClickablesHaveRoleAndLabel()`.
  - `EditorFormSuggestionTest`: `useFillsEverythingButTheDateAndTheEnd` (Starts date null, `repeatEnd == Never`, edited); `aRemovedPersonIsLeftOut`, `nobodyLeftIsEveryone`, `aChildKeepsThemselvesAndLosesTheRepeat`, `aRepeatThatDoesNotFitTheSpanIsDropped` (Review Focus 4); `aCopiedTagGoesWhenItsRowChanges` (Starts' tag stays when the date is picked, goes on a time step); `typingAgainKeepsWhatWasFilled`.
  - `FromBeforePaneTest`: `theListShowsWhileTheTitleIsFocused` ("From before", rows, the under-list line); `withNoMatchesThePaneAsksTheActiveQuestion`; `changeNeverSuggests`; `eachRowIsOneItemWithUseThis` (merged label, click label "Use this"); `threeRowsAtFontScale13ScrollAndNoneIsCutMidRow` (320 dp keyboard).
  - `EventEditorScreenTest.useThisClosesTheKeyboardAndOpensTheStartsDateWithTheBanner`: focus cleared, `StartDate` active, the banner text, quick-pick chips for a weekly suggestion; a quick pick moves on to Start time after 250 ms.
  - `IconFontTest` passes with `content_copy`.
  - Run `./gradlew :capability:calendar:testDebugUnitTest :core:ui:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Add `content_copy`** to `tools/fonts/icons.txt` and rebuild the subset (Global Constraints' commands).
- [ ] **Step 3: Implement** per rulings 43–45.
- [ ] **Step 4: Images.** New: `add_event_from_before_dark` (6a, three rows), `add_event_from_before_keyboard_dark`, `add_event_copied_dark` (6h: the rows with Copied, the banner, quick picks). Predicted: those three; `icon_` images if the icon test draws the font. Look at each against handoff 6a and 6h.
```bash
git add capability/calendar core/ui tools/fonts
git commit -m "Suggest titles from before while typing, fill in the rest with Use this, and show what was copied"
```

---

### Task 14: `simplify` over the whole branch

**Review:** the controller.

- [ ] **Step 1:** Run the `simplify` skill over the range `57a34ebb..HEAD` (this plan's commits), as 9c-1b's was run once for the branch.
- [ ] **Step 2:** The gate. Commit each change set as its own plain sentence. Record partials in the ledger.

---

### Task 15: The emulator walk-through on `culvery-5` (resumed: 9c-1, 9c-1b and this plan together)

**Review:** the controller, with the user at the emulator.

**Rules** (as 9c-1b Task 17, unchanged):
- Use only the `culvery-5` AVD (`emulator-5554`), started with `-no-snapshot-save -camera-front emulated` (check `adb -s "$S" emu avd name`).
- It holds a debug build, so `install -r` the debug APK. Never uninstall, and never run a Gradle install task while a device is connected.
- The controller enters the debug sample PINs (`DebugSeed.kt`).
- Offline is `adb -s "$S" shell svc wifi disable` and `svc data disable`, put back after.
- **Faults are collected, not fixed on the spot.** At the end the user is shown the list and asked before any fix (memory: walk-through faults, ask first).
- Put back every setting changed.

- [ ] **Step 0: Before starting**, check `docs/superpowers/plans/2026-09-23-plan1-followups.md` has entries for "Choosing the destination calendar per event" and "A year-long fetch of past events (the remembered list builds up instead)"; add them if missing and commit "Note the cuts from Plan 9c-1c in the follow-ups". Then tell the user, first, **"Not in this build, on purpose":** the destination calendar per event; a year-long fetch; Home's speed (measured on the tablet).
- [ ] **Step 1: The AVD and the build.** `./gradlew :app:assembleDebug`, then `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2: Re-check what changed** (one line each):
  1. **The week:** This week, ‹ ›, Add event in that order; paging changes "Next week" and the dates together; a narrow shared chip reads "Sam +1", not cut.
  2. **Toasts:** after Add, the toast sits above the editor's foot and the detail sheet's Delete; the foot buttons don't move while busy.
  3. **F2:** the editor opens with no grey frame, from each entry point.
  4. **Detail chips:** a tapped chip shows busy at once; clearing the last person says "An event needs at least one person."
  5. **PIN pad:** pick a date then Add event at once: after the PIN, the same pane.
  6. **Keyboard:** after leaving the title, it stays down until the title is tapped.
  7. **Starts and Ends:** defaults (next hour today, 09:00 another day, an hour long); a Starts step moves the end; an Ends hour-down that would reach the start is greyed; an end on a later day by the Ends date; the Ends grid greys days before the start and past 59 days; All day hides the times and keeps the dates; a timed weekend Fri 18:00 – Sun 16:00 saves and shows as one lane bar.
  8. **Clash:** two busy people both named; over several days the first clash only; no line on All day.
  9. **Repeats with a span:** a 3-day event can't repeat every day ("Too long to repeat that often."); lengthening past a chosen repeat shows "Too long" on Repeats.
  10. **Check and the toast:** one chip per occurrence; span words; the foot hint "Repeats are optional" only while it matters.
  11. **Suggestions:** typing "sw" lists the sample's Swimming under "From before"; Use this fills who, time and repeat, with Copied tags, the banner and quick picks; a child's Use this keeps them; removing the sample connection and re-adding it shows only its own titles.
- [ ] **Step 3: Never reached before** (one line each): idle close at 2 minutes untouched and at 10 minutes edited (warning at 9:40); monthly "4th Saturday" and "last Thursday" land on the right dates in the fake; a counted series reads back "Every Saturday · 12 times · last on Sat 12 Dec"; Undo while offline on a just-queued series leaves nothing to send; Undo by another person signed in is refused; "Day 2 of 3" on Home on a span's second day (set the AVD's date and put it back); a timed span over midnight; the person sheet and Favourites sheets (the wider sheet, darker scrim, edge); every other new thing in this plan.
- [ ] **Step 4: The faults.** Show the user the collected list and ask which to fix. Each agreed fix: a failing test, the fix, the gate, a commit naming it, `install -r`, a re-check.
- [ ] **Step 5: Put back** every setting, then `adb -s "$S" emu kill`.
- [ ] **Step 6: Record** under "Plan 9c-1, 9c-1b and 9c-1c: emulator walk-through".
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record the emulator walk-through of Plans 9c-1, 9c-1b and 9c-1c"
```

---

### Task 16: On the tablet

**Review:** the controller with the user. **Needs the SM-T510 (`R52N60KXWJF`) on the cable.** 4e's tablet rules: save and restore every setting changed, a signed release installed with `adb -s R52N60KXWJF install -r`, **never uninstall**, and the names check before any commit.

- [ ] **Step 1: Install the release**, signed per `docs/setup/release.md`. Keep `mapping.txt` in `culvery-notes/mappings/`.
- [ ] **Step 2: To check when the tablet is next plugged in** (from the follow-ups):
  - the doorbell: pull logcat around 2026-10-21 13:10 (missed), 16:20 (fine) and 18:03 (unexplained), and record what it shows;
  - Home's speed: time from launch to Home filled, three runs, recorded with the build.
- [ ] **Step 3: At the wall** (the user):
  - the editor reads at arm's length; Starts and Ends with the steppers are obvious (spec §1's goal);
  - a timed weekend event and an all-day span show right in Google Calendar on a phone;
  - a shared event made on the wall shows in the calendar's own colour, with the right people back after a sync;
  - a "4th Saturday" and a "last Thursday" series land on the right dates in Google; Undo of a series removes it from Google;
  - after a sync, typing a known title suggests it, and Use this fills it;
  - a child's locked tiles read as locked, not broken.
- [ ] **Step 4: Record** "Plans 9c-1, 9c-1b and 9c-1c: on the tablet": what read and what didn't, the doorbell and speed results. No names.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plans 9c-1, 9c-1b and 9c-1c on the tablet"
```

---

### Task 17: Final review, merge, install

- [ ] **Step 1: The review.** Opus reviews the whole branch (9c-1, 9c-1b and this plan) against Plan 9's spec §2.1, §4.8 and §6.4, the 9c spec, this spec, and the three plans. **One fix wave** follows, each fix with its failing test first and the gate.
- [ ] **Step 2: The follow-ups.** Add to `docs/superpowers/plans/2026-09-23-plan1-followups.md`, under "From Plans 9c-1b and 9c-1c":
  - choosing the destination calendar per event;
  - skipping one date of a series from the event's details;
  - an undone create whose drain delete is lost on restart (`UndoneKeys` is in memory);
  - Home is slow to fill, with Task 16's numbers;
  - the notes for the end-of-v1 design review (9c-1b's and this plan's);
  - remove the "From before" suggestions entry: built here.
  Commit: "Record what Plans 9c-1b and 9c-1c left for later".
- [ ] **Step 3: Merge**, on the user's say: `git merge --ff-only plan-9c-forms` on `main`, then the gate on main. Never push. 9c-2 then branches from it.
- [ ] **Step 4: Install** the release from the merged `main` on the tablet with `adb -s R52N60KXWJF install -r`.

---

## Spec coverage (this spec → tasks)

| Spec | Task |
|---|---|
| §2.1 Starts, Ends, All day replace Day and Time | 6, 9 |
| §2.2 the existing steppers, one time control | 8, 9 (ruling 27) |
| §2.3 suggestions built on this branch | 11–13 |
| §2.4 week header order | 1 |
| §3.1 the rows, two targets, All day switch, Ends follows | 9 (ruling 23) |
| §3.2 the four panes, live steppers, no carry, quick picks only after a suggestion | 8, 9, 13 |
| §3.3 moving together, end never at or before start, later day, back to start's day, all-day inclusive, timed spans unlocked, all-day >60 locked, cap reworded | 6, 9 (rulings 14–18) |
| §3.3 repeats and spans | 10 (ruling 29) |
| §3.4 defaults, not edited, Change values | 6, 9 (ruling 13) |
| §3.5 clash over the span, several days, all day, busy hours gone, every busy person | 7, 9 |
| §3.6 order, moving on, Skip, what's missing | 9 (rulings 25–26) |
| §3.7 Check words, date chips per occurrence, toast and detail, foot hint | 7, 10 |
| §3.8 what goes, F1 | 9 (ruling 28) |
| §4.1 the table, fill, seed, forget, 12 months, connection removal, local only, the reader | 11, 12 |
| §4.2 matching | 12 |
| §4.3 the list, row, lines, under-list line, TalkBack, keyboard up, `content_copy` | 12, 13 |
| §4.4 Use this, never copied, child, banner, quick picks, typing again, edited | 12, 13 |
| §5 F2 | 3 |
| §5 F3 | 2 |
| §5 week header | 1 |
| §5 notes: label and dates; foot buttons; chips' names; detail chip busy; pane behind the PIN; keyboard; last person | 1; 2; 1; 4; 5; 5; 4 |
| §6 not in scope | 15 Step 0, 17 |
| §7 Home's speed | 16, 17 |
| §8 calls | rulings 13, 14, 20–21, 27, 29, 36–38, 40, 44 |
