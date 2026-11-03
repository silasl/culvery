# Culvery Plan 16: Birthdays

Brainstormed with the user on 2026-11-03. The look must be on the tablet, finished, by the evening before the next family birthday; that date is in culvery-notes, not here. Visual choices stay provisional for the end-of-v1 design review.

## 1. Intent

On a family member's birthday the wall says so: it greets them by name and dresses up in their colour for the day, and every time the tablet wakes, confetti falls. It is a small daily delight, seen from across the room, and it needs nothing from the family but the yearly birthday event they already keep in the calendar.

Success on the day: the tablet greets the right person by name from midnight to midnight, in their colour, and the confetti falls each time it wakes. Nobody else's birthday is claimed by mistake. Everything the family reads stays as readable as on any other day.

### Goals
- A birthday is found in the calendar: an all-day event with "birthday" in its title, for one household member (3.1).
- The tablet shows that person's birthday look all day: the "full party" option from the brainstorm (2.2).
- Confetti falls once each time the tablet wakes (2.3).
- Settings › Display can preview the look on any day (3.5).

### Non-goals
- Other occasions (anniversaries, name days, Christmas). Birthdays only for now (the user).
- Sound of any kind. The doorbell stays as it is (2.4).
- Anything in the house beyond the tablet: no Hue, Sonos, plugs or Home Assistant. Party mode and Holiday mode are unchanged.
- Ages ("Happy 10th birthday"). Nothing in the event tells us the age reliably.
- Birthdays stored in People. The calendar is the only source.

### Anti-goals (a failure even if it works)
- Someone is greeted on a day that isn't their birthday, or under the wrong name.
- A person's name or birthday reaches a log, a committed file, a test or a screenshot fixture.
- Text the family reads becomes harder to read, in any of the six person colours, day or night.
- Confetti or balloons keep the screen or CPU busy while the tablet is dark or asleep, or make it run warm.
- The night clock lights the room more than it does today.

## 2. Decisions (the user's)

1. **Who counts:** only birthdays tagged for a family member. No tag, no theme. A birthday on the Family row gets nothing. The greeting is "Happy birthday <name>", using the person's name from People.
2. **How far:** full party (option C):
   - The tablet's accent (buttons, switches, the active tab, sliders) becomes the birthday person's colour, over a soft tint of it.
   - Bunting along the top of Home.
   - A cake in place of the Culvery mark in the rail.
   - Balloons drifting slowly up behind Home's content.
   - The big Home clock's digits in **Cherry Bomb One**, in the person's colour.
   - "🎂 Happy birthday <name>" under the date, in a soft pill of the person's colour.
