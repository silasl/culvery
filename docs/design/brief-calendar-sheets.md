# Design brief: Culvery calendar sheets (hand-off addition)

This extends the existing **Culvery** hand-off (`docs/design/house_hub_handoff/`). Use the same visual language throughout: the tokens, DM Sans, Material Symbols Rounded, flat surfaces with no shadows, and the same radii. Canvas is 1280×800 dp landscape. Deliver light and dark themes. Both sheets use the right-side sheet pattern from the Holiday mode sheet: 600 dp wide, full height, `bg` background, a 1 dp left border, scrim `rgba(0,0,0,.55)`, and a 48 dp close button.

## Context
- A wall-mounted family tablet. Viewing never needs a PIN. Any change asks for a 4-digit personal PIN, and the PIN also records **who** made the change.
- Everyone's events show on one shared calendar. Each event belongs to a person, or to "Family", and is coloured by that person: Alex `#4CB387`, Sam `#5B9BE0`, Mia `#E07BA8`, Family `#E0A85B`. These names and colours are placeholders; real households set their own.
- Only events on the household's **master calendar** can be edited from the tablet. Events from other calendars (e.g. a school-term ICS feed) and recurring events are read-only on the tablet.
- Roles: Admin and Adult can add or edit any event. Children can add events only for themselves, and edit only events they created.

## Sheet 1: Event detail
Opens when an event is tapped (on the Calendar week view or the Home Today card).

Content:
- Title
- Day and time (all-day or a time range)
- Who it's for (person chip in their colour)
- Created by (person name, or "Added from phone")
- Source calendar name

States to show:
1. **Editable event:** Edit and Delete actions.
2. **Read-only event** (other calendar, or recurring): no Edit/Delete, plus a short explanation such as "Edit in Google Calendar" or "From School terms (read-only)".
3. **Untagged master-calendar event** (added from a phone, shown as Family): an "Assign to…" action to pick a person.
4. **Pending sync:** a small "Syncing…" mark on an event that was just saved and hasn't reached Google yet.
5. **Delete confirmation:** inline or a small dialog. It must be deliberate; a child should not delete by accident.

Permission checks happen when an action is **tapped**, never by hiding buttons (the PIN pad appears over the sheet if needed).

## Sheet 2: Quick-add / edit event
The goal is speed. The common case is: tap **+**, type a title, tap a person, tap **Save**. It opens from a **+** on the Today card, a **+** on the Calendar tab header, or a tap on an empty spot in a week-view day column (which pre-selects that day).

Fields, in this order:
1. **Title:** text field, focused on open, with the on-screen keyboard showing. The layout must still work with the keyboard taking roughly the bottom 40% of the screen.
2. **Who:** person chips (Family plus each person, in their colours). Pre-selected to whoever is signed in, otherwise Family. For a signed-in Child, only their own chip is enabled; show the others disabled, not hidden.
3. **Day:** chips for Today, Tomorrow and the next five weekdays by name, plus "Pick date…", which opens a date picker.
4. **Time:** chips for All day, Morning 09:00, Afternoon 14:00, Evening 18:00, plus "Pick time…". Duration chips 30 min / 1 h / 2 h, default 1 h. Hide the duration when All day is selected.
5. **Save:** a primary full-width button in the style of the Holiday sheet's primary button. Show the PIN pad over the sheet if nobody is signed in.

Also show:
- **Edit mode:** the same sheet, pre-filled, titled "Edit event", with Delete available.
- **Validation:** Save disabled until the title is non-empty.
- **Save failed:** the event could not be saved to Google (rolled back). A short message and the sheet stays open with the input kept.

Not in scope: recurring events, reminders, location, attendees, notes.

## Deliverables
- Screens for every state above, light and dark, at 1280×800 dp.
- Measurements in dp/sp in the same style as the existing README (padding, radii, type sizes, chip sizes). Touch targets are at least 44 dp.
- A short addition to the hand-off README describing both sheets and their interactions.
