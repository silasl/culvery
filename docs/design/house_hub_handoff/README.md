# Handoff: Culvery — personal house management app (Android tablet)

_Formerly “House Hub”. The app is now called **Culvery**._

## Overview
A full-screen, landscape Android tablet app (wall-mounted / kiosk) for managing a family home. Five tabs — Home, Calendar, Lights, Music, Climate — plus a persistent **Holiday mode** button that configures multiple devices at once (Philips Hue, heating, Sonos, alarm, smart plugs, Google Calendar).

## About the Design Files
`Culvery.dc.html` is a **design reference built in HTML** — a working prototype of intended look and behaviour, not production code. Recreate it natively for Android. Recommended stack if none exists: **Kotlin + Jetpack Compose (Material 3)**, kiosk/immersive mode (`WindowInsetsController` hide system bars, `FLAG_KEEP_SCREEN_ON`, optional Lock Task / screen pinning).

The `.dc.html` sources and `support.js` are kept locally, not in the repo (the design tool generates them).

Open the HTML file in Chrome to interact with it. Tweakable props: `theme` (auto/dark/light), `showStatusBar`, `armCountdown`, `simulateSaveFailure` (forces the calendar save-failed state).

## Fidelity
**High-fidelity.** Colours, type, spacing, radii and interactions are final. Recreate closely; Material 3 components may be restyled to match.

