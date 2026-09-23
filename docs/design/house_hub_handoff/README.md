# Handoff: House Hub — personal house management app (Android tablet)

## Overview
A full-screen, landscape Android tablet app (wall-mounted / kiosk) for managing a family home. Five tabs — Home, Calendar, Lights, Music, Climate — plus a persistent **Holiday mode** button that configures multiple devices at once (Philips Hue, heating, Sonos, alarm, smart plugs, Google Calendar).

## About the Design Files
`House Hub.dc.html` is a **design reference built in HTML** — a working prototype of intended look and behaviour, not production code. Recreate it natively for Android. Recommended stack if none exists: **Kotlin + Jetpack Compose (Material 3)**, kiosk/immersive mode (`WindowInsetsController` hide system bars, `FLAG_KEEP_SCREEN_ON`, optional Lock Task / screen pinning).

Open the HTML file in Chrome to interact with it. Tweakable props: `theme` (auto/dark/light), `showStatusBar`, `armCountdown`.

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
- `House Hub.dc.html` — interactive prototype (all screens, states and logic in one file; open in Chrome).
