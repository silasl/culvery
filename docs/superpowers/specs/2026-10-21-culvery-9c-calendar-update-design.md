# Culvery 9c: the calendar update (Add event editor, month title, shared events)

Brainstormed 2026-10-21 from the user's calendar design review. The handoff is `docs/design/2026-10-calendar-update/handoff.md` (sizes, colours, copy and behaviour; its HTML reference is in `culvery-notes/design-review-calendar-2026-10/`). It is an add-on to the October redesign: Plan 9's spec (`2026-10-19-culvery-9-redesign-design.md`) and its rulings still hold except where this spec changes them. Where this spec and the handoff disagree, this spec wins. Units as Plan 9 §3: dp = handoff px ÷ 1.5.

Branch `plan-9c-forms` (it already holds 9c-1, built and reviewed but not walked through), in the one `culvery` checkout.

## 1. Why

The user found the Add event side sheet not intuitive: it was hard to see which date was chosen, the panel was cramped and scrolled, and setting a start and finish was fiddly. They asked for a redesign, not specifically a full-screen one; the full-screen guided editor is the design's answer. **The goal is an Add event that is obvious to use.** Anything in the handoff that adds work without serving that is a candidate to defer.

Done means: the three changes below built, screenshot tests re-recorded and looked at, **one** walk-through on the emulator and the tablet covering 9c-1 and this update together, a final review, a local merge, the build on the tablet.

## 2. Rulings (the user, 2026-10-21)

1. **Bundle it into 9c-1.** The calendar update is built on `plan-9c-forms` on top of 9c-1, and the two are walked through and reviewed as one. 9c-1's emulator walk-through of the side sheet is not done.
2. **Build the editor as designed (handoff §1), except the "From before" suggestions.** Suggestions (6a's list, 6h's Copied state) need a year of past events the tablet does not keep (it syncs from yesterday). They go to the follow-ups. The title field stays; the keyboard's Next moves to Who.
3. **Undo removes a repeating series too.** Only from the 8-second toast, only the series just added on this tablet by the person signed in. It is the one exception to "a series can't be deleted from the wall"; nothing else gains series delete.

## 3. In scope

### 3.1 The Add event editor (handoff §1, 6b–6g, 6i)
It **replaces** the 9c-1 side sheet for both adding ("New event", Add event) and changing ("Change event", Save changes). Layout, row states, panes, sizes and copy as the handoff, with these settled:
- **Reuse 9c-1's model.** `EventForm`'s rules carry over: required title, the multi-day span with its 60-day cap and lock rules (timed spans and all-day spans over 60 days stay locked when changed), COUNT repeats 2–52 default 10, the child lock, `mayRepeat`, "{name} can add events for {name} only." The side sheet's layout code goes; shared pieces that the editor uses stay (HhFooterButton's reason line, HhChoiceChip locked, HhRoundButton hold-to-repeat, the field error style). The shared sheet width/scrim/edge change (9c-1 Task 2) stays for the other sheets.
- **Full screen** is a new overlay mode over the whole shell (rail included), keeping the overlay's idle close (2 minutes, 10 once edited). Close with changes shows "Discard this event?" (Discard / Keep editing) using the existing inline confirm card style.
- **Who is multi-select** (§3.3), with "Everyone" saving a Family event.
- **Day:** the month grid, Monday first, up to 3 person dots per day from the cached events, past days dimmed, quick picks. "Lasts more than a day" turns on range picking (first tap, last tap), forces All day, crosses months, and keeps the 60-day cap with its message.
- **Time:** All day | At a time; hour chips 07–22 in three groups; "Other time" opens the existing time picker for hours outside them; minutes :00/:15/:30/:45; length 30 min, 45 min, 1 hour, 2 hours, Until… (exact end time, same day).
- **Clash check** (6i): for each chosen person, timed events overlapping the chosen time, from the cached days the week view already loads; all-day events don't count. It warns and never blocks. Beyond the synced window (today + `MAX_WEEKS_AHEAD` weeks) there is no free/busy line at all, rather than a false "free".
- **Repeats** (6f), worded from the chosen day: Doesn't repeat; Every {weekday}; Every 2 weeks, on {weekday}; Every month, on the {nth} {weekday} (the 5th occurrence reads "last"); Every year, on {d MMMM}; and Every day (kept, per Plan 9c's earlier ruling that Every day stays). **Ends:** Keeps going | Stops after, with one stepper "12 times · last on Sat 12 Dec" (2–52). "On a date" goes from the form; events made elsewhere with UNTIL are still read and shown. Repeats only for new events, as now.
- **Check** (6g): the sentence, the first 4 dates plus "+ N more", the week chip preview.
- **After Add:** back to the week on the event's first date, and an 8-second toast "Added {title}…" with **Undo**, which deletes the event just added (cancelling it if still in the outbox). The shell's toast gains an optional action.
- **Saves to:** the foot line reads "Saves to the {calendar} calendar" without "Change". Choosing a calendar per event needs changes to which calendar is writable (today only the main one is); it goes to the follow-ups.
- **Entry points** as the handoff: the week's Add event (nothing filled — today the first day shown is prefilled; change to none), an empty column (that day), Home's Today + (today), and the event detail's change (every row filled).

