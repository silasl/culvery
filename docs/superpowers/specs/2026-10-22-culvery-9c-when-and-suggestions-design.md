# Culvery 9c: When as Starts and Ends, title suggestions, and the walk-through fixes

Brainstormed 2026-10-22 during the emulator walk-through of 9c-1 and the calendar update (Plan 9c-1b, Task 17). It adds to `2026-10-21-culvery-9c-calendar-update-design.md` ("the 9c spec") and changes it where it says so. Everything else in the 9c spec, Plan 9's spec and the 9c-1b plan's rulings still holds. Units as Plan 9 §3: dp = handoff px ÷ 1.5. Sample names only (Alex, Sam, Mia).

Branch `plan-9c-forms`, on top of the 9c-1b build (Tasks 1–16 and the whole-branch simplify are done). The walk-through is paused. It picks up again once this is built, from where it stopped.

## 1. Why

At the walk-through the user found the Day and Time panes fiddly. Picking a span meant a "Lasts more than a day" switch, two taps on the grid, then hour, minute and length chips. Google Calendar has a Start date and time, an End date and time, and one All day checkbox that affects both. The user called that "a much more elegant way than fannying about with chips". The goal is still the 9c spec's: **an Add event that is obvious to use.**

The user also pointed out that the handoff's "From before" title suggestions (6a, 6h) were missing. The 9c spec's ruling 2 had cut them because the tablet keeps no year of past events. The user chose to build them now, from a list of titles that sync remembers, with no year-long fetch.

## 2. Rulings (the user, 2026-10-22)

1. **When is Starts and Ends.** One **Starts** row (date and time), one **Ends** row (date and time) and an **All day** switch replace the Day row and the Time row. This replaces the Day pane's "Lasts more than a day" switch and range picking, its quick picks, and the Time pane's hour, minute and length chips. Who and Repeats stay as they are.
2. **A time is picked with the calendar's existing hour and minute steppers** (`TimePickerCard`'s: hours step by 1, minutes by 15), so the app keeps one time control. The user said "keep it consistent". Settings' Earlier / Later rows stay in Settings.
3. **Title suggestions are built on this branch,** from a remembered-titles list filled by sync (§4).
4. **The week header order** is This week (far left), then ‹ ›, then Add event, so the arrows stay next to Add.

## 3. When: Starts, Ends and All day

### 3.1 The rows
The left column reads: title, Who, **All day** (a switch row), **Starts**, **Ends**, Repeats. Child and Change events drop Repeats as now (9c-1b ruling 21).

- **Starts** and **Ends** are each one row with two tap targets: the **date** ("Sat 26 Sep") and, when All day is off, the **time** ("10:00"). Each target is its own button for TalkBack ("Starts, Saturday 26 September", "Starts at 10:00").
- **All day** is the shared `HhSwitchRow` look, inside the left column. Turning it on hides both times and keeps both dates. Turning it off brings back the last times, or the defaults (§3.4).
- An unset Starts date reads "Choose a day", set and active like today's Day row. Ends shows nothing until Starts has a date, then follows it.

### 3.2 The panes
Each target opens its own pane on the right, with the question as its title (`paneTitle`, so TalkBack speaks it):

| Target | Question | Pane |
|---|---|---|
| Starts date | "Which day does it start?" | The month grid as today: Monday first, up to 3 person dots, past days dimmed. |
| Starts time | "What time does it start?" | The steppers, and the clash line under them (§3.5). |
| Ends date | "Which day does it end?" | The month grid. Days before the start, and days more than 59 after it, are dimmed and can't be tapped. |
| Ends time | "What time does it end?" | The steppers, and the clash line. |

- **The steppers sit in the pane.** There is no Cancel / Set: each step changes the form at once, and Next moves on. Use one shared stepper composable for this pane and for `TimePickerCard`, which stays for nothing else unless the plan finds another caller (the "Other time" and "Until…" pickers go).
- **The steppers work like a clock** (the user, 2026-10-22, at the plan review). Minutes carry into the hour both ways: 11:00 down gives 10:45, and 10:45 up gives 11:00. Hours step by 1. **No step ever changes a date**; the date changes only from a date pick. At the day's edges a Starts step wraps within the day (23:45 up → 00:00, 00:00 down → 23:45). An Ends step that would leave the end's day, or reach the start, is disabled. Starts and Ends behave the same way.
- **Quick picks** show only after a suggestion (§4.4). Otherwise the date panes are the grid alone.

