# Culvery — Plan 9c-1b: The calendar update (full-screen Add event editor, month title, shared events) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–16 are subagent tasks; Task 17 is the controller on the `culvery-5` AVD with the user; Tasks 18–19 need the user and the son's SM-T510.
>
> **This plan finishes 9c-1.** It is built on `plan-9c-forms` on top of 9c-1 (Tasks 1–10 of `2026-10-20-culvery-09c-redesign-forms.md`, built and reviewed). **9c-1's closing tasks (11 emulator, 12 tablet, 13 final review) were not run and are replaced by Tasks 17–19 here**, which walk through, review and merge 9c-1 and this update together (spec §2 ruling 1). 9c-2 (wizard and Settings) still follows as its own plan once this merges.

**Goal:** Add event becomes an obvious, full-screen editor that asks one question at a time; the week view's title is the month; one event can be for two or more people and shows as such everywhere.

**Architecture:**
- **Shared events first, as data.** The people stay in the one `forPerson` string everywhere it is stored (Google's `culvery.person`, Room's column, the outbox JSON) as a comma-separated list of PersonIds, so storage and providers pass it through. A new `Who.kt` reads and writes the list; `EventUi`/`SpanUi` carry `people: List<Person>` and every view and spoken label uses the who line.
- **The shell gains two things:** a full-screen overlay mode (over the rail too, keeping the idle close) and a toast with one action.
- **The editor's model is a new `EditorForm`** that ports `EventForm`'s rules (9c-1) and adds the set of people, an optional day, range picking, hour/minute/length, the reworded repeats and the clash check. The new screen (`EventEditorScreen` plus one file per pane) is built and tested beside the old side sheet, which keeps working until Task 16 swaps the host over and deletes the sheet, `EventForm` and the date picker in one commit. So every commit leaves a working Add event.
- **Undo** is a new `CalendarEditor.undoAdd`, with `CalendarWriter.deleteSeries` for a series.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00, Material 3), Hilt 2.57.1, Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-21-culvery-9c-calendar-update-design.md` (the authority: rulings §2, scope §3 with §3.4, calls §5, tests §6). The drawings are the handoff `docs/design/2026-10-calendar-update/handoff.md` (sizes in px; dp = px ÷ 1.5). When a value is unclear, the HTML reference is `C:\Users\si\code\siland\culvery-notes\design-review-calendar-2026-10\design_handoff_culvery_calendar\Culvery Home Review.dc.html` (frames 6a–6i at the top, 3a and Home 1a further down). Where the spec and the handoff disagree, the spec wins. Plan 9's spec (`2026-10-19-culvery-9-redesign-design.md`) and 9c-1's rulings still hold except where this plan changes them.

**Previous plans (format, shared parts, rulings):** `docs/superpowers/plans/2026-10-20-culvery-09c-redesign-forms.md` (9c-1), `2026-10-20-culvery-09b-redesign-tabs.md`, `2026-10-19-culvery-09a-redesign-home.md`.

**Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md`. Task 19 adds "From before" suggestions and choosing the destination calendar per event there (spec §4).

**Builds on:** branch `plan-9c-forms` (HEAD 0877df71) in the one checkout `C:\Users\si\code\siland\culvery`. There are no worktrees. Work only there. Never push.

| # | Task | Review |
|---|---|---|
| 1 | Shared events: the people list, its storage round trips, colour and permissions | **opus** (old readers; the child rule on a set; no colour) |
| 2 | Shared events: `EventUi.people`, the who line, Home's rows, Coming up, the detail sheet, spoken words | sonnet |
| 3 | Shared events: the week chips and the lane | sonnet |
| 4 | The detail sheet's Who chips become toggles of a set | **opus** (the assign gate; last person; Family exclusive) |
| 5 | The week's title is the month | sonnet |
| 6 | Shell: the full-screen overlay and the toast's action | sonnet |
| 7 | `EditorForm`: who, day and range, time and length, what's missing | **opus** (ported rules; the range; the 60-day cap; the draft) |
| 8 | Repeats worded from the day: nth weekday, the count's last date, the read-back | **opus** (the rule written to Google; the fake's expander; the last date) |
| 9 | What the tablet holds: day dots, busy hours, the clash, the free starts, the Check words | **opus** (the held window; who counts as busy; the event itself) |
| 10 | The editor's frame: top bar, rows and their states, the foot, discard, keyboard up | sonnet |
| 11 | The Who pane | **opus** (a child reaches nobody else) |
| 12 | The Day pane: quick picks, the month, the range | sonnet |
| 13 | The Time pane and the clash | sonnet |
| 14 | The Repeats pane and the Check pane | sonnet |
| 15 | Undo: the toast's action, `undoAdd`, `deleteSeries` | **opus** (the outbox race; the series; who may undo) |
| 16 | The editor in place: host, entry points, after Add, the side sheet removed | **opus** (the gate path; nothing of the sheet left) |
| 17 | The emulator walk-through on `culvery-5` (9c-1 and this update) | controller + user |
| 18 | On the tablet | controller + user, **needs the SM-T510** |
| 19 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:**
1. **Shared events before the editor** (1–4). The editor's Who pane writes a set; the set must already store, read, show and be permitted.
2. **The month title alone** (5): it touches only the week header.
3. **The shell next** (6), because the editor needs full screen and Undo needs the toast's action.
4. **The models** (7–9) before any pane. They are new files beside 9c-1's `EventForm`, so the old sheet keeps working.
5. **The frame, then a pane per task** (10–14), tested directly with sample forms.
6. **Undo** (15) changes the create's toast, which the old sheet already uses.
7. **The swap** (16) last: host, entry points and the removal in one commit.

## Shared components 9c-2 reuses

- **`HhSegmented`** (Task 13), for any two- or three-way choice in Settings.
- **`OverlayHost.showFullScreen`** (Task 6), should a wizard page want it.
- **`Role.label`** (Task 11), moved to `:core:household`; 9c-2's person sheet uses it.

## Rulings against the code

Each is the plan's call unless it cites the spec or the user.

1. **Units** are as in 9a/9c-1: dp = handoff px ÷ 1.5, rounded to whole dp/sp; every 2 px edge is 1 dp; touch targets stay 48 dp (the handoff's 64 and 68 px chips are 48 dp). Values in **Units** below.
2. **The people are one string** (spec §3.3, §5 call 1). `forPerson: String?` keeps its name and type in `RemoteEvent`, `EventDraft`, `StoredEvent`, `EditableEvent`, Room's `EventEntity.forPerson` and the outbox JSON, and now holds `"family"`, one PersonId, or several joined by `,` with no spaces, in the household's order (Settings › People, `people()` `ORDER BY sortOrder`). PersonIds are UUIDs, so they hold no comma. **No migration**, DB version stays 7. `"family"` is never mixed with people: the writer refuses to build it (`tagFor`) and the reader drops `family` from a mixed list (`peopleIn`).
3. **An old reader** (a build before this plan) reads `"sam,mia"` as one unknown id and falls back to the calendar's person, as it does for a removed person today. Nothing crashes, and an old build keeps an untouched tag (`EventForm.kt:289`), so nothing is rewritten. This is a note, not a test: the old code isn't in this build.
4. **A removed person** is dropped when read (spec §3.4). One left makes it theirs. **A tag that names people of whom none are left is Family.** Only an untagged event (null or blank) uses the calendar's person, as today.
5. **No colour for a shared event** (spec §3.3, §5 call 2). `CalendarEditor.colorOf` already gives null for a set (`personOf(PersonId("sam,mia"))` finds nobody), so an insert sends no `colorId` and a PATCH that sets a set sends `colorId: null`. Task 1 makes that explicit (exactly one real person, else null) and pins it. `GoogleEvents.kt` doesn't change.
6. **Permissions on a set** (spec §3.3). `mayCreateFor`/`mayRetag` already compare the whole tag with the person's id, so a child's set must be exactly them. `CREATE` or `ASSIGN` allows any set. The code doesn't change; the tests are **pins** that guard against a later move to `who in peopleIn(forPerson)`.
7. **`EventUi.person` goes;** `people: List<Person>` (never empty, household order) replaces it, as does `SpanUi.people`. Removing the field makes the compiler find every view and spoken label (spec §3.4 "everywhere"). Add `val shared: Boolean get() = people.size > 1`.
8. **The who line** (spec §3.3): one name; two "Sam + Mia"; three or more "Alex, Sam +1" (then +2, +3…). Spoken: "Sam and Mia", "Alex, Sam and Mia".
9. **Striped bars:** one shared `StripedBar(colors, width, radius = width / 2)` splits the bar along its length into equal bands, the first person at the top, clipped to the bar's shape. The week chip's edge is 4 dp: the spec's "6 dp" is read as the handoff's 6 px. It is used by Home's Today and Tomorrow rows (Tomorrow's 75 % kept), Coming up, the detail sheet's title bar, the week chip's 4 dp edge and the lane bar's edge. A single colour draws as today's `ColourBar`, so single-person images don't move.
10. **The detail sheet's inline chips** (spec §3.4) toggle membership and save at once, as today's single tap does.
    - They are `HhPersonChip`s with a new `selected: Boolean?` (core/ui), toggleable when not null. The row lists the household's people, then Family last.
    - Tapping a person adds or removes them. Removing the last person does nothing. Family replaces the set, and a person replaces Family.
    - The set toggled is the shown one (`detail.event.people` ids, minus Family when Family is only the fallback), so a removed person's id is never written back.
    - The chooser stays open after a toggle. The chips are disabled while a write is busy, and the next toggle works from the re-collected detail, so two quick taps both land.
    - `CalendarEditor.assign` takes the new set. ASSIGN (adults) is still the gate, so a child is told `ASK_AN_ADULT` and nothing changes.
    - Tapping the detail's For row on an editable event opens the editor with Who active (spec §3.3, Task 16).
