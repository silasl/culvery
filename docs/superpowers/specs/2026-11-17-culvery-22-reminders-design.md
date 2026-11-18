# Culvery Plan 22: Reminders

Brainstormed with the user from 2026-11-13 to 2026-11-17. This is follow-up item 20. Visual choices stay provisional for the end-of-v1 design review. A reminder's words and the people it names are personal: they appear in no log, test, fixture, screenshot or committed file. Tests and screenshots use the sample household.

## 1. Intent

The wall reminds the family of things at a set time: a card opens over the screen with a short chime and stays until someone deals with it. The first case is the evening before a bin collection, naming the bins that go out, from the dates Plan 17 already reads. The family also makes their own reminders on the wall, once or on chosen weekdays, each for a person or for Family.

Success: at 18:00 the evening before a collection the wall chimes and shows "Bins out tonight" with the right bins, and whoever taps Done ends it. A child's "PE kit" reminder shows every Friday morning in their colour. Nothing lights or sounds in night hours.

### Goals
- A reminder made on the wall: words, who it's for, a time, and one day or a repeat from the same choices as an event (2.1, 2.7, 10).
- A reminder added from a calendar event's card, which then follows that event (10).
- A card that stays until Done or Later, with one chime (2.2, 2.4, 2.5).
- Night hours, door quiet hours, Holiday mode and the doorbell are respected (2.3, 4.4).
- Bins the evening before, from the calendar's stored collections (2.9).
- A Reminders tab that lists them and holds the Add button (2.8).

### Non-goals
Each is on the follow-ups list under Plan 22.
- Reminders made on a phone or read from Google (a "Reminders" calendar was the route weighed).
- A repeat that ends on a date, or one the calendar's picker doesn't offer (section 10 lists what it offers).
- Following one date of a repeating event that was skipped or moved on a phone.
- A sound chosen per reminder, a chime that repeats, or a spoken reminder.
- Anything drawn on the night clock or the dark screen.
- Reminders shown on the calendar or on a Home card.
- Night-holding in the alert layer for every capability (the Plan 8 follow-up). Reminders applies its own rules.

### Anti-goals (a failure even if it works)
- A card or chime in night hours without a tap, or anything drawn over the night clock.
- A reminder that takes the door card's place, or a chime over a ring.
- The wall kept lit all day by an unanswered card.
- A reminder lost because the wall restarted at its time.
- A child deleting or changing a reminder they didn't make.
- A reminder's words, or a person's name, in a log line.
- The wrong bins, or the wrong evening.

## 2. Decisions (the user's)

### 2.1 What a reminder is
Bins the evening before, plus reminders made on the wall. Phone and Google stay out of this plan.

### 2.2 A card that stays
A reminder shows as a card over the screen, like the door card. It wakes a dark screen by day. It stays until someone taps Done or Later; it has no timeout. It never shows in night hours unprompted.

### 2.3 Night hours
A reminder that comes due in night hours, and a card still unanswered when night starts, both wait. If someone taps the night clock awake, the waiting card shows then, silently. If nobody does, it shows with its chime when night ends. The night clock itself is untouched: Plan 8's ruling that nothing draws over it stands.

### 2.4 The sound
One short chime of its own, once, when the card opens. It is not the doorbell and it does not repeat. A ring always wins. The card is silent in door quiet hours. Reminders wait during Holiday mode.

### 2.5 Done and Later
Done ends the reminder (for a repeating one, until its next time). Later closes the card and brings it back in 30 minutes with its chime; it can be tapped again. Anyone at the wall may tap either, with no PIN.

### 2.6 Whose it is, and who may change it
As the calendar: a reminder is for one or more people, or Family, and the card carries their colour and name. Adding, changing and deleting follow the events rule and PIN: adults for anyone; children add for themselves and change what they made.

### 2.7 Once or repeating
A reminder is for one day, or repeats. As first built it repeated weekly on chosen weekdays only; section 10 (2026-11-18) replaces that with the calendar's own repeat choices, shared by both.