### 3.2 The week title shows the month (handoff §2)
Title "September 2026" (year muted), or "September – October 2026" across two months, with the small month label over the 1st's column. The subtitle is "{This week | Next week | In N weeks} · {Wed 23 – Tue 29 Sep} · {the existing sync text}".

### 3.3 Shared events (handoff §3)
- **Storage:** the people stay in Google's `culvery.person` private property, as a comma-separated list of PersonIds; a single id reads as today, so old events need nothing. Room keeps its one column with the same format: no migration. The outbox JSON stays tolerant. "family" is never mixed with people.
- **Google colour:** a shared event gets no `colorId` (the calendar's own colour); one colour can't show two people.
- **Order:** the household's own order (Settings › People), since there is no age. Admin-first-then-age from the handoff is not available.
- **Who line:** "Sam + Mia"; three or more "Alex, Sam +1". Spoken: "Sam and Mia".
- **Display:** Home's Today rows (striped bar, one row per event), the week chips (neutral `s2`, striped 6 dp edge, overlapping dots), the multi-day lane bars (striped edge), event detail's Who row. Single-person events look as now.
- **Permissions:** an adult may choose any set; a child only themselves (the set must be exactly them). Retagging to a set follows `mayRetag` for each person.
- **Event detail:** changing Who opens the editor's multi-select Who pane.

### 3.4 Settled after the builder check (my calls)
- **Days past the synced window:** the Day pane allows any date; its dots exist only for cached days. After adding an event the week can't show, the week stays where it was and the toast names the date ("Added Guitar lesson, Sat 12 Dec").
- **Who starts empty** for an adult ("Choose who", required, as the handoff); a child's Who starts as themselves, their only choice.
- **The detail sheet's inline Who chips** (recurring and phone-made events, written as ASSIGN/ASSIGN_SERIES) become multi-select toggles with the same rules; the last person can't be cleared, and Family stays exclusive. Change opens the editor as now.
- **Busy means** a timed event whose resolved people (tag, or the calendar's mapped person) include the chosen person. Family events don't make anyone busy. "Everyone" checks nobody.
- **The detail sheet reads repeats back in the editor's words** ("Every Saturday", "Every month, on the 4th Saturday", "12 times · last on Sat 12 Dec"); `RepeatChoice`'s chip text and its read-back split as needed.
- **A removed person** is dropped from a shared event's set when read: one left makes it theirs; none makes it Family.
- **Everywhere an event shows its person, it shows the set:** `EventUi` carries the people, and every view and spoken label uses the who line.

## 4. Not in scope
- "From before" suggestions (follow-ups).
- Choosing the destination calendar per event (follow-ups).
- The "Home, rethought" artboards in the review file beyond the shared-event rows: Plan 9's speculative-designs list still holds.
- 9c-2 (wizard and Settings) stays its own plan after this merges.

## 5. Calls I made (check these)
1. Comma-separated people in the existing property and column, no migration.
2. Shared events get no Google colour.
3. Household order instead of age.
4. Every day kept as a repeat option.
5. Monthly repeats by nth weekday ("4th Saturday", 5th → "last") as the handoff words them; 9c-1's Monthly-by-date goes from the form.
6. No free/busy line beyond the synced window.
7. Undo deletes the event; no per-event calendar choice ("Change" dropped).
8. The editor covers the rail too (full screen); idle close kept.
9. The week's Add event opens with no day chosen (the handoff), not the first day shown.

## 6. Tests and rollout
Unit and screenshot tests per piece; the editor's frames 6b–6g and 6i each get a dark screenshot; shared events get Home, week, lane and detail screenshots. The Google reader and writer get round-trip tests for one, two and "family". One walk-through on `culvery-5`, then the tablet, then the final review — covering 9c-1's model and this update together.