11. **The month title** (spec §3.2): "September" 45 sp W700, letter-spacing −1 sp, then "2026" 45 sp W400 `mute`, 12 dp apart. Across two months it reads "September – October 2026", and across a year "December 2026 – January 2027". TalkBack hears "September 2026", or "September to October 2026". `HhPageHeader` gains an overload whose `title` is a composable slot; the String one calls it.
12. **The week subtitle** (spec §3.2): "{This week | Next week | In N weeks} · {range} · {sync}". The range is "Wed 23 – Tue 29 Sep", with the month on both ends across months. The sync part is `weekLine`. Only its age line ("Updated…", "Out of date —…", "Not updated yet") is joined with its first letter lowered. "Updating…" and a problem line ("The tablet is offline…") keep their case. Updating… keeps replacing only the sync part. The small "Oct" (11 sp W700 `accent`) sits over the date in the 1st's column, and only when the week crosses a month.
13. **The full-screen overlay** (spec §3.1, §5 call 8) is `OverlayHost.showFullScreen(content)`, an interface method with a default that calls `show(content = content)`, so the other hosts need no change. `OverlayState` overrides it and sets `fullScreen = true`. `OverlayLayer` then draws the content with `fillMaxSize`, over a `bg` box and with no scrim, so nothing dismisses on a tap. The idle close (2 min, 10 edited, the 20 s warning) is unchanged. `MainActivity` passes `overSheet = overlay.isShowing && !overlay.fullScreen` to the PIN pad, so the pad covers the whole screen over the editor.
14. **The toast's action** (spec §3.1): `Toaster.show(message, icon, urgent, action: ToastAction)` is a second interface method with a default that drops the action. `ToastAction(label: String, onClick: () -> Unit)`. `ShellToasts` and the calendar's `RecordingToaster` override it. A toast with an action shows for `TOAST_WITH_ACTION_MS = 8_000` (with `activity.timeout(…, hasControls = true)`), not `toastMillis`. core/ui doesn't depend on core/plugin, so `HhToast` takes plain `actionLabel: String? = null, onAction: () -> Unit = {}`, and `ToastLayer` maps `ToastMessage.action` onto them. The icon and message stay one merged group with the live region, as now. The action is a sibling text button in `accent`, 48 dp tall, after the message. An outer row tagged `toast` holds both, so `toast_action` is its own node in the merged tree and TalkBack reaches it. A tap runs `onClick` and hides the toast. **The toast keeps the shared `ink` pill and its icon** (Plan 9 §6.10). The handoff's `s3` pill with a check goes to the design review, so there stays one toast look.
15. **`EditorForm` is new, beside `EventForm`** (spec §3.1 "reuse 9c-1's model"). It ports these rules: the 100-character title, required title, the 60-day cap and its message, timed and over-60-day spans locked on edit, `keepsOwnSpan`, `touched`/`unchanged`, `edited`, COUNT 2–52 default 10, `canRepeat = mode == New && repeats`. **`edited` counts every answer**: the title, Who, Day, multi-day, Until, the hour, the minute, the length and the repeat. Choosing only a day still gets the 10-minute idle close and the discard check. Lengths are `java.time.Duration`, as in `EventForm`. `EventForm.kt`, its tests and the side sheet go in Task 16. Until then the shared top-level names (`TimeChoice`, `MAX_SPAN_DAYS`, `UNTIL_TOO_LATE`, `MAX_TITLE_LENGTH`, `lengthLabel`) stay in `EventForm.kt`, and `EditorForm.kt` uses them. Task 16 moves them into `EditorForm.kt`.
16. **Who starts empty for an adult; a child starts as themselves** (spec §3.4). "Everyone" is `listOf(PersonId.FAMILY)` and clears individual picks. Picking a person clears Everyone. An edit opens on the set it shows elsewhere: its resolved people, with removed people dropped (ruling 4). An untagged event resolves through its calendar's person, else Family. `whoTouched` keeps an untouched edit's tag exactly. Who counts as touched only when the **set** differs (`peopleIn` compared as sets), so re-picking someone or a reordered household isn't a retag. The form then sends no `FOR_PERSON`. A child editing a shared set has their own tile locked too, so they can't untick themselves into a set Save would refuse.
17. **Day is optional until saved:** `day: LocalDate?` starts null for the week's Add event (spec §5 call 9), on the column's date for an empty-column tap, and on today for Home's +.
18. **Range picking** (spec §3.1, handoff 6d). The "Lasts more than a day" switch is `setMultiDay(on)`. On, it forces All day; the stashed time comes back if it is turned off with the same Day. The first tap sets Day and waits for the last. A later tap sets Until, or ends as one day when it is the same date. A tap before Day starts again from it. Once the range is set, the next tap starts a new one. Ranges cross months, and the 60-day cap (`UNTIL_TOO_LATE`) holds Save back. `UNTIL_TOO_EARLY` and "moving Day moves Until" go: a tap can't make an Until before Day. While multi-day is on, the Time pane's "At a time" is a locked segment with the line "An event over several days is all day."
19. **Time** (spec §3.1). `time` is `AllDay` or `Custom(LocalTime)`. "At a time" with no hour yet is the pane's own state, not the form's: the row still reads All day and nothing is lost. An hour chip sets `hour:00`, or keeps the chosen minute. Minutes are ignored while All day. "Other time" opens the existing `TimePickerCard` for any hour. Lengths are 30 min, 45 min, 1 hour and 2 hours (`EDITOR_LENGTHS`), plus "Until…", which opens `TimePickerCard` for an end time on the same day. A pick at or before the start is refused, and the pane shows "The end is before the start. Pick a later time." until the time or length changes. A length outside the four (an edit's own, or an Until pick) shows as "Until 18:30" with that chip selected. **The chips read "30 min" and "45 min" as the spec lists them**, which overrides 9c-1 ruling 19. TalkBack still hears `lengthLabel` ("30 minutes").
20. **Moving on** (handoff Behaviour): `MOVE_ON_MS = 250`. These move on: a Day pick (a one-day pick, or the range's last tap), All day, a length chip or an Until pick, and Doesn't repeat. An hour, a minute and a repeating choice don't, so the minutes, length and Ends can still be set; Next moves on. Who never moves on by itself. Next is "Next: {row}", or "Next: Check" from the last row. Skip (Time and Repeats only) moves on and changes nothing. Not moving on after an hour or a repeating choice differs from the handoff and goes to the design review. The pane's question carries `paneTitle`, so TalkBack speaks the new question when the pane changes.
21. **Rows by mode:** a new adult event shows Who, Day, Time, Repeats. A child's has no Repeats (9c-1 ruling 10). Change event has Who, Day, Time, because only new events repeat. A locked span (a timed or over-60-day multi-day edit, 9c-1 ruling 17) shows Day as one row with `lockedDatesLabel` and a `lock` icon in place of the chevron, which doesn't open a pane. Time is hidden.
22. **Where it opens:** a new event with an empty title focuses the title, with Who active. Change event opens on Check with nothing focused, except from the detail's For row, which opens on Who (`EditorRequest.Edit(ref, from, start = EditorStep.Who)`). The keyboard's Next (`ImeAction.Next`) clears focus and makes Who active. While the title is focused, the pane shows the active row's question (no suggestions, spec §2 ruling 2).
23. **What's missing** (handoff): `needsLine(missing)` gives "Needs a title", "Needs who", "Needs a day", "Needs a title and who", "Needs a title and a day", "Needs who and a day", "Needs a title, who and a day". The foot's reason is that line, else the cap's message (`UNTIL_TOO_LATE`), else null. A refused end time changes nothing in the draft, so its message stays in the Time pane only, until the next time or length change, and doesn't disable Save. This widens 9c-1 ruling 5: Add event and Save changes both show a reason whenever they're disabled. The hint while enabled is "Clashes are allowed; both show on the week" on a clash, "Everything’s set" on Check, and "Time and repeats are optional" otherwise.
24. **The foot's Add event is `HhFooterButton` at `ButtonSize.Large` (60 dp)**, not the handoff's 64 dp: 9c-1 ruling 5's reasoning holds. Its reason and hint sit **under** the button, as the handoff draws, with the `info` icon; that is a new `below = true` on `HhFooterButton`. The 9c-1 sheet's reason above goes with the sheet. Change event's foot has `DeleteButton` (Large) on the left, as 9c-1 ruling 5.
25. **Saves to:** "Saves to the {name} calendar" (spec §3.1), with no "Change". On New, `{name}` is the master source's name. On Change it is the event's own calendar (`EventUi.sourceName`), because that is where the change goes. Either way the name is shown with a trailing " calendar" (any case) removed, so "Family calendar" reads "Saves to the Family calendar". A Google primary calendar named by its address reads "Saves to the alex@example.com calendar"; that is for the design review.
26. **Discard** (spec §3.1): ✕ on an edited form shows `HhConfirmCard` in place of the foot: "Discard this event?" on New and "Discard your changes?" on Change, then "Keep editing" (left) and "Discard" (`ButtonTone.Destroy`). Its icon is `Icons.DELETE` on New and `Icons.CLOSE` on Change, where only the edits are thrown away and a bin would read as deleting the event. ✕ on an unedited form closes at once.
27. **Keyboard up** (handoff 6a): while the title is focused and the IME shows, the rows are 61 dp, their labels go and the values read "Who", "Day", "All day", "Doesn’t repeat" until set. The foot is hidden. Nothing is cut mid-row; a test checks this at font scale 1.0 and 1.3 with a 320 dp keyboard.
28. **The context line** (handoff top bar) is "From Calendar · Alex signed in", "From Home · Alex signed in", or without " · …" when nobody is signed in. `EditorRequest` gains `from: EditorFrom { Home, Calendar }`, passed by `rememberEventAdder` and `rememberEventOpener`.
29. **Quick picks** (handoff §1 6c): "Today", "Tomorrow", the next Saturday or Sunday after tomorrow by its date ("Sat 26 Sep"), and "{Wednesday} next week" (today + 7). The HTML draws "This weekend"/"Next weekend"; the handoff's text wins. A quick pick in multi-day mode is a first tap.
30. **Day dots and the clash read only what the tablet has actually synced** (spec §3.1, §3.4). `CalendarRepository.held(today)` is a member, built like `days()`, with the queued changes laid over it. It covers today to `Held.lastDay`, which is the earliest of:
    - `lastShownDay(today)`;
    - for each connection, its last successful sync's day + `SYNC_FUTURE_DAYS`. The sync's window ends, exclusive, at that day + 29 (`CalendarSync.kt:93`), and `lastShownDay` is today + 27, so a sync five days ago holds four fewer days.

    Past `lastDay` there are no dots, no busy hours and no result line (spec §5 call 6). `Held.unhealthy: Set<PersonId>` holds the people whose calendars sit on a connection that needs signing in, has a read problem, or is out of date (the week's "Out of date" rule). A check that includes any of them is `None`, because their events are missing, not absent. The Day pane still allows any date.
31. **Busy** (spec §3.4): a **timed** event whose resolved people include the person. Family events and all-day events make nobody busy, and Everyone checks nobody. The event being changed never clashes with itself. A busy hour is any overlap with [h:00, h+1:00). The clash is the first overlapping event, by start, for the first busy person in household order. The free starts are the nearest earlier and nearest later quarter-hours on that day, from 07:00 to 23:00, where the whole length is free for every chosen person. That gives at most two chips.
32. **Clash and free-check copy:** one person free reads "Mia is free then". Several read "Sam and Mia are free then", using `spokenWho`. One person busy reads "Overlaps Piano, 15:30–16:15". Several: "Sam has Dentist, 12:30–13:00". The free line is "Mia is free at" (several: "Sam and Mia are free at") then the chips, then "or keep it; clashes are allowed." With no free start: "Clashes are allowed."
33. **Repeats worded from the day** (spec §3.1, §5 calls 4–5). `RepeatChoice` gains `MonthlyOnWeekday` and `fun RepeatChoice.wording(day: LocalDate): String`. The editor offers `EDITOR_REPEATS = listOf(Never, Weekly, Fortnightly, MonthlyOnWeekday, Yearly, Daily)`, worded "Doesn’t repeat", "Every Saturday", "Every 2 weeks, on Saturday", "Every month, on the 4th Saturday", "Every year, on 26 September" and "Every day". The nth is `(dayOfMonth − 1) / 7 + 1` and reads "1st"…"4th"; a 5th reads "last", written `BYDAY=-1SA`, otherwise `BYDAY=4SA`. `Monthly` (by date), `COMMON`, `MORE` and `label` stay for the old sheet and go in Task 16. With no day chosen, the Repeats pane says "Choose a day first." with a quiet "Choose a day" button that opens Day.
34. **Ends** (spec §3.1): Keeps going (`RepeatEnd.Never`) | Stops after (`After(n)`, 2–52, from 10). `RepeatEnd.OnDate` and the form's UNTIL go in Task 16. The stepper's centre reads "{n} times · last on {EEE d MMM}" from `occurrences(choice, day, n).last()`. `occurrences` follows the RRULE: monthly by nth weekday, and yearly from 29 Feb only in leap years. **It is the one place the last date is worked out.** The stepper, the detail's read-back (from `seriesStart` and the rule parsed back to a `RepeatChoice`) and the Check pane all call it, and a property test pins the fake's expander to it (Task 8).
35. **The detail sheet reads repeats in the editor's words** (spec §3.4). `repeatsLabel(rule, start, zone)` is reworded:
    - WEEKLY interval 1 is "Every {weekday}", from the start, or from a single BYDAY that matches it;
    - WEEKLY interval 2 is "Every 2 weeks, on {weekday}";
    - MONTHLY with `BYDAY=nXX` is "Every month, on the {nth} {weekday}", and `-1` is "last";
    - MONTHLY by date is "Every month, on the {d}{st|nd|rd|th}";
    - YEARLY is "Every year, on {d MMMM}";
    - DAILY is "Every day";
    - other intervals are "Every {n} {units}", as today;
    - anything else is "Yes".
    Then `COUNT=n` adds " · {n} times · last on {EEE d MMM}" and `UNTIL` adds " · until {EEE d MMM}". UNTIL may be a date or a UTC time (`20261212T095959Z`, as Google writes timed series); either is read in the household zone.
    **The series' first date is kept with the rule**, so "last on" can be worked out. Both providers put a `DTSTART:{yyyyMMdd}` line before the `RRULE:` line in `RemoteEvent.recurrenceRule`: Google from the master that `ruleOf` already fetches, the fake from the series it made. The date is the master's start in the household zone; for an all-day master it is the start date as given. It is stored in the same string, so there is no migration. `seriesStart(rule): LocalDate?` reads it, and anything that reads rule parts skips the line. A rule without it (stored before this plan, until the next sync) reads " · {n} times" alone.
36. **The Check sentence** (handoff 6g) is "{title} for {who}, {when}, {time}{, from … to …}.{ N times in all.}". `{who}` is `spokenWho`, or "everyone". `{when}` is "Sat 26 Sep", "Mon 19 – Fri 23 Oct", or, for a series, its wording with only the first letter lowered ("every Saturday", "every month, on the 4th Saturday"). One helper, `lowerFirst(s)` in `EditorWords.kt`, does this for both `checkSentence` and `addedLine`. `{time}` is "10:00–10:45" or "all day". A series that keeps going adds "from 26 Sep"; a counted one adds "from 26 Sep to 12 Dec. 12 times in all." The first four start dates show as chips with the people's dots, then "+ {n − 4} more" for a counted series. A series that keeps going shows four chips and no more.
37. **After Add** (spec §3.1, §3.4). From every entry point, Home included, the editor closes onto the Calendar tab, on the week that holds the event's first date (`weeksTo(today, date)`). That works when the date is today to `lastShownDay`. Otherwise the week stays where it was and the toast names the date. The editor lives in the shell's overlay, not in a tab, so it asks for the week through a new `@Singleton class WeekFocus { val requested: StateFlow<Int?>; fun show(weeksAhead: Int); fun consumed() }` in `:capability:calendar`. `WeekViewHost` collects it into `rememberWeekShown().show(n)` and then calls `consumed()`. From Home, the editor also calls `LocalShellNavigator.current.openTab(CALENDAR_TAB_ID)`.
38. **The added toast** (spec §3.1): "Added {title}." for a one-off, "Added {title}, every Saturday." for a series, and "Added {title}, Sat 12 Dec." whenever the week can't show it, past `lastShownDay` or before today (both: "Added {title}, every Saturday from Sat 12 Dec."). It carries the Undo action. A queued create says the same. `EVENT_ADDED` and `eventAddedFor` go.
39. **Undo** (spec §2 ruling 3). `CalendarEditor.undoAdd(added: AddedEvent)` runs on the app scope under the write lock.
    - **Who:** Undo is allowed only when the person signed in now is the one who added it (`who.person.id.value == added.createdBy`). There is no EDIT bypass and no EDIT_OWN gate: having added it is the authority. Anyone else, admin included, is refused with `cannotChangeOthers(name)`, and nothing changes. With nobody signed in it asks for a PIN (`PinReason.Delete`), then applies the same test.
    - **The outbox:** a queued create with that key is dropped with `dropCreate`, which also takes what is queued behind it. A queued series can only have an ASSIGN_SERIES of the same key behind it.
    - **The drain race:** the drain sends outside the lock, so Undo's delete can reach the service before an in-flight create. `UndoneKeys` (a `@Singleton` in-memory set shared by `CalendarEditor` and `CalendarSync`) gets the key first. When the drain's CREATE comes back Accepted for an undone key, it doesn't mirror it, and deletes it at once (`delete`, or `deleteSeries` for a series). The key stays in the set until a delete succeeds; the drain retries it on each pass. It is lost on restart, a gap that is written down.
    - **Then** Undo sends a delete for the key: `delete` for a one-off, `deleteSeries` for a series. Gone (never landed) is success.
    - **Saying what happened:**
      - Only after the delete succeeds (or the drain owns it) does the mirror lose the event, or for a series every row whose `seriesId` or `remoteId` is the key. The toast is then "Removed {title}.".
      - A one-off that can't reach the service queues a DELETE as a delete does today. The mirror hides it through the pending overlay and the toast is "Removed {title}.", which is true once it sends.
      - A series that can't be reached, or is refused (`WriteRejectedException`, no longer a series), isn't queued. A never-attempted queued create (`attempts == 0`) was only dropped, so it is gone and the toast says "Removed {title}.". Anything else (sent at least once, so Google may hold it) leaves the mirror as it was and toasts "Couldn’t undo — remove it in {service} on your phone.".
    - Nothing else gains series delete.
40. **`CalendarWriter.deleteSeries(conn, source, seriesId)`** deletes a whole series (Google: `toChange(series = true)`, then DELETE. Gone is success; one that is no longer a series is refused). The fake removes the series it created. The testkit's contract pins it for both.
41. **The side sheet goes** (spec §3.1) in Task 16, with every test and image that drew it (listed there). The shared pieces stay: `HhFooterButton`, `HhChoiceChip(locked)`, `HhRoundButton(repeatWhileHeld)`, `HhStatusLine(icon, style, spoken)`, `ShellTokens.sheetWidth`/`sheetScrim`/edge, `TimePickerCard`, `PickerLayer`.
42. **The form stays plain in-memory state** (9c-1 ruling 20): nothing goes in `rememberSaveable`.
43. **The shell variable for prediction lists is `$LISTS`** (`C:/t9`), as in 9c-1.

## Notes for the end-of-v1 design review

From this plan's calls:
- the Add event button at 60 dp (the handoff draws 64);
- the shared `ink` toast rather than the `s3` one;
- "Saves to the {address} calendar" for an unnamed Google calendar;
- the quick picks' wording;
- chips at 48 dp where the handoff draws 43 or 45;
- the past and outside-month day fades are lighter than the handoff's 35 % / 25 %, to keep 4.5:1 (`HhAlpha`);
- Time doesn't move on after an hour, nor Repeats after a repeating choice (ruling 20);
- the earlier free start is the nearest quarter-hour (14:45 in 6i), where the handoff draws 14:00;
- the shared chip's edge is 4 dp (6 px);
- the Repeats hint now points to the phone. Skipping one date from the event's details is a follow-up.

## Units (converted once, px ÷ 1.5)

| Where | Handoff px | dp / sp |
|---|---|---|
| Editor top bar | 128 tall, padding 0 48; close 72; title 44/700; context 21 muted | 85; sides 32; `HhCloseButton` 48; 29 sp W700; 14 sp `mute` |
| Body | padding 0 48 44; `760px \| 1fr`; gap 32 | sides 32, bottom 29; left column 507; gap 21 |
| Title field | 112, `s1`, r 28, padding 0 32, 36/600, 3 px accent ring | 75, `surf`, r 19, sides 21, 24 sp W600, 2 `accent` inset |
| Rows | 116, r 28, padding 0 24 0 30, gap 14; icon 38; label 18/700 ls 1.5; value 28/600; chevron | 77, r 19, start 20 end 16, gap 9; 25; 12 sp W700 ls 1 sp; 19 sp W600; 24 |
| Rows, keyboard up | 92 | 61 |
| Row tag | 40, 18/700, icon | 27, 12 sp W700, icon 16 |
| Active ring | 3 px `accent` inset | 2 |
| Foot | gaps 14; line 21 + `event_note`; button 96, r 48, 28/700; reason/hint 21 + `info` | gaps 9; 14 sp `mute`, icon 16; `ButtonSize.Large` 60 (ruling 24); 14 sp `mute`, icon 16 |
| Pane | `s1`, r 36, padding 36 44 40, gap 22; question 40/700; Skip 23/700; Next 72, `s2`, 23/700 | `surf`, r 24, top 24 sides 29 bottom 27, gap 15; 27 sp W700; 15 sp W700 `mute`; 48, `surf2`, 15 sp W700, `arrow_forward` |
| Who tiles | 3 columns, gap 20, 240 tall, r 30, `s2`; circle 96, initial 42/700 #111; name 30/700; role 20 muted; selected: colour + 0x33, 4 px ring, check badge 48 | gap 13, 160, r 20, `surf2`; 64, 28 sp W700 `0xFF111111`; 20 sp W700; 13 sp `mute`; 3 ring, badge 32 |
| Everyone row | 104, `s2`; circle 60 Family purple, `groups` | 69; 40, `FAMILY_COLOR`, icon 24 |
| Day | quick picks 64; month 30/700; ‹ › pill `s2`; letters 18/700; cells 84, circle 80, date 27; dots 8, up to 3; today ring 3, date 700; past 35 %, outside 25 % | 48; 20 sp W700; `HhRoundButton` pair on a `surf2` pill; 12 sp W700 `mute`; 56, 53, 18 sp; 5; 2, W700; `HhAlpha.PAST_DAY` (0.62) / new `HhAlpha.OUTSIDE_MONTH`, at whatever `ContrastTest` passes |
| Range band | `accentSoft` across the cell, rounded at ends and wraps | full cell width, 53 tall, r 27 at the ends |
| Time | hour chips 68, 6 a row; busy: 2 px amber inset, 10 dot; result 40/700; free chips 64 | 48 (`HhChoiceChip`), 6 a row; 1 `weather` inset, dot 7; 27 sp W700 tabular; 48 |
| Repeats | rows 84, selected `accentSoft` + 3 px ring; stepper buttons 80 | 56, 2 ring; `HhRoundButton` 53 → 48 (touch rule keeps 48; icon 24) |
| Check | sentence 36/600; date chips 60 with a dot | 24 sp W600; 40 (not tappable), dot 8 |
| Week title | 68/700 ls −1; year 68/400 muted, 18 apart; subtitle 23; month label 17/700 accent | 45 sp W700 ls −1 sp; 45 sp W400 `mute`, 12; 15 sp; 11 sp W700 `accent` |
| Shared chip | `s2`; 6 px striped edge; padding left 18; dots 12, overlap 3, ring 2 | `surf2`; 4; 12; 8, −2 overlap, 1 ring in `surf2` |
| Clash tag | `weather` + 0x26 dark / 0x22 light | `weather.copy(alpha = 0x26/255f)` dark, `0x22/255f` light |

**Height budget** at 1280 × 800 dp: the left column for an adult's new event is top bar 85 + title 75 + 9 + four rows 308 + three gaps 27 + foot (line 19 + 9 + 60 + 9 + reason 19) 116 + bottom 29 ≈ 649 dp, with about 150 spare. With a 320 dp keyboard: 85 + 75 + 9 + 4 × 61 + 27 + 29 ≈ 469 of 480 dp. The Day pane for a six-week month is 572 dp of pane height after its header; its content is about 556 dp (Task 12 asserts it doesn't scroll). Any pane that is taller scrolls inside itself.

## Global Constraints

**From the spec, verbatim:**

- §1 "**The goal is an Add event that is obvious to use.** Anything in the handoff that adds work without serving that is a candidate to defer."
- §2.2 "Suggestions (6a's list, 6h's Copied state) need a year of past events the tablet does not keep (it syncs from yesterday). They go to the follow-ups. The title field stays; the keyboard's Next moves to Who."
- §2.3 "Undo removes a repeating series too. Only from the 8-second toast, only the series just added on this tablet by the person signed in. It is the one exception to "a series can't be deleted from the wall"; nothing else gains series delete."
- §3.1 "Clash check (6i): … It warns and never blocks. Beyond the synced window (today + `MAX_WEEKS_AHEAD` weeks) there is no free/busy line at all, rather than a false "free"."
- §3.3 "the people stay in Google's `culvery.person` private property, as a comma-separated list of PersonIds; a single id reads as today, so old events need nothing. Room keeps its one column with the same format: no migration. The outbox JSON stays tolerant. "family" is never mixed with people."
- §3.3 "an adult may choose any set; a child only themselves (the set must be exactly them). Retagging to a set follows `mayRetag` for each person."
- §6 "the editor's frames 6b–6g and 6i each get a dark screenshot; shared events get Home, week, lane and detail screenshots. The Google reader and writer get round-trip tests for one, two and "family"."

**Copy** (verbatim, curly apostrophes in code):
- **Top bar:** "New event", "Change event", "From Calendar · {name} signed in", "From Home · {name} signed in", "From Calendar", "From Home".
- **Left column:** "What’s happening?"; "Who", "Day", "Time", "Repeats"; "Choose who", "Choose a day", "All day", "Doesn’t repeat"; "Clash"; "Saves to the {name} calendar"; "Add event", "Save changes".
- **Reasons and hints:** the seven `needsLine` forms (ruling 23); "An event can last up to 60 days. Pick an earlier last day." (kept); "The end is before the start. Pick a later time."; "Time and repeats are optional", "Clashes are allowed; both show on the week", "Everything’s set".
- **Discard:** "Discard this event?", "Discard your changes?", "Keep editing", "Discard".
- **Panes:** "Who’s it for?", "Which day?", "Which days?" (multi-day on), "What time?", "Does it repeat?", "Check it"; "Next: Who", "Next: Day", "Next: Time", "Next: Repeats", "Next: Check", "Skip".
- **Who:** "Everyone", "Shows as a Family event", "Pick more than one person for a shared event.", the role words "Admin", "Adult", "Child"; the child's line "{name} can add events for {name} only." (kept).
- **Day:** "Today", "Tomorrow", "{EEE d MMM}", "{EEEE} next week"; "Lasts more than a day"; "Tap the first day, then the last"; "Earlier months", "Later months" (TalkBack on ‹ ›).
- **Time:** "All day", "At a time", "Morning", "Afternoon", "Evening", "Other time", ":00", ":15", ":30", ":45", "30 min", "45 min", "1 hour", "2 hours", "Until…", "Until {HH:mm}"; "An event over several days is all day."; the free/clash lines of ruling 32.
- **Repeats:** the wordings of ruling 33; "Keeps going", "Stops after", "{n} times · last on {EEE d MMM}", "Fewer times", "More times" (TalkBack, kept); "To skip one date, change it in {service} on your phone."; "Choose a day first.", "Choose a day".
- **Check:** the sentence (ruling 36), "First few", "+ {n} more", "On the week".
- **Toasts:** "Added {title}.", "Added {title}, {wording}.", "Added {title}, {EEE d MMM}.", "Added {title}, {wording} from {EEE d MMM}.", "Undo", "Removed {title}.", "Couldn’t undo — remove it in {service} on your phone."
- **Shared events:** "Sam + Mia", "Alex, Sam +1"; spoken "Sam and Mia", "Alex, Sam and Mia".
- **Week:** "{Month}", "{Month} – {Month} {year}", "{This week | Next week | In N weeks} · {range} · {sync}", "Oct" over the 1st.

**Privacy:** nothing new is stored or logged beyond the tag that already holds a PersonId. A toast names only the event's title, as the added toast does today. Log lines name no title and no person.

**From the house rules** (as Plans 9a, 9b and 9c-1; binding):

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

("Task 3" in the colour-check line is 9c-1's Task 3; `pixelAt`, `windowBitmap` and `isButton` are already in core/ui's `testFixtures`.)

**Icons.** The handoff names 15. Already in the font: `calendar_month`, `schedule`, `repeat`, `chevron_right`, `check`, `info`, `arrow_forward`, `close`, `lock`. Missing: `group`, `groups` (Task 10, Task 11), `event_note` (Task 10), `warning`, `check_circle` (Task 13). `content_copy` is only for suggestions and isn't added. Each task that adds one follows `tools/fonts/README.md` steps 3–4 from the blob at 608ce00 (`git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$LISTS/material-symbols-full.ttf"`, then `python tools/fonts/subset.py --source "$LISTS/material-symbols-full.ttf"`), keeps `IconFontTest` passing, and puts an icon with no filled form in `NO_FILLED_FORM`.

## Review Focus

1. **A shared event and an old reader.**
   - **Cases:** "sam,mia" written by this build and read back by Google, the fake, Room and the outbox; "sam" (an old event); "family"; "sam,family" from bad data; "sam,mia" after Sam is removed, and after both are; "x,y" that names nobody known.
   - **Expected:** each round-trips unchanged; "sam" reads as Sam alone; "sam,family" reads as Sam; after Sam's removal it reads as Mia's; with nobody left, or nobody known, it is Family; an old build reads a list as the calendar's person (ruling 3, a note).
   - **Tests** (Task 1; the round trips are **pins**, since the string already passes through): `GoogleWriteTest.whoRoundTripsForOneTwoAndFamily`, `CalendarProviderContractTest.aDraftForTwoPeopleSyncsBackUnchanged`, `CalendarStoreTest.aSharedTagRoundTripsThroughTheMirrorAndTheOutbox`, `WhoTest.familyIsDroppedFromAMixedList`, `WhoTest.aRemovedPersonIsDroppedAndOneLeftMakesItTheirs`, `WhoTest.aTagWithNobodyLeftIsFamily`.
2. **Undo of a series while it is still in the outbox.**
   - **Cases:** a weekly series queued (offline, never sent), Undo; the same with an assign of the series queued behind it; Undo whose delete reaches Google **before** the drain's in-flight create; a series the service accepted, Undo; a series whose send timed out, Undo while still offline; a one-off accepted, Undo while offline; Undo by an adult, an admin or a child signed in after Alex added it.
   - **Expected:** the queued create and what's behind it are dropped; the in-flight create is deleted as soon as it is accepted and never mirrored; an accepted series is deleted with every occurrence gone from the mirror; the timed-out series says "Couldn’t undo…" and stays in the mirror; an offline one-off queues a DELETE; anyone but Alex is refused and nothing changes.
   - **Tests** (Task 15): `CalendarEditorTest.undoingASeriesStillInTheOutboxDropsItAndWhatIsBehindIt`, `undoBeforeTheDrainsCreateLandsStillRemovesIt`, `undoingASeriesTheServiceHasDeletesItAndEveryOccurrence`, `undoingASeriesWhoseSendTimedOutWhileStillOfflineSaysItCouldNot`, `undoingAOneOffOfflineQueuesItsDelete`, `onlyThePersonWhoAddedItCanUndo`; `CalendarProviderContractTest.deleteSeriesRemovesEveryOccurrence`.
3. **A range across a month boundary.**
   - **Cases:** multi-day on, tap Thu 29 Oct, page to November, tap Sun 1 Nov; the band over the visible month's trailing November days; a range Mon 26 Oct – Tue 3 Nov, seen in November, wrapping from the Sun 1 Nov line onto the next; day + 59 and day + 60.
   - **Expected:** the draft is `AllDay(29 Oct)` → `AllDay(2 Nov)`, Day reads "Thu 29 Oct – Sun 1 Nov", Time is All day; the band is drawn on 30 and 31 Oct and on the trailing 1 Nov cell, rounded at both ends and at the wrap; +60 holds Save back with the cap's message.
   - **Tests:** Task 7: `EditorFormTest.aRangeAcrossAMonthEndsTheDayAfterItsLastDay`, `theRangeKeepsTheSixtyDayCap`; Task 12: `DayPaneTest.aRangeCrossesIntoTheNextMonth`, `theBandIsRoundedAtItsEndsAndWhereItWraps`.
4. **A signed-in child can't reach anyone else.**
   - **Cases:** Mia (child) opens New: taps Alex's tile, Everyone, then Next; Mia opens a Sam + Mia event made by Alex and taps Sam; Mia taps a detail sheet chip; a draft for "mia,sam" sent past the UI; Mia's session ends with the editor open.
   - **Expected:** Who starts as Mia only; other tiles and Everyone are locked, a tap toasts "Mia can add events for Mia only." and changes nothing; Sam stays selected-locked; the detail chip asks for an adult and nothing is written; `CalendarEditor.create` refuses "mia,sam" for `create.self`; after the session ends, Save asks for a PIN and the editor's gate still refuses.
   - **Tests:** Task 1 (**pins**, guarding against a later `who in peopleIn(forPerson)` check): `CalendarEditorTest.aChildCannotAddASharedEventEvenWithThemselvesInIt`, `aChildCannotRetagToASet`; Task 4: `EventDetailHostTest.aChildTappingADetailChipIsAskedForAnAdultAndNothingChanges`; Task 11: `WhoPaneTest.aChildsOtherTilesAndEveryoneAreLocked`, `aTapOnALockedTileExplainsAndChangesNothing`, `aChosenLockedTileStaysChosen`; Task 16: `EventEditorHostTest.aChildWhoseSessionEndsCannotSaveForSomeoneElse`.
5. **A clash past the held window.**
   - **Cases:** Mia chosen, a timed event of hers 15:30–16:15 on `lastShownDay`, and the same on `lastShownDay + 1`; 15:00 for 45 minutes on each day; the last sync five days ago; Mia's calendar needing sign-in; Everyone chosen; an all-day event of hers; changing her Piano itself to 15:45.
   - **Expected:** on `lastShownDay` the 15 hour is busy and the line reads "Overlaps Piano, 15:30–16:15" with the Clash tag; the day after shows no busy hour, no result line and no tag (never "free"); a sync five days old shrinks the held days by four; a calendar needing sign-in gives no line for Mia; Everyone and the all-day event show no clash; the edited event doesn't clash with itself.
   - **Tests:** Task 9: `CalendarRepositoryTest.aStaleSyncHoldsFewerDays`; `ClashTest.aDayPastTheHeldWindowHasNoLine`, `aPersonWhoseCalendarNeedsSignInGetsNoLine`, `allDayAndFamilyEventsMakeNobodyBusy`, `theEventBeingChangedDoesNotClashWithItself`; Task 13: `TimePaneTest.pastTheHeldWindowThereIsNoResultLineAndNoTag`.

The spec's own tests (§6) have their tasks: each of 6b–6g and 6i gets a dark image (Tasks 11–14); shared events get Home, week, lane and detail images (Tasks 2, 3); Google round trips for one, two and family (Task 1); one walk-through (Task 17).

---

## File Structure

```
capability/calendar/.../Who.kt (new: peopleIn, tagFor, resolvePeople, whoLine, spokenWho) (Task 1)
capability/calendar/.../CalendarUi.kt (EventUi.people), MultiDay.kt (SpanUi.people), ui/Components.kt (StripedBar), ui/TodayCard.kt, ui/ComingUpCard.kt, ui/EventDetailSheet.kt, ui/Spoken.kt (Task 2)
capability/calendar/.../ui/WeekView.kt (EventChip), ui/WeekLane.kt (Task 3)
capability/calendar/.../CalendarEditor.kt (assign of a set, order), ui/EventDetailSheet.kt (ChooseWho), ui/EventDetailHost.kt, core/ui/.../Controls.kt (HhPersonChip selected) (Task 4)
capability/calendar/.../CalendarUi.kt (monthTitle, weekRange, weekSubtitleLine), ui/WeekHeader.kt, ui/WeekView.kt (DayHeader), core/ui/.../PageHeader.kt (Task 5)
core/plugin/.../Overlays.kt (showFullScreen, ToastAction), core/ui/.../Shell.kt (HhToast action), app/.../shell/ShellOverlays.kt, app/.../shell/ui/OverlayLayers.kt, app/.../MainActivity.kt (Task 6)
capability/calendar/.../EditorForm.kt (new) (Task 7)
capability/calendar/.../Recurrence.kt (MonthlyOnWeekday, wording, occurrences), Repeats.kt, EditorForm.kt, provider/calendar-fake/.../RecurrenceExpander.kt, the providers' DTSTART line (Task 8)
capability/calendar/.../Held.kt (new: HeldEvent, dots, busy, clash, free starts), EditorWords.kt (new: row values, Check sentence), CalendarRepository.kt (held) (Task 9)
capability/calendar/.../ui/EventEditorScreen.kt (new), ui/EditorFlow.kt (new), ui/EditorRows.kt (new), core/ui/.../Buttons.kt (HhFooterButton below) (Task 10)
capability/calendar/.../ui/WhoPane.kt (new), core/household/.../Model.kt (Role.label) (Task 11)
capability/calendar/.../ui/DayPane.kt (new) (Task 12)
capability/calendar/.../ui/TimePane.kt (new), core/ui/.../Controls.kt (HhSegmented) (Task 13)
capability/calendar/.../ui/RepeatsPane.kt (new), ui/CheckPane.kt (new) (Task 14)
capability/calendar/.../UndoneKeys.kt (new), CalendarEditor.kt (undoAdd, the added toast), CalendarSync.kt (undone keys), CalendarContract.kt (deleteSeries), CalendarStore.kt (applySeriesDeleted), provider/calendar-google/.../GoogleCalendarProvider.kt, provider/calendar-fake/.../FakeCalendarProvider.kt, capability/calendar-testkit/.../CalendarProviderContractTest.kt, its TinyProvider and ContractSuiteSelfTest (Task 15)
capability/calendar/.../WeekFocus.kt (new), ui/EventEditorHost.kt, ui/Sheets.kt, ui/CardHosts.kt, ui/WeekHeader.kt, ui/WeekView.kt; deletes ui/EventEditorSheet.kt, EventForm.kt, the date picker in ui/Pickers.kt (Task 16)
docs/superpowers/plans/2026-09-23-plan1-followups.md (Tasks 17–19)
```

---

### Task 1: Shared events: the people list, its storage round trips, colour and permissions (spec §3.3, §3.4; rulings 2–6; Review Focus 1, 4)

**Review:** opus.

**Files:**
- Create: `capability/calendar/.../Who.kt`.
- Modify:
  - `CalendarUi.kt`: `resolvePerson` becomes a thin call of `resolvePeople(forPerson, sourcePerson, people, people.keys.toList()).first()` until Task 2 removes it.
  - `CalendarEditor.kt`: `colorOf`.
  - `provider/calendar-fake/.../SampleEvents.kt`: one shared sample, "Cinema" (id `cinema`), Sam + Mia, on the third day, 17:00–18:00. It is a new title because Mia's "Swimming" already exists there and in `SampleUi`. `forSource` splits `forName = "Sam,Mia"` on commas and maps each name.
- Test:
  - create `WhoTest.kt`;
  - `CalendarEditorTest.kt`, `CalendarStoreTest.kt`, `PersonResolutionTest.kt`;
  - `provider/calendar-google/src/test/.../GoogleWriteTest.kt`, `GoogleReadTest.kt`;
  - `provider/calendar-fake/src/test/.../FakeCalendarProviderTest.kt` (its title filters don't match "Cinema");
  - `capability/calendar-testkit/.../CalendarProviderContractTest.kt`, and `capability/calendar-testkit/src/test/.../ContractSuiteSelfTest.kt`: `runCount` 21 → 22, ReadOnly's 21 → 22, and `droppedTagsAreCaught` expects the two-people check too.

**Interfaces:**
- Produces, in `Who.kt`:
  - `fun peopleIn(forPerson: String?): List<PersonId>`: split on `,`, trimmed, blanks dropped, distinct, in the order written; `family` dropped when anyone else is there; null or blank gives an empty list.
  - `fun tagFor(people: List<PersonId>, order: List<PersonId>): String`: `require(people.isNotEmpty())`, `require(PersonId.FAMILY !in people || people.size == 1)`; sorted by index in `order` (ids not in `order` last, as given), joined with `,`.
  - `fun resolvePeople(forPerson: String?, sourcePerson: PersonId, people: Map<PersonId, Person>, order: List<PersonId>): List<Person>`: the ids in `peopleIn` that `people` knows (Family included), in household order. An untagged event gives `listOf(people[sourcePerson] ?: Person.Family)`; a tag with nobody known gives `listOf(Person.Family)` (ruling 4).
  - `fun whoLine(people: List<Person>): String` and `fun spokenWho(people: List<Person>): String` (ruling 8). `spokenWho` calls `fun spokenNames(names: List<String>): String` ("Sam", "Sam and Mia", "Alex, Sam and Mia"), which Task 9's free lines also use.
- `CalendarEditor.colorOf(forPerson)` gives a colour only for a tag of exactly one real person.

- [ ] **Step 1: The failing tests.**
  - `WhoTest`:
    - `aSingleIdReadsAsThatPerson`: `peopleIn("sam") == [sam]`; `peopleIn(null)` and `peopleIn("")` are empty.
    - `aListReadsInTheOrderWritten`: `peopleIn("sam,mia") == [sam, mia]`; `" sam , mia ,"` reads the same; `"sam,sam"` reads `[sam]`.
    - `familyIsDroppedFromAMixedList` (Review Focus 1): `peopleIn("sam,family") == [sam]`; `peopleIn("family") == [FAMILY]`.
    - `aTagIsInTheHouseholdsOrder`: with order `[alex, sam, mia]`, `tagFor([mia, sam], order) == "sam,mia"`.
    - `familyIsNeverMixed`: `tagFor([FAMILY, sam], order)` and `tagFor([], order)` throw; `tagFor([FAMILY], order) == "family"`.
    - `aRemovedPersonIsDroppedAndOneLeftMakesItTheirs` (Review Focus 1): with Sam gone from `people`, `resolvePeople("sam,mia", FAMILY, …) == [Mia]`.
    - `aTagWithNobodyLeftIsFamily` (Review Focus 1): with both gone, on a source mapped to Alex, `[Family]`; `"x,y"` naming nobody known gives `[Family]` too.
    - `anUntaggedEventIsTheCalendarsPerson` (**pin**): `null` on a source mapped to Alex gives `[Alex]`.
    - `resolvedPeopleFollowTheHouseholdOrder`: `"mia,alex"` resolves to `[Alex, Mia]` with order `[alex, sam, mia]`.
    - `theWhoLine`: `[Sam]` is "Sam"; `[Sam, Mia]` is "Sam + Mia"; `[Alex, Sam, Mia]` is "Alex, Sam +1"; four people end "+2".
    - `theSpokenWho`: "Sam", "Sam and Mia", "Alex, Sam and Mia".
  - `GoogleWriteTest.whoRoundTripsForOneTwoAndFamily` (**pin**, spec §6): for `"sam"`, `"sam,mia"` and `"family"`, an insert through `FakeGoogleServer` puts that exact string in `extendedProperties.private["culvery.person"]`, and the next sync's `RemoteEvent.forPerson` equals it.
  - `GoogleWriteTest.aSharedDraftSendsNoColour` (**pin**, ruling 5): a hand-made draft with `forPerson = "sam,mia", forPersonColor = null` inserts with no `colorId`, and a PATCH of `FOR_PERSON` to it sends `colorId` as `JsonNull` (the pattern of `whoChangedToFamilyClearsTheColour`).
  - `CalendarEditorTest.aSharedEventGetsNoColour` (**pin**, ruling 5): through `ScriptedWriter`, Alex's create for `"sam,mia"` has `draft.forPersonColor == null`; for `"sam"` it is Sam's colour.
  - `GoogleReadTest.aPersonListIsReadAsWritten` (**pin**): a listed event with `"culvery.person": "sam,mia"` reads `forPerson == "sam,mia"`.
  - `FakeCalendarProviderTest.aSharedEventRoundTrips` (**pin**): create, update and `assignSeries` keep `"sam,mia"`.
  - `CalendarProviderContractTest.aDraftForTwoPeopleSyncsBackUnchanged` (**pin**, Review Focus 1): a create with `forPerson = "a,b"` syncs back with `forPerson == "a,b"`. It runs for both providers.
  - `CalendarStoreTest.aSharedTagRoundTripsThroughTheMirrorAndTheOutbox` (**pin**, Review Focus 1): `applySync` with `"sam,mia"` reads back the same. `enqueue` of a CREATE whose draft has it reads back the same from `pendingNow()`. `assignSeries` writes it to every occurrence.
  - `CalendarEditorTest`:
    - `anAdultMayAddForAnySet` (**pin**): Alex (`create`) adds for `"sam,mia"`, written once.
    - `aChildCannotAddASharedEventEvenWithThemselvesInIt` (**pin**, Review Focus 4): Mia (`create.self`) adding `"sam,mia"` toasts "Mia can add events for Mia only.", with nothing written or queued.
    - `aChildMayAddForThemselvesAlone` (**pin**): `"mia"` is written.
    - `aChildCannotRetagToASet` (**pin**, Review Focus 4): Mia editing her own `"mia"` event to `"sam,mia"` (TITLE + FOR_PERSON) is refused with the same toast.
    - `anAdultMayRetagToASet` (**pin**).
  - `PersonResolutionTest` keeps passing through `resolvePerson`.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*WhoTest*' --tests '*CalendarEditorTest*' --tests '*CalendarStoreTest*' --tests '*PersonResolutionTest*' :provider:calendar-google:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest :capability:calendar-testkit:testDebugUnitTest`. Expected: FAIL to compile (no `Who.kt`), and `ContractSuiteSelfTest` fails on its counts until they are updated. The storage, colour and permission tests are pins and pass once `WhoTest` compiles.
- [ ] **Step 2: Implement** per the Interfaces and rulings 2–6. The fake's sample uses `idsByName` for Sam and Mia.
- [ ] **Step 3: The gate and commit.** Expected: PASS, **no image changed**, since no screenshot test draws the fake's samples.
```bash
git add capability/calendar capability/calendar-testkit provider/calendar-google provider/calendar-fake
git commit -m "Let one event be for several people, stored as a list of ids in the one tag, with no colour of its own and a child still only able to add for themselves"
```

---

### Task 2: Shared events: `EventUi.people`, the who line, Home's rows, Coming up, the detail sheet, spoken words (spec §3.3, §3.4; rulings 7–9)

**Review:** sonnet.

**Files:**
- Modify:
  - `CalendarUi.kt`: `EventUi.person` → `people: List<Person>` with `shared`; `StoredEvent.toUi` takes `order: List<PersonId>` and calls `resolvePeople`; `resolvePerson` goes.
  - `CalendarRepository.kt`: both `toUi` call sites, in `days` and in `event()` (`:107`), pass the household order (`household.people` ids); `Shown` carries it beside its map.
  - `MultiDay.kt`: `SpanUi.person` → `people`; spoken uses `spokenWho`.
  - `ui/Components.kt`: `StripedBar(colors: List<Color>, width: Dp, modifier: Modifier = Modifier, radius: Dp = width / 2)` (as `ColourBar` rounds) replaces `ColourBar`'s uses for events; `ColourBar` stays only where something isn't an event.
  - `ui/TodayCard.kt` (the bar, and the meta line's who line), `ui/ComingUpCard.kt:115/:120`, `ui/EventDetailSheet.kt:203` (title bar), `:248–249` (Who row: overlapping dots as on the week chip, then the who line), `ui/Spoken.kt:52/:57`.
  - `ui/WeekView.kt` and `ui/WeekLane.kt` read `people.first()` for now, so they compile unchanged in look (Task 3 redraws them).
- Test: `PersonResolutionTest.kt` (moves to `resolvePeople`), `ui/SampleUi.kt` (a `cinema` shared sample, Sam + Mia, kept in its own `SampleUi.shared` list outside the default days so existing images don't move; every `person =` becomes `people = listOf(…)`), `ui/CardsTest.kt`, `ui/EventDetailSheetTest.kt`, `SpokenTimeTest.kt` or the spoken test that covers `Spoken.kt`, `MultiDayTest.kt`, `ui/CardScreenshotTest.kt`, `ui/DetailScreenshotTest.kt`, `app/src/test/.../ShellScreenshotTest.kt`; and the files that build or read the field: `WhenLabelTest.kt:55` (`EventUi(…)`), `LaneRowsTest.kt:10` (`SpanUi(…)`), `CalendarRepositoryTest.kt:144/:214/:403/:487/:559` (`.person` → `.people.single()`).

**Interfaces:**
- Consumes: `resolvePeople`, `whoLine`, `spokenWho` (Task 1).
- Produces: `EventUi.people`, `EventUi.shared`, `SpanUi.people`, `StripedBar`.

- [ ] **Step 1: The failing tests.** Each UI test ends with `assertClickablesHaveRoleAndLabel()`.
  - `CardsTest.aSharedTodayRowHasOneStripePerPersonAndTheWhoLine`: one row for Cinema (not two); its meta reads "17:00–18:00 · Sam + Mia"; `pixelAt(bar, 1.dp, 2.dp)` is Sam's colour and `pixelAt(bar, 1.dp, height − 2.dp)` is Mia's. The bands split the bar along its length.
  - `CardsTest.aSharedTomorrowRowKeepsItsFade`: the tomorrow row's top stripe is Sam's colour at 75 % over its background (within 2 per channel, values hard-coded).
  - `CardsTest.aSinglePersonRowIsUnchanged` (**pin**): Alex's row bar is one colour top to bottom.
  - `CardsTest.comingUpUsesTheWhoLine`: a shared row there reads "… · Sam + Mia".
  - `EventDetailSheetTest.theWhoRowShowsTheSet`: the For row reads "Sam + Mia" with two dots; the header bar is striped.
  - `EventDetailSheetTest.threePeopleReadAsTwoAndMore`: Alex, Sam, Mia gives "Alex, Sam +1".
  - The spoken test `aSharedEventIsSpokenWithAnd`: the Today row's description begins "Cinema, Sam and Mia, …"; the week chip's the same.
  - `MultiDayTest.aSharedSpanSaysBoth`: `SpanUi.spoken` begins "Half term, Sam and Mia, …".
  - `PersonResolutionTest`: its cases move to `resolvePeople(...)` with one-element expectations; unchanged results.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*CardsTest*' --tests '*EventDetailSheetTest*' --tests '*MultiDayTest*' --tests '*PersonResolutionTest*' --tests '*Spoken*'`. Expected: FAIL to compile.
- [ ] **Step 2: Implement** per rulings 7–9. `StripedBar` with one colour draws exactly `ColourBar`'s shape and colour.
- [ ] **Step 3: Images.**
  - Predicted: new `today_shared_dark`, `today_shared_light`, `detail_shared_dark`, `detail_shared_light`, `coming_up_shared_dark`; no existing image changes (single-person bars are unchanged).
  - Check against handoff §3 (1a, pin 48) and "Event detail".
```bash
git add capability/calendar app
git commit -m "Show a shared event's people everywhere it shows its person: striped bars and the who line on Home, Coming up and the event's details, and both names when spoken"
```

---

### Task 3: Shared events: the week chips and the lane (spec §3.3; ruling 9)

**Review:** sonnet.

**Files:**
- Modify: `ui/WeekView.kt` (`EventChip`, made `internal` for Task 14's preview), `ui/WeekLane.kt:82`, `ui/CalendarType.kt` (`sharedChipEdge` 4, `sharedChipPaddingStart` 12, `sharedDot` 8, `sharedDotOverlap` 2, `sharedDotRing` 1).
- Test: `ui/WeekViewTest.kt`, `ui/WeekPixelsTest.kt`, `ui/WeekLaneTest.kt`, `ui/WeekScreenshotTest.kt`.

**Interfaces:**
- Produces: `internal fun EventChip(event: EventUi, day: LocalDate, onOpen: ((EventRef) -> Unit)?)`, clickable only when `onOpen` isn't null (the Check pane's preview passes null); the chip's dot row is tagged `chip_dots`.

- [ ] **Step 1: The failing tests.**
  - `WeekPixelsTest.aSharedChipIsNeutralWithAStripedEdge`: the Cinema chip's fill `pixelAt(chip, 30.dp, 4.dp)` is `surf2`, not a tint; `pixelAt(chip, 1.dp, 4.dp)` is Sam's colour and `pixelAt(chip, 1.dp, height − 4.dp)` is Mia's.
  - `WeekPixelsTest.aSinglePersonChipKeepsItsTintAndNoEdge` (**pin**).
  - `WeekViewTest.aChipWithNoOpenIsNotClickable`: `EventChip(…, onOpen = null)` has no click action.
  - `WeekViewTest.aSharedChipsMetaHasOverlappingDotsAndTheWhoLine`: `chip_dots` holds two 8 dp dots whose lefts are 6 dp apart; the meta reads "17:00 · Sam + Mia"; the content starts 12 dp from the chip's left.
  - `WeekLaneTest.aSharedSpanHasAStripedEdge`: the bar's 4 dp edge pixel at the top is Sam's colour and at the bottom Mia's; a single-person bar is unchanged (**pin**).
  - `WeekViewTest.aSharedChipIsSpokenWithAnd`: "Cinema, Sam and Mia, Friday 17:00".

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*Week*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per Units and ruling 9.
- [ ] **Step 3: Images.**
  - Predicted: new `week_shared_dark`, `week_shared_light`, `week_multiday_shared_dark`; no existing `week_` image changes.
  - Check against handoff §3 (3a, pin 49).
```bash
git add capability/calendar
git commit -m "Draw a shared event's week chip on the neutral surface with a striped edge and both people's dots, and stripe a shared multi-day bar's edge"
```

---

### Task 4: The detail sheet's Who chips become toggles of a set (spec §3.4; ruling 10; Review Focus 4)

**Review:** opus.

**Files:**
- Modify:
  - `CalendarEditor.kt`: `assign(ref, people: List<PersonId>)`. The internal constructor gains `order: suspend () -> List<PersonId>`, wired from `household.people` ids; `TestEngines.kt:30` passes it.
  - `core/ui/.../Controls.kt`: `HhPersonChip(…, selected: Boolean? = null)`, toggleable (`Role.Checkbox`) when not null.
  - `ui/EventDetailSheet.kt`: `ChooseWho` shows `selected` chips (tags `assign_{name}`) for the household's people, then Family last.
  - `ui/EventDetailHost.kt:75–81`: ChoosingPerson stays open after a toggle; the chips are disabled while `action.busy`.
- Test: `ui/EventDetailHostTest.kt`, `ui/EventDetailSheetTest.kt`, `CalendarEditorTest.kt`, `ui/DetailScreenshotTest.kt`, `core/ui/src/test/.../ControlsTest.kt`.

**Interfaces:**
- Consumes: `tagFor`, `peopleIn` (Task 1).
- Produces:
  - `suspend fun CalendarEditor.assign(ref: EventRef, people: List<PersonId>): EditResult` writes `tagFor(people, order())`; ASSIGN_SERIES for a recurring event, as today.
  - `internal fun toggled(current: List<PersonId>, tapped: PersonId, mayClear: Boolean = false): List<PersonId>`, in `Who.kt`:
    - tapping Family gives `[FAMILY]`;
    - tapping a person when the set is Family gives `[person]`;
    - otherwise membership is toggled;
    - removing the last person leaves the set as it was, unless `mayClear`.

- [ ] **Step 1: The failing tests.**
  - `WhoTest.toggling`: `[sam]` + mia gives `[sam, mia]`; `[sam, mia]` + sam gives `[mia]`; `[mia]` + mia stays `[mia]`; `[sam]` + FAMILY gives `[FAMILY]`; `[FAMILY]` + sam gives `[sam]`.
  - `EventDetailSheetTest.theChipsShowTheSetChosen`: on a Sam + Mia recurring event in ChoosingPerson, `assign_Sam` and `assign_Mia` are selected and the others aren't. TalkBack: the chips are toggles (`Role.Checkbox`), with "Sam, chosen".
  - `EventDetailHostTest.tappingAChipAddsThatPersonToTheEvent`: Alex signed in taps `assign_Sam` on Mia's event, and the store's tag becomes `"sam,mia"` (household order).
  - `EventDetailHostTest.theLastPersonCannotBeCleared` (**pin**: a tap on the chosen person already writes the same tag): tapping Mia on a Mia-only event writes nothing.
  - `EventDetailHostTest.familyStaysExclusive`: tapping Family on Sam + Mia writes `"family"`; then tapping Sam writes `"sam"`.
  - `EventDetailHostTest.aChildTappingADetailChipIsAskedForAnAdultAndNothingChanges` (**pin**, Review Focus 4; ASSIGN already gates it): Mia (child) taps `assign_Sam` and gets the `ASK_AN_ADULT` toast; nothing is written and nothing is queued.
  - `CalendarEditorTest.assigningASetToASeriesTagsEveryOccurrence`: ASSIGN_SERIES with `"sam,mia"` and no colour.
  - `EventDetailHostTest.theChooserStaysOpenAfterAToggle`: after tapping Sam, `assign_Alex` is still shown.
  - `EventDetailHostTest.twoTogglesInARowBothLand`: with a writer holding the first write, Sam is tapped on Mia's event; the chips are disabled until it lands; then Alex is tapped. The tag is `"alex,sam,mia"`.
  - `EventDetailHostTest.aRemovedPersonIsDroppedWhenTheSetIsChanged`: on `"sam,gone"`, tapping Mia writes `"sam,mia"`.
  - `ControlsTest.aPersonChipCanBeAToggle`: with `selected = true` it is `Role.Checkbox`, checked; with null it is a button, as today (**pin**).

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventDetail*' --tests '*CalendarEditorTest*' --tests '*WhoTest*' :core:ui:testDebugUnitTest --tests '*ControlsTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per ruling 10. The host passes `toggled(detail.event.people.map { it.id }, tapped)` (Family dropped when it is only the fallback for an untagged event) and skips the call when it equals the current set.
- [ ] **Step 3: Images.**
  - Predicted: `detail_untagged_` and `detail_recurring_` (the Family chip, and `selected` chips); new `detail_choose_shared_dark`.
  - Check against handoff §3 "Event detail".
```bash
git add capability/calendar core/ui
git commit -m "Let the event details' who chips choose several people, keeping at least one and Family on its own"
```

---

### Task 5: The week's title is the month (spec §3.2; rulings 11, 12)

**Review:** sonnet.

**Files:**
- Modify: `CalendarUi.kt`: `weekTitle` stays as the "This week" part. Add `monthTitle(start: LocalDate): MonthTitle`, `weekRange(start: LocalDate): String` and `weekSubtitleLine(weeksAhead: Int, start: LocalDate, sync: String): String`. Also:
  - `ui/WeekHeader.kt:64–90`;
  - `ui/WeekView.kt` (`DayHeader` gains the month label, tag `week_month_label`);
  - `core/ui/.../PageHeader.kt` (the slot overload);
  - `ui/CalendarType.kt` (`monthTitle`, `monthTitleYear`, `monthLabel`, `monthYearGap` 12).
- Test: create `MonthTitleTest.kt`; `ui/WeekViewTest.kt`; `ui/WeekScreenshotTest.kt`; `core/ui/src/test/.../PageHeaderTest.kt` (pin).

**Interfaces:**
- Produces:
  - `data class MonthTitle(val months: String, val year: String, val spoken: String)`, with `months` "September" or "September – October", `year` "2026", and `spoken` "September 2026" or "September to October 2026". Across years, `months` is "December 2026 – January" and `year` is "2027".
  - `fun HhPageHeader(title: @Composable () -> Unit, subtitle: String?, …)`; the String overload calls it with today's `Text`.
- Consumes: `weekLine` (unchanged).

- [ ] **Step 1: The failing tests.**
  - `MonthTitleTest.oneMonth`: Wed 2 Sep 2026 gives "September", "2026", "September 2026".
  - `MonthTitleTest.twoMonths`: Wed 23 Sep gives a week to Tue 29 Sep, so one month. Wed 30 Sep gives "September – October", "2026", "September to October 2026".
  - `MonthTitleTest.acrossTheYear`: Wed 30 Dec 2026 gives "December 2026 – January", "2027".
  - `MonthTitleTest.theRange`: "Wed 23 – Tue 29 Sep"; across months "Wed 30 Sep – Tue 6 Oct".
  - `MonthTitleTest.theSubtitle`:
    - "This week · Wed 23 – Tue 29 Sep · updated from Google Calendar 2 minutes ago";
    - "Next week · …" and "In 2 weeks · …";
    - an "Out of date — …" line is joined as "out of date — …";
    - "The tablet is offline. Check the Wi-Fi, then Update now." keeps its capital.
  - `WeekViewTest.theTitleIsTheMonthWithTheYearMuted`: `week_title` contains "September" and "2026", the year's text colour is `mute`, and the heading is spoken "September 2026".
  - `WeekViewTest.aWeekAcrossTwoMonthsLabelsTheFirst`: the week from Wed 30 Sep shows `week_month_label` "Oct" over Thu 1 Oct's date, and only there. A week within one month has none.
  - `WeekViewTest.updatingReplacesOnlyTheSyncPart`: after Update now, `week_subtitle` reads "This week · Wed 23 – Tue 29 Sep · Updating…".
  - `PageHeaderTest.theStringTitleIsUnchanged` (**pin**).

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*MonthTitleTest*' --tests '*WeekViewTest*' :core:ui:testDebugUnitTest --tests '*PageHeader*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 11–12 and Units.
- [ ] **Step 3: Images.**
  - Predicted: every `week_` image (the title and subtitle); new `week_two_months_dark`, `week_two_months_light`. `app` images showing the Calendar tab (`ShellScreenshotTest`).
  - Check against handoff §2.
```bash
git add capability/calendar core/ui app
git commit -m "Title the week with its month, say which week and its dates in the line under it, and mark the 1st when a week crosses into a new month"
```

---

### Task 6: Shell: the full-screen overlay and the toast's action (spec §3.1; rulings 13, 14)

**Review:** sonnet.

**Files:**
- Modify:
  - `core/plugin/.../Overlays.kt`: `OverlayHost.showFullScreen`, `ToastAction`, `Toaster.show(…, action)`, `TOAST_WITH_ACTION_MS`.
  - `core/ui/.../Shell.kt`: `HhToast(…, actionLabel: String? = null, onAction: () -> Unit = {})`. These are plain parameters, because core/ui has no dependency on core/plugin (ruling 14).
  - `app/.../shell/ShellOverlays.kt`: `OverlayState.fullScreen`, and `ToastMessage.action`.
  - `app/.../shell/ui/OverlayLayers.kt`: `OverlayLayer` full-screen; `ToastLayer` timing and action.
  - `app/.../MainActivity.kt:224`.
- Test:
  - `app/src/test/.../shell/ui/OverlayLayersTest.kt`, `ShellOverlaysTest` (or the test holding `ShellToasts`);
  - `core/ui/src/test/.../ControlsTest.kt` and `FormPiecesScreenshotTest.kt`;
  - `capability/calendar/src/test/.../RecordingToaster.kt` and `ui/RecordingOverlay.kt` (they record both).

**Interfaces:**
- Produces:
  - `fun OverlayHost.showFullScreen(content: @Composable () -> Unit)`, an interface member with default `show(content = content)`.
  - `data class ToastAction(val label: String, val onClick: () -> Unit)`.
  - `fun Toaster.show(message: String, icon: String = TOAST_ICON_INFO, urgent: Boolean = false, action: ToastAction)`, an interface member with default `show(message, icon, urgent)`.
  - `const val TOAST_WITH_ACTION_MS = 8_000L`.
  - `OverlayState.fullScreen: Boolean`, reset by `show` and `dismiss`.
  - The toast's button is tagged `toast_action`.

- [ ] **Step 1: The failing tests.** Each UI test ends with `assertClickablesHaveRoleAndLabel()`.
  - `OverlayLayersTest.aFullScreenOverlayCoversTheRailAndHasNoScrim`: after `showFullScreen`, the content's bounds are the whole window (1280 × 800), there is no `overlay_scrim`, and a tap at (10, 400) dp doesn't dismiss it.
  - `OverlayLayersTest.aFullScreenOverlayStillClosesWhenIdle`: unedited, it goes at 120 s (checked at 120 s − 1 frame and + 1 frame); edited, the `sheet_warning` shows at 580 s and it goes at 600 s.
  - `OverlayLayersTest.aSheetIsUnchanged` (**pin**): `show` keeps the scrim and the right edge.
  - `OverlayLayersTest.thePinPadCoversTheWholeScreenOverAFullScreenOverlay`: the activity's pin pad host gets `overSheet = false` while the full-screen overlay shows. (Tested through the `overSheet` expression extracted as `internal fun pinOverSheet(overlay: OverlayState): Boolean`.)
  - `OverlayLayersTest.aToastWithAnActionStaysEightSeconds`: under `mainClock`, `toast` is shown at 7 984 ms and gone at 8 016 ms, whatever its word count.
  - `OverlayLayersTest.tappingTheActionRunsItAndHidesTheToast`: `toast_action` "Undo" is a `Role.Button` 48 dp tall; a tap calls `onClick` once and the toast is gone.
  - `ControlsTest.aToastWithoutAnActionIsUnchanged` (**pin**, beside the existing `HhToast` tests).
  - `ShellToastsTest.aNewToastReplacesOneWithAnAction`: a plain toast after an action toast drops the action.

  Run `./gradlew :app:testDebugUnitTest --tests '*OverlayLayers*' --tests '*ShellToasts*' :core:ui:testDebugUnitTest --tests '*ControlsTest*'`. Expected: FAIL to compile.
- [ ] **Step 2: Implement** per rulings 13–14. `ToastLayer` waits `activity.timeout(TOAST_WITH_ACTION_MS, hasControls = true)` for a toast with an action.
- [ ] **Step 3: Images.**
  - Predicted: new `toast_action_dark`, `toast_action_light`, recorded in core/ui's `FormPiecesScreenshotTest`. No existing image changes.
  - Check against handoff 6g's toast, keeping the shared look (ruling 14).
```bash
git add core/plugin core/ui app capability/calendar
git commit -m "Let an overlay take the whole screen, rail included, and let a toast carry one action for eight seconds"
```

---

### Task 7: `EditorForm`: who, day and range, time and length, what's missing (spec §3.1, §3.4; rulings 15–19, 23; Review Focus 3)

**Review:** opus.

**Files:**
- Create: `capability/calendar/.../EditorForm.kt`; `capability/calendar/src/test/.../EditorFormTest.kt` (Europe/London, today Wed 23 Sep 2026 10:00 as `EventFormTest`'s; ranges on Mon 19 Oct 2026).
- Modify: nothing else (the old `EventForm` stays until Task 16).

**Interfaces:**
- Consumes: `TimeChoice`, `MAX_SPAN_DAYS`, `UNTIL_TOO_LATE`, `MAX_TITLE_LENGTH`, `lengthLabel` (`EventForm.kt`); `RepeatChoice`, `RepeatEnd.Never`/`After`, `recurrenceRule`, `MIN_TIMES`/`MAX_TIMES`/`DEFAULT_TIMES` (`Recurrence.kt`); `tagFor`, `peopleIn` (Task 1); `toggled` (Task 4); `EditableEvent`, `EventDraft`, `EventField`, `daySpan`, `whenLabel`.
- Produces, in `EditorForm.kt`:
  - `class EditorForm(mode: EventForm.Mode, today: LocalDate, now: LocalTime, zone: ZoneId, signedIn: PersonId?, childOnly: PersonId?, preselectedDay: LocalDate?, order: List<PersonId>, repeats: Boolean = true)`. It reuses `EventForm.Mode` until Task 16 moves `Mode` here.
  - `var title` and `fun updateTitle(value)`, as `EventForm`.
  - `var who: List<PersonId>` (private set): empty for an adult's new event; `[childOnly]` for a child's. `fun toggleWho(person: PersonId)` uses `toggled(who, person, mayClear = true)`; the pane's Next and the reason handle an empty set. `val everyone: Boolean`.
  - `var day: LocalDate?`, `var until: LocalDate?`, `var multiDay: Boolean`, `val awaitingLastDay: Boolean`; `fun setMultiDay(on: Boolean)`, `fun tapDay(date: LocalDate)` (ruling 18); `val untilError: String?` (`UNTIL_TOO_LATE` or null).
  - `var time: TimeChoice`, `var length: java.time.Duration`, `val startTime: LocalTime?`, `var endError: String?`; `fun chooseAllDay()`, `fun chooseHour(hour: Int)`, `fun chooseMinute(minute: Int)`, `fun chooseTime(time: LocalTime)`, `fun chooseLength(length: Duration)`, `fun chooseEndTime(end: LocalTime)`, `val untilTimeLabel: String` ("Until…" until an end is picked, then "Until 18:30").
  - `val EDITOR_LENGTHS = listOf(Duration.ofMinutes(30), Duration.ofMinutes(45), Duration.ofHours(1), Duration.ofHours(2))` (`java.time.Duration`, as `EventForm`); `fun lengthChip(length: Duration): String` gives "30 min", "45 min", "1 hour", "2 hours".
  - `internal const val END_BEFORE_START = "The end is before the start. Pick a later time."`.
  - `val mode: EventForm.Mode`.
  - `val pickerTime: LocalTime`: the start picker's initial, which is the chosen time, else the next whole hour (as `EventForm.pickerTime`).
  - `val endPickerTime: LocalTime`: the end picker's initial, which is the start plus the length.
  - `val datesLocked`, `fun lockedDatesLabel(service)`, `val canRepeat`, `var repeat`, `var repeatEnd`, `fun chooseRepeat`, `fun stopsAfter()`, `fun keepsGoing()`, `fun fewerTimes()`, `fun moreTimes()`. They carry over from `EventForm`, with `stopsAfter` as 9c-1's `chooseAfter` and `keepsGoing` as `chooseEnd(Never)`.
  - `enum class Needed { Title, Who, Day }`, `val missing: List<Needed>`, `fun needsLine(missing: List<Needed>): String?`, `val reason: String?`, `val canSave: Boolean get() = reason == null`.
  - `val edited`, `val touched`, `val unchanged`, `fun draft(createdBy: String?): EventDraft` (requires `canSave`).

- [ ] **Step 1: The failing tests** (`EditorFormTest`). The ports of 9c-1's `EventFormTest` cases, renamed to the new members, are listed with the new ones.
  - **Who:**
    - `anAdultsNewEventStartsWithNobody`: `who` is empty and `missing` has `Who`.
    - `aChildsNewEventStartsAsThemselves`: `who == [mia]`.
    - `toggleWhoKeepsTheHouseholdOrder`: mia then alex gives `[alex, mia]`.
    - `everyoneClearsThePeopleAndAPersonClearsEveryone`.
    - `theLastPersonCanBeClearedInTheForm`: from `[sam]`, `toggleWho(sam)` gives `[]` and `Needed.Who`. The detail sheet's chips can't do this (Task 4).
    - `anEditOpensOnItsSetWithRemovedPeopleDropped`: an event tagged `"sam,gone"` opens with `[sam]`.
    - `anUntaggedEditOpensAsItsCalendarsPerson`: an untagged event on a calendar mapped to Alex opens with `[alex]`, and its untouched draft keeps `forPerson == null`. On an unmapped calendar it opens as Family.
    - `anUntouchedWhoKeepsTheTagExactly`: the untagged edit's draft has `forPerson == null`.
    - `theDraftCarriesTheSetsTag`: `[mia, sam]` gives `"sam,mia"`.
  - **Day:**
    - `theWeeksAddEventStartsWithNoDay` (spec §5 call 9): `preselectedDay = null` gives `day == null` and `Needed.Day`.
    - `anEmptyColumnStartsOnItsDay`.
    - `aOneDayTapSetsTheDay`.
  - **Range** (ruling 18):
    - `turningOnMultiDayForcesAllDay`: a timed 18:00 form, `setMultiDay(true)` gives `time == AllDay`; `setMultiDay(false)` with the same Day restores 18:00.
    - `theFirstTapWaitsForTheLast`: tap Mon 19 gives `day == Mon 19`, `until == null`, `awaitingLastDay`.
    - `theSecondTapSetsTheLastDay`: then Thu 22 gives `until == Thu 22` and `!awaitingLastDay`, and a draft `AllDay(Mon 19)` → `AllDay(Fri 23)`.
    - `aSecondTapOnTheSameDayIsOneDay`.
    - `aTapBeforeTheFirstStartsAgain`: Mon 19, then Fri 16, gives `day == Fri 16` and still awaiting.
    - `aTapAfterARangeStartsANewOne`.
    - `aRangeAcrossAMonthEndsTheDayAfterItsLastDay` (Review Focus 3): Thu 29 Oct then Sun 1 Nov gives `AllDay(29 Oct)` → `AllDay(2 Nov)`.
    - `theRangeKeepsTheSixtyDayCap` (Review Focus 3): Mon 19 + 59 gives `untilError == null`; + 60 gives `UNTIL_TOO_LATE`, `reason == UNTIL_TOO_LATE` (with title and who set) and `!canSave`.
    - `turningMultiDayOffDropsUntil`.
    - `aRangeAcrossTheYearEnd`: Wed 30 Dec to Sat 2 Jan ends Sun 3 Jan 2027.
  - **Time** (ruling 19):
    - `anHourSetsItOnTheHour`: `chooseHour(10)` gives `Custom(10:00)`, length 1 hour.
    - `aMinuteKeepsTheHour`: then `chooseMinute(45)` gives 10:45; `chooseHour(14)` gives 14:45.
    - `aMinuteWhileAllDayIsIgnored`.
    - `thePickersStartWhereTheFormIs`: on New at 09:20, `pickerTime` is 10:00. After `chooseHour(14)` with 45 minutes, `pickerTime` is 14:00 and `endPickerTime` is 14:45. `mode` is the one given.
    - `otherTimeSetsAnyTime`: `chooseTime(06:30)`.
    - `theLengthsAreTheFour`: `EDITOR_LENGTHS` chips read "30 min", "45 min", "1 hour", "2 hours".
    - `anEndTimeSetsTheLength`: 10:00, then `chooseEndTime(11:20)`, gives length 80 minutes and `untilTimeLabel == "Until 11:20"`.
    - `anEndAtOrBeforeTheStartIsRefused`: `chooseEndTime(09:30)` and `(10:00)` leave the length and set `endError == END_BEFORE_START`. `reason` stays null and `canSave` stays true, because the draft is unchanged. `chooseLength(30 min)` clears the error.
    - `anEditsOwnLengthShowsAsUntil`: a 90-minute edit gives "Until {end}".
    - `aTimedEventStillEndsByMidnight` (port): 23:00 for 2 hours ends 00:00 the next day.
  - **Missing and reason** (ruling 23):
    - `needsLineNamesWhatIsMissing`: each of the seven combinations gives its line exactly, and nothing missing gives null.
    - `theReasonIsNeedsFirst`: missing Day plus the cap gives "Needs a day".
  - **Edited and touched** (ruling 15, ruling 16):
    - `choosingOnlyWhoOrADayIsEdited`: on a fresh form, each answer on its own makes `edited` true: Who, Day, multi-day on, the last day, an hour, a length, a repeat. A minute counts only after an hour: a minute while All day is ignored, so it isn't an edit.
    - `aReorderedSetIsNotTouched`: an edit stored as `"mia,sam"` (an old household order), with Sam ticked off and on again, has `touched` without `FOR_PERSON` and is `unchanged`.
    - `everyoneAndAPersonNeverMix`: Everyone then Sam gives `[sam]`; Sam then Everyone gives `[FAMILY]`; neither draft throws in `tagFor`.
  - **Ported from `EventFormTest`** (same assertions, new members):
    - `aTitleIsAtMostAHundredCharacters`;
    - `aTimedSpanStaysLocked` and `aMixedAllDayAndTimedEventStaysLocked`;
    - `anAllDaySpanOpensWithItsFirstAndLastDays` (`multiDay == true`, `until == Fri 23`);
    - `anUntouchedAllDaySpanIsUnchanged`;
    - `movingTheLastDayOnAnEditTouchesOnlyTheTimes`;
    - `anEditWhoseTimeWasChosenThenAllDayAgainIsUnchanged`;
    - `aChildsFormNeverRepeats`;
    - `theCountStaysBetweenTwoAndFiftyTwo`;
    - `typingThenClearingIsNotEdited`;
    - `anUnchangedEditIsUnchanged`.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*EditorFormTest*'`. Expected: FAIL to compile.
- [ ] **Step 2: Implement** per the Interfaces and rulings 15–19, 23. `draft`'s rule comes from `recurrenceRule(repeat, repeatEnd, allDay, zone)` (Task 8 adds the day).
- [ ] **Step 3: The gate and commit.** Expected: PASS, **no image changed**.
```bash
git add capability/calendar
git commit -m "Add the new editor's form: several people, a day that starts empty, a range of days, hours and minutes, and a reason that says what's missing"
```

---

### Task 8: Repeats worded from the day: nth weekday, the count's last date, the read-back (spec §3.1, §3.4; rulings 33–35)

**Review:** opus.

**Files:**
- Modify:
  - `Recurrence.kt`: `RepeatChoice.MonthlyOnWeekday`, `wording`, `EDITOR_REPEATS`, `occurrences`, and `recurrenceRule(…, day: LocalDate)`. The old sheet's call (`EventForm.recurrence`) passes its `day`.
  - `Repeats.kt`: `repeatsLabel` per ruling 35.
  - `EditorForm.kt`: `stepperLabel`, `repeatsValue`, the day in the rule.
  - `provider/calendar-fake/.../RecurrenceExpander.kt`: `BYDAY=nXX` for MONTHLY.
  - The series' first date kept with the rule (ruling 35): `provider/calendar-google/.../GoogleCalendarProvider.kt` (`ruleOf` prefixes `DTSTART:` from the master's start) and `provider/calendar-fake/.../FakeCalendarProvider.kt` (the same from its series). Check every reader of `recurrenceRule` (`grep -rn "recurrenceRule" --include=*.kt`) skips the line.
  - `ui/SampleUi.kt:165/:172`: the hard-coded `repeats = "Every week"` becomes the new words ("Every Thursday"), so `detail_recurring_` and `EventDetailSheetTest.kt:107/:128` move with it.
- Test:
  - `RecurrenceTest.kt` (`:25`'s list of `RepeatChoice.entries` labels gains `MonthlyOnWeekday`), `RepeatsTest.kt` (its expectations move to the editor's words), `EditorFormTest.kt`, `CalendarRepositoryTest.kt:599` (Piano reads "Every {weekday}"), `ui/EventDetailSheetTest.kt`, `ui/DetailScreenshotTest.kt`;
  - `provider/calendar-fake/src/test/.../RecurrenceExpanderTest.kt`, `FakeCalendarProviderTest.kt` (`:366`/`:374` pass the day to `recurrenceRule`);
  - `provider/calendar-google/src/test/.../GoogleWriteTest.kt`, `GoogleReadTest.kt`.

**Interfaces:**
- Produces:
  - `RepeatChoice.MonthlyOnWeekday("Every month", "MONTHLY", 1)`. Its `label` is unused by the new editor; the old sheet doesn't list it.
  - `fun RepeatChoice.wording(day: LocalDate): String` (ruling 33).
  - `val EDITOR_REPEATS: List<RepeatChoice>`.
  - `fun nthWeekday(day: LocalDate): Int` (1–4, or −1 for a 5th).
  - `fun ordinal(n: Int): String` ("1st", "2nd", "3rd", "4th", "last" for −1; and "21st", "22nd", "23rd", "11th"–"13th" for days of the month).
  - `fun recurrenceRule(choice: RepeatChoice, end: RepeatEnd, allDay: Boolean, zone: ZoneId, day: LocalDate): String?`. `MonthlyOnWeekday` writes `;BYDAY={n}{XX}` before the end; the others are unchanged.
  - `fun occurrences(choice: RepeatChoice, day: LocalDate, count: Int): List<LocalDate>`: the one source of the last date (ruling 34).
  - `fun seriesStart(rule: String?): LocalDate?` and `fun choiceOf(rule: String, start: LocalDate): RepeatChoice?` (the rule parsed back, null for anything the editor can't make).
  - In `EditorForm`: `val stepperLabel: String?` ("12 times · last on Sat 12 Dec" while `After`) and `val repeatsValue: String` ("Doesn’t repeat", or the wording, plus " · 12 times").
  - `repeatsLabel(rule, start, zone)` per ruling 35. It first tries `choiceOf(rule, seriesStart ?: start)?.wording(…)`, so the editor's words are built in one place. Only monthly by date, other intervals and "Yes" are its own branches.

- [ ] **Step 1: The failing tests.**
  - `RecurrenceTest`:
    - `theWordingFollowsTheDay`: for Sat 26 Sep 2026 the six read "Doesn’t repeat", "Every Saturday", "Every 2 weeks, on Saturday", "Every month, on the 4th Saturday", "Every year, on 26 September", "Every day".
    - `aFifthWeekdayIsTheLast`: Thu 29 Oct 2026 reads "Every month, on the last Thursday" and writes `BYDAY=-1TH`.
    - `theFourthWeekdayIsFourthEvenWhenItIsAlsoTheLast`: Sat 26 Sep gives `BYDAY=4SA`.
    - `aMonthlyOnWeekdayRule`: `"RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=4SA;COUNT=12"`.
    - `occurrencesFollowTheRule`:
      - weekly from Sat 26 Sep, 12 gives Sat 12 Dec last;
      - monthly 4th Saturday from 26 Sep, 3 gives 26 Sep, 24 Oct, 28 Nov;
      - last Thursday from 29 Oct, 3 gives 29 Oct, 26 Nov, 31 Dec;
      - yearly from Tue 29 Feb 2028 (`LocalDate.of(2028, 2, 29)`), 2 gives 29 Feb 2028 and 29 Feb 2032;
      - daily 3 gives three days.
    - `neverBothUntilAndCountForAnyRepeatEnd` (port, with the day).
  - `EditorFormTest`:
    - `theStepperSaysTheCountAndTheLastDate`: Sat 26 Sep, weekly, `stopsAfter()` and `moreTimes()` twice gives "12 times · last on Sat 12 Dec".
    - `changingTheDayRewordsTheRepeat`: monthly on Sat 26 Sep, then the day Thu 29 Oct, gives a rule with `BYDAY=-1TH`.
    - `theRepeatsRowValue`: "Doesn’t repeat"; "Every Saturday"; "Every Saturday · 12 times".
  - `RepeatsTest` (the read-back, ruling 35):
    - `"RRULE:FREQ=WEEKLY"` on a Saturday reads "Every Saturday";
    - `"RRULE:FREQ=WEEKLY;BYDAY=SA"` reads the same;
    - `INTERVAL=2` reads "Every 2 weeks, on Saturday";
    - `"RRULE:FREQ=MONTHLY;BYDAY=4SA"` reads "Every month, on the 4th Saturday";
    - `BYDAY=-1TH` reads "Every month, on the last Thursday";
    - `"RRULE:FREQ=MONTHLY;BYMONTHDAY=22"` reads "Every month, on the 22nd";
    - YEARLY on 26 Sep reads "Every year, on 26 September";
    - DAILY reads "Every day";
    - `"DTSTART:20260926
RRULE:FREQ=WEEKLY;COUNT=12"` adds " · 12 times · last on Sat 12 Dec", read through `occurrences`;
    - `COUNT=12` with no `DTSTART` line adds " · 12 times";
    - `UNTIL=20261212` adds " · until Sat 12 Dec";
    - `UNTIL=20261212T095959Z` (Google's timed form) reads " · until Sat 12 Dec" in Europe/London, not a day off;
    - `INTERVAL=3` monthly reads "Every 3 months";
    - `BYDAY=MO,WE` reads "Yes";
    - `theReadBackAgreesWithTheEditor`: for every `EDITOR_REPEATS` entry except Never, on Sat 26 Sep, `repeatsLabel(recurrenceRule(choice, Never, false, zone, day), day, zone)` equals `choice.wording(day)`.
  - `RecurrenceExpanderTest`:
    - `aMonthlyNthWeekdayFollowsTheWeekday`: from Sat 26 Sep 2026, `BYDAY=4SA;COUNT=3` gives 26 Sep, 24 Oct, 28 Nov at the same local time.
    - `theLastWeekdayIsTheLastInEachMonth`: `BYDAY=-1TH`.
    - `aCountedNthSeriesStopsAtItsCount`.
    - `theExpanderAgreesWithOccurrences` (property test, risk of drift between the two): for each of `EDITOR_REPEATS` except Never; start days 22–31 Oct 2026, 29 Feb 2028, and a 10:00 timed start that crosses the 25 Oct clocks change; COUNT 2, 10 and 52: the fake's expansion of `recurrenceRule(choice, After(n), allDay, zone, day)` equals `occurrences(choice, day, n)` as local dates.
  - `FakeCalendarProviderTest.aSeriesKeepsItsFirstDateWithItsRule`: a created weekly series syncs back with `seriesStart(rule) == ` its first date on every occurrence.
  - `GoogleReadTest.aSeriesRuleCarriesItsFirstDate`: an instance whose master starts 26 Sep reads a rule beginning `DTSTART:20260926`. A timed master at 23:30 on 26 Sep in Europe/London, which Google gives as `2026-09-26T22:30:00Z`, also reads `DTSTART:20260926`.
  - `GoogleWriteTest.aMonthlyNthSeriesSendsItsRule` (**pin**): the insert's `recurrence` holds the `BYDAY=4SA` line and no `DTSTART` line.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*RecurrenceTest*' --tests '*RepeatsTest*' --tests '*EditorFormTest*' --tests '*EventFormTest*' :provider:calendar-fake:testDebugUnitTest --tests '*RecurrenceExpander*' :provider:calendar-google:testDebugUnitTest --tests '*GoogleWriteTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 33–35. The expander finds the nth (or last) weekday of each month by stepping months from the series' first date.
- [ ] **Step 3: Images.**
  - Predicted: `detail_recurring_` (its Repeats row's words, from `SampleUi`).
  - Check against handoff 6f's wording.
```bash
git add capability/calendar provider/calendar-fake provider/calendar-google
git commit -m "Word repeats from the chosen day, repeat monthly on the nth weekday, show a count's last date, and read repeats back in the same words"
```

---

### Task 9: What the tablet holds: day dots, busy hours, the clash, the free starts, the Check words (spec §3.1, §3.4; rulings 30–32, 36; Review Focus 5)

**Review:** opus.

**Files:**
- Create: `capability/calendar/.../Held.kt`, `EditorWords.kt`; tests `HeldTest.kt`, `ClashTest.kt`, `EditorWordsTest.kt`.
- Modify: `CalendarRepository.kt` (`held` as a member, since `store`, `household`, `zone` and `catalog` are private), `CalendarRepositoryTest.kt`.

**Interfaces:**
- Consumes: `resolvePeople` (Task 1), `overlayPending`, `lastShownDay`, `EditorForm` (Tasks 7–8), `occurrences`.
- Produces, in `Held.kt`:
  - `data class HeldEvent(val ref: EventRef, val title: String, val people: List<PersonId>, val start: EventTime, val end: EventTime)`.
  - `data class Held(val events: List<HeldEvent>, val lastDay: LocalDate, val unhealthy: Set<PersonId>)` (ruling 30).
  - In `CalendarRepository`: `fun held(today: LocalDate): Flow<Held>`, built like `days()` (`overlayPending`, then `resolvePeople` with the household order), from today to `lastDay`.
  - `fun Held.dotsOn(date: LocalDate, colors: Map<PersonId, Long>, order: List<PersonId>): List<Long>`: up to 3 distinct colours of the people (Family included) with an event that day, in household order then Family; empty past `lastDay`.
  - `fun Held.busyHours(people: List<PersonId>, day: LocalDate, zone: ZoneId, except: EventRef?): Set<Int>`.
  - `sealed interface FreeCheck { data object None : FreeCheck; data class Free(val people: List<PersonId>) : FreeCheck; data class Busy(val person: PersonId, val event: HeldEvent, val freeStarts: List<LocalTime>) : FreeCheck }`.
  - `fun Held.check(people: List<PersonId>, day: LocalDate, start: LocalTime, length: java.time.Duration, zone: ZoneId, except: EventRef?, order: List<PersonId>): FreeCheck`, which is `None` for no people, for Everyone, past `lastDay`, or when any chosen person is in `unhealthy`. The checked span ends at midnight at most, as the draft caps it.
- Produces, in `EditorWords.kt`:
  - `fun freeLine(check: FreeCheck.Free, names: (PersonId) -> String): String` and `fun clashLine(check: FreeCheck.Busy, single: Boolean, names: (PersonId) -> String, zone: ZoneId): String` (ruling 32). Both join names with `spokenNames` (Task 1).
  - `fun freeAtLine(people: List<String>): String`, also through `spokenNames`; `const val CLASHES_ALLOWED_TAIL = "or keep it; clashes are allowed."`, `CLASHES_ALLOWED = "Clashes are allowed."`.
  - `fun whoValue(form, people): String`, `fun dayValue(form): String`, `fun timeValue(form): String` (row values).
  - `fun checkSentence(form: EditorForm, people: List<Person>): String` and `fun firstDates(form: EditorForm): Pair<List<LocalDate>, Int>` (four dates and how many more) (ruling 36).

- [ ] **Step 1: The failing tests.**
  - `ClashTest` (Europe/London; Mia's Piano Thu 1 Oct 15:30–16:15; today Wed 23 Sep; checks are 45 minutes, as frame 6i):
    - `anOverlapIsABusyHour`: `busyHours([mia], Thu 1 Oct)` is `{15, 16}`.
    - `allDayAndFamilyEventsMakeNobodyBusy` (Review Focus 5): an all-day event of Mia's and a Family timed event give no busy hour and `Free([mia])`.
    - `aSharedEventMakesEachOfItsPeopleBusy`: Sam + Mia's Cinema makes Sam busy.
    - `aCalendarsPersonIsBusyWithItsUntaggedEvents`: an untagged event on a calendar mapped to Mia counts for Mia.
    - `theClashNamesTheFirstOverlap`: 15:00 for 45 minutes gives `Busy(mia, Piano, …)`.
    - `theFreeStartsAreTheNearestBeforeAndAfter`: 15:00 for 45 minutes gives free starts 14:45 and 16:15.
    - `aMultiDayTimedEventMakesTheDaysBetweenBusy`: a timed event Fri 18:00 – Sun 16:00 makes every Saturday hour busy.
    - `theCheckStopsAtMidnight`: 22:30 for 2 hours checks only to 00:00, so an event at 00:15 the next day isn't a clash.
    - `theClocksChangeKeepsLocalHours`: on Sun 25 Oct 2026 an event 10:00–11:00 local marks hour 10 only.
    - `noFreeStartBeforeSevenOrPastEleven`: the whole span must lie within 07:00–23:00. A day busy 07:00–23:00 gives none, and so does a 2-hour check on a day busy 07:00–21:30.
    - `severalPeopleNameTheBusyOneInHouseholdOrder`: Sam and Mia both busy names Sam.
    - `everyoneChecksNobody`: `[FAMILY]` gives `None`.
    - `aDayPastTheHeldWindowHasNoLine` (Review Focus 5): on `lastDay + 1`, with a Piano there in the data, `check` is `None` and `busyHours` is empty. On `lastDay` it is `Busy`.
    - `theEventBeingChangedDoesNotClashWithItself` (Review Focus 5): `except = piano.ref` at 15:45 gives `Free`.
    - `aPersonWhoseCalendarNeedsSignInGetsNoLine` (Review Focus 5): with Mia in `unhealthy`, a day with no events of hers gives `None`, not `Free`.
  - `HeldTest`:
    - `dotsAreUpToThreeInHouseholdOrder`: four people with events give three dots, Alex, Sam, Mia.
    - `aSharedEventGivesEachPersonADot`.
    - `noDotsPastTheHeldWindow`.
  - `CalendarRepositoryTest.heldCoversTodayToTheLastShownDayWithQueuedChanges`: a queued create shows, a queued delete hides, and an event on `lastShownDay + 1` isn't there.
  - `CalendarRepositoryTest.aStaleSyncHoldsFewerDays` (Review Focus 5): with the connection's last successful sync five days ago, `lastDay` is that sync's day + `SYNC_FUTURE_DAYS` (today + 23), four days short of `lastShownDay`.
  - `CalendarRepositoryTest.anUnhealthyConnectionMarksItsPeople`: a connection needing sign-in puts the person mapped to its calendar in `unhealthy`; a healthy one doesn't.
  - `EditorWordsTest`:
    - `theRowValues`: "Choose who", "Sam + Mia", "Everyone"; "Choose a day", "Sat 26 Sep", "Thu 29 Oct – Sun 1 Nov"; "All day", "10:00–10:45".
    - `theFreeAndClashLines`: "Mia is free then", "Sam and Mia are free then", "Overlaps Piano, 15:30–16:15", "Sam has Dentist, 12:30–13:00", "Mia is free at", "Sam and Mia are free at".
    - `theCheckSentence`:
      - "Guitar lesson for Mia, every Saturday, 10:00–10:45, from 26 Sep to 12 Dec. 12 times in all." (handoff 6g exactly);
      - "Guitar lesson for Mia, Sat 26 Sep, 10:00–10:45.";
      - "Half term for Sam and Mia, Mon 19 – Fri 23 Oct, all day.";
      - "Bins for everyone, every Monday, all day, from 28 Sep.".
    - `theFirstFewDates`: 12 weekly gives four dates and 8 more; a series that keeps going gives four and 0; a one-off gives one and 0.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*ClashTest*' --tests '*HeldTest*' --tests '*EditorWordsTest*' --tests '*CalendarRepositoryTest*'`. Expected: FAIL to compile.
- [ ] **Step 2: Implement** per rulings 30–32 and 36. Free starts step by 15 minutes outward from the chosen start, the earlier first, and stop at the first free one each way.
- [ ] **Step 3: The gate and commit.** Expected: PASS, **no image changed**.
```bash
git add capability/calendar
git commit -m "Work out from the days the tablet holds who is busy when, the nearest free times, the day dots, and the words the new editor uses"
```

---

### Task 10: The editor's frame: top bar, rows and their states, the foot, discard, keyboard up (handoff §1 Layout, 6a; rulings 20–28)

**Review:** sonnet.

**Files:**
- Create:
  - `ui/EventEditorScreen.kt`: `EventEditorScreen`, the top bar, the left column and the foot.
  - `ui/EditorRows.kt`: `EditorRow`, `RowState`, the tag pill.
  - `ui/EditorFlow.kt`: `EditorStep`, `EditorFlow`, `MOVE_ON_MS`.
  - `ui/EditorPane.kt`: the pane's frame, with the question, Skip and Next.
  - Tests `ui/EventEditorScreenTest.kt` and `ui/AddEventScreenshotTest.kt`.
- Modify:
  - `core/ui/.../Buttons.kt`: `HhFooterButton(…, below: Boolean = false, hint: String? = null)`, where the reason or hint goes under the button with the `info` icon.
  - `ui/CalendarType.kt`: `EditorDimens`, with the Units values.
  - `CalendarRepository.kt`: `masterCalendarName: Flow<String?>`.
  - Icons: `event_note`, and `group` (the Who row's icon).
  - `ui/SampleUi.kt` / `ui/EditForms.kt`: `editorForm(...)` fixtures.
- Test: `core/ui/src/test/.../ButtonsTest.kt`.

**Interfaces:**
- Consumes: `EditorForm` (7–8), `EditorWords` (9), `HhFooterButton`, `HhConfirmCard`, `HhCloseButton`, `HhTextField`, `DeleteButton`, `SaveFailure`.
- Produces:
  - `enum class EditorStep { Who, Day, Time, Repeats, Check }`.
  - `class EditorFlow(steps: List<EditorStep>, initial: EditorStep)`, with `var active`, `fun open(step)`, `fun next()`, `fun moveOnSoon()` (moves after `MOVE_ON_MS` unless `active` changes first), `val nextStep: EditorStep?`, and `fun nextLabel(): String` ("Next: Day"…).
  - `internal const val MOVE_ON_MS = 250L`.
  - `@Composable internal fun EventEditorScreen(form: EditorForm, flow: EditorFlow, members: List<Member>, context: String, savesTo: String?, busy: Boolean, failure: SaveFailure?, picker: EditorPicker, childOnly: PersonId?, childName: String?, held: Held?, onClose: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit, onRefusedWho: () -> Unit, onPicker: (EditorPicker) -> Unit, pane: @Composable (EditorStep) -> Unit = { EditorPaneFor(it, …) })`.
  - Tags: `editor_screen`, `editor_title`, `editor_row_{who|day|time|repeats}` (with `_value` and `_tag`), `editor_saves_to`, `editor_save`, `editor_save_reason`, `editor_save_hint`, `editor_delete`, `editor_discard`, `editor_discard_keep`, `editor_discard_confirm`, `editor_pane`, `editor_question`, `editor_next`, `editor_skip`.
  - `internal const val DISCARD_NEW = "Discard this event?"`, `DISCARD_CHANGES = "Discard your changes?"`, `KEEP_EDITING = "Keep editing"`, `DISCARD = "Discard"`.
  - `fun savesToLine(name: String): String` (ruling 25). The host passes the master's name on New and the event's `sourceName` on Change.
  - The panes take what they need from these: the people are `members.map { it.person }`, and the event a clash check skips is derived from `form.mode` (an Edit's ref), not passed.
  - `Icons.EVENT_NOTE`, `Icons.GROUP`.
- Until Tasks 11–14, `EditorPaneFor` draws each pane's question with an empty body.

- [ ] **Step 1: The failing tests.** These are in `EventEditorScreenTest` at 1280 × 800 dp; each ends with `assertClickablesHaveRoleAndLabel()`.
  - `theTopBarHasCloseTitleAndContextOnly`:
    - `sheet_close` is 48 dp at 32 dp from the left;
    - "New event" is a heading;
    - the context reads "From Calendar · Alex signed in";
    - nothing else in the bar is clickable (no Add event there).
  - `theColumnsAreAsDrawn`: the left column is 507 dp wide, the pane starts 21 dp after it, and the body's bottom is 29 dp above the window's.
  - `aNewEventFocusesTheTitleWithWhoActive` (ruling 22): `editor_title` is focused, and `editor_row_who` is selected, with the `accentSoft` fill (`pixelAt`) and an `accent` icon.
  - `theRowsShowTheirStates`:
    - Who is not yet set, so its value is "Choose who" in `mute` with a `mute` icon;
    - after a Day is set, the Day row is done: `surf` fill, `accent` icon, value in `ink`;
    - the rows are 77 dp tall and 9 dp apart.
  - `tappingARowOpensItsPane`: a tap on `editor_row_time` makes it active and `editor_question` reads "What time?".
  - `nextMovesOnAndIsNeverGreen`: `editor_next` reads "Next: Day" on Who, and its fill is `surf2`; a tap makes Day active.
  - `skipShowsOnTimeAndRepeatsOnly`.
  - `aChildHasNoRepeatsRowAndAnEditHasNone` (ruling 21).
  - `moveOnSoonWaitsAQuarterSecond` (ruling 20): under `mainClock`, after `flow.moveOnSoon()` on Day, Day is still active at 234 ms and Time at 266 ms. A row tap at 100 ms cancels the move.
  - `theNewQuestionIsAPaneTitle` (ruling 20): after the move, `editor_question` has `SemanticsProperties.PaneTitle` "What time?".
  - `addEventIsTheOnlyGreenButton`: across the screen only `editor_save` has an `accent` fill, and only once it can save.
  - `theFootSaysWhatIsMissing` (ruling 23):
    - empty, `editor_save` is disabled with `editor_save_reason` "Needs a title, who and a day" **under** it and the `info` icon;
    - with a title and who: "Needs a day";
    - all three set, the button is enabled with `editor_save_hint` "Time and repeats are optional".
  - `theFootSaysWhereItSaves`: `editor_saves_to` reads "Saves to the Family calendar" with `event_note` and no "Change".
  - `changeEventSaysItsOwnCalendar` (ruling 25): an edit of an event whose source is named "Sam’s calendar" reads "Saves to the Sam’s calendar", not the master's name.
  - `changeEventSaysSaveChangesAndOffersDelete`: an edit shows "Change event", `editor_save` "Save changes" and `editor_delete` on its left; it opens on Check with nothing focused.
  - `closingUnchangedClosesAtOnce`: ✕ calls `onClose` with no card.
  - `closingWithChangesAsksToDiscard` (spec §3.1): after typing, ✕ shows `editor_discard` "Discard this event?" in place of the foot. "Keep editing" hides it; "Discard" calls `onClose`. On an edit the card reads "Discard your changes?", and its Discard shows `close`, not the bin (ruling 26).
  - `withTheKeyboardUpTheRowsShrinkAndTheFootGoes` (ruling 27): with a 320 dp IME, the rows are 61 dp, have no labels, and read "Who", "Day", "All day", "Doesn’t repeat"; there is no `editor_save`.
  - `nothingIsCutWithTheKeyboardUp` (ruling 27): at font scale 1.0 and 1.3 with a 320 dp IME, every row's bottom is above the IME's top, and no row value overflows beyond its one-line ellipsis (`didOverflowHeight || isLineEllipsized(0)` on the value's `TextLayoutResult`, as `FontScaleTest.kt:170`).
  - `aLongValueEllipsisesOnOneLineAtTheDefaultScale`: at font scale 1.0, a locked span's long Day value keeps one line, ending "…".
  - `theKeyboardsNextMovesToWho` (spec §2 ruling 2): `performImeAction` on `editor_title` clears focus and Who is active.
  - `theFailureCardShowsAboveTheFoot` (port of 9c-1's): with a `SaveFailure`, the card shows and Save reads "Try again".
  - `ButtonsTest.aFooterReasonCanSitBelow`: with `below = true`, `x_reason` sits 9 dp under the button with the `info` icon; a `hint` shows while enabled.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*EventEditorScreenTest*' :core:ui:testDebugUnitTest --tests '*ButtonsTest*' --tests '*IconFont*'`. Expected: FAIL to compile.
- [ ] **Step 2: Implement** per rulings 20–28 and Units. The screen sits in a `Box(Modifier.fillMaxSize().background(bg).windowInsetsPadding(ime))`. The left column is a `Column` with the rows on top and the foot pushed down with `weight(1f)` space. The pane is a `Column` that scrolls itself.
- [ ] **Step 3: Images.**
  - New: `add_event_start_dark`, `add_event_start_light` (empty, Who active), `add_event_keyboard_dark` (6a, keyboard up, 320 dp), `change_event_dark` (Check active, its pane empty until Task 14).
  - No existing image changes.
  - Check against handoff §1 Layout and 6a.
```bash
git add capability/calendar core/ui tools/fonts
git commit -m "Add the full-screen editor's frame: the event so far on the left with its rows and one Add event button, the question on the right, and a check before losing changes"
```

---

### Task 11: The Who pane (handoff 6b; spec §3.1, §3.4; ruling 16; Review Focus 4)

**Review:** opus.

**Files:**
- Create: `ui/WhoPane.kt`; test `ui/WhoPaneTest.kt`.
- Modify:
  - `core/household/.../Model.kt`: `val Role.label: String`, which replaces `roleLabel` at `app/.../ShellViewModel.kt:249–251` and `roleName` at `core/setup/.../SetupCopy.kt:97–101`; their callers (`ShellViewModel.kt:147`, `core/setup/.../PeopleUi.kt:81`) call `role.label`, and `app/src/test/.../ShellViewModelTest.kt:363`'s `roleLabelsReadAsWords` moves to `RoleLabelTest`;
  - `CalendarRepository.kt`: `members: Flow<List<Member>>`;
  - `ui/EditorPane.kt`: `EditorPaneFor(Who)`;
  - `ui/AddEventScreenshotTest.kt`;
  - Icons: `groups` (Everyone).

**Interfaces:**
- Consumes: `HhChoiceChip(locked)`'s rule (9c-1 ruling 8: one `locked` flag for paint and click), `EditorForm.toggleWho`, `cannotAddForOthers`.
- Produces:
  - `@Composable internal fun WhoPane(form: EditorForm, members: List<Member>, childOnly: PersonId?, childName: String?, busy: Boolean, onRefusedWho: () -> Unit)`.
  - Tags: `who_{name}`, `who_everyone`, `editor_who_only`.
  - `internal const val EVERYONE = "Everyone"`, `SHOWS_AS_FAMILY = "Shows as a Family event"`, `PICK_MORE_THAN_ONE = "Pick more than one person for a shared event."`.
  - `Icons.GROUPS`.

- [ ] **Step 1: The failing tests.** Each ends with `assertClickablesHaveRoleAndLabel()`.
  - `tilesAreThreeAcrossAsDrawn`: the tiles are 160 dp tall, 13 dp apart, three a row; each has a 64 dp circle with the initial, the name, and the role ("Adult", "Child").
  - `aTileTogglesAndSeveralCanBeChosen`: taps on Sam then Mia select both (`toggleable`, "chosen"); each selected tile's fill is its colour at 0x33 over `surf2` (hard-coded) with a 32 dp check badge; `editor_row_who_value` reads "Sam + Mia".
  - `everyoneClearsThePicks`: `who_everyone` selected gives `form.who == [FAMILY]`, the tiles unselected, and the row "Everyone".
  - `whoDoesNotMoveOnByItself` (ruling 20): after a tap, Who is still active at 500 ms.
  - `theHintSaysYouCanPickMore`.
  - `aChildsOtherTilesAndEveryoneAreLocked` (Review Focus 4): with Mia as a child:
    - Alex, Sam and Everyone have no fill (`pixelAt` is `surf`), a `lock` and their colour dot;
    - Mia's tile is selected;
    - `editor_who_only` reads "Mia can add events for Mia only.".
  - `aTapOnALockedTileExplainsAndChangesNothing` (Review Focus 4): a tap on Alex calls `onRefusedWho` once, and `form.who` stays `[mia]`.
  - `aChosenLockedTileStaysChosen` (Review Focus 4): Mia editing her own event that Alex retagged to Sam + Mia: Sam's tile is selected and locked, and a tap does nothing and calls no `onRefusedWho`.
  - `aChildsOwnTileInASharedEditIsLocked` (ruling 16): in the same edit, Mia's own tile is selected and locked, so the set can't become `[sam]`.
  - `busyFadesNoTile` (port).
  - `RoleLabelTest.theRolesWords` (core/household): "Admin", "Adult", "Child". It fails to compile until `Role.label` exists; the app and setup tests that read the words pass once their calls move.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*WhoPaneTest*' :core:household:testDebugUnitTest :core:setup:testDebugUnitTest :app:testDebugUnitTest --tests '*ShellViewModel*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per ruling 16 and Units.
- [ ] **Step 3: Images.**
  - New: `add_event_who_dark` (6b, Sam and Mia chosen), `add_event_who_child_dark`.
  - Also predicted: `add_event_start_*` and `add_event_keyboard_*`, because the Who pane now draws beside them.
  - Check against handoff 6b.
```bash
git add capability/calendar core/household core/setup app tools/fonts core/ui
git commit -m "Add the editor's Who pane: tiles that choose one or several people, Everyone for a Family event, and a child locked to themselves"
```

---

### Task 12: The Day pane: quick picks, the month, the range (handoff 6c, 6d; rulings 18, 29, 30; Review Focus 3)

**Review:** sonnet.

**Files:**
- Create: `ui/DayPane.kt`; test `ui/DayPaneTest.kt`.
- Modify: `ui/EditorPane.kt`, `ui/AddEventScreenshotTest.kt`, `core/ui/.../Colors.kt` (`HhAlpha.OUTSIDE_MONTH`).
- Test: `core/ui/src/test/.../ContrastTest.kt`.

**Interfaces:**
- Consumes: `EditorForm.tapDay`/`setMultiDay`/`untilError`, `Held.dotsOn`, `HhSwitchRow`, `HhRoundButton`, `HhChoiceChip`, `HhStatusLine(icon = ERROR, style = fieldError)`.
- Produces:
  - `@Composable internal fun DayPane(form: EditorForm, held: Held?, people: List<Person>, onPicked: () -> Unit)` (it calls `flow.moveOnSoon()` through `onPicked`);
  - `internal fun quickPicks(today: LocalDate): List<Pair<String, LocalDate>>`;
  - `internal fun monthCells(month: YearMonth): List<LocalDate>` (Monday first, 5 or 6 whole weeks);
  - tags `day_quick_{0..3}`, `month_title`, `month_prev`, `month_next`, `day_{yyyy-MM-dd}`, `day_multi`, `day_error`, `day_hint`;
  - `internal const val LASTS_MORE_THAN_A_DAY`, `TAP_FIRST_THEN_LAST`.

- [ ] **Step 1: The failing tests.** Each ends with `assertClickablesHaveRoleAndLabel()`; today is Wed 23 Sep 2026.
  - `quickPicksAreAsTheHandoffSays` (ruling 29): "Today", "Tomorrow", "Sat 26 Sep", "Wednesday next week"; on a Friday the weekend pick is Sun.
  - `theMonthStartsOnMonday`: `monthCells(Sep 2026)` starts Mon 31 Aug and ends Sun 4 Oct; the weekday letters read M T W T F S S.
  - `ContrastTest.outsideMonthDaysKeepTheirContrast` (core/ui), written first: it picks `HhAlpha.OUTSIDE_MONTH`'s value as the lowest that passes 4.5:1 in both themes, as `PAST_DAY` does.
  - `pastAndOutsideDaysFade`: each cell carries a test-only semantics key, `DayFade`, holding the alpha it draws at. Mon 21 Sep's is `PAST_DAY`, and Mon 31 Aug's is that chosen number, hard-coded. Past days are still tappable (any date is allowed).
  - `todayIsRingedAndTheChosenDayFilled`: Wed 23's ring pixel is `accent` and its date is W700; after a tap on Sat 26, its circle centre-edge pixel is `accent` with `accentInk` text.
  - `aPickMovesOnToTime` (ruling 20): a tap on Sat 26 sets the day, and Time is active at 266 ms.
  - `upToThreeDotsFromHeldEvents`: a day with four people's events shows three 5 dp dots; a day past `held.lastDay` shows none.
  - `theMonthPagesBothWays`: `month_next` shows October; `month_prev` from September shows August (past months allowed); they are named "Later months" and "Earlier months".
  - `theSwitchTurnsOnRangesAndTheQuestionSaysDays`: `day_multi` on makes the question "Which days?", shows `day_hint` "Tap the first day, then the last", and makes Time "All day".
  - `aRangeCrossesIntoTheNextMonth` (Review Focus 3): tap Thu 29 Oct, `month_next`, tap Sun 1 Nov. The row reads "Thu 29 Oct – Sun 1 Nov"; Time is active at 266 ms.
  - `theBandIsRoundedAtItsEndsAndWhereItWraps` (Review Focus 3): October with Thu 29 Oct – Sun 1 Nov shows:
    - the ends filled `accent`;
    - Fri 30 and Sat 31 banded `accentSoft` across the full cell (pixels at the cell's left and right edges);
    - the trailing Sun 1 Nov cell filled as an end.
    For Mon 26 Oct – Tue 3 Nov, the band's last cell on the Sun 1 Nov line is rounded at its right, and Mon 2 Nov's at its left.
  - `pastSixtyDaysTheLineSays`: + 60 shows `day_error` with `UNTIL_TOO_LATE` and the reason in the foot.
  - `theDayPaneFitsWithoutScrollingForASixWeekMonth`: August 2026 (six lines) at 1280 × 800: the pane's `VerticalScrollAxisRange.maxValue` is 0.
  - `atFontScale13TheLastWeekCanStillBeReached`: at 1.3, `day_2026-09-06` can be scrolled to and tapped, and `day_multi` isn't cut.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*DayPaneTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 18, 29–30 and Units. The band is drawn behind each cell's circle: full width in the middle, and a half-width rounded cap at an end or at a line's first or last column.
- [ ] **Step 3: Images.**
  - New: `add_event_day_dark` (6c), `add_event_days_dark` (6d, Thu 29 Oct – Sun 1 Nov).
  - Check against handoff 6c and 6d.
```bash
git add capability/calendar
git commit -m "Add the editor's Day pane: quick picks, a month with each day's people dots, and a switch to pick a run of days across months"
```

---

### Task 13: The Time pane and the clash (handoff 6e, 6i; rulings 19, 20, 31, 32; Review Focus 5)

**Review:** sonnet.

**Files:**
- Create: `ui/TimePane.kt`; test `ui/TimePaneTest.kt`.
- Modify:
  - `core/ui/.../Controls.kt`: `HhSegmented`;
  - `ui/EditorPane.kt`;
  - `ui/EditorRows.kt`: the Clash tag on the Time row;
  - `ui/EventEditorScreen.kt`: the hint on a clash, and the `EditorPicker` branches `StartTime` and `EndTime` using `TimePickerCard`;
  - `ui/Pickers.kt`: `EditorPicker` gains `StartTime`, `EndTime`;
  - Icons: `warning`, `check_circle`.
- Test: `core/ui/src/test/.../SegmentedTest.kt`; `ui/AddEventScreenshotTest.kt`.

**Interfaces:**
- Consumes: `EditorForm` time members (Task 7), `Held.busyHours`/`check`, `freeLine`/`clashLine`/`freeAtLine` (Task 9), `TimePickerCard`, `PickerLayer`.
- Produces:
  - `@Composable fun HhSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, tag: String, modifier: Modifier = Modifier, locked: Set<Int> = emptySet(), onLocked: () -> Unit = {})`. It is a `surf2` pill of 48 dp segments; the selected one is `accent` with `accentInk`. Semantics: a `selectableGroup` of `Role.RadioButton`. A locked segment draws as `HhChoiceChip(locked)`.
  - `@Composable internal fun TimePane(form: EditorForm, held: Held?, people: List<Person>, onPicker: (EditorPicker) -> Unit, onPicked: () -> Unit)`; the event it skips comes from `form.mode`.
  - Tags: `time_mode` (segments `time_mode_0`/`_1`), `hour_07`…`hour_22`, `hour_other`, `minute_00`…`minute_45`, `length_30`, `length_45`, `length_60`, `length_120`, `length_until`, `time_result`, `time_free_{HHmm}`, `time_end_error`, `time_all_day_only`.
  - `Icons.WARNING`, `Icons.CHECK_CIRCLE`.

- [ ] **Step 1: The failing tests.** Each ends with `assertClickablesHaveRoleAndLabel()`.
  - `SegmentedTest.oneIsSelectedAndALockedOneExplains`.
  - `allDayIsTheDefaultAndMovesOn` (ruling 20): the pane opens on "All day"; tapping All day moves on at 266 ms.
  - `atATimeShowsTheHoursInThreeGroups`: "Morning" 07–11, "Afternoon" 12–17, "Evening" 18–22 plus `hour_other`, six a row, in tabular figures. The row stays "All day" until an hour is tapped (ruling 19).
  - `anHourThenMinutesThenLength`: `hour_10`, `minute_45` and `length_45` give the row "10:45–11:30", and Repeats is active at 266 ms. Before the length tap, Time is still active at 500 ms.
  - `otherTimeAndUntilOpenThePicker`: `hour_other` calls `onPicker(StartTime)`; `length_until` reads "Until…" and calls `onPicker(EndTime)`; an end of 11:20 makes `length_until` read "Until 11:20", selected.
  - `anEndBeforeTheStartSaysSo`: an `EndTime` pick of 09:00 after 10:00 shows `time_end_error` with `END_BEFORE_START`.
  - `busyHoursHaveAnAmberEdgeAndDot`: with Mia chosen and her Piano 15:30–16:15 on the day, `hour_15` and `hour_16` have a `weather` edge pixel and a 7 dp dot; `hour_14` doesn't.
  - `theResultSaysFree`: 10:00 gives `time_result` "10:00–11:00" and "Mia is free then" in `accent` with `check_circle`.
  - `aClashWarnsAndNamesTheFreeStarts` (6i): 15:00 for 45 minutes shows:
    - `time_result` "15:00–15:45" in `weather` with `warning` and "Overlaps Piano, 15:30–16:15";
    - "Mia is free at" with `time_free_1445` and `time_free_1615`, then "or keep it; clashes are allowed.";
    - the Time row's `editor_row_time_tag` "Clash";
    - `editor_save` still enabled, with the hint "Clashes are allowed; both show on the week".
  - `aFreeStartChipMovesTheTime`: `time_free_1615` sets 16:15, and the clash goes.
  - `pastTheHeldWindowThereIsNoResultLineAndNoTag` (Review Focus 5): Piano is on both days. As the control, 15:00 on `lastShownDay` shows the line and the Clash tag. On `lastShownDay + 1` there is no `time_result` check line (only the range), no busy hour and no Clash tag.
  - `everyoneHasNoCheck`: as the control, Mia chosen at 15:00 shows the clash. Everyone at the same time shows none.
  - `aPersonWhoseCalendarNeedsSignInHasNoCheck`: as the control, a healthy Mia at 15:00 shows the clash. With Mia in `held.unhealthy` there is no `time_result` check line and no busy hour.
  - `multiDayLocksAtATime` (ruling 18): with multi-day on, `time_mode_1` is locked, and a tap shows `time_all_day_only` "An event over several days is all day.".
  - `skipLeavesItAsItIs`: `editor_skip` moves to Repeats with the time unchanged.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*TimePaneTest*' :core:ui:testDebugUnitTest --tests '*SegmentedTest*' --tests '*IconFont*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 19, 31–32 and Units. The pane reads `held` once per recomposition through `remember(held, form.who, form.day, form.startTime, form.length)`.
- [ ] **Step 3: Images.**
  - New: `add_event_time_dark` (6e, Mia free at 10:00–10:45), `add_event_clash_dark` (6i, at 15:00–15:45 as the frame), `segmented_dark`, `segmented_light` (core/ui).
  - Check against handoff 6e and 6i.
```bash
git add capability/calendar core/ui tools/fonts
git commit -m "Add the editor's Time pane: all day or hour and minute chips with a length, busy hours marked, and a warning that names the clash and the nearest free times"
```

---

### Task 14: The Repeats pane and the Check pane (handoff 6f, 6g; rulings 33, 34, 36)

**Review:** sonnet.

**Files:**
- Create: `ui/RepeatsPane.kt`, `ui/CheckPane.kt`; tests `ui/RepeatsPaneTest.kt`, `ui/CheckPaneTest.kt`.
- Modify: `ui/EditorPane.kt`, `ui/EventEditorScreen.kt` (the "Everything’s set" hint on Check), `ui/AddEventScreenshotTest.kt`.

**Interfaces:**
- Consumes: `EDITOR_REPEATS`, `wording`, `stepperLabel`, `fewerTimes`/`moreTimes`, `HhRoundButton(repeatWhileHeld)`, `HhSegmented` (Task 13), `checkSentence`, `firstDates` (Task 9), `EventChip` (Task 3).
- Produces:
  - `@Composable internal fun RepeatsPane(form: EditorForm, onPicked: () -> Unit, onChooseDay: () -> Unit)`;
  - `@Composable internal fun CheckPane(form: EditorForm, people: List<Person>)`;
  - tags `repeat_{Choice}`, `ends_mode`, `ends_fewer`, `ends_more`, `ends_count`, `repeats_hint`, `repeats_need_day`, `check_sentence`, `check_date_{0..3}`, `check_more`, `check_chip`;
  - `internal fun previewUi(form: EditorForm, people: List<Person>): EventUi` (ref `EventRef("", "", "preview")`).

- [ ] **Step 1: The failing tests.** Each ends with `assertClickablesHaveRoleAndLabel()`.
  - `RepeatsPaneTest.theOptionsAreWordedFromTheDay`: for Sat 26 Sep, six 56 dp radio rows in `EDITOR_REPEATS` order with the ruling 33 words. The selected one has an `accentSoft` fill and a 2 dp `accent` ring.
  - `RepeatsPaneTest.doesntRepeatMovesOnAndARepeatDoesNot` (ruling 20).
  - `RepeatsPaneTest.endsShowOnlyWhileItRepeats`: with Weekly, `ends_mode` "Keeps going | Stops after" shows; with Doesn't repeat, it doesn't.
  - `RepeatsPaneTest.stopsAfterShowsOneStepper`: "Stops after" shows `ends_count` "10 times · last on Sat 28 Nov" between `ends_fewer` and `ends_more`, named "Fewer times"/"More times". Two taps on `ends_more` give "12 times · last on Sat 12 Dec".
  - `RepeatsPaneTest.theStepperStopsAtItsEnds` (port): at 2 `ends_fewer` is disabled; at 52 `ends_more` is.
  - `RepeatsPaneTest.holdingMoreStepsTheCount` (port): held 816 ms gives 14.
  - `RepeatsPaneTest.theHintIsUnderneath`: `repeats_hint` reads "To skip one date, change it in Google Calendar on your phone." (the master's service).
  - `RepeatsPaneTest.withNoDayItAsksForOne` (ruling 33): `repeats_need_day` "Choose a day first." and a "Choose a day" button that calls `onChooseDay`.
  - `RepeatsPaneTest.theRowReadsTheChoice`: `editor_row_repeats_value` "Every Saturday · 12 times".
  - `CheckPaneTest.theSentenceAndTheFirstFew` (6g): Guitar lesson for Mia, weekly from Sat 26 Sep, 10:00–10:45, 12 times:
    - `check_sentence` reads the handoff's sentence exactly;
    - `check_date_0`…`_3` read "Sat 26 Sep", "Sat 3 Oct", "Sat 10 Oct", "Sat 17 Oct", each with Mia's dot;
    - `check_more` reads "+ 8 more".
  - `CheckPaneTest.theWeekChipPreview`: `check_chip` is the week chip for Mia, "10:00 · Mia" and "Guitar lesson", tinted as on the week.
  - `CheckPaneTest.noButtonInThePane`: the pane has no clickable (the preview chip is given `onOpen = null`), and `editor_save_hint` reads "Everything’s set".
  - `CheckPaneTest.aSharedOneOffReadsBoth`: "Cinema for Sam and Mia, Fri 25 Sep, 17:00–18:00." with a neutral striped preview chip.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*RepeatsPaneTest*' --tests '*CheckPaneTest*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 33–34, 36 and Units.
- [ ] **Step 3: Images.**
  - New: `add_event_repeats_dark` (6f, Weekly, Stops after 12), `add_event_check_dark` (6g), `add_event_check_shared_dark`. Changed: `change_event_dark`, whose Check pane is now drawn.
  - Check against handoff 6f and 6g.
```bash
git add capability/calendar
git commit -m "Add the editor's Repeats pane, worded from the day with one stepper for how many times, and the Check pane that says the event back in a sentence"
```

---

### Task 15: Undo: the toast's action, `undoAdd`, `deleteSeries` (spec §2 ruling 3, §3.1; rulings 38–40; Review Focus 2)

**Review:** opus.

**Files:**
- Create: `UndoneKeys.kt` (`@Singleton class UndoneKeys @Inject constructor()`).
- Modify:
  - `CalendarContract.kt`: `CalendarWriter.deleteSeries`.
  - `CalendarEditor.kt`: `AddedEvent`, `undoAdd`, the added toast with its action; `EVENT_ADDED` and `eventAddedFor` go.
  - `CalendarSync.kt`: when a CREATE comes back Accepted and its key is in `UndoneKeys`, it isn't mirrored and is deleted at once. A key whose delete failed is retried on the next pass (ruling 39).
  - `CalendarStore.kt` + `db/CalendarDatabase.kt`: `applySeriesDeleted`, a DAO delete by `seriesId` or `remoteId`; no schema change.
  - `CalendarUi.kt`: `addedLine`, and `internal fun weeksTo(today: LocalDate, date: LocalDate): Int?` (null past `lastShownDay` or before today). `addedLine`'s `beyond` is the date whenever `weeksTo` is null.
  - `TestEngines.kt`, and every test that constructs `CalendarSync` or `CalendarEditor`: pass `UndoneKeys()`.
  - `provider/calendar-google/.../GoogleCalendarProvider.kt`, `provider/calendar-fake/.../FakeCalendarProvider.kt`.
  - `capability/calendar-testkit/.../CalendarProviderContractTest.kt`. The series checks don't use `assertMatches`, which asserts a written event is never recurring (`:202`).
  - `capability/calendar-testkit/src/test/.../TinyProvider.kt`: a series create that expands its occurrences, and `deleteSeries`.
  - `ContractSuiteSelfTest.kt`:
    - `runCount` goes from 22 to 25;
    - ReadOnly's skip count gains the three new checks;
    - each broken fixture's expected failures widen: StrictDelete fails `deleteSeriesOfAGoneSeriesSucceeds`, and Blind and Recreating fail `deleteSeriesRemovesEveryOccurrence`.
  - Test doubles: `ScriptedWriter.kt`, `StubEditor.kt` (or whatever implements `CalendarWriter` in tests).
- Test:
  - `CalendarEditorTest.kt`: every `EVENT_ADDED` assertion (`:129/:552/:612/:626/:651/:679/:847`) moves to `addedLine(...)`.
  - `WriteCopyTest.kt`, `CalendarStoreTest.kt`, `CalendarSyncTest.kt`.
  - `ui/EventEditorHostTest.kt:51/:198/:239`: expect "Added {title}.".
  - `app/src/testDebug/.../SampleAddTest.kt:26/:137`: `containsExactly(addedLine("…", null, null), CHANGES_SAVED, EVENT_DELETED)`.
  - The providers' contract tests and `GoogleWriteTest.kt`.

**Interfaces:**
- Consumes: `Toaster.show(…, action)`, `ToastAction` (Task 6); `repeatsLabel`, `RepeatChoice.wording` (Task 8).
- Produces:
  - `suspend fun CalendarWriter.deleteSeries(conn: Connection, source: CalendarSource, seriesId: String)`, with the same throws as `delete` and the contract of ruling 40.
  - `data class AddedEvent(val key: String, val connectionId: String, val sourceId: String, val title: String, val series: Boolean, val createdBy: String)`. Whether the create was ever sent is read from its outbox row, under the write lock, when Undo runs, because the drain may try it within the 8 s.
  - `suspend fun CalendarEditor.undoAdd(added: AddedEvent): EditResult`. The only gate is `who.person.id.value == added.createdBy` (ruling 39). With nobody signed in, it asks for a PIN with `PinReason.Delete`.
  - `class UndoneKeys { fun add(key: String); fun has(key: String): Boolean; fun done(key: String); val pending: Set<String> }`.
  - `fun addedLine(title: String, wording: String?, beyond: LocalDate?): String` (ruling 38).
  - `const val UNDO = "Undo"`, `fun removed(title: String): String = "Removed $title."`, `fun couldNotUndo(service: String): String`.
  - `suspend fun CalendarStore.applySeriesDeleted(connectionId: String, sourceId: String, seriesId: String)`.

- [ ] **Step 1: The failing tests.**
  - `WriteCopyTest.theAddedLine`:
    - "Added Guitar lesson.";
    - "Added Guitar lesson, every Saturday.";
    - "Added Guitar lesson, Sat 12 Dec.";
    - "Added Guitar lesson, every Saturday from Sat 12 Dec.".
  - `CalendarEditorTest`:
    - `anAddedEventsToastOffersUndo`: a create gives the `RecordingToaster` one toast "Added Dinner." with the action "Undo"; a queued create gives the same.
    - `undoingAOneOffTheServiceHasDeletesIt`: Undo writes one delete for the key, the mirror loses the event, and the toast is "Removed Dinner.".
    - `undoingASeriesTheServiceHasDeletesItAndEveryOccurrence` (Review Focus 2):
      - a weekly series is accepted, then synced, giving three occurrences with `seriesId` = key;
      - Undo calls `deleteSeries(key)` once, and no row with that `seriesId` is left.
    - `undoingASeriesStillInTheOutboxDropsItAndWhatIsBehindIt` (Review Focus 2):
      - offline, the series is queued and never sent (`attempts == 0`);
      - an assign of the series is queued behind it, the only change a queued series allows;
      - Undo leaves `pendingNow()` empty, and its `deleteSeries` attempt (offline) isn't queued;
      - the toast is "Removed {title}.";
      - back online, the drain sends nothing.
    - `undoBeforeTheDrainsCreateLandsStillRemovesIt` (Review Focus 2, ruling 39):
      - the scripted writer holds the drain's create;
      - Undo drops the row, and its delete reaches the service first, finding nothing (gone);
      - the writer then accepts the create;
      - the drain finds the key in `UndoneKeys`, doesn't mirror the event, and deletes it;
      - the next sync finds nothing, and `UndoneKeys.pending` is empty.
    - `aFailedDrainDeleteIsRetried`: in the same race, the drain's delete fails once (offline). The key stays in `UndoneKeys`, and the next pass deletes it.
    - `undoingAOneOffOfflineQueuesItsDelete` (Review Focus 2): the event is accepted, then the tablet goes offline. Undo queues one DELETE for the key, and the toast is "Removed Dinner.".
    - `undoingASeriesThatCannotBeReachedSaysSo` (ruling 39): the series is accepted, then the tablet goes offline. Undo queues nothing and toasts "Couldn’t undo — remove it in Google Calendar on your phone.", and all three occurrences are still in the mirror.
    - `undoingASeriesThatIsNoLongerASeriesSaysSo`: `deleteSeries` throws `WriteRejectedException`. The toast is the same, and the mirror is unchanged.
    - `undoingASeriesWhoseSendTimedOutWhileStillOfflineSaysItCouldNot` (Review Focus 2):
      - the create timed out, so it is queued with `attempts == 1` and Google may hold it;
      - the tablet is still offline;
      - Undo drops the row, toasts "Couldn’t undo — …", and leaves the mirror as it was.
    - `aDrainAttemptAfterTheAddCountsAtUndo`: the create is queued with `attempts == 0`, then the drain tries once and times out, still offline. Undo reads `attempts == 1` and toasts "Couldn’t undo — …".
    - `onlyThePersonWhoAddedItCanUndo` (Review Focus 2, ruling 39): Alex adds an event. Then Sam (adult), an admin, and Mia (child) are each signed in in turn. Each Undo is refused with `cannotChangeOthers(name)`, and nothing is written, dropped or queued.
    - `thePersonWhoAddedItCanUndoWithoutEditOwn`: with a role set lacking `EDIT_OWN`, Alex's Undo still removes the event.
    - `undoDoesNotOpenSeriesDeleteElsewhere` (**pin**, spec §2 ruling 3): `delete(ref)` of a recurring occurrence still returns `NotEditable`.
  - `CalendarProviderContractTest`:
    - `deleteSeriesRemovesEveryOccurrence` (Review Focus 2): a weekly COUNT=3 create, then `deleteSeries`; the next sync has none of its occurrences.
    - `deleteSeriesOfAGoneSeriesSucceeds`.
    - `deleteSeriesOfAOneOffIsRefused`: `WriteRejectedException`.
  - `GoogleWriteTest.deleteSeriesLooksThenDeletesTheSeries`: a GET of the series id, then a DELETE of it. A 410 on the GET returns without a DELETE.
  - `CalendarStoreTest.applySeriesDeletedRemovesTheSeriesAndItsOccurrences`.
  - `MonthTitleTest.weeksToTheDate`: today gives 0; today + 6 gives 0; today + 7 gives 1; `lastShownDay` gives 3; one day more gives null; yesterday gives null.

  Run `./gradlew :capability:calendar:testDebugUnitTest --tests '*CalendarEditorTest*' --tests '*WriteCopyTest*' --tests '*CalendarStoreTest*' --tests '*CalendarSyncTest*' --tests '*EventEditorHostTest*' :capability:calendar-testkit:testDebugUnitTest :provider:calendar-google:testDebugUnitTest :provider:calendar-fake:testDebugUnitTest :app:testDebugUnitTest --tests '*SampleAddTest*'`. Expected: FAIL to compile.
- [ ] **Step 2: Implement** per rulings 38–40.
  - `report` builds the action for a saved CREATE from the draft and the authorised person.
  - `undoAdd` runs through `onAppScope`, so closing anything can't cut it off.
  - The mirror is changed only after a delete succeeds.
- [ ] **Step 3: The gate and commit.** Expected: PASS, **no image changed**.
```bash
git add capability/calendar capability/calendar-testkit provider/calendar-google provider/calendar-fake app
git commit -m "Offer Undo on the toast after adding an event, which removes it, a whole new series included, even while it is still waiting to be sent"
```

---

### Task 16: The editor in place: host, entry points, after Add, the side sheet removed (spec §3.1 entry points and After Add, §3.4; rulings 13, 17, 22, 28, 37, 41; Review Focus 4)

**Review:** opus.

**Files:**
- Modify:
  - `ui/EventEditorHost.kt`:
    - `EditorRequest` (`:33`) becomes `New(day: LocalDate?, from: EditorFrom)` and `Edit(ref, from, start: EditorStep = EditorStep.Check)`;
    - the host opens an `EditorForm` (with `order` from `repo.people`, `childOnly`, `repeats`);
    - it holds an `EditorFlow`;
    - it collects `repo.held(today)`, `repo.members` and `repo.masterCalendarName`;
    - it draws `EventEditorScreen`, passing the master's name on New and `EventUi.sourceName` on Change (ruling 25);
    - on `Done`/`Queued` for a new event, after `onClose()`, it calls `weekFocus.show(n)` for `weeksTo(today, day)`, and opens the Calendar tab when `from == Home` (ruling 37).
    - `rememberEventAdder` (`:174`) and `rememberEventOpener` gain `from`.
  - `WeekFocus.kt` (create, ruling 37); `ui/CardHosts.kt`: `WeekViewHost` collects `WeekFocus.requested` into `shown.show(n)` then `consumed()`; Home's adder (`:25`) passes `EditorFrom.Home` and today.
  - `ui/Sheets.kt:28`: the detail's Change opens `EditorRequest.Edit(ref, from)`. `showEditor` uses `showFullScreen`; `showDetail` carries `from`.
  - `ui/EventDetailSheet.kt` / `ui/EventDetailHost.kt`: on an editable event, the For row is clickable ("Change who") and opens `Edit(ref, from, start = EditorStep.Who)` (spec §3.3).
  - `ui/WeekHeader.kt:109–111`: its `onAdd` becomes `((LocalDate?) -> Unit)?`, and Add event calls `onAdd(null)` (spec §5 call 9).
  - `ui/WeekView.kt`: `onAdd: ((LocalDate?) -> Unit)?`; the empty column passes its date.
  - `EditorForm.kt`: takes in `Mode`, `TimeChoice`, `MAX_SPAN_DAYS`, `UNTIL_TOO_LATE`, `MAX_TITLE_LENGTH` and `lengthLabel` from `EventForm.kt`.
  - `Recurrence.kt`: `Monthly`, `COMMON`, `MORE`, `RepeatChoice.label`, `RepeatEnd.OnDate` and the UNTIL branch go.
  - `ui/Pickers.kt`: `DatePickerCard`, `firstPageStart`, `pageOf`, `pageStart`, `pageRangeLabel`, `dateCellLabel` go; `EditorPicker` is `{ None, StartTime, EndTime }`.
  - `ui/CalendarType.kt`: the sheet-only dimens go.
- Delete:
  - `ui/EventEditorSheet.kt`, `EventForm.kt`;
  - tests `ui/EventEditorSheetTest.kt`, `ui/EditorScreenshotTest.kt`, `EventFormTest.kt`;
  - every `editor_*` baseline in `capability/calendar/src/test/screenshots/`: `editor_keyboard_`, `editor_empty_`, `editor_child_`, `editor_repeats_`, `editor_more_options_`, `editor_edit_`, `editor_own_length_`, `editor_failed_`, `editor_date_picker_`, `editor_time_picker_`, `editor_locked_`, `editor_until_`, `editor_until_error_`, `editor_span_edit_`.
- Re-point:
  - `ui/EventEditorHostTest.kt` (every test, to the new tags; see Step 1);
  - `CalendarEditorTest.kt:137` (`aMultiDayAllDayEventIsWrittenWithItsExclusiveEnd`): builds an `EditorForm` in place of `EventForm` (`setMultiDay(true)`, then `tapDay` Mon 19 Oct and Wed 21 Oct). Task 15 edits this file earlier;
  - `ui/EditForms.kt`: its `EventForm` fixtures (`dinnerOf`, `editOf`, …) go; the `editorForm` ones stay;
  - `provider/calendar-fake/src/test/.../FakeCalendarProviderTest.kt:366/:374`: the `RepeatEnd.OnDate` cases build a hand-written `UNTIL` rule string instead, so the expander still reads rules made on phones;
  - `ui/PickersTest.kt` (the date picker's tests go; the time picker's stay);
  - `ui/FontScaleTest.kt:175` (`atFontScale15TheEditorSummaryTakesTwoLinesBeforeCuttingTheName` becomes `atFontScale15TheEditorsRowValuesEllipsiseOnOneLine` on `EventEditorScreen`);
  - `RecurrenceTest.kt` (the OnDate cases go);
  - `ui/WeekViewTest.kt` (Add event passes null);
  - `ui/CardsTest.kt`;
  - `app/src/test/.../OverlayLayersTest.kt` (its editor references).

**Where 9c-1's sheet tests went** (so nothing is lost):
- Child lock: `WhoPaneTest` (Task 11).
- Save gating and the reason: `EventEditorScreenTest` (Task 10).
- Keyboard: `EventEditorScreenTest.withTheKeyboardUp…`, `nothingIsCut…` (Task 10).
- Counts: `RepeatsPaneTest` (Task 14).
- Until and the 60-day cap: `EditorFormTest` and `DayPaneTest` (Tasks 7, 12).
- Locked spans: `EditorFormTest` (Task 7), and `EventEditorScreenTest.aLockedSpanShowsOneDayRow` (here).
- Failure card: Task 10.
- Gone with the sheet's layout, with no successor: section labels, Time beside Length, More options and scrolling, Pick a date / Pick a time.

**Interfaces:**
- Consumes: everything from Tasks 6–15.
- Produces:
  - `enum class EditorFrom { Home, Calendar }`;
  - `fun contextLine(from: EditorFrom, signedIn: String?): String` (ruling 28);
  - Consumes `weeksTo` (Task 15).

- [ ] **Step 1: The failing tests.** Each UI test ends with `assertClickablesHaveRoleAndLabel()`.
  - `EventEditorHostTest` (re-pointed, plus new):
    - `theWeeksAddEventOpensFullScreenWithNoDay`: `week_add_event` opens `editor_screen` over the whole window, with "Choose a day" and `RecordingOverlay.fullScreen == true`.
    - `anEmptyColumnOpensOnItsDay`, `homesPlusOpensOnToday`.
    - `detailsChangeOpensChangeEventWithEveryRowFilled`: the Who, Day and Time values match the event, and Check is active.
    - `theDetailsForRowOpensTheEditorOnWho` (spec §3.3): on an editable event, tapping the For row opens Change event with Who active; on a read-only event the row isn't clickable.
    - `savingANewEventAsksForThePinAddsItAndCloses` (port).
    - `afterAddTheWeekShowsTheEventsFirstDate` (ruling 37): from the week on this week, adding on today + 9 shows "Next week" with the event's chip.
    - `afterAddPastTheWeeksReachTheWeekStays`: today + 40 keeps this week and toasts "Added Dinner, {date}." with Undo.
    - `afterAddOnAPastDayTheToastNamesTheDate` (ruling 38): yesterday keeps the week and the toast names the date.
    - `afterAddFromHomeTheCalendarOpensOnTheEventsWeek` (ruling 37): from Home's +, adding on today + 9 calls `openTab(CALENDAR_TAB_ID)` on the `RecordingNavigator`, and `WeekFocus.requested` is 1.
    - `aChildWhoseSessionEndsCannotSaveForSomeoneElse` (Review Focus 4): Mia (child) opens New; the session ends; `form.toggleWho(alex)` is called directly; Save asks for a PIN, Mia's PIN is entered, and the result is `cannotAddForOthers("Mia")`, with no write and no pending change.
    - `theLockedTilesFollowTheSessionRole` (port): Mia's session ends with the editor open, and the tiles unlock.
    - `idleCloseStillApplies` (ruling 13): unedited, it goes at 120 s.
    - `anEditorWithOnlyADayChosenWaitsTenMinutesAndAsksBeforeDiscarding` (ruling 15): with only a Day tapped, it is still open at 120 s, warns at 580 s and goes at 600 s; ✕ before that shows "Discard this event?".
    - `aRepeatSetByAnAdultCannotBeSavedByAChild` (**pin**): Alex sets Every Saturday, the session ends, and Mia's PIN at Save is refused with `CANT_REPEAT_FOR_CHILD`; nothing is written.
    - `deleteStillAsksForAPinThenHandsOverToTheDetailSheet` (port, through `editor_delete`).
    - `tryAgainAfterATabletFailureReusesTheKeyAndMakesOneEvent`, `anUnchangedEditClosesWithoutAPin`, `typingATitleMarksTheSheetEditedAndClearingItDoesNot`, `dismissingAnEditedSheetSavesNothing`, `aSheetThatCannotLoadClosesAndSaysSo`, `eachOpeningGetsAFreshSheet`, `aQueuedCreateOpensAsSyncingAndItsEditQueuesBehindIt` (ports, same assertions on the new tags).
    - `theContextLineSaysWhereFromAndWho`: "From Calendar · Alex signed in"; from Home with nobody signed in, "From Home".
  - `EventEditorScreenTest.aLockedSpanShowsOneDayRow` (ruling 21): a timed span edit shows Day with "Mon 28 – Wed 30 · change dates in Google Calendar on your phone" and `lock`, and no Time row; tapping Day opens no pane.
  - `CopyScanTest` passes with the side sheet's copy gone.

  Run `./gradlew :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per the rulings. Check nothing imports the deleted names: `grep -rn "EventEditorSheet\|EventForm\b\|DatePickerCard\|RepeatEnd.OnDate\|RepeatChoice.MORE\|EditorPicker.Date" --include=*.kt .` (outside `build/`) finds nothing.
- [ ] **Step 3: Images.**
  - Predicted: every `editor_*` baseline deleted (above); `week_` (none, unless the Add event button's state changed); `today_` none.
  - No new image here; the editor's images were recorded in Tasks 10–14.
  - Look at one `add_event_` and the week after an add in a test against handoff 6g's "After Add".
```bash
git add -A capability/calendar app provider/calendar-fake
git commit -m "Open the new full-screen editor from the week, an empty column, Home and an event's details, go to the event's week after adding it, and remove the old side sheet"
```

---

### Task 17: The emulator walk-through on `culvery-5` (9c-1 and this update together)

**Review:** the controller, with the user at the emulator. (The workflow runs `simplify` over the branch first, then the gate.)

**Rules.** As Plan 9a's Task 15:
- Use only the `culvery-5` AVD, started with `-no-snapshot-save -camera-front emulated` (check `adb -s "$S" emu avd name`).
- It holds a debug build, so `install -r` the debug APK.
- Never uninstall, and never run a Gradle install task while a device is connected.
- The user types every PIN.
- Put back every setting changed.

- [ ] **Step 1: The AVD and the build.** Run `./gradlew :app:assembleDebug`, then `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2: Walk the checklist** (one line each).
  1. **Shared sheets (9c-1):** the event detail, a person sheet and Favourites are the wider sheet with the darker scrim and the edge.
  2. **The week:**
     - the title is the month with the year muted;
     - a week across two months reads "September – October" with "Oct" over the 1st;
     - the line under it says which week, the dates and the sync age, and paging forward changes "Next week", "In 2 weeks".
  3. **Shared events:** the fake's Cinema (Sam + Mia):
     - one row on Home with a striped bar and "Sam + Mia";
     - a neutral week chip with a striped edge and two dots;
     - the detail's For row "Sam + Mia";
     - in the detail's chips, adding Alex makes "Alex, Sam +1", and Mia can't be the last one cleared.
  4. **The editor opens:**
     - full screen over the rail from the week's Add event (no day), an empty column (that day), Home's + (today) and an event's Change (every row filled, "Save changes");
     - its context line is right in each case.
  5. **What, Who and Day:**
     - with the title typed and the keyboard up, the rows shrink and nothing is cut, and Next goes to Who;
     - the foot names what's missing;
     - Who picks Sam and Mia, and Everyone clears them;
     - Day picks a date, and Time becomes active a moment later.
  6. **Lasts more than a day** (9c-1's multi-day, reworded):
     - Thu 29 Oct to Sun 1 Nov across the month shows the band and one bar across the week (9b's lane), with "Day 2 of 3" on Home on its second day (set the AVD's date if needed, and put it back);
     - past 60 days the line says so and Add event waits;
     - opened again, the range is editable;
     - a timed event over midnight from the fake still shows the locked dates line in one Day row.
  7. **Time:**
     - hours, minutes and lengths;
     - after an hour the pane waits for the length (not moving on is a design-review note, so the user should say how it feels);
     - Other time and Until… use the existing picker;
     - a clash with the fake's sample warns in amber, names the event and the nearest free starts, adds the Clash tag, and Add event stays green;
     - a day past four weeks shows no free or busy line.
  8. **Repeats (9c-1's COUNT, reworded):**
     - the options are worded from the day;
     - Stops after shows "10 times · last on …" and holding + runs to 52 and stops;
     - a weekly event ending after 3 times shows 3 times on the week;
     - "Every month, on the 4th Saturday" and "Every month, on the last Thursday" land on the right dates in the fake;
     - the detail sheet reads a counted series back as "Every Saturday · 12 times · last on Sat 12 Dec".
  9. **Check and Add:**
     - the sentence and the first few dates match;
     - after Add the week shows the event's week, and the toast says "Added …" with Undo for 8 seconds;
     - adding from Home's + also lands on the Calendar tab on the event's week;
     - Undo removes it, a series included; Undo by a different person signed in is refused;
     - Undo while offline (the fake's offline switch) on a just-queued series leaves nothing to send.
  10. **A child (9c-1's child lock):**
      - signed in as Mia, Who starts as Mia and the others and Everyone are locked with the padlock and the line;
      - a tap says "Mia can add events for Mia only.";
      - there is no Repeats row;
      - the detail chips ask for a grown-up.
  11. **Discard:** closing with changes asks "Discard this event?", and Keep editing keeps them.
  12. **Idle:** the editor left alone closes after 2 minutes, and edited, it warns at 9:40 and closes at 10.
- [ ] **Step 3: The fault loop.** Each fault gets a failing test, the fix, the gate, a commit naming it, `install -r`, and a re-check.
- [ ] **Step 4: Put back** every setting, then `adb -s "$S" emu kill`.
- [ ] **Step 5: Record** under "Plan 9c-1 and the calendar update: emulator walk-through".
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record the emulator walk-through of Plan 9c-1 and the calendar update"
```

---

### Task 18: On the tablet

**Review:** the controller with the user. **Needs the SM-T510 on the cable.** 4e's tablet rules apply: save and restore every setting changed, signed releases with `adb install -r`, never uninstall, and the names check before any commit.

- [ ] **Step 1: Install the release**, signed per `docs/setup/release.md`. Keep `mapping.txt` in `culvery-notes/mappings/`.
- [ ] **Step 2: At the wall** (the user):
  - the full-screen editor reads at arm's length, and the chosen day is obvious (spec §1's goal);
  - a shared event made on the wall shows in Google Calendar on a phone in the calendar's own colour, with the right people back on the wall after a sync;
  - a multi-day all-day event and a series ending after N times show correctly there;
  - a monthly "4th Saturday" and a "last Thursday" series land on the right dates in Google;
  - Undo of a series removes it from Google;
  - a child's locked tiles read as locked, not broken.
- [ ] **Step 3: Record** "Plan 9c-1 and the calendar update: on the tablet": what read and what didn't. No names.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 9c-1 and the calendar update on the tablet"
```

---

### Task 19: Final review, merge, install

- [ ] **Step 1: The review.** Opus reviews the whole branch (9c-1 and this plan) against 9's spec §2.1, §4.8 and §6.4, this spec, 9c-1's plan and this plan. One fix wave follows, each fix with its failing test first and the gate.
- [ ] **Step 2: The follow-ups.** Add to `docs/superpowers/plans/2026-09-23-plan1-followups.md`, under "From Plan 9c-1b":
  - "From before" suggestions (spec §2 ruling 2);
  - choosing the destination calendar per event (spec §3.1);
  - skipping one date of a series from the event's details (the Repeats hint points to the phone for now);
  - an undone create whose drain delete is lost on restart (`UndoneKeys` is in memory, ruling 39);
  - the notes for the end-of-v1 design review above.
  Commit: "Record what Plan 9c-1b left for later".
- [ ] **Step 3: Merge**, on the user's say: `git merge --ff-only plan-9c-forms` on `main`, then the gate on main. Never push. 9c-2 then branches from it.
- [ ] **Step 4: Install** the release from the merged `main` on the tablet with `install -r`.

---

## Spec coverage (9c calendar update → tasks)

| Spec | Task |
|---|---|
| §2.1 bundled into 9c-1, walked through and reviewed as one | header, 17–19 |
| §2.2 the editor as designed, no suggestions; Next to Who | 10–14 (ruling 22) |
| §2.3 Undo removes a series too, nothing else | 15 |
| §3.1 reuse 9c-1's model | 7 (ruling 15) |
| §3.1 full screen, idle close, discard | 6, 10, 16 |
| §3.1 Who multi-select, Everyone | 7, 11 |
| §3.1 Day: month grid, dots, past dimmed, quick picks, range, cap | 7, 9, 12 |
| §3.1 Time: segments, hours, Other time, minutes, lengths, Until | 7, 13 |
| §3.1 clash check, none past the window | 9, 13 |
| §3.1 Repeats worded, Every day, Ends stepper, On a date gone, UNTIL read | 8, 14, 16 |
| §3.1 Check | 9, 14 |
| §3.1 After Add, 8 s toast, Undo | 6, 15, 16 |
| §3.1 Saves to, no Change | 10 |
| §3.1 entry points | 16 |
| §3.2 month title, subtitle, month label | 5 |
| §3.3 storage, colour, order, who line, display, permissions, detail | 1–4 |
| §3.4 past the window | 9, 12, 13, 16 (rulings 30, 37) |
| §3.4 Who starts empty / a child as themselves | 7, 11 |
| §3.4 detail's inline chips multi-select | 4 |
| §3.4 busy means | 9 (ruling 31) |
| §3.4 detail reads repeats in the editor's words | 8 (ruling 35) |
| §3.4 a removed person dropped | 1 (ruling 4) |
| §3.4 the set everywhere | 2, 3 |
| §4 not in scope | 19 (follow-ups) |
| §6 dark images of 6b–6g and 6i; shared images; Google round trips; one walk-through | 11–14; 2–3; 1; 17 |