### 2.8 Its own tab
"Reminders" on the rail, between Shopping and Music, with the list, the Add button, and a count on the rail icon.

### 2.9 Bins
One set time the evening before each collection, 18:00 unless changed. Unanswered at night, it waits under 2.3. On collection day it reads "Bins out today". At 12:00 on collection day it clears itself.

### 2.10 The card doesn't hold the wall awake
Today anything on the alert layer keeps the wall lit. A reminder card must not:
- By day it wakes a dark screen when it arrives. With nobody about, the wall goes dark after its usual time, and the card is there, silently, at the next wake (a tap or the camera).
- At night it shows for the minute the wall is awake after a tap. Then the clock returns and the reminder goes on waiting.

### 2.11 Approach
See section 3: a new capability on the wall's own clock.

## 3. Approaches

1. **A new Reminders capability on the wall's own clock (chosen).** A `:capability:reminders` module with its tab, a Room database, and an engine on the existing minute tick. Bins come through a seam in `:core:house` that the calendar implements. The card uses the alert layer and the chime uses `:core:sound`. It matches how every other feature is built and leaves the calendar as it is.
2. **Inside the calendar capability.** Reminders as a kind of stored event. No seam is needed, but the calendar is the largest module, its store is shaped around Google's events and the outbox, and reminders would inherit sync and permission code for no gain.
3. **Android alarms.** AlarmManager fires each reminder. The wall is the pinned home app and always in front, so nothing is gained; it adds a permission and a boot receiver, and breaks the stated "no alarms" choice (`HouseModeScheduler`).

## 4. How it works

### 4.1 The reminder
- **Words:** 1 to 60 characters after trimming.
- **For:** one or more people, or Family. Stored as person ids, never names.
- **Made by:** the signed-in person's id.
- **Time:** a time of day in the household's zone, to the minute.
- **When:** one date, or a non-empty set of weekdays.

A one-off must be in the future when saved: "Pick a time that hasn’t passed." The list holds at most 50 reminders (bins aside): "The list is full. Delete a reminder to add another."

### 4.2 Waiting
Each reminder keeps one moment, **answered at**: when it was made, last changed, or last marked Done. A reminder is **waiting** when one of its times falls after "answered at" and at or before now. This one rule gives:
- **Missed while off.** A time that passed while the wall was off or restarting is still after "answered at", so the reminder is waiting when the wall is back.
- **No backlog.** A repeating reminder with three missed times is waiting once. Its card shows the latest of them.
- **Never answered.** A repeating reminder still waiting at its next time stays one card, now showing the newer time.
- **Done.** Sets "answered at" to now. A one-off is then deleted.
- **A change** to a reminder's time or days sets "answered at" to now, so an edit never fires for a time already past.

**Later** stores "snoozed until" (now + 30 minutes). A snoozed reminder is still waiting, but its card stays closed until then. Done, a change, or a newer time clears the snooze.

An unanswered one-off waits indefinitely.

### 4.3 The card
- It goes on the alert layer: above tabs, sheets and Settings, below the PIN pad.
- It shows an alarm-clock mark, the words, a stripe and name for who it's for (one stripe per person, "Family" for Family), the time it was due, and Later and Done.
- **Several waiting:** one card at a time, oldest due first, marked "1 of 2". Done or Later moves to the next.
- TalkBack reads it as the door card is read: the words, who, then the two buttons.

### 4.4 When the card shows, and when it chimes
The engine decides each minute, and at once when any input below changes.

