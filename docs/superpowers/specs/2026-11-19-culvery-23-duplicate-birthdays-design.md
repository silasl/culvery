# Culvery Plan 23: Birthdays that show twice

Written on 2026-11-19 from the user's ruling of the same day ("all as suggested"). Follow-ups item 42. The branch is `plan-23-duplicate-birthdays`, cut from main.

## 1. Intent

A birthday shows once on the wall.

Success: an event of Google's "birthday" kind is never stored or shown, with a test that fails without the fix. Every other event reads as it does today.

### Non-goals
- Matching events by name or date to find doubles. The kind decides, nothing else.
- Changing or deleting anything in Google Calendar.
- Changing which events give the birthday look (Plan 16's rule stands: a tagged, yearly, all-day event with "birthday" in its title).

### Anti-goal (a failure even if it works)
- A birthday the family made by hand stops showing.

## 2. The cause

Google Calendar has its own kind of event for a birthday, made with its Birthday button or from a contact. The API marks it `eventType: "birthday"`. It is private to the account, starts in the birth year when one was given, and Google refuses most changes to it.

The household's calendar holds one of these beside the family's own yearly event for the same person, so the wall showed that birthday twice. The provider leaves out one kind today, `workingLocation` (`GoogleEvent.isGone` in `GoogleEvents.kt`); every other kind is read as an ordinary event.

## 3. The change

`GoogleEvent.isGone` is also true for `eventType == "birthday"`. That one property already decides all of these, so no other code changes:

- A full read skips the event.
- An incremental read lists its id as removed, so a copy the tablet already holds goes when Google next reports the event. A copy Google doesn't report again goes at the next full read, which comes at least every six weeks.
- A look-up of the event answers "gone", as for a working location.

No database change and no new setting.

## 4. What the household gives up (ruled)

A birthday that exists only as Google's kind doesn't show on the wall. None does today. The wall could not tag such an event for a person anyway, so it could never give the birthday look.

## 5. Tests

In `GoogleReadTest`, beside the working-location test:

- A birthday-kind event is skipped by a full read and listed as removed by an incremental one. It fails before the change.
- A hand-made yearly all-day event titled as a birthday, with no kind or with `default`, is still read. It guards the anti-goal.

## 6. Checking it

- The emulator runs the sample household, which doesn't read Google, so its walk-through only shows that the calendar and the sample birthdays are unchanged.
- On the tablet the doubled birthday's next date is a year away, outside the weeks the tablet reads, so there is nothing to see there either until then. The test is the proof. A stronger check is the user's to choose: make a Birthday in Google Calendar for a date in the coming weeks and see that the wall leaves it out.
