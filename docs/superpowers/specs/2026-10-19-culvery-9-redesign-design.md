# Culvery 9: the October redesign, applied to what exists

Brainstormed 2026-10-19 from the user's design review. The design handoff is in `docs/design/2026-10-redesign/handoff.md` (sizes, colours, copy and behaviour per screen) with its 46 numbered notes in `notes.txt`. The handoff is **high fidelity**: its values are final unless it says "provisional". This spec says what Plan 9 takes from it, what it leaves, and the rulings that change it. Where this spec and the handoff disagree, this spec wins.

Branch `plan-9-redesign`, from `main` at 7d03651, in the one `culvery` checkout (no worktrees).

## 1. Purpose and outcome

The user's end-of-v1 design walk-through produced a redesign. Plan 9 applies it to the screens and features Culvery has today. The designs for features Culvery doesn't have yet are **speculative**: they wait for those features (§8).

Done means: every in-scope screen matches the handoff (allowing for the rulings below), the screenshot tests re-recorded to the new look and each one looked at, a walk-through on the emulator and the tablet, a final review, a local merge, and the build on the tablet.

## 2. Rulings (the user, 2026-10-19)

1. **New features are speculative designs.** Shopping list, the One-tap tray and Settings › Home buttons, Party mode, house Holiday mode, Start hoover, the sound pads, the family slideshow, and Door's Clear history, latest-picture panel, Mark all seen and See full size are **not** built in Plan 9. **Multi-day events are folded into 9b and 9c** (the user, 2026-10-20): the week's lane in 9b, creating them in 9c's Add event, "Day 2 of 3" on Home's Today with them; their plans settle the details from note 31 and the follow-ups' multi-day entry, and the "**Not**" lines below no longer apply to them.
2. **Coming up goes now.** Home's Coming up card is removed. Today always fills 5 rows, topping up from tomorrow or scrolling (note 9), and the week is one tap away. Lights and Music take the right column until Shopping and the tray arrive.
3. **Holiday mode stays on Home's Lights card for now.** Until the One-tap tray exists, the Home Lights card keeps a Holiday mode button. The Lights tab also gets the design's top tray with All lights off and Holiday lights (note 15).
4. **The door card closes after 60 seconds** (was 2 minutes, Plan 5). The ring stays in the Door tab with its badge.
5. **An off room looks off** (note 18). Its slider is disabled, keeping the remembered level and reading "Off"; the switch or a scene chip turns the room on (a scene chip turns it on with that scene). This replaces Plan 7's "moving the slider of an off room turns it on".

## 3. Units

The handoff gives every size in device pixels on a 1920 × 1200 artboard, and says dp = px ÷ 1.5 (sp likewise for type). The builder confirms the SM-T510's density (expected 1.5, 240 dpi; the app's screenshot tests already draw at 1280 × 800 dp) before converting, and converts once, in the theme and the screens' dimens, rounding to whole dp.

## 4. In scope, by screen

The handoff's section for each screen is the specification; the notes in brackets are the reasons.