### 3.3 How Starts and Ends move together
- **Moving the start moves the end with it and keeps the length,** date and time, as Google does. A start moved late in the day can push the end onto the next day. The start's own date never changes from a stepper.
- **The end can't be at or before the start.** Any Ends stepper button whose step would put the end at or before the start is disabled. For example, with a start of 10:00 and an end of 11:00 on the same day, hour-down is disabled, and minute-down still works.
- **An end on a later day** is set with the Ends date. The Ends date grid can't pick a day before the start date, so no message is needed. Picking a later day keeps the end time.
- **Moving the end date back to the start's day** when the end time would then be at or before the start: the end becomes the start + 1 hour. *(My call.)*
- **All day:** the end date is the last day of the event (inclusive), as the week's lane shows it. It is written with the end date + 1, as today.
- **A timed event may now run over several days** (Fri 18:00 to Sun 16:00). This overrides the 9c spec §3.1 "timed spans … stay locked when changed" and 9c-1b ruling 21 for timed spans: a timed span made elsewhere opens in the editor like any other event. **An all-day span over 60 days still stays locked** on Change, because Save would refuse it.
- **The 60-day cap** covers both kinds: the end can be at most 59 days after the start's date. The cap message stays (`UNTIL_TOO_LATE`), worded for an end: "An event can last up to 60 days. Pick an earlier end."
- **Repeats and spans** *(my call)*: a repeat is offered only if one occurrence ends before the next one starts. Every day is offered only for an event within one day. Weekly is offered for spans up to 7 days, every 2 weeks up to 14, monthly up to 28, and yearly up to the 60-day cap. A choice that the span rules out is shown locked, with the line "Too long to repeat that often." Changing the span after a repeat is chosen falls back to Doesn't repeat, and says so in the Repeats row.

### 3.4 Defaults
*(my calls)*
- **A new event starts with All day off.** Most family events are at a time, and the Google pattern starts timed.
- **The start time:** the next whole hour from now if the start is today (but not past 23:00), else 09:00. The default **end** is the start + 1 hour.
- These defaults are filled, not chosen. The rows show them in the "set" style, and they don't count as edited (no discard check for defaults alone). A child gets the same defaults.
- **Opening a Change** shows the event's own values. An all-day event has All day on.
- **Who starts empty** for an adult, and Starts' date as the entry point says (9c-1b ruling 17), all unchanged.

### 3.5 The clash check
The rules are unchanged (9c-1b rulings 30–32: cached days only, timed events, the chosen people) and now take the whole span [start, end).
- **One day:** the same lines as today, under the steppers. A free-start chip moves the start, and the end follows (§3.3).
- **Over several days:** the overlap line only, naming the first clash. There are no free-start chips.
- **All day:** no clash line (all-day events never clash).
- **Busy hours** drawn on the hour chips go with the chips. *(My call: the clash line carries it. Goes to the design review.)*
- **The clash line names every busy person,** not just the first. A walk-through note found "Sam has Dentist" while Mia was busy too. Up to two named: "Sam has Dentist, 12:30–13:00; Mia has Piano, 12:45–13:30". With more: "… and 1 more".

### 3.6 Moving on and Next
- The rows step in this order: Who, Starts, Ends, Repeats, Check.
- **Picking a Starts date** moves on after 250 ms (`MOVE_ON_MS`): to the Starts time when All day is off, else to Repeats, since Ends already matches.
- **Picking an Ends date** moves on to the Ends time, or to Repeats when All day is on.
- **The steppers never move on by themselves.** Use Next.
- **Skip** appears on Ends ("Skip" keeps the end that follows the start) and on Repeats.
- **What's missing:** "Needs a day" stays for the Starts date. Nothing else in When is required.

