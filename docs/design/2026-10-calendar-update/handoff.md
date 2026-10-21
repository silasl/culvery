# Handoff: Culvery calendar update (Add event flow, month title, shared events)

## Overview
This is an add-on to `design_handoff_culvery_redesign`. It covers three calendar changes made after that package:
1. **Add event becomes a full-screen editor** that asks one question at a time (artboards 6a–6i). It **replaces** the side sheet (4a) and the "Add event sheet" section of the main README.
2. **The week view shows the month as its title** (3a, note 47).
3. **Shared events** (one event for two or more people) on Home and the week view (notes 48 and 49).

## About the design files
The file in this bundle is a **design reference made in HTML**, not production code. Recreate it in the Culvery Android app (Kotlin and Jetpack Compose), using its existing theme and components. The tokens, units and type scale are the same as the main package: **all sizes are device px on a 1920 × 1200 artboard; dp ≈ px ÷ 1.5** (check the device density first).

## Fidelity
High fidelity. Colours, type, spacing and copy are final unless marked provisional.

## How to read the reference file
Open `Culvery Home Review.dc.html`. The section at the top, "Turn 3 · Add event, start to finish", holds the flow artboards, each with a caption underneath. 3a and the Home artboards (1a–1d) are further down, with numbered pins that match the note list.

| id | Frame |
|---|---|
| 6a | 1 · What: typing the title, keyboard up, suggestions |
| 6h | 1+ · After "Use this" on a suggestion |
| 6b | 2 · Who |
| 6c | 3 · Day |
| 6d | 3+ · Day, over several days |
| 6e | 4 · Time |
| 6i | 4+ · Time, the person isn't free |
| 6f | 5 · Repeats |
| 6g | 6 · Check and add |

---

## 1. The Add event editor

### Model
It's **a form with a guide, not a strict wizard.** The left side is the event so far; the right side asks about whichever row is active. Picking something moves on to the next row automatically, and tapping any row goes back to it.
- **Required:** a title, at least one person, and a day.
- **Optional:** Time defaults to All day; Repeats defaults to Doesn't repeat.
- **Exactly one green button on screen: Add event.** Next and Skip are always quiet.

### Entry points
- **The week's "+ Add event":** nothing is filled in.
- **Tapping empty space in a week column:** Day is filled with that date.
- **Home's Today +:** Day is filled with today.
- **Changing an existing event:** the same screen, titled "Change event", with every row filled; the button reads "Save changes".

### Layout (1920 × 1200, full screen, `bg`)
- **Top bar (128 px tall, padding 0 48):**
  - Close: a 72 px round quiet button.
  - The title "New event" at 44/700, with a context line under it at 21 muted (e.g. "From Calendar · Alex signed in").
  - **Nothing else.** Add event is not in the top bar.
- **Body:** padding 0 48 44, in a grid of `760px | minmax(0, 1fr)` with a 32 px gap.

**Left column: the event**
- **Title field:** 112 px tall, `s1`, radius 28, padding 0 32, text 36/600. While focused it gets a 3 px `accent` inset ring and an accent caret. The placeholder is "What's happening?" in muted.
- **Rows: Who, Day, Time, Repeats.** Each is 116 px tall, radius 28, padding 0 24 0 30, 14 px apart. A row has:
  - a 38 px icon: `group`, `calendar_month`, `schedule` or `repeat`;
  - a label at 18/700, letter-spacing 1.5, uppercase, muted;
  - the value at 28/600, on one line with "…" if too long;
  - an optional tag;
  - a `chevron_right` at the end.
- **Row states:**
  - **Active:** `accentSoft` fill, 3 px `accent` inset ring, accent icon.
  - **Done:** `s1` fill, accent icon, value in `text`.
  - **Not yet set:** `s1` fill, muted icon, placeholder value in muted ("Choose who", "Choose a day", "All day", "Doesn't repeat").
- **Row tags:** 40 px pills with an icon and 18/700 text.
  - **Copied:** `accentSoft` fill, `accent` text, `content_copy` icon.
  - **Clash:** amber at about 15% (`weather` + alpha 0x26 dark / 0x22 light) with `weather` text and a `warning` icon.
