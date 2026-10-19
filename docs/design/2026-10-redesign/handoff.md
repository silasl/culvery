# Handoff: Culvery wall-tablet redesign (October 2026 review)

## Overview
A redesign of Culvery, the family wall tablet (Samsung Galaxy Tab A 10.1, SM-T510, 1920 × 1200 landscape, wall-mounted, used at arm's length and read from across the room). It answers the October 2026 design review: Home, Calendar week, the Music, Lights and Door tabs, the door card, Add event, the PIN pad, the night clock, Settings › Home buttons, Settings › Calendars and the setup wizard.

The headline changes:
- The forecast moves into the header.
- Coming up is removed. Today always fills 5 rows, topping up from tomorrow or scrolling.
- The freed space becomes a Shopping list and a "One-tap" tray of single-action buttons (Party mode, Holiday mode, Start hoover, and up to 3 sounds), configured in a new Settings › Home buttons page.

## About the design files
The file in this bundle is a **design reference made in HTML**, not production code. Recreate it in the Culvery Android app's existing environment (Kotlin and Jetpack Compose, with its existing theme, typography and components), following the codebase's established patterns. Don't ship or port the HTML.

## Fidelity
**High fidelity.** Colours, type, spacing, radii and copy are final unless a note says "provisional". The existing token set (DM Sans, the dark and light palettes, person colours, button tones) is unchanged. New values are listed under Design tokens.

**Units.** Every measurement here and in the HTML is in **device pixels on a 1920 × 1200 artboard**. The SM-T510 renders at about 1.5× density, so **dp = px ÷ 1.5** (for example, 72 px = 48 dp and 92 px ≈ 60 dp). Check the device density before converting. The type sizes below are px; divide by 1.5 for sp.

## How to read the reference file
Open `Culvery Home Review.dc.html` in a browser. It's a pannable canvas of 1920 × 1200 artboards, each with an id badge, and yellow numbered pins that match the "What changed, and why" note list beside them. The Tweaks props (theme, things today, long event title, rooms, Party mode on, signed in, camera watching, weather tint) switch states live.

| id | Screen |
|---|---|
| 1a | Home, every card set up (Today scrolling with 7 events) |
| 1b | Home, before Lights and Music (slideshow shown, 2 today plus 3 tomorrow) |
| 1c | Home, signed in, nothing today (5 from tomorrow) |
| 1d | Home in the opposite theme (light check), Party mode on |
| 2a | Door card over Home |
| 2b, 2d | Lights tab, 4 rooms and 9 rooms (scrolling) |
| 2c | Settings › Home buttons |
| 3a | Calendar week, with multi-day bars |
| 3b | Music tab |
| 3c | Door tab |
| 4a | Add event sheet, child signed in |
| 4b | PIN pad, locked out |
| 4c | Night clock with the camera mark |
| 5a | Setup: Who else lives here? |
| 5b | Setup: Add person, name taken |
| 5c | Settings › Calendars, with read problems |

---

## Screens

### Shared frame (Home and the tabs)
- **Rail:** 160 px wide, with a 1 px right border (`line`) and 28 px top padding.
  - **Culvery mark:** 84 × 76, with 76 px below it. Make the space above the mark equal to the space at its sides (note 6).
  - **Nav items:** a 96 × 56 icon pill (radius 28) over a 20 px label, 22 px apart. The active item has an `accentSoft` pill, a filled icon and a 600-weight label; inactive items are muted with an outline icon.
  - **Door badge:** `danger` fill, `onDanger` text 17/700, 28 px min height, with a 3 px ring in `bg` (note 5).
  - **Settings tile:** 120 × 120, radius 30, `s1`, pinned to the bottom.
- **Main area:** padding 0 42 34 44.
- **Top bar:** 72 px high, right-aligned, 28 px gaps, in this order:
  - **Camera mark (note 10):** a 44 px pill in `accentSoft`. It holds a 10 px dot, a filled `photo_camera` icon and "Watching" (19/700), all in `accent`. Show it only while "Wake when someone walks up" is on.
  - **Signed-in block (note 11):** an `account_circle` icon, "Alex · Admin" in `text`, then a 44 px quiet "Sign out" pill (s2, 19/700) and a 1 × 28 px divider.
  - **Theme control (note 7):** an icon and "Night · Auto" or "Day · Auto" (21, muted). While previewing the other theme it reads "Day · Preview" (or "Night · Preview") and tapping it returns to Auto.
- Material Symbols Rounded is used for the icons. Use the app's existing icon set where equivalents exist.

### Home (1a–1d)
**Header (note 1).**
- **Left:** the clock at 150/600, line-height .86, letter-spacing −5. Below it, the date at 34, muted, with a 22 px gap.
- **Right, current weather:** an 84 px icon, the temperature at 64/700 and the condition at 22 muted.
- **Right, then:** a 1 × 100 divider (s3) and a 3-day strip: Today, Thu, Fri, 34 px apart. Each day has its label (20/600 muted), a 42 px icon, and the high (22/700) with the low (22 muted).
- **Forecast card:** removed. When there's no location, show a header pill "Add home location for weather" instead.
- **Weather icon colours:** amber or gold for sun, and with weather tint on, blue for rain (#7FB2E5 dark, #2F6FAE light) (W7).

**Grid** (below the header, filling the rest of the screen, 22 px gaps): columns of 600 px and minmax(0, 1fr).

**Today card (notes 9 and 21).**
- **Card:** `s1`, 1 px `border`, radius 36, padding 30/32.
- **Header:** "Today" at 34/700. On the right, a 68 px round green + (only when someone signed in can add) and a 68 px quiet "See the week" pill. Hide "See the week" while the pinned button below is showing.
- **Row:** `s2`, radius 22, padding 20/22, with a 7 px colour bar in the person's colour. The title is 31/600 and clamps to **2 lines**, then "…". Meta is 24 muted, e.g. "07:45–08:30 · Sam". A repeat icon sits on the right where relevant.
- **Fill rule (MAX = 5):**
  - n > 5: all of today's events in a scroll view. Rows fade under a 200 px gradient (transparent to `s1`) to a pinned 72 px pill, 32 px from the card's sides and 30 px from its foot. The pill shows a `calendar_month` icon, "{n−4} more today · Open calendar" and an `arrow_forward`, and opens the Calendar tab on today. The scroll view gets 110 px bottom padding so the last row can clear the pill.
  - n = 5: the 5 rows, no pill.
  - n < 5: today's rows, then a "TOMORROW" divider (22/700, letter-spacing 1.5, uppercase, muted, with a 1 px s3 rule), then (5 − n) of tomorrow's events. Tomorrow rows are **outlined**: 2 px `s2` border, no fill, padding 16/20, a 75%-opacity colour bar, title 27/600 on 1 line, meta 22.
  - n = 0: "Nothing on today" (28 muted), then 5 rows from tomorrow.
  - If long titles push rows past the card's height, switch to the n > 5 scroll treatment, triggered by measured overflow and not only by the count.
- **Multi-day events:** one row reading "Day 2 of 3".

**Right column.**
- **1a/1c layout:** a utility row, then a 318 px row with the Lights and Music cards.
- **1b layout (no Lights or Music):** a family slideshow fills the top (note 14), with a 400 px utility row below it.

**Shopping card (note 12).**
- **Card:** `s1`, radius 36, padding 28/30.
- **Header:** "Shopping" at 30/700 and "7 items" at 21 muted. On the right, a 60 px round quiet +.
- **Items:** 46 px rows, each with a 30 px circle (3 px s3 ring), the name at 24, and "Mia" etc. in 19 muted when the item was added from a phone.
- **Footer:** "+2 more · added from phones too" (20 muted).
- Anyone can add or tick items without a PIN.

**One-tap tray (note 13), 500 px wide.** This pattern marks **single-action buttons** throughout the app.
- **Tray:** a 2 px `s3` outline, radius 36, padding 16, no fill. At the top, a "ONE-TAP" label (18/700, letter-spacing 1.5, muted) with a filled `touch_app` icon.
- **Row 1, modes:** 2 equal tiles, `s2`, radius 26, each with a 36 px filled icon in `accent` and a 22/700 label. **On:** the tile fills `accent`, the label and icon turn `onAccent`, and "ON" (16/700, letter-spacing 1) appears on the right. Tapping again turns it off. **No switches** inside the tiles.
- **Row 2, Hoover:** `s2`, radius 26. A `cleaning_services` icon, "Hoover" (22/700) over "Docked · 86%" (18 muted), and a 54 px green "▶ Start" pill.
- **Row 3, sounds:** 3 equal pads, `s2`, radius 26, each a `volume_up` icon in accent over the name (19/700, 1 line). They play straight away on the usual Sonos room, never during quiet hours.
- The 3 rows share the height equally, 12 px apart.
- **House Holiday mode** covers lights, heating and the door chime, and can switch on Lights' own holiday mode.

**Lights card (note 3).**
- **Top row:** a 46 px filled bulb in accent, "Lounge" at 30/700, "On · 70% · Relax" at 22 muted, and a 64 px round › (navigates to the Lights tab).
- **Middle:** the main room's brightness. A `brightness_low` icon, a 30 px track (s3) with the fill in accent, and a `brightness_high` icon.
- **Bottom:** "All lights off", a 68 px primary pill with a power icon, and "Scene", a 68 px quiet pill whose 46 px inner chip shows the current scene. Holiday mode is no longer on this card.

**Music card (note 4).**
- **Top:** 132 px art (radius 20), then the room at 30/700, the station or track at 25 and the show or artist at 21 muted.
- **Control row (68 px):** a primary Pause, a quiet Next, and a volume pill (`s2`) containing a `volume_up` icon and a 14 px track.

**Family slideshow (1b only, note 14).**
- A full-bleed photo with radius 36 and a bottom gradient to rgba(0,0,0,.55).
- The caption is the title at 26/700 over "August · added by Sam" at 20, with page dots (an active 28 × 10 pill and 10 px dots).
- **Open decision:** when every card is set up, the slideshow moves to the idle screen.

**Light theme (1d, notes 8 and 19).** Cards get a 1 px `#D5DCD8` border. Everything else maps through the light tokens.

### Door card (2a, note 16)
- **Behind:** a full-screen scrim, rgba(0,0,0,.64).
- **Card:** 1120 px wide, `s1`, a 2 px `s3` border, radius 44, padding 36, gap 26, shadow 0 40 120 rgba(0,0,0,.6).
- **Header:** an 88 px `accentSoft` circle with a 50 px filled doorbell in accent. "Someone's at the door" at 46/700, and "Front Door · 11:54 · just now" at 25 muted. On the right, a quiet 52 px chip with `volume_down` and "Music lowered".
- **Snapshot:** the full card width at 540 px tall (16:9), radius 28.
- **Buttons, both 88 px:** "Got it" is primary (flex 2, 28/700); "Door history ›" is quiet (flex 1).
- **Timing:** the card closes by itself after 60 s, and the event stays in the Door tab.

### Lights tab (2b/2d, notes 15, 18 and 20)
- **Page header:** the title at 68/700 (letter-spacing −1), and below it "{on} of {n} rooms on · Hue Bridge" at 23 muted. On the right, a One-tap tray holding "All lights off" (action) and "Holiday lights" (lights-only mimic-presence mode, which shows ON when active).
- **Room cards:** `s1`, radius 36, padding 30/32.
  - **Top row:** a 52 px bulb, the name at 34/700, the status at 23 muted, and a 96 × 54 switch with a 40 px knob.
  - **Slider:** a 34 px track with the % on the right (23/600).
  - **Scene chips:** 60 px, radius 30, **always filled**. The active scene is `accent` with `onAccent` text; the others are `s2` with `text`.
- **An off room:** an outline bulb in muted, an `s3` switch with a muted knob. The slider has an `s2` track with an `s3` fill at the remembered level, and the label reads "Off".
- **Grid:** 1–4 rooms in 2 columns; 5–6 in 3 columns (rows share the height); 7 or more in 3 columns at a fixed 420 px row height with scrolling. When scrolling, a 220 px fade to `bg` runs into a centred 68 px pill: `keyboard_arrow_down` and "{n−6} more rooms".
- **Order:** rooms that are on sort first.

### Settings › Home buttons (2c, note 17)
- **Settings list:** a 480 px panel in `s1`. The title is 52/700. Items are 78 px, radius 24, 26/600; the active item is `accentSoft` with `accent` text. A 72 px quiet "Close" sits at the bottom.
- **New page:** "Home buttons" goes between Display and Music.
- **Page header:** the title at 56/700, then "These sit in the One-tap tray on Home and do their thing straight away. Up to 6." at 24 muted.
- **Left column, "ON HOME · 6 OF 6":** 96 px rows, each with an icon, the name at 26/600 and its kind at 20 muted ("Mode · stays on until tapped again", "Action" or "Sound"). On the right, an `s2` pill holding the up and down arrows (the end arrow at 30% opacity), then a separate `remove_circle` in danger.
- **Right column, 520 px, "ADD":** sound chips (Applause, Airhorn, Rimshot, Ta-da, Crickets, Laugh track) and action chips (Bedtime lights, Stop all music, Ring the family). These lists are provisional. When the tray is full, the chips fade to 50% under the info strip "The tray is full. Remove one to add another." The footer note reads "Sounds play on the usual Sonos room (Settings › Music), not during quiet hours."

### Calendar week (3a, notes 22, 23, 24 and 31)
- **Header:** "This week" at 68/700, with "Updated from Google Calendar 2 minutes ago" below it. On the right, one `s2` pill holding ‹ and ›, both 68 px (‹ at 35% opacity when disabled), then a 76 px primary "+ Add event". Past the current week, a "This week" pill appears between them.
- **No legend.**
- **Columns:** 7 columns, 14 px apart, each `s1`, radius 30, padding 18/14/14. Today's column has a 3 px inset ring in accent and an accent day label. The day label is 21/600 muted and the date 34/700.
- **Chips:** filled with the person's colour as a tint (alpha 0x30 in dark, 0x26 in light), radius 18, padding 12/14. The meta is 17/600 muted with a 10 px colour dot ("07:45 · Sam"); the title is 21/600 and clamps to 2 lines.
- **Adding:** the empty space in a column is a tap target that opens Add event on that day. On press it shows a 2 px dashed accent ghost with "+ Add on Sun 27". At rest it is plain.
- **Multi-day strip:** a lane under the headers, 62 px tall, with a matching spacer in every column. Each bar spans its days: inset 10 px into the first and last columns, radius 18, a 6 px inset left edge in the person's colour, and the tint at 0x4D (dark) or 0x40 (light). It holds a `date_range` icon, the title at 21/700 and the range at 17/600 muted. A bar that runs past the week has a square right end and "until Thu →". Overlapping bars stack into extra lane rows. Single-day all-day events stay as chips.

### Music tab (3b, notes 25, 26 and 27)
- **Header:** "Music" with "3 rooms · 1 group · Sonos". The One-tap tray holds "Pause everywhere".
- **Room cards:** `s1`, radius 36, padding 26/30.
  - **Left:** 120 px art, the room at 32/700, the track at 25 and the artist at 21 muted.
  - **Right:** a 72 px play/pause (primary while playing), a 72 px Next, and a 420 px volume pill with an icon, a track and the number.
  - **Chips (60 px):** a group shows "2 rooms" in `accentSoft` with `accent` text and an expand icon. Favourites and Shuffle everything use the quiet fill.
  - **Group expanded:** a 2-column grid of 72 px per-room volume rows.
- **Nothing playing:** hide Next, show "▶ Play last", an empty art tile, and "Nothing playing" in muted.

### Door tab (3c, notes 28, 29, 30 and 32)
- **Header:** the same title block as Lights: "Door" with "Front Door · Back Garden · 2 new rings". On the right, a 72 px "Clear history" in danger text on the quiet fill, with a delete icon.
- **List** (left column, 640 px panel on the right):
  - **Day labels:** "TODAY" and "YESTERDAY" (19/700, uppercase, muted).
  - **Rows:** `s1`, radius 28. A 64 px icon circle: doorbell on `accentSoft`/accent for rings; directions_walk on s2/muted for motion; help on s2/muted for an unknown notice. The title is 27/600, e.g. "14:02 · Someone rang"; the sub-line is 21 muted.
  - **Thumbnail and tag:** a 160 × 90 thumbnail on the right when there's a picture. New rings carry a red "NEW" tag (30 px, `danger`).
  - **Copy:** rings say "Someone rang" and motion says "Movement". "Ring alert" becomes "Notice from Ring", with the sub-line "Culvery couldn't tell what this was".
- **Latest picture panel:** the snapshot at 328 px, then "Mark all seen" (primary, clears the rail badge) and "See full size" (quiet). Tapping a row's thumbnail shows that picture in the panel.
- **Clear history:**
  - **Confirm card:** asks "Clear all {n} door events? Pictures saved on this tablet are deleted too. Ring keeps its own copies."
  - **PIN:** Admin or Adult only.
  - **Data:** it **must delete the cached snapshot and thumbnail files from app storage and any image cache** as well as the log rows, leaving nothing on the device.
  - **Auto-clear (suggested):** an automatic clear after 30 days (Settings › Door) that deletes files the same way.

### Add event sheet (4a, notes 33, 34, 35 and 36)
- **Sheet:** slides in from the right, 920 px, `bg`, a 1 px s3 left border, padding 44/48/40, over a scrim of rgba(0,0,0,.6).
- **Header:** the title at 52/700, the summary line at 23 muted, and a 72 px round quiet close.
- **Title field:** 96 px tall, `s1`, radius 26, 31/600.
- **Section labels:** 19/700, letter-spacing 1.5, uppercase, muted.
- **Chips:** 64 px tall, radius 32, 23 px text. Selected: `accent`, 700. Unselected: `s1`, 600.
- **Who, for a child:** the other people's chips are **locked**: transparent fill, a 2 px s3 inset ring, a lock icon, the colour dot kept, muted text. The child's own chip uses their colour with a check. Under the chips: "Mia can add events for Mia only."
- **Day:** Today … Tue, then "Pick a date" with a calendar icon. Drop the ellipsis, and use "Pick a time" likewise.
- **Time and Length:** side by side. Time is All day or a time chip with a clock icon; Length is 30 min, 1 hour or 2 hours.
- **Repeats:** Never, Every week, Every 2 weeks and "More options ▾". More options expands in place to Every month, Every year and an Ends row (Never, On a date, After N times). The sheet scrolls only once that's open.
- **Footer:** a 92 px full-width primary with a check icon. **Disabled:** a 2 px outline, muted label, no fill, and the reason above it ("Add a title first").

### PIN pad, locked out (4b, note 37)
- **Behind:** a scrim, rgba(0,0,0,.64).
- **Card:** 620 px wide, `s1`, a 2 px s3 border, radius 44.
- **Top:** an 88 px `dangerSoft` circle with a filled `lock_clock` in danger, "Too many tries" at 40/700, and "Wait a moment, then try your PIN again." at 23 muted.
- **Countdown:** "0:27" at 44/700 in tabular figures and danger, with a 260 × 8 bar that runs down as it counts.
- **Keys:** 116 px, at 30% opacity, **including 0 and backspace**. Cancel stays at full strength. Keep the layout steady between states.

### Night clock (4c, notes 38 and 39)
- **Background:** pure black.
- **Clock:** 300/600, letter-spacing −12, tabular figures, `#5A5A5A`. It's provisional; test it in the dark room.
- **Camera mark:** a bare filled camera in `#2E3331`, top right, in the same spot as the awake "Watching" pill, with no dot or text.

### Setup wizard (5a/5b, notes 40–45)
- **Step dots:** 16 px. Done steps are accent, the current step is a 44 × 16 accent pill, and the rest are s3.
- **Content column:** 960 px, centred.
- **5a, Who else lives here?**
  - The title is 60/700 with a muted sub-line.
  - **Person rows:** 112 px, `s1`, radius 28, each with a 22 px colour dot, the name at 28/600, the role and PIN state at 21 muted, and a ›.
  - "Add person" is a **quiet** 72 px button with `person_add`.
  - Back (quiet) and Next (primary, with an arrow) are 92 px and sit **inside the column** at its foot. Only one green button per screen.
- **5b, Add person sheet** (920 px):
  - **Name:** a field error is a 3 px inset danger ring with the message directly **under** the field (21/600 danger, error icon), e.g. "Someone is already called Sam. Try "Sam B" or a nickname."
  - **Colour:** 76 px swatches. Taken colours are at 30% with a block icon; the selected swatch gets a 5 px bg gap, a 4 px `text` ring and a check. Underneath: "Green and blue are taken by Alex and Sam."
  - **Role:** full-width radio rows, 88 px min height, radius 24. Each has a 30 px radio, the name at 25/700 and its description at 20 muted. Selected: `accentSoft` with a 3 px accent inset ring and a 9 px radio ring.
  - **PIN:** "Set PIN" (quiet) with the hint "Without a PIN, Sam can only look."
  - **Footer:** a 92 px full-width primary "Save person".

### Settings › Calendars (5c, note 46)
- **Account row:** 112 px, with the Google account, "Updated 2 minutes ago" and a quiet Disconnect.
- **Calendar rows:** a fixed **104 px** line: the name at 27/600, with the Main badge **inline** (36 px, `accentSoft`/accent, "Main · new events go here"); a person dropdown pill (56 px, s2, colour dot and expand icon); and Show with an 88 × 50 switch.
- **Main calendar:** its switch is at 45% opacity and labelled "Always shown".
- **Read problem:** a full-width `dangerSoft` strip at the card's foot (padding 16/24/16/30) with `sync_problem` in danger, the message in `text` at 21, and the fix button (an s1 pill) inside it.
- **"Use for new events":** a quiet 60 px button under the line, only on non-main calendars.

---

## Interactions and behaviour
- **Theme:** follows sunrise and sunset with the existing 400 ms fade. The theme control previews the other theme.
- **Today:** the scroll view hides its scrollbar; the fade and pinned pill stay fixed. The pill and "See the week" both open Calendar on today.
- **One-tap:** modes toggle on tap and run the device commands, with optimistic UI that reverts on error with a toast. Actions and sounds fire once, with a 150 ms press flash.
- **Door card:** appears over any screen with the chime; the music ducks to the level set in Settings › Music; it closes after 60 s; "Got it" marks the ring seen.
- **Lights:** a switch toggles the room. Tapping a scene chip on an off room turns it on with that scene. The slider is disabled while the room is off but keeps its level.
- **Calendar:** tapping empty column space opens Add event for that day, showing the ghost while pressed.
- **PIN lockout:** the countdown ticks every second, and the keys re-enable at 0:00.
- **Toasts and warning pills:** stay at least 6 s, plus 1 s per 10 words. The "Settings closes in 20 s" pill should show its countdown as a ring that runs down.
- **Clear history:** confirm, then PIN, then delete the rows **and** the files.

## State
Today: `todayEvents`, `tomorrowEvents`, measured overflow, then the mode (scroll, exact or topped up). Home buttons: an ordered list of up to 6 `{type: mode|action|sound, id}`, plus each mode's on/off. Shopping: items `{text, addedBy?, done}`, synced. Lights: rooms `{on, level, dimmable, scenes, activeScene}`. Door log: `{time, kind: ring|motion|unknown, source, snapshotPath?, seen}`. Camera watching flag. Signed-in user and role.

## Design tokens
These are the existing tokens, plus `dangerSoft` and `border`.
- **Dark:** bg #0E1011 · s1 #1A1D1E · s2 #24282A · s3 #303537 · text #F1F4F2 · muted #9AA3A0 · accent #4CB387 · accentSoft #173427 · onAccent #0E1011 · danger #EE7B6A · dangerSoft #3A211D · weather #E0B85B · rain #7FB2E5 · card border none.
- **Light:** bg #EDF0EE · s1 #FFFFFF · s2 #F1F4F2 · s3 #DDE3E0 · text #111514 · muted #5A6461 · accent #23704F · accentSoft #D3EDE1 · onAccent #FFFFFF · danger #B83A28 · dangerSoft #F7DFDA · weather #8A6400 · rain #2F6FAE · **card border #D5DCD8 (1 px)**.
- **Person colours:** Alex #4CB387 · Sam #5B9BE0 · Mia #E37FAE · Family #9B7BE0 · plus lime #A6C64E · orange #E8613A · yellow #D9C23A. Chip tint alpha: 0x30 dark / 0x26 light; multi-day bar 0x4D / 0x40.
- **Night clock:** #5A5A5A on #000. Camera glyph #2E3331.
- **Radii (px):** cards 36 · sheets and door card 44 · rows 22–28 · tray tiles 26 · pills fully rounded.
- **Gaps:** grid 22 · lists 14–18 · chips 12.
- **Control heights (px):** chips 60–64 · quiet buttons 68–72 · primary sheet footers 88–92.
- **Type:** DM Sans. Clock 150/600 · page titles 68/700 · sheet titles 52/700 · card titles 30–34/700 · row titles 27–31/600 · body 22–25 · meta 19–21 · section labels 18–19/700 uppercase, letter-spacing 1.5.
- **Shadows:** door card and PIN pad 0 40 120 rgba(0,0,0,.6). The pinned Today pill has 0 −6 24 rgba(0,0,0,.18).

## Assets
- **Placeholders, to be supplied:** the photo, album art and Ring snapshot areas are striped placeholders.
- **Culvery mark:** cropped from the review screenshots. Use the real asset.
- **Icons:** Material Symbols Rounded (Google Fonts); map to the app's existing icon set.

## Open decisions
1. Whether the slideshow moves to the idle screen when every card is set up (note 14).
2. The night clock grey and size, to be tested in the dark room (note 39).
3. The extra sounds and actions in Home buttons are placeholders.
4. These are proposed, not existing: "Pause everywhere", "Play last", "Mark all seen" and "See full size".

## Not redesigned (keep current, apply the new patterns)
- **Settings pages:** Display, Music, Lights, Door, Home location, People and Tablet lock. Use the 5c row pattern and put errors under their fields.
- **Wizard steps:** Welcome, Where's home, Who's setting up, Connect calendar, Your calendars, Ready and PIN-to-carry-on.
- **Event sheets:** Event detail (read-only, repeating, delete confirm) and the date and time pickers.
- **Sheets:** Favourites, and the Hue pairing and address sheets.

## Files
- `Culvery Home Review.dc.html`: every artboard and the full note list (notes 1–46).
- `support.js`: the runtime the reference file needs to open.
- `assets/`: the two review screenshots the Culvery mark is cropped from.