### 3.7 Words that follow
- **The Check sentence:**
  - `{when}` is "Sat 26 Sep, 10:00–10:45", "Fri 23 Oct 18:00 – Sun 25 Oct 16:00", "Mon 19 – Fri 23 Oct, all day" or "Sat 26 Sep, all day";
  - a series keeps its wording ("every Saturday, 10:00–10:45").
  - The date chips show one chip per occurrence, not per day of a span. This fixes the walk-through note where a multi-day event drew one date chip and a normal chip.
- **The added toast and the detail sheet** read the same span words.
- **The foot hint** "Time and repeats are optional" becomes "Repeats are optional" (the time now always has a value), and goes when the form is complete. This fixes the note about the hint on a complete Change.

### 3.8 What goes
The Day pane's "Lasts more than a day" switch and range picking, its quick picks (except §4.4), the Time pane (All day | At a time segments, the hour, minute and length chips, "Other time", "Until…", "An event over several days is all day."), the length words used only by the chips, and every test and image that draws them. The 9c-1b fault F1 (the range restarting after the switch) goes with them.

## 4. Title suggestions (handoff 6a, 6h)

### 4.1 What is remembered
- **A new Room table, `remembered_titles`,** in the calendar database (version 7 → 8, with a migration and its test). Columns:
  - the title as typed and as a match key (trimmed, lower-cased); the key is unique per connection;
  - the connection id;
  - the people tag (`forPerson`'s format);
  - all day or not;
  - the start time and the length in minutes (timed), or the number of days (all day);
  - the repeat as its RRULE (with no COUNT, UNTIL or DTSTART), kept only if it parses to a `RepeatChoice` the editor offers (`EDITOR_REPEATS`); otherwise null, which reads as Doesn't repeat. The rule, not a date, says "4th" or "last";
  - for a series, its first date (the anchor for fortnightly quick picks, §4.4);
  - the remembered date: the latest occurrence on or before today if there is one, else the earliest one after today;
  - the remote event id that supplied the row.
- **Filled by sync:** after each successful sync of a connection, every visible event it read with a non-blank title is upserted by key. Where several events share a title, the one with the latest remembered date wins. A series counts once.
- **Seeded once** from the events already stored when the table is first created, so the list isn't empty after the upgrade.
- **Forgotten with its event** *(my call)*: when sync removes the event a row came from, or Undo removes it, the row is deleted unless another stored event has the same title, which then supplies it. So a mistyped or undone title doesn't stay for a year.
- **Kept 12 months** after its remembered date. Older rows are deleted on the same pass.
- **Removing a connection** deletes its rows (the debug sample's removal included), so sample and real titles never mix.
- **Local only:** it is never sent anywhere, and isn't in any export or log.
- **Read by** a `@Singleton` (e.g. `TitleSuggestions`) with `suspend fun matching(typed: String, max: Int = 3): List<Suggestion>`.

### 4.2 Matching
- Starts at 2 typed characters.
- **Matches:** the key starts with what's typed, or any word in it does ("less" finds "Guitar lesson"). Case and accents are ignored.
- **Order:** title-start matches first, then word matches; within each, the latest date first.
- **At most 3.** The same title from two connections shows once (the latest).
- **Only for New events.** Change never suggests.

### 4.3 The list (6a)
- **When:** while the title is focused, the right pane shows "From before" with the matches. With none, it shows the active row's question as now.
- **Each row** is `s2`, radius 26 px, with:
  - a colour bar (`StripedBar` for a set, Family's colour for Family);
  - the title (28/600);
  - a muted line: who · pattern · when, e.g. "Mia · Saturdays 10:00–10:45 · last on Sat 12 Sep", or "next on Sat 3 Oct" when the remembered date is after today, or "Family · Sat 11 Jul, 10:30";
  - the fill line in accent (19/600, `content_copy` icon), e.g. "Fills in Mia, 10:00–10:45 and every Saturday", "Fills in Everyone and 10:30–12:00", "Fills in Sam, all day for 3 days";
  - a 64 px "Use this" pill (`accentSoft` / `accent`).
- **Under the list:** "A suggestion never copies the date. Or keep typing; Next on the keyboard moves to Who."
- **TalkBack:** each row reads as one item, "Guitar lesson. Fills in Mia, 10:00 to 10:45 and every Saturday", with the action "Use this".
- **Keyboard up** (9c-1b ruling 27): the list has to fit the pane above the keyboard. With 3 rows that don't fit at font scale 1.3, it scrolls; nothing is cut mid-row.
- **`content_copy`** is added to the icon font (plan's icon steps).

### 4.4 After "Use this" (6h)
- **Filled in:** the title as remembered, Who (people still in the household; none left → Everyone), All day, the start time and the length (the end follows the start once a date is picked), and Repeats. Each filled row carries a green **Copied** tag until it is changed.
- **Never copied:** the date, or when a repeat ends (Ends stays "Keeps going").
- **A child:** Who stays themselves. The Copied tag isn't shown on Who. A copied repeat is dropped, since a child can't repeat.
- **Then** the keyboard closes and the **Starts date** is active and empty. Its pane opens with a banner (`accentSoft`, radius 22 px, `content_copy`): "Copied from Guitar lesson (last on Sat 12 Sep): who, time and repeats. Tap any row to change it. The date is never copied." The banner names only what was copied ("who and time", …).
- **Quick picks** follow a copied repeat: the next three dates that fit it, from today. Weekly and monthly on a weekday give the next three such days. Fortnightly gives the next three that keep the remembered series' own fortnight, counted from its first date, so the off weeks are never offered. Yearly gives the next one. Doesn't repeat and Every day give none. Picking one is a Starts date pick (§3.6).
- **Typing again** after Use this keeps what was filled. The suggestions show again for the new text.
- **Edited:** Use this counts as an edit (the discard check applies).

## 5. Walk-through fixes

To be built with this, each with a failing test first:
- **F2:** a blank grey screen for about 1 s before the full-screen editor draws. Find the cause (first composition, or the overlay drawing `bg` before its content) and draw the editor in the first frame.
- **F3:** the 8-second toast covers the detail sheet's Delete and the editor's Add event button. Move the toast clear of the bottom foot when one is showing, or shift it above it. The plan picks one and pins it with a test.
- **The week header order** (§2 ruling 4).
- **The walk-through notes** (the user, 2026-10-22: all but Home's speed):
  - "Next week" shows briefly over the old dates while the week changes. The label and the dates change in the same frame.
  - The foot buttons move while busy. Their places stay fixed; only the content inside them changes.
  - Chips cut off shared names. The who line fits, or shortens by the who-line rules ("Alex, Sam +1") before any cut.
  - Assigning on the detail chips takes about 1 s with no feedback. The tapped chip shows it is busy at once.
  - The pane behind the PIN pad moved from Day to Time. After the PIN, the editor is on the pane it was on before.
  - The keyboard comes back briefly. Once the title loses focus, the keyboard doesn't show again until the title is tapped.
  - Removing the last person on the detail chips is refused silently. It now says why: "An event needs at least one person."

## 6. Not in scope
- Choosing the destination calendar per event (still follow-ups).
- A year-long fetch of past events (the remembered list builds up instead).
- Settings' time rows.

## 7. Left for later
- Home is slow to fill: goes to the follow-ups, to be measured on the tablet.

## 8. Calls I made (check these)
1. All day starts off for a new event; the default time is the next hour (today) or 09:00, for an hour.
2. One shared stepper in the pane, live, with no Cancel / Set; the "Other time" and "Until…" pickers go.
3. Superseded at the plan review: the steppers work like a clock (minutes carry into the hour, no step changes a date; §3.2). An end on a later day is set with the Ends date, and a stepper button that would put the end at or before the start is disabled.
10. (The user, at the plan review.) Suggestions show to anyone at the wall, with no PIN. Hidden calendars' titles are never remembered or suggested.
4. Busy hours go with the chips; the clash line names up to two busy people.
5. A timed span made elsewhere is no longer locked.
6. Suggestions: at most 3, from 2 characters, title-start before word matches, 12 months, deleted with their connection.
7. Quick picks come back only after a suggestion with a repeat; fortnightly ones keep the series' own fortnight.
8. A span can repeat only if one occurrence ends before the next one starts.
9. A remembered title is forgotten when the event it came from is deleted or undone, unless another event has that title; a series shows "next on…" when its remembered date is ahead.