- **Foot of the column** (pushed to the bottom, 14 px gaps):
  - A muted line: `event_note` icon, "Saves to the Family calendar", and "Change" in accent at 700. "Change" picks the destination Google calendar; the default is the main calendar.
  - **Add event:** full width, 96 px tall, radius 48, 28/700, with a check icon.
    - **Can't save yet:** a 2 px `s3` inset outline with a muted label, and the reason underneath (info icon, 21 muted): "Needs a title, who and a day", "Needs a day", etc.
    - **Can save:** an `accent` fill with `onAccent` text, and a hint underneath at 21 muted: "Time and repeats are optional"; "Clashes are allowed; both show on the week" (when there's a clash); "Everything's set" (on Check).
- **While the keyboard is up (6a):** rows shrink to 92 px and drop their labels (the values read "Who", "Day", "All day", "Doesn't repeat"), and the column foot is hidden. Nothing may be cut mid-row.

**Right column: the question pane**
- **Pane:** `s1`, radius 36, padding 36 44 40, 22 px gaps.
- **Header:** the question at 40/700. On the right, an optional **Skip** (a text button in muted, 23/700) and **Next: {row}**, a 72 px quiet pill (`s2`, 23/700) with an arrow. **Next is never green.**
- **Skip** appears on Time and Repeats only.

### The frames

**6a, What.**
- **System keyboard** (about 470 px). Its Next key moves on to Who.
- **"From before" suggestions:** past events whose titles match what's typed. Each is a `s2` row, radius 26, with:
  - a colour bar, the title at 28/600 and its pattern at 21 muted;
  - **a line saying exactly what it fills**, in accent at 19/600 with a `content_copy` icon, e.g. "Fills in Mia, 10:00–10:45 and every Saturday";
  - a 64 px "Use this" pill (`accentSoft`/`accent`).
- Under the list: "A suggestion never copies the date."
- **Rule:** a suggestion copies the title, who, start time, length and how it repeats. **It never copies the date or the end of a repeat.**

**6h, after "Use this".**
- The copied rows fill in with the **Copied** tag. Day is active and empty.
- **Banner** at the top of the Day pane (`accentSoft`, radius 22, `content_copy` icon): "Copied from Guitar lesson (last term): who, time and repeats. Tap any row to change it. The date is never copied."
- **Pane title:** "Which Saturday does it start?"
- **Quick picks follow the copied pattern:** the next three Saturdays. Picking a day jumps straight to Check (6g).

**6b, Who.**
- **Person tiles:** 3 columns, 20 px gap, each 240 px tall, radius 30, `s2`. A tile has a 96 px circle in the person's colour with their initial (42/700, #111), the name at 30/700 and the role at 20 muted.
  - **Selected:** fill = person colour + alpha 0x33, a 4 px inset ring in the person's colour, and a 48 px check badge in the top right.
  - **You can select more than one person.**
- **"Everyone" row** (104 px, `s2`): a 60 px Family-purple circle with a `groups` icon, "Everyone" and "Shows as a Family event". Selecting it clears individual picks; it's saved as a Family event, not a shared one.
- **A child signed in:** the other people are locked (outline, padlock, colour dot kept), as in 4a.

**6c, Day.**
- **Quick picks:** 64 px chips. Today, Tomorrow, the next weekend day, and the same weekday next week.
- **Month:**
  - A header with the month at 30/700, and ‹ › in one `s2` pill.
  - Weekday letters at 18/700 muted, **Monday first**.
  - Cells 84 px tall, each holding an 80 px circle with the date at 27 and up to **3 dots of 8 px** (the colours of people with events that day).
  - **Past days:** 35% opacity. **Days outside the month:** 25%.
  - **Today:** a 3 px accent ring and a 700 weight.
  - **Selected:** an `accent` fill with `onAccent` text.
- **"Lasts more than a day":** a switch at the foot of the pane.

**6d, Day over several days.**
- With the switch on: tap the first day, then the last.
- Both end days are filled `accent`. The days in between get an `accentSoft` band across the full cell width; the band is rounded at the start and end and wherever it wraps onto a new line of the month.
- The range **can cross into the next month**, including the trailing days of the visible month.
- **Time is set to All day automatically.** The row value reads "Thu 29 Oct – Sun 1 Nov".

**6e, Time.**
- **Segmented control:** All day | At a time.
- **Hour chips:** in Morning (07–11), Afternoon (12–17) and Evening (18–22) groups, 6 per row, 68 px tall, tabular figures. For an hour outside these, use "Until…" or an "Other time" picker.
- **Busy hours** (the chosen person has a timed event in that hour) get a **2 px amber inset edge and a 10 px amber dot**. All-day events don't count as busy.
- **Minutes:** :00, :15, :30, :45. **How long:** 30 min, 45 min, 1 hour, 2 hours, Until… (picks an exact end time).
- **Result line at the foot:** the range at 40/700 in tabular figures, plus a free/busy check: "Mia is free then" in accent with a `check_circle`.
- **Skip** leaves it as All day.

**6i, Time when the person isn't free.**
- The result line turns amber with a `warning` icon and names the clash: "Overlaps Piano, 15:30–16:15".
- The Time row gets the **Clash** tag.
- Below the result: "Mia is free at" followed by the **nearest free starts for the same length** as 64 px chips (here 14:00 and 16:15), then "or keep it; clashes are allowed."
- **It warns but never blocks.** Add event stays green.
- **With several people:** check each of them and name the busy one ("Sam has Dentist, 12:30").

**6f, Repeats.**
- **Options are worded from the chosen day:** Doesn't repeat; Every Saturday; Every 2 weeks, on Saturday; Every month, on the 4th Saturday; Every year, on 26 September. Each is an 84 px radio row; the selected row has an `accentSoft` fill and a 3 px accent ring.
- **Ends:** a segmented control, Keeps going | Stops after.
- **"Stops after":** **one stepper showing both the count and the last date.** It has 80 px − and + buttons with a centre pill reading "12 times · last on Sat 12 Dec". This replaces separate "on a date" and "after N times" options.
- A hint underneath: "Skip one week, like half-term, later from the event itself." Skipping a single date belongs in Event detail.

**6g, Check and add.**
- **Sentence** at 36/600: "Guitar lesson for Mia, every Saturday, 10:00–10:45, from 26 Sep to 12 Dec. 12 times in all."
- **"First few":** 60 px chips for the first 4 dates with a colour dot, plus "+ 8 more".
- **"On the week":** a preview of the week-view chip.
- **No button in the pane.** Add event at the foot of the left column is the only action.
- **After Add:** go back to the week (on the event's first date), and show a toast for 8 s: `s3` pill, 84 px, with a check icon, "Added Guitar lesson, every Saturday" and an "Undo" button.

### Behaviour
- **Moving on automatically:** after a single-choice pick (Day, Time, Repeats), the next row becomes active about 250 ms later. Who doesn't move on by itself, since you can pick more than one person; use Next.
- **Required fields:** check them live. The reason under the disabled Add event always names what's missing.
- **Close with changes:** confirm with "Discard this event?" (Discard / Keep editing).
- **Busy and clash checks:** use the cached week data for the chosen people, ignoring all-day events.
- **Suggestions:** match against the household's events from the past 12 months by title prefix and words, show at most 3, and treat events with the same title as one entry using the most recent.

---

## 2. Month as the week view title (3a, note 47)
- **Title:** "September" at 68/700, letter-spacing −1, followed by "2026" at 68/400 in muted, 18 px apart.
- **Subtitle (23 muted):** "This week · Wed 23 – Tue 29 Sep · updated from Google 2 min ago".
- **A week across two months:** the title reads "September – October 2026", and the column for the 1st shows a small "Oct" (17/700, accent) above its date.
- **Paging forward:** the subtitle reads "Next week · …", then "In 2 weeks · …", and so on.

---

## 3. Shared events (notes 48 and 49)
A shared event is one event for **two or more named people**. "Everyone" is not shared: it stays a Family event in Family purple.

**Fixed person order:** Admin first, then by age.

**Who line:** "Sam + Mia" for two people; "Alex, Sam +1" for three or more.

**Home: Today and Tomorrow rows (1a, pin 48)**
- The 7 px colour bar splits vertically into **equal stripes, one per person**, keeping the bar's 4 px radius (clip with overflow).
- The meta line uses the who line, e.g. "13:00–13:30 · Sam + Mia".
- **One row per event, never one per person.** Tomorrow rows keep their 75% opacity on the striped bar.

**Week view chips (3a, pin 49)**
- **Background: neutral `s2`, not a person tint.** Mixing tints muddies them.
- **Left edge:** 6 px wide, full height, split into equal person stripes. The chip's left padding grows to 18 px to clear it.
- **Meta dots:** 12 px, one per person, overlapping by 3 px, each with a 2 px ring in the chip's background colour. Then the who line.
- Single-person chips keep their tint and have no edge, so shared ones stand out.
- **Multi-day shared events:** the bar's 6 px left edge becomes the same set of stripes.

**Event detail and Add event:** the Who row reads "Sam + Mia". Changing it opens the multi-select Who pane (6b).

---

## Design tokens
These are the same as the main package. In use here:
- **Amber:** `weather`, #E0B85B dark and #8A6400 light, used for busy hours and clashes. Its soft version is `weather` + alpha 0x26 (dark) or 0x22 (light).
- **Person tint on Who tiles:** alpha 0x33.
- **Shared chip background:** `s2`.

## Files
- `Culvery Home Review.dc.html`: the full reference, with the flow at the top (6a–6i), 3a and the Home artboards (1a–1d).
- `support.js`: the runtime the reference file needs to open.
- `assets/`: the two review screenshots the Culvery mark is cropped from.