The card is **up** when at least one reminder is waiting and not snoozed, and none of these hold:
- setup isn't complete, or Culvery isn't in front;
- Holiday mode is on, or its state isn't known yet;
- it is night (or night isn't known yet) and the wall isn't lit;
- a door card is showing.

A reminder is **owed one chime** each time:
- one of its times arrives (the first, or a newer one on a repeating reminder that is still waiting);
- its snooze ends;
- night ends, or Holiday mode ends, while it is waiting and not snoozed. This is the user's ruling in 2.3: a card ignored the evening before chimes again in the morning.

The chime is paid the first time the card is up while sound is allowed: not night, and not door quiet hours. Several owed at once are paid with one chime. What is owed is stored, so a restart neither loses a chime nor plays one twice.

**The wall wakes**, by day, whenever a chime becomes owed and whenever one is paid, even if the card was already up on a dark screen. A chime never plays to a dark wall.

| When | The card | The chime |
|---|---|---|
| Day, wall lit | shows at once | plays |
| Day, wall dark | wakes the wall and shows | plays |
| Nobody about for the sleep time | the wall goes dark over it; it is there at the next wake | already paid, so silent |
| A newer time arrives on a waiting reminder | stays up, showing the newer time; wakes the wall | plays |
| Door quiet hours, before night | shows | owed until quiet hours end |
| Night, wall on the clock | stays down | owed |
| Night, someone taps the clock | shows for the awake minute, then comes down | owed |
| Night ends | shows, waking the wall | plays, even if it chimed the evening before |
| Holiday mode | stays down until Holiday ends | plays then, if allowed |
| A door card is up, or a ring arrives | comes down; returns when the door card closes | plays then, if still owed |
| Another app in front, or setup running | stays down until the wall is back | plays then, if allowed |

- **Waking** is as a tap would wake the wall. The card then counts for nothing: the wall's sleep timer runs as if the card weren't there.
- **The card blocks the wall.** Like the door card, it takes every tap until Done or Later, including for someone who taps the night clock awake.
- **The chime** plays at the doorbell's volume (the `DoorQuiet` seam's `volume`). It never starts while a ring is opening or playing.
- **Music** ducks for the chime and comes back when the chime ends. It does not stay down while the card is up: the card has no timeout, unlike the door card's 60 seconds.
- **Door quiet hours** are Door's own setting (`DoorQuiet.quietNow()`). With quiet hours switched off there, only night silences the chime.
- **With no door capability,** the chime plays at full volume and only night silences it. With the capability but no doorbell connected, it plays at the saved doorbell volume: Door's own values are left as they are.
- **A ring during the chime** cuts it, as it cuts a pad.

### 4.5 The tab
- **Rail:** label "Reminders", an alarm-clock icon, order 25. Always there once setup is complete.
- **Badge:** the count of waiting reminders, snoozed ones included.
- **List**, in three groups, each shown only when it has rows:
  - **Waiting:** each with the time it was due, or "Back at 18:30" when snoozed, and a Done button anyone may tap. A waiting bins reminder is a row here like any other, and counts in the badge.
  - **Today:** reminders whose next time is today.
  - **Coming up:** the rest, by next time, with "Every Friday", "Mon to Fri", "Every day" or "Once".
- Each row has the person stripe, the time, the words and who.
- **Empty:** "No reminders yet. Tap Add reminder to make one."
- **The Bins row** sits at the foot (4.7).

### 4.6 Adding and changing
- **Add reminder** opens the form as a sheet, as Add event does. "Add reminder" in the form asks who's there (the PIN pad). While a child's PIN is still in force when the form opens, the form is theirs from the start.
- **The form:** the words; who it's for; Once or Repeats; a day (today by default, up to a year ahead) or seven day chips; the time. Save is "Add reminder" on a new one and "Save changes" on an existing one.
- **Tapping a row** opens the same form with "Delete reminder". "Save changes" and "Delete reminder" ask who's there. Deleting shows "Reminder deleted."; there is no Undo, as with a deleted event.
- **After a PIN,** the next changes need none for two minutes, whoever makes them. This is the events rule, accepted here as it is there.
- **Permissions**, registered as the calendar's are:

| Permission | Who |
|---|---|
| Add reminders for anyone | Admin, Adult |
| Add your own reminders | everyone |
| Change any reminder | Admin, Adult |
| Change reminders you made | everyone |
| Change the bins reminder | Admin, Adult |