3. **Confetti:** falls once, for about 2½ seconds, each time the tablet wakes on a birthday.
4. **Quiet:** no sound. The doorbell is unchanged.
5. **Birthdays only.** Other occasions are for later.
6. **Duplicates:** two or more birthday events for the same person on the same day are one birthday. (The family's calendar has a duplicate today.)
7. **Sound buttons, later:** when the Sound buttons idea is built, one button switches to "Happy Birthday" on a birthday. Recorded in the follow-ups, not built here.

## 3. Calls I made (to check while reading)

### 3.1 What counts as a birthday
- An event counts when all hold:
  - it is all-day, one day long, and on today;
  - it repeats every year (the user, plan review: a child can't make a repeating event, so a child can't give themselves a birthday look; real birthdays are yearly);
  - its title contains the word "birthday" (case-insensitive, whole word, so "Birthday", "birthday!" and "Sam's birthday" count, "birthdays" too; "bday" does not);
  - its "for" tag names one or more household members (the user: no tag, no theme).
- Only the tag counts. An untagged event never falls back to the calendar it came from, as other events do (`resolveIds`), so "Grandma's birthday" in a parent's own calendar greets nobody. Tag ids that aren't in People, and Family, are ignored; if nothing is left, it gets nothing.
- An event tagged for two people ("Sam and Alex's birthday") is a birthday for each of them.
- Timed events don't count, so "Sam's birthday party, 14:00" at a friend's house doesn't make it Sam's birthday.
- Accepted risk (the user, plan review): a yearly all-day "Leo's birthday" tagged for Sam would greet Sam. Titles aren't matched against names, because "Mum's birthday" tagged for a person listed by first name must still count.

### 3.2 Which look wins
- A birthday's look beats a seasonal theme on the same day (a birthday on 31 October shows the birthday, not Halloween).
- The doorbell's *Follow the theme* keeps following the seasonal theme alone. A birthday never changes the doorbell.
- Two people's birthdays on one day: the greeting names both, "Happy birthday Sam and Alex" (three: "Sam, Alex and Mia"), in People order. The colours, clock and cake take the first of them in People order.
- The look starts and ends at local midnight with the same 400 ms colour fade as a seasonal theme. The night clock is showing then, so the change is unseen until morning. The night clock is unchanged.

### 3.3 Confetti
- "Wakes" means the screen goes from asleep (the dark screen) or the night clock to awake (Plan 8's `Screen` changing to `AWAKE`). Starting the app does not count, and neither does Culvery leaving and coming back to the front.
- Confetti doesn't fall during the night hours (the user, plan review: a tap on the night clock in a dark room gets no confetti), when animations are off (`AnimatorsOn`), or while the door card, a PIN pad or any alert is showing. It falls over whatever screen is showing, never takes a touch, and is skipped by TalkBack.
- Confetti colours: the six person colours and the accent. About 60 pieces, gone by 3 seconds.

### 3.4 Balloons
- Like Halloween's bats: drawn behind Home's content, faint enough to leave every text's contrast unchanged, drifting only while Home shows and the screen is awake and animations are on. Otherwise drawn still.

### 3.5 Preview
- Settings › Display's "Seasonal themes" section gets a **Birthday** row with a Preview on/off control, behind the same PIN. It previews the first person in People order, confetti on wake included, so the look can be checked in full from across the room. It ends when turned off or at midnight, as a seasonal preview does, and turning one preview on turns any other off.
- What shows, in order: a preview (seasonal or birthday) beats a real birthday, which beats a seasonal theme in its dates.

### 3.6 Personal data
- No name, title, date or person id in any log line. Logs may say a birthday look started or ended and how many people, nothing more.
- Tests and screenshots use made-up names ("Sam", "Alex"). Nothing real in committed files.

## 4. How it works

### 4.1 Finding today's birthdays
- The calendar capability works out today's birthday people from today's stored events only (the user, plan review: not the 12-week held events), by the rule in 3.1, deduplicated by person and ordered by People. It re-reads when the household's date changes and when today's stored events change. A birthday added on the tablet shows after its next sync.
- It publishes the list through a seam in `core` so the app and `core/ui` can read it without depending on the calendar module, as `ShowingTheme` does for the seasonal theme. The builder picks the seam.
- If the calendar hasn't synced since the household's date changed, the stored events from the last sync still count, however old that sync is. A yearly event is stored well ahead, so a birthday shows even if the morning sync fails.
- Before the day (the user, plan review): check on the tablet that the real birthday event is yearly, all-day and tagged for the person, logging nothing, and tag the series if it isn't.

### 4.2 The birthday look
- Plan 14's `SeasonalLook` holds a fixed look per enum entry. A birthday look is built from a person's colour, so it can't be an enum entry as it stands. The builder decides the shape. One option: generalise "what's showing" to a look (seasonal or birthday) with `SeasonalTheme` and a birthday-for-a-colour both producing a `SeasonalLook`. Whatever the shape:
  - `CulveryTheme` gets its colours from the look showing.
  - Home's clock face, rail mark, card corner (none for birthdays) and sky read the same look slots they read today.
  - The Door module still reads only the seasonal theme (3.2).
- **Colours:** person colours are a fixed set of six (`PersonPalette`), so the birthday colour sets are twelve fixed sets (six colours × day and night), worked out ahead in code, not computed at run time from arbitrary colours. Each starts from the normal day or night set and changes `accent`, `accentInk`, `accentSoft`, `accentOnInk` and a soft tint of `bg`/`surf2`/`line` towards the person colour. Yellow and lime will need a much darker accent by day to pass. The mockup's pink, for a start: day `bg` #F7EAEE, `surf2` #F3E2E8, `line` #EBD3DC, `accent` near #C2457E, `accentSoft` #F8DCE8.
- **Contrast:** extend the existing contrast tests to loop over all twelve sets, as Plan 14 did for Halloween. `danger` must stay clearly apart from `accent` in every set (the Halloween check), which matters most for the pink and orange people.
- **Clock face:** Cherry Bomb One, each digit in a fixed slot as Creepster is, in the look's accent, sized so the header doesn't jump (Plan 14 4.4's rules).
- **Drawings,** our own vector paths in code, nothing downloaded:
  - Bunting: a string of small triangular flags in the six person colours across the top of Home, above the header and clear of the header's text.
  - Cake: the rail mark's size and place, in the person's colour, with a candle. A picture only, skipped by TalkBack.
  - Balloons: three to five, in the person colours, rising slowly and wrapping.
  - The greeting pill: under the date, ink on a soft pill of the person colour; TalkBack reads "Happy birthday Sam" (no emoji spoken).

### 4.3 The font
- Cherry Bomb One (SIL Open Font Licence 1.1 by the brainstorm's preview; to be confirmed from the font's own `OFL.txt`). Download from github.com/google/fonts (`ofl/cherrybombone/`) at a pinned commit with its METADATA and a sha256, as Creepster was. Commit the licence as `core/ui/licenses/OFL-CherryBombOne.txt`. The user approved this download on 2026-11-03; if its licence file isn't OFL, stop and ask.

### 4.4 Settings
- Settings › Display, "Seasonal themes": the Birthday preview row (3.5). Its words follow the page's style. While on, it says it ends at midnight.

## 5. Constraints and house rules
- Branch `plan-16-birthdays` in the one `culvery` checkout (no worktrees). Plain-sentence commits, no trailers. Never push without asking, and check for personal data first.
- No personal data in logs or committed files (3.6).
- No deprecated APIs without asking the user.
- Test gate: `./gradlew testDebugUnitTest verifyRoborazziDebug`.
- Subagents use `C:/t9` for scratch.
- On the tablet: `install -r`, never uninstall or wipe.
- Time: on the tablet by the evening before the next family birthday.

## 6. Left to the builder
- The seam for today's birthdays and the shape that lets a look be seasonal or birthday (4.1, 4.2).
- The twelve colour sets' final values, within the checks.
- The bunting, cake and balloons' paths, sizes and speeds; the confetti's motion.
- Where the greeting pill sits exactly, and whether the clock's header band needs room for the bunting.

## 7. Walk-through checks
- Emulator: a made-up person's birthday by a sample event (all-day, "Sam's birthday", tagged for Sam), day and night; a duplicate; an untagged birthday in a person's own calendar (no look); two people on one day; a Family birthday and a timed "birthday party" (no look); the preview on, off and ending at midnight; a birthday on a Halloween day (birthday wins; the doorbell still plays the organ); waking from the dark screen and from the night clock (confetti); animations off (no confetti, still balloons).
- Each of the six person colours, day and night, on the screenshot tests.
- Tablet (release, `install -r`): the user turns the preview on and looks from across the room, then sees the real day.