## Target device
- Landscape tablet, design canvas **1280 × 800 dp** (e.g. 10" 16:10). Scale layouts proportionally; grids use fractional columns.
- Touch-first: minimum hit target 44 dp; most primary controls are 48–88 dp.

## Global layout
- **Status bar** (30 dp, optional): time left; theme indicator (tap to preview Day/Night), wifi, battery right. 13 sp / 500, muted colour. In production use system status bar or hide in kiosk.
- **Holiday banner** (only when Holiday mode active): full width, padding 10×24, bg `accentSoft`. Icon `flight_takeoff` (accent) · "Holiday mode on" (700) · "Back {date} · {n} routines running" (muted) · right-aligned pill button "I'm home" (accent bg, accentInk text, 14 sp/700, padding 9×18, radius 20).
- **Navigation rail** (left, 108 dp wide, 1 dp right border `line`): 5 items, each 88 dp wide — icon in a 64×38 pill (radius 19; active bg `accentSoft`, icon filled), label 13 sp/600 below (active `ink`, inactive `mute`). Bottom of rail: **Holiday button** 80×80, radius 26, icon `luggage` 30 dp + label "Holiday" / "Away" (12 sp/700). Inactive bg `surf2`; active bg `accent`, text `accentInk`.
- **Content area**: padding 24 top, 28 sides, 22 bottom; vertical gap 18.
- Cards: bg `surf`, radius 22–28, no shadows, no borders.

## Screens

### 1. Home
- Header row: big clock **104 sp / 600, letter-spacing −4, tabular numerals**; date below (21 sp, muted, e.g. "Wednesday 23 September"). Right: weather (icon `partly_cloudy_day` 44 dp in #E0B85B, "17°" 34 sp/600, "High 19° · Low 11°" 14 sp muted), 1 dp divider, indoor temp (34 sp) + heating status — tapping goes to Climate.
- Grid: 3 columns `1.15fr 1fr 1fr`, 2 rows, gap 14.
  - **Today** (col 1, spans 2 rows, radius 26, padding 22): title 19 sp/700 + "Week" chip (→ Calendar). Event rows: bg `surf2`, radius 16, padding 12×14, 4 dp colour bar (person colour), title 17 sp/600, "HH:MM–HH:MM · Person" 14 sp muted.
  - **Scenes** (cols 2–3, row 1): title + "{n} lights on ›" (→ Lights). 4 equal tiles (Morning `wb_twilight`, Evening `nights_stay`, Film `movie`, Night `bedtime`), radius 18, padding 14×16, icon 30 dp top, name 16 sp/700 bottom. Active scene: bg `accent`, text `accentInk`.
  - **Now playing** (col 2, row 2): 104 dp cover art (radius 16), zone name (13 sp/700 accent, "+ group" suffix if grouped), title 17 sp/700, artist 14 sp muted, 50 dp round play/pause (bg `ink`, icon `bg`).
  - **Holiday tile** (col 3, row 2): `luggage` icon 34 dp accent; "Going away?" / "Set up holiday mode · {n} routines". When active: bg `accentSoft`, "Holiday mode on" / "Back {date}". Opens Holiday sheet.

### 2. Calendar (Google shared calendar)
- Header: "This week" 34 sp/700, subtitle "Family calendar · synced with Google {x} ago". Right: person legend (10 dp dot + name).
- 7 equal columns (gap 10), each a card radius 22, padding 14×10: day label (14 sp/600, "Today" in accent) + date number 24 sp/700. Today column has 2 dp inset accent ring.
- Event chips: bg = person colour at ~15–18% alpha, radius 12, padding 8×10; time 12 sp/700 in person colour; title 14 sp/600.
- Person colours: Alex `#4CB387`, Sam `#5B9BE0`, Mia `#E07BA8`, Family `#E0A85B`. Map to Google calendars (one per person + shared family calendar).

### 3. Lights (Philips Hue)
- Header: "Lights" + subtitle "{n} of {m} rooms on · Philips Hue bridge connected" (during holiday: "Presence simulation running — manual control still works"). Buttons "All on" / "All off" (bg `surf2`, 15 sp/700, padding 13×22, radius 24).
- Grid 4 × 2 of room tiles (radius 24, padding 18). Off: bg `surf`; On: bg `surf2`.
  - Top-left: 56 dp round toggle button, `lightbulb` filled. On: bg = current colour swatch, icon `#1A1712`. Off: bg `surf3`, icon muted.
  - Top-right: 34 dp colour dot (3 dp border `surf`); tap cycles colour presets: Warm `#FFD49A`, White `#FFF3DE`, Cool `#A9CBFF`, Violet `#CDA8FF`, Coral `#FFA89A`. 35% opacity when off.
  - Bottom: room name 19 sp/700, status "{bri}% · {colour}" or "Off" 14 sp muted, brightness slider 1–100 (track 8 dp `surf3`, thumb 24 dp `ink`). Moving slider turns room on.
- Rooms: Kitchen, Lounge, Dining, Hallway, Main bedroom, Mia's room, Office, Garden (map to Hue rooms/zones).

### 4. Music (Sonos)
- 2 columns `1.35fr 1fr`, gap 18.
- Now playing card (radius 28, padding 28): 300 dp cover, "PLAYING IN {ZONE}" 13 sp/700 accent, letter-spacing .6; title 32 sp/700; artist 18 sp muted; 6 dp progress bar; elapsed/total 13 sp tabular; controls: prev/next 60 dp (`surf2`), play/pause 80 dp (`ink` bg).
- Speakers list: card per zone (radius 20, padding 14×16). Selected zone: bg `surf2` + 2 dp inset accent ring. Row: speaker icon (accent if playing), name 17 sp/700, state "Playing / Idle / Paused"; "Group"/"Grouped" pill (grouped: bg `accentSoft`, accent text). Volume slider 0–100 with numeric value.
- Zones: Lounge, Kitchen, Bedroom, Garden.

### 5. Climate
- 2 columns `1.2fr 1fr`.
- Heating card (radius 28, padding 30): title + "Inside now {t}°". Centre: − / + buttons 88 dp round, target temp **160 sp / 600, letter-spacing −6** (muted when not in Heat mode; "Off" when off), status line below. Step 0.5°, range 10–25°. Pressing +/− forces Heat mode.
- Segmented mode control (bg `surf2`, padding 6, radius 22): Heat `local_fire_department`, Eco `eco` (12° frost protection), Off `power_settings_new`. Active: accent bg.
- Rooms list: name, humidity "% RH", temperature 24 sp/700.
- "Boost hot water" button (1 hour); active: accent bg, "Hot water boosting · Ends in 60 min".

### 6. Holiday mode sheet (overlay)
- Right-side sheet 600 dp wide, full height, bg `bg`, 1 dp left border; scrim `rgba(0,0,0,.55)` — tap scrim to dismiss. Close button 48 dp.
- Title "Holiday mode" (34 sp/700) / subtitle "{n} nights · {x} of 7 routines selected". When active: "You're away" / "{x} routines running until {date}".
- Date steppers (2 cards): **Leaving** ("Today" or "Fri 25 Sep") and **Back** (leave + nights), each with 44 dp −/+ buttons. Defaults: leave in 2 days, 9 nights.
- Routine list (toggle rows, radius 18, 42 dp icon circle, title 16 sp/700, sub 13 sp muted, 52×30 switch):
  1. Simulate presence — Hue lights on/off at random, 18:00–23:30
  2. Frost protection — heating held at 12°, hot water off
  3. Warm up before return — back to {target}° three hours before arrival
  4. Silence Sonos — pause all speakers, disable alarms
  5. Arm alarm & cameras — motion alerts to Alex and Sam
  6. Switch off smart plugs — TV, coffee machine, office; fridge stays on
  7. Add "Away" to calendar — blocks {leave} – {return} (default off)
- Primary button (full width, 18 sp/700, padding 18, radius 30, accent): "Start holiday mode". With `armCountdown` on: tapping starts a **10 s countdown** — label "Arming in {n}s — tap to arm now", plus a Cancel button. On arm: sheet closes, banner shows, rail button becomes "Away", selected routines applied (lights off → simulation, heating → Eco, Sonos paused).
- When active, primary becomes "End holiday mode" (bg `surf2`). "I'm home" in the banner does the same: restores Heat mode and clears the banner.

## 7. Calendar sheets (event detail + quick-add/edit)

Both use the Holiday sheet pattern: right-side sheet **600 dp** wide, full height, bg `bg`, 1 dp left border `line`, scrim `rgba(0,0,0,.55)` (tap to dismiss), 48 dp round close button (`surf2`, icon `close` 26 dp). Sheet padding 28×30×26 (detail) / 24×30×20 (quick-add). Vertical gap 16.

### Entry points
- Tap an event: Home **Today** rows (now buttons) and week-view event chips → **Event detail**.
- **+** on the Today card: 44 dp round, `accent` bg, icon `add` 26 dp; "Week" chip is now 44 dp tall (radius 22, 14 sp/600).
- **Add event** on the Calendar header (right of the legend): 48 dp tall, radius 24, padding 0 20 0 14, accent, icon 24 dp + 15 sp/700.
- Tap empty space in a week-view day column → Quick-add with that day pre-selected. Each column ends with a faint `add` icon (24 dp, `mute`, 50% opacity, min 40 dp area) as a hint.
- Event chip badges (15 dp, `mute`, right of the time): `cloud_upload` = syncing, `lock` = other calendar, `repeat` = recurring. Today rows show the same badge at 20 dp.

### Permissions and PIN
- Viewing never needs a PIN. Every **change** (save, edit, delete, assign) calls `guard(action)` **when tapped**. Buttons are never hidden for permission reasons.
- If nobody is signed in → **PIN pad** over the sheet. On a correct PIN the user is signed in and the action continues. That user is recorded as the creator (`by`).
- Session: stays signed in for **2 min after the last action**, then signs out. The status bar shows `account_circle` + "Alex · Admin" + a "Sign out" text button (accent, 12 sp/700).
- Rules: Admin/Adult can add, edit, delete and assign anything editable. A Child can add only for themselves and edit/delete only events whose `by` is them; assigning is adults-only.
- If a check fails → **toast**: bottom-centre pill, bg `ink`, text `bg`, 16 sp/600, padding 14×22, radius 26, `info` icon. It sits 28 dp above the bottom, or 340 dp when the keyboard is up, and auto-hides after 3.5 s. Copy: "Mia can only change events they created." / "Mia can only add events for themselves." / "Ask an adult to assign this event."

**PIN pad**: covers the sheet area (600 dp, scrim `rgba(0,0,0,.5)`, tap outside = cancel). Card 400 dp wide, `surf`, radius 30, padding 26×28, gap 14, centred.
- 56 dp lock badge (`accentSoft`, `lock` 30 dp accent).
- "Who's this?" 24 sp/700, with reason text 15 sp muted: "Enter your PIN to {save|edit|delete|assign} this event. It also records who made the change."
- 4 dots, 18 dp, 16 dp gap. Filled = `ink`; empty = 2 dp `mute` border; error = `danger` border.
- Error line (18 dp high, 14 sp/700 `danger`): "Wrong PIN — try again". Digits clear after a wrong PIN.
- Keypad 3×4 of 76 dp circles (`surf2`, 30 sp/600), gaps 12 (row) × 20 (column). Bottom row: Cancel (text 16 sp, transparent), 0, backspace icon.
- The prototype-only hint "Prototype PINs · Alex 1111 · Sam 2222 · Mia 3333" is not for production.

### Sheet 1 — Event detail
- **Header**: 6 dp colour bar (person colour, full title height) + title 34 sp/700, −0.5 tracking, `text-wrap: pretty` + close button.
- **Info card** (`surf`, radius 22, padding 2×18): rows are at least 58 dp tall, with 1 dp `line` dividers. Each row has an icon (22 dp `mute`), a label (15 sp `mute`, 104 dp wide) and a value (17 sp/600).
  - **When**: "Today · 19:30–21:00" / "Sat 26 Sep · All day".
  - **For**: 12 dp dot + name.
  - **Created by**: name / "Added from phone" / "Calendar feed".
  - **Calendar**: "Family calendar" (master) / "School terms · read-only".
  - **Repeats** (recurring only).
- **Editable** (master calendar, not recurring): footer has Delete (60 dp, padding 0 26, radius 30, `surf2`, `danger` text 17 sp/700, `delete` icon) + Edit (flex 1, 60 dp, accent, 18 sp/700, `edit` icon).
- **Read-only**: no footer. Explanation card (`surf`, radius 22, padding 16×18, 26 dp icon, title 16 sp/700, sub 14 sp muted):
  - Other calendar: `lock` — "From School terms (read-only)" / "This is a subscribed calendar, so it can't be changed here."
  - Recurring: `event_repeat` — "Repeating event" / "Edit repeating events in Google Calendar on your phone."
- **Untagged** (master, `by = phone`, person = Family): `accentSoft` card, radius 22, padding 16×18, `smartphone` icon. Text: "Added from a phone" / "Showing as Family until someone assigns it." Its **Assign to…** button (44 dp, radius 22, accent, 15 sp/700) expands a row of person chips (48 dp, `surf`, dot + name). Picking a person → guard → sets person, marks syncing. Edit/Delete remain.
- **Pending sync**: pill above the title (30 dp, radius 15, `surf2`, `mute` 13 sp/700, `cloud_upload` 18 dp): "Syncing to Google…". It also shows as a badge on the event chip. It clears when the Google write is confirmed (prototype: 2.5 s).
- **Delete confirmation** (inline, replaces the footer): `dangerSoft` panel, radius 24, padding 20, gap 16. Text: "Delete this event?" 18 sp/700 + "“{title}” will be removed from Google Calendar for everyone." 14 sp muted. Buttons (60 dp each, flex 1): **Keep event** (`surf`) on the **left**, where Delete was, so a double tap is harmless; **Delete event** (`danger` bg, `dangerInk`, `delete_forever`) on the right. So deleting takes guard (PIN) + Delete + a confirm on the opposite side. After deleting, the sheet closes with the toast "Event deleted".

### Sheet 2 — Quick-add / edit
- **Header**: "New event" / "Edit event" 30 sp/700 + a live summary 15 sp muted ("Today · 18:00–19:00 · Family") + close.
- **Body** scrolls (gap 20). Section labels: 13 sp/700 `mute`, 0.5 tracking, uppercase (WHO / DAY / TIME / LENGTH), 10 dp above the chips. Chips wrap with 8 dp gaps.
- **Chip**: height 48, padding 0 18, radius 24, 16 sp/600, gap 8.
  - Unselected: `surf`/`ink`. Selected: `accent`/`accentInk`.
  - Person chips show a 12 dp colour dot. When selected, bg is the person colour with ink `#0E1011` and a `check` icon replaces the dot.
  - Disabled: 38% opacity, still tappable → explains why via toast.
  - Secondary text in time chips ("09:00"): 500 weight, 72% opacity.
1. **Title**: 64 dp, radius 18, `surf`, 22 sp/600, padding 0 20. 2 dp border, `accent` when focused. Placeholder "What's happening?". It is focused on open for **new** events (keyboard shows); edit mode doesn't auto-focus.
2. **Who**: Family, Alex, Sam, Mia. Defaults to the signed-in user, otherwise Family. A signed-in Child sees the other chips disabled.
3. **Day**: Today, Tomorrow, then the next 5 days by weekday name, plus **Pick date…** (`calendar_month` icon; shows the chosen date, e.g. "Mon 5 Oct", when outside the week).
4. **Time**: All day, Morning 09:00, Afternoon 14:00, Evening 18:00, plus **Pick time…** (`schedule` icon; shows e.g. "16:15"). The default slot is the next one coming up: before 09:00 → Morning, before 14:00 → Afternoon, otherwise Evening. **Length**: 30 min / 1 h / 2 h, default 1 h, hidden for All day.
- **Footer**: Save, same as the Holiday primary button (flex 1, 60 dp, radius 30, 18 sp/700, `check` icon). Label "Save event" / "Save changes". **Disabled** (bg `surf2`, text `mute`, no-op) until the trimmed title is non-empty. Edit mode adds Delete on the left (same as the detail sheet), which runs the guard, then opens the detail sheet in delete-confirm state.
- **Save flow**: guard → optimistic insert with `syncing: true` → sheet closes. On failure (Google rejects the write), roll back, keep the sheet open with inputs intact, and show above the footer a `dangerSoft` card (radius 18, padding 14×16, `cloud_off` 24 dp `danger`) with "Couldn't save to Google Calendar" 16 sp/700 + "Nothing was changed. Your details are still here — check the connection and try again." 14 sp. Save becomes **Try again** (`refresh` icon).
- **On-screen keyboard**: the system IME takes about the bottom 320 dp (40%). Use `adjustResize`: the sheet shrinks to **480 dp**, the header and Save stay pinned, and the body scrolls (Title + Who + the first row of Day stay visible). The prototype draws a stand-in keyboard. The toast moves above it.
- **Pickers** (over the sheet like the PIN pad, card `surf`, radius 30, padding 26):
  - **Date** (520 dp): "Pick a date" + range sub. A 7-column grid of the 5 weeks starting this Monday: header 13 sp/700 muted; cells 56 dp high, radius 16, `surf2`, 16 sp/600. Today has a 2 dp accent ring, the selected day is accent, past days are at 30% and inactive, and the 1st of a month shows as "1 Oct". Cancel button 52 dp.
  - **Time** (440 dp): "Pick a time". Hour and minute columns, each with an 88×52 up/down button (`surf2`, radius 18, `expand_less`/`expand_more` 32 dp) around a 72 sp/600 tabular value, separated by ":" 64 sp. Step is 1 h / 15 min. Cancel + **Set time** (56 dp, radius 28).

### Data model additions
`event { id, day/date, allDay, start (min), durationMin, title, person (fam|userId), createdBy (userId|'phone'|feed), calendar ('master'|feedId), recurring, syncState ('synced'|'pending'|'failed') }`. Store `person` and `createdBy` in Google `extendedProperties.private`. Events with no `person` show as Family / untagged. Treat any event with `recurringEventId` or from a non-master calendar as read-only.

## Theming — Auto day/night
- `theme = auto` (default): **light 07:00–19:00, dark otherwise** (consider switching to sunrise/sunset + ambient light sensor, and dim screen brightness at night). Tapping the status bar theme indicator previews the other theme.

| Token | Dark | Light |
|---|---|---|
| bg | #0E1011 | #EDF0EE |
| surf | #1A1D1E | #FFFFFF |
| surf2 | #24282A | #F1F4F2 |
| surf3 | #303537 | #DDE3E0 |
| line | #1F2324 | #DAE0DD |
| ink | #F1F4F2 | #111514 |
| mute | #9AA3A0 | #5A6461 |
| accent | #4CB387 | #2E8A64 |
| accentInk | #08170F | #FFFFFF |
| accentSoft | #173427 | #D3EDE1 |
| danger | #EE7B6A | #B83A28 |
| dangerSoft | #3A211D | #F7DFDA |
| dangerInk | #1A0906 | #FFFFFF |
| kbdBg (prototype IME stand-in) | #131617 | #D3DAD6 |
| kbdKey | #2A2F31 | #FFFFFF |

Theme transition: background/colour 400 ms. Other transitions: tile/pill backgrounds 200–250 ms ease.

## Typography
- **DM Sans** (Google Fonts) 400/500/600/700 for all UI. Tabular numerals for clocks, temps, volumes.
- Scale (sp): 160 (target temp), 104 (home clock), 34 (screen titles, header values), 32 (track title), 24, 22 (section titles), 19–17 (card titles), 16–14 (body), 13–12 (labels).
- Icons: **Material Symbols Rounded**, fill 0 default, fill 1 for active/filled states.

## Spacing & radii
- Gaps: 4, 6, 8, 10, 12, 14, 18, 28. Card padding 14–30.
- Radii: 12 (chips), 14–18 (small tiles/rows), 20–24 (cards/pills), 26–28 (large cards), full circles for icon buttons.
- No shadows inside the app.

## State (suggested ViewModel)
- `tab`, `themeMode`, `now` (ticks every minute)
- Lights: `rooms[{id,name,on,bri,hueIndex}]`, `activeScene`
- Music: `playing`, `track`, `selectedZone`, `zones[{id,name,vol,grouped}]`
- Climate: `target`, `mode (heat|eco|off)`, `boost`, room readings
- Holiday: `sheetOpen`, `active`, `leaveDate`, `nights`, `routines{key:bool}`, `armingSecondsLeft`
- Calendar: events for next 7 days per person

## Integrations (implementation notes)
- **Google Calendar**: Calendar API v3, OAuth (household account), read shared calendars; write the "Away" event on holiday arm. Cache and poll/sync every few minutes; show "synced x ago".
- **Philips Hue**: local Hue Bridge API (v2/CLIP, mDNS discovery, link-button pairing). Rooms/zones → tiles; scenes → Scenes tiles. Presence simulation: schedule randomised on/off (bridge behaviours or app-side WorkManager — bridge-side preferred so it works if tablet sleeps).
- **Sonos**: Sonos Control API (cloud, OAuth) or local UPnP. Groups, volume, play/pause/skip, now-playing metadata + artwork.
- **Heating, alarm/cameras, smart plugs**: device vendors not yet decided — abstract behind a `DeviceProvider` interface. Consider Home Assistant as a single hub integration.
- Holiday mode should be executed server/hub-side where possible (schedules survive tablet reboot) with a single "apply/revert" transaction and per-routine error reporting.

## Not yet designed
Loading, error/offline and device-unreachable states; settings/account linking screens; idle screensaver.

## Assets
- Cover art areas are placeholders — use Sonos artwork URLs.
- Fonts: DM Sans; icons: Material Symbols Rounded. No other images.

## Screenshots
`screenshots/` — Home, Calendar, Lights, Music, Climate (dark), Holiday sheet (setup + active), and light-theme views with holiday mode on. Note: sliders appear as plain dots in the captures; the prototype renders them with an 8 dp track and 24 dp thumb.

## Files
- `Culvery.dc.html` — interactive prototype (all screens, states and logic in one file; open in Chrome).