- A child's who-picker is fixed to them. A child may make a repeating reminder: unlike a repeating event, they can delete it.
- Refusals use the calendar's wording pattern: "<Name> can only change reminders they made."
- **A person removed from the household** drops off their reminders. A reminder left with nobody shows as Family's. A reminder made by a removed person is changeable by adults only.

### 4.7 Bins
- **The seam.** `:core:house` gains a seam the calendar implements: the stored collections from today on, each with its date and its bins (wall name and chosen colour, in Plan 17's order), plus whether a bins connection exists and whether its last read failed. It reads the calendar's store; it makes no council reads.
- **The reminder.** For each collection on date D, with the set time T: it is waiting while the switch is on, now is from T on D−1 up to 12:00 on D, and it hasn't been marked Done for D. Between those it behaves as any reminder: the card, the chime, Later, night, quiet hours.
  - So switching it on after T the evening before makes it waiting at once, and moving T later takes the card down until the new time.
  - Words: "Bins out tonight" until midnight, then "Bins out today".
  - Below: the bin names ("Food, Recycling") with one stripe per bin in its colour. Who reads "Bins".
  - Done ends it for that collection.
- **The Bins row** on the tab: "Bins · The evening before a collection, 18:00" with a switch. It shows only while a bins connection exists and starts switched on. Tapping it offers the switch and the time; "Save changes" asks for an adult's PIN.
- **The time** must fall from 12:00 up to the start of night hours: "Pick a time from midday until night hours start." If night hours are later moved over it, the reminder follows the night rule.
- **Can't read.** While the last council read has failed, the row adds "Can’t read the bin dates". The reminder still fires from stored dates. With no stored collections nothing fires.
- **Late or changed dates.** A collection first seen after T on D−1 is waiting at once. If a waiting collection's bins change, the card follows. A collection that disappears from the store before its reminder starts has none. Once the reminder has started, Reminders keeps that collection itself until Done or 12:00 on D, whatever the calendar then lists: the council's page may drop a collection on its own morning.
- **Holiday.** A bins reminder still clears at 12:00 on D during Holiday, so it is never shown late.
- **Two collections can't overlap:** one clears at 12:00 and the next can't start before 12:00.
- **A hidden bins calendar** gives no reminders: the seam reads what the calendar shows, as the other calendar seams do.
- **More than one bins connection:** collections on the same date are merged into one reminder.

## 5. The pieces

- **`:capability:reminders`:** the capability (tab, badge, permissions), the store, the engine, the card, the form. Added to `settings.gradle.kts` and the app's dependencies as the README's "Adding a capability" says.
- **Store:** Room, `reminders.db`, schema exported. One table for reminders (4.1 plus "answered at", "snoozed until" and the chime paid mark). The bins switch, time and per-collection state go wherever the builder finds simplest (the same database or a DataStore file).
- **Engine:** a `Startable` on `@ApplicationScope`. It reads time only from `HouseholdClock` and `WallClock`, so tests and the debug clock drive it.
- **`:core:house`:** the bins seam (4.7).
- **`:capability:calendar`:** implements the seam from `CalendarStore`, as `CalendarBirthdays` and `CalendarMultiDayEvents` do.
- **`:core:sound`:** a new built-in chime, about a second, two rising notes, generated by `tools/sound` like the other built-ins. It is left off the doorbell and pad lists. A play call that refuses while a ring is opening or playing.
- **`:core:plugin` and the shell:** `AlertHost` lets an alert say it doesn't hold the wall awake, lets Reminders wake a day dark screen, and lets music duck for a chime's length without the alert staying loud. The door card keeps holding.
- **Sharing the alert layer.** There is one alert at a time. Reminders must be able to tell when its card has been replaced, and must never replace an alert that isn't its own. Door's side needs the same care: today it dismisses whatever is on the layer, so a ring's close must not take a reminder card with it, and a reminder must not be left over a door card.
- **`:core:ui`:** an alarm-clock icon joins the icon set (there is none today).
- **Shared pickers.** The who-picker and the day and time controls live in the calendar module today. Reminders can't depend on that module, so what it reuses moves to `:core:ui`; nothing is copied.
- **No new Android permission, no alarm, no network call.**

## 6. Logging and privacy

- One tag, `Reminders`.
- Lines carry counts and states only: `due 2`, `card up 1 of 2`, `chime`, `held night`, `held holiday`, `held ring`, `held away`, `done`, `later`, `bins due`, `bins cleared`. Never the words, a name, a person id or a time someone chose.
- `LogHygieneTest` enforces this; any new interpolations are added to its allow-list as counts or enum names.
- The stored types hide their words and people in `toString`, as `Sound` and `ModeRecord` do.

## 7. Tests

- **Timing, as unit tests with a fake clock:** once; weekly days; a clock change (the spring and autumn hours); missed while off; no backlog; Done; Later and a second Later; a change resetting "answered at"; the 50 limit.
- **The engine:** each row of 4.4's table, the chime paid once across a restart, a chime owed again at night's end, several waiting sharing one chime, the ring stepping in mid-card and mid-chime, music back up when the chime ends.
- **Bins:** evening to noon, "tonight" to "today", late-seen, changed, vanished before it starts, kept once started, switched off, Holiday, a failed read with stored dates.
- **Permissions:** adult, child (own and not own), the removed person.
- **The alert layer:** a non-holding alert doesn't hold; it wakes a day dark screen once; the door card still holds.
- **Screenshots**, light and dark: the tab filled, empty, and with the Bins row and its can't-read line; the form (once, repeating); the card for one person, two people, bins, and "1 of 2".
- **Copy:** every shown string passes `CopyScan`.

## 8. The walk-through (emulator)

Driven by the debug clock and the sample council, waiting on the log lines in section 6:
1. Add a one-off for a few minutes ahead; move the clock; the card opens and chimes.
2. Later; move 30 minutes; it returns with its chime. Done ends it.
3. A weekly reminder; move to its day; then a week on.
4. Move to 18:00: "Bins out tonight" with the sample's bins. Leave it; move past midnight: "Bins out today". Move to 12:00: gone.
5. Night: a reminder due at 06:45 stays down; a tap shows it silently for a minute; at 07:00 it shows and chimes.
6. Door quiet hours: the card shows silently.
7. Day, dark screen: the card wakes the wall; left alone, the wall goes dark again; a tap shows the card.
8. A ring while a card is up: the door card takes over, then the reminder returns.
9. A child signs in: adds their own, can't change an adult's.
10. Holiday mode on: nothing shows; off: the waiting card appears.
11. Restart the app with a reminder waiting: it is back, with no second chime.

## 9. Left to the builder

- The table and column shapes, and where the bins state is kept.
- How Reminders learns its card was replaced (a handle from `show`, an owner mark, or the existing `DoorQuiet.ringShowing`).
- The shape of the three `AlertHost` additions (not holding, waking, a duck for the chime only).
- The exact layout of the tab, form and card, following the redesign handoff's tokens and the door card's shape.
- The chime's notes.

## 10. Amendment, 2026-11-18: shared repeats, and reminders on calendar events

Ruled by the user after the first walk-through. Where this section differs from 2.7, 4.1, 4.2, 4.5, 4.6, 5 or 7, this section wins.

### 10.1 The user's rulings
1. The calendar's repeat choices move out of the calendar into shared code, and events and reminders use the very same choices and the same picker. Reminders get no cut-down version.
2. The shared picker gains **weekly on several chosen days**, so events get it too.
3. A reminder added from an event whose repeat the picker can't show (made on a phone: every 3 months, monthly by date, until a date) follows that repeat anyway. The form shows it in words; the person may switch to one of the picker's choices.
4. **Add reminder** is on every event's card and opens the reminder form filled in. Once the event has a reminder the button reads **Change reminder** and opens it. One reminder per event.
5. A linked reminder follows its event:
   - event deleted, on the wall or a phone: the reminder goes; on the wall the toast is "Event and its reminder deleted.";
   - event moved: the reminder keeps its gap before the event;
   - a repeating event's whole series deleted, or its time or repeat changed on a phone: the reminder follows;
   - one date of a series skipped or moved on a phone: not followed.
6. From the walk-through: the "time has passed" check comes before the PIN pad (W1); a child's own ticked tile in the who-picker loses its padlock (W3). "Save changes" follows whatever the calendar's editor does when nothing has changed (W4).

### 10.2 The repeat choices
- **Choices**, in the picker's order: Doesn’t repeat; weekly; every 2 weeks; monthly on the nth weekday; yearly; daily. Each is worded from the chosen day, as today.
- **Chosen days.** With weekly or every 2 weeks picked, a row of seven day chips shows. The chosen day's own weekday is ticked and can't be unticked; the others add days. On an event the chips are locked once the event is longer than a day.
- **Ends:** Keeps going, or Stops after 2 to 52 times. With several days, each day counts as one time.
- **Words.** One day: "Every Saturday", "Every 2 weeks, on Saturday". Several: "Every week, on Mon, Wed", "Every week, on Mon to Fri" (three or more in a row), "Every 2 weeks, on Mon, Wed". Weekly on all seven reads "Every day".
- **Written** as the RRULE Google expects: `BYDAY=MO,WE` on a weekly rule with more than one day.
- **Read back.** A phone's weekly rule with a list of days that includes the first day reads as this choice; the detail card's Repeats row words it.
- **Worked out in one place.** Shared code answers, for any rule it understands and a first day: the dates in order, the latest on or before a day, the first after a day, and the same rule started from a later date (its count reduced). It understands FREQ, INTERVAL, a weekly BYDAY list, a monthly BYDAY of one nth weekday, a monthly BYMONTHDAY of one day, COUNT and UNTIL. Anything else it refuses; it never guesses. The sample calendar uses it in place of its own copy.

### 10.3 The reminder
- **When** (replaces 4.1): a first day, a time, and either nothing (once) or a repeat rule. The seven weekday chips of the first build go; "PE kit, Mon and Wed" is weekly on chosen days from the shared picker.
- **Waiting** (4.2) is unchanged in meaning, worked out from the rule's dates for any period, not from the last eight days.
- **Done** on a repeat whose last time has been answered deletes it, as it deletes a one-off.
- **The form** (replaces 4.6's list): the words; who it's for; the day (today by default, up to a year ahead); the time; Repeats, with the shared picker. The Once / Repeats switch goes.
- **Past.** A one-off must be in the future, checked before the PIN pad and again after it. A repeating reminder has no such check: its first time is the next one after it is saved.
- **The list** words a repeat as the event's detail card does: "Once", "Every Friday", "Every week, on Mon to Fri", "Every month, on the 4th Saturday · 6 times".
- **The database** is redefined at version 1: it has only ever existed on the emulator. The emulator's copy is deleted by hand.

### 10.4 Add reminder on an event
- **Where.** A button on the detail card of every event, changeable or not: "Add reminder", or "Change reminder" when the event has one. It isn't shown on a bin collection (bins have their own), on a one-off event that has started, on a repeating event with no time left, or when the wall has no Reminders.
- **What opens.** The reminder form, as a new reminder:
  - words: the event's title, cut to 60;
  - who: the event's people, or Family when it has none (a signed-in child's form is still only theirs);
  - day and time: the event's start; 08:00 on the day for an all-day event; for a repeating event, the date tapped, or the next one if that has passed;
  - repeats: the event's repeat from that date, with its count reduced by the times already gone. A repeat the picker can't show sits as its own row, "As the event: Every 3 months", ticked. A repeat the shared code can't work out gives a one-off for that date.
- **Saving** is the reminder's own rule and PIN (4.6). Nothing is saved until "Add reminder" in the form.
- **Change reminder** opens the reminder as tapping its row would.

### 10.5 The link
- A reminder saved from that form remembers its event: the event's key, its start as it was then, and its repeat as it was then. The key is the calendar's and means nothing to Reminders. A repeating event is one key for the whole series, and an event made repeating on a phone keeps its key.
- A reminder saved on the event's own day with the event's repeat untouched **follows the repeat**. One the person moved to another day, or gave another repeat, keeps its own repeat.
- **Event deleted on the wall,** sent or still queued: the reminder is deleted at once with no second PIN, and the toast reads "Event and its reminder deleted." Undo of a just-added event takes its reminder too. If a queued delete is later refused, the event comes back without its reminder.
- **Event moved on the wall:** the reminder's day and time shift by the same amount. The toast reads "Changes saved. Its reminder moved too."
- **From a phone,** found at sync:
  - an event the sync removes: its reminder is deleted. Ageing out of the stored weeks, a hidden or removed calendar, and an event that was never stored are not removals;
  - a changed start: the reminder shifts by the same amount;
  - a series whose usual time changes (the commonest time of its coming dates): the reminder shifts;
  - a series whose repeat changes: a reminder that follows the repeat takes the new one, from its next date; one with its own repeat only shifts.
  - An event with a change still queued on the wall is left alone until that change is sent.
- **A shifted reminder** starts again from now. If its event's new start has already passed, a one-off is deleted. If only the reminder's time has passed, it shows now.
- **Not followed:** a change of title or people; one date of a series skipped or moved.
- **The reminder's own life is unchanged.** It can be changed or deleted on the Reminders tab; deleting it leaves the event alone. If the calendar is disconnected the reminder stays and simply stops following.
- A sync lost to a restart between reading and telling Reminders can leave a reminder for a deleted event. It is an ordinary reminder then, deleted by hand.

### 10.6 The pieces
- **`:core:repeat`** (new): the choices, the rule writing and reading, the dates, the words. Pure `java.time`. The calendar and both providers import it from there.
- **`:core:ui`:** the picker and its state, used by the event editor's Repeats pane and the reminder form. The calendar keeps only what is its own: "Choose a day first", "Too long to repeat that often.", and the skip-one-date hint.
- **`:core:house`:** one seam, implemented by Reminders and optional in the calendar: open the form for an event, whether an event has a reminder, an event has gone, an event's times are now these.
- **`:capability:calendar`:** the button; the calls from delete, Undo and a move; what a sync removed or changed, worked out inside the sync's own transaction.
- **`:capability:reminders`:** the schedule as a first day and a rule; the link columns; the seam.
- **Logs** gain `event gone` and `event moved` on the `Reminders` tag, with nothing else on the line.

### 10.7 Tests
- **Shared dates:** each choice; several days; a count across several days; every 3 months; monthly by date skipping short months; UNTIL; the latest on or before and the first after, years from the first day; a rule started later with its count reduced; a rule it refuses.
- **The calendar still passes** its repeat tests from the new package, plus chosen days written, read back and worded; the sample calendar expanding them.
- **Reminders timing** over a fortnightly, a monthly and a counted rule: missed while off, no backlog, Done on the last time.
- **The link:** deleted on the wall (sent and queued); Undo of an add; moved on the wall; removed, moved and re-ruled at sync; ageing out and a hidden calendar leaving the reminder; a queued change holding a sync off; one moved date of a series changing nothing.
- **The form from an event:** one-off, all-day, a series from a later date, a rule the picker can't show, a child.
- **Screenshots:** the picker with chosen days (event editor and reminder form); the detail card with each button; the form opened from an event.

### 10.8 More walk-through checks
12. A repeating reminder, weekly on two days: it fires on both.
13. A monthly reminder: move a month on; it fires once.
14. An event repeating weekly on two days, made on the wall: it shows on both days.
15. Add reminder from a one-off event; move the event an hour: the reminder moves. Delete the event: the reminder goes with the toast.
16. Add reminder from a repeating sample event: the form shows its repeat; the card then reads Change reminder.
17. Remove an event from the sample calendar's side (debug): the next sync removes its reminder.
18. A one-off for a time just passed is refused before the PIN pad. A child's own tile has no padlock.