### 4.1 Shared frame (handoff "Shared frame")
- Rail: mark padding equal on top and sides (note 6); nav pills; the Door badge as a danger pill with a background ring (note 5); the Settings tile.
- Top bar: the camera mark becomes the "Watching" pill, shown while the camera is running (§6.7, keeping Plan 8's rule) (note 10); the signed-in block with a real Sign out pill (note 11); the theme control reads "Night · Auto" / "Day · Auto", and "Day · Preview" / "Night · Preview" while previewing, tapping it returns to Auto (note 7).
- Light theme: cards get a 1 px `border` #D5DCD8 (note 8). New tokens `border` (light only) and `rain` (#7FB2E5 dark, #2F6FAE light); `dangerSoft` already exists.

### 4.2 Home (handoff "Home", 1a and 1c only)
- Header with the current weather and a 3-day strip; the Forecast card goes; with no location, a header pill "Add home location for weather"; rain icons blue, sun amber (notes 1, W7).
- Grid: Today on the left (600 px column), the right column Lights and Music. Ruling 2: no Coming up. **Lights on top, Music below, each about half the column's height** (the user, 2026-10-19); their parts keep the handoff's sizes and the extra height goes to space between the top row and the controls. When Shopping and the tray arrive, they return to the handoff's 318 px row at the foot. This replaces the plug-in 3 × 2 `HomeCardPlacer` grid for Home with the handoff's two-column layout; the placer's priority rules still decide which cards show.
- Today: the fill rule exactly as the handoff (MAX 5: scroll with a fade and a pinned "N more today · Open calendar" pill; exactly 5; topped up from tomorrow under a TOMORROW divider with outlined rows; nothing today); 2-line titles; overflow measured, not only counted (notes 9, 21). Hide "See the week" while the pinned pill shows. **Not** "Day 2 of 3" (multi-day, §8).
- Lights card: top row with the scene in the status ("On · 70% · Relax"), the brightness slider for the main room in the middle (disabled while the room is off, ruling 5), and the bottom row **All lights off** and **Holiday mode** (ruling 3). The design's Scene pill waits for the tray (note 3).
- Music card: bigger art, one control row (primary Pause, quiet Next, a volume pill with a speaker icon) (notes 4, 27).
- Layout for a household without Lights and Music: see §6.

### 4.3 Door card (handoff 2a)
Full-screen scrim, the bigger card with edge and shadow, the snapshot at full width 16:9, "Got it" primary and "Door history ›" quiet (opens the Door tab), and a "Music lowered" chip — shown only when a quieter actually lowered something. Closes after 60 s (ruling 4). The chime, quiet hours and night rules are unchanged.

### 4.4 Lights tab (handoff 2b/2d)
Page header with the count; a top tray (outlined, as the handoff's One-tap pattern) holding **All lights off** and **Holiday lights** (Holiday mode, shown ON while on) — the same Holiday mode as Home's, with its fresh PIN; room cards with the off look (ruling 5) and always-filled scene chips (note 18); the grid grows 2 → 3 columns then scrolls with a fade and "N more rooms" pill (note 20); rooms that are on sort first.

### 4.5 Music tab (handoff 3b)
Header with the count and "· Sonos"; room cards with the control row and labelled volume; chips always filled, the group chip "2 rooms" expanding to per-room volumes (note 25); nothing playing hides Next and shows **Play last** — the existing Play that resumes the queue, relabelled (note 26). **Not** "Pause everywhere" (§8): the header's tray is left out.

### 4.6 Door tab (handoff 3c, partly)
The shared title block ("Door" and a subtitle counting new rings), day labels, the row design with thumbnails and a red NEW tag on new rings, and the copy: "Someone rang", "Movement", "Notice from Ring" / "Culvery couldn't tell what this was" (notes 28, 29). **Not** the latest-picture panel, Mark all seen, See full size or Clear history (§8): the list takes the full width until they come.

### 4.7 Calendar week (handoff 3a, partly)
Header with one ‹ › pill and "+ Add event" (note 22); no legend (note 23); columns with today's ring; chips tinted with the person's colour, 2-line titles; empty column space as the add target with the pressed ghost "+ Add on Sun 27" (note 24). **Not** the multi-day lane (§8).

### 4.8 Add event sheet (handoff 4a)
The sheet from the right; the title field; section labels; chips; a child's Who chips locked with the explanation line (note 33); "Pick a date" and "Pick a time" without ellipses (note 34); Time and Length side by side; Repeats with "More options ▾" expanding to Every month, Every year and the Ends row, scrolling only once open (note 35); the footer primary, and **disabled as a 2 px outline with a muted label and the reason above it** ("Add a title first") (note 36). The disabled outline replaces the half-faded label from Plan 8 (b92e24b) for this footer and for every disabled `HhButton` (one disabled style app-wide).

### 4.9 PIN pad, locked out (handoff 4b)
The scrim, card, lock circle, "Too many tries" copy, the live countdown with a draining bar, every key dimmed including 0 and backspace, Cancel at full strength, no layout jump (note 37).

### 4.10 Night clock (handoff 4c)
300 px (200 sp), tabular figures, tighter tracking, #5A5A5A on black (provisional: tested in the dark room), and the camera mark as a bare #2E3331 glyph top right, in the awake pill's spot (notes 38, 39).

### 4.11 Setup wizard (handoff 5a/5b) and the patterns on the other steps
Step dots (done / here / to go) (note 40); one green button per screen, "Add person" quiet (note 41); Back and Next inside the 960 px column, 92 px, Next with an arrow (note 42); errors under their field with a suggestion (note 43); role radio rows (note 44); the full-width Save footer and the colour swatches with taken colours faded (note 45). The other wizard steps get the same patterns (handoff "Not redesigned").

### 4.12 Settings › Calendars (handoff 5c) and the other Settings pages
Fixed 104 px rows with the inline Main badge, the person dropdown and Show; the read problem as a strip at the card's foot; "Always shown" on the main calendar (note 46). The other Settings pages (Display, Music, Lights, Door, Home location, People, Tablet lock) take the 5c row pattern and errors under fields; the Settings list takes the handoff's list styling (2c's left panel).

### 4.13 Interactions (handoff "Interactions and behaviour")
- Toasts and warning pills stay at least 6 s plus 1 s per 10 words, capped at 12 s (§6.10).
- The "Settings closes in 20 s" pill shows its countdown as a ring that runs down.
- Today's scroll view hides its scrollbar; the pill and "See the week" open Calendar on today.

## 5. Not in scope (kept as today)

Event detail sheets, the date and time pickers, the Favourites sheet, the Hue pairing and address sheets: unchanged except the shared tokens and button styles they already use.

## 6. Calls I made (check these)

1. **A household without Lights or Music:** the right column shows **Coming up** (kept for this case only, restyled with the new tokens) until the slideshow or Shopping exists, so Home is never half empty; with only one of Lights or Music, that card takes the full right column. Each of the three layouts gets a screenshot test. *(The handoff's 1b uses the speculative slideshow.)*
2. **Home's Lights card bottom row** is All lights off and Holiday mode (ruling 3); the current scene moves into the status line, and the Scene pill comes with the tray.
3. **"Music lowered"** shows only when a quieter really lowered a group.
4. **One disabled button style** (the outline) everywhere, replacing Plan 8's half-faded label. The reason line above it is **optional**: shown where the handoff gives one (Add event's "Add a title first"), not invented for every disabled button.
5. **Door tab without the panel:** the list runs full width.
6. **Units:** converted once from px at the confirmed density (§3).
7. **The Watching pill follows the camera, not the setting.** Plan 8's privacy rule stands: a mark shows whenever the camera is running. The top bar's pill shows while the camera runs (`Presence.running`); on screens without the top bar (Settings, sheets, the wizard) the small overlay mark stays, as today. The night clock's bare glyph replaces the pill at night (where the camera is off anyway).
8. **"New" rings in the Door tab** are the rings that were unseen when the tab opened: they keep their NEW tag and count for that visit, while opening the tab still marks them seen (Plan 5), so the rail badge clears as before.
9. **Lights rooms sort once,** when the tab opens (on first), not live, so a card never jumps from under a finger.
10. **Toast timing:** at least 6 s plus 1 s per 10 words, capped at 12 s; the separate urgent/normal durations go.

## 7. Tests and rollout

- Every changed screen's Roborazzi baselines are re-recorded and each new image looked at against the handoff; the handoff's two Home screenshots are the "before".
- New behaviour gets tests: Today's fill rule at 0, 1–4, 5, 6+ and with measured overflow; the off-room slider disabled and a scene chip turning the room on; the door card closing at 60 s; the PIN countdown and keys re-enabling at 0:00; toast duration by word count; the Lights grid at 4, 6 and 9 rooms with on-first ordering; the theme control's preview copy; the Watching pill only while camera wake-up is on.
- Copy changes go through CopyScanTest; TalkBack labels follow the new copy.
- Walk-through on the emulator, then the tablet (`install -r`), including the night clock in the dark room.

**Size.** This is large. The plan should consider splitting it, for example 9a (shared frame, Home, door card, PIN pad, night clock), 9b (Lights, Music, Door and Calendar tabs) and 9c (Add event, setup wizard, Settings pages), each merged on its own.

## 8. Speculative designs, kept for later

Recorded in the follow-ups file under "Feature ideas", each pointing to the handoff: Shopping list (note 12), the One-tap tray and Settings › Home buttons (notes 13, 17) with Party mode, house Holiday mode, Start hoover and the sound pads, the family slideshow and the idle screen (note 14), multi-day events (note 31, the week's lane and "Day 2 of 3"), Door's Clear history with file deletion and a 30-day auto-clear, the latest-picture panel, Mark all seen and See full size (notes 30, 32), and Music's Pause everywhere. The handoff's open decisions 1–4 travel with them.
